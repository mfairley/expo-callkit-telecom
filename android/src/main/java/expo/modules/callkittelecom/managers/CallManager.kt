package expo.modules.callkittelecom.managers

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallsManager
import expo.modules.callkittelecom.events.CallEventEmitter
import expo.modules.callkittelecom.events.CallEvents
import expo.modules.callkittelecom.events.CallEvents.AUDIO_SESSION_ACTIVATED
import expo.modules.callkittelecom.events.CallEvents.AUDIO_SESSION_DEACTIVATED
import expo.modules.callkittelecom.events.CallEvents.CALL_ANSWERED
import expo.modules.callkittelecom.events.CallEvents.CALL_INTENT_RECEIVED
import expo.modules.callkittelecom.events.CallEvents.INCOMING_CALL_REPORTED
import expo.modules.callkittelecom.events.CallEvents.VOIP_PUSH_TOKEN_UPDATED
import expo.modules.callkittelecom.models.CallEndedReason
import expo.modules.callkittelecom.models.CallOptions
import expo.modules.callkittelecom.models.CallParticipant
import expo.modules.callkittelecom.models.CallSession
import expo.modules.callkittelecom.models.CallSessionOrigin
import expo.modules.callkittelecom.models.CallSessionStatus
import expo.modules.callkittelecom.models.IncomingCallEvent
import expo.modules.callkittelecom.store.CallStore
import expo.modules.callkittelecom.utils.CallKitTelecomLog
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/**
 * Central Android call lifecycle manager using Core-Telecom Jetpack.
 *
 * Responsibilities:
 * - Create/report calls through Core-Telecom's CallsManager
 * - Maintain native call session state
 * - Emit shared JS call events in expected order
 * - Coordinate audio lifecycle and request fulfillment semantics
 */
class CallManager private constructor() {
    companion object {
        private const val TAG = "ExpoCallKitTelecom.Call"

        /** Shared singleton instance used by module and notification receiver. */
        val shared = CallManager()
    }

    private lateinit var context: Context
    private lateinit var callsManager: CallsManager

    private var isInitialized = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Channels for dispatching actions into active Core-Telecom call scopes.
     *
     * Scope methods are called from within the addCall block via channel-based dispatch rather than
     * storing and calling CallControlScope references externally.
     */
    private class CallActions {
        val setActive = Channel<Unit>(Channel.CONFLATED)
        val setInactive = Channel<Unit>(Channel.CONFLATED)
        val disconnect = Channel<DisconnectCause>(Channel.CONFLATED)
        val endpointChange = Channel<CallEndpointCompat>(Channel.CONFLATED)
    }

    /**
     * Encapsulates the coroutine job, action channels, and timeout for a single call. Consolidates
     * what was previously three separate maps.
     */
    private class CallController(
        val job: Job,
        val actions: CallActions,
        var timeoutJob: Job? = null,
    )

    /** Active call controllers, keyed by call UUID. */
    private val activeCalls = ConcurrentHashMap<UUID, CallController>()

    private var incomingCallTimeoutMs = 45_000L
    private var outgoingCallTimeoutMs = 60_000L
    private var fulfillAnswerTimeoutMs = 30_000L

    /** Initializes Core-Telecom CallsManager + dependent managers. Safe to call repeatedly. */
    fun initialize(appContext: Context) {
        if (isInitialized) return

        context = appContext.applicationContext
        CallKitTelecomLog.init(context)

        // Configure event queue limits early so events emitted before the Expo
        // module loads (e.g. CALL_ANSWERED during cold-start answer) are queued
        // instead of dropped by the default limit of 0.
        CallEventEmitter.setQueueLimit(CALL_INTENT_RECEIVED, 1)
        CallEventEmitter.setQueueLimit(AUDIO_SESSION_ACTIVATED, 1)
        CallEventEmitter.setQueueLimit(AUDIO_SESSION_DEACTIVATED, 1)
        CallEventEmitter.setQueueLimit(INCOMING_CALL_REPORTED, 1)
        CallEventEmitter.setQueueLimit(CALL_ANSWERED, 1)
        CallEventEmitter.setQueueLimit(VOIP_PUSH_TOKEN_UPDATED, 1)

        callsManager = CallsManager(context)
        callsManager.registerAppWithTelecom(
            CallsManager.CAPABILITY_BASELINE or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING
        )

        incomingCallTimeoutMs =
            readTimeoutMs("ExpoCallKitTelecomIncomingCallTimeout", incomingCallTimeoutMs)
        outgoingCallTimeoutMs =
            readTimeoutMs("ExpoCallKitTelecomOutgoingCallTimeout", outgoingCallTimeoutMs)
        fulfillAnswerTimeoutMs =
            readTimeoutMs("ExpoCallKitTelecomFulfillAnswerCallTimeout", fulfillAnswerTimeoutMs)

        CallAudioManager.initialize(context)
        CallAudioManager.onRequestEndpointChange = { endpoint ->
            val activeId = CallStore.firstSession()?.id
            if (activeId != null) {
                activeCalls[activeId]?.actions?.endpointChange?.trySend(endpoint)
            }
        }
        DialtonePlayer.initialize(context)
        CaptureSessionManager.initialize(context)
        CallNotificationManager.initialize(context)

        isInitialized = true
        CallKitTelecomLog.d(TAG) { "Initialized CallManager" }
    }

    /** Reads a timeout from Android manifest metadata (seconds) and returns milliseconds. */
    private fun readTimeoutMs(key: String, defaultMs: Long): Long =
        try {
            val defaultSeconds = (defaultMs / 1000).toInt()
            val appInfo =
                context.packageManager.getApplicationInfo(
                    context.packageName,
                    PackageManager.GET_META_DATA,
                )
            val seconds = appInfo.metaData?.getInt(key, defaultSeconds) ?: defaultSeconds
            seconds.toLong() * 1000
        } catch (_: Throwable) {
            defaultMs
        }

    // region Call Timeout

    /** Starts a call timeout that marks non-connected calls as unanswered. */
    private fun startCallTimeout(id: UUID, timeoutMs: Long) {
        cancelCallTimeout(id)
        CallKitTelecomLog.d(TAG) { "Starting call timeout - id: $id, timeout: ${timeoutMs}ms" }

        val job =
            scope.launch {
                delay(timeoutMs)
                val session = CallStore.session(id) ?: return@launch
                if (session.status == CallSessionStatus.CONNECTED) {
                    return@launch
                }
                CallKitTelecomLog.d(TAG) { "Call timeout expired - id: $id" }
                DialtonePlayer.stop()
                reportCallEnded(id, CallEndedReason.UNANSWERED)
            }

        activeCalls[id]?.timeoutJob = job
    }

    /** Cancels an active timeout for a specific call UUID. */
    private fun cancelCallTimeout(id: UUID) {
        val job = activeCalls[id]?.timeoutJob ?: return
        job.cancel()
        activeCalls[id]?.timeoutJob = null
        CallKitTelecomLog.d(TAG) { "Cancelled call timeout - id: $id" }
    }

    // endregion

    /** Creates a Telecom URI from participant fields, preferring phone number. */
    private fun participantUri(participant: CallParticipant): Uri {
        val phoneNumber = participant.phoneNumber
        if (!phoneNumber.isNullOrBlank()) {
            return Uri.fromParts(PhoneAccount.SCHEME_TEL, phoneNumber, null)
        }

        val email = participant.email
        if (!email.isNullOrBlank()) {
            return Uri.fromParts(PhoneAccount.SCHEME_SIP, email, null)
        }

        return Uri.fromParts(
            PhoneAccount.SCHEME_SIP,
            "${participant.id}@callkit-telecom.local",
            null,
        )
    }

    // region Start Outgoing Call

    /**
     * Starts a new outgoing call via Core-Telecom.
     *
     * Steps:
     * - validates single-session constraint
     * - creates/queues local session
     * - preps audio for call
     * - calls addCall with DIRECTION_OUTGOING
     */
    fun startOutgoingCall(recipient: CallParticipant, options: CallOptions): String {
        val existingSession = CallStore.firstSession()
        if (existingSession != null) {
            CallKitTelecomLog.w(TAG) {
                "Cannot start outgoing call - session already exists: ${existingSession.id}"
            }
            throw IllegalStateException("A call session already exists")
        }

        val id = UUID.randomUUID()
        CallKitTelecomLog.d(TAG) { "Starting outgoing call - id: $id" }

        val session =
            CallSession(
                id = id,
                options = options,
                origin = CallSessionOrigin.OUTGOING_APP,
                remoteParticipants = listOf(recipient),
                incomingCallEvent = null,
                status = CallSessionStatus.REQUESTING,
                connectedAt = null,
                isMuted = false,
                isOnHold = false,
                dtmfDigits = null,
            )

        CallAudioManager.prepareAudioSessionForCall(options.hasVideo)
        CallStore.add(session)

        val attributes =
            CallAttributesCompat(
                displayName = recipient.displayName ?: "Unknown",
                address = participantUri(recipient),
                direction = CallAttributesCompat.DIRECTION_OUTGOING,
                callType =
                    if (options.hasVideo) {
                        CallAttributesCompat.CALL_TYPE_VIDEO_CALL
                    } else {
                        CallAttributesCompat.CALL_TYPE_AUDIO_CALL
                    },
                callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE,
            )

        val actions = CallActions()
        val job =
            launchCallScope(
                id = id,
                attributes = attributes,
                actions = actions,
                onAnswer = { /* Outgoing calls don't receive onAnswer */ },
            ) {
                CallStore.updateStatus(id, CallSessionStatus.CONNECTING)
                CallAudioManager.onAudioActivated(CallStore.allSessions())

                CallNotificationManager.showDialingCall(context, id, recipient.displayName)

                // Request speaker for video calls
                if (options.hasVideo) {
                    val speakerEndpoint = findEndpointByType(CallEndpointCompat.TYPE_SPEAKER)
                    if (speakerEndpoint != null) {
                        actions.endpointChange.trySend(speakerEndpoint)
                    }
                }

                DialtonePlayer.play(context)

                CallEventEmitter.send(
                    CallEvents.OUTGOING_CALL_STARTED,
                    mapOf("id" to id.toString()),
                )

                startCallTimeout(id, outgoingCallTimeoutMs)
            }

        activeCalls[id] = CallController(job, actions)

        return id.toString()
    }

    // endregion

    // region Report Incoming Call

    /**
     * Reports an incoming call via Core-Telecom.
     *
     * Steps:
     * - validates single-session constraint
     * - creates ringing session in store
     * - shows incoming call notification
     * - calls addCall with DIRECTION_INCOMING
     * - emits `onIncomingCallReported`
     */
    fun reportIncomingCall(event: IncomingCallEvent) {
        val existingSession = CallStore.firstSession()
        if (existingSession != null) {
            CallKitTelecomLog.w(TAG) {
                "Cannot report incoming call - session already exists: ${existingSession.id}"
            }
            throw IllegalStateException("A call session already exists")
        }

        val id = UUID.randomUUID()
        CallKitTelecomLog.d(TAG) { "Reporting incoming call - id: $id" }

        val caller =
            CallParticipant(
                id = event.caller.id,
                phoneNumber = event.caller.phoneNumber,
                email = event.caller.email,
                displayName = event.caller.displayName,
                avatarUrl = event.caller.avatarUrl,
            )

        val session =
            CallSession(
                id = id,
                options = CallOptions(hasVideo = event.hasVideo),
                origin = CallSessionOrigin.INCOMING,
                remoteParticipants = listOf(caller),
                incomingCallEvent = event,
                status = CallSessionStatus.RINGING,
                connectedAt = null,
                isMuted = false,
                isOnHold = false,
                dtmfDigits = null,
            )

        CallAudioManager.prepareAudioSessionForCall(event.hasVideo)
        CallStore.add(session)

        CallNotificationManager.showIncomingCall(
            context,
            id,
            event.caller.displayName,
            event.hasVideo,
        )

        val attributes =
            CallAttributesCompat(
                displayName = event.caller.displayName ?: "Unknown",
                address = participantUri(caller),
                direction = CallAttributesCompat.DIRECTION_INCOMING,
                callType =
                    if (event.hasVideo) {
                        CallAttributesCompat.CALL_TYPE_VIDEO_CALL
                    } else {
                        CallAttributesCompat.CALL_TYPE_AUDIO_CALL
                    },
                callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE,
            )

        val actions = CallActions()
        val job =
            launchCallScope(
                id = id,
                attributes = attributes,
                actions = actions,
                onAnswer = { _ ->
                    CallKitTelecomLog.d(TAG) { "Incoming call onAnswer (system) - id: $id" }
                    onCallAnswered(id)
                },
            ) {
                CallEventEmitter.send(
                    CallEvents.INCOMING_CALL_REPORTED,
                    mapOf("id" to id.toString()),
                )

                startCallTimeout(id, incomingCallTimeoutMs)
            }

        activeCalls[id] = CallController(job, actions)
    }

    // endregion

    // region Answer Call

    /** App-level answer request entrypoint (custom in-app answer button path). */
    fun answerCall(id: UUID) {
        CallKitTelecomLog.d(TAG) { "Answering call - id: $id" }
        CallNotificationManager.cancel(context)
        onCallAnswered(id)
    }

    /**
     * Fulfills pending incoming-call answer request.
     *
     * Marks call connected and transitions Core-Telecom scope to active.
     *
     * @return `true` if request existed and was fulfilled, `false` otherwise.
     */
    fun fulfillIncomingCallConnected(requestId: UUID): Boolean {
        val callId = FulfillRequestManager.fulfill(requestId) ?: return false

        val now = Instant.now()
        CallStore.update(callId) { session ->
            session.copy(status = CallSessionStatus.CONNECTED, connectedAt = now)
        }

        activeCalls[callId]?.actions?.setActive?.trySend(Unit)

        val callerName = CallStore.session(callId)?.remoteParticipants?.firstOrNull()?.displayName
        CallNotificationManager.showOngoingCall(context, callId, callerName, now.toEpochMilli())

        CallKitTelecomLog.d(TAG) {
            "Fulfilled incoming call - callId: $callId, requestId: $requestId"
        }
        return true
    }

    /** Reports outgoing media is connected and sets call state to connected. */
    fun reportOutgoingCallConnected(id: UUID) {
        CallKitTelecomLog.d(TAG) { "Reporting outgoing call connected - id: $id" }
        DialtonePlayer.stop()
        cancelCallTimeout(id)

        val now = Instant.now()
        CallStore.update(id) { session ->
            session.copy(status = CallSessionStatus.CONNECTED, connectedAt = now)
        }

        activeCalls[id]?.actions?.setActive?.trySend(Unit)

        val callerName = CallStore.session(id)?.remoteParticipants?.firstOrNull()?.displayName
        CallNotificationManager.showOngoingCall(context, id, callerName, now.toEpochMilli())
    }

    // endregion

    // region End Call

    /** Ends a call as a local/system user action (`onCallEnded` path). */
    fun endCall(id: UUID) {
        CallKitTelecomLog.d(TAG) { "Ending call - id: $id" }
        finishCall(id, emitEnded = true, reportedReason = null)
    }

    /** Reports externally-ended call with explicit reason (`onCallReportedEnded` path). */
    fun reportCallEnded(id: UUID, reason: CallEndedReason) {
        CallKitTelecomLog.d(TAG) { "Reporting call ended - id: $id, reason: ${reason.value}" }
        finishCall(id, emitEnded = false, reportedReason = reason)
    }

    /**
     * Shared call-finalization routine.
     * - Cancels timeouts and pending fulfill requests
     * - Disconnects Core-Telecom scope (which causes addCall to return)
     * - Emits ended/reported-ended events as requested
     * - Removes session from store
     * - Deactivates audio after last session
     */
    private fun finishCall(
        id: UUID,
        emitEnded: Boolean,
        reportedReason: CallEndedReason?,
        sendDisconnect: Boolean = true,
    ) {
        val existingSession = CallStore.session(id) ?: return
        DialtonePlayer.stop()
        cancelCallTimeout(id)
        FulfillRequestManager.cancelForCall(id)

        val callerName = existingSession.remoteParticipants.firstOrNull()?.displayName
        CallNotificationManager.showEndedCall(context, id, callerName)

        // Send disconnect cause to Core-Telecom scope via the action channel.
        // Don't cancel the job — let disconnect() cause addCall to return naturally
        // so the DisconnectCause is properly delivered to the Telecom framework.
        // The finally block provides safety-net cleanup if needed.
        if (sendDisconnect) {
            activeCalls.remove(id)?.actions?.disconnect?.trySend(disconnectCauseFor(reportedReason))
        }

        if (existingSession.status != CallSessionStatus.ENDED) {
            CallStore.updateStatus(id, CallSessionStatus.ENDED)
        }

        if (emitEnded) {
            CallEventEmitter.send(CallEvents.CALL_ENDED, mapOf("id" to id.toString()))
        }

        if (reportedReason != null) {
            CallEventEmitter.send(
                CallEvents.CALL_REPORTED_ENDED,
                mapOf("id" to id.toString(), "reason" to reportedReason.value),
            )
        }

        CallStore.remove(id)

        val remainingSessions = CallStore.allSessions()
        if (remainingSessions.isEmpty()) {
            CallAudioManager.onAudioDeactivated(remainingSessions)
        }

        CallKitTelecomLog.d(TAG) {
            "Call finished - id: $id, emitEnded: $emitEnded, reason: ${reportedReason?.value}"
        }
    }

    /**
     * Safety-net cleanup called from the addCall finally block.
     *
     * Ensures all resources are released even if finishCall didn't run due to an unexpected
     * exception or cancellation.
     */
    private fun cleanupCallIfNeeded(id: UUID) {
        activeCalls.remove(id)

        val session = CallStore.session(id) ?: return
        CallKitTelecomLog.w(TAG) {
            "Safety-net cleanup for call - id: $id, status: ${session.status.value}"
        }

        DialtonePlayer.stop()
        FulfillRequestManager.cancelForCall(id)
        CallNotificationManager.cancel(context)

        if (session.status != CallSessionStatus.ENDED) {
            CallStore.updateStatus(id, CallSessionStatus.ENDED)
        }
        CallEventEmitter.send(CallEvents.CALL_ENDED, mapOf("id" to id.toString()))
        CallStore.remove(id)

        if (CallStore.allSessions().isEmpty()) {
            CallAudioManager.onAudioDeactivated(emptyList())
        }
    }

    /** Maps shared end reasons to Android `DisconnectCause`. */
    private fun disconnectCauseFor(reason: CallEndedReason?): DisconnectCause =
        when (reason) {
            CallEndedReason.REMOTE_ENDED -> DisconnectCause(DisconnectCause.REMOTE)

            CallEndedReason.UNANSWERED -> DisconnectCause(DisconnectCause.MISSED)

            CallEndedReason.ANSWERED_ELSEWHERE,
            CallEndedReason.DECLINED_ELSEWHERE -> DisconnectCause(DisconnectCause.REMOTE)

            CallEndedReason.FAILED,
            CallEndedReason.UNKNOWN -> DisconnectCause(DisconnectCause.LOCAL)

            null -> DisconnectCause(DisconnectCause.LOCAL)
        }

    // endregion

    // region Mute Support

    /** Sets local mute state and emits `onSetMutedAction`. */
    fun setMuted(id: UUID, muted: Boolean) {
        CallKitTelecomLog.d(TAG) { "Setting mute state - id: $id, muted: $muted" }
        CallStore.updateMuted(id, muted)
        CallEventEmitter.send(
            CallEvents.SET_MUTED_ACTION,
            mapOf("id" to id.toString(), "isMuted" to muted),
        )
    }

    // endregion

    // region Video Support

    /** Reports video enabled state change and emits `onVideoChanged`. */
    fun reportVideo(id: UUID, enabled: Boolean) {
        CallKitTelecomLog.d(TAG) { "Setting video state - id: $id, enabled: $enabled" }
        CallStore.update(id) { session ->
            session.copy(options = session.options.copy(hasVideo = enabled))
        }

        CallAudioManager.prepareAudioSessionForCall(enabled)

        CallEventEmitter.send(
            CallEvents.VIDEO_CHANGED,
            mapOf("id" to id.toString(), "hasVideo" to enabled),
        )
    }

    // endregion

    // region Hold Support

    /** Sets hold state, updates Core-Telecom scope state, and emits `onSetHeldAction`. */
    fun setHeld(id: UUID, onHold: Boolean) {
        CallKitTelecomLog.d(TAG) { "Setting hold state - id: $id, onHold: $onHold" }
        CallStore.updateHeld(id, onHold)

        if (onHold) {
            activeCalls[id]?.actions?.setInactive?.trySend(Unit)
        } else if (CallStore.session(id)?.status == CallSessionStatus.CONNECTED) {
            activeCalls[id]?.actions?.setActive?.trySend(Unit)
        }

        CallEventEmitter.send(
            CallEvents.SET_HELD_ACTION,
            mapOf("id" to id.toString(), "isOnHold" to onHold),
        )
    }

    // endregion

    // region DTMF Support

    /** Records requested DTMF digits and emits `onDTMF`. */
    fun playDTMF(id: UUID, digits: String) {
        CallKitTelecomLog.d(TAG) { "Playing DTMF - id: $id, length: ${digits.length}" }
        CallStore.update(id) { session -> session.copy(dtmfDigits = digits) }

        CallEventEmitter.send(CallEvents.DTMF, mapOf("id" to id.toString(), "digits" to digits))
    }

    // endregion

    // region Core-Telecom Helpers

    /**
     * Launches a Core-Telecom call scope with shared lifecycle management.
     *
     * Handles channel setup, action dispatch via select, flow collectors, and safety-net cleanup in
     * a single place.
     *
     * @param id Call UUID
     * @param attributes Core-Telecom call attributes
     * @param actions Pre-created CallActions for this call's channel dispatch
     * @param onAnswer Callback for system-initiated answer (incoming calls)
     * @param onScopeReady Called after scope setup for direction-specific logic.
     * @return The launched Job
     */
    private fun launchCallScope(
        id: UUID,
        attributes: CallAttributesCompat,
        actions: CallActions,
        onAnswer: (callType: Int) -> Unit,
        onScopeReady: CallControlScope.() -> Unit,
    ): Job =
        scope.launch(CoroutineName("Call-$id")) {
            try {
                callsManager.addCall(
                    callAttributes = attributes,
                    onAnswer = onAnswer,
                    onDisconnect = { cause ->
                        CallKitTelecomLog.d(TAG) {
                            "Call onDisconnect - id: $id, cause: ${cause.code}"
                        }
                        finishCall(
                            id,
                            emitEnded = true,
                            reportedReason = null,
                            sendDisconnect = false,
                        )
                    },
                    onSetActive = { setHeld(id, false) },
                    onSetInactive = { setHeld(id, true) },
                ) {
                    val callScope: CallControlScope = this

                    // Single coroutine handles all action channels via select
                    launch { handleCallActions(id, actions, callScope) }

                    // Direction-specific setup
                    onScopeReady()

                    // Collect endpoint and mute state flows to keep scope alive
                    collectCallFlows(id, callScope)
                }
            } catch (_: CancellationException) {
                CallKitTelecomLog.d(TAG) { "Call coroutine cancelled - id: $id" }
            } catch (e: Exception) {
                CallKitTelecomLog.e(TAG) { "Call addCall failed - id: $id, error: ${e.message}" }
                finishCall(
                    id,
                    emitEnded = true,
                    reportedReason = CallEndedReason.FAILED,
                    sendDisconnect = false,
                )
            } finally {
                cleanupCallIfNeeded(id)
                CallKitTelecomLog.d(TAG) { "Call addCall block exited - id: $id" }
            }
        }

    /**
     * Processes call action channels using a single select loop.
     *
     * Runs until the coroutine is cancelled (when addCall returns). Using select ensures actions
     * are processed sequentially, preventing concurrent scope method calls from racing.
     */
    private suspend fun handleCallActions(id: UUID, actions: CallActions, scope: CallControlScope) {
        while (currentCoroutineContext().isActive) {
            select<Unit> {
                actions.setActive.onReceive {
                    handleControlResult(id, "setActive", scope.setActive())
                }
                actions.setInactive.onReceive { logIfError(id, "setInactive", scope.setInactive()) }
                actions.disconnect.onReceive { cause -> scope.disconnect(cause) }
                actions.endpointChange.onReceive { endpoint ->
                    logIfError(id, "requestEndpointChange", scope.requestEndpointChange(endpoint))
                }
            }
        }
    }

    /**
     * Handles CallControlResult for critical actions like setActive.
     *
     * When a critical action fails, the call cannot continue in a valid state and is ended with a
     * FAILED reason.
     */
    private fun handleControlResult(id: UUID, action: String, result: CallControlResult) {
        if (result is CallControlResult.Error) {
            CallKitTelecomLog.e(TAG) { "$action failed - id: $id, error: ${result.errorCode}" }
            reportCallEnded(id, CallEndedReason.FAILED)
        }
    }

    /** Logs Core-Telecom control action failures at error level. */
    private fun logIfError(id: UUID, action: String, result: CallControlResult) {
        if (result is CallControlResult.Error) {
            CallKitTelecomLog.e(TAG) { "$action failed - id: $id, error: ${result.errorCode}" }
        }
    }

    /**
     * Collects Core-Telecom endpoint and mute flows within the CallControlScope.
     *
     * These coroutines keep the addCall block alive and forward audio state changes to
     * CallAudioManager and CallStore.
     */
    private fun CoroutineScope.collectCallFlows(id: UUID, callScope: CallControlScope) {
        launch {
            callScope.availableEndpoints.collect { endpoints ->
                CallStore.session(id) ?: return@collect
                CallKitTelecomLog.d(TAG) {
                    "Available endpoints changed - id: $id, count: ${endpoints.size}"
                }
                CallAudioManager.onAvailableEndpointsChanged(endpoints)
            }
        }

        launch {
            callScope.currentCallEndpoint.collect { endpoint ->
                CallStore.session(id) ?: return@collect
                CallKitTelecomLog.d(TAG) {
                    "Endpoint changed - id: $id, type: ${endpoint.type}, name: ${endpoint.name}"
                }
                CallAudioManager.onEndpointChanged(endpoint)
            }
        }

        launch {
            callScope.isMuted.collect { muted ->
                val session = CallStore.session(id) ?: return@collect
                if (session.isMuted != muted) {
                    CallKitTelecomLog.d(TAG) { "Mute state changed - id: $id, isMuted: $muted" }
                    CallStore.updateMuted(id, muted)
                    CallEventEmitter.send(
                        CallEvents.SET_MUTED_ACTION,
                        mapOf("id" to id.toString(), "isMuted" to muted),
                    )
                }
            }
        }
    }

    /**
     * Shared answer workflow for incoming calls.
     *
     * Emits `onCallAnswered` with a generated `requestId` and waits for JS to call
     * `fulfillIncomingCallAnswered(requestId)` once media is connected.
     */
    private fun onCallAnswered(id: UUID) {
        val session = CallStore.session(id) ?: return
        if (session.status == CallSessionStatus.CONNECTED) {
            CallKitTelecomLog.d(TAG) { "Call already connected, ignoring answer - id: $id" }
            return
        }

        cancelCallTimeout(id)

        CallStore.updateStatus(id, CallSessionStatus.CONNECTING)
        CallAudioManager.onAudioActivated(CallStore.allSessions())

        // Request speaker for video calls
        if (session.options.hasVideo) {
            val speakerEndpoint = findEndpointByType(CallEndpointCompat.TYPE_SPEAKER)
            if (speakerEndpoint != null) {
                activeCalls[id]?.actions?.endpointChange?.trySend(speakerEndpoint)
            }
        }

        val request =
            FulfillRequestManager.createRequest(callId = id, timeoutMs = fulfillAnswerTimeoutMs) {
                reportCallEnded(it, CallEndedReason.FAILED)
            }

        CallKitTelecomLog.d(TAG) { "Call answered - id: $id, requestId: ${request.requestId}" }

        CallEventEmitter.send(
            CallEvents.CALL_ANSWERED,
            mapOf("id" to id.toString(), "requestId" to request.requestId.toString()),
        )
    }

    /** Finds an endpoint by type from the cached available endpoints. */
    private fun findEndpointByType(type: Int): CallEndpointCompat? =
        CallAudioManager.currentAvailableEndpoints.firstOrNull { it.type == type }

    // endregion
}

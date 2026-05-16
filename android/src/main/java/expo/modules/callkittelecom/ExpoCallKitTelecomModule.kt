package expo.modules.callkittelecom

import expo.modules.callkittelecom.events.CallEventEmitter
import expo.modules.callkittelecom.events.CallEvents
import expo.modules.callkittelecom.managers.CallAudioManager
import expo.modules.callkittelecom.managers.CallManager
import expo.modules.callkittelecom.managers.CaptureSessionManager
import expo.modules.callkittelecom.managers.VoIPPushManager
import expo.modules.callkittelecom.models.CallEndedReason
import expo.modules.callkittelecom.models.CallOptions
import expo.modules.callkittelecom.models.CallParticipant
import expo.modules.callkittelecom.models.IncomingCallEvent
import expo.modules.callkittelecom.services.CallNotificationReceiver
import expo.modules.callkittelecom.store.CallStore
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.util.UUID

class ExpoCallKitTelecomModule : Module() {
    /**
     * Checks the current activity's launch intent for ACTION_ANSWER.
     *
     * On cold start from a notification answer action, the intent is delivered to onCreate() — not
     * onNewIntent() — so OnNewIntent never fires. This method handles that case by checking the
     * launch intent directly.
     */
    private fun handleLaunchIntent() {
        val intent = appContext.currentActivity?.intent ?: return
        handleAnswerIntent(intent)
    }

    /** Extracts the call ID from an ACTION_ANSWER intent and answers the call. */
    private fun handleAnswerIntent(intent: android.content.Intent) {
        if (intent.action != CallNotificationReceiver.ACTION_ANSWER) return

        val callIdStr = intent.getStringExtra(CallNotificationReceiver.EXTRA_CALL_ID) ?: return
        val callId =
            try {
                UUID.fromString(callIdStr)
            } catch (_: IllegalArgumentException) {
                return
            }

        // Clear the action so it isn't re-processed on configuration changes
        intent.action = null

        CallManager.shared.answerCall(callId)
    }

    override fun definition() = ModuleDefinition {
        Name("ExpoCallKitTelecom")

        // region Events

        Events(
            CallEvents.CALL_SESSION_ADDED,
            CallEvents.CALL_SESSION_UPDATED,
            CallEvents.CALL_SESSION_REMOVED,
            CallEvents.AUDIO_SESSION_ACTIVATED,
            CallEvents.AUDIO_SESSION_DEACTIVATED,
            CallEvents.AUDIO_ROUTE_CHANGED,
            CallEvents.INCOMING_CALL_REPORTED,
            CallEvents.OUTGOING_CALL_STARTED,
            CallEvents.CALL_ANSWERED,
            CallEvents.CALL_ENDED,
            CallEvents.CALL_REPORTED_ENDED,
            CallEvents.SET_MUTED_ACTION,
            CallEvents.VIDEO_CHANGED,
            CallEvents.SET_HELD_ACTION,
            CallEvents.DTMF,
            CallEvents.CALL_INTENT_RECEIVED,
            CallEvents.VOIP_PUSH_TOKEN_UPDATED,
        )

        // endregion

        // region Lifecycle

        OnCreate {
            val context = appContext.reactContext ?: return@OnCreate

            CallManager.shared.initialize(context)

            VoIPPushManager.register()

            CallEventEmitter.setSender { eventName, body -> sendEvent(eventName, body) }

            // Handle the launch intent for cold-start answer actions.
            // OnNewIntent only fires when the activity is already running.
            // On cold start the ACTION_ANSWER intent is the launch intent,
            // so we must check it here.
            handleLaunchIntent()
        }

        OnDestroy { CallEventEmitter.setSender(null) }

        OnNewIntent { intent -> handleAnswerIntent(intent) }

        // Register per-event observers
        OnStartObserving(CallEvents.CALL_SESSION_ADDED) {
            CallEventEmitter.startObserving(CallEvents.CALL_SESSION_ADDED)
        }
        OnStopObserving(CallEvents.CALL_SESSION_ADDED) {
            CallEventEmitter.stopObserving(CallEvents.CALL_SESSION_ADDED)
        }

        OnStartObserving(CallEvents.CALL_SESSION_UPDATED) {
            CallEventEmitter.startObserving(CallEvents.CALL_SESSION_UPDATED)
        }
        OnStopObserving(CallEvents.CALL_SESSION_UPDATED) {
            CallEventEmitter.stopObserving(CallEvents.CALL_SESSION_UPDATED)
        }

        OnStartObserving(CallEvents.CALL_SESSION_REMOVED) {
            CallEventEmitter.startObserving(CallEvents.CALL_SESSION_REMOVED)
        }
        OnStopObserving(CallEvents.CALL_SESSION_REMOVED) {
            CallEventEmitter.stopObserving(CallEvents.CALL_SESSION_REMOVED)
        }

        OnStartObserving(CallEvents.AUDIO_SESSION_ACTIVATED) {
            CallEventEmitter.startObserving(CallEvents.AUDIO_SESSION_ACTIVATED)
        }
        OnStopObserving(CallEvents.AUDIO_SESSION_ACTIVATED) {
            CallEventEmitter.stopObserving(CallEvents.AUDIO_SESSION_ACTIVATED)
        }

        OnStartObserving(CallEvents.AUDIO_SESSION_DEACTIVATED) {
            CallEventEmitter.startObserving(CallEvents.AUDIO_SESSION_DEACTIVATED)
        }
        OnStopObserving(CallEvents.AUDIO_SESSION_DEACTIVATED) {
            CallEventEmitter.stopObserving(CallEvents.AUDIO_SESSION_DEACTIVATED)
        }

        OnStartObserving(CallEvents.AUDIO_ROUTE_CHANGED) {
            CallEventEmitter.startObserving(CallEvents.AUDIO_ROUTE_CHANGED)
        }
        OnStopObserving(CallEvents.AUDIO_ROUTE_CHANGED) {
            CallEventEmitter.stopObserving(CallEvents.AUDIO_ROUTE_CHANGED)
        }

        OnStartObserving(CallEvents.INCOMING_CALL_REPORTED) {
            CallEventEmitter.startObserving(CallEvents.INCOMING_CALL_REPORTED)
        }
        OnStopObserving(CallEvents.INCOMING_CALL_REPORTED) {
            CallEventEmitter.stopObserving(CallEvents.INCOMING_CALL_REPORTED)
        }

        OnStartObserving(CallEvents.OUTGOING_CALL_STARTED) {
            CallEventEmitter.startObserving(CallEvents.OUTGOING_CALL_STARTED)
        }
        OnStopObserving(CallEvents.OUTGOING_CALL_STARTED) {
            CallEventEmitter.stopObserving(CallEvents.OUTGOING_CALL_STARTED)
        }

        OnStartObserving(CallEvents.CALL_ANSWERED) {
            CallEventEmitter.startObserving(CallEvents.CALL_ANSWERED)
        }
        OnStopObserving(CallEvents.CALL_ANSWERED) {
            CallEventEmitter.stopObserving(CallEvents.CALL_ANSWERED)
        }

        OnStartObserving(CallEvents.CALL_ENDED) {
            CallEventEmitter.startObserving(CallEvents.CALL_ENDED)
        }
        OnStopObserving(CallEvents.CALL_ENDED) {
            CallEventEmitter.stopObserving(CallEvents.CALL_ENDED)
        }

        OnStartObserving(CallEvents.CALL_REPORTED_ENDED) {
            CallEventEmitter.startObserving(CallEvents.CALL_REPORTED_ENDED)
        }
        OnStopObserving(CallEvents.CALL_REPORTED_ENDED) {
            CallEventEmitter.stopObserving(CallEvents.CALL_REPORTED_ENDED)
        }

        OnStartObserving(CallEvents.SET_MUTED_ACTION) {
            CallEventEmitter.startObserving(CallEvents.SET_MUTED_ACTION)
        }
        OnStopObserving(CallEvents.SET_MUTED_ACTION) {
            CallEventEmitter.stopObserving(CallEvents.SET_MUTED_ACTION)
        }

        OnStartObserving(CallEvents.VIDEO_CHANGED) {
            CallEventEmitter.startObserving(CallEvents.VIDEO_CHANGED)
        }
        OnStopObserving(CallEvents.VIDEO_CHANGED) {
            CallEventEmitter.stopObserving(CallEvents.VIDEO_CHANGED)
        }

        OnStartObserving(CallEvents.SET_HELD_ACTION) {
            CallEventEmitter.startObserving(CallEvents.SET_HELD_ACTION)
        }
        OnStopObserving(CallEvents.SET_HELD_ACTION) {
            CallEventEmitter.stopObserving(CallEvents.SET_HELD_ACTION)
        }

        OnStartObserving(CallEvents.DTMF) { CallEventEmitter.startObserving(CallEvents.DTMF) }
        OnStopObserving(CallEvents.DTMF) { CallEventEmitter.stopObserving(CallEvents.DTMF) }

        OnStartObserving(CallEvents.CALL_INTENT_RECEIVED) {
            CallEventEmitter.startObserving(CallEvents.CALL_INTENT_RECEIVED)
        }
        OnStopObserving(CallEvents.CALL_INTENT_RECEIVED) {
            CallEventEmitter.stopObserving(CallEvents.CALL_INTENT_RECEIVED)
        }

        OnStartObserving(CallEvents.VOIP_PUSH_TOKEN_UPDATED) {
            CallEventEmitter.startObserving(CallEvents.VOIP_PUSH_TOKEN_UPDATED)
        }
        OnStopObserving(CallEvents.VOIP_PUSH_TOKEN_UPDATED) {
            CallEventEmitter.stopObserving(CallEvents.VOIP_PUSH_TOKEN_UPDATED)
        }

        // endregion

        // region Call Session

        // Returns the first active call session, if present.
        AsyncFunction("getActiveCallSession") { CallStore.firstSession()?.toMap() }

        // endregion

        // region Audio Session

        // Returns current audio session snapshot for diagnostics/UI state.
        Function("getAudioSessionState") { CallAudioManager.getAudioSessionState() }

        // Prepares audio session state before starting/reporting a call.
        Function("prepareAudioSessionForCall") { hasVideo: Boolean ->
            CallAudioManager.prepareAudioSessionForCall(hasVideo)
        }

        // Restores pre-call audio mode/route state.
        Function("restoreAudioSession") { CallAudioManager.restoreAudioSession() }

        // Overrides route to speaker (true) or clears override (false).
        Function("setAudioSessionPortOverride") { enabled: Boolean ->
            CallAudioManager.setAudioSessionPortOverride(enabled)
        }

        // endregion

        // region Capture Session

        // Returns capture session state (currently camera permission).
        Function("getCaptureSessionState") { CaptureSessionManager.getCaptureSessionState() }

        // endregion

        // region Start Outgoing Call

        // Starts a new outgoing call and returns the native call session ID.
        AsyncFunction("startOutgoingCall") {
            recipient: Map<String, Any?>,
            options: Map<String, Any?> ->
            val callId =
                CallManager.shared.startOutgoingCall(
                    recipient = CallParticipant.fromMap(recipient),
                    options = CallOptions(hasVideo = options["hasVideo"] as? Boolean ?: false),
                )
            callId
        }

        // endregion

        // region Report Incoming Call

        // Reports an incoming call to Telecom from app/push events.
        AsyncFunction("reportIncomingCall") { event: Map<String, Any?> ->
            CallManager.shared.reportIncomingCall(IncomingCallEvent.fromMap(event))
        }

        // endregion

        // region Answer Call

        // Answers an existing incoming call session.
        AsyncFunction("answerCall") { id: String ->
            CallManager.shared.answerCall(UUID.fromString(id))
        }

        // Fulfills pending incoming-call answer once media is connected.
        AsyncFunction("fulfillIncomingCallAnswered") { requestId: String ->
            CallManager.shared.fulfillIncomingCallConnected(UUID.fromString(requestId))
        }

        // Reports outgoing call media connection established.
        AsyncFunction("reportOutgoingCallConnected") { id: String ->
            CallManager.shared.reportOutgoingCallConnected(UUID.fromString(id))
        }

        // endregion

        // region End Call

        // Ends an active call.
        AsyncFunction("endCall") { id: String -> CallManager.shared.endCall(UUID.fromString(id)) }

        // Reports an externally-ended call with explicit reason.
        AsyncFunction("reportCallEnded") { id: String, reason: String ->
            CallManager.shared.reportCallEnded(
                UUID.fromString(id),
                CallEndedReason.fromValue(reason),
            )
        }

        // endregion

        // region Mute Support

        // Sets mute state for a call.
        AsyncFunction("setMuted") { id: String, muted: Boolean ->
            CallManager.shared.setMuted(UUID.fromString(id), muted)
        }

        // endregion

        // region Video Support

        // Reports call video enabled state changes.
        AsyncFunction("reportVideo") { id: String, enabled: Boolean ->
            CallManager.shared.reportVideo(UUID.fromString(id), enabled)
        }

        // endregion

        // region Hold Support

        // Sets call hold state.
        AsyncFunction("setHeld") { id: String, onHold: Boolean ->
            CallManager.shared.setHeld(UUID.fromString(id), onHold)
        }

        // endregion

        // region DTMF Support

        // Sends requested DTMF digits for a call.
        AsyncFunction("playDTMF") { id: String, digits: String ->
            CallManager.shared.playDTMF(UUID.fromString(id), digits)
        }

        // endregion

        // region VoIP Push

        // Registers for VoIP push by fetching the FCM token.
        Function("registerVoIPPush") { VoIPPushManager.register() }

        // Returns the current VoIP push token and its type.
        Function("getVoIPPushToken") { mapOf("token" to VoIPPushManager.token, "type" to "FCM") }

        // endregion
    }
}

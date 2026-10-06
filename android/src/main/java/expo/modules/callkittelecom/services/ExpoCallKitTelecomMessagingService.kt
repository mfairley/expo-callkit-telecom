package expo.modules.callkittelecom.services

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.messaging.RemoteMessage
import expo.modules.callkittelecom.managers.CallManager
import expo.modules.callkittelecom.managers.VoIPPushManager
import expo.modules.callkittelecom.models.CallEndedReason
import expo.modules.callkittelecom.models.IncomingCallEvent
import expo.modules.callkittelecom.store.CallStore
import expo.modules.notifications.service.ExpoFirebaseMessagingService
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Android FCM entry point for incoming call payloads.
 *
 * Extends expo-notifications' [ExpoFirebaseMessagingService] so that non-call messages are handled
 * by the existing notification delegate via [super], and call payloads are routed directly to
 * Telecom.
 *
 * Wire format (matches example/server/lib/fcm.ts): data["messageType"] names the push and
 * data[<messageType>] holds its JSON payload. Every call push payload carries a unique `eventId`,
 * which is used to drop duplicate deliveries.
 *
 * "incomingCall": the payload is the IncomingCallEvent (camelCase). The snake_case envelope
 * (messageType/key "incoming_call") is also accepted for backwards compatibility.
 *
 * "callEnded": the payload is {"eventId": "...", "serverCallId": "...", "reason"?: CallEndedReason}
 * and ends the session reported for that serverCallId, as if JS had called reportCallEnded. On a
 * killed app nothing else can: the ring is reported natively, so there is no JS observer, and the
 * phone would ring on until incomingCallTimeout. `reason` defaults to "remoteEnded".
 */
class ExpoCallKitTelecomMessagingService : ExpoFirebaseMessagingService() {
    companion object {
        private const val TAG = "ExpoCallKitTelecom.FCM"
        private const val KEY_MESSAGE_TYPE = "messageType"
        // Canonical camelCase envelope plus the snake_case form accepted for backwards
        // compatibility.
        private val MESSAGE_TYPE_INCOMING_CALL = setOf("incomingCall", "incoming_call")
        private val KEYS_INCOMING_CALL = listOf("incomingCall", "incoming_call")
        private val MESSAGE_TYPE_CALL_ENDED = setOf("callEnded", "call_ended")
        private val KEYS_CALL_ENDED = listOf("callEnded", "call_ended")
        private const val DEDUP_WINDOW_MS = 120_000L

        private val DEFAULT_CALL_ENDED_REASON = CallEndedReason.REMOTE_ENDED

        private val dedupeLock = Any()
        private val recentEventIds = ConcurrentHashMap<String, Long>()
    }

    /**
     * Routes call pushes to this module by `messageType`; everything else goes to
     * expo-notifications. A call push is always consumed here, even when malformed or a duplicate,
     * so it never surfaces as a notification.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        when (data[KEY_MESSAGE_TYPE]) {
            in MESSAGE_TYPE_INCOMING_CALL ->
                newCallPushPayload(data, KEYS_INCOMING_CALL)?.let(::handleIncomingCallPush)
            in MESSAGE_TYPE_CALL_ENDED ->
                newCallPushPayload(data, KEYS_CALL_ENDED)?.let(::handleCallEndedPush)
            else -> super.onMessageReceived(remoteMessage)
        }
    }

    override fun onNewToken(token: String) {
        VoIPPushManager.updateToken(token)

        // Let expo-notifications update its own token listeners.
        super.onNewToken(token)
    }

    // region Call Push Envelope

    /**
     * Parses a call push's JSON payload and checks its `eventId` against recent deliveries.
     *
     * @return the payload, or null when it's missing, malformed, has no `eventId`, or is a
     *   duplicate.
     */
    private fun newCallPushPayload(
        data: Map<String, String>,
        payloadKeys: List<String>,
    ): JSONObject? {
        val messageType = data[KEY_MESSAGE_TYPE]
        val nestedPayload = payloadKeys.firstNotNullOfOrNull { data[it] }
        if (nestedPayload == null) {
            Log.w(TAG, "Ignoring $messageType push without a payload")
            return null
        }

        val payload =
            try {
                JSONObject(nestedPayload)
            } catch (error: JSONException) {
                Log.w(TAG, "Failed to parse $messageType JSON payload: ${error.message}")
                return null
            }

        val eventId = (payload.opt("eventId") as? String)?.takeIf { it.isNotBlank() }
        if (eventId == null) {
            Log.w(TAG, "Ignoring $messageType push without an eventId")
            return null
        }

        if (!markEventAsNew(eventId)) {
            Log.d(TAG, "Dropping duplicate $messageType push - eventId: $eventId")
            return null
        }

        return payload
    }

    private fun markEventAsNew(eventId: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(dedupeLock) {
            recentEventIds.entries.removeIf { (_, seenAt) -> now - seenAt > DEDUP_WINDOW_MS }
            val seenAt = recentEventIds[eventId]
            if (seenAt != null && now - seenAt <= DEDUP_WINDOW_MS) {
                return false
            }
            recentEventIds[eventId] = now
            return true
        }
    }

    // endregion

    // region Incoming Call

    private fun handleIncomingCallPush(payload: JSONObject) {
        val eventMap = jsonObjectToMap(payload)
        Handler(Looper.getMainLooper()).post { processIncomingCall(eventMap) }
    }

    private fun processIncomingCall(eventMap: Map<String, Any?>) {
        try {
            CallManager.shared.initialize(applicationContext)

            // Wrap under the canonical envelope so we go through the same parser path as iOS.
            val event = IncomingCallEvent.fromPayload(mapOf("incomingCall" to eventMap))
            if (event == null) {
                Log.w(TAG, "Failed to validate incoming call event from FCM payload")
                return
            }

            CallManager.shared.reportIncomingCall(event)
            Log.d(TAG, "Reported incoming call from FCM payload")
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Ignoring incoming call push while another session exists: ${error.message}")
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to process incoming call push: ${error.message}", error)
        }
    }

    // endregion

    // region Call Ended

    private fun handleCallEndedPush(payload: JSONObject) {
        val push = parseCallEndedPush(payload) ?: return
        Handler(Looper.getMainLooper()).post { processCallEnded(push) }
    }

    private fun parseCallEndedPush(payload: JSONObject): CallEndedPush? {
        val serverCallId = (payload.opt("serverCallId") as? String)?.takeIf { it.isNotBlank() }
        if (serverCallId == null) {
            Log.w(TAG, "Ignoring call-ended push without a serverCallId")
            return null
        }

        return CallEndedPush(serverCallId, parseCallEndedReason(payload.opt("reason") as? String))
    }

    /** Maps the optional push reason, falling back to the default when absent or unrecognized. */
    private fun parseCallEndedReason(value: String?): CallEndedReason {
        if (value == null) return DEFAULT_CALL_ENDED_REASON

        return CallEndedReason.fromValueOrNull(value)
            ?: DEFAULT_CALL_ENDED_REASON.also {
                Log.w(TAG, "Unknown call-ended reason \"$value\", using ${it.value}")
            }
    }

    /**
     * Ends the session reported for the push's serverCallId, so a call the caller has abandoned
     * stops ringing at once rather than running to the timeout.
     */
    private fun processCallEnded(push: CallEndedPush) {
        try {
            CallManager.shared.initialize(applicationContext)

            val session = CallStore.sessionForServerCallId(push.serverCallId)
            if (session == null) {
                Log.d(TAG, "Ignoring call-ended push with no matching session")
                return
            }

            CallManager.shared.reportCallEnded(session.id, push.reason)
            Log.d(TAG, "Reported call ended from FCM payload - reason: ${push.reason.value}")
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to process call-ended push: ${error.message}", error)
        }
    }

    // endregion

    // region JSON Helpers

    private fun jsonObjectToMap(jsonObject: JSONObject): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val iterator = jsonObject.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            result[key] = jsonValueToAny(jsonObject.opt(key))
        }
        return result
    }

    private fun jsonArrayToList(array: JSONArray): List<Any?> {
        val result = mutableListOf<Any?>()
        for (index in 0 until array.length()) {
            result.add(jsonValueToAny(array.opt(index)))
        }
        return result
    }

    private fun jsonValueToAny(value: Any?): Any? =
        when (value) {
            null,
            JSONObject.NULL -> null

            is JSONObject -> jsonObjectToMap(value)

            is JSONArray -> jsonArrayToList(value)

            else -> value
        }

    // endregion
}

/** A validated call-ended push: which backend call to end, and why. */
private data class CallEndedPush(val serverCallId: String, val reason: CallEndedReason)

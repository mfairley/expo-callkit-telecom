package expo.modules.callkittelecom.services

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.messaging.RemoteMessage
import expo.modules.callkittelecom.managers.CallManager
import expo.modules.callkittelecom.managers.VoIPPushManager
import expo.modules.callkittelecom.models.IncomingCallEvent
import expo.modules.notifications.service.ExpoFirebaseMessagingService
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Android FCM entry point for incoming call payloads.
 *
 * Extends expo-notifications' [ExpoFirebaseMessagingService] so that non-call
 * messages are handled by the existing notification delegate via [super], and
 * call payloads are routed directly to Telecom.
 *
 * Wire format (matches example/server/lib/fcm.ts):
 *   data["messageType"]   = "incoming_call"
 *   data["incoming_call"] = JSON string of the IncomingCallEvent (camelCase)
 */
class ExpoCallKitTelecomMessagingService : ExpoFirebaseMessagingService() {
    companion object {
        private const val TAG = "ExpoCallKitTelecom.FCM"
        private const val KEY_MESSAGE_TYPE = "messageType"
        private const val MESSAGE_TYPE_INCOMING_CALL = "incoming_call"
        private const val KEY_INCOMING_CALL = "incoming_call"
        private const val DEDUP_WINDOW_MS = 120_000L

        private val dedupeLock = Any()
        private val recentMessages = ConcurrentHashMap<String, Long>()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data

        // Try to parse as an incoming call payload.
        val eventMap = if (data.isNotEmpty()) parseIncomingCallEvent(data) else null
        if (eventMap == null) {
            // Not a call push — let expo-notifications handle it.
            super.onMessageReceived(message)
            return
        }

        val dedupeKey = dedupeKey(eventMap) ?: return
        if (!markMessageAsNew(dedupeKey)) {
            Log.d(TAG, "Dropping duplicate incoming call push - key: $dedupeKey")
            return
        }

        Handler(Looper.getMainLooper()).post {
            processIncomingCall(eventMap)
        }
    }

    override fun onNewToken(token: String) {
        VoIPPushManager.updateToken(token)

        // Let expo-notifications update its own token listeners.
        super.onNewToken(token)
    }

    private fun processIncomingCall(eventMap: Map<String, Any?>) {
        try {
            CallManager.shared.initialize(applicationContext)

            // Wrap under the envelope so we go through the same parser path as iOS.
            val event = IncomingCallEvent.fromPayload(mapOf(KEY_INCOMING_CALL to eventMap))
            if (event == null) {
                Log.w(TAG, "Failed to validate incoming call event from FCM payload")
                return
            }

            CallManager.shared.reportIncomingCall(event)
            Log.d(TAG, "Reported incoming call from FCM payload")
        } catch (error: IllegalStateException) {
            Log.w(
                TAG,
                "Ignoring incoming call push while another session exists: ${error.message}",
            )
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to process incoming call push: ${error.message}", error)
        }
    }

    private fun parseIncomingCallEvent(data: Map<String, String>): Map<String, Any?>? {
        if (data[KEY_MESSAGE_TYPE] != MESSAGE_TYPE_INCOMING_CALL) {
            return null
        }

        val nestedPayload = data[KEY_INCOMING_CALL] ?: return null
        return try {
            jsonObjectToMap(JSONObject(nestedPayload))
        } catch (error: Throwable) {
            Log.w(TAG, "Failed to parse incoming_call JSON payload: ${error.message}")
            null
        }
    }

    private fun dedupeKey(eventMap: Map<String, Any?>): String? {
        val eventId = eventMap["eventId"] as? String
        if (!eventId.isNullOrBlank()) {
            return "event:$eventId"
        }

        val serverCallId = eventMap["serverCallId"] as? String
        if (!serverCallId.isNullOrBlank()) {
            return "call:$serverCallId"
        }

        return null
    }

    private fun markMessageAsNew(key: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(dedupeLock) {
            recentMessages.entries.removeIf { (_, seenAt) -> now - seenAt > DEDUP_WINDOW_MS }
            val seenAt = recentMessages[key]
            if (seenAt != null && now - seenAt <= DEDUP_WINDOW_MS) {
                return false
            }
            recentMessages[key] = now
            return true
        }
    }

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
            JSONObject.NULL,
            -> null

            is JSONObject -> jsonObjectToMap(value)

            is JSONArray -> jsonArrayToList(value)

            else -> value
        }
}

package expo.modules.callkittelecom.managers

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import expo.modules.callkittelecom.events.CallEventEmitter
import expo.modules.callkittelecom.events.CallEvents
import expo.modules.callkittelecom.utils.CallKitTelecomLog

/**
 * Manages FCM push token registration and storage.
 *
 * This singleton mirrors iOS's VoIPPushManager and handles:
 * - Registering for FCM push tokens
 * - Storing and exposing the current push token
 * - Emitting events when the token updates
 */
object VoIPPushManager {
    private const val TAG = "ExpoCallKitTelecom.VoIPPush"

    /** The current FCM push token, if available. */
    @Volatile
    var token: String? = null
        private set

    /** Registers for FCM push tokens by fetching the current token. */
    fun register() {
        FirebaseMessaging.getInstance()
            .token
            .addOnSuccessListener { newToken -> updateToken(newToken) }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to get FCM token: ${error.message}", error)
            }
    }

    /**
     * Updates the stored token and emits an event to JS.
     *
     * @param newToken The new token string, or null if invalidated.
     */
    fun updateToken(newToken: String?) {
        val oldToken = token
        token = newToken

        if (oldToken != newToken) {
            CallKitTelecomLog.d(TAG) { "VoIP token updated - hasToken: ${newToken != null}" }
            CallEventEmitter.send(
                CallEvents.VOIP_PUSH_TOKEN_UPDATED,
                mapOf("token" to newToken, "type" to "FCM"),
            )
        }
    }
}

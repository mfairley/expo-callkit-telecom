package expo.modules.callkittelecom.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import expo.modules.callkittelecom.managers.CallManager
import expo.modules.callkittelecom.utils.CallKitTelecomLog
import java.util.UUID

/**
 * Handles decline actions from call notifications.
 *
 * Answer actions use PendingIntent.getActivity() to bring the app to the foreground directly
 * (required on Android 12+), and are handled by [ExpoCallKitTelecomModule.OnNewIntent]. This
 * receiver only handles decline.
 */
class CallNotificationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ExpoCallKitTelecom.NotifReceiver"

        const val ACTION_ANSWER = "expo.modules.callkittelecom.ACTION_ANSWER"
        const val ACTION_DECLINE = "expo.modules.callkittelecom.ACTION_DECLINE"
        const val EXTRA_CALL_ID = "expo.modules.callkittelecom.EXTRA_CALL_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val callIdStr = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val callId =
            try {
                UUID.fromString(callIdStr)
            } catch (_: IllegalArgumentException) {
                CallKitTelecomLog.e(TAG) { "Invalid call ID in notification action: $callIdStr" }
                return
            }

        when (intent.action) {
            ACTION_ANSWER -> {
                // Answer action is handled by OnNewIntent in ExpoCallKitTelecomModule
                // since the PendingIntent launches the Activity directly.
                // This branch only fires for legacy broadcast-based answer actions.
                CallKitTelecomLog.d(TAG) {
                    "Notification answer action (broadcast) - callId: $callId"
                }
                CallManager.shared.answerCall(callId)
            }

            ACTION_DECLINE -> {
                CallKitTelecomLog.d(TAG) { "Notification decline action - callId: $callId" }
                CallManager.shared.endCall(callId)
            }
        }
    }
}

package expo.modules.callkittelecom.services

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import expo.modules.callkittelecom.IncomingCallActivity
import expo.modules.callkittelecom.LockedCallKeepAlive
import expo.modules.callkittelecom.managers.CallManager
import expo.modules.callkittelecom.managers.CallNotificationManager
import expo.modules.callkittelecom.store.CallStore
import expo.modules.callkittelecom.utils.CallKitTelecomLog
import java.util.UUID

/**
 * Handles decline actions from call notifications, and answer actions taken on the lock screen.
 *
 * Answer actions otherwise use PendingIntent.getActivity() to bring the app to the foreground
 * directly (required on Android 12+), and are handled by [ExpoCallKitTelecomModule.OnNewIntent].
 * With `androidAnswerOnLockScreen` on, a ring posted while the phone is locked answers through this
 * receiver instead (see [CallNotificationManager]), since SystemUI asks for the PIN before it sends
 * an action button's activity PendingIntent.
 *
 * Starting an activity from here is a notification trampoline, which Android 12+ blocks for apps
 * targeting it — unless the app has a visible window, as it does while the call screen shows the
 * ring over the lock screen. Where the start is refused (the ring was a heads-up only), the call is
 * answered all the same and a "Connecting…" notification stands in for the ring until the app
 * fulfils; its tap, and the ongoing notification's after that, opens the call screen.
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
                CallKitTelecomLog.d(TAG) {
                    "Notification answer action (broadcast) - callId: $callId"
                }
                val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (!CallNotificationManager.answerOnLockScreenEnabled) {
                    // Answer action is handled by OnNewIntent in ExpoCallKitTelecomModule
                    // since the PendingIntent launches the Activity directly.
                    // This branch only fires for legacy broadcast-based answer actions.
                    CallManager.shared.answerCall(callId)
                } else if (keyguard.isDeviceLocked && LockedCallKeepAlive.canKeepAlive(context)) {
                    answerOnLockScreen(context, callId)
                } else {
                    // Posted locked but taken unlocked (the phone woke without a lock, or the
                    // user unlocked before the ring was re-posted), or the app's JS is gone:
                    // answer and open the app. Android may refuse the start from a notification
                    // action's receiver; the call is answered either way, and the "Connecting…"
                    // notification (then the ongoing one) opens the app on tap.
                    CallManager.shared.answerCall(callId)
                    CallNotificationManager.showConnectingCall(
                        context,
                        callId,
                        callerName(callId),
                        onLockScreen = false,
                    )
                    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                        it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startFromReceiver(context, it, "the app")
                    }
                }
            }

            ACTION_DECLINE -> {
                CallKitTelecomLog.d(TAG) { "Notification decline action - callId: $callId" }
                CallManager.shared.endCall(callId)
            }
        }
    }

    /**
     * Answers on the lock screen like the call screen's own Answer. The keep-alive holds the call
     * before the answer: the answer moves the session to CONNECTING, and a call screen already
     * showing the ring would otherwise take that for "answered elsewhere" and close, where it now
     * adopts the call ([IncomingCallActivity.adoptLockedAnswer]). The answer takes the ring
     * notification down, so a "Connecting…" one is posted in its place before the call screen is
     * shown — a no-op start when it is already up (singleInstance; see its onNewIntent).
     */
    private fun answerOnLockScreen(context: Context, callId: UUID) {
        LockedCallKeepAlive.hold(context, callId)
        CallManager.shared.answerCall(callId)
        CallNotificationManager.showConnectingCall(
            context,
            callId,
            callerName(callId),
            onLockScreen = true,
        )
        startFromReceiver(
            context,
            IncomingCallActivity.answeredOnLockScreenIntent(context, callId),
            "the call screen",
        )
    }

    /**
     * Starts [intent] from this receiver, a notification trampoline. Android 12+ refuses it
     * silently when the app has no visible window (nothing is thrown; the start is dropped with a
     * system log line), so the attempt is logged here and the outcome is read off logcat — the
     * "Connecting…" notification is the way to [what] when the start was refused.
     */
    private fun startFromReceiver(context: Context, intent: Intent, what: String) {
        CallKitTelecomLog.d(TAG) {
            "Starting $what from the notification action; refused by Android unless the app has " +
                "a visible window"
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            CallKitTelecomLog.e(TAG, e) { "Starting $what from the notification action failed" }
        }
    }

    private fun callerName(callId: UUID): String? =
        CallStore.session(callId)?.remoteParticipants?.firstOrNull()?.displayNameOrHandle
}

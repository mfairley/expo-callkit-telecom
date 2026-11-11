package expo.modules.callkittelecom

import android.content.Context
import android.content.Intent
import expo.modules.callkittelecom.managers.CallManager
import expo.modules.callkittelecom.services.CallNotificationReceiver
import expo.modules.callkittelecom.store.CallStore
import expo.modules.callkittelecom.utils.CallKitTelecomLog
import java.util.UUID

/**
 * Answers a call when Answer is tapped on the incoming-call notification.
 *
 * That button opens MainActivity with [CallNotificationReceiver.ACTION_ANSWER]. This is called from
 * the activity's own lifecycle ([AnswerIntentListener]), not from the Expo module: when an FCM push
 * started the process, the module can exist before the activity does, or not exist yet at all, so
 * a check there can miss the intent and the call rings on until it is filed as missed. Answering
 * needs only the native call state, so React does not have to be running.
 */
internal object AnswerFromNotification {
    private const val TAG = "AnswerFromNotification"

    fun handle(context: Context, intent: Intent?, from: String) {
        if (intent?.action != CallNotificationReceiver.ACTION_ANSWER) return
        val callId = intent.getStringExtra(CallNotificationReceiver.EXTRA_CALL_ID)?.toUuidOrNull() ?: return

        // Clear the action so a configuration change or a later resume does not answer again.
        intent.action = null

        // Recents can re-deliver an old launch intent. Answer only a call this process holds:
        // answerCall cancels the call notification first, which could be another call's ring.
        if (CallStore.session(callId) == null) {
            CallKitTelecomLog.d(TAG) { "Ignoring answer intent for unknown call - id: $callId ($from)" }
            return
        }
        CallKitTelecomLog.d(TAG) { "Answering from notification - id: $callId ($from)" }
        CallManager.shared.initialize(context)
        CallManager.shared.answerCall(callId)
    }

    private fun String.toUuidOrNull(): UUID? =
        try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            null
        }
}

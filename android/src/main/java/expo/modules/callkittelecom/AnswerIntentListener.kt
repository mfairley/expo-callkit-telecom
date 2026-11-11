package expo.modules.callkittelecom

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import expo.modules.core.interfaces.ReactActivityLifecycleListener

/**
 * Sees every intent that opens MainActivity, before React has started.
 *
 * Cold start: the Answer intent is the launch intent and arrives in [onCreate].
 * App already open: it arrives in [onNewIntent].
 */
internal class AnswerIntentListener : ReactActivityLifecycleListener {
    /** The application context, taken in [onCreate]: the activity has none when this is built. */
    private var context: Context? = null

    override fun onCreate(activity: Activity?, savedInstanceState: Bundle?) {
        val app = activity?.applicationContext ?: return
        context = app
        AnswerFromNotification.handle(app, activity.intent, "create")
    }

    override fun onNewIntent(intent: Intent?): Boolean {
        context?.let { AnswerFromNotification.handle(it, intent, "newIntent") }
        return false
    }
}

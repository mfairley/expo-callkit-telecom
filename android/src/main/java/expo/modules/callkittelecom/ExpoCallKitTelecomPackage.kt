package expo.modules.callkittelecom

import android.content.Context
import expo.modules.core.interfaces.Package
import expo.modules.core.interfaces.ReactActivityLifecycleListener

/** Found by Expo autolinking (a `*Package.kt` that imports `Package`); hooks MainActivity's lifecycle. */
class ExpoCallKitTelecomPackage : Package {
    /**
     * Called from MainActivity's CONSTRUCTOR, before the activity has a base context: touching
     * [activityContext] here, even `applicationContext`, throws and the app cannot start.
     * The listener takes its context from the activity in `onCreate` instead.
     */
    override fun createReactActivityLifecycleListeners(
        activityContext: Context?
    ): List<ReactActivityLifecycleListener> = listOf(AnswerIntentListener())
}

package expo.modules.callkittelecom.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * Debug-only logging wrapper for expo-calls.
 *
 * All log calls are no-ops in release builds (where `FLAG_DEBUGGABLE` is unset),
 * eliminating both the I/O cost and the string-building cost of log messages.
 * The `msg` parameter is an `inline` lambda so the string is never allocated
 * when logging is disabled.
 */
object CallKitTelecomLog {
    @Volatile
    @PublishedApi
    internal var enabled = false
        private set

    /** Call once during module/manager init to auto-detect debuggable flag. */
    fun init(context: Context) {
        enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /** Allows tests or developer settings to override the auto-detected value. */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    inline fun d(
        tag: String,
        msg: () -> String,
    ) {
        if (enabled) Log.d(tag, msg())
    }

    inline fun w(
        tag: String,
        msg: () -> String,
    ) {
        if (enabled) Log.w(tag, msg())
    }

    inline fun e(
        tag: String,
        tr: Throwable? = null,
        msg: () -> String,
    ) {
        if (enabled) Log.e(tag, msg(), tr)
    }
}

package expo.modules.callkittelecom.managers

import android.content.Context
import expo.modules.callkittelecom.utils.PermissionUtils

/**
 * Reports capture-related state exposed through the shared JS API.
 *
 * Android parity currently focuses on camera permission status.
 */
object CaptureSessionManager {
    private lateinit var context: Context
    private var isInitialized = false

    /** Initializes manager with application context. Safe to call repeatedly. */
    fun initialize(appContext: Context) {
        if (isInitialized) return
        context = appContext.applicationContext
        isInitialized = true
    }

    /** Returns capture session state matching the TypeScript `CaptureSession` shape. */
    fun getCaptureSessionState(): Map<String, Any?> =
        mapOf("cameraPermission" to PermissionUtils.cameraPermission(context))
}

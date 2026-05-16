package expo.modules.callkittelecom.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Utility helpers that map Android runtime permission checks to shared string statuses. */
object PermissionUtils {
    /** Returns `granted` or `denied` for a specific Android permission. */
    private fun permissionStatus(context: Context, permission: String): String =
        if (
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            "granted"
        } else {
            "denied"
        }

    /** Returns microphone permission status for `RECORD_AUDIO`. */
    fun microphonePermission(context: Context): String =
        permissionStatus(context, Manifest.permission.RECORD_AUDIO)

    /** Returns camera permission status for `CAMERA`. */
    fun cameraPermission(context: Context): String =
        permissionStatus(context, Manifest.permission.CAMERA)
}

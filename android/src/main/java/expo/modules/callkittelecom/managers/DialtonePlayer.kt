package expo.modules.callkittelecom.managers

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import expo.modules.callkittelecom.utils.CallKitTelecomLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Plays the dialtone sound during outgoing call connection.
 *
 * Reads the dialtone resource name from AndroidManifest metadata
 * (`ExpoCallKitTelecomDefaultDialtone`) and plays it in a loop with a fade-in
 * until stopped.
 */
object DialtonePlayer {
    private const val TAG = "ExpoCallKitTelecom.Dialtone"
    private const val KEY_DEFAULT_DIALTONE = "ExpoCallKitTelecomDefaultDialtone"

    /** Delay before starting playback to let audio session settle (in ms). */
    private const val START_DELAY_MS = 50L

    /** Duration of volume fade-in (in ms). */
    private const val FADE_IN_DURATION_MS = 100L
    private const val FADE_STEPS = 10

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mutex = Mutex()

    private var player: MediaPlayer? = null
    private var fadeJob: Job? = null
    private var rawResourceId: Int = 0
    private var isInitialized = false

    /** Whether a dialtone resource is configured in the manifest. */
    val hasDialtone: Boolean
        get() = rawResourceId != 0

    /** Reads dialtone config from AndroidManifest metadata. Safe to call repeatedly. */
    fun initialize(context: Context) {
        if (isInitialized) return

        val appContext = context.applicationContext
        val dialtoneFilename =
            try {
                val appInfo =
                    appContext.packageManager.getApplicationInfo(
                        appContext.packageName,
                        PackageManager.GET_META_DATA,
                    )
                appInfo.metaData?.getString(KEY_DEFAULT_DIALTONE)
            } catch (_: Throwable) {
                null
            }

        if (dialtoneFilename == null) {
            CallKitTelecomLog.d(TAG) { "No dialtone configured in manifest" }
            isInitialized = true
            return
        }

        // The plugin writes the sanitized resource name into the manifest,
        // so we can use it directly for the resource lookup.
        rawResourceId = appContext.resources.getIdentifier(dialtoneFilename, "raw", appContext.packageName)
        if (rawResourceId == 0) {
            CallKitTelecomLog.e(TAG) { "Dialtone raw resource not found: $dialtoneFilename" }
        } else {
            CallKitTelecomLog.d(TAG) { "Initialized dialtone: $dialtoneFilename (resId=$rawResourceId)" }
        }

        isInitialized = true
    }

    /** Starts playing the dialtone sound in a loop with fade-in. */
    fun play(context: Context) {
        if (!hasDialtone) {
            CallKitTelecomLog.d(TAG) { "No dialtone configured, skipping playback" }
            return
        }

        if (!CallAudioManager.isActive) {
            CallKitTelecomLog.d(TAG) { "Audio session not active, skipping dialtone" }
            return
        }

        scope.launch {
            mutex.withLock {
                if (player != null) {
                    CallKitTelecomLog.d(TAG) { "Dialtone already playing" }
                    return@withLock
                }

                try {
                    val mp = MediaPlayer.create(context.applicationContext, rawResourceId)
                    if (mp == null) {
                        CallKitTelecomLog.e(TAG) { "Failed to create MediaPlayer for dialtone" }
                        return@withLock
                    }

                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                    player = mp

                    // Brief delay to let audio session settle
                    delay(START_DELAY_MS)

                    if (player == mp) {
                        mp.start()
                        fadeIn()
                        CallKitTelecomLog.d(TAG) { "Started playing dialtone" }
                    }
                } catch (e: Throwable) {
                    CallKitTelecomLog.e(TAG) { "Failed to play dialtone: ${e.localizedMessage}" }
                    player?.release()
                    player = null
                }
            }
        }
    }

    /** Fades in the volume from 0 to 1 over FADE_IN_DURATION_MS. */
    private fun fadeIn() {
        fadeJob?.cancel()
        val stepDuration = FADE_IN_DURATION_MS / FADE_STEPS
        val volumeStep = 1.0f / FADE_STEPS

        fadeJob =
            scope.launch {
                for (step in 1..FADE_STEPS) {
                    delay(stepDuration)
                    val volume = volumeStep * step
                    player?.setVolume(volume, volume)
                }
                fadeJob = null
            }
    }

    /** Stops playing the dialtone sound. */
    fun stop() {
        scope.launch {
            mutex.withLock {
                fadeJob?.cancel()
                fadeJob = null

                val mp = player ?: return@withLock
                player = null

                try {
                    if (mp.isPlaying) {
                        mp.stop()
                    }
                    mp.release()
                    CallKitTelecomLog.d(TAG) { "Stopped playing dialtone" }
                } catch (e: Throwable) {
                    CallKitTelecomLog.e(TAG) { "Error stopping dialtone: ${e.localizedMessage}" }
                }
            }
        }
    }

    /** Whether the dialtone is currently playing. */
    val isPlaying: Boolean
        get() = player?.isPlaying == true
}

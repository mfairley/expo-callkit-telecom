package expo.modules.callkittelecom.managers

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.core.telecom.CallEndpointCompat
import expo.modules.callkittelecom.events.CallEventEmitter
import expo.modules.callkittelecom.events.CallEvents
import expo.modules.callkittelecom.models.CallSession
import expo.modules.callkittelecom.utils.CallKitTelecomLog
import expo.modules.callkittelecom.utils.PermissionUtils

/**
 * Manages Android call audio state and routing for the shared calls API.
 *
 * Audio focus and mode are managed by Core-Telecom. This manager tracks endpoint state, emits route
 * changes to JS, and requests endpoint switches via the active call scope.
 */
object CallAudioManager {
    private const val TAG = "ExpoCallKitTelecom.Audio"

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager

    private var isInitialized = false
    internal var isActive = false
    private var configuredForVideo = false

    /** Callback to request endpoint change on the active Core-Telecom call scope. */
    internal var onRequestEndpointChange: ((CallEndpointCompat) -> Unit)? = null

    /** Last known active endpoint from Core-Telecom. */
    private var currentEndpoint: CallEndpointCompat? = null

    /** Last known available endpoints from Core-Telecom. */
    internal var currentAvailableEndpoints: List<CallEndpointCompat> = emptyList()

    private var routeCallback: AudioDeviceCallback? = null

    /** Initializes audio manager and route-change observation. Safe to call repeatedly. */
    fun initialize(appContext: Context) {
        if (isInitialized) return

        context = appContext.applicationContext
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        routeCallback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                    CallKitTelecomLog.d(TAG) { "Audio devices added - count: ${addedDevices.size}" }
                    // During active calls, Core-Telecom handles routing via endpoint Flows
                    if (!isActive) emitRouteChanged()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                    CallKitTelecomLog.d(TAG) {
                        "Audio devices removed - count: ${removedDevices.size}"
                    }
                    if (!isActive) emitRouteChanged()
                }
            }

        routeCallback?.let { callback -> audioManager.registerAudioDeviceCallback(callback, null) }

        isInitialized = true
        CallKitTelecomLog.d(TAG) { "Initialized CallAudioManager" }
    }

    /** Receives Core-Telecom current endpoint updates from the active call scope. */
    fun onEndpointChanged(endpoint: CallEndpointCompat) {
        currentEndpoint = endpoint
        emitRouteChanged()
    }

    /** Receives Core-Telecom available endpoint updates from the active call scope. */
    fun onAvailableEndpointsChanged(endpoints: List<CallEndpointCompat>) {
        currentAvailableEndpoints = endpoints
        emitRouteChanged()
    }

    /** Returns current audio session state in the shared TypeScript shape. */
    fun getAudioSessionState(): Map<String, Any?> {
        val sampleRate =
            audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toDoubleOrNull()
        val framesPerBuffer =
            audioManager
                .getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
                ?.toDoubleOrNull()
        val ioBufferDuration =
            if (sampleRate != null && framesPerBuffer != null) framesPerBuffer / sampleRate
            else null

        val currentRoute = currentRouteMap()

        return mapOf(
            "isActive" to isActive,
            "isOtherAudioPlaying" to audioManager.isMusicActive,
            "category" to "communication",
            "mode" to modeString(audioManager.mode),
            "sampleRate" to sampleRate,
            "ioBufferDuration" to ioBufferDuration,
            "inputNumberOfChannels" to 1,
            "outputNumberOfChannels" to 2,
            "microphonePermission" to PermissionUtils.microphonePermission(context),
            "currentRoute" to currentRoute,
            "availableRoutes" to availableRoutesMap(),
        )
    }

    /** Records whether the upcoming call uses video (affects default endpoint selection). */
    fun prepareAudioSessionForCall(hasVideo: Boolean) {
        CallKitTelecomLog.d(TAG) { "Preparing audio session for call - hasVideo: $hasVideo" }
        configuredForVideo = hasVideo
    }

    /** No-op on Android — Core-Telecom manages audio mode and focus. Kept for JS API compat. */
    fun restoreAudioSession() {
        CallKitTelecomLog.d(TAG) {
            "restoreAudioSession is a no-op on Android (Core-Telecom manages audio)"
        }
    }

    /**
     * Marks audio session as active and emits `onAudioSessionActivated`.
     *
     * Core-Telecom handles audio focus and mode automatically.
     */
    fun onAudioActivated(calls: List<CallSession>) {
        if (!isInitialized) return

        CallKitTelecomLog.d(TAG) { "Activating audio session - calls: ${calls.size}" }

        isActive = true

        val callInfos = calls.map { mapOf("id" to it.id.toString(), "status" to it.status.value) }

        CallEventEmitter.send(CallEvents.AUDIO_SESSION_ACTIVATED, mapOf("calls" to callInfos))
    }

    /**
     * Resets audio state after final call teardown and emits `onAudioSessionDeactivated`.
     *
     * Core-Telecom releases audio focus and restores mode when the call scope ends.
     */
    fun onAudioDeactivated(calls: List<CallSession>) {
        if (!isInitialized) return

        DialtonePlayer.stop()
        CallKitTelecomLog.d(TAG) { "Deactivating audio session - calls: ${calls.size}" }

        currentEndpoint = null
        currentAvailableEndpoints = emptyList()
        isActive = false

        emitRouteChanged()

        val callInfos = calls.map { mapOf("id" to it.id.toString(), "status" to it.status.value) }

        CallEventEmitter.send(CallEvents.AUDIO_SESSION_DEACTIVATED, mapOf("calls" to callInfos))
    }

    /** Requests endpoint change to speaker (`true`) or best non-speaker device (`false`). */
    fun setAudioSessionPortOverride(enabled: Boolean) {
        CallKitTelecomLog.d(TAG) { "Setting speaker - enabled: $enabled" }

        val endpoint =
            if (enabled) {
                currentAvailableEndpoints.firstOrNull { it.type == CallEndpointCompat.TYPE_SPEAKER }
            } else {
                selectBestNonSpeakerEndpoint()
            }

        if (endpoint != null) {
            onRequestEndpointChange?.invoke(endpoint)
        }
    }

    /**
     * Selects the best non-speaker endpoint matching iOS priority: Bluetooth > Wired Headset >
     * Earpiece (audio) / Speaker (video).
     */
    private fun selectBestNonSpeakerEndpoint(): CallEndpointCompat? {
        val available = currentAvailableEndpoints

        available
            .firstOrNull { it.type == CallEndpointCompat.TYPE_BLUETOOTH }
            ?.let {
                return it
            }
        available
            .firstOrNull { it.type == CallEndpointCompat.TYPE_WIRED_HEADSET }
            ?.let {
                return it
            }

        return if (configuredForVideo) {
            available.firstOrNull { it.type == CallEndpointCompat.TYPE_SPEAKER }
        } else {
            available.firstOrNull { it.type == CallEndpointCompat.TYPE_EARPIECE }
        }
    }

    /** Emits the current route and available routes snapshot to JS listeners. */
    private fun emitRouteChanged() {
        CallEventEmitter.send(
            CallEvents.AUDIO_ROUTE_CHANGED,
            mapOf("currentRoute" to currentRouteMap(), "availableRoutes" to availableRoutesMap()),
        )
    }

    /**
     * Converts Core-Telecom available endpoints into JS-facing AudioPort objects. Each endpoint
     * maps to a single output port.
     */
    private fun availableRoutesMap(): List<Map<String, String>> =
        currentAvailableEndpoints.mapNotNull { endpoint -> resolveOutputPort(endpoint) }

    /**
     * Builds the current route payload with the single active input/output device.
     *
     * During an active call, uses Core-Telecom's [CallEndpointCompat] to determine the active
     * device (matching iOS's `AVAudioSession.currentRoute` behavior). When no call is active, falls
     * back to built-in defaults.
     */
    private fun currentRouteMap(): Map<String, Any?> {
        val endpoint = currentEndpoint
        if (endpoint != null) {
            val output = resolveOutputPort(endpoint)
            val input = resolveInputPort(endpoint.type)
            return mapOf("inputs" to listOfNotNull(input), "outputs" to listOfNotNull(output))
        }

        val input =
            findDevicePort(AudioManager.GET_DEVICES_INPUTS, AudioDeviceInfo.TYPE_BUILTIN_MIC)
        val output =
            findDevicePort(AudioManager.GET_DEVICES_OUTPUTS, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
        return mapOf("inputs" to listOfNotNull(input), "outputs" to listOfNotNull(output))
    }

    /** Maps Core-Telecom endpoint to a port map for the JS layer. */
    private fun resolveOutputPort(endpoint: CallEndpointCompat): Map<String, String>? =
        when (endpoint.type) {
            CallEndpointCompat.TYPE_SPEAKER -> {
                findDevicePort(
                    AudioManager.GET_DEVICES_OUTPUTS,
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                )
            }

            CallEndpointCompat.TYPE_EARPIECE -> {
                findDevicePort(
                    AudioManager.GET_DEVICES_OUTPUTS,
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                )
            }

            CallEndpointCompat.TYPE_BLUETOOTH -> {
                mapOf(
                    "portType" to "bluetoothHFP",
                    "portName" to endpoint.name.toString(),
                    "uid" to endpoint.identifier.toString(),
                )
            }

            CallEndpointCompat.TYPE_WIRED_HEADSET -> {
                findDevicePort(AudioManager.GET_DEVICES_OUTPUTS, AudioDeviceInfo.TYPE_WIRED_HEADSET)
                    ?: findDevicePort(
                        AudioManager.GET_DEVICES_OUTPUTS,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    )
            }

            else -> {
                null
            }
        }

    /** Infers the active input device based on the endpoint type. */
    private fun resolveInputPort(endpointType: Int): Map<String, String>? {
        val inputType =
            when (endpointType) {
                CallEndpointCompat.TYPE_BLUETOOTH -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                CallEndpointCompat.TYPE_WIRED_HEADSET -> AudioDeviceInfo.TYPE_WIRED_HEADSET
                else -> AudioDeviceInfo.TYPE_BUILTIN_MIC
            }
        return findDevicePort(AudioManager.GET_DEVICES_INPUTS, inputType)
            ?: findDevicePort(AudioManager.GET_DEVICES_INPUTS, AudioDeviceInfo.TYPE_BUILTIN_MIC)
    }

    /** Finds the first matching device by type and returns its port map. */
    private fun findDevicePort(direction: Int, type: Int): Map<String, String>? =
        audioManager.getDevices(direction).firstOrNull { it.type == type }?.let { portMap(it) }

    /** Serializes an Android audio device into the shared audio port payload shape. */
    private fun portMap(info: AudioDeviceInfo): Map<String, String> =
        mapOf(
            "portType" to portTypeString(info.type),
            "portName" to info.productName?.toString().orEmpty(),
            "uid" to info.id.toString(),
        )

    /** Maps Android audio device constants to cross-platform port type identifiers. */
    private fun portTypeString(type: Int): String =
        when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "builtInReceiver"

            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "builtInSpeaker"

            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "headphones"

            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetoothA2DP"

            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetoothHFP"

            AudioDeviceInfo.TYPE_BLE_HEADSET -> "bluetoothLE"

            AudioDeviceInfo.TYPE_HDMI -> "hdmi"

            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET -> "usbAudio"

            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> "lineOut"

            else -> "android_$type"
        }

    /** Maps Android audio mode constants to readable string values for diagnostics. */
    private fun modeString(mode: Int): String =
        when (mode) {
            AudioManager.MODE_IN_COMMUNICATION -> "inCommunication"
            AudioManager.MODE_IN_CALL -> "inCall"
            AudioManager.MODE_NORMAL -> "normal"
            AudioManager.MODE_RINGTONE -> "ringtone"
            else -> "unknown"
        }
}

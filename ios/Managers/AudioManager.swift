import AVFoundation
// Implementation-only so apps never resolve this private module; see WebRTCHeaders/README.md.
@_implementationOnly import ExpoCallKitTelecomWebRTC
import os

private func permissionStatusString(_ status: AVAudioSession.RecordPermission) -> String {
  switch status {
  case .granted:
    return "granted"
  case .denied:
    return "denied"
  case .undetermined:
    return "undetermined"
  @unknown default:
    return "unknown"
  }
}

extension AVAudioSession.CategoryOptions {
  fileprivate var activeOptionNames: [String] {
    let mapping: [(AVAudioSession.CategoryOptions, String)] = [
      (.mixWithOthers, "mixWithOthers"),
      (.duckOthers, "duckOthers"),
      (.allowBluetoothHFP, "allowBluetoothHFP"),
      (.allowBluetoothA2DP, "allowBluetoothA2DP"),
      (.allowAirPlay, "allowAirPlay"),
      (.defaultToSpeaker, "defaultToSpeaker"),
      (.interruptSpokenAudioAndMixWithOthers, "interruptSpokenAudioAndMixWithOthers"),
      (.overrideMutedMicrophoneInterruption, "overrideMutedMicrophoneInterruption"),
    ]
    return mapping.compactMap { contains($0.0) ? $0.1 : nil }
  }
}

extension AVAudioSession.Port {
  /// Maps AVAudioSession.Port to consistent string identifiers for TypeScript.
  fileprivate var identifier: String {
    switch self {
    // Output ports
    case .builtInSpeaker: return "builtInSpeaker"
    case .builtInReceiver: return "builtInReceiver"
    case .headphones: return "headphones"
    case .bluetoothA2DP: return "bluetoothA2DP"
    case .bluetoothLE: return "bluetoothLE"
    case .bluetoothHFP: return "bluetoothHFP"
    case .airPlay: return "airPlay"
    case .HDMI: return "hdmi"
    case .carAudio: return "carAudio"
    case .usbAudio: return "usbAudio"
    case .lineOut: return "lineOut"
    // Input ports
    case .builtInMic: return "builtInMic"
    case .headsetMic: return "headsetMic"
    case .lineIn: return "lineIn"
    // Fallback for unknown ports
    default: return rawValue
    }
  }
}

private struct SavedAudioSessionConfig {
  let category: AVAudioSession.Category
  let mode: AVAudioSession.Mode
  let categoryOptions: AVAudioSession.CategoryOptions

  /// Converts to RTCAudioSessionConfiguration for use with configureAudioSession.
  func toRTCConfiguration() -> ECKTRTCAudioSessionConfiguration? {
    makeRTCConfiguration(category: category, categoryOptions: categoryOptions, mode: mode)
  }
}

/// Reports a WebRTC class missing at runtime. Logs in release builds and stops debug builds, so a
/// WebRTC build that renames its classes fails on the first debug run.
private func webRTCClassNotFound(_ name: String) {
  let message =
    "Neither LK\(name) nor \(name) found; the app must link a react-native-webrtc build"
  Log.audio.error(message)
  assertionFailure(message)
}

/// WebRTC's shared RTCAudioSession, or nil if the app doesn't link WebRTC.
private func rtcAudioSession() -> ECKTRTCAudioSession? {
  guard let session = ECKTRTCAudioSessionSharedInstance() else {
    webRTCClassNotFound("RTCAudioSession")
    return nil
  }
  return session
}

/// A new RTCAudioSessionConfiguration, or nil if the app doesn't link WebRTC.
private func makeRTCConfiguration(
  category: AVAudioSession.Category,
  categoryOptions: AVAudioSession.CategoryOptions,
  mode: AVAudioSession.Mode
) -> ECKTRTCAudioSessionConfiguration? {
  guard let config = ECKTRTCAudioSessionConfigurationCreate() else {
    webRTCClassNotFound("RTCAudioSessionConfiguration")
    return nil
  }
  config.category = category.rawValue
  config.categoryOptions = categoryOptions
  config.mode = mode.rawValue
  return config
}

/// Manages audio session configuration for CallKit integration with WebRTC.
///
/// This manager coordinates with WebRTC's RTCAudioSession to ensure proper audio
/// routing during VoIP calls. RTCAudioSession wraps and configures AVAudioSession
/// under the hood, so we only need to interact with RTCAudioSession directly.
///
/// Uses manual audio management to let CallKit control when audio is activated/deactivated.
///
/// ## Audio Session Lifecycle
///
/// 1. **Initialization**: `setRTCAudioSessionConfiguration(hasVideo:)` is called to set up
///    WebRTC's default audio configuration and enable manual audio management.
///
/// 2. **Call Start**: When a call is reported/started, `prepareAudioSessionForCall(hasVideo:)`
///    snapshots the current audio config and pre-heats the audio session for the call.
///
/// 3. **CallKit Activation**: When CallKit activates the audio session,
///    `onAVAudioSessionActivated()` notifies WebRTC and enables audio.
///
/// 4. **CallKit Deactivation**: When a call ends or is put on hold, `onAVAudioSessionDeactivated()`
///    disables audio.
///
/// 5. **Last Call End**: Once no calls remain and the audio session is inactive,
///    `restoreAudioSession()` restores the pre-call audio configuration. A held call keeps the
///    call configuration, so audio comes back when CallKit re-activates the session on resume.
///
/// Note: The LiveKit SDK's AudioSession.ts can also be used to configure audio based on call state:
/// https://github.com/livekit/client-sdk-react-native/blob/main/src/audio/AudioSession.ts#L206
final class AudioManager {
  static let shared = AudioManager()

  private(set) var isActive = false
  private var savedConfig: SavedAudioSessionConfig?

  private init() {
    setupRouteChangeObserver()
    setRTCAudioSessionConfiguration(hasVideo: false)
  }

  private func setupRouteChangeObserver() {
    NotificationCenter.default.addObserver(
      self,
      selector: #selector(handleRouteChange),
      name: AVAudioSession.routeChangeNotification,
      object: nil
    )
  }

  @objc private func handleRouteChange(_ notification: Notification) {
    let currentRoute = AVAudioSession.sharedInstance().currentRoute

    let outputs = currentRoute.outputs.map { port in
      [
        "portType": port.portType.identifier,
        "portName": port.portName,
        "uid": port.uid,
      ]
    }
    let inputs = currentRoute.inputs.map { port in
      [
        "portType": port.portType.identifier,
        "portName": port.portName,
        "uid": port.uid,
      ]
    }

    Log.audio.debug("Audio route changed to: \(outputs.first?["portType"] ?? "unknown")")

    Task { @MainActor in
      CallEventEmitter.shared.send(
        AudioRouteChangedEvent(inputs: inputs, outputs: outputs)
      )
    }
  }

  /// Sets the RTC audio session configuration
  /// This sets configuration but does not apply it to the audio session
  /// Enables manual audio management
  func setRTCAudioSessionConfiguration(hasVideo: Bool) {
    guard let rtcSession = rtcAudioSession() else { return }

    // Enable manual audio management - must be set before any audio session activation
    rtcSession.useManualAudio = true

    // Set the default WebRTC configuration
    let config =
      hasVideo ? getAudioSessionConfigurationForVideo() : getAudioSessionConfigurationAudioOnly()
    if let config {
      ECKTRTCAudioSessionConfigurationSetWebRTCConfiguration(config)
    }
  }

  // MARK: - State

  /// Returns the current audio session state.
  /// - Returns: A dictionary containing the current audio state and configuration.
  func getAudioSessionState() -> [String: Any] {
    let rtcSession = rtcAudioSession()
    let avSession = AVAudioSession.sharedInstance()

    // Get current route information
    let currentRoute = avSession.currentRoute
    let outputs = currentRoute.outputs.map { port in
      return [
        "portType": port.portType.identifier,
        "portName": port.portName,
        "uid": port.uid,
      ]
    }
    let inputs = currentRoute.inputs.map { port in
      return [
        "portType": port.portType.identifier,
        "portName": port.portName,
        "uid": port.uid,
      ]
    }

    // Get microphone permission status
    let microphonePermission = permissionStatusString(avSession.recordPermission)

    return [
      "isActive": isActive,
      "rtcSessionIsActive": rtcSession?.isActive ?? false,
      "avSessionIsActive": rtcSession?.isActive ?? false,
      "isAudioEnabled": rtcSession?.isAudioEnabled ?? false,
      "useManualAudio": rtcSession?.useManualAudio ?? false,
      "isOtherAudioPlaying": avSession.isOtherAudioPlaying,
      "category": avSession.category.rawValue,
      "mode": avSession.mode.rawValue,
      "categoryOptions": avSession.categoryOptions.activeOptionNames,
      "sampleRate": avSession.sampleRate,
      "ioBufferDuration": avSession.ioBufferDuration,
      "inputNumberOfChannels": avSession.inputNumberOfChannels,
      "outputNumberOfChannels": avSession.outputNumberOfChannels,
      "microphonePermission": microphonePermission,
      "currentRoute": [
        "inputs": inputs,
        "outputs": outputs,
      ],
    ]
  }

  // MARK: - Configuration

  /// Returns the RTCAudioSessionConfiguration for audio-only calls.
  /// Uses voiceChat mode without defaulting to speaker.
  private func getAudioSessionConfigurationAudioOnly() -> ECKTRTCAudioSessionConfiguration? {
    makeRTCConfiguration(
      category: .playAndRecord,
      categoryOptions: [.allowBluetoothHFP, .allowBluetoothA2DP, .allowAirPlay],
      mode: .voiceChat
    )
  }

  /// Returns the RTCAudioSessionConfiguration for video calls.
  /// Uses videoChat mode and defaults to speaker output.
  private func getAudioSessionConfigurationForVideo() -> ECKTRTCAudioSessionConfiguration? {
    makeRTCConfiguration(
      category: .playAndRecord,
      categoryOptions: [
        .allowBluetoothHFP, .allowBluetoothA2DP, .allowAirPlay, .defaultToSpeaker,
      ],
      mode: .videoChat
    )
  }

  /// Configures RTCAudioSession with the given configuration.
  /// - Parameter config: The audio session configuration to apply.
  private func configureAudioSession(_ config: ECKTRTCAudioSessionConfiguration?) {
    guard let config, let session = rtcAudioSession() else { return }

    // Apply the configuration to the AVAudioSession
    session.lockForConfiguration()
    defer { session.unlockForConfiguration() }

    do {
      try session.setConfiguration(config)
      Log.audio.debug(
        "Audio session configured (category: \(config.category), mode: \(config.mode))"
      )
    } catch {
      Log.audio.error("Failed to configure audio session: \(error.localizedDescription)")
    }
  }

  /// Prepares the audio session for an upcoming call.
  /// Call this when reporting/starting a call to pre-heat the audio session.
  /// - Parameter hasVideo: Whether the call includes video.
  func prepareAudioSessionForCall(hasVideo: Bool) {
    // Only snapshot if we don't already have a saved config (prevents overwriting)
    if savedConfig == nil {
      let avSession = AVAudioSession.sharedInstance()
      savedConfig = SavedAudioSessionConfig(
        category: avSession.category,
        mode: avSession.mode,
        categoryOptions: avSession.categoryOptions
      )
      Log.audio.debug("Saved audio session config for restoration")
    }

    // Configure the audio session for the call
    let config =
      hasVideo ? getAudioSessionConfigurationForVideo() : getAudioSessionConfigurationAudioOnly()
    configureAudioSession(config)
  }

  /// Restores the audio session to its pre-call configuration.
  /// Call this if a call fails to start after prepareAudioSession was called,
  /// or when a call ends, to return the audio session to its original state.
  func restoreAudioSession() {
    guard let config = savedConfig else {
      Log.audio.debug("No saved audio session config to restore")
      return
    }

    configureAudioSession(config.toRTCConfiguration())
    Log.audio.debug(
      "Audio session restored to previous config (category: \(config.category.rawValue), mode: \(config.mode.rawValue))"
    )
    savedConfig = nil
  }

  // MARK: - CallKit Audio Session Callbacks

  /// Called when CallKit activates the audio session.
  /// This happens after the user answers a call or starts an outgoing call.
  /// Audio session should already be configured via prepareAudioSessionForCall() before this is called.
  /// - Parameter calls: The current active call sessions.
  func onAVAudioSessionActivated(calls: [CallSession]) {
    isActive = true

    if let session = rtcAudioSession() {
      // Notify WebRTC that CallKit activated the audio session
      session.audioSessionDidActivate(AVAudioSession.sharedInstance())

      // Enable the audio unit for VOIP processing (required with useManualAudio = true)
      session.isAudioEnabled = true
    }

    Log.audio.debug("RTC audio session activated")

    Task { @MainActor in
      let callInfos = calls.map { AudioSessionCallInfo(from: $0) }
      CallEventEmitter.shared.send(AudioSessionActivatedEvent(calls: callInfos))
    }
  }

  /// Called when CallKit deactivates the audio session.
  /// This happens when the call ends, and also when it is put on hold.
  /// - Parameter calls: The call sessions that were active when deactivation occurred.
  func onAVAudioSessionDeactivated(calls: [CallSession]) {
    isActive = false

    if let rtcSession = rtcAudioSession() {
      // Disable the audio unit (required with useManualAudio = true)
      rtcSession.isAudioEnabled = false
      // Notify WebRTC that CallKit deactivated the audio session
      rtcSession.audioSessionDidDeactivate(AVAudioSession.sharedInstance())
    }

    // Restore the pre-call configuration only once no calls remain. A held call still needs the
    // call configuration when it resumes. If the ended call hasn't been removed from the store
    // yet, CallManager restores after removing it instead.
    if calls.isEmpty {
      restoreAudioSession()
    }

    Log.audio.debug("RTC audio session deactivated")

    Task { @MainActor in
      let callInfos = calls.map { AudioSessionCallInfo(from: $0) }
      CallEventEmitter.shared.send(AudioSessionDeactivatedEvent(calls: callInfos))
    }
  }

  // MARK: - Port Override

  /// Sets the speaker override for the audio session.
  /// Note that this only has an effect in voiceChat mode because voiceChat mode makes speaker the default
  /// - Parameter enabled: Whether to route audio to the speaker.
  func setAudioSessionPortOverride(_ enabled: Bool) {
    guard let rtcSession = rtcAudioSession() else { return }
    rtcSession.lockForConfiguration()
    defer { rtcSession.unlockForConfiguration() }

    do {
      if enabled {
        try rtcSession.overrideOutputAudioPort(.speaker)
      } else {
        try rtcSession.overrideOutputAudioPort(.none)
      }
      Log.audio.debug("Audio port override set to \(enabled ? "speaker" : "none")")
    } catch {
      Log.audio.error(
        "Failed to set audio port override to \(enabled ? "speaker" : "none"): \(error.localizedDescription)"
      )
    }
  }
}

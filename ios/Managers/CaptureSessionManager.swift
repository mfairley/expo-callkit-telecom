import AVFoundation
import livekit_react_native_webrtc

private func permissionStatusString(_ status: AVAuthorizationStatus) -> String {
  switch status {
  case .authorized:
    return "granted"
  case .denied:
    return "denied"
  case .notDetermined:
    return "undetermined"
  case .restricted:
    return "restricted"
  @unknown default:
    return "unknown"
  }
}

/// Manages video capture session state.
final class CaptureSessionManager {
  static let shared = CaptureSessionManager()

  private init() {
    configureMultitaskingCameraAccess()
  }

  /// Configures WebRTC to enable multitasking camera access.
  /// On iOS 18+ with voip background mode, this allows the camera to continue
  /// capturing when the app enters PiP or slides over another app.
  private func configureMultitaskingCameraAccess() {
    let options = WebRTCModuleOptions.sharedInstance()
    options.enableMultitaskingCameraAccess = true
    Log.call.debug("Enabled multitasking camera access for WebRTC")
  }

  /// Returns the current capture session state.
  func getCaptureSessionState() -> [String: Any] {
    let cameraPermission = permissionStatusString(
      AVCaptureDevice.authorizationStatus(for: .video)
    )

    var state: [String: Any] = [
      "cameraPermission": cameraPermission
    ]

    // Check multitasking camera access support (iOS 16+)
    if #available(iOS 16.0, *) {
      let session = AVCaptureSession()
      state["isMultitaskingCameraAccessSupported"] = session.isMultitaskingCameraAccessSupported
    }

    return state
  }
}

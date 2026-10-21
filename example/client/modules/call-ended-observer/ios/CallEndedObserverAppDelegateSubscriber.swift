import ExpoModulesCore
import os

/// Observes the `expo.modules.callkittelecom.ACTION_CALL_EVENT` notification, posted when a
/// call event can't reach a live JS observer — e.g. the user declined an incoming call while
/// the app was killed and `onCallEnded` had nowhere to go.
///
/// `userInfo` carries `eventName` and `payload`, the body JS would have received, including
/// the full `session` (so `serverCallId` and any push `metadata` are available here without
/// app-side state).
///
/// A real app would notify its backend here so the caller stops ringing immediately (begin a
/// UIApplication background task around the request). The example logs the payload to the
/// unified log, category `CallEndedObserver`.
///
/// Registered through expo-module.config.json `appDelegateSubscribers`, so it is listening from
/// app launch, before PushKit can deliver a call.
private let log = Logger(
  subsystem: Bundle.main.bundleIdentifier ?? "example", category: "CallEndedObserver")

public class CallEndedObserverAppDelegateSubscriber: ExpoAppDelegateSubscriber {
  public func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    // The center retains the block observer for the process lifetime.
    _ = NotificationCenter.default.addObserver(
      forName: Notification.Name("expo.modules.callkittelecom.ACTION_CALL_EVENT"),
      object: nil, queue: nil
    ) { notification in
      guard notification.userInfo?["eventName"] as? String == "onCallEnded",
        let payload = notification.userInfo?["payload"] as? [String: Any]
      else { return }
      let session = payload["session"] as? [String: Any]
      let incomingCall = session?["incomingCallEvent"] as? [String: Any]
      let serverCallId = incomingCall?["serverCallId"] as? String ?? "nil"
      let metadata = incomingCall?["metadata"] as? [String: Any] ?? [:]

      log.info(
        "Call ended with no live JS observer - serverCallId: \(serverCallId, privacy: .public), metadata: \(metadata, privacy: .public)"
      )
    }
    return true
  }
}

import ExpoModulesCore
import Intents

public class ExpoCallKitTelecomAppDelegateSubscriber: ExpoAppDelegateSubscriber {
  public func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    // Pre-warm session managers at app launch, before any calls
    _ = AudioManager.shared
    _ = CaptureSessionManager.shared

    // Register for VoIP push so we can receive pushes when app is launched from terminated state.
    VoIPPushManager.shared.register()

    // Initialize event emitter early so it can queue events before JS is ready
    Task { @MainActor in
      _ = CallEventEmitter.shared
      // Allow events to queue that may fire before JS is ready on cold start
      CallEventEmitter.shared.setQueueLimit(for: CallIntentReceivedEvent.self, limit: 1)
      CallEventEmitter.shared.setQueueLimit(for: AudioSessionActivatedEvent.self, limit: 1)
      CallEventEmitter.shared.setQueueLimit(for: AudioSessionDeactivatedEvent.self, limit: 1)
      CallEventEmitter.shared.setQueueLimit(for: IncomingCallReportedEvent.self, limit: 1)
      CallEventEmitter.shared.setQueueLimit(for: CallAnsweredEvent.self, limit: 1)
      CallEventEmitter.shared.setQueueLimit(for: VoIPPushTokenUpdatedEvent.self, limit: 1)
    }

    return true
  }

  public func application(
    _ application: UIApplication,
    continue userActivity: NSUserActivity,
    restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void
  ) -> Bool {
    guard let interaction = userActivity.interaction else {
      return false
    }

    if let intent = interaction.intent as? INStartAudioCallIntent {
      handleCallIntent(contacts: intent.contacts, hasVideo: false)
      return true
    }

    if let intent = interaction.intent as? INStartCallIntent {
      let hasVideo = intent.callCapability == .videoCall
      handleCallIntent(contacts: intent.contacts, hasVideo: hasVideo)
      return true
    }

    if let intent = interaction.intent as? INStartVideoCallIntent {
      handleCallIntent(contacts: intent.contacts, hasVideo: true)
      return true
    }

    return false
  }

  private func handleCallIntent(contacts: [INPerson]?, hasVideo: Bool) {
    guard let person = contacts?.first,
      let personHandle = person.personHandle,
      let handleValue = personHandle.value
    else {
      Log.call.warning("Call intent received but no valid handle found")
      return
    }

    let handleType: String
    switch personHandle.type {
    case .phoneNumber:
      handleType = "phoneNumber"
    case .emailAddress:
      handleType = "email"
    case .unknown:
      handleType = "unknown"
    @unknown default:
      handleType = "unknown"
    }

    Log.call.debug(
      "Call intent received - type: \(handleType), hasVideo: \(hasVideo)")

    Task { @MainActor in
      CallEventEmitter.shared.send(
        CallIntentReceivedEvent(
          handle: handleValue,
          handleType: handleType,
          hasVideo: hasVideo
        )
      )
    }
  }
}

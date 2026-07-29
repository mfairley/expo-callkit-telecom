import Foundation
import PushKit

extension VoIPPushManager: PKPushRegistryDelegate {
  /// Called when push credentials are updated.
  ///
  /// Converts the token data to a hex string and stores it for JS access.
  nonisolated func pushRegistry(
    _ registry: PKPushRegistry,
    didUpdate pushCredentials: PKPushCredentials,
    for type: PKPushType
  ) {
    guard type == .voIP else { return }

    let tokenData = pushCredentials.token
    let tokenString = tokenData.map { String(format: "%02x", $0) }.joined()

    Task { @MainActor in
      updateToken(tokenString)
    }
  }

  /// Called when push credentials are invalidated.
  ///
  /// Clears the stored token and notifies JS.
  nonisolated func pushRegistry(
    _ registry: PKPushRegistry,
    didInvalidatePushTokenFor type: PKPushType
  ) {
    guard type == .voIP else { return }

    Log.voipPush.debug("VoIP push token invalidated")

    Task { @MainActor in
      updateToken(nil)
    }
  }

  /// Called when a VoIP push notification is received.
  ///
  /// This method MUST report a call to CallKit before returning, per Apple's requirements.
  /// The payload is expected to wrap an `IncomingCallEvent` under the top-level
  /// key `"incomingCall"`, matching `IncomingCallEventParser`.
  ///
  /// - Parameters:
  ///   - registry: The push registry.
  ///   - payload: The push payload containing the incoming call event.
  ///   - type: The push type (should be .voIP).
  ///   - completion: Completion handler that must be called after processing.
  nonisolated func pushRegistry(
    _ registry: PKPushRegistry,
    didReceiveIncomingPushWith payload: PKPushPayload,
    for type: PKPushType,
    completion: @escaping () -> Void
  ) {
    guard type == .voIP else {
      completion()
      return
    }

    let dictionaryPayload = payload.dictionaryPayload

    Log.voipPush.debug("Received VoIP push - payload keys: \(dictionaryPayload.keys)")

    guard let event = IncomingCallEventParser.parse(from: dictionaryPayload) else {
      // Say WHICH parse guard failed — GUIDs only, no display names, so this is
      // safe to log and enough to identify the offending payload shape.
      let inner = (dictionaryPayload["incomingCall"] ?? dictionaryPayload["incoming_call"])
        as? [AnyHashable: Any]
      let callerId = (inner?["caller"] as? [AnyHashable: Any])?["id"] as? String
      Log.voipPush.error(
        """
        Failed to parse VoIP push payload as IncomingCallEvent - \
        wrapper: \(inner != nil), \
        eventId: \(inner?["eventId"] as? String ?? "<missing>"), \
        serverCallId: \(inner?["serverCallId"] as? String ?? "<missing>"), \
        callerId: \(callerId ?? "<missing>")
        """
      )
      // We must still report a call to CallKit even on parse failure, or the
      // app will be terminated.
      reportFailedIncomingCall(completion: completion)
      return
    }

    Log.voipPush.debug(
      "Parsed incoming call event - serverCallId: \(event.serverCallId)"
    )

    // Report the incoming call to CallKit using callback-based API
    // (async/await may not work reliably when app is launched from terminated state)
    CallManager.shared.reportIncomingCall(event: event) { _, error in
      if let error = error {
        Log.voipPush.error("Failed to report incoming call: \(error.localizedDescription)")
      } else {
        Log.voipPush.debug("Successfully reported incoming call from VoIP push")
      }
      completion()
    }
  }

  /// Reports a failed incoming call to CallKit when we can't parse the push payload.
  ///
  /// Per Apple's requirements, we must report a call to CallKit when receiving a VoIP push.
  /// If we can't parse the payload, we report a call and immediately end it.
  private nonisolated func reportFailedIncomingCall(completion: @escaping () -> Void) {
    let fallbackEvent = IncomingCallEvent(
      eventId: UUID().uuidString.lowercased(),
      serverCallId: UUID().uuidString.lowercased(),
      caller: IncomingCallEvent.Caller(
        id: UUID().uuidString,
        displayName: "Invalid Call",
        avatarUrl: nil,
        phoneNumber: nil,
        email: nil
      ),
      hasVideo: false,
      startedAt: Date(),
      metadata: nil
    )

    // Use callback-based API for reliability when app is launched from terminated state.
    // End the fallback session by ITS OWN id (delivered via the completion):
    // ending `firstSession` here ended the WRONG session whenever another call
    // was already active (the user's own outgoing call got reported ended while
    // the "Invalid Call" rang on), and when no session existed yet it raced the
    // store add and ended nothing.
    CallManager.shared.reportIncomingCall(event: fallbackEvent) { id, error in
      if let error = error {
        Log.voipPush.error("Failed to report fallback incoming call: \(error.localizedDescription)")
      }
      // Immediately end the call since it's invalid
      Task {
        // Dismisses the CallKit UI by id (no store dependency), then sweeps the
        // store in case the report path's async add landed after the removal.
        await CallManager.shared.reportCallEnded(for: id, reason: .failed)
        await CallManager.shared.store.remove(for: id)
      }
      completion()
    }
  }
}

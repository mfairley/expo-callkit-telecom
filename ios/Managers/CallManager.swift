import CallKit
import os

/// Errors that can occur during call operations.
enum CallError: LocalizedError {
  case sessionAlreadyExists

  var errorDescription: String? {
    switch self {
    case .sessionAlreadyExists:
      return "A call session already exists"
    }
  }
}

/// Manages CallKit integration for VoIP calls.
///
/// Handles the lifecycle of calls including starting outgoing calls,
/// reporting call state changes, and responding to CallKit actions.
class CallManager: NSObject {
  static let shared = CallManager()
  let store = CallStore()

  private let callController = CXCallController()
  private let provider: CXProvider

  private let supportsHolding = false
  private let supportsGrouping = false
  private let supportsUngrouping = false
  private let supportsDTMF = false

  /// Timeout duration for outgoing calls to connect.
  static let outgoingCallTimeout: Duration = {
    let seconds =
      Bundle.main.object(
        forInfoDictionaryKey: "ExpoCallKitTelecomOutgoingCallTimeout"
      ) as? Int ?? 60
    return .seconds(seconds)
  }()

  /// Timeout duration for incoming calls to be answered.
  static let incomingCallTimeout: Duration = {
    let seconds =
      Bundle.main.object(
        forInfoDictionaryKey: "ExpoCallKitTelecomIncomingCallTimeout"
      ) as? Int ?? 45
    return .seconds(seconds)
  }()

  /// Tasks tracking call timeouts, keyed by call ID.
  private var callTimeoutTasks: [UUID: Task<Void, Never>] = [:]

  /// Lock for thread-safe access to call timeout tasks.
  private let timeoutTasksLock = NSLock()

  private override init() {
    let configuration = CXProviderConfiguration()
    configuration.supportsVideo = true
    configuration.maximumCallGroups = 1
    configuration.maximumCallsPerCallGroup = 1
    configuration.supportedHandleTypes = [.phoneNumber, .generic]
    configuration.includesCallsInRecents = true

    if let ringtone = Bundle.main.object(
      forInfoDictionaryKey: "ExpoCallKitTelecomDefaultRingtone"
    ) as? String,
      ringtone != "default"
    {
      configuration.ringtoneSound = ringtone
    }

    provider = CXProvider(configuration: configuration)

    super.init()

    provider.setDelegate(self, queue: nil)
  }

  // MARK: - Call Timeout

  /// Starts a timeout timer for a call.
  ///
  /// For outgoing calls, the timeout is triggered if the call is not connected
  /// within the timeout period. For incoming calls, it's triggered if the user
  /// does not answer. When timeout occurs, the dialtone is stopped and the call
  /// is ended with `.unanswered` reason.
  ///
  /// - Parameters:
  ///   - id: The UUID of the call.
  ///   - timeout: The duration before the call times out.
  func startCallTimeout(for id: UUID, timeout: Duration) {
    Log.call.debug("Starting call timeout - id: \(id), timeout: \(timeout)")

    let task = Task {
      try? await Task.sleep(for: timeout)

      guard !Task.isCancelled else {
        return
      }

      // Remove self from timeout tasks since we're handling the timeout
      self.removeTimeoutTask(for: id)

      Log.call.debug("Call timeout expired - id: \(id)")

      // Stop the dialtone (for outgoing calls)
      DialtonePlayer.shared.stop()

      // End the call due to timeout
      await reportCallEnded(for: id, reason: .unanswered)
    }

    timeoutTasksLock.lock()
    callTimeoutTasks[id] = task
    timeoutTasksLock.unlock()
  }

  /// Removes and returns the timeout task for a call (thread-safe).
  ///
  /// - Parameter id: The UUID of the call.
  /// - Returns: The removed task, or nil if no task existed.
  @discardableResult
  private func removeTimeoutTask(for id: UUID) -> Task<Void, Never>? {
    timeoutTasksLock.lock()
    defer { timeoutTasksLock.unlock() }
    return callTimeoutTasks.removeValue(forKey: id)
  }

  /// Cancels the timeout timer for a call.
  ///
  /// - Parameter id: The UUID of the call.
  func cancelCallTimeout(for id: UUID) {
    if let task = removeTimeoutTask(for: id) {
      task.cancel()
      Log.call.debug("Cancelled call timeout - id: \(id)")
    }
  }

  /// Removes and returns all timeout tasks atomically (thread-safe).
  ///
  /// - Returns: Dictionary of all removed tasks keyed by call ID.
  private func removeAllTimeoutTasks() -> [UUID: Task<Void, Never>] {
    timeoutTasksLock.lock()
    defer { timeoutTasksLock.unlock() }
    let tasks = callTimeoutTasks
    callTimeoutTasks.removeAll()
    return tasks
  }

  /// Cancels all call timeout timers.
  ///
  /// Called when the provider resets to clean up all pending timeouts.
  func cancelAllCallTimeouts() {
    let tasks = removeAllTimeoutTasks()

    Log.call.debug("Cancelling all call timeouts - count: \(tasks.count)")
    for (id, task) in tasks {
      task.cancel()
      Log.call.debug("Cancelled call timeout - id: \(id)")
    }
  }

  /// Creates a CallKit handle from a participant.
  ///
  /// Uses phone number if available, then email, otherwise falls back to a generic handle with the participant ID.
  private func makeHandle(from participant: CallParticipant) -> CXHandle {
    if let phoneNumber = participant.phoneNumber {
      return CXHandle(type: .phoneNumber, value: phoneNumber)
    }
    if let email = participant.email {
      return CXHandle(type: .emailAddress, value: email)
    }
    return CXHandle(type: .generic, value: participant.id)
  }

  /// Sets the localized caller name on a CXCallUpdate for participants using a generic handle.
  ///
  /// This ensures the display name appears in the CallKit UI when neither phone number nor email is available.
  /// Only sets the name if the participant has no phone number or email (i.e., uses a generic handle).
  ///
  /// - Parameters:
  ///   - participant: The participant whose display name should be used.
  ///   - update: The CXCallUpdate to modify.
  public func setLocalizedCallerNameIfNeeded(
    for participant: CallParticipant,
    on update: CXCallUpdate
  ) {
    guard participant.phoneNumber == nil,
      participant.email == nil,
      let displayName = participant.displayName
    else {
      return
    }

    update.localizedCallerName = displayName
  }

  // MARK: - Start Outgoing Call

  /// Starts an outgoing call request to CallKit.
  ///
  /// Creates a new call session, requests CallKit to start the call, and returns the generated call ID.
  /// If CallKit rejects the request, the session is removed and an error is thrown.
  ///
  /// - Parameters:
  ///   - recipient: The participant to call.
  ///   - options: Call configuration options.
  ///   - isAppInitiated: indicates if the call was started from the app itself or from outside (e.g. from Siri intent)
  /// - Returns: The UUID assigned to this call.
  /// - Throws: An error if CallKit rejects the call request.
  func startOutgoingCall(
    recipient: CallParticipant,
    options: CallOptions,
    isAppInitiated: Bool = true
  ) async throws -> UUID {
    // Guard against starting a new call while one is already active
    if let existingSession = await store.firstSession {
      Log.call.warning("Cannot start outgoing call - session already exists: \(existingSession.id)")
      throw CallError.sessionAlreadyExists
    }

    // Prepare audio session before starting the call
    AudioManager.shared.prepareAudioSessionForCall(hasVideo: options.hasVideo)

    let id = UUID()
    Log.call.debug("Starting outgoing call - id: \(id)")

    let session = CallSession(
      id: id,
      options: options,
      origin: isAppInitiated ? .outgoingApp : .outgoingSystem,
      remoteParticipants: [recipient],
      incomingCallEvent: nil,
      status: .requesting,
      connectedAt: nil,
      isMuted: false,
      isOnHold: false,
      dtmfDigits: nil
    )
    await store.add(session)

    let handle = makeHandle(from: recipient)
    let startCallAction = CXStartCallAction(call: id, handle: handle)
    startCallAction.isVideo = options.hasVideo

    let transaction = CXTransaction(action: startCallAction)

    do {
      try await withCheckedThrowingContinuation {
        (continuation: CheckedContinuation<Void, Error>) in
        callController.request(transaction) { error in
          if let error = error {
            continuation.resume(throwing: error)
          } else {
            continuation.resume()
          }
        }
      }
      Log.call.debug("Outgoing call request accepted by CallKit - id: \(id)")
    } catch {
      Log.call.error(
        "Outgoing call request rejected by CallKit - id: \(id), error: \(error.localizedDescription)"
      )
      await store.remove(for: id)
      AudioManager.shared.restoreAudioSession()
      throw error
    }

    return id
  }

  // MARK: - Report Incoming Call

  /// Reports an incoming call to CallKit, displaying the native call UI.
  ///
  /// Creates a new call session with `.incoming` origin, reports the call to CallKit,
  /// and stores the session. The user can then answer or decline via the native UI.
  ///
  /// - Parameter event: The incoming call event containing caller info.
  /// - Throws: An error if CallKit rejects the incoming call report.
  func reportIncomingCall(event: IncomingCallEvent) async throws {
    let id = UUID()
    Log.call.debug("Reporting incoming call - id: \(id)")

    // Prepare audio session before reporting to CallKit
    AudioManager.shared.prepareAudioSessionForCall(hasVideo: event.hasVideo)

    let caller = CallParticipant(
      id: event.caller.id,
      phoneNumber: event.caller.phoneNumber,
      email: event.caller.email,
      displayName: event.caller.displayName,
      avatarUrl: event.caller.avatarUrl
    )

    let session = CallSession(
      id: id,
      options: CallOptions(hasVideo: event.hasVideo),
      origin: .incoming,
      remoteParticipants: [caller],
      incomingCallEvent: event,
      status: .ringing,
      connectedAt: nil,
      isMuted: false,
      isOnHold: false,
      dtmfDigits: nil
    )

    let update = CXCallUpdate()
    let handle = makeHandle(from: caller)
    update.hasVideo = event.hasVideo
    update.remoteHandle = handle
    setLocalizedCallerNameIfNeeded(for: caller, on: update)
    update.supportsHolding = supportsHolding
    update.supportsGrouping = supportsGrouping
    update.supportsUngrouping = supportsUngrouping
    update.supportsDTMF = supportsDTMF

    do {
      try await withCheckedThrowingContinuation {
        (continuation: CheckedContinuation<Void, Error>) in
        provider.reportNewIncomingCall(with: id, update: update) { error in
          if let error = error {
            continuation.resume(throwing: error)
          } else {
            continuation.resume()
          }
        }
      }
      Log.call.debug("Incoming call reported to CallKit - id: \(id)")

      await store.add(session)

      // Notify JS that an incoming call has been reported to CallKit
      await MainActor.run {
        CallEventEmitter.shared.send(IncomingCallReportedEvent(id: id))
      }

      // Start timeout for incoming call
      startCallTimeout(for: id, timeout: Self.incomingCallTimeout)
    } catch {
      Log.call.error(
        "Failed to report incoming call to CallKit - id: \(id), error: \(error.localizedDescription)"
      )
      AudioManager.shared.restoreAudioSession()
      throw error
    }
  }

  /// Reports an incoming call to CallKit using callbacks instead of async/await.
  ///
  /// This is used by VoIP push handling where Swift concurrency may not be fully
  /// initialized when the app is launched from a terminated state.
  ///
  /// - Parameters:
  ///   - event: The incoming call event containing caller info.
  ///   - completion: Called when the CallKit report completes (success or failure).
  func reportIncomingCall(event: IncomingCallEvent, completion: @escaping (Error?) -> Void) {
    let id = UUID()
    Log.call.debug("Reporting incoming call (sync) - id: \(id)")

    // Pre-heat audio session before reporting to CallKit
    AudioManager.shared.prepareAudioSessionForCall(hasVideo: event.hasVideo)

    let caller = CallParticipant(
      id: event.caller.id,
      phoneNumber: event.caller.phoneNumber,
      email: event.caller.email,
      displayName: event.caller.displayName,
      avatarUrl: event.caller.avatarUrl
    )

    let session = CallSession(
      id: id,
      options: CallOptions(hasVideo: event.hasVideo),
      origin: .incoming,
      remoteParticipants: [caller],
      incomingCallEvent: event,
      status: .ringing,
      connectedAt: nil,
      isMuted: false,
      isOnHold: false,
      dtmfDigits: nil
    )

    let update = CXCallUpdate()
    let handle = makeHandle(from: caller)
    update.hasVideo = event.hasVideo
    update.remoteHandle = handle
    setLocalizedCallerNameIfNeeded(for: caller, on: update)
    update.supportsHolding = supportsHolding
    update.supportsGrouping = supportsGrouping
    update.supportsUngrouping = supportsUngrouping
    update.supportsDTMF = supportsDTMF

    // Report to CallKit - this is the critical part that must happen immediately
    provider.reportNewIncomingCall(with: id, update: update) { [weak self] error in
      if let error = error {
        Log.call.error(
          "Failed to report incoming call to CallKit - id: \(id), error: \(error.localizedDescription)"
        )
        AudioManager.shared.restoreAudioSession()
        completion(error)
        return
      }

      Log.call.debug("Incoming call reported to CallKit - id: \(id)")

      // Now do the async work (store session, emit events, start timeout)
      Task {
        await self?.store.add(session)

        await MainActor.run {
          CallEventEmitter.shared.send(IncomingCallReportedEvent(id: id))
        }

        self?.startCallTimeout(for: id, timeout: Self.incomingCallTimeout)
      }

      completion(nil)
    }
  }

  // MARK: - Answer Call

  /// Answers an incoming call via CallKit.
  ///
  /// Use this when the user answers from the app's custom UI rather than
  /// the native CallKit UI. This triggers the CXAnswerCallAction delegate.
  ///
  /// - Parameter id: The UUID of the call to answer.
  /// - Throws: An error if CallKit rejects the answer request.
  func answerCall(for id: UUID) async throws {
    Log.call.debug("Answering call - id: \(id)")
    let action = CXAnswerCallAction(call: id)
    let transaction = CXTransaction(action: action)

    do {
      try await withCheckedThrowingContinuation {
        (continuation: CheckedContinuation<Void, Error>) in
        callController.request(transaction) { error in
          if let error = error {
            continuation.resume(throwing: error)
          } else {
            continuation.resume()
          }
        }
      }
      Log.call.debug("Answer call request accepted by CallKit - id: \(id)")
    } catch {
      Log.call.error(
        "Answer call request rejected by CallKit - id: \(id), error: \(error.localizedDescription)")
      throw error
    }
  }

  /// Fulfills an incoming call connection request.
  ///
  /// Call this from JS after connecting the media. Updates the call status
  /// to connected and signals the CXAnswerCallAction to complete.
  /// If the request has already timed out, this is a no-op.
  ///
  /// - Parameter requestId: The unique request ID from the CallAnsweredEvent.
  /// - Returns: Whether the request was successfully fulfilled.
  @discardableResult
  func fulfillIncomingCall(requestId: UUID) async -> Bool {
    guard let callId = await FulfillRequestManager.shared.fulfill(requestId: requestId) else {
      Log.call.debug("Incoming call request not found (timed out) - requestId: \(requestId)")
      return false
    }

    // Update status and connectedAt in a single event
    await store.update(for: callId) { session in
      session.status = .connected
      session.connectedAt = Date()
    }
    Log.call.debug("Fulfilled incoming call - callId: \(callId), requestId: \(requestId)")
    return true
  }

  /// Fails a pending incoming call connection request.
  ///
  /// Call this from JS when the answer flow fails before fulfilling
  /// (e.g., API error). Cancels the pending fulfill request, which causes
  /// the CXAnswerCallAction to fail. CallKit then ends the call via
  /// CXEndCallAction, triggering normal cleanup.
  ///
  /// - Parameter requestId: The unique request ID from the CallAnsweredEvent.
  func failIncomingCallConnected(requestId: UUID) async {
    await FulfillRequestManager.shared.cancel(requestId: requestId)
    Log.call.debug("Failed incoming call connection - requestId: \(requestId)")
  }

  /// Reports to CallKit that an outgoing call has connected.
  ///
  /// Call this when the media connection (e.g., LiveKit room) is established
  /// and the other party has answered the call.
  /// Updates the call state to connected in both CallKit and the local store.
  /// Stops the dialtone and cancels the outgoing call timeout.
  ///
  /// - Parameter id: The UUID of the call to report as connected.
  func reportOutgoingCallConnected(for id: UUID) async {
    Log.call.debug("Reporting outgoing call connected - id: \(id)")

    // Stop dialtone and cancel timeout
    DialtonePlayer.shared.stop()
    cancelCallTimeout(for: id)

    let now = Date()
    provider.reportOutgoingCall(with: id, connectedAt: now)
    await store.update(for: id) { session in
      session.status = .connected
      session.connectedAt = now
    }
  }

  // MARK: - End Call

  /// Ends a call by requesting CallKit to terminate it.
  ///
  /// This sends a CXEndCallAction to CallKit, which will trigger the
  /// provider delegate's end call handler to clean up the session.
  ///
  /// - Parameter id: The UUID of the call to end.
  /// - Throws: An error if CallKit rejects the end call request.
  func endCall(for id: UUID) async throws {
    Log.call.debug("Ending call - id: \(id)")
    let endCallAction = CXEndCallAction(call: id)
    let transaction = CXTransaction(action: endCallAction)

    do {
      try await withCheckedThrowingContinuation {
        (continuation: CheckedContinuation<Void, Error>) in
        callController.request(transaction) { error in
          if let error = error {
            continuation.resume(throwing: error)
          } else {
            continuation.resume()
          }
        }
      }
      Log.call.debug("End call request accepted by CallKit - id: \(id)")
    } catch {
      Log.call.error(
        "End call request rejected by CallKit - id: \(id), error: \(error.localizedDescription)")
      throw error
    }
  }

  /// Reports to CallKit that a call has ended.
  ///
  /// Call this when a call ends for any reason not initiated by the local user
  /// (e.g., remote hangup, network error, timeout, answered elsewhere).
  /// This removes the call from CallKit and cleans up the local session.
  /// Stops the dialtone and cancels any outgoing call timeout.
  ///
  /// - Parameters:
  ///   - id: The UUID of the call that ended.
  ///   - reason: The reason the call ended.
  func reportCallEnded(for id: UUID, reason: CXCallEndedReason) async {
    Log.call.debug("Reporting call ended - id: \(id), reason: \(reason.rawValue)")

    // Stop dialtone and cancel timeout (in case call ended before connecting)
    DialtonePlayer.shared.stop()
    cancelCallTimeout(for: id)

    provider.reportCall(with: id, endedAt: Date(), reason: reason)

    await MainActor.run {
      CallEventEmitter.shared.send(CallReportedEnded(id: id, reason: reason))
    }

    await store.remove(for: id)
  }

  // MARK: - Mute Support

  /// Sets the mute state for a call via CallKit.
  ///
  /// This sends a CXSetMutedCallAction to CallKit, which will handle
  /// the actual audio muting and trigger the provider delegate's handler.
  ///
  /// - Parameters:
  ///   - id: The UUID of the call to mute/unmute.
  ///   - muted: Whether the call should be muted.
  /// - Throws: An error if CallKit rejects the mute request.
  func setMuted(for id: UUID, muted: Bool) async throws {
    Log.call.debug("Setting mute state - id: \(id), muted: \(muted)")

    // Check if state already matches
    if let session = await store.session(for: id), session.isMuted == muted {
      Log.call.debug("Mute state already matches - id: \(id)")
      return
    }

    let action = CXSetMutedCallAction(call: id, muted: muted)
    let transaction = CXTransaction(action: action)

    do {
      try await withCheckedThrowingContinuation {
        (continuation: CheckedContinuation<Void, Error>) in
        callController.request(transaction) { error in
          if let error = error {
            continuation.resume(throwing: error)
          } else {
            continuation.resume()
          }
        }
      }
      Log.call.debug("Set mute request accepted by CallKit - id: \(id), muted: \(muted)")
    } catch {
      Log.call.error(
        "Set mute request rejected by CallKit - id: \(id), error: \(error.localizedDescription)")
      throw error
    }
  }

  // MARK: - Video Support

  /// Updates the video state for a call.
  ///
  /// This updates the local session and reports the change to CallKit.
  ///
  /// - Parameters:
  ///   - id: The UUID of the call to update.
  ///   - enabled: Whether video should be enabled.
  func reportVideo(for id: UUID, enabled: Bool) async {
    Log.call.debug("Setting video state - id: \(id), enabled: \(enabled)")

    await store.update(for: id) { session in
      session.options.hasVideo = enabled
    }

    AudioManager.shared.prepareAudioSessionForCall(hasVideo: enabled)

    let update = CXCallUpdate()
    update.hasVideo = enabled
    provider.reportCall(with: id, updated: update)

    await MainActor.run {
      CallEventEmitter.shared.send(VideoChangedEvent(id: id, hasVideo: enabled))
    }
  }

  // MARK: - Hold Support

  /// Sets the hold state for a call via CallKit.
  ///
  /// This sends a CXSetHeldCallAction to CallKit, which will handle
  /// the hold state change and trigger the provider delegate's handler.
  ///
  /// - Parameters:
  ///   - id: The UUID of the call to hold/unhold.
  ///   - onHold: Whether the call should be on hold.
  /// - Throws: An error if CallKit rejects the hold request.
  func setHeld(for id: UUID, onHold: Bool) async throws {
    Log.call.debug("Setting hold state - id: \(id), onHold: \(onHold)")
    let action = CXSetHeldCallAction(call: id, onHold: onHold)
    let transaction = CXTransaction(action: action)

    do {
      try await withCheckedThrowingContinuation {
        (continuation: CheckedContinuation<Void, Error>) in
        callController.request(transaction) { error in
          if let error = error {
            continuation.resume(throwing: error)
          } else {
            continuation.resume()
          }
        }
      }
      Log.call.debug("Set hold request accepted by CallKit - id: \(id), onHold: \(onHold)")
    } catch {
      Log.call.error(
        "Set hold request rejected by CallKit - id: \(id), error: \(error.localizedDescription)")
      throw error
    }
  }

  // MARK: - DTMF Support

  /// Sends DTMF tones for a call via CallKit.
  ///
  /// This sends a CXPlayDTMFCallAction to CallKit with the specified digits.
  ///
  /// - Parameters:
  ///   - id: The UUID of the call.
  ///   - digits: The DTMF digits to play (0-9, *, #).
  /// - Throws: An error if CallKit rejects the DTMF request.
  func playDTMF(for id: UUID, digits: String) async throws {
    Log.call.debug("Playing DTMF - id: \(id), length: \(digits.count)")
    let action = CXPlayDTMFCallAction(call: id, digits: digits, type: .singleTone)
    let transaction = CXTransaction(action: action)

    do {
      try await withCheckedThrowingContinuation {
        (continuation: CheckedContinuation<Void, Error>) in
        callController.request(transaction) { error in
          if let error = error {
            continuation.resume(throwing: error)
          } else {
            continuation.resume()
          }
        }
      }
      Log.call.debug("Play DTMF request accepted by CallKit - id: \(id)")
    } catch {
      Log.call.error(
        "Play DTMF request rejected by CallKit - id: \(id), error: \(error.localizedDescription)")
      throw error
    }
  }

}

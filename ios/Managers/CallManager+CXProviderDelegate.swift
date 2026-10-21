import AVFoundation
import CallKit
import UIKit
import os

extension CallManager: CXProviderDelegate {

  /// Timeout for waiting for the call to connect after answering.
  private static let answerCallTimeout: Duration = {
    let seconds =
      Bundle.main.object(
        forInfoDictionaryKey: "ExpoCallKitTelecomFulfillAnswerCallTimeout"
      ) as? Int ?? 30
    return .seconds(seconds)
  }()

  // MARK: - providerDidReset

  func providerDidReset(_ provider: CXProvider) {
    Log.call.debug("Provider did reset - removing all sessions")
    DialtonePlayer.shared.stop()
    cancelAllCallTimeouts()
    Task {
      await FulfillRequestManager.shared.cancelAll()
      await store.removeAll()
    }
  }

  // MARK: - CXStartCallAction

  func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
    Log.call.debug("CXStartCallAction - id: \(action.callUUID), isVideo: \(action.isVideo)")
    provider.reportOutgoingCall(with: action.callUUID, startedConnectingAt: Date())

    // Start timeout timer for outgoing call
    startCallTimeout(for: action.callUUID, timeout: Self.outgoingCallTimeout)

    Task {
      if let session = await store.session(for: action.callUUID),
        let participant = session.remoteParticipants.first
      {
        let update = CXCallUpdate()
        setLocalizedCallerNameIfNeeded(for: participant, on: update)
        provider.reportCall(with: action.callUUID, updated: update)
      }

      await store.updateStatus(for: action.callUUID, status: .connecting)

      await MainActor.run {
        CallEventEmitter.shared.send(OutgoingCallStartedEvent(id: action.callUUID))
      }
    }

    action.fulfill()
  }

  // MARK: - CXAnswerCallAction

  func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
    Log.call.debug("CXAnswerCallAction - id: \(action.callUUID)")

    // Cancel the incoming call timeout since user answered
    cancelCallTimeout(for: action.callUUID)

    Task {
      await store.updateStatus(for: action.callUUID, status: .connecting)

      // Create pending fulfill request with associated call ID
      let (requestId, resultTask) = await FulfillRequestManager.shared.createRequest(
        callId: action.callUUID,
        timeout: Self.answerCallTimeout
      )

      // Send event with request ID
      await MainActor.run {
        CallEventEmitter.shared.send(
          CallAnsweredEvent(id: action.callUUID, requestId: requestId)
        )
      }

      // Wait for fulfill or timeout
      let result = await resultTask.value

      switch result {
      case .fulfilled:
        Log.call.debug("CXAnswerCallAction fulfilled - id: \(action.callUUID)")
        action.fulfill()
      case .cancelled:
        Log.call.debug("CXAnswerCallAction failed - cancelled - id: \(action.callUUID)")
        action.fail()
      case .timedOut:
        Log.call.debug("CXAnswerCallAction failed - timeout - id: \(action.callUUID)")
        action.fail()
      }
    }
  }

  // MARK: - CXEndCallAction

  func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
    Log.call.debug("CXEndCallAction - id: \(action.callUUID)")

    // Stop dialtone and cancel timeout
    DialtonePlayer.shared.stop()
    cancelCallTimeout(for: action.callUUID)

    // The CallEndedEvent below is emitted from a Task, i.e. after action.fulfill()
    // has told CallKit the call is over. When PushKit launched the process in the
    // background (killed app, declined from the native UI) nothing else keeps iOS
    // from suspending it in that gap, so neither the event nor a native observer
    // of the undelivered-event notification would get to run. Hold a background
    // task across the hop; ended once the event is sent, or on expiry.
    // assumeIsolated: the provider's delegate queue is nil, so this runs on main.
    let endCallBackgroundTask = MainActor.assumeIsolated {
      let task = EndCallBackgroundTask()
      task.id = UIApplication.shared.beginBackgroundTask(
        withName: "ExpoCallKitTelecom.CXEndCallAction",
        expirationHandler: { task.end() })
      return task
    }

    Task {
      // Snapshot the session as ended so the embedded session reflects the terminal state.
      if var session = await store.session(for: action.callUUID) {
        session.status = .ended
        await MainActor.run {
          CallEventEmitter.shared.send(
            CallEndedEvent(id: action.callUUID, session: session))
        }
      }

      await store.remove(for: action.callUUID)

      await endCallBackgroundTask.end()
    }

    action.fulfill()
  }

  // MARK: - CXSetMutedCallAction

  func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
    Log.call.debug("CXSetMutedCallAction - id: \(action.callUUID), isMuted: \(action.isMuted)")

    Task {
      await store.updateMuted(for: action.callUUID, isMuted: action.isMuted)

      await MainActor.run {
        CallEventEmitter.shared.send(
          SetMutedActionEvent(id: action.callUUID, isMuted: action.isMuted))
      }
    }

    action.fulfill()
  }

  // MARK: - CXSetHeldCallAction

  func provider(_ provider: CXProvider, perform action: CXSetHeldCallAction) {
    Log.call.debug("CXSetHeldCallAction - id: \(action.callUUID), isOnHold: \(action.isOnHold)")

    Task {
      await store.updateHeld(for: action.callUUID, isOnHold: action.isOnHold)

      await MainActor.run {
        CallEventEmitter.shared.send(
          SetHeldActionEvent(id: action.callUUID, isOnHold: action.isOnHold))
      }
    }

    action.fulfill()
  }

  // MARK: - CXPlayDTMFCallAction

  func provider(_ provider: CXProvider, perform action: CXPlayDTMFCallAction) {
    Log.call.debug("CXPlayDTMFCallAction - id: \(action.callUUID), length: \(action.digits.count)")

    Task {
      await MainActor.run {
        CallEventEmitter.shared.send(DTMFEvent(id: action.callUUID, digits: action.digits))
      }
    }

    action.fulfill()
  }

  // MARK: - AVAudioSession didActivate

  func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
    Log.call.debug("CallKit audio session activated")

    Task {
      let sessions = await store.allSessions
      AudioManager.shared.onAVAudioSessionActivated(calls: sessions)

      // Play dialtone for outgoing calls that are still connecting
      if let session = sessions.first,
        session.origin == .outgoingApp || session.origin == .outgoingSystem,
        session.status == .connecting
      {
        DialtonePlayer.shared.play()
      }
    }
  }

  // MARK: - AVAudioSession didDeactivate

  func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
    Log.call.debug("CallKit audio session deactivated")
    DialtonePlayer.shared.stop()

    Task {
      let sessions = await store.allSessions
      AudioManager.shared.onAVAudioSessionDeactivated(calls: sessions)
    }
  }
}

/// The CXEndCallAction background task, ended exactly once: by the Task once
/// the event is sent, or by the expiration handler, whichever comes first.
/// Main-actor isolated so the compiler checks that `end()` can't race.
@MainActor
private final class EndCallBackgroundTask {
  var id = UIBackgroundTaskIdentifier.invalid

  func end() {
    guard id != .invalid else { return }
    UIApplication.shared.endBackgroundTask(id)
    id = .invalid
  }
}

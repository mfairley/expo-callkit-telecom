import AVFoundation
import CallKit
import Foundation

/// Protocol for events that can be sent to JS.
protocol CallEvent {
  static var name: String { get }
  var body: [String: Any] { get }
}

// MARK: - Constants

private enum CallEventKeys {
  static let id = "id"
}

// MARK: - Call Session Events

struct CallSessionAddedEvent: CallEvent {
  static let name = "onCallSessionAdded"

  let session: CallSession

  var body: [String: Any] {
    ["session": session.toDictionary()]
  }
}

struct CallSessionUpdatedEvent: CallEvent {
  static let name = "onCallSessionUpdated"

  let session: CallSession

  var body: [String: Any] {
    ["session": session.toDictionary()]
  }
}

struct CallSessionRemovedEvent: CallEvent {
  static let name = "onCallSessionRemoved"

  let id: UUID

  var body: [String: Any] {
    [CallEventKeys.id: id.uuidString]
  }
}

// MARK: - Audio Session Events

struct AudioSessionCallInfo {
  let id: UUID
  let status: CallSession.Status

  init(from session: CallSession) {
    self.id = session.id
    self.status = session.status
  }

  func toDictionary() -> [String: Any] {
    [
      "id": id.uuidString,
      "status": status.rawValue,
    ]
  }
}

struct AudioSessionActivatedEvent: CallEvent {
  static let name = "onAudioSessionActivated"

  let calls: [AudioSessionCallInfo]

  var body: [String: Any] {
    ["calls": calls.map { $0.toDictionary() }]
  }
}

struct AudioSessionDeactivatedEvent: CallEvent {
  static let name = "onAudioSessionDeactivated"

  let calls: [AudioSessionCallInfo]

  var body: [String: Any] {
    ["calls": calls.map { $0.toDictionary() }]
  }
}

struct AudioRouteChangedEvent: CallEvent {
  static let name = "onAudioRouteChanged"

  let inputs: [[String: String]]
  let outputs: [[String: String]]

  var body: [String: Any] {
    [
      "currentRoute": [
        "inputs": inputs,
        "outputs": outputs,
      ]
    ]
  }
}

// MARK: - Call Intent Events

struct CallIntentReceivedEvent: CallEvent {
  static let name = "onCallIntentReceived"

  let handle: String
  let handleType: String
  let hasVideo: Bool

  var body: [String: Any] {
    [
      "handle": handle,
      "handleType": handleType,
      "hasVideo": hasVideo,
    ]
  }
}

// MARK: - Call Action Events

struct IncomingCallReportedEvent: CallEvent {
  static let name = "onIncomingCallReported"

  let id: UUID

  var body: [String: Any] {
    [CallEventKeys.id: id.uuidString]
  }
}

struct OutgoingCallStartedEvent: CallEvent {
  static let name = "onOutgoingCallStarted"

  let id: UUID

  var body: [String: Any] {
    [CallEventKeys.id: id.uuidString]
  }
}

struct CallAnsweredEvent: CallEvent {
  static let name = "onCallAnswered"

  let id: UUID
  let requestId: UUID

  var body: [String: Any] {
    [
      CallEventKeys.id: id.uuidString,
      "requestId": requestId.uuidString,
    ]
  }
}

struct CallEndedEvent: CallEvent {
  static let name = "onCallEnded"

  let id: UUID

  var body: [String: Any] {
    [CallEventKeys.id: id.uuidString]
  }
}

struct CallReportedEnded: CallEvent {
  static let name = "onCallReportedEnded"

  let id: UUID
  let reason: CXCallEndedReason

  var body: [String: Any] {
    [
      CallEventKeys.id: id.uuidString,
      "reason": Self.reasonString(for: reason),
    ]
  }

  private static func reasonString(for reason: CXCallEndedReason) -> String {
    switch reason {
    case .failed: return "failed"
    case .remoteEnded: return "remoteEnded"
    case .unanswered: return "unanswered"
    case .answeredElsewhere: return "answeredElsewhere"
    case .declinedElsewhere: return "declinedElsewhere"
    @unknown default: return "unknown"
    }
  }
}

struct SetMutedActionEvent: CallEvent {
  static let name = "onSetMutedAction"

  let id: UUID
  let isMuted: Bool

  var body: [String: Any] {
    [
      CallEventKeys.id: id.uuidString,
      "isMuted": isMuted,
    ]
  }
}

struct SetHeldActionEvent: CallEvent {
  static let name = "onSetHeldAction"

  let id: UUID
  let isOnHold: Bool

  var body: [String: Any] {
    [
      CallEventKeys.id: id.uuidString,
      "isOnHold": isOnHold,
    ]
  }
}

struct DTMFEvent: CallEvent {
  static let name = "onDTMF"

  let id: UUID
  let digits: String

  var body: [String: Any] {
    [
      CallEventKeys.id: id.uuidString,
      "digits": digits,
    ]
  }
}

struct VideoChangedEvent: CallEvent {
  static let name = "onVideoChanged"

  let id: UUID
  let hasVideo: Bool

  var body: [String: Any] {
    [
      CallEventKeys.id: id.uuidString,
      "hasVideo": hasVideo,
    ]
  }
}

// MARK: - VoIP Push Events

struct VoIPPushTokenUpdatedEvent: CallEvent {
  static let name = "onVoIPPushTokenUpdated"

  let token: String?

  var body: [String: Any] {
    var result: [String: Any] = ["type": "APNS_VOIP"]
    if let token = token {
      result["token"] = token
    }
    return result
  }
}

import Foundation

/// Represents an active CallKit session.
struct CallSession: Equatable {
  let id: UUID
  var options: CallOptions
  let origin: Origin
  let remoteParticipants: [CallParticipant]
  let incomingCallEvent: IncomingCallEvent?
  var status: Status
  var connectedAt: Date?
  var isMuted: Bool
  var isOnHold: Bool
  var dtmfDigits: String?

  enum Origin: String {
    case incoming
    case outgoingApp
    case outgoingSystem
  }

  enum Status: String {
    case requesting
    case ringing
    case connecting
    case connected
    case ended
  }
}

// MARK: - Serialization

extension CallSession {
  func toDictionary() -> [String: Any] {
    var dict: [String: Any] = [
      "id": id.uuidString,
      "origin": origin.rawValue,
      "options": [
        "hasVideo": options.hasVideo
      ],
      "remoteParticipants": remoteParticipants.map { participant in
        var p: [String: Any] = [
          "id": participant.id
        ]
        if let phoneNumber = participant.phoneNumber {
          p["phoneNumber"] = phoneNumber
        }
        if let email = participant.email {
          p["email"] = email
        }
        if let displayName = participant.displayName {
          p["displayName"] = displayName
        }
        if let avatarUrl = participant.avatarUrl {
          p["avatarUrl"] = avatarUrl
        }
        return p
      },
      "status": status.rawValue,
      "isMuted": isMuted,
      "isOnHold": isOnHold,
    ]

    if let dtmfDigits = dtmfDigits {
      dict["dtmfDigits"] = dtmfDigits
    }

    if let connectedAt = connectedAt {
      dict["connectedAt"] = ISO8601DateFormatter().string(
        from: connectedAt
      )
    }

    if let incomingCallEvent = incomingCallEvent {
      dict["incomingCallEvent"] = incomingCallEvent.toDictionary()
    }

    return dict
  }
}

import ExpoModulesCore
import Foundation

/// A validated incoming call event, parsed from a push payload or supplied
/// directly by JS via `reportIncomingCall`.
///
/// Mirrors the TS `IncomingCallEvent` declared in `src/Calls.types.ts`.
struct IncomingCallEvent: Equatable {
  let eventId: String
  /// Server-assigned id for this call (distinct from the native UUID assigned
  /// by CallKit). Use this to talk to your backend about the call.
  let serverCallId: String
  let caller: Caller
  let hasVideo: Bool
  let startedAt: Date?
  /// App-defined extra fields forwarded verbatim from the push payload.
  /// Opaque to the library; excluded from `==` since `[String: Any]` is not
  /// `Equatable` and events with the same `eventId` are treated as equivalent.
  let metadata: [String: Any]?

  struct Caller: Equatable {
    let id: String
    let displayName: String?
    let avatarUrl: String?
    let phoneNumber: String?
    let email: String?
  }

  static func == (lhs: IncomingCallEvent, rhs: IncomingCallEvent) -> Bool {
    lhs.eventId == rhs.eventId
      && lhs.serverCallId == rhs.serverCallId
      && lhs.caller == rhs.caller
      && lhs.hasVideo == rhs.hasVideo
      && lhs.startedAt == rhs.startedAt
  }

  func toDictionary() -> [String: Any] {
    var dict: [String: Any] = [
      "eventId": eventId,
      "serverCallId": serverCallId,
      "hasVideo": hasVideo,
      "caller": {
        var c: [String: Any] = ["id": caller.id]
        if let displayName = caller.displayName {
          c["displayName"] = displayName
        }
        if let avatarUrl = caller.avatarUrl {
          c["avatarUrl"] = avatarUrl
        }
        if let phoneNumber = caller.phoneNumber {
          c["phoneNumber"] = phoneNumber
        }
        if let email = caller.email {
          c["email"] = email
        }
        return c
      }(),
    ]
    if let startedAt = startedAt {
      dict["startedAt"] = ISO8601DateFormatter().string(from: startedAt)
    }
    if let metadata = metadata {
      dict["metadata"] = metadata
    }
    return dict
  }
}

// MARK: - Expo Records (JS → Native, via `reportIncomingCall`)

struct IncomingCallCallerRecord: Record {
  @Field
  var id: String = ""

  @Field
  var displayName: String?

  @Field
  var avatarUrl: String?

  @Field
  var phoneNumber: String?

  @Field
  var email: String?
}

struct IncomingCallEventRecord: Record {
  @Field
  var eventId: String = ""

  @Field
  var serverCallId: String = ""

  @Field
  var caller: IncomingCallCallerRecord = IncomingCallCallerRecord()

  @Field
  var hasVideo: Bool = false

  @Field
  var startedAt: String?

  @Field
  var metadata: [String: Any]?

  func toModel() throws -> IncomingCallEvent {
    let startedAtDate: Date?
    if let raw = startedAt, !raw.isEmpty {
      startedAtDate = IncomingCallEventParser.parseRFC3339Date(raw) ?? Date()
    } else {
      startedAtDate = nil
    }
    return IncomingCallEvent(
      eventId: eventId,
      serverCallId: serverCallId,
      caller: IncomingCallEvent.Caller(
        id: caller.id,
        displayName: caller.displayName,
        avatarUrl: caller.avatarUrl,
        phoneNumber: caller.phoneNumber,
        email: caller.email
      ),
      hasVideo: hasVideo,
      startedAt: startedAtDate,
      metadata: metadata
    )
  }
}

// MARK: - Push-payload parsing (PushKit / FCM dictionary → model)

/// Parses an `IncomingCallEvent` from a VoIP push payload.
///
/// The payload must wrap the event under the top-level key `"incoming_call"`.
/// There is no fallback to a flat top-level shape. Inner keys are camelCase
/// to match the TS contract and the example server's wire format.
enum IncomingCallEventParser {
  static func parse(from payload: [AnyHashable: Any]) -> IncomingCallEvent? {
    guard let event = payload["incoming_call"] as? [AnyHashable: Any] else {
      return nil
    }

    let eventId = event["eventId"] as? String ?? ""
    let serverCallId = event["serverCallId"] as? String ?? ""
    guard !eventId.isEmpty, !serverCallId.isEmpty else {
      return nil
    }

    guard let callerDict = event["caller"] as? [AnyHashable: Any],
      let callerId = callerDict["id"] as? String,
      !callerId.isEmpty
    else {
      return nil
    }
    let caller = IncomingCallEvent.Caller(
      id: callerId,
      displayName: callerDict["displayName"] as? String,
      avatarUrl: callerDict["avatarUrl"] as? String,
      phoneNumber: callerDict["phoneNumber"] as? String,
      email: callerDict["email"] as? String
    )

    let hasVideo = event["hasVideo"] as? Bool ?? false

    let startedAt: Date?
    if let raw = event["startedAt"] as? String {
      startedAt = parseRFC3339Date(raw)
    } else {
      startedAt = nil
    }

    let metadata = event["metadata"] as? [String: Any]

    return IncomingCallEvent(
      eventId: eventId,
      serverCallId: serverCallId,
      caller: caller,
      hasVideo: hasVideo,
      startedAt: startedAt,
      metadata: metadata
    )
  }

  /// Parses an RFC 3339 timestamp. Handles optional fractional seconds.
  static func parseRFC3339Date(_ string: String) -> Date? {
    let withFraction = ISO8601DateFormatter()
    withFraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    if let date = withFraction.date(from: string) {
      return date
    }
    let noFraction = ISO8601DateFormatter()
    noFraction.formatOptions = [.withInternetDateTime]
    return noFraction.date(from: string)
  }
}

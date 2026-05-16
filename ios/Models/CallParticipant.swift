import ExpoModulesCore
import Foundation

struct CallParticipant: Equatable {
  let id: String
  let phoneNumber: String?
  let email: String?
  let displayName: String?
  let avatarUrl: String?
}

struct CallParticipantRecord: Record {
  @Field
  var id: String

  @Field
  var phoneNumber: String?

  @Field
  var email: String?

  @Field
  var displayName: String?

  @Field
  var avatarUrl: String?

  func toModel() -> CallParticipant {
    CallParticipant(
      id: id,
      phoneNumber: phoneNumber,
      email: email,
      displayName: displayName,
      avatarUrl: avatarUrl
    )
  }
}

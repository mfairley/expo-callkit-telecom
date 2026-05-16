import ExpoModulesCore
import Foundation

struct CallOptions: Equatable {
  var hasVideo: Bool
}

struct CallOptionsRecord: Record {
  @Field
  var hasVideo: Bool = false

  func toModel() -> CallOptions {
    CallOptions(hasVideo: hasVideo)
  }
}

import ExpoModulesCore
import Foundation

struct CallOptions: Equatable {
  var hasVideo: Bool
  var supportsHolding: Bool = true
  var supportsDTMF: Bool = true
}

struct CallOptionsRecord: Record {
  @Field
  var hasVideo: Bool = false

  @Field
  var supportsHolding: Bool = true

  @Field
  var supportsDTMF: Bool = true

  func toModel() -> CallOptions {
    CallOptions(hasVideo: hasVideo, supportsHolding: supportsHolding, supportsDTMF: supportsDTMF)
  }
}

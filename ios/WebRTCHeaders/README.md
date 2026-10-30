# WebRTC headers

Declarations for the few WebRTC and react-native-webrtc classes this module calls, exposed to
Swift as the private `ExpoCallKitTelecomWebRTC` module.

The pod doesn't depend on a WebRTC pod. These headers let the Swift code compile, and the classes
resolve against whichever WebRTC build the app ships: `@livekit/react-native-webrtc`, upstream
`react-native-webrtc`, Fishjam, etc.

WebRTC builds don't all use the same class names. LiveKit's (the `LiveKitWebRTC` pod, used by
`@livekit/react-native-webrtc` >= 144.2.0) prefixes its classes with `LK`, so `RTCAudioSession` is
`LKRTCAudioSession`. The WebRTC classes are therefore declared as protocols, and the inline
functions in these headers look the class up at runtime, `LK`-prefixed name first. Don't declare
them as `@interface`: that makes the pod reference one specific class name at link time, which
fails to link against builds that use the other name. `WebRTCModuleOptions` comes from
react-native-webrtc itself, which every fork names the same, so it stays an `@interface`.

Swift files import the module with `@_implementationOnly import`, which keeps it out of the
compiled `ExpoCallKitTelecom` module, so the app target never has to find these headers.

Keep these in sync with the upstream headers when adding calls. WebRTC declarations are under
WebRTC's BSD license (https://webrtc.googlesource.com/src/+/main/LICENSE); react-native-webrtc
declarations are under its MIT license.

# WebRTC headers

Declarations for the few WebRTC and react-native-webrtc classes this module calls, exposed to
Swift as the private `ExpoCallKitTelecomWebRTC` module.

The pod doesn't depend on a WebRTC pod. These headers let the Swift code compile, and because the
pod is a static framework, the class references resolve when the app links against whichever
WebRTC build it ships: `@livekit/react-native-webrtc`, upstream `react-native-webrtc`, Fishjam,
etc. All of them vend these classes under the same names.

Keep these in sync with the upstream headers when adding calls. WebRTC declarations are under
WebRTC's BSD license (https://webrtc.googlesource.com/src/+/main/LICENSE); react-native-webrtc
declarations are under its MIT license.

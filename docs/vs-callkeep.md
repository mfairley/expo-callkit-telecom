# `expo-callkit-telecom` vs `react-native-callkeep`

[`react-native-callkeep`](https://github.com/react-native-webrtc/react-native-callkeep) is the long-standing option for putting a native call UI on a React Native app. `expo-callkit-telecom` covers the same surface area but makes different choices — most of them driven by what has changed on the platform side since callkeep was written.

This page exists so you can decide which one to use without reading both source trees.

## TL;DR — should I use this?

Use **`expo-callkit-telecom`** if:

- You're on Expo (managed or with EAS) and want a config plugin that handles entitlements, background modes, permissions, ringtone bundling, and FCM service registration for you.
- You're using LiveKit or any other WebRTC stack with manual audio, and you've been fighting `AVAudioSession` vs `RTCAudioSession` coordination on iOS.
- You want incoming calls to work from a terminated state without writing your own PushKit / FCM glue.
- You're OK with iOS 15.1+ and Android API 26+.

Use **`react-native-callkeep`** if:

- You need to support Android API 23–25.
- You're on a bare React Native app, not Expo, and the callkeep API is already wired up.
- You're happy maintaining your own PushKit / FCM bridge.

## Side-by-side

| | `expo-callkit-telecom` | `react-native-callkeep` |
| --- | --- | --- |
| Android backend | Jetpack `androidx.core:core-telecom` (Google-recommended path forward) | Classic `android.telecom.ConnectionService` |
| Min Android SDK | 26 | 23 |
| Native languages | Swift + Kotlin | Objective-C + Java |
| VoIP push parsing | Parsed natively *before* JS is running, on both iOS (APNs VoIP) and Android (FCM data) | Not included — wire `pushRegistry:didReceiveIncomingPushWithPayload:` (or `react-native-voip-push-notification`) and FCM yourself |
| iOS audio session | Integrates with WebRTC's `RTCAudioSession` — manual-audio WebRTC stacks (LiveKit, plain WebRTC) work without extra wiring | Manipulates `AVAudioSession` directly; you coordinate `RTCAudioSession` from the app |
| Android incoming-call UI | Module owns the foreground service, notification, and full-screen intent — you don't wire any of that up | App is responsible for wiring service + notification |
| API shape | One typed `CallSession` object; one set of verbs (`request` / `report` / `fulfill`) that work the same on both platforms | Options split into `{ ios: {...}, android: {...} }`; several methods are platform-only |
| Expo config plugin | Yes — entitlements, background modes, permissions, ringtone bundling, FCM service registration | No (community plugins exist) |
| Tested with | iOS 26 / Android 15, real devices, LiveKit as the media transport | Long history of community usage across many stacks |

## On the Android backend specifically

`ConnectionService` predates `core-telecom` and is the API everyone wrote against because it's all that existed. `androidx.core:core-telecom` is Google's newer, opinionated wrapper that owns the foreground service, the incoming-call notification, and the full-screen intent on your behalf. The trade-off is `minSdk 26` instead of 23. If you don't need to support API 23–25, you save a real amount of bring-up code.

## On the iOS audio session specifically

The iOS audio session is the part of CallKit integration that breaks most often, because there are two parties ( `AVAudioSession` and WebRTC's `RTCAudioSession`) that both want to own the route, and CallKit will deactivate the session on you in ways that surprise both of them. `expo-callkit-telecom` coordinates with `RTCAudioSession` in manual-audio mode, which is what LiveKit (and any "I want to control the mic/speaker myself" WebRTC stack) expects. This is the integration that costs teams the most calendar time when they roll their own.

## On VoIP push

CallKit (and Android's equivalent) requires the call to be reported to the OS *within seconds* of the push arriving, including from a terminated state. With callkeep, you wire that up yourself: a PushKit delegate on iOS, an FCM service on Android, and code in both to call back into JS in the right order. `expo-callkit-telecom` parses both transports natively and reports the call to the OS before JS is running, so the cold-start case "just works" if your payload matches the documented shape.

## Migration sketch

If you're already on callkeep, the move is mostly mechanical:

1. Replace `RNCallKeep.setup({ ios, android })` with the `expo-callkit-telecom` config plugin in `app.json`.
2. Replace `RNCallKeep.startCall(uuid, handle)` with `Calls.startOutgoingCall(...)`.
3. Replace `RNCallKeep.displayIncomingCall(...)` with `Calls.reportIncomingCall(...)` — though if you've moved your push parsing into the module, you usually don't need to call this from JS at all.
4. Replace the various `RNCallKeep.addEventListener('answerCall', ...)` calls with `Calls.addCallAnsweredListener(...)` and friends.
5. Delete your PushKit / FCM glue.

The most subtle change is on iOS: callkeep leaves `RTCAudioSession` alone, so apps tend to grow workarounds for audio routing edge cases. With `expo-callkit-telecom` those workarounds are usually wrong and should be removed before testing.

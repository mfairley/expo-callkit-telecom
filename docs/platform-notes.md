---
description: Platform requirements for expo-callkit-telecom — iOS 15.1+ (CallKit + PushKit), Android API 26+ (Jetpack androidx.core-telecom). Keep-alive notes for background WebSocket signalling.
---

# Platform notes

## iOS

- Requires the `voip` background mode and a VoIP push certificate.
- Uses [CallKit](https://developer.apple.com/documentation/callkit) + [PushKit](https://developer.apple.com/documentation/pushkit) + [WebRTC](https://webrtc.org/)'s `RTCAudioSession` for manual audio control.
- Minimum iOS version: **15.1**.

## Android

- Requires [`MANAGE_OWN_CALLS`](https://developer.android.com/reference/android/Manifest.permission#MANAGE_OWN_CALLS) permission.
- Minimum SDK: **26** (Android 8.0).
- Uses [`androidx.core:core-telecom`](https://developer.android.com/develop/connectivity/telecom/voip-app/telecom).
- Incoming calls come via [FCM](https://firebase.google.com/docs/cloud-messaging) data messages — the config plugin registers `ExpoCallKitTelecomMessagingService` automatically.

## VoIP push token types

The VoIP push token type is reported as `"APNS_VOIP"` on iOS and `"FCM"` on Android — send both to your backend so it knows which transport to use.

## Keeping connections alive in the background

This module hands the OS a CallKit/Core-Telecom call, which keeps the *process* alive during a call — but JS timers (`setInterval`, `setTimeout`) and JS-side network heartbeats are still subject to background throttling once the screen locks. If your media stack needs an app-level heartbeat (e.g. a WebSocket signalling channel) to survive the background, pair this module with [`react-native-nitro-keepalive-timer`](https://www.npmjs.com/package/react-native-nitro-keepalive-timer) to get native timers that fire reliably while a call is active.

---
layout: home
description: Open-source Expo module for native calling UI on iOS and Android. Wraps CallKit (PushKit) and Jetpack androidx.core-telecom with one typed API, native VoIP push parsing, and a LiveKit-friendly audio session.

hero:
  name: expo-callkit-telecom
  text: Native calling UI for Expo
  tagline: CallKit on iOS, Jetpack Core-Telecom on Android. One typed API. VoIP push parsed natively. LiveKit-friendly audio session.
  actions:
    - theme: brand
      text: Getting started
      link: /getting-started
    - theme: alt
      text: View on GitHub
      link: https://github.com/mfairley/expo-callkit-telecom
    - theme: alt
      text: vs react-native-callkeep
      link: /vs-callkeep

features:
  - icon: 📱
    title: Native call UI
    details: '<a href="https://developer.apple.com/documentation/callkit">CallKit</a> on iOS, <a href="https://developer.android.com/reference/androidx/core/telecom/package-summary">Jetpack Core-Telecom</a> incoming-call notification + full-screen intent on Android. The module owns the system call UI, the audio session, and VoIP push — your app owns the media.'
  - icon: 🔔
    title: VoIP push, parsed natively
    details: '<a href="https://developer.apple.com/documentation/usernotifications/sending-voip-pushes">APNs VoIP</a> (<a href="https://developer.apple.com/documentation/pushkit">PushKit</a>) on iOS, <a href="https://firebase.google.com/docs/cloud-messaging">FCM</a> data messages on Android. Calls report to the OS before JS is running, so the cold-start case works without app-side glue.'
  - icon: 🎧
    title: LiveKit-friendly audio
    details: 'Integrates with <a href="https://webrtc.org/">WebRTC</a>''s <code>RTCAudioSession</code> in manual-audio mode, so <a href="https://livekit.io/">LiveKit</a> and plain WebRTC stacks work without app-side <a href="https://developer.apple.com/documentation/avfaudio/avaudiosession">AVAudioSession</a> coordination.'
  - icon: 🧩
    title: Typed, unified API
    details: 'One <code>CallSession</code> object. Three verbs — request, report, fulfill — that work the same on both platforms. No platform-only methods to keep straight.'
  - icon: 🛠️
    title: Expo config plugin
    details: '<a href="https://docs.expo.dev/config-plugins/introduction/">Expo config plugin</a> handles entitlements, background modes, microphone permission, ringtone bundling, and FCM service registration at prebuild.'
  - icon: 🆕
    title: Current platform APIs
    details: '<a href="https://www.swift.org/">Swift</a> on iOS (15.1+), <a href="https://kotlinlang.org/">Kotlin</a> + <a href="https://developer.android.com/jetpack/androidx/releases/core-telecom">androidx.core-telecom</a> on Android (minSdk 26). The path Google and Apple are pushing forward.'
---

## See it in action on iOS and Android

The runnable `example/` app drives the system call UI on both platforms — outgoing calls, banner-style incoming calls (while the device is in use), and full-screen incoming calls (when locked).

<DemoGrid />

## Verified against

This release is exercised end-to-end on real devices via the runnable `example/` app.

| | Tested against |
| --- | --- |
| [iOS](https://developer.apple.com/ios/) | 26 (minimum 15.1) |
| [Android](https://developer.android.com/about/versions) | 15 (minimum API 26) |
| [Expo SDK](https://expo.dev/changelog/sdk-55) | 55 |
| [React Native](https://reactnative.dev/) | 0.83 |
| [New Architecture](https://reactnative.dev/architecture/landing-page) | Yes |
| Media transport | [LiveKit RN SDK](https://github.com/livekit/client-sdk-react-native) |

## References for CallKit, Core-Telecom, VoIP push, and LiveKit

Authoritative references for the platform APIs this module is built on:

**Apple (iOS):**

- [CallKit](https://developer.apple.com/documentation/callkit) — the framework that owns the system call UI
- [PushKit](https://developer.apple.com/documentation/pushkit) — VoIP push delivery on iOS
- [Sending VoIP pushes through APNs](https://developer.apple.com/documentation/usernotifications/sending-voip-pushes) — the canonical payload doc
- [AVAudioSession](https://developer.apple.com/documentation/avfaudio/avaudiosession) — iOS audio session API

**Google (Android):**

- [Jetpack `androidx.core-telecom`](https://developer.android.com/jetpack/androidx/releases/core-telecom) — Google's recommended VoIP integration path (supersedes `ConnectionService`)
- [`androidx.core.telecom` reference](https://developer.android.com/reference/androidx/core/telecom/package-summary) — API surface
- [Firebase Cloud Messaging (FCM)](https://firebase.google.com/docs/cloud-messaging) — push delivery on Android

**Expo / React Native:**

- [Expo](https://expo.dev/) and the [Expo Modules API](https://docs.expo.dev/modules/overview/)
- [Expo config plugins](https://docs.expo.dev/config-plugins/introduction/)
- [React Native](https://reactnative.dev/) and the [New Architecture](https://reactnative.dev/architecture/landing-page)

**WebRTC / media:**

- [WebRTC.org](https://webrtc.org/)
- [LiveKit](https://livekit.io/) and the [LiveKit React Native SDK](https://github.com/livekit/client-sdk-react-native)


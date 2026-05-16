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
    details: CallKit on iOS, Telecom incoming-call notification + full-screen intent on Android. The module owns the system call UI, the audio session, and VoIP push — your app owns the media.
  - icon: 🔔
    title: VoIP push, parsed natively
    details: APNs VoIP (PushKit) on iOS, FCM data messages on Android. Calls report to the OS before JS is running, so the cold-start case works without app-side glue.
  - icon: 🎧
    title: LiveKit-friendly audio
    details: Integrates with WebRTC's RTCAudioSession in manual-audio mode, so LiveKit and plain WebRTC stacks work without app-side AVAudioSession coordination.
  - icon: 🧩
    title: Typed, unified API
    details: One CallSession object. Three verbs — request, report, fulfill — that work the same on both platforms. No platform-only methods to keep straight.
  - icon: 🛠️
    title: Expo config plugin
    details: Entitlements, background modes, microphone permission, ringtone bundling, and FCM service registration all handled at prebuild.
  - icon: 🆕
    title: Current platform APIs
    details: Swift on iOS (15.1+), Kotlin + androidx.core-telecom on Android (minSdk 26). The path Google and Apple are pushing forward.
---

## Verified against

This release is exercised end-to-end on real devices via the runnable `example/` app.

| | Tested against |
| --- | --- |
| iOS | 26 (minimum 15.1) |
| Android | 15 (minimum API 26) |
| Expo SDK | 55 |
| React Native | 0.83 |
| New Architecture | Yes |
| Media transport | LiveKit RN SDK |


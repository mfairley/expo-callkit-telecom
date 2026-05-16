# Changelog

All notable changes to `expo-callkit-telecom` are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.6] — 2026-05-16

### Changed
- API reference sections now appear in a learn-the-module order (Sessions → Requests → Reporters → Fulfillers → Call Events → Audio → Audio Events → Capture → VoIP Push → Hooks → Permissions → Core) instead of alphabetical.
- Within each interface, type, and category, members are now listed in source order — so `CallParticipant` reads `id` → `displayName` → `avatarUrl` → `phoneNumber` → `email` as defined, rather than alphabetically.

### Removed
- The self-referential "each release updates this matrix…" sentence following the Verified-against table in `README.md`, `docs/index.md`, and `docs/vs-callkeep.md`. The table itself stays; the meta-commentary was unnecessary.

## [0.2.5] — 2026-05-16

### Changed
- API reference page now groups items into 12 themed sections (Sessions, Requests, Reporters, Fulfillers, Call Events, Audio, Audio Events, Capture, VoIP Push, Permissions, Hooks, Core). Added `@category` tags to all exports in `src/Calls.ts` and `src/Calls.types.ts`, including the previously-uncategorized session functions and every type alias.
- Every interface and type alias now carries a one-line summary, replacing the empty placeholders and the repeated "Base type for all native events with metadata" inherited descriptions on event interfaces.

## [0.2.4] — 2026-05-16

### Added
- Documentation site at https://mfairley.github.io/expo-callkit-telecom/ — VitePress with auto-generated typedoc API reference, deployed via a GitHub Actions workflow on push to `main`.
- "Verified against" matrix in the README and docs site, listing the iOS / Android / Expo SDK / React Native / New Architecture / media-transport versions exercised end-to-end on real devices in each release.
- `docs:dev` / `docs:build` / `docs:preview` / `docs:api` scripts.

### Changed
- Reframed the `react-native-callkeep` comparison around architectural choices (Jetpack `androidx.core:core-telecom`, Swift + Kotlin, Expo Modules API, `RTCAudioSession` manual-audio coordination, native VoIP push parsing) — framing tied to platform recommendations rather than to any other library's release cadence.
- `docs/` and `marketing/` excluded from the npm tarball.

## [0.2.3] — 2026-05-16

### Added
- `CHANGELOG.md`.
- `docs/vs-callkeep.md` — standalone migration / comparison guide.

### Changed
- Tightened the npm description and expanded keywords for discoverability.
- Trimmed the README callkeep comparison; full side-by-side now lives at [`docs/vs-callkeep.md`](docs/vs-callkeep.md).

## [0.2.2] — 2026-05-16

### Changed
- Moved README badges below the intro for consistency with sibling repo.

## [0.2.1] — 2026-05-16

### Fixed
- Dropped `.svg` suffix from the license badge URL.

### Changed
- Auto-create a GitHub release on publish; README polish.
- Applied `swift-format` and `ktfmt` across the native sources.

## [0.2.0] — 2026-05-15

### Added
- Initial public implementation:
  - CallKit (iOS) + Jetpack `androidx.core:core-telecom` (Android) with a single typed `CallSession` and parity API (`request` / `report` / `fulfill`).
  - Native VoIP push parsing: APNs VoIP (PushKit) on iOS, FCM data messages on Android — calls can be reported from a terminated state.
  - System ringtone for incoming calls and looped dialtone (with fade-in) for outgoing calls; both configurable via the config plugin.
  - Cross-platform audio session management with typed port types and live route-change events; integrates with WebRTC's `RTCAudioSession` for manual-audio stacks (LiveKit, plain WebRTC).
  - Mute / hold / video / DTMF, both `app → system` and `system → app`.
  - iOS call intents: Recents list and Siri ("call Jane").
  - Config plugin: entitlements, background modes, microphone permission, ringtone/dialtone bundling, FCM service registration.
- CI: trusted publishing on Node 24 / npm 11.

[0.2.6]: https://github.com/mfairley/expo-callkit-telecom/releases/tag/v0.2.6
[0.2.5]: https://github.com/mfairley/expo-callkit-telecom/releases/tag/v0.2.5
[0.2.4]: https://github.com/mfairley/expo-callkit-telecom/releases/tag/v0.2.4
[0.2.3]: https://github.com/mfairley/expo-callkit-telecom/releases/tag/v0.2.3
[0.2.2]: https://github.com/mfairley/expo-callkit-telecom/releases/tag/v0.2.2
[0.2.1]: https://github.com/mfairley/expo-callkit-telecom/releases/tag/v0.2.1
[0.2.0]: https://github.com/mfairley/expo-callkit-telecom/releases/tag/v0.2.0

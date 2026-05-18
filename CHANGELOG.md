# Changelog

All notable changes to `expo-callkit-telecom` are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.5](https://github.com/mfairley/expo-callkit-telecom/compare/v0.3.4...v0.3.5) (2026-05-18)


### Documentation

* clean up keywords ([92560e1](https://github.com/mfairley/expo-callkit-telecom/commit/92560e1549e1367d5ac2619769ee41a2054df4f1))

## [0.3.4](https://github.com/mfairley/expo-callkit-telecom/compare/v0.3.3...v0.3.4) (2026-05-17)


### Documentation

* recommend wav audio format ([cdeed74](https://github.com/mfairley/expo-callkit-telecom/commit/cdeed74f38ee3072d6ab1382c8ec8ab019fa9403))

## [0.3.3](https://github.com/mfairley/expo-callkit-telecom/compare/v0.3.2...v0.3.3) (2026-05-17)


### Documentation

* add Cloudflare Web Analytics ([36906d7](https://github.com/mfairley/expo-callkit-telecom/commit/36906d752aeef82639e50343a6a4044dc3ebb422))
* tighten doc wording ([b841554](https://github.com/mfairley/expo-callkit-telecom/commit/b841554ddf7a8393d0818d3b5af9ed065628605e))

## [0.3.2](https://github.com/mfairley/expo-callkit-telecom/compare/v0.3.1...v0.3.2) (2026-05-17)


### Bug Fixes

* expose app.plugin in exports field so prebuild can resolve plugin ([559ad5a](https://github.com/mfairley/expo-callkit-telecom/commit/559ad5af6ea8d9ba91f912c6adee2dad92b2e296))


### Documentation

* fix layout of video on mobile ([4d1d85c](https://github.com/mfairley/expo-callkit-telecom/commit/4d1d85cf0f6169f388b9f84f45eca826c98a568b))

## [0.3.1](https://github.com/mfairley/expo-callkit-telecom/compare/v0.3.0...v0.3.1) (2026-05-17)


### Documentation

* improve wording of docs ([39c73e8](https://github.com/mfairley/expo-callkit-telecom/commit/39c73e82f6891039896a0368c5e76bbb5ca9ec35))

## [0.3.0](https://github.com/mfairley/expo-callkit-telecom/compare/v0.2.8...v0.3.0) (2026-05-17)


### Features

* **docs:** branded icon, demo gallery, social cards, SEO outlinks ([33f8bc3](https://github.com/mfairley/expo-callkit-telecom/commit/33f8bc3d6329fe04ca6f76eca452b481941fef64))


### Documentation

* **readme:** swap &lt;video&gt; for clickable poster &lt;img&gt; ([584dd1f](https://github.com/mfairley/expo-callkit-telecom/commit/584dd1f717766536e87c3b4cef3196412e606045))

## [0.2.8](https://github.com/mfairley/expo-callkit-telecom/compare/v0.2.7...v0.2.8) (2026-05-16)


### Documentation

* cross-link related functions and add per-page SEO descriptions ([c03b2d4](https://github.com/mfairley/expo-callkit-telecom/commit/c03b2d4420bd15f2fd40c1ad1f20112bedc9a122))

## [0.2.7] — 2026-05-16

### Added
- VitePress now emits a `sitemap.xml` on each docs build for better search-engine crawling.
- Prominent "📖 Full documentation" link near the top of the README, plus a `docs` badge in the badge row.
- `SECURITY.md` describing how to report vulnerabilities via GitHub Security Advisories.
- `.github/ISSUE_TEMPLATE/` with bug-report and feature-request templates, plus a `config.yml` that points casual readers at the docs and the callkeep comparison first.
- `exports` field in `package.json` alongside the existing `main` and `types` (modern dual-export shape; no behavior change for consumers).

### Changed
- `homepage` field in `package.json` now points at the docs site (`https://mfairley.github.io/expo-callkit-telecom/`) instead of the README anchor — affects the "Homepage" link on npm's package page.
- README `<h1>` expanded to "expo-callkit-telecom — native calling UI for Expo (CallKit + Jetpack Core-Telecom)" for clearer SEO and first-impression scannability.

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

[0.2.7]: https://github.com/mfairley/expo-callkit-telecom/releases/tag/v0.2.7
[0.2.6]: https://github.com/mfairley/expo-callkit-telecom/releases/tag/v0.2.6
[0.2.5]: https://github.com/mfairley/expo-callkit-telecom/releases/tag/v0.2.5
[0.2.4]: https://github.com/mfairley/expo-callkit-telecom/releases/tag/v0.2.4
[0.2.3]: https://github.com/mfairley/expo-callkit-telecom/releases/tag/v0.2.3
[0.2.2]: https://github.com/mfairley/expo-callkit-telecom/releases/tag/v0.2.2
[0.2.1]: https://github.com/mfairley/expo-callkit-telecom/releases/tag/v0.2.1
[0.2.0]: https://github.com/mfairley/expo-callkit-telecom/releases/tag/v0.2.0

# Security Policy

## Supported versions

The latest minor version on `main` is supported. Earlier versions may receive security fixes at the maintainer's discretion.

## Reporting a vulnerability

Please report security issues **privately** via [GitHub Security Advisories](https://github.com/mfairley/expo-callkit-telecom/security/advisories/new) rather than as a public issue.

I'll acknowledge within 7 days and aim to ship a fix within 30 days where reasonable. If you don't get a response in that window, please open a private follow-up via the same channel.

## Scope

In scope:

- Vulnerabilities in this module's TypeScript, Swift, or Kotlin source.
- Vulnerabilities in the config plugin that produce insecure entitlements, permissions, or background-mode configuration in consumer apps.

Out of scope:

- Vulnerabilities in third-party packages this module depends on — please report those to the upstream maintainer (and feel free to flag them here so they can be tracked).
- Vulnerabilities in the system platforms (iOS CallKit / PushKit, Android Jetpack `androidx.core:core-telecom`, FCM) — report those to Apple / Google.

# Edo v1.0 — Publishing and upgrade policy

The unified v1.0 release is available at [GitHub Releases](https://github.com/Subaru771/EDO/releases/tag/v1.0).

| Platform | Version | Public download |
| --- | --- | --- |
| Android TV / Google TV | 1.0 (Android version code **57**) | `Edo-v1.0-Android-TV.apk` |
| Windows Desktop | 1.0 | `Edo-Desktop-v1.0-Windows-Portable.zip` (Java bundled) |

The two applications share the public v1.0 identity. Keep the Android package ID `com.dropwave.tv` and increase the **Android version code** for each future update.

### Release safeguards
- Sign Android builds using the **existing signing key** and verify its fingerprint and install compatibility.
- Verify the exact Windows and Android binary assets on real devices before publishing.
- Publish SHA-256 checksums; use immutable tags and don't automatically overwrite released assets.
- Keep signing keys, passwords, and private files outside GitHub.
- Windows binaries are unsigned until an Authenticode signing identity is configured.

The [legacy v5.4.2 release](https://github.com/Subaru771/EDO/releases/tag/v5.4.2) is retained for users who need earlier files. Don't republish its APK as v1.0.

[Build instructions](../BUILDING.md) · [Security](../SECURITY.md)

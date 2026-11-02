# Edo v5.4 feature verification matrix

| Feature | State | Notes |
|---|---|---|
| Native TV four-action home + Lite receive | SOURCE | Android APK/device not tested here |
| Browser modern responsive layout and tabs | MOCK BROWSER TEST | Chromium desktop/mobile interactions verified with mock API |
| Media category index + largest files | JVM TEST | Server aggregation; not transcoding or content analysis |
| Same-name/size duplicate warning | JVM TEST | No silent overwrite; contents not compared |
| Unfinished browser queue manifest | JS SYNTAX + MOCK UI | Requires reselecting local file after restart |
| Resumable chunk transfer and SHA-256 | JVM TEST | Existing server and client integration coverage |
| New-device approval default ON | SOURCE | New installs only; existing preference preserved |
| Android local crash report/diagnostic screen | SOURCE | Android framework compilation and TV test needed |
| Secure HTTPS and authenticated WebDAV | JVM TEST on desktop | Self-signed Android TLS client trust still difficult; opt in |
| Native peer picker and TV-to-TV transfer | JVM TEST core / Android SOURCE | mDNS Android UI unverified |
| iOS Bonjour and file upload/download | SWIFT PARSE | Xcode/device build not verified |
| iOS Share Extension with App Group | SWIFT PARSE / plist valid | Requires provisioning/signing; stage, then open main app |
| One-way backup and safe two-way merge | JVM TEST | Doesn't propagate deletions automatically |
| Windows JAR app | COMPILED | Requires Java 17; EXE via Actions, unverified |
| Android APK and signed iOS IPA | NOT BUILT | Workflow/source provided |
| Signed background updates / SFTP | NOT IMPLEMENTED | Requires release infrastructure / extra protocol implementation |
| Cross-network internet relay | NOT IMPLEMENTED | Local-only by design |

# Build Edo v1.0

Edo has two components: the Android TV app (Java 17 / Gradle) and the desktop host (Java 21). Both use the visible version **1.0**.

## Android TV / Google TV

- Android `applicationId`: `com.dropwave.tv` — retained to support updates to the existing app.
- `versionName`: `1.0`. `versionCode`: **57** (must increase from previous releases).
- Compile SDK 35, target SDK 34, minimum SDK 29, JDK 17, Gradle 8.9.

**Windows local debug build:** from the repository root, run `scripts\build-android-debug.cmd` with Java 17 and Android SDK. Output: `app\build\outputs\apk\debug\app-debug.apk`.

**Windows signed build:** run `scripts\build-android-release.cmd`. The build reuses the existing keystore under `%USERPROFILE%\EdoSigningKeys\edo-upload-key.jks`. Enter signing passwords only in the local prompt. Outputs: `app\build\outputs\apk\release\app-release.apk` and `app\build\outputs\bundle\release\app-release.aab`.

**GitHub:** `Build Edo v1.0 TV debug APK` tests compilation. `Build signed Edo v1.0 Android TV release` is manual and requires the four encrypted Actions secrets: `EDO_KEY_BASE64`, `EDO_STORE_PASS`, `EDO_KEY_ALIAS`, `EDO_KEY_PASS`. Leave publication disabled to download and test the signed artifact first; publishing requires explicit hardware-test confirmation.

**Warning:** preserve the original signing key. An APK signed with a different key cannot simply update the previously installed app. Back up app data before considering uninstall/reinstall. Never commit or publish signing keys or passwords.

## Edo Desktop (Windows, macOS, Linux)

Requires **JDK 21+ to build**, or Java 21+ to run the JAR. Build from the repository root:

```bash
bash desktop/build.sh
java -jar desktop/dist/Edo-Desktop.jar
```

The `.github/workflows/desktop-windows.yml` CI builds a portable Windows `Edo.exe` with its own Java runtime. The portable Windows package is **unsigned** and should be hardware-tested before publication. Keep the entire extracted directory.

## Tests

With Java 21, Python 3 and Node.js installed:

```bash
bash scripts/test-all.sh
```

Tests cover the transfer engine and file browser; passing them does **not** replace Android compilation, signed APK testing, or real-device security review.

## Local-network security

Edo's HTTP mode is plaintext. Optional HTTPS uses a self-signed certificate; clients must verify the fingerprint, and some browsers may reject connections without a valid IP SAN.

See [secure connection instructions](docs/SECURE-CONNECTION-GUIDE.md) and [release policy](docs/RELEASES.md).

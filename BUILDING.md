# Build and install Edo v5.4

## Google TV / Android APK

1. Extract the complete project ZIP in Windows (do not run from inside ZIP).
2. Ensure JDK 17 or newer is installed. The script verifies `java` and `javac`.
3. Run `BUILD-APK-WINDOWS.cmd`. It locates an Android SDK in `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or `%LOCALAPPDATA%\Android\Sdk`. If Platform 35 / Build Tools 35 are missing, it installs a verified Google Android CLI before attempting package installation. It prompts you to accept the Android SDK licenses.
4. The expected debug APK is `app\build\outputs\apk\debug\app-debug.apk`.
5. Use your existing TV file transfer method to move the APK to Google TV, then launch it with Android's installer. Android may require allowing installs from this source. Some TV firmware prevents sideloading.

If SDK setup fails, build through `.github/workflows/android-apk.yml` instead. A successfully completed Java test run does not prove Android compilation.

**Compatibility:** minSdk 29 (Android 10), compileSdk 35, targetSdk 34, Java 17, Android Gradle Plugin 8.7.3 / Gradle 8.9. Application ID `com.dropwave.tv` is deliberately retained. Installing over an earlier APK requires compatible signing keys; mismatched debug keys may require uninstall/reinstall, which clears app-private settings. Public files in `Downloads/DropWave` should be backed up independently.

## Windows / desktop

Compile `bash desktop/build.sh` on a JDK 17+ system. Run `java -jar desktop/dist/Edo-Desktop.jar` or use `desktop/dist/Edo-Windows.vbs` on Windows to launch silently via javaw. The GitHub Windows installer workflow uses jpackage to bundle a runtime; the `.exe` is not included until CI produces it.

## iPhone / iPad

Safari works without installation. For the SwiftUI app, you need macOS Xcode to create and sign a native iOS app. On a Mac, install XcodeGen and from `ios/` run `xcodegen generate`, open `Edo.xcodeproj`, configure a signing team and run on a device. GitHub Actions can verify simulator compilation without signing but is not a free permanent installation route for physical devices.

## Optional HTTPS/WebDAV

TV: Dashboard → Security & Pairing → Toggle HTTPS. The app restarts the sharing service; the displayed QR address changes to `https://`. Compare its certificate fingerprint with the browser's certificate information. WebDAV credentials are displayed by a separate TV button.

Desktop: Check 'Encrypted HTTPS', then Start sharing. The desktop displays the SHA-256 fingerprint and WebDAV credentials. Browser clients may refuse a self-signed connection until you explicitly trust it. iOS often requires a trusted certificate profile; do not bypass TLS validation.

## New secure connection help

See `SECURE-CONNECTION-GUIDE.md` for the Android TV certificate fingerprint, public certificate export, and per-platform limitations. Changing between HTTP and HTTPS no longer stops/restarts the whole Android service asynchronously.

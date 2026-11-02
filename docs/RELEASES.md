# Edo v1.0 release policy

**Public version label: v1.0 on all platforms.**

The Android TV application retains package ID `com.dropwave.tv` and uses version code **57**, ensuring this version is newer than the previously signed package. Do not reset the Android version code to 1.

## Current availability

- **Desktop v1.0:** Java ZIP available on the [GitHub Releases page](https://github.com/Subaru771/EDO/releases); needs Java 21+. Windows portable EXE can be obtained from a successful [Windows Actions run](https://github.com/Subaru771/EDO/actions/workflows/desktop-windows.yml), but is not yet hardware-verified.
- **Android TV v1.0:** source and debug build workflow available. A new **signed** APK must be produced with the original signing key and tested before it is labeled a public v1.0 release. The [previous signed release](https://github.com/Subaru771/EDO/releases/tag/v5.4.2) remains available until the new one is verified.

## Publishing controls

1. Build and run regression tests.
2. Sign the Android APK and AAB with the **existing key**, never a new one.
3. Verify APK signature, package name, version code and version name; install and test the exact signed APK on a physical TV.
4. Verify PC transfers and file management and Windows installer on a physical PC.
5. Publish a **new v1.0 GitHub release** containing correctly labeled binaries and SHA-256 checksums. Do not silently replace old assets or republish an old APK as v1.0.
6. Only archive or retire the prior release after v1.0 downloads are validated and public.

CI builds previews. Publication is a separate, intentional action. Windows binaries remain unsigned until a trustworthy Authenticode signing identity is configured. Keep keys, passwords and upload credentials out of the repository.

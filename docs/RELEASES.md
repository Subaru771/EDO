# Edo release and versioning policy

| App | Version | Download |
| --- | --- | --- |
| Android TV / Google TV | v5.4.2 | `app-release.apk` in public GitHub Releases |
| Edo Desktop | v1.0 | `Edo-Desktop-v1.0-Windows.zip` (Java 21+) |
| Portable Edo.exe preview | Desktop v1.0 | Windows GitHub Actions build artifact |

Android and Desktop versions are independent. The Windows portable preview bundles its own Java runtime, but has not yet passed physical Windows testing.

## Publication rules
- CI builds and tests **without overwriting published release assets**.
- Each new public binary receives a new immutable tag and a SHA-256 checksum.
- Test the installer on real hardware and review security and privacy before publishing.
- Keep Android signing keys private; don't lose the existing app's release key.
- Windows executables are **unsigned** until a real Authenticode code-signing identity is configured.
- Windows SmartScreen warnings may appear for unsigned downloads.

[GitHub Releases](https://github.com/Subaru771/EDO/releases) · [Windows builds](https://github.com/Subaru771/EDO/actions/workflows/desktop-windows.yml)
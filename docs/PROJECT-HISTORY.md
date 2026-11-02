# Edo — Every Device. One Connection.

**Free, local-first file transfers between Google TV, Windows, Android, iPhone, macOS and Linux (platform methods vary). A TV is NOT required for PC-to-PC transfers.**

> **PC to PC? Yes.** Run **Edo Desktop** on one computer to share a folder; on the other computer, open the address shown in Edo in a web browser, enter the pairing PIN, and upload/download files in either direction. Both computers must be reachable on the same LAN. See [PC-to-PC walkthrough](docs/PC-TO-PC.md).

## Choose how you want to share

| Transfer | Install on receiver / hub | Use on other device | TV required? |
|---|---|---|---|
| **PC ↔ PC** | Edo Desktop (Java 17+) | Chrome, Brave, Firefox, Edge or another compatible browser | **No** |
| **PC ↔ Google TV** | Edo Android TV APK | PC browser | Yes — if TV is the receiver |
| **Phone ↔ Google TV** | Edo Android TV APK | iPhone Safari or Android browser | Yes — if TV is the receiver |
| **Phone ↔ PC** | Edo Desktop (Java 17+) | Phone browser | **No** |
| **Mac/Linux ↔ PC** | Edo Desktop on a compatible Java host | Browser | **No** |

**Quick setup — PC to PC**

1. On **Computer A**, install Java 17+ and open `desktop/dist/Edo-Desktop.jar` (Windows: double-click `desktop/dist/Edo-Windows.vbs`).
2. Choose a safe folder to share, then click **Start sharing**. Edo shows a local address, such as `http://192.168.1.20:8765`, and a six-digit pairing PIN.
3. On **Computer B**, connect to the same Wi-Fi/Ethernet network, open that exact address in a browser, and pair using the PIN. Approve on Computer A if required.
4. To send **B → A**, choose files in Computer B's browser. To send **A → B**, open the **Files** tab and download shared files from Computer A.
5. For folder backups or non-destructive two-way merges, use Edo Desktop on the other PC as well; this is optional for ordinary file transfers.

**What Edo does not do:** No automatic internet relay or public-web transfer service; optional HTTPS uses a local certificate that may require manual trust. Plain HTTP is **not encrypted**, so use it only on a trusted network. The native Google TV and iOS distributions still require platform-specific build/testing before production claims.

## Download and build

- **PC:** `desktop/dist/Edo-Desktop.jar` (Java 17+), with `desktop/dist/Edo-Windows.vbs` for Windows.
- **Google TV:** build the Android APK using `BUILD-APK-WINDOWS.cmd` or the GitHub Actions APK workflow. Installation on actual hardware must still be verified.
- **iOS:** browser access works with a running Edo server on the LAN; the native iOS companion is source-only and not signed.
- **Documentation:** [All connection guides](docs/SHARING-GUIDE.md) · [PC-to-PC instructions](docs/PC-TO-PC.md) · [Security details](SECURITY.md).

---

# Edo v5.4 — Official Logo + Classic TV

**Edo — Every Device. One Connection.**

This update preserves your **classic native Google TV two-panel look** and the existing transfer features. HTTPS now uses an in-service restart rather than an asynchronous stop/restart; TV and browser connection guidance is clearer, and a certificate fingerprint/export tool is available on the TV.

Read **[CHANGELOG-v5.3.md](CHANGELOG-v5.3.md)** for changes and **[SECURE-CONNECTION-GUIDE.md](SECURE-CONNECTION-GUIDE.md)** for step-by-step encrypted connection and recovery instructions.

**Branding:** See **[BRANDING-v5.4.md](BRANDING-v5.4.md)** for Android, TV, web, Windows, and iOS logo files.

**Important:** Edo still uses a self-signed Android TV certificate that can be rejected by browsers (especially without an IP SAN). v5.4 improves diagnostics/onboarding, **not** universal certificate trust. `HTTP` mode is unencrypted. The Android APK itself has not been built or tested on a physical Google TV in this environment.

Build the APK using **`BUILD-APK-WINDOWS.cmd`** (from the extracted project). The application ID stays `com.dropwave.tv` so that previously installed compatible-signed builds can update in place.

---

# Edo v5.2 — Classic UI + Transfer Compatibility Fix

See **TRANSFER-FIX.md** for the corrected pairing behavior, diagnosis steps and verification status.

# Edo v5.1 — Classic Google TV layout

**Edo — Every Device. One Connection.**

This build retains Edo v5.0's transfer server, desktop application, iOS source, browser UI, test suites, and safety features, while restoring the **v4-era native Google TV main screen**: the two-panel Connect a Device / Your TV Files dashboard, QR code, pairing PIN, file browser, and security settings. The four-tile v5 Google TV home screen is removed.

- The TV's optional QR-only Quick Receive mode is still accessible from **Security & Pairing → Quick receive**.
- Local diagnostics remain available under **Security & Pairing**.
- The browser UI on the phone/PC is **unchanged** from v5.0.
- Previously received files and app identity are not intentionally changed.

**Verification:** The complete server/desktop integration suite, web syntax, and metadata checks can be run with `bash scripts/test-all.sh`. The native Android UI still requires an APK build and device testing.

---

# Edo v5.0 — Every Device. One Connection.

Edo is a local-first file-sharing system centered on a native Android TV receiver with browser clients, a Java desktop companion, and an experimental native iOS companion. No account or external cloud storage is needed. **Not all device builds are verified.**

## What's in this release

- **Native Android TV home:** Classic two-panel dashboard with QR pairing and direct access to received files, plus an optional QR-only *Lite Receive* mode accessible from Security & Pairing. New-device approval is enabled by default for fresh installs, and pairing requests are accessible even in Lite mode.
- **Browser app:** Refined desktop/mobile layout; transfers, multi-folder file manager, file search, media-category filters, largest-file report, session/storage diagnostics, duplicate-name checking, and shareable diagnostic JSON.
- **Transfer recovery:** Existing offset-checked resumable upload backend and retry engine, now with an unfinished-transfer manifest that reminds users to reselect local files after browser restart. Browser file permissions cannot be restored automatically. This is *not* autonomous background uploading.
- **Security:** Existing optional TLS and authenticated HTTPS WebDAV retained; TV approval default enabled on new installs. Android TLS self-signed certificate must be manually trusted by clients. **HTTP remains unencrypted and is still supported** for compatibility.
- **Android native diagnostics:** Local crash log and explicit user-initiated sharing. No telemetry; only the user decides whether to export.
- **iOS experimental project:** Bonjour discovery, file transfer, and a Share Extension staging files into an App Group; users must open Edo after sharing. Requires Apple-provisioned app-group entitlements, Xcode build, and device testing.
- **Desktop:** Java 17+ companion with peer discovery, one-way backup and non-destructive merge. Windows EXE generated from GitHub Actions when workflows run.

## Get started on Windows (Android APK)

1. Extract this ZIP completely. Open `Edo-v5.1`.
2. Double-click `BUILD-APK-WINDOWS.cmd`.
3. Review the Android SDK licenses when prompted. The script attempts to install Platform 35/Build Tools 35 and Gradle automatically.
4. The debug APK **should** appear at `app/build/outputs/apk/debug/app-debug.apk`. This build was **not** performed in the authoring environment and requires verification on your PC/TV.
5. Alternatively, upload the project files to a GitHub repository, then use Actions → **Build Edo TV APK** and download the APK artifact after successful CI completion.

## Desktop

- Compiled JAR: `desktop/dist/Edo-Desktop.jar` (Java 17+).
- On Windows, double-click `desktop/dist/Edo-Windows.vbs` to start without a console window (requires Java installed).
- Build desktop sources with `bash desktop/build.sh`. GitHub Actions can create an EXE installer with a bundled runtime.

## iOS

The iOS source lives under `ios/`. Generate an Xcode project with `xcodegen generate`, then build/sign using an Apple developer team that has the **App Groups** capability for both app and Share Extension. A macOS GitHub Action checks simulator compilation, but an installable signed IPA is **not** included. Share extension files are staged locally and uploaded after opening the paired main app; no reliable background share-extension transfer is claimed.

## Tests

Run `bash scripts/test-all.sh`. This performs Java integration tests for auth, resume, sync, peer transfer, LAN discovery, WebDAV/TLS, and v5 media index; JavaScript syntax; Swift syntax parsing (not iOS build); and manifest/plist validation.

## Important limits

- Android app has **not** yet been built and exercised on an actual Google TV; iOS source hasn't been Xcode-compiled/signing tested here.
- HTTPS with a device-local self-signed Android certificate is **not** zero-warning browser trust; use trusted LANs if you operate HTTP.
- No universal browser mDNS API, no SFTP, no automatic signed updater, no cloud relay, no silent two-way delete propagation.
- Duplicate *name/size* detection does not prove two files have identical contents. Default upload policy retains both files.
- Resuming a browser upload after closing/reopening requires user re-selection of the original file(s); browser permission cannot be recovered silently.

Older Android application ID `com.dropwave.tv` is intentionally preserved for upgrading; legacy file storage path remains accessible, even though Edo is the current brand.

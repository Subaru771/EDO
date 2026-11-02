# Edo — Every Device. One Connection.

**Free, local-first file sharing between computers, phones and Google TV.** Use a browser on the second device—no cloud service or account is required. **A TV is not required for PC-to-PC transfers.**

> Edo is currently a source-first release candidate. Its desktop Java app and core transfer protocols have passed automated tests; a production-signed Android TV APK and iOS build are **not yet verified** on real devices.

## Choose your transfer

| Scenario | Run Edo on... | Connect using... | Needs a TV? |
| --- | --- | --- | --- |
| **PC ↔ PC** | One computer: **Edo Desktop** | Browser on the other PC | **No** |
| **Phone ↔ PC** | Computer: **Edo Desktop** | iPhone/Android browser | **No** |
| **PC ↔ Google TV** | TV: Edo APK | PC browser | Yes |
| **Phone ↔ Google TV** | TV: Edo APK | Safari/Android browser | Yes |
| **Mac/Linux ↔ Windows** | One computer: Edo Desktop (Java 17+) | Browser on the other computer | **No** |

### PC ↔ PC in 4 steps — no TV needed

1. **Computer A:** Launch `desktop/dist/Edo-Desktop.jar` (Java 17+). On Windows you can double-click `desktop/dist/Edo-Windows.vbs` instead.
2. Pick a shared folder and click **Start sharing**. Note the local address (for example `http://192.168.1.20:8765`) and the pairing PIN.
3. **Computer B:** On the same Wi-Fi or Ethernet network, open that address in a browser, enter the PIN, and approve on Computer A if requested.
4. **B → A:** Use the **Transfer** tab to upload files. **A → B:** Use **Files** to download files from Computer A.

No install is needed on Computer B. Install Edo Desktop on **both** PCs only if you want each to host folders or use advanced backup/merge tools.

**[Detailed PC-to-PC guide](docs/PC-TO-PC.md)** · **[All device combinations](docs/SHARING-GUIDE.md)**

## Other quick starts

**Google TV:** Install a verified Edo APK, open Edo on the TV, scan its QR code or enter the TV's local address on a phone/computer, pair with its PIN, and transfer files. The classic TV interface is intentionally preserved.

**Phone ↔ PC:** Start sharing through Edo Desktop on the computer, open its local address in the phone browser, pair, then upload or download. No TV or phone companion app is needed for this mode.

## Features

- File uploads/downloads, progress, resumable chunks and retries
- Received-file browser, folder management, previews, search, and ZIP export
- Pairing PIN, remembered devices and optional host approval
- Optional HTTPS and authenticated WebDAV (local certificates can require manual trust)
- Desktop backup and safe non-destructive two-way folder merge
- Local network discovery; manual IP/QR connection fallback
- Classic native Android TV interface and separate browser UI

## Get Edo

- **Windows/macOS/Linux desktop:** `desktop/dist/Edo-Desktop.jar` — needs Java 17+. A Windows launcher is alongside it.
- **Google TV / Android TV:** use `BUILD-APK-WINDOWS.cmd` after extracting the source, or the GitHub Actions Android APK workflow. This produces a build that **must be tested on real hardware** before public release.
- **iOS:** Safari can connect to a running Edo host on the LAN. The native iOS companion source is in the full project archive (not this shortened GitHub-web-upload package); no signed iOS app is included.
- **Source and tests:** `bash scripts/test-all.sh`. More build information in [BUILDING.md](BUILDING.md).

## Network and security

Edo works when devices can reach one another on the same trusted Wi-Fi or Ethernet LAN; guest-network isolation or a firewall may block the connection. **HTTP is unencrypted**—do not use it on untrusted networks or forward its port to the public internet. Optional HTTPS uses self-signed local certificates and can be refused by some browsers; verify fingerprints when trusting a certificate. See [SECURITY.md](SECURITY.md) and [Secure connection guide](SECURE-CONNECTION-GUIDE.md).

## Current release status

Automated tests exercise the server, large transfers, resume, peer pairing, WebDAV/TLS, discovery, and desktop sync. **That is not equivalent to verified Android/iOS device support.** Signed public APK/AAB distribution and platform test coverage are pending. For store screenshots, use captures from an actual running TV, not UI mockups.

**Publishing:** [Release description](publishing/GITHUB-RELEASE-DESCRIPTION.md) · [TV store listing](publishing/PLAY-LISTING.md) · [Publishing checklist](publishing/PUBLICATION-CHECKLIST.md) · [What changed](CHANGELOG-v5.4.2.md) · [History](docs/PROJECT-HISTORY.md)

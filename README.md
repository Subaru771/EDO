# Edo — Every Device. One Connection.

**Free, local-first file sharing for computers, phones, and Google TV.** Send and receive files over your local Wi-Fi or Ethernet network without a cloud account.

[Download Edo v5.4.2](https://github.com/Subaru771/EDO/releases/tag/v5.4.2) · [How to use Edo](#how-to-connect) · [Report a problem](https://github.com/Subaru771/EDO/issues)

## Download Edo

**Choose the device that will host your files.** The other device usually needs only a web browser.

| Download | For | What you get |
| --- | --- | --- |
| **[Android TV / Google TV — APK](https://github.com/Subaru771/EDO/releases/download/v5.4.2/app-release.apk)** | Google TV and compatible Android TV devices | Signed Android installation package |
| **[Windows PC — ZIP](https://github.com/Subaru771/EDO/releases/download/v5.4.2/Edo-v5.4.2-Windows-PC.zip)** | Windows 10/11 | Edo Desktop Java application, launcher and setup guide |
| [All release assets](https://github.com/Subaru771/EDO/releases/latest) | Everyone | Latest published downloads and source archives |

**Windows:** Extract the entire ZIP, install [Java 17 or newer](https://adoptium.net/temurin/releases/) if necessary, then double-click **`Start-Edo-Windows.cmd`**. This is a Java desktop companion, **not a standalone Windows EXE**.

**macOS / Linux:** On a computer with Java 17+, extract the same desktop ZIP and run `java -jar Edo-Desktop.jar`. A native Mac or Linux installer is not included.

**iPhone / iPad / Android phone:** Open the host's local address using Safari or another compatible browser. No separate phone app is required for browser transfers; a native iOS application has not been released.

> **Release status:** The signed Android APK and Windows desktop ZIP are publicly available. PC-to-Google TV transfers were tested with a debug APK; separate hardware testing of the production-signed APK and the Windows ZIP is not yet confirmed. Please [report issues](https://github.com/Subaru771/EDO/issues).

## What can connect?

| Transfer | Run Edo on | Connect from |
| --- | --- | --- |
| **PC ↔ PC** | One Windows, macOS or Linux computer | Browser on the other computer |
| **Phone ↔ PC** | Computer running Edo Desktop | Phone browser |
| **PC ↔ TV** | Google TV / Android TV running Edo | Computer browser |
| **Phone ↔ TV** | Google TV / Android TV running Edo | Phone browser |

**One device hosts; another device connects.** A host can accept uploads and provide downloads through the local browser interface. The host must remain running and reachable on the network.

## How to connect

### PC ↔ PC

1. On **Computer A**, extract the Windows ZIP and start **Edo Desktop** using `Start-Edo-Windows.cmd` (or `java -jar Edo-Desktop.jar` on macOS/Linux).
2. Select the folder to share and click **Start sharing**.
3. Note the displayed network address, for example `http://192.168.1.20:8765`, and the pairing PIN.
4. On **Computer B**, open that exact address in Chrome, Edge, Brave, Safari, or another compatible browser.
5. Pair using the PIN and approve the connection on Computer A if prompted.
6. Use **Transfer** to send files **from B to A**, or **Files** to download files **from A to B**.

Only Computer A needs Edo Desktop for this setup. Both PCs must be on the same reachable local network. For more options, read the [PC-to-PC guide](docs/PC-TO-PC.md).

### Phone ↔ PC

Start Edo Desktop on your computer, then visit its displayed address from your phone's browser. Pair and upload or download files. **No TV needed.**

### PC or phone ↔ Google TV

1. Install [Edo for Android TV](https://github.com/Subaru771/EDO/releases/download/v5.4.2/app-release.apk) and open it on the TV.
2. Ensure sharing is running. The TV will display an address and connection options.
3. On your computer, open the TV's local address. On your phone, you can scan the TV's QR code.
4. Complete pairing if requested, then send or receive files.

**Security note:** Scanning the QR code may automatically supply pairing information; treat it as a credential. Enable TV approval for new devices when appropriate.

[More device combinations and troubleshooting](docs/SHARING-GUIDE.md)

## Features

- Upload and download files through a browser on the local network
- File and folder browsing, search, previews and ZIP export
- Transfer progress, resumable transfer functions and retries
- PIN pairing, remembered devices and optional host approval
- Optional HTTPS and authenticated WebDAV (certificate setup may be necessary)
- Device discovery with manual IP-address fallback
- Desktop backup and non-destructive folder synchronization tools
- A classic native Google TV interface and a separate browser interface

Feature availability can differ by platform, and not every device/browser combination has been verified.

## Network, security and privacy

Edo is designed for **trusted local networks**. The two devices must be able to reach each other; guest-Wi-Fi isolation, firewalls and incorrect IP addresses can prevent connections.

- **HTTP is not encrypted.** Avoid it on untrusted networks.
- **Do not forward Edo's server port to the public internet.**
- Optional HTTPS may use a self-signed certificate that requires manual trust.
- A QR code or remembered session may carry pairing access; use optional host approval for more control.

Read [SECURITY.md](SECURITY.md) and the [secure connection guide](SECURE-CONNECTION-GUIDE.md).

## Documentation and development

- [PC-to-PC setup](docs/PC-TO-PC.md)
- [All sharing methods](docs/SHARING-GUIDE.md)
- [Build instructions](BUILDING.md)
- [Changelog](CHANGELOG-v5.4.2.md)
- [Project history](docs/PROJECT-HISTORY.md)
- [GitHub Releases](https://github.com/Subaru771/EDO/releases)

Edo v5.4.2 is an early public release. Automated tests cover parts of the transfer engine, but they do not establish universal compatibility. Contributions and bug reports are welcome through [Issues](https://github.com/Subaru771/EDO/issues).

---

**Edo — Every Device. One Connection.**  
*Share locally. Keep it simple.*

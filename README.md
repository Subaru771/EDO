<div align="center">

# Edo

**Every Device. One Connection.**

Free, local-first file sharing for computers, phones and Google TV.
Send and receive files over your local Wi-Fi or Ethernet network, with no cloud account.

[**Download**](https://github.com/Subaru771/EDO/releases/latest) · [How to connect](#how-to-connect) · [Security](SECURITY.md) · [Report a problem](https://github.com/Subaru771/EDO/issues)

</div>

## Download

**Choose the device that will host your files.** The other device usually needs only a web browser.

| Download | For | What you get |
| --- | --- | --- |
| [**Android TV / Google TV APK**](https://github.com/Subaru771/EDO/releases/download/v5.4.2/app-release.apk) | Google TV and compatible Android TV devices | Signed Android installation package (v5.4.2) |
| [**Edo Desktop v1.0 for Windows**](https://github.com/Subaru771/EDO/releases/download/v5.4.2/Edo-Desktop-v1.0-Windows.zip) | Windows 10/11 | Redesigned dashboard with a native **Your files** browser, launcher and setup guide. Requires Java 21+ |
| [All release assets](https://github.com/Subaru771/EDO/releases/latest) | Everyone | Latest published downloads and source archives |

### Windows

1. Extract the **entire** ZIP.
2. Install [Java 21 or newer](https://adoptium.net/temurin/releases/?version=21) if you don't have it.
3. Double-click **`Start-Edo-Windows.vbs`** to launch the desktop dashboard. If it doesn't open, run **`Start-Edo-Windows.cmd`** to see the error.

> [!NOTE]
> The Windows ZIP is a Java desktop companion, **not a standalone EXE**.

Use **Your files** in the sidebar to search, import, create folders, rename, copy/move and delete items inside the shared folder.

### macOS and Linux

On a computer with Java 21+, extract the same desktop ZIP and run:

```bash
java -jar Edo-Desktop.jar
```

The desktop app is cross-platform, but native Mac and Linux installers are not included.

### iPhone, iPad and Android phone

Open the host's local address in Safari or another compatible browser. No phone app is needed for browser transfers, and a native iOS app has not been released.

> [!IMPORTANT]
> **Release status:** The signed Android APK and the Windows desktop ZIP are publicly available. PC-to-Google TV transfers were tested with a debug APK. Separate hardware testing of the production-signed APK and the Windows ZIP is not yet confirmed. Please [report any issues](https://github.com/Subaru771/EDO/issues).

## What can connect?

One device **hosts** and another **connects**. The host accepts uploads and provides downloads through a local browser interface, and it must stay running and reachable on the network.

| Transfer | Run Edo on | Connect from |
| --- | --- | --- |
| **PC ↔ PC** | One Windows, macOS or Linux computer | Browser on the other computer |
| **Phone ↔ PC** | Computer running Edo Desktop | Phone browser |
| **PC ↔ TV** | Google TV / Android TV running Edo | Computer browser |
| **Phone ↔ TV** | Google TV / Android TV running Edo | Phone browser |

## How to connect

### PC ↔ PC

1. On **Computer A**, extract the Windows ZIP and start Edo Desktop with `Start-Edo-Windows.cmd` (or `java -jar Edo-Desktop.jar` on macOS/Linux).
2. Select the folder to share and click **Start sharing**.
3. Note the displayed network address (for example `http://192.168.1.20:8765`) and the pairing PIN.
4. On **Computer B**, open that exact address in Chrome, Edge, Brave, Safari or another compatible browser.
5. Pair using the PIN, and approve the connection on Computer A if prompted.
6. Use **Transfer** to send files from B to A, or **Files** to download from A to B. On Computer A, manage the shared folder in the **Your files** tab.

Only Computer A needs Edo Desktop, and both PCs must be on the same reachable local network. More options are in the [PC-to-PC guide](docs/PC-TO-PC.md) and the [Windows v1.0 quick start](desktop/README-WINDOWS-v1.0.md).

### Phone ↔ PC

Start Edo Desktop on your computer, then open its displayed address in your phone's browser. Pair, then upload or download files.

### PC or phone ↔ Google TV

1. Install [Edo for Android TV](https://github.com/Subaru771/EDO/releases/download/v5.4.2/app-release.apk) and open it on the TV.
2. Make sure sharing is running. The TV displays an address and connection options.
3. On your computer, open the TV's local address. On your phone, you can scan the TV's QR code.
4. Complete pairing if asked, then send or receive files.

> [!WARNING]
> Scanning the QR code may automatically supply pairing information, so treat it as a credential. Enable TV approval for new devices when appropriate.

More device combinations and troubleshooting are in the [sharing guide](docs/SHARING-GUIDE.md).

## Edo Desktop v1.0

The Windows desktop app has dedicated **Share**, **Backup & Sync** and **Settings** screens, a prominent local connection address and pairing PIN, and quick-copy controls. It uses the same local sharing engine as the previous version.

## Features

- Upload and download files through a browser on the local network
- File and folder browsing, search, previews and ZIP export
- Transfer progress, resumable transfers and retries
- PIN pairing, remembered devices and optional host approval
- Optional HTTPS and authenticated WebDAV (certificate setup may be necessary)
- Device discovery with a manual IP-address fallback
- Built-in **Your files** browser in Edo Desktop v1.0 for local folder management
- Desktop backup and non-destructive folder synchronization tools
- A classic native Google TV interface, plus a separate browser interface

Feature availability can differ by platform, and not every device/browser combination has been verified.

## Network, security and privacy

Edo is designed for **trusted local networks**. The two devices must be able to reach each other. Guest-Wi-Fi isolation, firewalls and incorrect IP addresses can prevent connections.

- **HTTP is not encrypted.** Avoid it on untrusted networks.
- **Do not forward Edo's server port to the public internet.**
- Optional HTTPS may use a self-signed certificate that requires manual trust.
- A QR code or remembered session may carry pairing access. Use optional host approval for more control.

See [SECURITY.md](SECURITY.md) and the [secure connection guide](SECURE-CONNECTION-GUIDE.md).

## Documentation and development

- [PC-to-PC setup](docs/PC-TO-PC.md)
- [All sharing methods](docs/SHARING-GUIDE.md)
- [Build instructions](BUILDING.md)
- [Changelog](CHANGELOG-v5.4.2.md)
- [Project history](docs/PROJECT-HISTORY.md)
- [GitHub Releases](https://github.com/Subaru771/EDO/releases)

**Versions:** Android TV **v5.4.2** · Edo Desktop **v1.0**

Automated tests cover parts of the transfer engine, but they do not establish universal compatibility. Contributions and bug reports are welcome through [Issues](https://github.com/Subaru771/EDO/issues).

---

<div align="center">

**Edo — Every Device. One Connection.**
*Share locally. Keep it simple.*

</div>

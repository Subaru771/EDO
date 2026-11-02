# Edo — Every Device. One Connection.

**Free, local-first file sharing for computers, phones and Google TV.**
Send and receive files over your Wi-Fi or Ethernet network. No cloud account, no internet upload.

[Download](https://github.com/Subaru771/EDO/releases/tag/v5.4.2) · [How to connect](#how-to-connect) · [Report a problem](https://github.com/Subaru771/EDO/issues)

---

## Download

Pick the device that will **host** your files. The other device usually needs only a web browser.

| Download | For | Notes |
|---|---|---|
| [**Android TV / Google TV (APK)**](https://github.com/Subaru771/EDO/releases/download/v5.4.2/app-release.apk) | Google TV and compatible Android TV devices | Signed APK |
| [**Windows PC: Edo Desktop v1.0**](https://github.com/Subaru771/EDO/releases/download/v5.4.2/Edo-Desktop-v1.0-Windows.zip) | Windows 10 / 11 | Dashboard, **Your files** browser, launcher and setup guide. Requires **Java 21+** |
| [All release assets](https://github.com/Subaru771/EDO/releases/latest) | Everyone | Latest downloads and source archives |

### Windows
1. Extract the **entire** ZIP.
2. Install [Java 21 or newer](https://adoptium.net/temurin/releases/?version=21) if you don't have it.
3. Double-click **`Start-Edo-Windows.vbs`**.

If nothing opens, run **`Start-Edo-Windows.cmd`** instead to see the error message.

> Edo Desktop is a Java app, **not a standalone `.exe`**.

### macOS / Linux
With Java 21+ installed, extract the same desktop ZIP and run:

```
java -jar Edo-Desktop.jar
```

The desktop app is cross-platform, but native macOS and Linux installers are not provided.

### iPhone, iPad and Android phones
No app needed. Open the host's local address in Safari, Chrome or another modern browser. A native iOS app has not been released.

> **Release status:** The signed Android APK and the Windows ZIP are publicly available. PC-to-Google TV transfers were tested with a debug APK. The production-signed APK and the Windows ZIP have not yet been confirmed on separate hardware. If something breaks, please [open an issue](https://github.com/Subaru771/EDO/issues).

---

## What can connect?

One device **hosts**, another **connects**. The host must stay running and reachable on the network.

| Transfer | Run Edo on | Connect from |
|---|---|---|
| **PC ↔ PC** | One Windows, macOS or Linux computer | Browser on the other computer |
| **Phone ↔ PC** | Computer running Edo Desktop | Phone browser |
| **PC ↔ TV** | Google TV / Android TV running Edo | Computer browser |
| **Phone ↔ TV** | Google TV / Android TV running Edo | Phone browser |

---

## How to connect

### PC ↔ PC

1. On **Computer A**, start Edo Desktop (see [Download](#download)).
2. Choose the folder to share and click **Start sharing**.
3. Note the **address** (for example `http://192.168.1.20:8765`) and the **pairing PIN**.
4. On **Computer B**, open that exact address in any modern browser.
5. Enter the PIN, and approve the connection on Computer A if prompted.
6. Use **Transfer** to send files from B to A, or **Files** to download from A to B.

On Computer A, manage the shared folder from the **Your files** tab. Only Computer A needs Edo installed; both computers must be on the same reachable network.

More detail: [PC-to-PC guide](docs/PC-TO-PC.md) · [Windows v1.0 quick start](desktop/README-WINDOWS-v1.0.md)

### Phone ↔ PC
Start Edo Desktop on your computer, open its address in your phone's browser, pair, then upload or download.

### PC or phone ↔ Google TV
1. Install the [Android TV APK](https://github.com/Subaru771/EDO/releases/download/v5.4.2/app-release.apk) and open Edo on the TV.
2. Make sure sharing is running. The TV shows its address and a QR code.
3. On a computer, open the TV's address. On a phone, scan the QR code.
4. Complete pairing if asked, then send or receive files.

> **Security note:** A QR code may carry pairing access, so treat it like a password. Turn on TV approval for new devices when appropriate.

More combinations and troubleshooting: [Sharing guide](docs/SHARING-GUIDE.md)

---

## Edo Desktop v1.0

A redesigned Windows dashboard with dedicated **Share**, **Backup & Sync** and **Settings** screens, a prominent connection address and PIN, and one-click copy controls. It runs on the same local sharing engine as earlier versions.

## Features

- Upload and download through a browser on your local network
- Browse folders, search, preview files and export as ZIP
- Transfer progress, resumable transfers and automatic retries
- PIN pairing, remembered devices and optional host approval
- Optional HTTPS and authenticated WebDAV (may need certificate setup)
- Device discovery, with a manual IP fallback
- **Your files** browser in Edo Desktop for managing the shared folder
- Backup and non-destructive folder sync tools
- Native Google TV interface plus a separate browser interface

Availability varies by platform, and not every device/browser combination has been verified.

---

## Network, security and privacy

Edo is built for **trusted local networks**. Both devices must be able to reach each other; guest-Wi-Fi isolation, firewalls and wrong IP addresses are the usual causes of failed connections.

- **HTTP is not encrypted.** Avoid it on untrusted networks.
- **Never forward Edo's port to the public internet.**
- Optional HTTPS may use a self-signed certificate that you must trust manually.
- QR codes and remembered sessions can carry pairing access. Use host approval for tighter control.

Read [SECURITY.md](SECURITY.md) and the [secure connection guide](SECURE-CONNECTION-GUIDE.md).

---

## Documentation and development

- [PC-to-PC setup](docs/PC-TO-PC.md)
- [All sharing methods](docs/SHARING-GUIDE.md)
- [Build instructions](BUILDING.md)
- [Changelog](CHANGELOG-v5.4.2.md)
- [Project history](docs/PROJECT-HISTORY.md)
- [GitHub Releases](https://github.com/Subaru771/EDO/releases)

**Versions:** Android TV **v5.4.2** · Edo Desktop **v1.0**

Automated tests cover parts of the transfer engine but do not guarantee universal compatibility. Bug reports and contributions are welcome via [Issues](https://github.com/Subaru771/EDO/issues).

---

**Edo — Every Device. One Connection.**
*Share locally. Keep it simple.*

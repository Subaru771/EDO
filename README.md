# Edo — Every Device. One Connection.

**Local file sharing between PCs, phones and Google TV using a browser as the client.** Transfer directly on your local network without a cloud account.

[Downloads](#downloads) · [PC-to-PC setup](#pc-to-pc-sharing) · [Security](SECURITY.md) · [Report a bug](https://github.com/Subaru771/EDO/issues)

## Downloads

| Platform | Download | Requirements |
| --- | --- | --- |
| Google TV / Android TV | **[Signed APK](https://github.com/Subaru771/EDO/releases/download/v5.4.2/app-release.apk)** | Android TV v5.4.2 |
| Windows PC | **[Edo Desktop v1.0](https://github.com/Subaru771/EDO/releases/download/v5.4.2/Edo-Desktop-v1.0-Windows.zip)** | Current Java ZIP; **Java 21+** |
| Windows portable Edo.exe preview | [GitHub Actions build](https://github.com/Subaru771/EDO/actions/workflows/desktop-windows.yml) | Java bundled; test build pending real-device verification |
| iPhone / Android phone / another PC | Browser | Connect to a running Edo host on the same LAN |

The public Windows ZIP uses Java 21 and a launcher script. The new portable `Edo.exe` includes its own Java runtime but is **not a verified public release yet**. [Windows installation guide](docs/WINDOWS-SETUP.md)

## PC-to-PC sharing

1. On **PC A**, start Edo Desktop, select a folder and click **Start sharing**.
2. Copy the local URL and PIN shown in Edo.
3. On **PC B**, open the URL in a web browser and complete pairing.
4. Upload B → A from **Transfer**, or download A → B from **Files**.
5. On PC A, organize the shared folder using Edo Desktop's built-in **Your files** browser.

[Full PC-to-PC guide](docs/PC-TO-PC.md) · [Other device combinations](docs/SHARING-GUIDE.md)

## PC or phone to Google TV

1. Open Edo on Google TV and start sharing.
2. Enter the TV's local address in a browser or scan its QR code from your phone.
3. Pair and transfer files.

**QR security:** The QR code includes pairing credentials. Anyone who can scan or photograph it may be able to pair, so use TV-side approval for new devices. The published APK may have different defaults than newer main-branch code.

## Why Edo?

- **Google TV-first:** use a phone or PC browser to send files to your TV.
- **Browser-based client:** the second device doesn't need the full native app.
- **Desktop file manager:** browse, search, import, rename, copy, move and delete shared files.
- **Advanced sharing:** optional HTTPS and WebDAV, transfer recovery, and local backup/sync.

[LocalSend](https://localsend.org/) is a mature encrypted native-app alternative. Edo focuses on Google TV hosting and browser clients. **Edo's default HTTP connections are not encrypted.**

## Screenshots and interface

Edo Desktop v1.0 has a modern **Share**, **Your files**, **Backup & Sync**, and **Settings** interface. Real UI screenshot capture is being prepared; we won't present mockups as hardware screenshots.

## Privacy and security

Edo is for **trusted local networks**. Devices must be on reachable Wi-Fi or Ethernet; guest-network isolation or firewalls may block connections.

- HTTP is unencrypted. Do not use it on untrusted Wi-Fi or forward its port to the internet.
- HTTPS is optional and may require trusting a local certificate.
- PINs, QR links and remembered sessions grant access to the shared folder.
- Device and feature compatibility vary; no independent audit has been completed.

[Security policy](SECURITY.md) · [Developer review](docs/SECURITY-REVIEW.md) · [HTTPS guide](SECURE-CONNECTION-GUIDE.md)

## Development and releases

**Android TV v5.4.2** and **Edo Desktop v1.0** are independent version tracks.

GitHub Actions runs tests and builds previews. It **does not automatically replace public downloads**.

[Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [MIT License](LICENSE) · [Release policy](docs/RELEASES.md) · [GitHub Releases](https://github.com/Subaru771/EDO/releases)

**Edo — Every Device. One Connection.**

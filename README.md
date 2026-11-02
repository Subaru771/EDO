# Edo — Every Device. One Connection.

**Free, local-first file sharing between computers, phones, and Google TV.**

Transfer files across your local network without cloud storage, subscriptions, or an account.



[**Download Edo v5.4.2**](https://github.com/Subaru771/EDO/releases/latest) · [Report an Issue](https://github.com/Subaru771/EDO/issues)

---

## Download Edo

### Google TV / Android TV

**[Download the latest signed APK](https://github.com/Subaru771/EDO/releases/latest)**

1. Open the link above.
2. Under **Assets**, download `app-release.apk`.
3. Transfer the APK to your Android TV or Google TV.
4. Install it and open Edo.
5. Scan the QR code or enter the connection address on your other device.
6. Start sharing files!


### Windows / macOS / Linux

Edo Desktop allows your computer to act as a file-sharing host.

- Find `desktop/dist/Edo-Desktop.jar` in the repository.
- Install Java 17 or newer if needed.
- Run Edo Desktop and select a folder to share.
- Open the displayed address on another device.

Windows users can also use `desktop/dist/Edo-Windows.vbs`.

### iPhone / iPad / Android phones

No phone installation is required for browser-based transfers.

Open Safari, Chrome, or another compatible browser and connect to a computer or Google TV running Edo.

The native iOS application is not yet released.

---

## Choose Your Transfer

| Scenario | Run Edo on | Connect using | TV required? |
|---|---|---|---|
| **PC ↔ PC** | Computer A | Browser on Computer B | No |
| **Phone ↔ PC** | Computer | Phone browser | No |
| **PC ↔ Google TV** | Google TV | PC browser | Yes |
| **Phone ↔ Google TV** | Google TV | Phone browser | Yes |
| **Mac/Linux ↔ Windows** | One computer | Browser on another | No |

## PC-to-PC Sharing — No TV Needed

1. **Computer A:** Launch Edo Desktop.
2. Select the folder you want to share and start the server.
3. Copy the local network address displayed by Edo, such as `http://192.168.1.20:8765`.
4. **Computer B:** Open that address in a web browser.
5. Enter the pairing PIN, or complete the available pairing process.
6. Upload files to Computer A or download files from its shared folder.

Both computers should be connected to the same reachable local network.

You only need Edo Desktop on the computer acting as the host. You can run it on both computers for additional sharing and synchronization options.

[Detailed PC-to-PC Guide](docs/PC-TO-PC.md) · [All Sharing Methods](docs/SHARING-GUIDE.md)

---

## Features

- Local file uploads and downloads
- Phone, computer, and Google TV transfers
- Browser-based connection
- QR-code pairing
- PIN authentication and remembered devices
- Optional approval for new devices
- Transfer progress and resumable transfer functionality
- File and folder management
- File previews, search, and ZIP export
- Optional HTTPS and WebDAV support
- Local device discovery
- Desktop backup and non-destructive folder synchronization

Some advanced features vary by platform.

## How Edo Works

**One device hosts. The other connects.**

For example:

- Google TV runs Edo → iPhone connects through Safari.
- Windows PC runs Edo Desktop → another Windows PC connects through Chrome.
- Computer runs Edo Desktop → Android phone connects through its browser.

Files transfer over the local network instead of being uploaded to a cloud service.

## Security & Privacy

Edo is designed for use on trusted local networks.

- Pairing helps restrict access.
- Optional host approval provides additional control.
- HTTP transfers are not encrypted.
- Optional HTTPS may require certificate trust configuration.
- Avoid exposing Edo's server port directly to the public internet.

**Important:** A QR code can contain pairing information. Anyone who obtains it may be able to connect while that pairing information remains valid.

Read the [Security Policy](SECURITY.md) and [Secure Connection Guide](SECURE-CONNECTION-GUIDE.md).

---

## Release Status

**Latest release: v5.4.2**

- Public GitHub repository: Available
- Production-signed Android APK: Published
- PC-to-Google TV transfers: Tested successfully using a debug build
- Desktop

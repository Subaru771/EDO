# Edo v1.0 — HTTPS connection guide

Edo's original TV dashboard is unchanged. The security guide and certificate tools are under **Security & Pairing**. Both the full dashboard and QR-only view show the current transfer mode beneath the address.

## How to connect

1. Connect your Google TV and the sending device to the same trusted Wi-Fi or Ethernet LAN.
2. Open Edo on the TV. Confirm the transfer service shows **READY** and note the exact `http://` or `https://` URL. You can scan the QR code.
3. When HTTPS is enabled, the TV offers **How to connect with encryption** and **Certificate fingerprint / export**. View the SHA-256 fingerprint and compare it independently with the certificate presented by your client. Do not approve a mismatched certificate.
4. After the page opens, enter the six-digit PIN displayed on the TV, unless QR pairing already supplied it. If approval is enabled, approve the request next to the TV's QR code.
5. The web app's **Settings → Secure connection help** contains a live, non-sensitive connection check and platform tips.

## Important browser limitation

Android KeyStore supplies Edo's TV TLS certificate. Its automatically issued self-signed certificate may lack an IP-address Subject Alternative Name (SAN). Modern browsers can reject that certificate even if you install it or add an exception. Edo v1.0 **does not automatically bypass certificate validation or fix this limitation**. Exporting the public `.cer` is provided only for inspection and explicit trust decisions; it is not a guarantee of browser compatibility.

**Windows:** Review the local certificate in your browser / certificate viewer, compare its fingerprint with the TV, and trust it only if you understand the risk. The connection still may fail if the IP SAN is missing.

**Android:** Certificate installation and browser trust depend on device version and policy. Compare the certificate fingerprint before accepting it. A user-installed certificate does not guarantee that every app will accept it.

**iPhone / iPad:** Safari may refuse a self-signed local certificate, especially without the IP SAN, even if a certificate profile is installed. There is no reliable one-tap bypass. Browser-based file transfers may need HTTP mode on a trusted LAN until trusted certificate provisioning is implemented and validated.

## Safe recovery if HTTPS fails

1. Return to the TV's classic dashboard, scroll to **Security & Pairing**.
2. Select **Toggle HTTPS encryption**. The server restarts in place (without stopping and starting the Android Service asynchronously).
3. Verify the address on the TV now begins with `http://` and scan a fresh QR code.
4. Open Edo in your browser and pair with the displayed PIN.

**HTTP is unencrypted:** use only a trusted home network and do not expose Edo to the internet or port-forward its transfer port. If the sharing service fails, select **Check connection / troubleshoot** for the error and retry. Existing received files are not deleted when you switch modes.

## Public reachability test

The new `GET /api/health` route returns a small response such as:

```json
{"app":"Edo","version":"1.0","ready":true,"transport":"https","pairingRequired":true}
```

It deliberately excludes PINs, session identifiers, trusted device lists, filenames, and WebDAV credentials. All file-management routes remain authenticated.

## Compatibility

The older PIN-pairing and original upload endpoint remain available. Legacy v1 receivers are supported by the Java peer client fallback. Only the connection's configured transport is listened to; an HTTPS receiver does not automatically open a parallel unencrypted HTTP transfer server.

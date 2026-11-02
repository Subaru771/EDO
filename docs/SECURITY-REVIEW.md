# Targeted security review — November 2026

This is a **developer source review**, not an independent penetration test, formal audit or security certification.

## Existing safeguards
- PIN pairing, failed-attempt throttling, authenticated file APIs and trusted-device controls.
- HttpOnly session cookies with SameSite=Strict, and Secure when HTTPS is active.
- LAN-only incoming address checks and optional HTTPS / HTTPS-only WebDAV.
- Desktop file access restricted to the user-selected sharing folder.

## Source hardening included in v1.0
- API requests with `Sec-Fetch-Site: cross-site` are rejected by the server.
- Fresh TV installs default to requiring approval for new devices and persist that setting; existing user choices are retained.
- The QR code embeds pairing credentials; treat it like a password.

## Remaining risks
- HTTP remains unencrypted; a compromised LAN can observe transfers and credentials.
- Six-digit PINs are not cryptographic encryption.
- A new signed v1.0 Android APK must be built and hardware-tested before public distribution.
- The request parser, WebDAV implementation, session persistence, symlink behavior and browser CSP need independent review.
- Edo should never be exposed through port forwarding to the public internet.

Use only on a trusted local network and enable HTTPS where verified to work.
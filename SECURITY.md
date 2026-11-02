# Edo v4.0 security notes

Edo is not meant to run on public Wi-Fi, a public IP, or an exposed router port. The server refuses non-LAN source addresses but routers and network configurations vary. Never port-forward the Edo port or UDP discovery port.

**Default HTTP** is unencrypted. Six-digit PIN pairing, rate limiting, optional native approval, session cookies, and a trusted-device registry restrict access but do not protect traffic against passive interception by another LAN participant.

**Optional HTTPS** uses a locally generated certificate; desktop keys are stored under the local user's `.edo-desktop`, Android keys are kept by AndroidKeyStore. Compare the certificate fingerprint shown in the receiver app before manually trusting the cert. The desktop certificate includes the initial local IP as an SAN; if the IP changes you may need to regenerate the cert after backing up the original certificate/key files. Android's self-generated certificate may not include the IP SAN, so some browsers will reject it even after trust. No code path disables TLS validation in production clients. HTTP remains a fallback for ease of use on private networks, not an encrypted mode.

**WebDAV** is disabled unless TLS is active. It uses a different random password saved in the server's private state and accepts Basic authentication only over HTTPS. It cannot overwrite existing files silently. It should never be shared with untrusted devices. Treat WebDAV clients as having permission to manage the shared file area.

**Remote control** is off by default; TV-side opt-in is required, and only a paired session can invoke one of a small number of allowed in-app keys. It does not have Android system-level input permissions.

**Sync** is additive and preserves conflicts rather than overwriting existing files. Copy or back up files independently before enabling scheduled merges.

**Unverified:** Android TV/AndroidKeyStore behavior on device, iOS signed-build behavior, and physical-network security review. Do not treat this project as a production-hardened secure file server without further audits.


## v5 defaults and diagnostics

On fresh Android installations, TV approval is enabled by default for new pairing attempts. Existing users retain their previous stored preference. The Lite Receive screen also includes pending approval actions. The Android diagnostics page stores only the most recent uncaught exception in private app files and never uploads it automatically; users can explicitly share a report.

Browser duplicate handling is name-and-size based, not a cryptographic content comparison. Skip policies must not be treated as byte-verified duplicates. TLS remains optional: Android's auto-generated self-signed certificate may not satisfy browser hostname verification; do not assume that PIN-based pairing encrypts HTTP. Do not port-forward Edo.

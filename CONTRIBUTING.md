# Contributing to Edo v1.0

Pull requests and issue reports are welcome. Both Desktop and Android TV use public version **1.0** for this release.

- Explain the affected platform, steps to reproduce, and actual vs expected result.
- Run `bash scripts/test-all.sh` with Java 21, Python 3 and Node.js.
- Test Android Java builds with Java 17 and Gradle; test desktop with Java 21.
- Preserve Android package ID `com.dropwave.tv` and increase `versionCode` on all future TV releases.
- Do not commit credentials, private signing keys, personal data, or generated build artifacts.
- Do not automatically republish existing signed release assets.
- Include real screenshots with IPs, pairing PINs, private filenames and QR credentials obscured.
- Submit only source and artwork that you have the right to license under MIT.

Report security issues responsibly rather than publishing active secrets or exploit instructions. Read [SECURITY.md](SECURITY.md).

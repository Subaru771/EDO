# Code-signing and checksums

The Android APK uses an Android release signing key. Retain the keystore and passwords securely and use the same key for later updates.

The Windows `jpackage` process creates an `Edo.exe` with Java bundled, **not a digitally signed executable**. For a signed installer, obtain a trusted Authenticode signing identity (or eligible OSS signing program), secure the credential in CI, and sign the built package. Unsigned executables may trigger SmartScreen.

Never recommend disabling antivirus for ordinary installation. Publish an immutable source tag and SHA-256 checksum with every public binary.
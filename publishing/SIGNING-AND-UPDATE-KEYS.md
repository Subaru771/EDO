# Signing and upgrade compatibility

Android app ID intentionally remains `com.dropwave.tv`. Changing it would make this a separate app and disconnect upgrades from earlier releases.

Google Play distribution uses an **AAB** signed with a durable upload key; the store serves APKs using Google Play App Signing. A separately distributed APK may be signed with a different certificate. As a result, an existing sideloaded Edo APK might not update in-place to the Play version. Before uninstalling, copy or back up files that you want to preserve.

To generate/build locally on Windows: `BUILD-SIGNED-PLAY-RELEASE-WINDOWS.cmd`.

The signing key will be created at `%USERPROFILE%\EdoSigningKeys\edo-upload-key.jks`, **outside the source tree**. Keep multiple secure off-device backups and your passwords; never commit the key or upload it to untrusted services. Do not create a different key every release. Play App Signing has specific upload-key reset procedures, but plan to retain the original key.

Alternatively, set encrypted GitHub Actions repository secrets `EDO_KEY_BASE64`, `EDO_STORE_PASS`, `EDO_KEY_ALIAS`, `EDO_KEY_PASS` and run the workflow `Build signed Edo TV release (AAB + APK)` on your dedicated Edo repository. Keep the private key out of source and release artifacts.

The release pipeline has not been executed here. Verify output on actual TV hardware before posting downloads.

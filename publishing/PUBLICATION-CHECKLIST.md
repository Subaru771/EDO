# Edo public launch checklist — 2026-11-02

## Before any public download

- [ ] Run `bash scripts/test-all.sh` (backend/integration tests only; does not verify Android build).
- [ ] Compile the Android APK and AAB using Android SDK and JDK 17.
- [ ] Install on a real Google TV with remote and verify startup, QR display and back navigation.
- [ ] Verify iPhone Safari / Android Chrome / Windows browser transfers on your own LAN.
- [ ] Test upload and download of a large file with Wi-Fi interrupted and reconnected.
- [ ] Test PIN pairing, revoking a device, optional approval, HTTPS fallback.
- [ ] Review sensitive permissions (`REQUEST_INSTALL_PACKAGES`) and file access behavior.
- [ ] Make a back-up of the original Android signing key and passwords. Store outside GitHub.
- [ ] Confirm an update from the previous Android version using the actual signing key.
- [ ] Remove all placeholder contact and release URLs from website and privacy documents.
- [ ] Check app title/logo availability and choose a support contact address.

## Google Play TV

- [ ] Register a Google Play Console developer account (one-time $25 fee) if needed.
- [ ] Create Edo with package ID `com.dropwave.tv` (cannot change an existing Play app's ID).
- [ ] Upload the **signed AAB**, not the debug APK.
- [ ] Publish a real privacy-policy URL and add a support email.
- [ ] Complete Data Safety and `REQUEST_INSTALL_PACKAGES` declarations accurately.
- [ ] Add store icon, 1280×720 TV banner and genuine unaltered TV screenshot(s).
- [ ] Select Android TV release type (Play Console > Setup > Advanced settings > Form factors).
- [ ] Use internal testing first; for newly created personal accounts, complete the required closed test (at least 12 opted-in testers for 14 days) before requesting production access.
- [ ] Request production release only after tests and TV quality checks pass.

## GitHub Releases

- [ ] Create a dedicated Edo repository (do not put Edo in your YLTA repository).
- [ ] Decide whether source will be public and pick an explicit software license if you want to make it open source.
- [ ] Upload only release files; never upload the Android signing key, tokens or passwords.
- [ ] Create tag `v5.4.2` and a release after verifying the signed APK.
- [ ] Include signed APK, versioned source ZIP, changelog, checksum, installation instructions, privacy-policy URL and security warning.
- [ ] Label experimental iOS source as **source**, not a working installable iOS app.

## Not verified in this environment

Android APK/AAB compilation, real Google TV behavior, Apple signing and Google Play acceptance. Do not claim these steps passed until validated.

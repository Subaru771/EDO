# Edo v5.4.1 public-release preparation

This package is a publishing-ready **source candidate**, not an already published application. Android TV visual layout and actual Edo file transfer features are unchanged from v5.4. The source has been prepared for a production signing pipeline and store submission.

**New:** Windows signed AAB/APK builder; GitHub Actions signed-build workflow; TV-only Google Play form-factor declaration; Play listing/graphics; static website and privacy policy; screenshots checklist; no signing keys in the archive.

**Not done:** Android APK/AAB compilation in this environment; TV device smoke tests; public website deployment; Google Play Console setup/submission/review; public GitHub release. Don't label the app released until these finish.

## First actions

1. Extract the ZIP completely.
2. Run `BUILD-APK-WINDOWS.cmd` to confirm a working Android build (debug test).
3. Test Edo on actual TV hardware, with a phone and PC.
4. Run `BUILD-SIGNED-PLAY-RELEASE-WINDOWS.cmd` to create the production-signed .aab/.apk.
5. Prepare your real TV screenshots; host `publishing/website` publicly and add a support email.
6. Follow `publishing/PUBLICATION-CHECKLIST.md` to submit to Google Play or create a GitHub release.

Keep your original signing key if there is one. Do not generate a replacement key if an existing Play release is using another signing identity.

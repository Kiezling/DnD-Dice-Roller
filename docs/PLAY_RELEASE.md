# Play Store release

Release publication is separate from building the app or merging a pull request.
Version **1.1 / code 4** remains the proposed Play release. The current September
24 source includes persistent history, dark mode, Goldenrod controls, updated
feedback, and offline SecureRandom. Its release APK and bundle build unsigned;
the earlier signed artifacts predate these changes and must not be uploaded.

An earlier version was built and signed locally on September 23, 2026.
The owner submitted an upload-key reset request, which Google Play reports as
pending. A production draft with release notes is saved, but the bundle has not
been uploaded and the release has not been submitted for review.

The replacement certificate and both signed artifacts passed signature
verification. Signing keys, passwords, and release artifacts remain outside Git.
The signed, optimized APK also installed and cold-launched successfully on the
API 37 test emulator with no entries in the crash log. The production upgrade
check still requires Google's signed artifact after upload. The repository's
default release build remains unsigned.

## Release checklist

1. Play Console has been checked: Play App Signing is enabled and the highest
   uploaded version code is **3**. Recheck before upload if another release has
   been prepared in the meantime.
2. Keep the production application ID **com.kieslingdev.simpledice**. Debug builds
   use a separate `.debug` ID and cannot update the production listing.
3. Wait for the upload-key reset to take effect, then verify that Play Console's
   upload certificate matches the replacement key. The old certificate was
   verified, but its keystore password could not be recovered. The reset changes
   the upload key; Google's app signing key remains unchanged.
4. Configure signing locally, without committing keys or passwords. Build with
   `./gradlew bundleRelease` and verify the signed bundle. The default repository
   configuration deliberately produces unsigned release artifacts.
5. Test the signed, optimized release and verify an upgrade from the old
   Google-signed app using Play's internal testing track or Google's generated
   signed APK. A locally upload-key-signed APK cannot update the Google-signed
   production app because their signing certificates differ.
6. Create the production release, add notes, resolve any Console requirements,
   and submit it for review. Verify the actual status before announcing availability.

Output: `app/build/outputs/bundle/release/app-release.aab`.
The build task name `signReleaseBundle` alone does not establish that a bundle is
signed. Signing must be configured and independently verified.

## Proposed release notes

Save and browse your complete roll history with timestamps, range selection,
subtotals, and selective deletion. Choose instant or timed rolls and dark mode
from the new hamburger menu. Goldenrod controls and updated maximum/one animations
make results easy to spot. Rolls use secure, entirely offline randomness. Includes
light haptics, hold-to-animate, and improved landscape and large-text layouts.

## References

- [Google Play update requirements](https://support.google.com/googleplay/android-developer/answer/9859350)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
- [Prepare and roll out a release](https://support.google.com/googleplay/android-developer/answer/9859348)

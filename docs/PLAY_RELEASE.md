# Play Store release

Release publication is separate from building the app or merging a pull request.
The owner has requested the update; the current bundle is prepared but unsigned,
and has not been uploaded or submitted to Google Play.

## Release checklist

1. Open the existing app in Play Console and verify its signing configuration and
   highest uploaded version code. The candidate is version **1.1 / code 4**; use
   a higher unused code if Play has already received that code.
2. Keep the production application ID **com.kieslingdev.simpledice**. Debug builds
   use a separate `.debug` ID and cannot update the production listing.
3. Use the authorized upload key whose public certificate matches Play Console.
   The owner has the original keystore; its public certificate matches the original
   release bundle. The current Play upload certificate still requires verification.
4. Configure signing locally, without committing keys or passwords. Build with
   `./gradlew bundleRelease` and verify the signed bundle. The default repository
   configuration deliberately produces unsigned release artifacts.
5. Test the signed, optimized release and the upgrade path through Play's internal
   testing track before production submission.
6. Create the production release, add notes, resolve any Console requirements,
   and submit it for review. Verify the actual status before announcing availability.

Output: `app/build/outputs/bundle/release/app-release.aab`.
The build task name `signReleaseBundle` alone does not establish that a bundle is
signed. Signing must be configured and independently verified.

## Proposed release notes

Updated for modern Android devices. Added clearer history labels, a last-rolled
die indicator, and a new clear-history button. Ones now appear in red and maximum
rolls in gold for every die, with subtle animations. Hold Roll to scramble, then
release to roll. Includes light haptics, persistent history colors, and improved
landscape and accessibility support.

## References

- [Google Play update requirements](https://support.google.com/googleplay/android-developer/answer/9859350)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
- [Prepare and roll out a release](https://support.google.com/googleplay/android-developer/answer/9859348)

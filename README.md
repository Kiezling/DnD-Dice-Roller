# DnD Dice Roller

A small offline Android dice roller, reconstructed in Kotlin from the owner's
original APK. Choose D4, D6, D8, D10, D12, D20 or D100; roll; see the ten previous
results and cumulative totals, plus a permanent timestamped roll log.

The current roll appears below the history. Each history total includes the
current roll and all newer rolls, even when their dice have different sizes.
The current result shows ones in red and each die's maximum in gold; history
numbers stay neutral. Ones shake and flash brighter red twice in half a second,
then pulse toward maroon over 0.9 seconds each way. Maximums shine twice in half
a second, then repeat a 1.2-second shine with 2.4 seconds between sweeps. Controls use Goldenrod (`#DAA520`)
accents. Tap Roll for an immediate result and a
light haptic, or hold it to scramble numbers and release to commit one roll. Drag
outside the button to cancel. System haptic and animation preferences are respected.

The committed die stays beside the result even if you select a different die for
the next roll. Die selections also give a brief light haptic. The hamburger icon
above **Total** opens **Menu**, with two choices: instant taps with hold-to-animate/release-to-roll (the
default), or animated taps lasting 0.5–5 seconds in half-second steps. A saved dark
mode toggle applies to the main screen, settings, and full history.

Tap **History** for a separate full-screen log with local date/time timestamps on
the right, sized for wide dates and times and wrapping at large text sizes, newest
first. Rolls, recent visibility, selected die, and settings are saved locally in
SQLite and survive closing the app. The main trash button clears only recent
rolls; the full log has its own trash button to delete everything after confirmation.

Touch a recent history row and drag across adjacent rows to add them. Dragging
back shrinks the range. A faint gold outline marks selected rows; the sum and a
trash icon stay vertically centered beside them. Selection deletion removes those
rolls from both recent history and the archive, and recomputes running totals.
The selection has one continuous outline. Tap any selected number, blank space
above the rows, or the header area to deselect. In Full Roll History, tap a start
and end row or drag over results to select a range; swipe the timestamps to scroll
without changing selection. The sum and selected-roll trash sit in the gaps
beside the selected values, switching to a bottom bar with a centered sum and
bottom-right trash when the range extends offscreen (or large text leaves
insufficient room). All trash icons are white in dark mode and black in light mode.
Tap a selected row to deselect.
Keyboard/accessibility clicks can select a start and end row. A separate landscape
layout and scrollable history/choices support compact screens.

Supports **Android 6.0 (API 23) and newer**, compiling and targeting **Android 17
(API 37)**. The minimum was raised with the owner's approval to use current AndroidX.

Rolls use Android's device-seeded [SecureRandom](https://developer.android.com/reference/java/security/SecureRandom)
with unbiased bounded sampling. A single generator is reused without manually
setting a seed. The app needs no internet connection or network permission;
independent random rolls can still repeat or form streaks.

## Open and build

Open this folder in Android Studio. Use JDK 21 (tested),
Android SDK 37, and the included Gradle 9.7.1 wrapper (AGP 9.4.0). Set `ANDROID_HOME` to your SDK
location, or let Android Studio create the ignored `local.properties` file.
Gradle downloads the declared build tools and dependencies on the first build.

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

On macOS/Linux, use `bash ./gradlew` with the same tasks.

The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Its
application ID ends in `.debug`, so it can coexist with the Play version.

Run the Android UI tests on a chosen emulator (replace its serial if necessary):

```powershell
$env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat connectedDebugAndroidTest
```

`assembleRelease` builds an optimized **unsigned** release APK. Signing and Play
publishing are not configured. Check the existing Play signing setup and version
code before publishing any future update.

The actual original project was subsequently found and backed up unchanged in
[DnD-Dice-Roller-Original](https://github.com/Kiezling/DnD-Dice-Roller-Original),
tag `original-v1.0-build3`. Debug builds appear on the phone as **DnD Dice Roller Test**.

GitHub Actions runs unit tests, lint, and debug/release builds on pull requests
and changes to main. Device-test evidence is in the verification notes.

See [Play Store release steps](docs/PLAY_RELEASE.md) for the signing and upload checklist.

## Where to look

- [DiceState.kt](app/src/main/java/com/kieslingdev/simpledice/DiceState.kt): dice,
  random rolls, history, running totals, and critical-result rules.
- [MainActivity.kt](app/src/main/java/com/kieslingdev/simpledice/MainActivity.kt):
  view binding, roll settings, and rendering.
- [ArchiveActivity.kt](app/src/main/java/com/kieslingdev/simpledice/ArchiveActivity.kt): full-screen timestamped history and selection controls.
- [DiceStore.kt](app/src/main/java/com/kieslingdev/simpledice/DiceStore.kt): durable local history and preferences.
- [SelectionHistoryView.kt](app/src/main/java/com/kieslingdev/simpledice/SelectionHistoryView.kt): drag selection, subtotal placement, and deletion.
- [Recovery notes](docs/RECOVERY.md): what was recovered and why the code changed.
- [Verification](docs/VERIFICATION.md): tests, visual checks, and known limits.

The original source was unavailable during the initial work; this is a reconstruction, not a verbatim
restoration of the original Kotlin project. The original launcher artwork is
retained. Raw decompiler output and tools are ignored local recovery artifacts,
not required to build a fresh checkout.

---
Privacy Policy

Last updated: September 17, 2026

This Privacy Policy describes how DnD Dice Roller ("the App") handles your information.

Data Collection and Usage

No Personal Data Collected: The App does not collect, store, track, or share any personally identifiable information (such as your name, email, address, or phone number).

No Device or Usage Data: The App does not access sensitive device permissions, device identifiers, location data, or usage metrics.

No Third-Party Tracking: The App contains no third-party SDKs, analytics frameworks, or advertising networks.

Core Functionality
Roll results, timestamps, and preferences are stored locally so they survive app closure. You can delete the retained roll log from History. All random number generation (RNG) and dice calculation logic execute entirely on your device locally. No data is ever transmitted over the internet or sent to external servers.

Children’s Privacy
Because the App does not collect any data from any user, it complies with the Children’s Online Privacy Protection Act (COPPA) and does not solicit or gather information from children under the age of 13.

Changes to This Policy
If the data practices of the App ever change, this Privacy Policy will be updated accordingly.

Contact Us
If you have questions or suggestions regarding this Privacy Policy, contact:

Email: CoryKiesling@Gmail.com

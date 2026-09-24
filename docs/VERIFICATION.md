# Verification — compatibility and interaction update, 2026-09-23

## Build

Gradle 9.7.1, Android Gradle Plugin 9.4.0, JBR 21, compile/target SDK 37,
minimum SDK 23 (Android 6.0, approved by the owner). AppCompat 1.8.0,
Core KTX 1.19.1, AndroidX Test runner/core 1.7.0 and test JUnit 1.3.0.

These tasks passed on the final implementation (the unit task ran earlier on the
same model code; final layout/animation changes do not alter that model):

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
```

- 10 unit tests passed: ranges/endpoints for all dice, history capacity and totals,
  selection changes, clearing, critical rules, and invalid inputs.
- Android lint: **no issues found** (zero warnings/errors).
- Debug and optimized unsigned release APKs built successfully.
- No new app permissions, networking, tracking, or native libraries were added.
- Themed monochrome D20 launcher artwork added; obsolete pre-API-23 background
  bitmap fallbacks removed. Existing color launcher artwork retained.

## Android interaction tests

All **10 tests passed on each emulator**:

| Android | API | Emulator | Result |
| --- | --- | --- | --- |
| 6.0 | 23 | DiceApi23 / emulator-5558 | 10 passed |
| 16 | 36 | existing emulator-5554 | 10 passed |
| 17 | 37 | DiceApi37 / emulator-5556 | 10 passed |

Coverage includes all seven dice, roll/history totals, clear, rotation restoration,
red ones and gold maximums on every die, persistent history colors, committed die
label independent of selection, exactly one roll on tap/held release, no committed
roll during preview, drag-out/cancel, recreation during hold, accessibility click
and long-click, and rolling with animations disabled. A pixel comparison checks
that the glint crosses the actual numeral and finishes at unchanged gold.

Tests use a deterministic random source only through an internal test seam. The
production default remains Kotlin Random; scrambled previews are not stored rolls.
The reduced-motion test restores the prior effective animation scale, using 1 when
the previous setting was absent (deleting a live scale setting did not restore the
cached animator state on recent Android versions).

Runs targeted explicit emulator serials, never the physical phone. Local output:
`.recovery/modernize-build.log`, `.recovery/modernize-instrumentation-api23.log`,
`.recovery/modernize-instrumentation-api36.log`,
`.recovery/modernize-instrumentation-api37.log`.

## Visual checks

Inspected populated layouts at 1080×2424, compact portrait 840×1260, compact
landscape 1260×840 (420 dpi), and compact portrait at 200% font scale.

- [Portrait](screenshots/modernized.png)
- [Small portrait](screenshots/modernized-small.png)
- [Landscape with history beside controls](screenshots/modernized-landscape.png)
- [Enlarged text](screenshots/modernized-large-font.png)

The original vertical layout left almost no history visible in compact landscape;
a separate landscape resource now keeps history visible beside the controls.
Older history scrolls; die labels scroll horizontally when 48dp touch targets do
not all fit. The slider also selects every die. Headers remain **Die / History /
Total**, with the exact total semantics described for accessibility. Red #800000
and gold #866B00 are unchanged. Status/navigation/cutout insets remain respected.

Emulator display size, font scale, and screen timeout were restored after QA.
The two dedicated compatibility emulators were shut down; their AVDs remain
available for future testing.

## Artifacts and limits

- Installable test APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Optimized unsigned release: `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Version: 1.1 / code 4; debug name: **DnD Dice Roller Test** with `.debug` package suffix.
- Unit report: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Lint report: `app/build/reports/lint-results-debug.html`.

Haptics use the platform's brief CLOCK_TICK feedback and respect user preferences;
physical vibration strength has not been evaluated on a real device in this update.
The release APK is unsigned and not runtime-tested as a signed optimized build.
Testing three OS versions does not establish compatibility with every OEM/device.
No production signing, Play Console, publication, physical-phone update, or remote
push was performed for this update. The repository includes the complete source, tests, and build configuration.

## Version references

- [Android 17 SDK setup](https://developer.android.com/about/versions/17/setup-sdk)
- [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- [AndroidX releases](https://developer.android.com/jetpack/androidx/versions)
- [Core releases](https://developer.android.com/jetpack/androidx/releases/core)
- [Gradle distributions](https://services.gradle.org/distributions/)

Earlier recovery and original-source backup details are preserved in
[RECOVERY.md](RECOVERY.md) and the owner's local recovery notes. The original source is
backed up separately at https://github.com/Kiezling/DnD-Dice-Roller-Original,
tag `original-v1.0-build3`. An earlier debug reconstruction was installed on the
owner's Pixel 10 at their separate request; this new APK has only been installed
on emulators.

## Updated build installed on owner's phone
After the owner explicitly requested installation, the final updated debug APK was
installed successfully on their Pixel 10 and launched with `Status: ok` (449 ms
cold start). Package verification confirms version1.1-debug/code4, minSdk23 and
targetSdk37. This supersedes the earlier emulator-only installation scope for the
new build. Physical haptic feel still requires the owner's assessment.

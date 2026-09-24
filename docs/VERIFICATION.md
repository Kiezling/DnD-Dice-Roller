# Verification — Goldenrod history update

Verified September 24, 2026. The current app uses Goldenrod `#DAA520` in both
light and dark themes. The question-mark icons and help dialog are removed from
portrait and landscape. The hamburger above Total opens Menu, including
**Instant Roll (hold to animate)**, timed rolls, and dark mode.

## Behavior covered

- Persistent SQLite archive and preferences; newest-first stable IDs and local
  timestamps. Main clear preserves the archive; selected deletion removes only
  selected records; deleting the full archive requires confirmation.
- Range selection by taps or drag, reverse contraction, neutral subtotals, and
  timestamp-column scrolling. Offscreen selections retain a centered subtotal
  and bottom-right delete button. Trash icons are white in dark mode.
- Timestamp widths account for two-digit months/days, hours/day periods and the
  widest digits. Large fonts and compact rows wrap the full date instead of clipping.
- Instant taps, hold/release, timed rolls in half-second steps, cancellation,
  lifecycle cleanup, reduced-motion behavior, and system haptic preferences.
- Maximum results shine twice in 500 ms, then repeat a 1.2-second sweep with a
  2.4-second rest. Ones shake and flash twice in 500 ms, then pulse over 900 ms
  each way. Resuming a saved result skips the introductory burst.
- A shared, device-seeded SecureRandom supplies unbiased bounded results offline.
  No network permissions, manually supplied seeds or repeat suppression.

## Results

- 15 unit tests passed.
- All 28 instrumentation tests passed on the final build on Android 6 / API23.
- The same 28 tests passed on API37 before the final palette/help-removal edits.
- Debug APK, instrumentation APK, optimized unsigned release APK and release AAB
  built successfully. Lint: zero errors and six optional KTX-style suggestions.
- Timestamp tests cover all 12 months and 24 hours in US, UK and German locales
  at 320dp and normal/200% text sizes. Rendered-pixel checks cover both quick
  feedback bursts, continuing animations, and reset behavior.
- Visually inspected current light/dark main screens, Menu, history, selected
  ranges, and landscape. The test emulator is separate from the owner's play emulator.
- Installed the final debug APK over the owner's existing Pixel10 test app with
  `adb install -r`: Success. Cold launch: Status ok, 723 ms. Existing data retained;
  no test runner was used on the phone.

## Current screenshots

[Light main](screenshots/goldenrod-main.png) ·
[Dark main](screenshots/goldenrod-dark.png) ·
[Menu](screenshots/goldenrod-menu.png) ·
[History](screenshots/goldenrod-history.png) ·
[Selection](screenshots/goldenrod-selection.png) ·
[Landscape](screenshots/goldenrod-landscape.png)

## Artifacts and release scope

Version remains **1.1 / code 4**; the installed test app is
**DnD Dice Roller Test**, package `com.kieslingdev.simpledice.debug`.

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Unsigned release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Unsigned AAB: `app/build/outputs/bundle/release/app-release.aab`.
- Unit report: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Lint report: `app/build/reports/lint-results-debug.html`.

Installed APK SHA256:
`29A312C1CB4948C2A14DAFE892BDE45B5021AE3C637E92342DDD65E10081E695`.

GitHub Actions builds/tests/lints PRs and main on a clean Linux checkout. Device
compatibility checks do not establish behavior on every OEM/device. The final
optimized release has not yet been signed or runtime-tested. Earlier signed
artifacts are stale; see [Play release steps](PLAY_RELEASE.md) before any upload.
No Play publication is part of this update. Historical reconstruction evidence
is preserved in [RECOVERY.md](RECOVERY.md).

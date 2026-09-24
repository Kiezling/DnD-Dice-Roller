# Recovery and simplification notes

> This is a historical record of the initial reconstruction. The subsequent
> modernization uses Android 6.0+ and target API 37, applies critical colors to
> every die, and adds gestures, animations, and adaptive layouts. See
> [README](../README.md) and [current verification](VERIFICATION.md).

## Original source subsequently located

After the reconstruction, the owner located the original project at
`B:/Android Studio/SimpleDice`. Its actual Kotlin source, layouts, Gradle files,
artwork and original release bundle are now preserved unchanged in the private
[DnD-Dice-Roller-Original repository](https://github.com/Kiezling/DnD-Dice-Roller-Original),
tag `original-v1.0-build3`. All 75 copied original files were verified byte-for-byte.
The account below describes the initial APK recovery; original source is now also
available for reference. The original drive contents have not been edited.
The original GitHub repository contained a README only. This project was
reconstructed from the owner's `3.apk` (version code 3, version name 1.0), not
edited from the original Kotlin source. JADX 1.5.6 recovered the app's behavior,
layout and launcher artwork. Compiler-generated Java is not a fair measure of how
many lines the original Kotlin contained, so no source-line reduction is claimed.

## What the APK showed

The app has one activity. It selects D4, D6, D8, D10, D12, D20 or D100 using a
slider or tappable labels, initially D20. Each roll samples uniformly from 1
through the selected die's number of sides. The large number is the current
result; ten previous results appear above it, newest nearest the bottom.

Each history row shows the die used, its value, and a total including that roll,
the current result, and all intervening rolls. Totals intentionally combine dice
of different sizes. Only D20 and D100 highlight maximum results in gold and rolls
of one in dark red. Clear removes current/history/totals but keeps the selection.

## Changes worth learning from

| Recovered approach | Reconstructed approach | Why it helps |
| --- | --- | --- |
| Seven repeated selection/range branches | One `DICE` list and one random call | Adding a die changes one source of truth. |
| Roll data stored in text widgets | `Roll` and `DiceState` Kotlin data classes | Calculations work with integers and can be tested without Android. |
| Ten individually copied history results, die labels and totals | A bounded list of eleven rolls: current plus ten previous | Values and their die labels stay together. |
| Parse text back to integers and repeat growing sums | One running-total loop | Each total is calculated once without parsing UI text. |
| Separate reset assignments for every label | Clear the list and render | No history field can be accidentally left behind. |
| Fixed history labels with many positional constraints | One reusable XML history row in a scrollable column | Less layout duplication; small screens can reach every row. |
| No explicit state restoration in the recovered activity | Save selection and roll values in the activity state Bundle | Results survive rotation/activity recreation. |
| Kotlin synthetic view lookups | Generated View Binding | View references are type checked. |

The UI remains deliberately simple: one activity, one state file, three layouts,
and no database, network service, navigation layer or dependency-injection setup.
The original launcher artwork is reused. Changes to presentation are limited to
scrolling, selection feedback, larger touch targets, darker accessible colors,
screen-reader descriptions, and modern system-bar/cutout insets.

## Compatibility and release boundaries

- Release application ID remains `com.kieslingdev.simpledice`. Debug adds
  `.debug`, allowing installation next to the Play version.
- Minimum SDK remains 16 (Android 4.1). AppCompat 1.6.1 and Core 1.9.0 are pinned
  to retain that support. Compile/target SDK is 36, matching the installed SDK
  and emulator used for validation. This is not a claim of newest-version support.
- Release version is prepared as code 4 / name 1.1. Verify the next available
  version code in Play Console before any future publication.
- Release signing is intentionally not configured. The APK cannot recover the
  original private signing/upload key. Use the existing Play signing arrangement
  and an authorized upload key for any future update.
- State restoration covers Android's saved-instance-state lifecycle. Closing the
  task or force-stopping the app does not promise persistent roll history.
- The locally recovered APK, decompiled third-party libraries, and JADX download
  remain in ignored `.recovery/` and `.tools/` folders. They are not app sources or
  build inputs; fresh checkouts build without them.

## Provenance

Input APK SHA-256:
`FF4F4D8B1FC04D167BF2890ED88EB935E328C42155AD2EB90554D6F6141C11D0`.

Decompiler: [JADX 1.5.6](https://github.com/skylot/jadx/releases/tag/v1.5.6).
The app's MainActivity decompiled without errors. Four errors occurred elsewhere
in bundled dependencies; those dependencies were not reconstructed from their
decompiled code and are resolved normally by Gradle.

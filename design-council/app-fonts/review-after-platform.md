# After-check: platform

What I checked: `git diff main`, `build_archivo.py`, the five TTFs (cmap, metrics, names, size) and the comparison sheets. `FontTest`, `ThemeTest` and `FitTextTest` all pass locally: 14 tests, 0 failures.

## Verdict: fix first (one small item)

**Blocking: U+202F (narrow no-break space) is missing from every file.** `formatTime()` (`Format.kt` 149) uses `ofLocalizedTime(SHORT)`. On Android 14+ and on JDK 21 (the harness), that produces `6:10 PM`, so "started 6:10 PM" in `*-03-workout` and "7:30 AM" in `light-15-bodyweight` fall back to the system font at the space before the AM/PM marker. `FontTest` doesn't catch this because it only scans literal characters in the source.

Fix in `build_archivo.py`, for every file:
- map U+202F to the existing thin-space glyph (U+2009);
- add U+200B as an empty glyph with zero advance (`SetGroupsUi.kt` 484 writes it as an escape, `​`).

Then extend `FontTest` to cover runtime text: assert that `formatTime`, `formatDate` and the `\uXXXX` escapes in the source are all in every cmap.

## Chip wrap

Accept it. Today's fonts wrap "42.5 kg/DB / × 10" in the same place (`after/dark-03c`), and the wider space is what makes the value readable. If it bothers anyone, lower `FitText`'s floor for ledger chips only, rather than narrowing the space again.

## Non-blocking follow-ups

- **Notifications aren't covered by the harness.** The `@font/…` and `fontFeatureSettings` in RemoteViews layouts should be checked once on the API 34 emulator and on the A71, collapsed and expanded, before the PR.
- **The bold files' metadata is inconsistent.** `fsSelection` has REGULAR set and BOLD unset, and `macStyle` is 0. Weight class 700 drives Android's matching, so nothing fake-bolds, but set the bits in `set_names`.
- **The licence download is unpinned** (`LICENCE` has no SHA-256). Pin it like the font.
- **Size:** 264 KB raw, about 101 KB deflated, about +5% on the release APK. Fine as is. A variable file for the words could save about 35 KB later.
- **Cyrillic and Greek names** still fall back to the system font. Note that trade-off in DESIGN.md.
- **Stat tile labels** still clip at 200% ("Workin" in `large-05`). This is an existing bug.

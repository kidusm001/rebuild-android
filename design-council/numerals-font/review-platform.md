# Platform review: numerals font

## 1. Verdict: bundle a numerals font only

Today's numerals depend on a family name nobody has checked on the A71. Robolectric's `fonts.xml` maps `sans-serif-condensed` to static RobotoCondensed (I checked this in `nativeruntime-dist-compat-1.0.16.jar`), so our JVM screenshots show that. AOSP 12+ maps it to variable Roboto at `wdth 75` (fairly sure, not checked). What One UI does with it, and whether a Samsung font style overrides it, is **unknown**. If the name doesn't resolve, Compose falls back to the default sans (moderately sure). That font is normal width, about M's ~123%, which breaks the 200% layouts before any jitter. Bundling turns that unknown into a file we ship and test, for tens of KB. **Don't bundle a text font.** It would override a font style the user chose, put Latin words next to Amharic in fallback fonts that don't match, and cost hundreds of KB. It fixes nothing we've measured.

## 2. Pick

- **First: B, Roboto Condensed Bold** as a static instance. It matches today at 100% on all four widths, and `tnum` is present. It's the only choice that needs no new design judgment.
- **Runner-up: H, Fira Sans Condensed Bold.** It runs 97–99% width, so it fits wherever today fits. It has the clearest 1/7, 3/8 and 6/9 at arm's length. Pick it only if the council wants a visible change.
- D reads best, but at 105–106% it's the first to break `1:08:42` at 200%. J, K and L jitter.

## 3. Scores (legibility / stability / fit_200 / character / cost)

- A, system (today): 3 / 2* / 3* / 3 / 4. *These two are unverified on the A71.
- **B, Roboto Condensed:** 3 / 4 / 3 / 3 / 3
- D, Barlow Semi Condensed: 4 / 4 / 2 / 3 / 3
- E, Barlow Condensed: 3 / 4 / 3 / 3 / 3
- **H, Fira Sans Condensed:** 4 / 4 / 3 / 3 / 3
- I, Archivo 75: 3 / 4 / 4 / 2 / 3

## 4. Risks and checks

1. **The subset has to cover words too.** `Numerals` draws "Rest 2:50", "Pause" and "Done" (RestBar.kt:135), "of 4" (RestEndMotion.kt:303), and "kg", "DB" and "Last" in ledger and History lines, which use U+00A0. Android falls back glyph by glyph without any error. A digits-only subset would show "Rest" in system Bold next to condensed digits. Subset to Basic Latin, Latin‑1, `– − × · …` and U+00A0/U+202F, and keep `tnum`, `lnum` and `kern`.
2. **The licence file may be shrunk away.** `res/raw/lucide_license.txt` isn't referenced from code. Release builds have `isShrinkResources = true` and the project has no `keep.xml`, so the file is probably already stripped from release APKs (not checked: run `unzip -l` on a release APK). Ship `OFL.txt` in `assets/` or add a `tools:keep`. If the OFL header names a Reserved Font Name, rename the subset.
3. **The A71 tops out at Android 13, so font scaling is linear** (fairly sure). `rebuild_api34` and the harness (`sdk = 35`) use Android 14's non-linear scaling, which flatters display sizes at 200%. Re-run the 200% screens with `@Config(sdk = [33])` or an API 33 AVD.
4. **Ship one static Bold `.ttf`.** DurationPicker asks for Medium and MeasuredEntry for Normal; both probably resolve to Bold today (not verified). A Medium file would quietly change them.
5. **Tests.** Add a Robolectric `NATIVE` test with `TextMeasurer` that asserts "0:00" through "9:59" and "1:08"/"1:11" all have the same width in `displayLarge`. Add a cmap-coverage test for the subset list. With a bundled font these tests check the same bytes the A71 renders.
6. **On the A71,** with the default font style and then a downloaded Samsung style, check:
   - the countdown going 1:11 → 1:08
   - ledger columns
   - the compact "Rest 2:50" bar
   - stat tiles at max text size
   - an Amharic exercise name in History

## 5. What would change my mind

- **Toward keeping system fonts:** A71 evidence, meaning `adb shell cat /system/etc/fonts.xml` plus screenshots under a non-default font style. It would need to show that `sans-serif-condensed` is Roboto Condensed, that font styles leave it alone, and that `tnum` holds.
- **Toward H or D over B:** the council judging today's figures weak at arm's length. D only if it fits at linear 200% on API 33.
- **Toward bundling a text font:** proof that One UI font styles break our layouts. Even then I'd fix the layouts, not the font.

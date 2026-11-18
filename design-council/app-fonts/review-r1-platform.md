# Round 1 review: platform and shipping

Lens: what it costs to ship and live with each set. Measured with fontTools on the downloaded sources. Scripts are in `scratchpad/platform/`.

## Top 3

**1. Roboto Flex.** It keeps today's metrics, so `large-01`, `large-03` and `large-05` lay out like the baseline. Its digits are tabular by default. The display optical size gives the countdown a crisper, heavier 2:50 (`dark-02`). Coverage is full (Ext-A, Greek, Cyrillic; only Ѐ/ѐ are missing). One subset variable file (wght, wdth, opsz) is about **124 KB deflated**.

**2. Archivo.** It has the most 200% headroom of any set with character. Every large screen holds the baseline layout, and its numerals are 92–96% of today's width. One variable file is **89 KB**. Its digits are proportional, so it depends on `tnum`; every style sets it, and a test should lock that in. It is Latin only, so typed Cyrillic or Greek names fall back to the system font.

**3. Roboto.** The zero-risk control: 100% widths, full coverage including `●○`, one **105 KB** file, and no changes to layouts or tests. It adds no character.

Honourable mention: **Sofia** holds 200% and has full scripts. Its weights run light (its 600 stem is 0.100 em against Roboto's 0.129), and Amharic looks a size too big beside it.

## Scores (fit / legibility / numerals / large / distinct)

| Set | fit | leg | num | large | dist |
|---|---|---|---|---|---|
| baseline | 3 | 3 | 3 | 3 | 1 |
| roboto | 3 | 3 | 3 | 4 | 1 |
| noto | 2 | 3 | 3 | 1 | 1 |
| robotoflex | 3 | 3 | 4 | 4 | 2 |
| archivo | 3 | 3 | 3 | 4 | 3 |
| fira | 3 | 3 | 2 | 2 | 3 |
| sofia | 3 | 2 | 3 | 3 | 3 |
| atkinson | 2 | 3 | 2 | 2 | 2 |
| asap | 2 | 3 | 1 | 4 | 3 |
| nunitosans | 2 | 2 | 3 | 2 | 2 |
| instrument | 2 | 2 | 3 | 1 | 2 |
| googlesansflex | 2 | 3 | 2 | 0 | 0 |
| monasans | 2 | 3 | 1 | 1 | 0 |

## Eliminate

- **googlesansflex**: it breaks layouts at 100% scale. "42.5 kg/DB × 9" wraps even after FitText has shrunk it as far as it can (`dark-03c-workout-drop`), and "Seated dumbbell shoulder press" wraps (`dark-02`). No Cyrillic; reads as Pixel UI.
- **monasans**: the OFL Reserved Font Name "Mona" means any subset we ship must be renamed. Its zero is slashed in the countdown (`dark-02`), and it is GitHub's face. No Cyrillic.
- **instrument**: missing `± ½ ² µ`, Cyrillic and Greek. Its word spacing is tight (`dark-10`), and titles wrap to 3 lines (`large-03`).
- **noto**: its line box is 1.36 em against Roboto's 1.17, so cards, tiles and the rest bar all grow (`dark-02`, `dark-10`). At 200% "Today 0 of 2" ends up under the rest bar (`large-03`).
- **nunitosans**: the same tall metrics, wraps at 200% (`large-03`), and no Greek.
- **fira**: statics only, at **307 KB**, the heaviest set. It wraps at 200% (`large-01`, `large-03`), and its colon is narrow.
- **atkinson**: words and numerals come from two families. The words face is Latin only with 35 Ext-A gaps, and the numerals lose the hyperlegible shapes.
- **asap**: its colon is 0.37 of a digit, so `2:50` looks jammed (`dark-02`), and Compose can't track one glyph. Latin only.

## Round 2 tuning

- **Roboto Flex**: numerals at wdth 72–78 and wght 700, with opsz matched to size (36 for the countdown, 144 for the 240 sp rest-end numeral). Try GRAD −25 in dark mode only; it calms glare without reflowing text.
- **Archivo**: numerals at wdth 80–85, which buys a larger countdown. Check titles at 700 on `light-09`.
- **Roboto**: no tuning; it stays as the control.
- **All**: declare numerals at 700 only, or deliberately at 600 and 700. The baseline declares only Bold, so today every numeral draws at 700. The candidates draw real 500 and 600 (`DurationPicker`, compact `RestBar`, `HistoryScreens` 334/1087), and that is partly why they look lighter.

## Problems found

- **`●○`** (the chart legends, `Charts.kt` 306) are missing in every set except roboto, sofia and monasans. Those three draw them about twice the size of the fallback (`dark-15-bodyweight`). Draw them as shapes, like `↳`, which falls back everywhere (`dark-03c`).
- **Licence**: `res/raw/lucide_license.txt` is not in the release APK, because `shrinkResources` strips unreferenced raw files. I checked the release APK in the `button-highlight-flash` worktree. The OFL text has to ship with the fonts, so put it in `assets/` or add `tools:keep`. `docs/icons.md` wrongly says the Lucide licence is packaged.
- **Size**: the release APK is about 2.2 MB, so fonts add 4–14%. A single variable file roughly halves the cost of statics (Roboto: 233 → 105 KB deflated).
- **Stat tiles at 200%** clip their labels with no ellipsis ("Workin", "Exercis" in `large-05`, baseline included). This bug already exists.
- **Font scale**: the harness runs on SDK 35, where 200% scaling is non-linear. The A71 runs Android 13, where scaling is linear, so headlines grow more on the phone than in these screens.
- **Tests**: no test guards the font; ThemeTest checks colours only, and FitTextTest uses an unstyled TextStyle. Add three:
  - every Typography style uses the bundled family and has `tnum`;
  - a `Paint.hasGlyph` coverage test over the app's literal symbols (`· – × − … “ ” › NBSP`) and a sample of Latin-1, Ext-A and Cyrillic;
  - a Robolectric check that the variable path matches the statics. That path is unverified, since the harness loads static `Font(File)`.
- **Amharic** (`scratchpad/platform/amharic.png`): roboto, robotoflex, archivo and noto sit level with the Latin. Sofia, asap and nunitosans make Ethiopic look larger and darker.

**Most important round-2 experiment**: build the finalists as they would ship: one subset variable `res/font` file plus `FontVariation.Settings`, with the OFL text in `assets`. Pixel-diff them in the harness against these statics. Then run them on the API 34 emulator at maximum font scale with Amharic, Cyrillic and drop-set data.

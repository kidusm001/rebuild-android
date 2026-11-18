# App fonts, round 1: UX and accessibility review

Lens: glance reading mid-set in a bright gym, low vision, 200% scale, dark-mode glare, confusable characters, small labels. Details were checked on full-size crops.

## 1. Top 3

**1. Archivo.** It's the only non-Roboto set that holds every 200% screen I checked. In `large-03-workout` the title stays at two lines and "Today 0 of 2" stays above the rest bar. In `large-01-today` "+1 more" stays visible. The countdown colon has air on both sides (`light-03-workout`), and the tiles in `light-06-plan` read as solid, separate shapes. It's plainer than Roboto without being sporty, and its numerals sit at 92–96% of today's width, which leaves room to grow them. Weak spots: bare `I` and `l`, and Latin only.

**2. Roboto Flex.** It passes every large screen. At `opsz 36` it has the densest countdown and tiles in the set, the fastest read under glare. `GRAD` can thin light-on-dark text without reflowing it, which helps the amber readouts in `dark-10-exercise-history`. It adds no distinctiveness. Its words are set at a fixed `opsz 16`, so small labels come out tighter: "Seated dumbbell shoulder press" fits on one line in `light-09-receipt`, where every other set wraps.

**3. Noto Sans.** It has the best letterforms for low vision: open apertures, a large x-height and a clear 6/8. It matches Noto Ethiopic. At `wdth 100` it **fails 200%**: in `large-03-workout` the title takes three lines and "Today 0 of 2" ends up under the rest bar, and in `large-01-today` "+1 more" is lost. Its width axis can fix this, so it goes through on condition.

## 2. Scores (0–4)

| Set | fit | legib. | numerals | large | distinct |
| --- | --- | --- | --- | --- | --- |
| baseline | 2 | 3 | 3 | 3 | 1 |
| roboto | 2 | 3 | 3 | 4 | 1 |
| noto | 3 | 4 | 3 | 1 | 2 |
| robotoflex | 3 | 3 | 4 | 4 | 1 |
| archivo | 3 | 3 | 3 | 4 | 3 |
| fira | 3 | 4 | 2 | 2 | 3 |
| sofia | 3 | 2 | 2 | 4 | 3 |
| atkinson | 2 | 3 | 2 | 2 | 3 |
| asap | 2 | 2 | 1 | 3 | 2 |
| nunitosans | 2 | 2 | 2 | 2 | 2 |
| instrument | 2 | 2 | 2 | 1 | 2 |
| googlesansflex | 2 | 3 | 3 | 0 | 0 |
| monasans | 2 | 3 | 2 | 1 | 1 |

## 3. Eliminate

- **asap**: in `2:50` the colon nearly touches the 2, and "42.5kg/DB×10" runs together (`light-03-workout`, `dark-10-exercise-history`).
- **nunitosans**: low x-height and thin numerals that fade in glare.
- **instrument**: "dumbbell·Target" has no visible space, the title takes three lines at 200%, and missing glyphs bring back font mixing.
- **googlesansflex**: the exercise name wraps at **100%** scale (`dark-02-today-active`). It's the worst at 200% and reads as Pixel UI.
- **monasans**: a slashed zero shows in the bold and condensed cuts (`2:5Ø`, "Ø RIR") but not consistently elsewhere. It also fails 200% and is GitHub's brand face.
- **atkinson**: the words get a slashed zero ("Ø of 2") but the numerals don't ("10", "2:50"), so two zero designs sit on one screen and the hyperlegibility never reaches the numbers. It wraps at 200%.
- **sofia**: it fits 200% best, but its low cap height makes everything read a size smaller (`dark-22-rest-end-sweep-hold`, the nav labels). Its digits are loosely spaced and its en dash looks like an em dash. Adding 1 sp would use up the width saving.

**Keep `roboto` as the control.** **Fira** has the best 1/l/I separation, but its words run 104% wide, it has no width axis and it wraps at 200%. Drop it unless others rank it high.

## 4. Round 2 tuning

- **Archivo:** numerals at `wdth 80` and `wdth 85`, `wght 700` and `750`, to use the headroom. Re-check 3/8 and 6/8 at 64 sp. Render one Cyrillic name to check the fallback.
- **Roboto Flex:** make words' `opsz` follow the sp size. Try `GRAD -50` on light-on-dark text and on amber text on dark. Compare numerals at `wght 700` and `750`.
- **Noto Sans:** words at `wdth 87.5` and `90`. Re-run `large-01`, `large-03`, `large-05` and `large-03d`. Keep the numerals at `wdth 75 wght 700`.

## 5. Problems found

- `dark-02-today-active` (googlesansflex): the exercise name wraps at default scale.
- `large-03-workout`: the title wraps to three lines and pushes "Today 0 of 2" under the rest bar (noto, instrument, googlesansflex, monasans). "Set 1 of 2 · prefilled…" wraps to three lines (fira, atkinson, nunitosans).
- `large-01-today` (noto, fira, instrument, googlesansflex, monasans): "+1 more" is pushed under the fade.
- `large-05-history-detail`: tile labels are cut to "Minute / Workin / Exercis" in baseline and most sets. This is **pre-existing**; fix it in the layout.
- `light-03-workout` and `dark-10-exercise-history` (asap, instrument, Fira Condensed): the colon and `×` crowd the digits.
- `light-03-workout` (sofia): the en dash renders as long as an em dash.
- Mixed zero designs: monasans and atkinson.
- `↳` falls back to a system glyph everywhere (`large-03d-set-and-drop-editor`). Draw it as a vector icon.

## Most important round-2 experiment

A **confusable-digit glance sheet**: the countdown at 64 sp on amber and on `#0F0E0D` showing `1:11`, `3:38`, `6:08`, `0:58` and `10:01`, plus a ledger row "18.5 kg × 11 · RIR 1" beside "Incline / lateral". Render at device size, blur to about 20/80 vision, and rank which digits stay distinct. Today's screens only show 2, 5 and 0.

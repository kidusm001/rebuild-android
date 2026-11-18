# App fonts, round 2: visual and typography review

## 1. Final pick: `archivo-w80`

- **Words:** Archivo at `wdth 100`, weights 400/500/600/700 as Theme.kt assigns them today.
- **Numerals:** Archivo at `wdth 80`, `wght 700`, with `tnum` on every numeric style.
- I wouldn't split the difference at wdth 78. At 80 the numerals are 97–100% of today's width, so the 200% layouts still fit (large-03-workout matches Roboto's layout line for line). At 75 the extra width isn't used, and the ledger reads pinched.

Why w80 over the other two Archivo cuts: on the dark ledger (dark-10-exercise-history), the space in "42.5 kg/DB × 10" opens at 80. At 75 the decimal point and "kg" crowd the figure, and the 800 weight makes this worse: "42.5kg" fuses into one block and the counters of 8 and 9 close up. In the 64 sp glance rows, 80/700 keeps the most open counters in 3/8 and 6/0/8 while matching 75/800's presence. Under blur, 80/700 holds as well as 800 does. The 800 weight is louder without being clearer, which is the wrong trade for a calm notebook.

On WHOOP drift: beside the `din-ref` column (glance.png), Archivo's figures are clearly a grotesque, not a DIN. They have stroke modulation, a flagged 1 and squarer bowls, where DIN is round-shouldered and evenly drawn. The 75/800 cut drifts closest to a poster or stat-sheet look, and w80/700 drifts least. On the near-black canvas it reads as Rebuild's own ink, not as a wearable's dashboard.

## 2. Runner-up: Roboto Flex (words wdth 100 opsz 16; numerals wdth 72 opsz 36 wght 700)

It should win instead only if Latin-only coverage is unacceptable, meaning the owner expects exercise names typed in Cyrillic or Greek. That is the one requirement Archivo can't meet, and Roboto Flex meets it with no layout risk. Fira is a close third for warmth, but it reflows at 200% ("Set 1 of 2 · prefilled from last time" takes 3 lines, and the inputs drop a row in large-03-workout), so it shouldn't be the fallback.

## 3. Scores (0–4)

| Set | fit | legibility | numerals | large | distinct |
| --- | --- | --- | --- | --- | --- |
| roboto | 2 | 3 | 3 | 3 | 1 |
| robotoflex | 2 | 3 | 3 | 3 | 1 |
| archivo (w75/700) | 4 | 3 | 3 | 3 | 3 |
| archivo-w80 | 4 | 3 | 4 | 3 | 3 |
| archivo-w75-800 | 3 | 3 | 3 | 3 | 3 |
| fira | 3 | 4 | 3 | 2 | 3 |

## 4. Must-fix before shipping

- **Space around × and after the decimal in the ledger.** Even at w80, "kg/DB×10" is tight on dark. Check that `ledgerValueStyle()` keeps the thin or normal spaces around `×`. If they're already there, add about +0.01 em of tracking to the ledger style only, then re-check the 200% ledger.
- **Vertical metrics.** Archivo sits a few dp lower than Roboto in the rest-end sweep ("2 of 3", dark-22-rest-end-sweep-hold) and in the alarm bar (light-23). Check that the label, digit and "of 3" are optically centred in the sheet. Set `LineHeightStyle` with `Trim.Both` on the countdown, or adjust padding. Don't leave the drift to chance.
- **Headline weight.** Archivo at 700 for `headlineLarge` is slightly heavy at 32 sp on the warm canvas. Try 650 on the variable instance, or 600 with -0.01 em tracking, and keep whichever leaves "Tuesday" calm.
- **Glyph coverage.** Archivo is Latin only. Verify on a device that a Cyrillic or Greek exercise name falls back cleanly, and that `−`, `×`, `–`, `·` and the NBSP all render from Archivo. `↳` should become a vector icon so it never falls back.
- **On-device check.** Robolectric smooths hinting. Check the 11–12 sp labels ("RIR 1", "Last time 13 Oct", the Il1 O0 row) on the A71 in dark mode at arm's length before signing off.
- **Pre-existing and font-independent:** the stat-tile labels clip at 200% ("Minute", "Workin"). That still needs a layout fix.

## 5. Did round 2 change my mind?

Partly. I still pick Archivo. I asked for the heavier 75/800 countdown, but side by side it crowds the ledger and edges towards the stat-sheet look. The w80/700 cut does more for the countdown by opening its counters than the extra weight did.

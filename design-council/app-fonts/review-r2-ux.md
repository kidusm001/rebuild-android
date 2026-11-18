# App fonts, round 2: UX and accessibility review

## 1. Final pick: `archivo-w80`

- **Words:** Archivo `wdth 100`, `wght` 400 / 500 / 600 / 700.
- **Numerals:** Archivo `wdth 80`, `wght 700`, with `tnum` on every numeral style.

It is the only set that passes every check through my lens:

- **200% scale.** It holds `large-01-today`, `large-03-workout` and `large-05-history-detail` exactly as Roboto does.
- **Logged-set chip.** In `dark-03c-workout-drop` it fits "42.5 kg/DB × 10" on one line at default scale. Roboto and Fira both wrap it to "42.5 kg/DB / × 10".
- **Confusable digits (`glance.png`).** 1:11, 3:38, 6:08 and 10:01 stay distinct under blur. Its O and 0 differ clearly in width, unlike the DIN reference, where they look almost the same.

Against `wdth 75`, it opens the ledger and enlarges the countdown slightly, still within today's width.

I wouldn't pick a setting between the rendered ones without a new render. The one I'd test is in must-fix 3.

## 2. Runner-up: Roboto Flex

It's the runner-up because it has the densest countdown, passes every large screen and has Cyrillic and Greek.

**It should win instead if** exercise names typed in Cyrillic or Greek must render in the app's own font. Archivo is Latin only, so those names would fall back to the system font, which is the mixing this change exists to remove.

## 3. Scores (0–4)

| Set | fit | legibility | numerals | large | distinct |
| --- | --- | --- | --- | --- | --- |
| roboto | 2 | 3 | 3 | 4 | 1 |
| robotoflex | 3 | 3 | 4 | 4 | 1 |
| archivo | 3 | 3 | 3 | 4 | 3 |
| archivo-w80 | 3 | 3 | 4 | 4 | 3 |
| archivo-w75-800 | 3 | 3 | 3 | 4 | 2 |
| fira | 3 | 4 | 2 | 2 | 3 |

- **archivo-w75-800:** strongest under blur, but the ledger is the most cramped ("42.5kg/DB×10"), and it looks the closest of the finalists to the `din-ref` column.
- **fira:** still wraps "Set 1 of 2 · prefilled…" to three lines and pushes "+1 more" under the fade at 200%. It also wraps the logged-set chip at default scale.

## 4. Must-fix before shipping

1. **Word space in the ledger.** Archivo's narrow space nearly joins the number and the unit ("42.5 kg/DB" in `dark-10-exercise-history`, ledger and drop rows). Add about +0.01 em tracking to `ledgerValueStyle()` or widen that space, then re-check that the chip still fits.
2. **Width of the logged-set chip.** At `wdth 80`, "42.5 kg/DB × 10" ends about 1 dp from the edit button (`dark-03c-workout-drop`). Test the longest realistic values, such as "107.5 kg/DB × 12" and lb and plate units. If one wraps, it must break before "×" and keep "× 12" together: use a non-breaking space between "×" and the reps.
3. **Countdown weight in daylight.** In the blurred `glance.png`, `wdth 80` at 700 reads a little lighter than Roboto and `w75-800`. Check the rest bar on the A71 at full brightness in daylight. If it reads light, raise only the countdown roles (the rest bar and the rest-over number) to `wght 750` or `800` at `wdth 80`, and re-measure their width against today's.
4. **Coverage.** Render one Cyrillic exercise name and one Amharic name on the device to confirm how the fallback looks.
5. **Carried over from round 1.** Draw `↳` as a vector icon. Fix the stat tile labels cut to "Minute / Workin / Exercis" in `large-05-history-detail`. That one is a layout problem, not the font.

## 5. Did round 2 change my mind?

Partly. Archivo stays first, but I moved from `wdth 75` to `wdth 80`. The ledger reads better and it still fixes the chip wrap that Roboto shows. The heavier `w75-800` reads well under blur but crams the ledger and moves toward the WHOOP/DIN look.

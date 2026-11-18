# Round 2 review: product and brand positioning

## Final pick: `archivo-w80`

- **Words:** Archivo, `wdth 100`, `wght` 400 / 500 / 600 / 700, in the same
  roles as today.
- **Numerals:** Archivo, `wdth 80`, `wght 700`, `tnum` on every numeral style.
  One setting covers the countdown, ledger, tiles and chart readouts.

For the product, Archivo-w80 gives Rebuild its own face without paying for it in
layout. It's a plain, sober grotesque that no lifting app uses: not Material
(roboto, robotoflex), not geometric fitness (Poppins, Figtree) and not a brand.
On the dark ledger (dark-10) and the receipt (light-09) it reads like a
well-kept logbook. It should still look right in two years, because it isn't
fashionable. Width 80 fixes my round-1 concern: "18.5 kg × 11" and
"42.5 kg/DB × 10" have air again (glance, dark-10), and it stays within today's
countdown width.

The WHOOP check passes. In glance.png the `din-ref` column is narrower and
squarer, with DIN's closed, mechanical 6 and 8. Archivo's figures are rounder
and more open, so they read as a grotesque, not a stat sheet. `archivo-w75-800`
moves closer to that look because of its weight, which is why I don't pick it.

A setting between the rendered ones is worth one measurement: countdown roles
only (rest bar, sweep, alarm bar) at `wdth 78 wght 750`. The blurred amber row
favours the heavier figures. Ship it only if it measures ≤100% of today on
`1:08:42`, and otherwise keep 700.

## Runner-up: `fira`

Fira is still the warmest and most "notebook" voice, and its small labels are
the clearest on dark (glance: "Il1 O0 · RIR 1"). It costs layout, though.
Archivo holds the "Set 1 of 2 · prefilled from last time" line at two lines at
200%, where Fira's runs to three, pushing the editor down (large-03). The alarm
copy wraps an extra line too (light-23). **Fira should win only if Archivo looks
cold or industrial on the A71 during evening review.** In that case, ship Fira
words one weight step lighter and accept the 200% reflow.

## Scores (0–4)

| Set | fit | legibility | numerals | large | distinct |
| --- | --- | --- | --- | --- | --- |
| roboto | 2 | 3 | 3 | 3 | 1 |
| robotoflex | 2 | 3 | 3 | 3 | 1 |
| archivo | 3 | 3 | 2 | 3 | 3 |
| archivo-w80 | 4 | 3 | 3 | 3 | 3 |
| archivo-w75-800 | 3 | 3 | 3 | 3 | 2 |
| fira | 4 | 4 | 3 | 2 | 3 |

## Must-fix before shipping

1. **Keep `tnum` on every numeral style.** Archivo's digits aren't tabular by
   default. Add a test that asserts `tnum` on each numeral style.
2. **Script coverage.** Archivo has Latin only, so Cyrillic and Greek exercise
   names fall back to the system font. Accept this, but write it down. Amharic
   falls back to Noto Sans Ethiopic whatever we pick. It sits a touch large
   beside Archivo (fit 1.08), but looked fine in glance.
3. **Draw `↳` as a vector.** Otherwise One UI's symbol font shows up inside
   drop rows.
4. **Put the licence (OFL) in `assets/`**, so `shrinkResources` doesn't strip it.
5. **Fix the stat-tile labels in large-05.** They truncate to "Minute",
   "Workin" and "Exercis" in every set. This is a layout bug the font change
   doesn't cause or fix.
6. **Verify on the A71 (not possible in Robolectric):**
   - hinting of 11–12 sp labels on `#1D1B19` ("Last time 13 Oct", "RIR 1");
   - the countdown under gym glare on `#EBA42A`;
   - an Amharic exercise name beside a `headlineMedium` title;
   - that Samsung's font style no longer leaks into any screen, including
     dialogs, the notification and picture-in-picture, which use their own text
     paths.

## Did round 2 change my mind?

Yes. In round 1, Fira was my pick and Archivo second. Round 2 changed that in
three ways: Fira wraps more at 200% (large-03, light-23), wdth 80 removes
Archivo's cramped ledger, and the DIN reference shows Archivo isn't WHOOP. For a
gym-first app, layout parity at large text matters more than Fira's extra warmth.

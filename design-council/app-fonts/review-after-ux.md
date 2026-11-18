# App fonts, after-check: UX and accessibility

## 1. Verdict: ship

The implementation lands what I asked for. Nothing I found blocks the PR.

- **Countdown and rest-over.** Both sit on today's baselines with a clear colon (`light-23-rest-alarm-bar`, `dark-22-rest-end-sweep-hold`).
- **Number–unit space.** It now reads as a space: "42.5 kg/DB" in `dark-10-exercise-history` and "82.3 kg" in `light-15-bodyweight`.
- **200% scale.** It holds exactly as baseline does in `large-01-today` ("+1 more" stays visible), `large-03-workout` ("Today 0 of 2" stays above the rest bar) and `large-05-history-detail`.
- **Drawn ↳.** Heavier than the old fallback, but it reads as an arrow, not an L (`dark-03c-workout-drop`, `large-03d-set-and-drop-editor`).

## 2. Blocking items

None.

## 3. Chip wrap: accept

`dark-03c-workout-drop` breaks "42.5 kg/DB" / "× 10" in the same place baseline does. The break falls before "×", so the reps stay together and the chip still reads in one glance. Don't narrow the space again to win the line back. If one line matters later, shorten the label ("42.5 kg × 10" on dumbbell rows, where "/DB" is already in the header) instead of changing the font.

## 4. Non-blocking follow-ups

- **Daylight check (my round-2 must-fix 3).** On the A71 at full brightness outdoors, look at the rest bar. If `wdth 80` at 700 reads light, raise only the countdown roles to 750–800 and re-check their width.
- **Non-Latin names.** The fonts are subset to Latin only, so a Cyrillic or Amharic exercise name falls back to the system font. Check it on the device.
- **Landscape.** In `land-03-workout` the rest sheet covers the entry tiles. Confirm it predates the font change; there's no baseline landscape render.
- **Tile labels at 200%.** The tiles in `large-05-history-detail` still read "Minute / Workin / Exercis". This predates the change; fix it in the layout.
- **Date ranges.** The en dash in "Mon 19 Oct – Sun 25 Oct" has tighter side spacing than in Roboto (`light-15-bodyweight`).

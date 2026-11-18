# After-check: product and brand

## Verdict: ship

The implementation matches what I asked for in round 2. At 100% the app still
reads as Rebuild (calm, warm, plain), but with its own voice. It no longer
leans on whatever the phone supplies.

- **Dark ledger (dark-10):** reads like a kept logbook. "42.5 kg/DB × 10" has
  air, and nothing drifts toward WHOOP's DIN look.
- **Receipt (light-09) and bodyweight (light-15):** quiet and grown-up.
- **Countdowns (light-23, dark-22):** sit exactly where today's do.
- **Text at 200% (large-03, large-05):** wraps line for line like baseline,
  which is why I picked Archivo over Fira.
- **The drawn `↳` (dark-03c):** matches the stem weight and amber, so the drop
  row no longer mixes in One UI's symbol font.

Nothing blocks.

## Chip wrap: accept

"42.5 kg/DB" / "× 10" in dark-03c breaks at the same point as baseline, keeps
the multiplier with its reps, and stays readable mid-set. A clean two-line chip
is better than a one-line chip with the number running into its unit. Don't
narrow the space again.

## Non-blocking follow-ups

1. **Stat tile labels (large-05).** They truncate to "Minute", "Workin" and
   "Exercis" in both baseline and impl1. This is a layout bug the font change
   doesn't cause. File it separately (let the label wrap, or use `FitText`).
   It shouldn't hold up this PR.
2. **Check on the Galaxy A71 before release:**
   - small labels on `#1D1B19` (hinting);
   - the countdown under gym glare;
   - an Amharic exercise name beside a `headlineMedium` title;
   - that the notification, picture-in-picture and dialogs no longer show
     Samsung's font style.
3. **The drop arrow's head is small** at 14 sp and could be read as an "L" at
   arm's length. Consider a slightly larger arrowhead in the drawn glyph.
4. **The optional 650 headline weight** wasn't tried. Leave it at 700 unless
   on-device titles feel loud.

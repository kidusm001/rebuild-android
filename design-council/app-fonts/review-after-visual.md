# App fonts, after-check: visual and typography

## Verdict: ship

The implementation matches the decision. Words and numerals now read as one family. The countdown, the rest-end "2 of 3" and the alarm bar "+0:01" (dark-22, light-23) sit exactly where today's do, so the Roboto ascent and descent did their job. Headlines at 700 look calm on the warm canvas: "Tuesday" and "Push" in light-01-today aren't blocky, so the skipped 650 weight is fine.

## Hand-drawn glyphs

- **↳** (dark-03c-workout-drop, "↳ Drop 32.5 × 7"). The drawn arrow now matches the stroke weight of the amber "Drop" and sits on the baseline at cap height. The system fallback it replaces was smaller, lighter and sat lower. It's slightly wider than the old one, but it reads clearly as "continues this set", so I'd keep it.
- **● ○** (light-15-bodyweight legend). Both sit centred on the x-height beside "Weigh-ins" and "Weekly average". The weight matches the label, and the size balances with the text better than the fallback did. No issue.

## Space around ×

The numerals space at 0.40 fixes what I flagged. "42.5 kg/DB × 9" (dark-03c) and "82.3 kg average" (light-15) now have a clear number–unit break without looking gappy.

One small note: Archivo's word-face × (as in "2 × 8–12", light-01, and "32.5 × 7") is small and light compared with Roboto's. It reads, so it isn't blocking.

## Chip wrap: accept

"42.5 kg/DB" / "× 10" wraps where today's fonts already wrap it. The break keeps "× 10" together, and the chip's height matches its neighbours' rhythm. Narrowing the space again just for this one chip would bring back the cramping everywhere else. If a one-line chip is wanted later, set "kg/DB" one step smaller as a unit suffix in the ledger chip. Don't touch the space.

## Non-blocking follow-ups

- The stat-tile labels still clip at 200% ("Minute", "Workin", "Exercis"; large-05-history-detail). This predates the font change and needs a layout fix (wrap or shrink the label).
- Check on the A71, in dark mode at arm's length: small labels at 11–12 sp ("RIR 0", "Last time"), and the word-face × in the Today list.
- When dark-mode glare is next reviewed, revisit the headline at 650.

# Review: UX and accessibility lens

## 1. Verdict: bundle numerals only

Bundle a subset numerals font for the `display*` roles and the ledger values. Keep all words on the phone's font. Numerals here work like a clock face: they need a fixed width (`tnum`), a known width at 200%, and the same shapes on every phone. Today all three depend on an alias no one has checked on the user's Samsung, where a user font style can replace system families. If that fails, the countdown jitters and the app gives no sign of it. Words are different: a chosen font style or Amharic fallback is the user's call, and bundling a text font would override it with no gain in reading. About 11 KB buys certainty where it matters.

## 2. Font pick

**First: B, Roboto Condensed (subset, about 11 KB).** In both themes it looks the same as today (100% width on every string). It keeps the 200% layouts that already pass, adds no new look to argue about, and closes the remap risk. Its weakness is narrow counters: the `0` and `8` are tall slots, and in dark mode halation closes them a little for astigmatic eyes.

**Runner-up: D, Barlow Semi Condensed Bold.** It is the best of the safe fonts at a distance: open apertures, round counters, a colon with room around it, and distinct `3/5/6/8/9`. `1:08` and `1:11` read cleanest in the dark specimen. The cost is 105–106% width on the countdowns. Its taller caps also sit lower, and in the specimen `1:11` nearly touches the ledger card. It is my pick only if it passes the 200% checks.

Rejected:
- **J/K/L** jitter. L's stick `1` also makes `1:11` look like a barcode.
- **M** overflows the `12,480` tile at 100%.
- **H and I** crowd the colon, so `1:08` blurs together at arm's length.
- **F** is 108% wide and heavier and more "tech" than calm.

## 3. Scores (0–4)

| | legib. | stab. | fit_200 | char. | cost |
|---|---|---|---|---|---|
| A today's alias | 3 | 2 (unverified) | 4 | 3 | 4 |
| **B Roboto Cond.** | 3 | 4 | 4 | 3 | 4 |
| C Roboto Flex 75 | 3 | 4 | 3 | 3 | 2 |
| **D Barlow Semi Cond.** | 4 | 4 | 2 | 4 | 4 |
| E Barlow Cond. | 3 | 4 | 4 | 3 | 4 |
| F Plex Cond. | 3 | 4 | 1 | 2 | 4 |
| H Fira Cond. | 2 | 4 | 3 | 2 | 3 |
| M Inter | 4 | 4 | 0 | 3 | 4 |

E has Barlow's shapes at today's width, but its narrower `0` and `8` give up most of what makes Barlow worth choosing.

## 4. Risks and checks

1. **Check the A71 first.** Log what `sans-serif-condensed` resolves to under Default and under one downloaded font style, then screen-record 15 s of the countdown. This tells us whether bundling is a fix or insurance.
2. **The A71 stops at Android 13.** The Android 14 non-linear scaling doesn't apply there. Find One UI's largest font size and test `1:08:42` and the stat-tile row at that size. Also test at 200% on the API 34 emulator.
3. **Vertical metrics.** Check display lines for clipping or collisions with the dock and ledger at 100%, 130% and 200%, keeping the current `lineHeight`.
4. **Subset coverage.** Ledger values mix in `kg`, `/` and `DB`. Either include those Latin glyphs or split the spans, because a mid-value fallback looks broken. Keep `tnum`/`lnum` in the subset.
5. **Contrast.** Check that light-mode amber "today" values reach at least 4.5:1. Check the dark-mode countdown at minimum brightness.
6. **TalkBack.** Confirm the countdown is still announced as a time.

## 5. What would change my mind

- **Toward system fonts:** the A71 renders Roboto Condensed with `tnum` under every font style, and someone re-checks after each One UI update.
- **Toward D first:** D passes the 200% and largest-One-UI checks without a layout break, and a bright-light test reading `1:08`, `6:38` and `8:36` at about 70 cm gives fewer misreads than B.
- **Toward bundling text:** only evidence that user word fonts break layouts. Even then, fix the layout rather than override the font.

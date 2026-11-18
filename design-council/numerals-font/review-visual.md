# Review: type as identity (visual critic)

## 1. Verdict: bundle the numerals font only

The countdown is Rebuild's largest element, yet today a phone maker or Samsung font style decides its face and whether `tnum` works. Taking that back costs ~10–30 KB. Words stay on the phone's font. On the A71 that font is One UI's, not Roboto, so bundling Roboto for words would make Rebuild look foreign and override a font style the user chose. Amharic falls back to system fonts anyway. Dependable means the numbers never move, not overriding the phone.

**Correction to option 2's scope:** `Numerals` styles whole strings, such as `42.5 kg/DB × 10`, `plates/stack`, the compact rest bar's `"$word $time"` and history lines. A digits-only subset would make Android set `kg/DB` in a fallback face in the middle of the string. Subset to Basic Latin plus `× – − · : . , / %`, and keep `tnum` and `kern`.

## 2. Font pick

- **First: B, Roboto Condensed Bold, bundled.** It is the same face as today, so nothing reflows, the 200% checks still pass, and it is Roboto's own narrow sibling, so it pairs cleanly with words. Yes, it is generic. But every candidate that escapes generic brings a borrowed voice:
  - Barlow (D/E): highway signage and sportswear.
  - Plex (F): IBM corporate, heavy enough to fill the tiles edge to edge.
  - Fira (H): warm, but its colon crowds `1:08` and `12,480`.
  - Archivo (I): editorial and athletic, and its spaces collapse in `107.5 kg × 6`.

  Rebuild's identity comes from the amber dock and the restraint. A calm product shouldn't announce its numerals.
- **Runner-up: G, Sofia Sans Condensed.** It is the only candidate that adds character without getting loud.
  - For: its footed `1` fills the tabular cell, so `1:11` and `18` hold an even rhythm, while B's footless `1` leaves holes (`1:1 1`). Its open spacing reads calmest on the sheet.
  - Against: its Bold looks a step lighter, which loses punch in a bright gym (try ExtraBold). The `g` in `kg` is quirky. `12,480` is at 103% width, so the 200% checks need redoing.
- C (Roboto Flex): a slightly blacker B, not worth the instancing step.

## 3. Scores (0–4)

| | legib. | stab. | fit_200 | char. | cost |
|---|---|---|---|---|---|
| A today | 3 | 2 (unverified on A71) | 4 | 3 | 4 |
| **B** | 3 | 4 | 4 | 3 | 4 |
| C | 4 | 4 | 3 | 3 | 2 |
| D | 3 | 4 | 2 | 2 | 4 |
| E | 3 | 4 | 4 | 2 | 4 |
| F | 4 | 4 | 2 | 2 | 4 |
| **G** | 3 | 4 | 3 | 4 | 4 |
| H | 3 | 4 | 3 | 3 | 3 |
| I | 3 | 4 | 3 | 2 | 4 |

Rejected:
- J, K and L: digits not tabular, so the countdown jitters, and they are poster or sports faces. The specimens also show J and K clipping the stacked countdown.
- M: overflows the stat tile at 100%.

## 4. Risks and checks

1. **One face per string:** screenshot the ledger, "Rest 2:50" in the compact bar, a history line and a `plates/stack` value. No letter may fall back to another face.
2. **`tnum` survives subsetting:** run pyftsubset with `--layout-features+=tnum,kern`. Frame-diff the countdown through 1:11 → 1:08 → 1:00. The colon's x position must not move.
3. **A71 before and after:** record what `sans-serif-condensed` resolves to today with the default font style and with a Samsung font style. After bundling, confirm the bundled face renders under both.
4. **Weight mapping:** declare the file as `Font(R.font.…, FontWeight.Bold)`. Check that the SemiBold `titleMedium` uses (`HistoryScreens`, `DurationPicker`) get no fake bold.
5. **Vertical metrics:** check the dock and the 64 sp countdown for clipping at 100% and 200%, in both themes.
6. **200% on One UI:** check the stat tiles and `1:08:42`.

## 5. What would change my mind

- **Keep system fonts** if the A71 resolves to Roboto Condensed with working `tnum` under every Samsung style, and no other phone matters. I'd still lean towards bundling as cheap insurance.
- **Swap to G (ExtraBold)** if an arm's-length gym test shows B's tabular `1` reading gappy, or G's footed `1` reading faster.
- **G, not a louder face,** if the owner wants the numerals to carry brand identity.
- **A bundled text face for words:** no. If One UI font styles break layouts at 200%, fix the layout.

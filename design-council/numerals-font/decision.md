# Font decision: synthesis

> **Superseded** by [../app-fonts/decision.md](../app-fonts/decision.md): the
> owner chose to bundle fonts for all text, and the council then picked Archivo.

Three Opus critics reviewed the same [brief](brief.md) and specimen packet
independently: [UX and accessibility](review-ux.md),
[visual and typography](review-visual.md) and
[Android platform](review-platform.md).

## Decision

**Bundle one font for numerals only: Roboto Condensed Bold (candidate B).
Words stay on the phone's font.**

All three critics reached this verdict independently and picked the same font.

## Why bundle the numerals

- The countdown, the ledger and the stat tiles depend on `tnum` (equal-width
  digits). Nobody has checked what `sans-serif-condensed` resolves to on the
  user's Galaxy A71 under One UI and Samsung font styles, or whether that font
  supports `tnum`. Bundling removes that unknown.
- Our JVM screenshot tests can't catch a problem here: Robolectric maps
  `sans-serif-condensed` to its own Roboto Condensed (platform critic, checked
  in the Robolectric jar).
- The cost is small: one static Bold `.ttf` in `res/font`, about 11–40 KB
  depending on the subset, works offline and on every API level from minSdk 26.

## Why Roboto Condensed (B)

- It is the face the app already uses, so it's 100% of today's width on every
  measured string. The 200% layouts that pass today stay valid, and nothing
  reflows.
- It supports `tnum`, has every needed glyph and pairs cleanly with Roboto or
  the phone's font for words.
- Every candidate with more personality carries a borrowed voice (Barlow reads
  as signage or sportswear, Plex as IBM) or costs width.

## Why not bundle a text font

It would override the font style the user chose on their phone, wouldn't match
the system fallback used for Amharic and other non-Latin names, and fixes no
measured problem. All three critics rejected it.

## Runners-up (no agreement)

| Critic | Runner-up | Why |
| --- | --- | --- |
| UX | D Barlow Semi Condensed | Most open shapes at arm's length, but 105–106% wide on countdowns |
| Visual | G Sofia Sans Condensed (ExtraBold) | Character without loudness; its footed 1 keeps "1:11" evenly spaced |
| Platform | H Fira Sans Condensed | 97–99% wide, clear digits; the UX critic thought its colon crowds the digits |

Revisit only if an arm's-length gym test shows B misread or gappy, or if the
numbers should carry more brand identity.

Ruled out: J Saira, K Oswald and L Big Shoulders (no equal-width digits, so the
countdown jitters); M Inter (overflows the stat tile at 100%); F Plex (108% wide).

## Requirements for the implementation

1. **The subset must cover Basic Latin + Latin-1**, plus `× – − · /` and the
   non-breaking space, not digits alone. The numerals style also draws words: the
   compact rest bar renders `"$word $time"` (`RestBar.kt:135`), and ledger values
   include `kg/DB`. Android swaps in another font for each missing glyph, so a
   digits-only subset would mix two faces inside one string. Keep `tnum`, `lnum` and
   `kern` when subsetting.
2. **Ship Bold only.** `DurationPicker` and other call sites already ask for
   other weights; check that they render as intended.
3. **Licence:** include the font's licence in a place release resource
   shrinking won't strip (`assets/` or `tools:keep`).
4. **Tests:** a Robolectric test that every time from `0:00` to `9:59` measures
   the same width with the bundled font, and that the font contains every
   character in the subset list.
5. **Device checks:** run the 200% checks on an API 33 emulator. The A71 stops
   at Android 13 (critic's belief, not verified), where large text scales
   linearly; the API 34 emulator scales it less and looks better than the phone
   will. If possible, screenshot the A71 under a non-default Samsung font style
   before and after the change.
6. Update DESIGN.md "Type" and the `Numerals` doc comment in `Theme.kt`.

## What would reverse the decision

If `adb shell cat /system/etc/fonts.xml` and screenshots from the A71 show that
`sans-serif-condensed` is Roboto Condensed with working `tnum` under every
Samsung font style, keeping system fonts is defensible. Even then, two critics
would still bundle as cheap insurance.

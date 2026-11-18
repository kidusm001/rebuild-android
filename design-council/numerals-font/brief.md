# Font decision: shared brief

## The question

Should Rebuild ship its own font(s) inside the APK, or keep using the fonts the
phone provides? If it ships fonts, for which text, and which font?

Possible answers include, but are not limited to:

1. **Keep system fonts** (today). Roboto for words, platform `sans-serif-condensed`
   Bold for numerals.
2. **Bundle a numerals font only** (subset to digits and the few symbols numbers
   use); words stay on the phone's font.
3. **Bundle everything**: a numerals font plus a text font for all words.
4. Something else you can justify.

## The product

Rebuild is a personal, offline, account-free Android workout notebook for a
returning lifter. It is used one-handed in a bright gym, then at home in a dim
room. Brand personality: calm, direct, dependable. No marketing dashboard, no
streaks, no decorative metrics. The design system is "Ink + Ember": warm tinted
canvas, signal amber `#FFB224` as the one action and rest colour, cards separated
by tone instead of lines. The user's own phone is a **Samsung Galaxy A71
(One UI)**. `minSdk` is 26.

## Today's type (from `app/src/main/java/com/rebuild/ui/theme/Theme.kt`)

- Words: Material 3 default `Typography` (Roboto on stock Android, or whatever
  the maker or user font maps to), with tighter tracking on headlines.
- Numerals: `FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed"), Bold))`
  for `displayLarge/Medium/Small` (rest countdown, entry tiles, stat tiles, chart
  readouts) and the ledger values. A code comment says it was chosen because
  "it ships with Android, so the app stays offline, and its narrow figures
  survive 200% text".
- Every text role sets `fontFeatureSettings = "tnum"`. DESIGN.md relies on this:
  "Native tabular numerals align read-only values, entry values and the rest
  countdown." If the font doesn't support `tnum`, the countdown jitters and the
  ledger columns go out of line.
- The app respects the user's font scale up to 200%. Above 130% several layouts
  switch to stacked blocks.

## Facts and uncertainties

- Phone makers can remap system font families, and Samsung lets users choose a
  system font style. **No one has checked** what `sans-serif-condensed` resolves to
  on the A71, or whether the result supports `tnum`. Treat this as a risk, not a
  proven fault.
- Android bundled fonts (`res/font`) work offline and on every API level the app
  supports. Downloadable Google Fonts need Play Services and a network on first
  use, so they don't fit an offline-first app.
- Licences: every candidate below is under the SIL Open Font License (Roboto
  Condensed is Apache 2.0 or OFL depending on the build). Bundling is allowed.
- Words written in other scripts (the user may use Amharic exercise names, for
  example) fall back to system fonts no matter what we bundle.

## Evidence packet (this folder)

- `metrics.json`: per candidate, whether the digits are equal-width once `tnum`
  is applied (`tabular_after_tnum`; **false means the countdown will jitter**:
  J Saira, K Oswald and L Big Shoulders are false), missing glyphs (`× – − : .`),
  full file size, size after subsetting to the numeral set, and
  `width_vs_today_pct`: the width of `2:50`, `1:08:42`, `107.5 kg × 12` and
  `12,480` as a percentage of today's font. Today's font already passes the
  200% text checks, so anything above ~100% needs re-checking at 200%. (On
  Android 14+, large sp sizes also scale less than 2× at a 200% setting.)
- `specimen.py`: the script that made all of this, if you want to check how.
- `specimens/<ID>-light.png` and `<ID>-dark.png`: each candidate in the app's
  real colours on a 360 dp column at 3 px/dp, showing the rest dock countdown,
  the full-size countdown (two frames, `1:08` and `1:11`, stacked so you can see
  whether digits stay put), the set ledger (last time in Roboto, today in the
  candidate) and three stat tiles. Words in the specimens are Roboto.
- `specimens/overview-light.png` and `overview-dark.png`: contact sheets for an overview
  only. Judge from the full-size files.
- `../../docs/images/workout.png`, `today.png`: older app screenshots for context
  (the palette has warmed since; the type is the same).

The specimens are drawn with Pillow and HarfBuzz (libraqm) at 3 px/dp. They
approximate Compose's rendering but are not device screenshots. Hinting,
line height and the exact layout of the real screens will differ.

## Your task

Assess independently. Do not read other critics' files. Open the full-size
specimen images, not only the overview. Then write your review to the path given
in your instructions with:

1. **Verdict**: system fonts, numerals only, or everything bundled. One
   paragraph explaining why.
2. **Font pick**: if you'd bundle, name your first choice and a runner-up from
   the candidates (or name another font and say why it's worth testing). If
   you'd bundle text fonts too, name those.
3. **Scores** 0–4 for each candidate you seriously considered, on: `legibility`
   (reading at a glance, mid-set, from arm's length), `stability` (equal-width
   digits, no jitter), `fit_200` (fits at 200%), `character` (matches "calm, direct,
   dependable", Ink + Ember), `cost` (size, licence, upkeep; 4 = cheapest).
4. **Risks and checks** to do on the emulator or the A71 before shipping.
5. **What would change your mind.**

Keep it under 700 words. Be blunt; disagreement is welcome.

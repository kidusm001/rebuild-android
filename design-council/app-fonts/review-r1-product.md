# Round 1 review: product and brand positioning

Lens: the lifter using Rebuild daily in the gym and at night. Does it feel like
their own notebook, not marketing, a Material template or a borrowed brand, and
will it in 2028? The research's open space is one warm or engineered family with
its own condensed numerals. That favours humanist or plain grotesques and
penalises geometric, rounded, fashionable or owned faces.

## Top 3

**1. Fira Sans + Fira Sans Condensed.** The only set that looks written for Rebuild.
Open, slightly handmade humanist shapes read as a field notebook, not SaaS or
Material. The condensed numerals are sturdy and humanist, so the dark ledger and
rest bar don't drift toward DIN or WHOOP (dark-10, dark-03). Not tied to a
trend, so it should age well. Small labels hold up in dark mode (dark-02), and
at 200% the tile reads "Minutes" where baseline cuts it (large-05). Weaknesses:
500/600 words look heavier than Roboto's ("Add workout note", light-03), and the
colon is narrow. The Mozilla link is faint.

**2. Archivo (wdth 100 / 75).** A plain grotesque, direct without being
sporty. Words match Roboto's width, so 200% holds (large-03, large-05). The
receipt (light-09) reads calm and grown-up. Risk: at wdth 75 the ledger closes
up ("42.5kg/DB×10", dark-10) and on near-black it edges toward a stat sheet, the
trap closest to WHOOP. Latin only.

**3. Sofia Sans + Sofia Sans Condensed (with tuning).** The most character, and the best
match for the research's unclaimed "warm grotesque". It suits Ember. As rendered
it is too small and light: the receipt (light-09) and today (light-01) look
faint, and the countdown (dark-03) has the least ink. Tuning can fix that.
Amharic fit is the worst (1.13).

Keep Roboto as the round-2 control: dependable, but generic Material.

## Scores (0–4)

| Set | fit | legibility | numerals | large | distinct |
| --- | --- | --- | --- | --- | --- |
| baseline | 2 | 3 | 3 | 3 | 1 |
| roboto | 2 | 3 | 3 | 3 | 1 |
| noto | 2 | 3 | 3 | 1 | 1 |
| robotoflex | 2 | 3 | 3 | 3 | 1 |
| archivo | 3 | 3 | 3 | 3 | 3 |
| fira | 4 | 4 | 3 | 3 | 3 |
| sofia | 3 | 2 | 3 | 3 | 4 |
| atkinson | 2 | 3 | 2 | 2 | 2 |
| asap | 2 | 3 | 2 | 3 | 2 |
| nunitosans | 2 | 2 | 2 | 2 | 2 |
| instrument | 2 | 2 | 3 | 2 | 2 |
| googlesansflex | 2 | 3 | 3 | 1 | 0 |
| monasans | 2 | 3 | 1 | 2 | 0 |

## Eliminate

- **googlesansflex**: reads as Pixel UI. Wide spacing wraps names (dark-02,
  large-03).
- **monasans**: GitHub's face. A slashed zero everywhere ("2ø Oct", countdown
  "2:5ø", dark-03) looks like a terminal.
- **atkinson**: two kinds of zero in one card ("Latest · 2ø Oct" above
  "42.5 × 10", dark-10). The legibility benefit misses the key numbers, and
  200% wraps more (large-03).
- **asap**: rounded and casual. Numerals jammed ("42.5kg/DB×10", dark-10,
  light-05), with a very narrow colon.
- **nunitosans**: small, soft, friendly-SaaS. Light numerals on the rest bar.
- **instrument**: fashionable, so it will date. Middots are cramped
  ("20 Oct·Baseline", light-01), and missing ± ½ ² µ brings back font mixing.
- **noto**: adds width, no identity. It reflows at 200% (large-03), and
  Amharic falls back to Noto Ethiopic anyway.
- **robotoflex**: same as roboto, with a 1.2 MB file.

## Round 2 tuning

- **Fira:** words one weight step lighter (keep 700 for headlines). Compare
  Condensed SemiBold with Bold for numerals. Add about 0.04 em around the
  countdown colon. Use the 97–99% width headroom for +1 sp on the countdown.
- **Archivo:** numerals at wdth 82–85, with +0.01 em tracking on the ledger and
  tiles. Put the dark ledger beside WHOOP to check it doesn't read as a stat
  sheet.
- **Sofia:** +1 sp on body and labels, words at 450/550, numerals at
  Condensed 800. Re-check 200%.

## Problems found

- large-05, every set including baseline: tile labels truncate ("Minute",
  "Workin", "Exercis"). This is a layout bug.
- dark-02 googlesansflex: the current exercise name wraps to two lines.
- large-03 noto, instrument, googlesansflex, monasans: the title runs to three
  lines. On noto, "Today 0 of 2" is clipped by the dock.
- dark-10 and light-09 atkinson and monasans: slashed zeros in word text.
- light-01 noto: the "Upper" row is pushed into the nav fade.
- `↳` falls back to the system font in every set (known). Draw it as a vector.

## Most important round-2 experiment

Put tuned Fira, Archivo and Sofia on the owner's Galaxy A71 and read them at
arm's length in the gym and in a dim room: the countdown, a ledger row and an
Amharic exercise name. Robolectric can't show hinting, glare or Ethiopic
fallback.

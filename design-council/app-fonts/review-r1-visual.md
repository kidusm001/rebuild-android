# App fonts, round 1: visual and typography review

Lens: type as identity and craft. Does the face give "calm, direct, dependable" and Ink + Ember a voice of its own without getting loud, sporty or decorative? Do words and numerals feel like one hand? Is the countdown a signature moment?

## Top 3

**1. Archivo.** This set has the best harmony between words and numerals. It's a plain, sturdy grotesque. "Tuesday" and "Push" (light-01-today) have weight without swagger, and body text sits evenly, a little more compact than Roboto. Its wdth 75 numerals are clearly the same design narrowed, not a second voice. The countdown (light-03-workout) is upright and square-shouldered, with a well-placed colon, and "55 / 9 / 5" in the stat tiles (light-05-history-detail) looks engraved rather than athletic. Pill labels ("Start Push", "Add workout note") fill the pills firmly. It also holds up best at 200% among the non-Roboto faces: "Incline dumbbell press" stays on one line (large-01-today). The risk is that its narrow cuts lean towards signage, so on the near-black canvas it should be checked against a DIN/WHOOP read.

**2. Instrument Sans.** This set has the most distinct headline character with the least noise. The "y", "a" and "s" of "Tuesday" have a quiet, contemporary warmth, and the "2:50" countdown in the rest bar is crisp and confident. It feels the most like a deliberate brand choice, not a default. On craft it's weaker than Archivo. The middot spacing is tight ("Baseline·5 days", "12 working sets·about", light-05, light-01). Spacing is tight at text sizes, and the face wraps sooner at 200%: the exercise title runs to 3 lines in large-03-workout and "press" falls onto its own line in large-01. Coverage gaps (± ½ µ, no Cyrillic) are a real fallback risk. Its fashionability may date.

**3. Fira Sans + Fira Sans Condensed.** This set is the most legible and humane. The open apertures and clear 1/l and 6/9 read well in body text and small labels in dark mode (dark-02-today-active), and its bold labels hold up in pills. The condensed numerals are honest and match the words' skeleton. Text blocks are a touch loose and wide, so at 200% "Incline dumbbell press" wraps and "Set 1 of 2 · prefilled from last time" takes 3 lines (large-03). It has a faint Mozilla association, but much less than the brand faces.

Honourable mention: **Asap** is warm and compact and holds 200% well, but the countdown colon is jammed in "2:50" and "×" crowds in "42.5kg/DB×10" (dark-10-exercise-history). The rounded terminals edge towards casual. It's worth one round-2 try at wdth 85.

## Scores (0–4)

| Set | fit | legibility | numerals | large | distinct |
| --- | --- | --- | --- | --- | --- |
| baseline | 2 | 3 | 3 | 3 | 1 |
| roboto | 2 | 3 | 3 | 3 | 1 |
| noto | 2 | 3 | 3 | 1 | 1 |
| robotoflex | 2 | 3 | 3 | 4 | 1 |
| archivo | 4 | 3 | 4 | 3 | 3 |
| fira | 3 | 4 | 3 | 2 | 3 |
| sofia | 2 | 2 | 2 | 3 | 4 |
| atkinson | 2 | 3 | 2 | 3 | 2 |
| asap | 3 | 3 | 2 | 3 | 3 |
| nunitosans | 2 | 2 | 2 | 3 | 2 |
| instrument | 4 | 3 | 3 | 2 | 4 |
| googlesansflex | 2 | 3 | 3 | 1 | 0 |
| monasans | 2 | 3 | 2 | 2 | 1 |

Sofia's and Nunito Sans's scores are as rendered. Both look underweight (see Problems).

## Eliminate

- **googlesansflex**: it reads as Pixel system UI. Wide tracking around × ("2 × 8–12", light-01) and round colon dots push the countdown towards Apple/Google. It's the worst at 200%.
- **monasans**: it carries the GitHub association, and the build here renders a single-storey "a" ("Save as routine", "Baseline", light-05) and slashed zeros in dates ("20 Oct"). That reads as a code tool, not a notebook.
- **atkinson**: the slashed zero turns up in dates and times ("6:10 PM", "20 Oct"). The wide words and the Fira numerals are two voices, and the countdown gets none of the hyperlegible traits.
- **noto**: bland, larger at the same sp, and the worst wrapping at 200% (large-01, large-03). Its "Rest over" sits higher than the others (dark-22).
- **nunitosans**: soft, light and generic. Emphasis barely registers, and the countdown has no authority.
- **robotoflex**: same voice as Roboto with more complexity. Keep plain **roboto** as the round-2 control instead.
- **sofia** (conditional): it has the most character, but as rendered the semibold roles look regular, the countdown is thin, the en dash runs long, and the cap height is low. Re-render only if the weights prove to be a harness error. Otherwise drop it.

## Round 2 tuning

- **Archivo:** numerals at wdth 75 vs 80, wght 700 vs 750. Headlines at 650 so they don't go blocky. Check `tnum` on the stat tiles. Put the dark rest bar beside a DIN/Barlow reference to rule out a WHOOP read.
- **Instrument Sans:** words at wdth 95–100 with +0.005–0.01 em tracking on body and label sizes. Add an explicit space around `·` (thin space or NBSP plus padding). The headline needs a 200% check: try headlineMedium at wdth 90. List every string that uses ± ½ µ and decide whether to substitute or avoid it.
- **Fira:** words at 400/500/600 with the 700 headline kept. Try Fira Sans Condensed for the 200% headline only. For the countdown colon, add about 0.04 em of tracking around ":" or use a spaced colon glyph.
- **Asap** (if kept): numerals at wdth 85 with colon spacing, and words at 500 minimum for labels.

## Problems found

- **Tile labels clip at 200% in every set, baseline included** ("Minute", "Workin", "Exercis"; large-05-history-detail). This predates the font change. It needs a layout fix (wrap, autosize or abbreviate) whatever face is chosen.
- **Sofia and Nunito Sans weights:** 600 and 700 roles render close to 400. The current-exercise row isn't emphasised (dark-02-today-active), and the countdown and "2" sweep are thin (light-03, dark-22). This is likely static-instance weight mapping. Verify it before judging those faces.
- **Sofia:** the en dash renders near em length ("8—12", "6:30 PM—7:25 PM"; light-01, light-05).
- **Asap:** the countdown colon is jammed (light-03 rest bar), and "×" is cramped in the ledger (dark-10, light-05).
- **Instrument:** the middot has almost no side space (light-01, light-05, dark-02).
- **Mona Sans:** single-storey "a" and slashed zero in body text. Check whether a stylistic set got baked into the instance.
- **Noto and Nunito Sans:** taller vertical metrics shift "Rest over" and the sweep digit upward (dark-22-rest-end-sweep-hold).
- **Large mode:** "press" falls onto its own line in noto, fira, instrument, googlesansflex and monasans (large-01-today).

## Most important round-2 experiment

The countdown is the signature moment. Render the rest bar, the rest-end sweep and the alarm bar, in light and dark, for Archivo, Instrument and Fira. Use numerals at wdth 75/80/85 × wght 700/800 and tune the colon spacing. Show each set next to its own words, and next to a DIN reference so a WHOOP resemblance can be ruled out.

# App fonts: curator's candidate pairings

The owner has decided Rebuild bundles its own fonts for **all** text, words and
numbers, so the app no longer mixes the phone's font with its own. The numerals
round ([../numerals-font/decision.md](../numerals-font/decision.md)) picked Roboto
Condensed Bold on the assumption that words stayed on the system font. That
assumption is gone, so the numerals choice is open again and has to pair with a
text face.

This file proposes 10 pairings for the council. Every font below is OFL, in
[github.com/google/fonts](https://github.com/google/fonts), and was checked on
18 Nov 2026: the directory listing and `METADATA.pb` were fetched, each file
URL returned HTTP 200, and each file was downloaded and measured (method at the
end).

## How to read the numbers

- **Numerals width** is the width of `2:50`, `1:08:42`, `107.5 kg × 12` and
  `12,480` with `tnum` on, as a % of today's platform Roboto Condensed Bold. It's
  the same measure as `width_vs_today_pct` in the numerals round. 100% means the
  200% font-scale layouts that pass today stay valid. Under 100% leaves room.
- **Words width** is the width of "Incline dumbbell press" and "Today's target: 2
  working sets · 8–12 reps · 2 RIR" at 400, as a % of Roboto Regular.
- **Tabular**: the digits are equal width with `tnum` on. Every listed numerals
  setting passes this. "Default" means the digits are already equal width
  without `tnum`.
- **Ethiopic fit** is how a typical Noto Sans Ethiopic syllable's height (0.739
  em) compares with the face's Latin cap height. Around 1.03–1.05 means Amharic
  names sit level with Latin capitals. At 1.10 or more, Amharic looks a size too
  big beside the Latin. Stroke style matters too: Noto Sans Ethiopic is a
  low-contrast humanist sans.

## Summary

| # | Pairing | Numerals width | Words width | Scripts | Ethiopic fit |
| --- | --- | --- | --- | --- | --- |
| 1 | **Roboto** wdth 100 / wdth 75 (safe, one family) | 100, 100, 100, 100 | 100, 100 | Latin, Cyrillic, Greek | 1.04 |
| 2 | **Noto Sans** wdth 100 / wdth 75 (one family) | 98, 98, 99, 98 | 107, 104 | Latin, Cyrillic, Greek | 1.03, same design family |
| 3 | **Roboto Flex** wdth 100 / wdth 75 (one family) | 101, 101, 102, 102 | 100, 99 | Latin, Cyrillic, Greek | 1.04 |
| 4 | **Archivo** wdth 100 / wdth 75 (one family) | 95, 95, 92, 96 | 99, 99 | Latin only | 1.08 |
| 5 | **Fira Sans** / **Fira Sans Condensed** | 97, 97, 99, 99 | 104, 100 | Latin, Cyrillic, Greek | 1.07 |
| 6 | **Sofia Sans** / **Sofia Sans Condensed** | 101, 100, 95, 103 | 96, 98 | Latin, Cyrillic, Greek | 1.13 |
| 7 | **Atkinson Hyperlegible Next** / **Fira Sans Condensed** (accessibility) | 97, 97, 99, 99 | 101, 101 | Latin only (words) | 1.11 |
| 8 | **Asap** wdth 100 / wdth 75 (one family) | 89, 88, 88, 91 | 100, 97 | Latin only | 1.06 |
| 9 | **Nunito Sans** wdth 100 / wdth 75 (one family) | 96, 95, 98, 98 | 101, 102 | Latin, Cyrillic | 1.05 |
| 10 | **Instrument Sans** wdth 100 / wdth 75 (one family) | 97, 97, 93, 99 | 103, 99 | Latin only, gaps | 1.03 |

Base URL for every file below: `https://raw.githubusercontent.com/google/fonts/main/ofl/`.
The licence for each family is `OFL.txt` in the same directory.

---

## 1. Roboto, one variable family (safe)

- **Files:** [ofl/roboto](https://github.com/google/fonts/tree/main/ofl/roboto),
  `https://raw.githubusercontent.com/google/fonts/main/ofl/roboto/Roboto%5Bwdth,wght%5D.ttf`
  (477 KB, about 276 KB once subset to Latin, Latin Extended-A, Greek and Cyrillic).
- **Settings:** words at `wdth 100`, `wght` 400/500/600/700. Numerals at
  `wdth 75 wght 700`.
- **Why it fits:** the new Google Fonts Roboto is a variable font whose `wdth 75`
  is Roboto Condensed. It measured exactly 100% of today's numerals on all four
  strings, and words measured exactly 100% of today's Roboto. On stock Android
  the app looks the same as it does now, and the A71's Samsung font style stops
  leaking in. One file covers both roles. It has a real `tnum` feature and its
  digits are tabular by default, with full Cyrillic and Greek.
- **Variant:** `ofl/robotocondensed/RobotoCondensed%5Bwght%5D.ttf` gives
  Roboto Condensed as its own file if two families are easier to wire than
  width settings. It's the numerals round's pick, again 100%.
- **Main risk:** no gain in character. Roboto is "Android default", so the app
  looks like any Material app, and on the owner's Samsung it now differs from
  every other app on the phone. That's the intended trade-off, but it should be
  a conscious one.

## 2. Noto Sans, one variable family (best next to Amharic)

- **Files:** [ofl/notosans](https://github.com/google/fonts/tree/main/ofl/notosans),
  `https://raw.githubusercontent.com/google/fonts/main/ofl/notosans/NotoSans%5Bwdth,wght%5D.ttf`
  (2.0 MB full, about 338 KB subset). The `wdth` axis runs from 62.5 to 100.
- **Settings:** words at `wdth 100`, `wght` 400/500/600/700, or `wdth 87.5` if
  the 200% screens get tight (that setting is 95–97% of Roboto). Numerals at
  `wdth 75 wght 700` (98–99%).
- **Why it fits:** it's the same design family as Noto Sans Ethiopic, the font
  Android falls back to for Amharic. Matching stroke, cap height (1.03) and
  rhythm make it the cleanest answer to "mixing fonts looks messy" for Amharic
  exercise names too. It's calm, open and humanist, with full Cyrillic and
  Greek and a real `tnum`. Digits are tabular by default.
- **Variant:** Open Sans (`ofl/opensans/OpenSans%5Bwdth,wght%5D.ttf`) is the
  design Noto Sans grew from. It measured 92–94% for numerals at `wdth 75` and
  103–105% for words. It's a smaller file, but it carries a stronger "generic
  website" association.
- **Main risk:** words are 104–107% the width of Roboto at `wdth 100`, so the
  200% checks must be re-run or the words set at `wdth 87.5`. The x-height is
  slightly larger, so it reads bigger at the same sp. The voice is neutral to
  the point of bland.

## 3. Roboto Flex, one family with optical size and grade

- **Files:** [ofl/robotoflex](https://github.com/google/fonts/tree/main/ofl/robotoflex),
  `https://raw.githubusercontent.com/google/fonts/main/ofl/robotoflex/RobotoFlex%5BGRAD,XOPQ,XTRA,YOPQ,YTAS,YTDE,YTFI,YTLC,YTUC,opsz,slnt,wdth,wght%5D.ttf`
  (1.7 MB full, about 1.2 MB subset with every axis kept; much smaller as
  static instances).
- **Settings:** words at `wdth 100`, `opsz` matched to the sp size, `wght`
  400/500/600/700. Numerals at `wdth 75 wght 700 opsz 36` (101–102%), or about
  `wdth 72` to land on 100%.
- **Why it fits:** it's Roboto's voice with real optical sizes, so the 64 sp
  countdown is drawn as display type rather than enlarged text. It also has a
  `GRAD` axis that can thin text on the dark canvas without changing its width,
  which helps the amber-on-near-black glare noted in Theme.kt. Its digits are
  tabular by default.
- **Main risk:** size and complexity. The file is large unless it's instanced,
  and it has no `tnum` feature (it relies on default tabular digits, which the
  measurement confirms). Many axes mean more to get wrong in Compose
  `FontVariation.Settings`, and visually it's still Roboto.

## 4. Archivo, one variable family (grotesque, most width headroom with Latin only)

- **Files:** [ofl/archivo](https://github.com/google/fonts/tree/main/ofl/archivo),
  `https://raw.githubusercontent.com/google/fonts/main/ofl/archivo/Archivo%5Bwdth,wght%5D.ttf`
  (643 KB, about 309 KB subset). `wdth` runs from 62 to 125.
- **Settings:** words at `wdth 100`, `wght` 400/500/600/700 (99%). Numerals at
  `wdth 75 wght 700` (92–96%), or `wdth 80` to use up the spare width.
- **Why it fits:** a sturdy, plain grotesque from Omnibus-Type with a wide width
  range in one file. It is direct without being sporty, it has headroom under
  today's numerals and it matches Roboto's word width, so the 200% layouts hold.
  The previous round's specimen I (Archivo at width 75) looked steady.
- **Main risk:** Latin only, with no Cyrillic or Greek. At the narrow widths it
  edges toward industrial or signage. Digits aren't tabular by default, so
  `tnum` must stay on in every style (Theme.kt already does this). Its Ethiopic
  fit is fair (1.08).

## 5. Fira Sans with Fira Sans Condensed (humanist, multi-script)

- **Files:** [ofl/firasans](https://github.com/google/fonts/tree/main/ofl/firasans)
  and [ofl/firasanscondensed](https://github.com/google/fonts/tree/main/ofl/firasanscondensed),
  all static:
  - `https://raw.githubusercontent.com/google/fonts/main/ofl/firasans/FiraSans-Regular.ttf`
  - `https://raw.githubusercontent.com/google/fonts/main/ofl/firasans/FiraSans-Medium.ttf`
  - `https://raw.githubusercontent.com/google/fonts/main/ofl/firasans/FiraSans-SemiBold.ttf`
  - `https://raw.githubusercontent.com/google/fonts/main/ofl/firasans/FiraSans-Bold.ttf`
  - `https://raw.githubusercontent.com/google/fonts/main/ofl/firasanscondensed/FiraSansCondensed-Bold.ttf`

  These are about 450–490 KB each before subsetting.
- **Settings:** words in Fira Sans 400/500/600/700. Numerals in Fira Sans
  Condensed Bold (97–99%).
- **Why it fits:** built for small screens (Firefox OS), with open apertures and
  clear 1/l/I and 6/9 at arm's length. It's warm without being soft, with full
  Cyrillic and Greek and a real `tnum`. It was the numerals round's platform
  critic's runner-up, and as a matching pair it now earns its place.
- **Main risk:** the colon is narrow (0.45 of a digit), which the UX critic felt
  crowds the countdown. There are five static files to subset and keep in step.
  It carries a faint Mozilla association, and its words run 100–104% of Roboto.

## 6. Sofia Sans with Sofia Sans Condensed (most character)

- **Files:** [ofl/sofiasans](https://github.com/google/fonts/tree/main/ofl/sofiasans)
  and [ofl/sofiasanscondensed](https://github.com/google/fonts/tree/main/ofl/sofiasanscondensed):
  - `https://raw.githubusercontent.com/google/fonts/main/ofl/sofiasans/SofiaSans%5Bwght%5D.ttf`
  - `https://raw.githubusercontent.com/google/fonts/main/ofl/sofiasanscondensed/SofiaSansCondensed%5Bwght%5D.ttf`

  Each is about 340 KB, or about 194 KB subset.
- **Settings:** words in Sofia Sans `wght` 400/500/600/700 (96–98%). Numerals in
  Sofia Sans Condensed `wght 700`, or 800 as the visual critic suggested
  (95–103%).
- **Why it fits:** the numerals round's visual critic picked its condensed cut
  as runner-up for "character without loudness". Its footed 1 keeps "1:11"
  evenly spaced. It's a real superfamily with full Cyrillic and Greek and a
  real `tnum`, and its words are slightly narrower than Roboto's.
- **Main risk:** the cap height is low (0.655 em), so it looks a size smaller
  than Roboto at the same sp and Amharic looks oversized beside it (1.13, the
  worst here). Sizes may need to go up by 1 sp, which uses up the width saving.
  `12,480` measured 103%. The stylised details (the curled `l`) may read as
  decorative to some.

## 7. Atkinson Hyperlegible Next with Fira Sans Condensed (accessibility)

- **Files:**
  - [ofl/atkinsonhyperlegiblenext](https://github.com/google/fonts/tree/main/ofl/atkinsonhyperlegiblenext),
    `https://raw.githubusercontent.com/google/fonts/main/ofl/atkinsonhyperlegiblenext/AtkinsonHyperlegibleNext%5Bwght%5D.ttf`
    (112 KB, about 91 KB subset; `wght` runs from 200 to 800)
  - plus `https://raw.githubusercontent.com/google/fonts/main/ofl/firasanscondensed/FiraSansCondensed-Bold.ttf`
- **Settings:** words in Atkinson Hyperlegible Next `wght` 400/500/600/700 (101%).
  Numerals in Fira Sans Condensed Bold (97–99%). A fallback for the numerals is
  Roboto `wdth 75 wght 700` from pairing 1 (100%).
- **Why it fits:** the Braille Institute designed it so that easily confused
  letters and digits look different (I/l/1, O/0 with a slashed zero, 6/b, 9/g).
  That suits reading mid-set in glare, and it backs PRODUCT.md's accessibility
  section. It's the smallest file here.
- **Main risk:** its own figures are 121–124% of today's width, so it can't set
  the numerals. The numerals come from a second, condensed face that lacks the
  slashed zero and other hyperlegible traits, so the app's most-read text gets
  the least of the accessibility benefit. Latin only. Its look is distinctive
  and wide-set. Amharic fit is weak (1.11), and the stroke contrast differs from
  Noto Ethiopic.

## 8. Asap, one variable family (warmest)

- **Files:** [ofl/asap](https://github.com/google/fonts/tree/main/ofl/asap),
  `https://raw.githubusercontent.com/google/fonts/main/ofl/asap/Asap%5Bwdth,wght%5D.ttf`
  (368 KB, about 218 KB subset). `wdth` runs from 75 to 125.
- **Settings:** words at `wdth 100`, `wght` 400/500/600/700 (97–100%). Numerals
  at `wdth 75 wght 700` (88–91%), or about `wdth 85` to use up the spare width.
- **Why it fits:** softly rounded terminals give the warmth of "Ink + Ember"
  without going cute. It reads calm and friendly. It has the most numerals
  headroom of any one-family option, and its digits are tabular by default.
- **Main risk:** Latin only. The colon is very narrow (0.38 of a digit), so
  `1:08` can look jammed and the countdown may need extra letter spacing around
  it. The rounded softness may read as casual rather than dependable, and the
  numbers look compressed at `wdth 75`.

## 9. Nunito Sans, one variable family (soft humanist)

- **Files:** [ofl/nunitosans](https://github.com/google/fonts/tree/main/ofl/nunitosans),
  `https://raw.githubusercontent.com/google/fonts/main/ofl/nunitosans/NunitoSans%5BYTLC,opsz,wdth,wght%5D.ttf`
  (558 KB). `wdth` runs from 75 to 125 and `opsz` from 6 to 12.
- **Settings:** words at `wdth 100 opsz 12`, `wght` 400/500/600/700 (101–102%).
  Numerals at `wdth 75 wght 700` (95–98%).
- **Why it fits:** gentle and readable, with a calm voice and Cyrillic. Digits
  are tabular by default (no `tnum` feature, but the measurement confirms equal
  widths). Amharic fit is good (1.05).
- **Main risk:** the x-height is low (0.484), so body text looks small at the
  same sp. `opsz` stops at 12, so there's no display optical size for the
  countdown. There's no Greek. Nunito is widely used in friendly SaaS
  interfaces, which edges toward generic.

## 10. Instrument Sans, one variable family (contemporary)

- **Files:** [ofl/instrumentsans](https://github.com/google/fonts/tree/main/ofl/instrumentsans),
  `https://raw.githubusercontent.com/google/fonts/main/ofl/instrumentsans/InstrumentSans%5Bwdth,wght%5D.ttf`
  (190 KB). `wght` runs from 400 to 700, exactly the needed range, and `wdth`
  from 75 to 100.
- **Settings:** words at `wdth 100`, `wght` 400/500/600/700 (99–103%). Numerals
  at `wdth 75 wght 700` (93–99%).
- **Why it fits:** a crisp, plain contemporary grotesque in a compact file. The
  condensed setting is clean and the cap height matches Ethiopic well (1.03).
- **Main risk:** coverage gaps: no `± ½ ¼ ¾ ² ³ µ ¤ ¦ ¬`, and no Cyrillic or
  Greek. A missing glyph falls back to another face inside a string, which is
  the mixing problem this change exists to fix. Spacing is tight at text sizes.
  It's fashionable on startup sites right now, so it may date.

---

## Considered and dropped

- **Mona Sans** (`ofl/monasans`, `wdth 75`: 89–93%): it is GitHub's brand
  typeface, so it fails the "clone of a big brand" rule. **Google Sans Flex**
  (`ofl/googlesansflex`) and **Radio Canada** (`ofl/radiocanada`) are dropped
  for the same reason.
- **Inclusive Sans** (`ofl/inclusivesans`): no `tnum` feature and digits not
  equal width, so the countdown would jitter. That fails a hard constraint.
- **Encode Sans** (`ofl/encodesans`): its narrowest width (`wdth 75`) is still
  109–113% of today's numerals.
- **IBM Plex Sans** (`ofl/ibmplexsans`): its condensed setting measured 110–112%,
  and it carries the IBM voice the numerals round already rejected.
- **Pathway Extreme** (`ofl/pathwayextreme`): 103–107% wide and missing `¤`.
- **Anek Latin** (`ofl/aneklatin`, `wdth 75`: 78–84%): plenty of room, but quirky
  and Latin only. It's worth testing only if the countdown should grow.
- **Barlow, Oswald, Saira, Big Shoulders:** ruled out in the numerals round
  (signage or sportswear voice, or digits not equal width).
- **Public Sans, Source Sans 3, Hanken Grotesk, Geist:** no condensed width, so
  each would need a second family for numerals and adds nothing over 5 or 7.

## Notes that apply to every candidate

- **`↳` (U+21B3) is missing from all of them**, Roboto included. It's used in
  `SetGroupsUi.kt` for drops and pauses, so it will always fall back to a
  system symbol font. Drawing it as a vector icon would make it consistent.
  `−` (U+2212), `×`, `–`, `·` and the non-breaking space are present in all ten.
- **Variable or static.** minSdk 26 supports Compose
  `Font(resId, weight, variationSettings = FontVariation.Settings(...))`. A
  simpler, smaller and more test-friendly route is to cut static instances with
  `fontTools.varLib.instancer` and then `pyftsubset`, one file per weight plus
  the numerals cut, keeping `kern`, `tnum`, `lnum`, `case` and `zero`.
  Robolectric's handling of variation settings is unverified.
- **Subset scope.** Latin, Latin-1 and Latin Extended-A at minimum. Keep
  Cyrillic and Greek where the family has them, because user-typed exercise
  names can contain them, and a missing glyph falls back to the system font,
  which is the mixing problem this change exists to fix.
- **Check before choosing:** run the 200% screens on the API 33 emulator for
  any pair whose words measure above 100% (2, 5, 9, 10). Look at Amharic
  exercise names beside each text face.

## Method

All files were downloaded from `raw.githubusercontent.com/google/fonts/main/ofl/…`
and measured with HarfBuzz (uharfbuzz) and fontTools at the stated axis values,
with `tnum` and `kern` on. Today's numerals baseline is the system
`RobotoCondensed-Bold.ttf`, and the words baseline is `Roboto-Regular.ttf`.
Coverage was read from each font's `cmap`. The Ethiopic height is the mean ink
height of መ ን ገ በ ሳ ር in Noto Sans Ethiopic. Subset sizes use the variable
font with Latin, Latin Extended-A, Greek, Cyrillic and general punctuation. A
quick contact sheet of all ten pairings was rendered for the curator's own
review and is not part of this packet. The council should make proper
specimens with the FontLab harness.

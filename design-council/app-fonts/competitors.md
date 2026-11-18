# Typefaces in workout apps and calm utility apps

Research for Rebuild's font choice (calm, direct, dependable; Ink + Ember; numbers read at a
glance mid-set). Public sources only: brand guidelines, first-party blogs, first-party
CSS and open-source code, Fonts In Use, and press articles. No app binaries were opened.
fitbod.me showed a bot check, which was not bypassed.

**Confidence key.** **V** = verified by a first-party source (brand PDF, the company's own
blog, open-source app code). **W** = first-party *website* CSS: real, but it shows the
marketing site, and the native app may differ. **S** = a credible secondary source.
**I** = inferred. **U** = unknown.

## Evidence table

| App | Text face | Numerals / display face | Source | Conf. |
|---|---|---|---|---|
| Strong | App: unknown. Site: Inter (+ Merriweather serif) | U | strong.app CSS | W / U |
| Hevy | Web app hevy.com: Figtree. Marketing site: Inter | No separate face seen | hevy.com, hevyapp.com CSS | W (mobile app I) |
| Fitbod | U | U | site behind bot check | U |
| JEFIT | Site: Inter + Barlow | Barlow Semi Condensed / Barlow Condensed | jefit.com CSS | W |
| Alpha Progression | Site: Rubik | U | alphaprogression.com CSS | W |
| Boostcamp | Site: Poppins + DM Sans | U | boostcamp.app CSS | W |
| StrongLifts | Site: system-ui stack | U | stronglifts.com HTML | W |
| Liftosaur | **Poppins** (Regular / SemiBold / Bold) for all app text, numbers included | Iosevka for its Liftoscript code editor (web) | github.com/astashov/liftosaur `src/components/primitives/text.tsx`, `src/fonts.css` | **V** |
| RP Hypertrophy | Store site: Figtree | Roboto Condensed (site) | rpstrength.com CSS | W (app U) |
| Gymshark Training | U | Wordmark is custom condensed lettering | madegooddesigns.com (fan write-up) | S / U |
| Nike Training Club | App U. Nike digital is reported as Helvetica Now | Nike Futura ND (condensed, uppercase) | third-party design-system extractions; Nike brand standards name Futura for large elements | S |
| Ladder | Site self-hosts SF Pro Display; app most likely uses the iOS system font | Same | joinladder.com HTML | W / I |
| Future | Site: Season Sans / Season Mix, SF Pro Text | Sharp Grotesk, Victor Serif (editorial) | future.co CSS | W (app U) |
| Apple Fitness / Workout | SF Pro (iOS), SF Compact (watchOS) | Rounded SF variants in watch complications; ring numbers reported as SF Pro Rounded | developer.apple.com HIG typography; blakecrosley.com | V (system) / S (numerals) |
| Garmin Connect | U (garmin.com uses a system stack) | U | garmin.com HTML; forum reports cover size only | U |
| Strava | Boathouse (custom, credited to Grilli Type) + Noto Sans on strava.com | Boathouse Condensed / Extended, Noto Sans ExtraCondensed | wtfont.app/site/strava.com; X post by @jtenclay (Nov 2024); sensatype.com | W / S. App UI: sources conflict (Inter vs Boathouse) |
| WHOOP | **Proxima Nova** (headlines Bold, all caps, +10% tracking; body Semibold) | **DINPro Bold** "for numbers" | developer.whoop.com, *WHOOP Brand & Design Guidelines* PDF | **V** |
| Oura | AkkuratLL | PP Editorial New (serif accents) | ouraring.com CSS; Creative Bloq says the app uses these too | W / S |
| Things 3 | San Francisco (system) | System | culturedcode.com CSS (ui-sans-serif); shadcn.io extraction | W / I |
| Linear | **Inter**, with **Inter Display** for headings since the 2024 redesign | Mono is reported as Berkeley Mono (unverified) | linear.app/now/how-we-redesigned-the-linear-ui | **V** / S |
| Notion Calendar (Cron) | "NotionInter", a custom Inter cut | Same | notion.com/product/calendar CSS | W (app I) |
| Arc | Site: Marlin Soft SQ / Marlin (custom) + Space Mono | U | arc.net CSS | W (app U) |
| Revolut | Inter (body) | Aeonik Pro (display) | revolut.com CSS; Fonts In Use #61484 ("web, app, billboards") | W / S |
| Monzo | Monzo Sans (custom cut of Universal Sans) | Oldschool Grotesk (hero) | monzo.com/blog/weve-had-a-little-makeover (Oct 2022; the post lists the app rollout as a next step) | V (brand) / I (app) |

## 1. Patterns

**Lifting apps keep type invisible.** On the evidence, no dedicated strength app ships a
custom typeface. They use the system font or a free Google font: Poppins (Liftosaur,
Boostcamp), Figtree (Hevy web, RP), Rubik (Alpha Progression), Inter (Strong's and Hevy's
sites), Barlow (JEFIT), SF (Ladder). Custom faces belong to brands with hardware or
advertising budgets: Strava, WHOOP, Nike, Monzo, Arc.

**Friendly geometric is the consumer-fitness default.** Poppins, Figtree, Rubik and DM Sans
are round, open and beginner-friendly. That mood is approachable, not dependable.

**When a brand treats numbers seriously, it splits the faces and goes industrial or
condensed.** WHOOP states the rule outright: Proxima Nova for words, DINPro Bold for numbers.
JEFIT carries the full Barlow width family, RP uses Roboto Condensed, and Strava ships
condensed and extended cuts of its own face plus Noto Sans ExtraCondensed. DIN and
condensed grotesques read as the "stat sheet" look. Apple goes the other way and uses
rounded numerals on the watch.

**Calm utility apps converge on a neo-grotesque and add one voice.** Linear uses Inter plus
Inter Display, Oura AkkuratLL plus an editorial serif, Revolut Inter plus Aeonik, Monzo a
functional sans plus a warm hero grotesque, and Things uses the system font with nothing
added. Calm comes from restraint: one quiet workhorse face, and personality spent in a
single place.

**Monospace numerals are rare.** Mono shows up only for code (Liftosaur's Iosevka, Linear's
mono). Aligned numbers come from tabular figures (`tnum`), not from a mono face.

## 2. Clone risks and open space

Choices that would make Rebuild look like a specific app:

- **DIN-style numerals with a grotesque on a near-black canvas → WHOOP.** Rebuild's dark
  theme (`#0F0E0D`) makes this the closest trap. Avoid DIN and Barlow-as-DIN for numbers.
- **Barlow Condensed numerals → JEFIT** and generic "sports stat" styling.
- **Poppins → Liftosaur / Boostcamp. Figtree → Hevy (web) / RP.** Both also pull the voice
  toward bubbly.
- **Rounded numerals inside progress rings → Apple Fitness.** Rebuild already uses rings.
- **An editorial serif accent → Oura.** **Heavy condensed italic caps → Nike or Gymshark**
  (and against Rebuild's "no marketing" anti-reference).
- **Inter everywhere** copies no single app, but it is the default of the whole calm-SaaS
  set (Linear, Notion, Revolut body, Strong's site), so it reads as anonymous.

Open space:

- **One family for both words and numbers, with the numbers set narrower and heavier along a
  width axis.** No lifting app on this list does this. It gives the at-a-glance condensed
  numerals without bringing in a second "sport" voice. It also replaces today's mismatched
  pairing of Roboto with the device's `sans-serif-condensed`, whose shapes depend on the OEM.
- **A warm or engineered grotesque** (Plex-like or Sofia-like) is unclaimed in the
  category. It sits between geometric-friendly and DIN-athletic, which fits "notebook, not
  coach".

## 3. Open-licence stand-ins for relevant proprietary faces

Every family below is from Google Fonts (OFL) and can be bundled in the APK as `res/font`.

| Proprietary | Seen at | Closest open alternatives |
|---|---|---|
| DINPro | WHOOP numbers | Barlow (+ Semi Condensed, Condensed). D-DIN is often listed as OFL; check its licence first |
| Proxima Nova | WHOOP words | Montserrat, Figtree |
| AkkuratLL | Oura | Inter, Instrument Sans, Hanken Grotesk |
| Aeonik Pro | Revolut | Figtree, Geist |
| SF Pro / SF Compact | Apple, Ladder, Things | Inter (closest in metrics and mood) |
| SF Pro Rounded | Apple Fitness | Google Sans Flex with the `ROND` axis, Nunito |
| Helvetica Now | Nike | Inter, Public Sans |
| Nike Futura ND (condensed) | Nike | Oswald, Bebas Neue (for reference only; avoid) |
| Maison Neue (Strava's former face) | Strava | Inter, Hanken Grotesk |
| Boathouse | Strava | No good match; not needed |

## 4. Shortlist to test for Rebuild

Features and axes were checked in each font file in github.com/google/fonts. "Tabular by
default" means all ten digits share one advance width in the shipped font.

1. **Roboto Flex** (digits tabular by default, `pnum` available; `wdth` 25–151, `opsz`,
   `GRAD`). The lowest-risk change: the words stay Roboto-like and the numbers become a
   true condensed bold of the same design, not whatever the device maps to
   `sans-serif-condensed`. Least distinctive.
2. **IBM Plex Sans** (digits tabular by default, slashed `zero`; `wdth` 75–100,
   `wght` 100–700). An engineered "notebook" voice nobody in fitness uses; condensed
   numerals come from the same file. It has no `pnum`, so figures in running text stay
   tabular.
3. **Sofia Sans + Sofia Sans Condensed** (`tnum` in both; `wght` 1–1000). The warmest
   option: humanist detailing that suits Ember, with condensed numerals in a sibling family
   that shares the same skeleton.
4. **Mona Sans** (`tnum`, `case`; `wdth` 75–125, `wght` 200–900). A sturdy grotesque with a
   real condensed range for big countdown and set values. A slight GitHub association.
5. **Instrument Sans** (`tnum`, `case`; `wdth` 75–100, `wght` 400–700). A calm, slightly
   warm grotesque. The narrower weight range limits how bold the numbers can get.
6. **Archivo** (`wdth` 62–125, `wght` 100–900 per Google Fonts metadata; `tnum` not
   checked). The deepest condensed range on the list for very large numerals. It can tip
   into "sporty", so test it at Rebuild's weights.
7. **Google Sans Flex** (OFL since late 2025; `tnum`, slashed `zero`; `wdth` 25–151,
   `opsz`, `ROND`). The most Android-native option, with keep-it-square control through
   `ROND` = 0. Risk: it reads as Pixel system UI, and rounding drifts toward Apple Fitness.
8. **Atkinson Hyperlegible Next** (`tnum`, `case`; `wght` 200–800, no width axis). A
   legibility control: it separates 1/7, 3/8 and 6/9 under glare. Numbers would need size
   rather than width to stand out.

**Numeral approach for all of them:** set every numeric role to `tnum` (Kotlin
`fontFeatureSettings = "tnum"`; Roboto Flex and Plex are already tabular). Take the
at-a-glance numerals from the same family's width axis (about `wdth` 75–85, weight 650–750),
not from a second typeface. Inter (with `tnum`, `zero`, `cv01`) is worth one specimen as the
category-default control, but it has no width axis.

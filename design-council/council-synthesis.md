# Rebuild design council: findings and chosen direction

## What was reviewed

Three independent reviewers used the same six-app evidence bundle and the same
Rebuild 0.1.3 native baseline:

- GPT-6 Astra, high effort.
- Claude Opus 5.5, high effort.
- Claude Fable 5.1, high effort.

Each inspected the eighteen first-party reference images and Rebuild's paired
dark/light screens. The reference set contains Hevy, Strong, Fitbod, Boostcamp,
Alpha Progression and Liftin'. No member saw another member's initial assessment.
The complete reviews and machine-readable ratings are retained alongside this
document.

The six criteria, each scored 0-4, are hierarchy, legibility, action clarity,
orientation, theming and fit for Rebuild. Totals describe subjective assessments
of the visible evidence, not measured usability, overall product quality or
scientific rankings.

## Independent ratings

| Design | Astra / 24 | Opus / 24 | Fable / 24 | Mean / 24 |
| --- | ---: | ---: | ---: | ---: |
| Hevy | 18 | 18 | 18 | 18.0 |
| Strong | 19 | 19 | 20 | 19.3 |
| Fitbod | 16 | 15 | 16 | 15.7 |
| Boostcamp | 16 | 16 | 18 | 16.7 |
| Alpha Progression | 17 | 16 | 17 | 16.7 |
| Liftin' | 16 | 15 | 15 | 15.3 |
| Rebuild 0.1.3 baseline | 19 | 17 | 17 | 17.7 |

All six visual criteria were rated in each row. State coverage was nevertheless
limited: the competitor material is publisher artwork, not an independently
tested installation. Only Hevy and Strong have light-theme examples in this
sample. Liftin' is an iOS style reference. Several Android-facing store images
depict iOS-style chrome. Dates and depicted versions are unknown.

The mean is included for transparency, not used as a mandate to clone the
highest-scoring app. A one- or two-point difference is not a statistically
meaningful gap.

## Agreement that matters

### 1. Preserve Rebuild's purpose and dark identity

All three reviewers prefer retaining the warm charcoal/amber dark palette,
native typography, rounded-outline icons, explicit logging and focused exercise
flow. None recommends a social feed, coaching dashboard, estimated strength
score, exercise-photo hero, subscription or gamification.

The existing dark surface `#171410` and primary `#E6C27A` are an anchor, not a
temporary palette to replace with a competitor's blue or pink.

### 2. Put previous performance beside the current task

Hevy, Strong and Boostcamp show aligned previous/current data. Alpha Progression
shows a compact dated prior-session block. Every reviewer identified this as more
scannable than Rebuild's long "Last comparable" sentence.

Chosen interpretation: retain the current large entry controls and single Log
action, but render previous working sets in a compact, dated, labeled layout.
Preserve actual kg, reps and optional RIR. Do not adopt tiny spreadsheet inputs
or change comparison/prefill logic.

### 3. Give actions a clear priority

Strong demonstrates distinguishable current input and completed rows. Fitbod
provides a useful example of emphasizing the current row, although its media
hero and derived scores do not fit Rebuild.

Rebuild should keep one obvious primary action, clear completed-set indicators,
and quieter secondary actions. Color is supplementary to labels, structure and
completion state.

### 4. Keep progress metrics honest

The references explicitly depict different metrics: actual heaviest weight,
estimated 1RM, 10RM, volume, bodyweight and strength scores. These are not
interchangeable. Rebuild will continue to show actual logged weight/reps rather
than borrow a more impressive-looking number with different meaning.

## Disagreements and decisions

### Light primary button

**Astra and Opus** recommend near-neutral light surfaces with an ink/charcoal
primary action and restrained amber selection details. **Fable** prefers an
amber-container primary button with dark text to preserve the same visual
character across themes.

**Decision:** use the charcoal-action direction as the first implementation,
with nearly neutral, faintly warm light surfaces. This addresses the user's
specific dislike of the current beige/brown treatment while keeping warmth in
selected and focus details. Dark tokens remain unchanged. Fable's tonal-amber
proposal is a legitimate alternative, not a rejected accessibility failure.

Final token values must be checked in native light/dark, selected, error,
disabled and focus states. The existing light palette is not being replaced
because it fails ordinary text contrast; this is a hierarchy and preference
decision.

### Rest panel and 200% text

Opus described the primary button as covered in a particular 200% capture;
Astra called it viewport pressure rather than proof of an unreachable button;
Fable considered the reflow adequate. The baseline packet had no keyboard-open
interaction and included particular scroll positions.

**Decision:** this is not a three-member agreement that a bug exists. Re-run
actual geometry and touch/scroll checks on the updated app. Fix any demonstrated
obstruction, but do not add padding blindly or regress the previously verified
IME/landscape layout.

### Remaining/total timer

All reviewers see value in understandable timer context, with Strong/Liftin'
providing visual examples. However, an accurate original/current total is not
guaranteed by a deadline-only timer model after extensions and adjustments.

**Decision:** retain the correct countdown and existing add/skip/options
controls. Do not invent a denominator or change the persistence schema merely
to imitate a promotional timer.

### Dense versus spacious set tables

The references trade density against larger input areas. The council agrees on
aligned read-only comparisons but not on making Rebuild a full-workout
spreadsheet.

**Decision:** improve data alignment and grouping while retaining large native
entry fields, one focused exercise, 48 dp interactive targets and scalable text.
At narrow widths or larger fonts, reflow read-only rows instead of shrinking
labels or forcing horizontal scrolling.

## Bounded implementation brief

1. **Retune light semantic colors only.** Nearly neutral light surfaces, ink
   primary actions, subtle warm selected surfaces. Preserve the existing dark
   palette and system-aware theme behavior.
2. **Replace the prior-performance paragraph with structured set data.** A dated
   previous-session heading and aligned set/kg/reps/RIR values near entry,
   with the relevant working-set ordinal clear where useful. Show absent data
   truthfully; retain an accessible route to all prior results.
3. **Apply primary/secondary action hierarchy consistently.** Log, scheduled
   Start or Next at target completion stays prominent. Secondary details,
   notes and corrections remain recognizable and accessible without making
   every link an amber focal point.
4. **Refine spacing and numeric treatment.** Native system typography, tabular
   figures where supported, readable completed-set rows and modest existing
   surface grouping. No tiny input cells, nested cards or decorative media.
5. **Verify, rather than assume, repeated-use efficiency.** Preserve one-tap
   prefilled logging and all previous state, timer, backup, migration and
   accessibility safeguards. Compare paired native before/after states at
   normal/large text, light/dark and keyboard/landscape combinations.

No workout/PDF changes are needed. The prescribed lat pulldown, set targets,
manual progression and existing records remain unchanged.

## Independent check of the implemented candidate

The same three models inspected matched native before/after captures without
reading each other's follow-up opinions:

| Reviewer | Rebuild baseline / 24 | Reviewed candidate / 24 | Verdict |
| --- | ---: | ---: | --- |
| Astra | 19 | 21 | Accept with nits; no must-fix |
| Opus 5.5 | 17 | 19 | Accept with nits; no must-fix |
| Fable 5.1 | 17 | 19 | Improved, with one required action-affordance correction |

All three identified the aligned previous-set presentation and cleaner light
theme as improvements. These numbers describe the frozen visual candidate, not
full release qualification or a new market comparison.

Fable identified that rest-day Start actions looked like static text after the
accent was reduced. Opus separately noted a related issue with neutral History
actions. The coordinator selected a small correction: outlined alternative
Start buttons and existing leading outline icons for phase/history/note
actions. This restores visible affordances without making every link amber.

Fable then checked only that correction and reported **Resolved**, with no
further change required for its flag. Its preference for lighter-looking
outlined pills was recorded as a nonblocking taste judgment. The other
reviewers were not asked to rerate the full app, and no hypothetical score
increase is invented for the patch.

Original after-checks are in `after-astra.md`, `after-opus.md` and
`after-fable.md`; the resolution is in `after-fable-affordance.md`. Exact
reviewed inputs are preserved under `reviewed-candidate/` and the separate
`final-affordance/` packet. The latter distinguishes real rest-day captures
from a controlled test-clock scheduled-day example.

### Visual judgment versus native verification

Identical geometry checks on both the old baseline and initial candidate
passed in the selected seven IME/size/font scenarios. This did not reproduce
the claimed obstruction from the original fixed-position screenshot.

A later full run nevertheless exposed an intermittent, persistent large-text
keyboard relocation defect. That is separate execution evidence, not proof
that either initial screenshot interpretation was universally correct. The
implementation owner reproduced it and changed the existing relocation
trigger to use the measured viewport height, without shrinking text or
relaxing geometry assertions. Final test and APK evidence must remain
separate from the council's visual verdicts.

## Primary reference sources

| Reference | First-party visual source | Useful observation |
| --- | --- | --- |
| Hevy | [Google Play](https://play.google.com/store/apps/details?id=com.hevy&hl=en_US) | Previous/current columns and routine action hierarchy |
| Strong | [Google Play](https://play.google.com/store/apps/details?id=io.strongapp.strong&hl=en_US) | Theme role consistency, aligned fields and state differentiation |
| Fitbod | [Google Play](https://play.google.com/store/apps/details?id=com.fitbod.fitbod&hl=en_US) | Current-row emphasis and compact exercise summaries |
| Boostcamp | [Google Play](https://play.google.com/store/apps/details?id=com.bpmhealth.boostcamp&hl=en_US) | Separating previous, target and actual values; density tradeoff |
| Alpha Progression | [Google Play](https://play.google.com/store/apps/details?id=com.alphaprogression.alphaprogression&hl=en_US) | Dated prior-session mini-table and day orientation |
| Liftin' | [App Store](https://apps.apple.com/us/app/liftin-gym-workout-tracker/id1445041669) | Concise exercise summaries and explicit units; iOS-only caveat |

Detailed image URLs, gallery positions, dimensions, hashes and limitations are
in `references/reference-dossier.md` and `references/references.json`. Competitor
media remain private critique evidence and must not ship as Rebuild assets.

## Verification limits

This council is not a substitute for physical Galaxy A71 testing, TalkBack
speech or a human usability study. Published screenshots cannot establish
offline support, reliable timers, correct data handling, tap counts or dynamic
font behavior in competing products. No universal "best" claim is made.

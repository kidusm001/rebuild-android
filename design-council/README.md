# Rebuild workout-app design study

## Start here

- [Delivered 0.1.4 update and verification outcome](implementation-result.md)
- [Council synthesis and chosen direction](council-synthesis.md)
- [Shared rating brief](council-brief.md)
- [First-party reference dossier](references/reference-dossier.md)
- [Reference overview](references/overview-contact-sheet.jpg)
- [Rebuild 0.1.3 paired native baseline](baseline/contact-sheet.png)
- [Additional history and large-text baseline](baseline/supplemental-contact-sheet.png)

The council consists of GPT-6 Astra, Claude Opus 5.5 and Claude Fable 5.1,
each at high effort. They independently evaluated the same six selected
workout-app reference sets and Rebuild's paired native light/dark baseline.

## Original independent reviews

| Reviewer | Full assessment | Structured scores |
| --- | --- | --- |
| GPT-6 Astra | [Review](review-astra.md) | [JSON](rating-astra.json) |
| Claude Opus 5.5 | [Review](review-opus.md) | [JSON](rating-opus.json) |
| Claude Fable 5.1 | [Review](review-fable.md) | [JSON](rating-fable.json) |

Each model's initial assessment is preserved, including disagreements. Scores
are subjective visual judgments, not clinical claims, accessibility
certification, usability-study results or a definitive market ranking.

## Independent implementation checks

The same three members then inspected the implemented 0.1.4 visual candidate:

- [Astra after-check](after-astra.md)
- [Opus after-check](after-opus.md)
- [Fable after-check](after-fable.md)
- [Exact reviewed before/after workout comparison](reviewed-candidate/comparison-workout-rest.png)
- [Exact reviewed before/after Today comparison](reviewed-candidate/comparison-today.png)

These assessments refer to the frozen `reviewed-candidate/` packet. Fable
flagged one remaining secondary-action affordance problem, with a related
observation from Opus. The small correction is preserved separately:

- [Fable's confirmation that the flag is resolved](after-fable-affordance.md)
- [Final light rest-day action treatment](final-affordance/final-light-today.png)
- [Final dark History action treatment](final-affordance/final-dark-history-detail.png)
- [Final packet scope and test-clock distinctions](final-affordance/README.md)

The final APK and its tests are a separate release artifact. The original
candidate was not silently replaced under already-written reviews.

## What this study does and does not support

The strongest common recommendations are to preserve Rebuild's dark identity,
make previous set data easier to compare with current entry, use accent colors
more selectively, and improve the light theme without cloning another brand.

The competitor material consists of public publisher/store promotional images.
It is not evidence that an app handles interruptions, screen readers, large
fonts, keyboards, offline use or backups correctly. Only Rebuild's own native
execution can establish those properties for the delivered app.

The examples are kept as attributed, private critique evidence. Do not use their
logos, illustrations or screenshots in Rebuild or redistribute them as a
replacement for the original publisher material. Links, provenance, image hashes
and platform/version uncertainty are recorded in the dossier.

The implementation and its final verification are now complete, with the result
recorded separately. Initial council scores must not be presented as scores for
a later APK.

The `baseline/` directory is a byte-for-byte archive of the packet supplied to
the council. Its original provenance manifest also records the capture location
in the implementation session. The local image copies remain available if that
session is not open.

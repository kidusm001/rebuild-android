# After-check — claude-fable-5.1 (effort high) — Rebuild 0.1.4 candidate (code 5)

Scope: bounded before/after check against my earlier recommendations using the candidate packet (manifest, color-tokens.json, after-*/comparison-* PNGs). No build, device, TalkBack or other reviewer output. Fixture identical except weekday (Monday rest-day launcher) and timer seconds.

## Verdict: Accept with nits — one small must-fix

### Achieved
- **Previous-set provenance and readability** — `after-dark-workout-rest.png`, `after-light-workout-rest.png`: "Previous working sets · Sep 26, 2026" with aligned Set / kg / Reps / RIR rows replaces the wrapping sentence; columns match the entry fields' vocabulary. At 200 % (`after-dark-entry-200.png`, `after-light-entry-200-scrolled.png`) rows reflow into labelled groups ("Set 1 · kg 42.5 Reps 10 RIR 2") without truncation.
- **One primary action** — Log set is the only filled control in both themes; Next / Target details / Alerts links are now neutral (`comparison-workout-rest.png`).
- **Light quality** — `after-light-today.png`, `after-light-workout-rest.png`, `after-light-history.png`: #FAF9F7 near-neutral surface, ink primary with cream label, cream selected chip and rest bar. The beige/olive muddiness is gone; the theme now reads as the same product as dark. Ink-button rather than my suggested tonal-amber button is a legitimate alternative — taste, not blocking.
- **Dark preserved** — visually unchanged palette in all after-dark captures; manifest states 29 explicit tokens unchanged.
- **No occlusion/misalignment seen** — scrolled 200 % captures show whole fields and Log set above the rest bar; owner's seven IME geometry cases are noted, not re-verified by me.

### Must-fix (1)
1. **Rest-day Today has no visible action affordance** — `after-dark-today.png`, `after-light-today.png`: with no scheduled workout, the only entry points are the four "Start" text buttons and "Change phase", now rendered in body-text colour. They read as labels. Restore a visible affordance without reintroducing amber everywhere: e.g. secondary-colour text (#6B4E0E light / secondary dark), an outlined "Start" chip, or whole-row tap with chevron. Style-only; logging/plan behaviour untouched.

### Non-blocking nits
- Date label "Sep 26, 2026" floats mid-row beside the "Previous working sets" heading; right-align or place inline after the heading (`after-dark-workout-rest.png`, more visible at 200 %).
- Finish is amber in dark but ink in light — small role-parity gap; acceptable.
- History-detail links (Add missed set / Exercise history / Add exercise note) also went neutral (`after-light-history-detail.png`); pencils preserve editability, so low risk.
- Surfaces remain flat (no card grouping); deferred, fine for 0.1.4.

### RB candidate scores (before → after)
| hierarchy | legibility | action_clarity | orientation | theming | rebuild_fit | total |
| 3→3 | 3→4 | 3→3 | 3→3 | 2→3 | 3→3 | 17→19 |

Hierarchy and action clarity hold rather than rise because the workout gains are offset by the Today rest-day affordance loss; fixing it would move action_clarity to 4 in my judgment. Judgments are visual only; no interaction, contrast measurement or speech verified.

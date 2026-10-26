# Rebuild design council: shared brief

## User intent

Research real workout-app designs, independently rate the same reference set
with different models, identify useful practices, and apply a coherent selection
to Rebuild. The user has authorized autonomous decisions and does not want an
interview. The user likes Rebuild's existing dark color scheme more than its
light scheme.

## Product constraints

Rebuild is a personal, native Android workout companion for a returning lifter,
used one-handed on a Galaxy A71 in the gym. It works offline without an account.
The central task is logging actual weight, repetitions and optional reps in
reserve immediately after each set, then resting or moving to another exercise.

Keep:

- The prescribed routine, manual phase choice, baseline/current/optional upper
  set distinction, and current lat-pulldown replacement.
- One-tap completion of an already-prefilled set, with no automatic logging.
- Persistent workout recovery, accurate history, editable actual logs and
  explicit today-only versus future-plan changes.
- A visible, usable rest timer with keyboard and landscape handling.
- Local backup and explicit safety checks before replacement.
- System-aware light/dark support, scalable text, visible labels and accessible
  touch targets. Do not force dark mode or remove light mode.
- Existing rounded-outline icon consistency.

Do not add social feeds, calorie tracking, automatic coaching, subscriptions,
accounts, cloud synchronization, gamified streaks, or a generic fitness dashboard.
Do not copy another product's brand, illustrations, screenshots or proprietary
layout wholesale into the app.

## Independence

Each council member receives the same frozen reference dossier and Rebuild
baseline packet. Do not read another member's files, ratings or messages before
submitting your initial assessment. Do not search for scores/reviews to anchor
your judgment. The researcher collected evidence, not ratings.

Use installed Impeccable product-design, typography, color/contrast,
cognitive-load and interaction guidance as heuristics, not universal laws. Apply
native Android principles, not HTML-specific rules or a requirement to adopt
every generic skill example.

## Evidence discipline

- Open the actual supplied images at readable size. A source description or
  tiny contact sheet alone is insufficient to rate a screen.
- Cite the reference ID, image filename and source URL for observations.
- Distinguish an observed screenshot affordance, a first-party behavior claim,
  a design inference and a genuinely exercised interaction.
- Screenshots cannot establish real tap counts, performance, accessibility
  semantics, offline support, persistence, keyboard behavior or dynamic text.
- A promotional composite may omit intermediate states. Note whether each
  reference is Android, iOS, cross-platform or unknown, and which theme appears.
- Mark unavailable evidence as `null` / "not evidenced", never zero quality.
- Do not claim an exhaustive market survey or universal best design. The
  reference set is intentionally bounded and purpose-specific.

## Shared rating rubric

Score the visible evidence from 0 to 4 on each criterion:

| Key | Criterion | What is being judged |
| --- | --- | --- |
| `hierarchy` | Task hierarchy | Is the next useful action clear? Does secondary material compete with logging? |
| `legibility` | Typography and data readability | Can exercise names, set values and prior results be read and compared? |
| `action_clarity` | Visible input and action clarity | Are fields, completion, editing and primary/secondary affordances understandable? |
| `orientation` | Navigation and status clarity | Is the current screen/workout/exercise and visible progress understandable? |
| `theming` | Visual cohesion and surfaces | Do color roles, spacing, grouping and component treatments form a coherent interface? |
| `rebuild_fit` | Transferability to Rebuild | Are the demonstrated patterns useful to this personal, offline, one-handed logger? |

Scale: 0 = seriously obstructive; 1 = weak; 2 = adequate with notable tradeoffs;
3 = strong; 4 = exceptional in the available evidence. Scores are subjective
expert judgments, not user-study results or accessibility certification.

Total is out of 24 only when all six dimensions are evidenced. Otherwise report
the sum and possible total for the evidenced dimensions, plus coverage; do not
silently convert incomplete evidence into a comparable full score.

Rate every selected reference and Rebuild's baseline. For Rebuild, separately
explain the dark-versus-light treatment even if the six-axis row covers the
shared interface. Honor the user's dark-theme preference; do not assume it makes
the current light theme good.

## Required reviewer output

Write one original review with:

1. The rubric table for every reference and Rebuild, evidence coverage and
   confidence per row.
2. For each reference, two concrete strengths, one or two tradeoffs, and what to
   adopt or deliberately avoid. Cite images and source URLs.
3. Three to five cross-reference practices, each supported by more than one
   reference where possible.
4. A bounded Rebuild design direction: preserve, change, avoid. Specify what
   should happen to light surfaces, primary-action emphasis, typography,
   density, current/prior set presentation, home/history and rest controls.
5. Disagreements or uncertainty you expect reasonable reviewers could have.
6. No more than five highest-value implementation changes, ordered by impact.
   Explain risks to existing behavior and how to check them.

Also write a compact JSON rating record, using the filename assigned in your
task. Shape:

```json
{
  "reviewer": "assigned model name",
  "effort": "high",
  "ratings": [
    {
      "id": "R1",
      "name": "reference name",
      "scores": {
        "hierarchy": 3,
        "legibility": 3,
        "action_clarity": 2,
        "orientation": 3,
        "theming": 3,
        "rebuild_fit": 3
      },
      "confidence": "medium",
      "evidence": ["relative image path"],
      "limitations": ["unobserved interaction state"],
      "adopt": ["specific transferable pattern"],
      "avoid": ["specific mismatch with Rebuild"]
    }
  ],
  "rebuild_dark": "preserve/change assessment",
  "rebuild_light": "recommended direction",
  "top_changes": ["bounded change"],
  "uncertainties": ["specific limitation"]
}
```

The coordinator will verify the arithmetic and compare members' actual
disagreements. Do not fabricate agreement, assign an overall market winner, or
modify the application. Return your findings even if another member might
disagree.

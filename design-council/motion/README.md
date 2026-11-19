# Motion

The motion system's design record. A motion designer audited recordings of the app and wrote `spec.md`. A motion critic reviewed it until it was satisfied (`review-spec-r1.md` to `r3.md`), then judged the built app from new recordings (`review-build-r*.md`). Both are Opus agents, defined in `.claude/agents/motion-designer.md` and `motion-critic.md`.

DESIGN.md's Motion, Haptics and Navigation rules are the short form of this spec. The tokens live in `app/src/main/java/com/rebuild/ui/theme/Motion.kt`, and `MotionCurvesTest` checks the curve promises.

Paths in the reviews that point into a scratch directory refer to recordings made during the work. Those recordings are not kept in the repo.

## Tools

Each tool reads a screen recording, made with `.claude/skills/verify-rebuild/scripts/evidence.sh record-while`:

- `tools/rsheet.sh` puts every real frame of a window on a contact sheet, each stamped with its time.
- `tools/blankframes.py` lists frames whose body is one flat colour.
- `tools/presence.py` measures how much of the screen is present in each frame of a transition, against the settled screens on either side. A transition passes at 0.40 or more.
- `tools/regiondiff.py` finds the first frame where a region changes.
- `tools/hrec.sh` and `tools/hapt.py` record with haptic timestamps from `dumpsys vibrator_manager`, and place each tick against the frames it should land with.

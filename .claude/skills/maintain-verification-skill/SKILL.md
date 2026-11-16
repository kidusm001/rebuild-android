---
name: maintain-verification-skill
description: Periodic pass that keeps the verify-rebuild skill and its feature map true as the app changes. Reads every feature from source in parallel, drives every feature live on the emulator, and ships at most one PR of proven corrections. Use for "audit the verify skill", "is verify-rebuild still right", or after a release that changed several screens.
disable-model-invocation: true
---

# Maintain the verification skill

Adapted from [pstack](https://github.com/cursor/plugins/tree/main/pstack) by Lauren Tan (MIT; see `.claude/skills/LICENSE-pstack`).

A feature map rots the moment the app changes. This is the upkeep loop for `.claude/skills/verify-rebuild/`. The unit of rigour is the feature: cover every feature file from source, and exercise every feature live.

## Outcomes

Pick one, and say which:

- **clean**: every feature got source and live coverage; nothing worth shipping. No branch, no PR.
- **changed**: one PR ships proven corrections to the skill, its helpers or its map.
- **blocked**: coverage couldn't finish, or a proven fix couldn't ship safely. Say exactly what blocked it.

## Edit scope

Only edit `.claude/skills/verify-rebuild/` (its SKILL.md, `features/`, `scripts/`). Never edit app code during a run. A behaviour the map describes that the app no longer does is either drift (fix the map) or a product regression (report it to the owner; don't paper over it in the map).

## Pass

1. **Index hygiene.** Read `features/README.md` and list its sibling files. Fix missing, extra, duplicate or dead entries.
2. **Source wave.** One read-only subagent per feature file, launched together. Each explains how that feature works from the source, flags likely drift with `file:line` citations (renamed test tags and copy are the usual suspects), and returns one live-verification recipe. Subagents never drive the emulator and never edit.
3. **Reconcile.** Merge overlapping recipes into as few app states as practical (fresh install, chosen phase, seeded history). Spot-check cited drift. Sweep recent merges (`git log --since=<last pass>`) for user-facing screens missing from the map; require a concrete source path before calling one missing.
4. **Live pass.** Required even when the source looks clean. You alone drive the one emulator, serially, following the skill's own Launch section. Exercise every feature at least once, and hold three rules throughout:
   1. Never drive a device you haven't health-checked since it last did something surprising. Run `emu.sh doctor` before the first drive and again after any failed drive. Where doctor can't see the failure (a stuck screen on a healthy device), relaunch the app or `emu.sh reset` rather than hoping.
   2. Evidence captured so far survives every cleanup, checked at its location, not assumed.
   3. Nothing a drive started outlives its usefulness. A doctor failure caused by drift is drift: fix it within edit scope and retry once before calling the pass blocked. A feature that can't be reached is "unreachable" only with the concrete precondition (seed data, permission, OS version) and the route attempted.
5. **Triage.** A wrong or missing user-view description is drift: fix it. Working behaviour the helpers can't drive is a helper gap: fix the helper (executable, usage shown in its header and in SKILL.md) and drive it live again. App behaviour that's actually broken is a product bug: record it for the owner and keep it out of this PR.
6. **Ship or stop.** For changed: one PR of proven corrections, following the `pull-requests` skill; re-read every changed file first. For clean or blocked: no PR; report the outcome and the coverage honestly.

Keep short run notes (features covered, unreachable preconditions, confirmed drift, outcome) in the scratchpad. Don't commit them.

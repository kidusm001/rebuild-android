---
name: blast-radius
description: Find what a change could break somewhere else before it ships, beyond its diff, and prove the one fact it's safe because of by running real code. Use for "blast radius of X", "what could this break", before merging a change to something shared (theme colours, a shared component, a reducer, the snapshot codec, notifications), or when a small diff isn't trusted.
---

# Blast radius

Adapted from [pstack](https://github.com/cursor/plugins/tree/main/pstack) by Lauren Tan (MIT; see `.claude/skills/LICENSE-pstack`).

Find what a change breaks somewhere else, before it ships. Listing callers is not the job; grep does that in a second. The job is the breakage grep won't show you: a theme role that one screen reads as a fill and another as text, a reducer rule a second action depends on, a codec field an older backup still carries.

## Don't trust your own writeup

A blast-radius writeup that sounds right is worthless: it reads as convincing whether or not it's true. Find the one or two facts the change's safety depends on and prove them by running code.

### How sure are you

For each fact, get it as far down this list as is cheap, and say where it stopped.

1. You said so. Worthless on its own.
2. You pointed at the line: a real `file:line`, or the library's own source.
3. You showed the bad case can't happen, walking the failure step by step.
4. You ran it: a unit test or small JVM test that calls the real code and fails loud if you're wrong.
5. You reproduced it in the running app (the `verify-rebuild` skill), or in the screenshot harness (`REBUILD_SCREENS=<dir> ./gradlew testDebugUnitTest --tests com.rebuild.ui.ScreenshotHarness`) compared with `main`.

## Steps

1. **Read the change.** The diff, the symbols it adds, changes and deletes, and what it now does differently, including what the diff doesn't spell out.
2. **Find the one fact it's safe because of.** Most risky-looking changes are safe because of a single fact ("only the dark scheme's fill changes; every text use reads `accentText()`"). If it holds, most risky cases clear at once. Spend your time here.
3. **Look where grep stops.** In this repo that means:
   - theme roles (`primaryContainer`, `accentText()`, `surfaceContainer*`) read under different names across `ui/`;
   - `StateChanges`, the reducer every `Action` goes through, and `FakeRepository`, which reuses it in device tests;
   - `SnapshotCodec` and backups: an older snapshot must still open and compare record by record;
   - notification channels, whose sound and vibration Android fixes once they exist;
   - `RemoteViews` layouts, `AndroidManifest.xml`, test tags the device tests and `verify-rebuild` find controls by;
   - Compose recomposition and gesture timing (captured values in `pointerInput`, `LaunchedEffect` keys).
   Read the library source at the pinned version when behaviour depends on it.
4. **Be honest about each risk.** Give each one a real chance of happening and a real cost. Keep confirmed risks separate from the ones you checked and cleared. Cite real `file:line`; a search that finds nothing is still an answer. Never invent a caller or an API.
5. **Prove the one fact.** Write a test that runs the real code, run it, and paste what happened.
6. **For a wide change**, get a second opinion: send the diff and your writeup to a critic subagent (or two with different briefs, such as visual and correctness) and merge what they find. Different reviewers catch different real bugs.

## What to hand back

- **What it does.** What changed, including the part that isn't obvious.
- **The one fact it's safe because of.** State it, say which step you got it to, and show the proof. If you couldn't prove it, write "unproven".
- **Risks.** For each: how it breaks, `file:line`, how likely and how bad, how to check. Paste the proof for the ones that matter.
- **Cleared.** What you checked and why it's fine.
- **Before you merge.** The cheapest test or repro that catches the real bug, including the test you wrote.

Write plainly, cite real code, and keep anything private out of anything public.

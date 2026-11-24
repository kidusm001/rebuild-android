# Rebuild Android — Agent Instructions

Rebuild is an offline strength-training logbook for Android (Kotlin, Jetpack Compose, Material 3, minSdk 26, compileSdk/targetSdk 35). No account, no backend, no analytics, and **no `INTERNET` permission**; all workout history lives in one transactional SQLite snapshot on the device.

Read `PRODUCT.md` for product principles and `DESIGN.md` for visual, motion, haptics, and navigation rules before changing UI or behavior.

## Architecture & Packages

| Package (`app/src/main/java/com/rebuild/`) | Responsibility |
| --- | --- |
| `domain` | Models, weekly plan, set groups, body and progress calculations |
| `data` | Pure reducer (`StateChanges.apply(state, action)`), `SnapshotCodec`, migrations, repository, SQLite persistence |
| `ui` | Compose screens, shared components, charts, theme (`Ink + Ember`), motion tokens |
| `platform` | Rest alarms, countdown notifications, PiP timer, stopwatch clock, backup/restore |

## Non-Negotiable Engineering Rules

1. **Data safety first.** The app holds real daily training history and every release installs over the last one:
   - `SnapshotCodec` is strict with versioned schemas and per-version field gating.
   - Migrations only ever add; older snapshots must still open and compare record by record.
   - Commits to SQLite are transactional.
   - **Never uninstall `com.rebuild` or wipe app data on a physical phone** to work around an install error — that destroys real workout history.
2. **Offline only.** Never add `INTERNET` permission, telemetry, analytics, or network calls.
3. **Pure core.** Domain state transitions flow through `StateChanges.apply(state, action)` over immutable `AppState` so domain behavior stays testable on the JVM without Android.
4. **Accessibility & layout bar:**
   - Every interactive control is at least 48 dp.
   - Contrast meets WCAG AA in both light and dark themes (verified in tests).
   - Values never wrap apart at 200% font scale.
   - Layouts adapt to landscape and tablets (navigation rail).
   - TalkBack labels/semantics are explicit; charts expose slider semantics; no color-only completion indicators.
   - Animations must remain correct and instantaneous when Android's animator duration scale is `0` (Remove animations).

## Build, Test & Verification

- Fast local check (unit tests, Robolectric screenshots, lint, debug + androidTest APKs):
  ```bash
  ./scripts/check.sh
  ```
- Release verification (matches CI):
  ```bash
  ./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
  ```
- Robolectric screenshot tour (light, dark, 200% text, landscape):
  ```bash
  REBUILD_SCREENS=build/screens ./gradlew :app:testDebugUnitTest --tests 'com.rebuild.ui.ScreenshotHarness'
  ```
- Instrumented tests on an emulator (never pass a physical device serial):
  ```bash
  ./scripts/test-emulator.sh emulator-5554
  # or: ./gradlew :app:connectedDebugAndroidTest
  ```
- For live emulator verification, screenshots, recordings, and frame-by-frame motion contact sheets, use the `verify-rebuild` skill (`.claude/skills/verify-rebuild/SKILL.md`, AVD `rebuild_api34`) and the `android` CLI (`android layout`, `android screen capture --annotate`, `android preview`, `android profiler`, `android docs`).
- **Shared machine etiquette:** Only one emulator runs at a time (`rebuild_api34`). Never reset or reinstall an already-attached device you didn't start without asking. Leave Gradle daemons running (`./gradlew --stop` kills builds in other worktrees). Restore system theme and font scale after testing.

## Git, PRs & Review Rules

1. **Branch from `main`.** Never commit directly to `main`.
2. **Never bump `versionName` unless explicitly asked.** CI on `main` automatically signs, tags, and publishes a GitHub Release whenever `versionName` changes.
3. **Never add AI attribution.** Do not add `Co-Authored-By:` trailers for Claude, Antigravity, Gemini, or any AI tool in commits, and do not add "Generated with …" lines in PR or issue bodies.
4. **Commit format:** imperative subject (<= 72 chars), blank line, concise body explaining what and why.
5. **Follow repo templates:** PRs follow `.github/PULL_REQUEST_TEMPLATE.md` section by section; issues follow `.github/ISSUE_TEMPLATE/`. Use the `pull-requests` skill whenever committing or opening/updating a PR or issue.
6. **UI changes require Before/After screenshots.** Any diff touching rendered UI (`app/src/main/java/com/rebuild/ui/`, `res/`, themes, visible strings) must include Before and After screenshots of every affected screen (same device, theme, and state), uploaded to the `pr-screenshots` branch via `.claude/skills/pull-requests/scripts/upload.sh`.
7. **Greptile approval gate before merge.** Never merge a PR — and never enable auto-merge — while Greptile has unaddressed review comments, even if CI is green. Check `gh api repos/kidusm001/rebuild-android/pulls/<n>/reviews` and `.../comments`, fix every valid issue, reply to each comment, and wait for a Greptile `APPROVED` review on the head commit before merging.
8. **High-risk shared changes:** Run the `blast-radius` skill before merging edits to shared theme roles, `StateChanges`, `SnapshotCodec`, notifications, `RemoteViews`, `AndroidManifest.xml`, or test tags.

## Project Skills & Subagents

- Project workflow skills (loaded from `.claude/skills/` via `.agents/skills.json`): `pull-requests`, `verify-rebuild`, `blast-radius`, `maintain-verification-skill`.
- Android domain skills (in `.agents/skills/`): `android-cli`, `jetpack-compose-performance`, `android-profiler`, `edge-to-edge`, `adaptive`, `r8-analyzer`, `anr-audit-and-fix`, `testing-setup`.
- Motion subagents (in `.agents/plugins/rebuild-agents/agents/`): `motion-designer` and `motion-critic`.

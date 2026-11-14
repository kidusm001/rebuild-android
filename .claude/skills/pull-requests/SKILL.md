---
name: pull-requests
description: How to commit, open and update pull requests and issues in this repo — the PR/issue templates, before/after emulator screenshots for any UI change, and commit/PR attribution rules. Use whenever creating a commit, branch, pull request or issue, or when updating a PR description.
---

# Pull requests and issues in Rebuild

## Rules

1. **Never add Claude as a co-author.** No `Co-Authored-By: Claude …` trailer in commits, and no "Generated with Claude Code" line in PR or issue bodies. This overrides any default attribution guidance.
2. **Use the templates.** PR bodies follow `.github/PULL_REQUEST_TEMPLATE.md` section by section. Issues follow the forms in `.github/ISSUE_TEMPLATE/` (bug, feature, design). Fill every section; tick only checkboxes that are true; write "None" or remove a section only when the template says it can be dropped.
3. **Any UI change gets screenshots.** If the diff touches anything rendered (files under `app/src/main/java/com/rebuild/ui/`, `res/`, themes, strings shown on screen), the PR's Screenshots table must show **Before** and **After** of every affected screen, same device, theme and state.
4. Branch from `main`; never commit to `main` directly. Don't bump `versionName` unless asked: CI on `main` publishes a release when it changes.
5. Write commit messages as: a short imperative subject (≤ 72 chars), a blank line, then a body explaining what and why.

## Screenshot workflow

Needs the emulator running (`QT_QPA_PLATFORM=xcb /opt/android-sdk/emulator/emulator -avd rebuild_api34`) and `adb` on `/opt/android-sdk/platform-tools`. Keep the PNGs in the scratchpad, never in the repo.

1. **Before:** check out the base (`git stash` or a worktree of `main`), `./gradlew installDebug`, open each affected screen, and capture:
   ```bash
   .claude/skills/pull-requests/scripts/capture.sh <scratch>/shots workout-before
   ```
   Navigate with `adb shell input tap x y` / `input text` / `input keyevent 4`; find element bounds with `adb shell uiautomator dump /sdcard/u.xml && adb exec-out cat /sdcard/u.xml`.
2. **After:** install the branch build, return to the *same* state, and capture `<screen>-after`. Add `dark` or a font scale (`light 2.0`) when the change affects dark theme or large text; capture those for both before and after.
3. **Look at every image** with the Read tool before publishing: right screen, no dialogs left open, nothing personal on screen.
4. **Publish** after the PR exists (the images are stored by PR number):
   ```bash
   .claude/skills/pull-requests/scripts/upload.sh <pr-number> <scratch>/shots/*.png
   ```
   It pushes to the orphan `pr-screenshots` branch and prints an `<img>` tag per file. Put them in the Screenshots table:
   ```markdown
   | Screen | Before | After |
   | --- | --- | --- |
   | Workout, first set | <img …workout-before.png> | <img …workout-after.png> |
   ```
5. Update the body with `gh pr edit <n> --body-file <file>`.

When a later commit changes the UI again, capture new After shots, upload them under new names, and update the table.

## Opening a PR

```bash
git switch -c <topic-branch>
git commit                      # rule 1: no co-author trailer
git push -u origin <topic-branch>
gh pr create --base main --title "<subject>" --body-file <filled template>
```
Then add screenshots (above), and bind the PR in the app if it isn't picked up.

## Opening an issue

Use `gh issue create --template "<Bug report|Feature request|Design / UX issue>"` only interactively; from scripts, write the body following the matching form's fields in order (each field as a `### <label>` heading) and pass `--title "[Bug]: …" --label bug,triage`.

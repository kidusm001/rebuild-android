#!/usr/bin/env bash
# Publish screenshots for a pull request and print a Markdown image link for each one.
# Usage: upload.sh <pr-number> <png>...
# Images go to the orphan branch `pr-screenshots`, under pr-<number>/, so they never enter main's history.
set -euo pipefail
pr=$1; shift
repo=$(gh repo view --json nameWithOwner -q .nameWithOwner)
root=$(git rev-parse --show-toplevel)
work=$(mktemp -d)
trap 'git -C "$root" worktree remove --force "$work" >/dev/null 2>&1 || true; rm -rf "$work"' EXIT

if git -C "$root" ls-remote --exit-code --heads origin pr-screenshots >/dev/null; then
  git -C "$root" fetch -q origin pr-screenshots
  git -C "$root" worktree add -q --detach "$work" FETCH_HEAD
else
  git -C "$root" worktree add -q --detach "$work"
  git -C "$work" checkout -q --orphan pr-screenshots
  git -C "$work" rm -rq --cached . && git -C "$work" clean -fdq
  printf 'Screenshots referenced from pull requests. Not part of the app.\n' > "$work/README.md"
fi

mkdir -p "$work/pr-$pr"
for f in "$@"; do cp "$f" "$work/pr-$pr/$(basename "$f")"; done
git -C "$work" add -A
git -C "$work" commit -qm "Screenshots for PR #$pr" || true
git -C "$work" push -q origin HEAD:refs/heads/pr-screenshots

for f in "$@"; do
  b=$(basename "$f")
  echo "<img src=\"https://raw.githubusercontent.com/$repo/pr-screenshots/pr-$pr/$b\" width=\"240\" alt=\"${b%.png}\">"
done

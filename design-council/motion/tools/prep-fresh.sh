#!/usr/bin/env bash
# prep-fresh.sh: no active workout (discards one from Today), then a fresh process on Today, settled.
U="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)/.claude/skills/verify-rebuild/scripts/ui.sh"
adb shell am force-stop com.rebuild; adb shell am start -W -n com.rebuild/.MainActivity >/dev/null; sleep 5
if $U has "Resume workout"; then $U tap "Discard" >/dev/null; sleep 1.5; $U tap "Discard" >/dev/null; sleep 2.5; fi
adb shell am force-stop com.rebuild; adb shell am start -W -n com.rebuild/.MainActivity >/dev/null; sleep 5

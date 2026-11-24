#!/usr/bin/env bash
# Common journeys, each from where the previous one leaves off. They stop with a message at the first step that fails.
#   flows.sh first-run [EXPERIENCE] [DAYS] [GEAR]   walk setup (default regular 4 gym: Upper and lower, Build
#                                   consistency), skip rest alerts, land on Today
#   flows.sh start-workout          Today: resume the active workout, else start today's, else the first on the week
#   flows.sh log-set <kg> <reps>    log one working set on the exercise in focus
#   flows.sh set-rest <seconds>     set the running rest to a quick choice: 30, 60, 90, 120, 180 or 300
#   flows.sh seed-history           restore ~10 weeks of realistic history (replaces all app data; fresh install only)
#   flows.sh rest-alert <SOUND> [VISUAL]   More > Rest alert: BUZZ|LONG_BUZZ|CHIME|ALARM|SILENT, NONE|FLASH|SWEEP
set -euo pipefail
here=$(dirname "$0")
ui="$here/ui.sh"
adb="${ANDROID_HOME:-/opt/android-sdk}/platform-tools/adb"
root=$(git -C "$here" rev-parse --show-toplevel)

case ${1:-} in
  first-run)
    # Setup: welcome, experience (new|returning|regular|experienced), days (2–6), gear (gym|dumbbells|bodyweight),
    # the plan, then the optional rest-alert step, skipped (or done when notifications are already allowed).
    "$here/emu.sh" launch >/dev/null
    "$ui" wait setup-action 20; "$ui" tap setup-action; sleep 0.6
    "$ui" tap "setup-experience-${2:-regular}"; "$ui" tap setup-action; sleep 0.6
    "$ui" tap "setup-days-${3:-4}"; "$ui" tap setup-action; sleep 0.6
    "$ui" tap "setup-gear-${4:-gym}"; "$ui" tap setup-action; sleep 0.8
    "$ui" tap setup-action; sleep 1
    if "$ui" has setup-rest-skip; then "$ui" tap setup-rest-skip; else "$ui" tap setup-rest-done; fi
    "$ui" wait "Today" 10 ;;
  start-workout)
    "$ui" tap "Today"
    # The active workout, else today's scheduled one, else (a rest day) the first workout on this week's list.
    if "$ui" has "Resume workout"; then "$ui" tap "Resume workout"
    elif "$ui" has "start-scheduled"; then "$ui" tap "start-scheduled"
    else "$ui" tap "Start"; fi
    "$ui" wait "log-set" 10 ;;
  log-set)
    # Clear each field (a prefilled value would otherwise be appended to), type, then log.
    for field in "entry-kg:$2" "entry-reps:$3"; do
      "$ui" tap "${field%%:*}"; "$adb" shell input keyevent KEYCODE_MOVE_END
      for _ in 1 2 3 4 5 6; do "$adb" shell input keyevent KEYCODE_DEL; done
      "$ui" type "${field#*:}"
    done
    "$ui" back
    "$ui" tap "log-set"
    "$ui" wait "rest-bar" 10 ;;
  set-rest)
    # The picker is a dialog window, where test tags aren't visible: its quick times are found by their spoken
    # names ("1 minute 30 seconds") and the confirm button by its text.
    spoken=$(python3 -c '
import sys
s = int(sys.argv[1]); parts = [(s // 60, "minute"), (s % 60, "second")]
print(" ".join(f"{n} {u}{chr(115) if n != 1 else str()}" for n, u in parts if n))' "$2")
    "$ui" tap "Rest options"; "$ui" tap "Set time"
    "$ui" wait "$spoken" 5; "$ui" tap "$spoken"; "$ui" tap "Set"
    # The picker's title, not the time: the rest bar's "Add 30 seconds" would match "30 seconds" too.
    "$ui" gone "Rest time remaining" 5 ;;
  seed-history)
    # A JVM test writes the snapshot; the app's own restore flow loads it, exactly as a user moving phones would.
    seed=/tmp/rebuild-verify-state/seed.json; mkdir -p "$(dirname "$seed")"
    # --rerun: Gradle doesn't know SEED_OUT is an input, so it would otherwise skip the test as up to date and
    # leave a stale (or missing) seed. The old file is removed first so a skipped run can't go unnoticed.
    rm -f "$seed"
    (cd "$root" && SEED_OUT="$seed" ./gradlew -q :app:testDebugUnitTest --tests com.rebuild.devseed.SeedHistoryGen --rerun)
    [ -s "$seed" ] || { echo "the seed test didn't write $seed" >&2; exit 1; }
    "$adb" push "$seed" /sdcard/Download/rebuild-seed.json >/dev/null 2>&1
    "$here/emu.sh" launch >/dev/null
    # Restore is offered on setup's welcome ("I have a backup").
    "$ui" wait setup-restore 20; "$ui" tap setup-restore
    # The picker reopens in the last folder it showed. Already in Downloads: pick the file. Otherwise open the drawer
    # and choose its Downloads item ("title=Downloads": the toolbar can say "Downloads" too).
    "$ui" wait "Show roots" 10; sleep 1
    # In grid view the file has no "title" node; switch to the list ("List view" is the button that does so).
    if "$ui" has "List view"; then "$ui" tap "List view"; sleep 1; fi
    if ! "$ui" has "title=rebuild-seed.json"; then
      "$ui" tap "Show roots"
      "$ui" wait "title=Downloads" 5; sleep 0.8; "$ui" tap "title=Downloads"
      "$ui" wait "title=rebuild-seed.json" 10
    fi
    "$ui" tap "title=rebuild-seed.json"
    "$ui" wait "Save current backup" 10; "$ui" tap "Save current backup"
    "$ui" wait "SAVE" 10; "$ui" tap "SAVE"
    "$ui" wait "Replace all data" 10; "$ui" tap "Replace all data"
    # The restore ends with a "Restore complete" message whose OK must be dismissed before anything else responds.
    "$ui" wait "OK" 15; "$ui" tap "OK"
    "$ui" wait "Today" 15 ;;
  rest-alert)
    "$ui" tap "More"; sleep 1
    "$ui" has "more-rest-alert" || "$adb" shell input swipe 540 1900 540 500 300
    "$ui" tap "more-rest-alert"
    "$ui" tap "rest-sound-$(echo "$2" | tr 'A-Z' 'a-z')"
    [ -n "${3:-}" ] && { "$adb" shell input swipe 540 1900 540 900 300; "$ui" tap "rest-visual-$(echo "$3" | tr 'A-Z' 'a-z')"; }
    "$ui" back ;;
  *) sed -n '2,9p' "$0"; exit 2 ;;
esac

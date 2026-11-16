#!/usr/bin/env bash
# Common journeys, each from where the previous one leaves off. They stop with a message at the first step that fails.
#   flows.sh first-run [PHASE]      choose a phase on the first-run screen (default INTRO_TWO = Build consistency)
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
    "$ui" wait "phase-${2:-INTRO_TWO}" 20
    "$ui" tap "phase-${2:-INTRO_TWO}"
    "$ui" tap "Start with"
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
    "$ui" tap "Rest options"; "$ui" tap "Set time"
    "$ui" tap "duration-quick-$2"; "$ui" tap "duration-set"
    "$ui" gone "duration-set" 5 ;;
  seed-history)
    # A JVM test writes the snapshot; the app's own restore flow loads it, exactly as a user moving phones would.
    seed=/tmp/rebuild-verify-state/seed.json; mkdir -p "$(dirname "$seed")"
    (cd "$root" && SEED_OUT="$seed" ./gradlew -q :app:testDebugUnitTest --tests com.rebuild.devseed.SeedHistoryGen)
    "$adb" push "$seed" /sdcard/Download/rebuild-seed.json >/dev/null
    "$ui" wait "Restore a backup" 20; "$ui" tap "Restore a backup"
    "$ui" wait "Show roots" 10; "$ui" tap "Show roots"; "$ui" tap "Downloads"; sleep 2
    "$ui" tap "rebuild-seed.json"
    "$ui" wait "Save current backup" 10; "$ui" tap "Save current backup"
    "$ui" wait "SAVE" 10; "$ui" tap "SAVE"
    "$ui" wait "Replace all data" 10; "$ui" tap "Replace all data"
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

#!/usr/bin/env bash
# Rebuild verification: the emulator and the app on it.
#   emu.sh up        boot the rebuild_api34 emulator if no device is attached, wait until usable
#   emu.sh install   build and install the debug app, grant notifications and exact alarms
#   emu.sh reset     wipe the app's data (fresh install state) and grant again
#   emu.sh launch    open the app (it is not opened by install or reset)
#   emu.sh doctor    read-only: is this device and app worth driving? exits non-zero if not
#   emu.sh down      stop the emulator this skill started (never one it didn't), stop Gradle daemons
# Env: ANDROID_HOME (default /opt/android-sdk), AVD (default rebuild_api34), REBUILD_EVIDENCE (default /tmp/rebuild-verify).
set -euo pipefail
ANDROID_HOME=${ANDROID_HOME:-/opt/android-sdk}
AVD=${AVD:-rebuild_api34}
EVIDENCE=${REBUILD_EVIDENCE:-/tmp/rebuild-verify}
STATE=/tmp/rebuild-verify-state
PKG=com.rebuild
adb="$ANDROID_HOME/platform-tools/adb"
root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
mkdir -p "$EVIDENCE" "$STATE"

booted() { [ "$("$adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; }

grant() {
  "$adb" shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
  "$adb" shell appops set $PKG SCHEDULE_EXACT_ALARM allow 2>/dev/null || true
}

case ${1:-} in
  up)
    # Never start a second emulator: an attached device (even one still booting) is waited for, and more than one
    # attached device is refused, since every other command would then be ambiguous.
    attached=$("$adb" devices | awk 'NR > 1 && $2 != "" { n++ } END { print n + 0 }')
    if [ "$attached" -gt 1 ]; then
      echo "more than one device attached; disconnect one or set ANDROID_SERIAL" >&2; "$adb" devices >&2; exit 1
    fi
    if [ "$attached" = 1 ]; then
      echo "device already attached; waiting for it to finish booting"
      "$adb" wait-for-device
      for _ in $(seq 1 120); do booted && break; sleep 2; done
      booted || { echo "attached device did not finish booting" >&2; exit 1; }
      echo "emulator ready"; exit 0
    fi
    # xcb: the Qt wayland plugin is missing on this machine. swiftshader: the host GPU path segfaults.
    QT_QPA_PLATFORM=xcb nohup "$ANDROID_HOME/emulator/emulator" -avd "$AVD" -gpu swiftshader_indirect \
      -no-snapshot-save -no-audio >"$EVIDENCE/emulator.log" 2>&1 &
    echo $! >"$STATE/emulator.pid"
    "$adb" wait-for-device
    for _ in $(seq 1 120); do booted && break; sleep 2; done
    booted || { echo "emulator did not finish booting; see $EVIDENCE/emulator.log" >&2; exit 1; }
    "$adb" shell settings put global window_animation_scale 1 >/dev/null
    "$adb" shell input keyevent KEYCODE_WAKEUP
    echo "emulator ready"
    ;;
  install)
    (cd "$root" && ./gradlew -q installDebug)
    grant
    echo "installed $(grep -o 'versionName = "[^"]*"' "$root/app/build.gradle.kts")"
    ;;
  reset)
    "$adb" shell pm clear $PKG >/dev/null
    grant
    echo "app data cleared"
    ;;
  launch)
    "$adb" shell am start -n $PKG/.MainActivity >/dev/null
    echo "app opened"
    ;;
  doctor)
    ok=1
    "$adb" get-state >/dev/null 2>&1 || { echo "FAIL no device attached (run: emu.sh up)"; exit 1; }
    booted && echo "ok   device booted" || { echo "FAIL device still booting"; ok=0; }
    installed=$("$adb" shell dumpsys package $PKG 2>/dev/null | grep -m1 versionName | tr -d ' \r' | cut -d= -f2) || installed=
    expected=$(grep -o 'versionName = "[^"]*"' "$root/app/build.gradle.kts" | cut -d'"' -f2)
    [ -n "$installed" ] && echo "ok   app installed ($installed; checkout says $expected)" || { echo "FAIL app not installed (run: emu.sh install)"; ok=0; }
    "$adb" shell dumpsys package $PKG 2>/dev/null | grep -q "POST_NOTIFICATIONS: granted=true" \
      && echo "ok   notifications granted" || echo "warn notifications not granted (rest alerts won't post)"
    "$adb" shell dumpsys power | grep -q "mWakefulness=Awake" && echo "ok   screen awake" || echo "warn screen asleep (input keyevent KEYCODE_WAKEUP)"
    avail=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
    [ "$avail" -gt 1500 ] && echo "ok   host RAM ${avail} MB free" || echo "warn host RAM low (${avail} MB); the emulator may crash"
    [ $ok = 1 ]
    ;;
  down)
    if [ -f "$STATE/emulator.pid" ] && kill -0 "$(cat "$STATE/emulator.pid")" 2>/dev/null; then
      "$adb" emu kill >/dev/null 2>&1 || kill "$(cat "$STATE/emulator.pid")"
      rm -f "$STATE/emulator.pid"
      echo "emulator stopped"
    else
      echo "no emulator started by this skill is running; leaving any other device alone"
    fi
    (cd "$root" && ./gradlew --stop -q) || true
    echo "evidence kept in $EVIDENCE"
    ;;
  *) sed -n '2,10p' "$0"; exit 2 ;;
esac

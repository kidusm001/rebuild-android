#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
serial="${1:?Usage: scripts/test-emulator.sh emulator-SERIAL}"
case "$serial" in
  emulator-[0-9]*) ;;
  *) printf '%s\n' "Refusing automated fixture tests on a physical device. Use the manual acceptance checklist." >&2; exit 2 ;;
esac
adb -s "$serial" get-state
./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
result="$(adb -s "$serial" shell am instrument -w -r com.rebuild.test/androidx.test.runner.AndroidJUnitRunner)"
printf '%s\n' "$result"
if ! grep -q '^OK (' <<<"$result" || grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' <<<"$result"; then
  exit 1
fi

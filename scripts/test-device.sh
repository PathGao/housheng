#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
export ANDROID_USER_HOME="$PWD/.tools/android-user"
if [ -x .tools/android-sdk/platform-tools/adb ]; then
  adb="$PWD/.tools/android-sdk/platform-tools/adb"
else
  adb=adb
fi
classes=io.github.pathgao.housheng.DeviceValidationTest,io.github.pathgao.housheng.SessionPreferencesTest,io.github.pathgao.housheng.PlatformValidationTest,io.github.pathgao.housheng.ReportStoreTest,io.github.pathgao.housheng.ProductFlowTest
transport=${2:-usb}
if [ "${1:-}" = "--model" ]; then
  case "$transport" in direct|usb) ;; *) echo "transport must be direct or usb" >&2; exit 2 ;; esac
  if [ "$transport" = usb ]; then
    python3 -c 'import json, urllib.request; r=json.load(urllib.request.urlopen("http://127.0.0.1:18765/health", timeout=2)); assert r["ready"] and r["model"] == "clef-flash"'
    "$adb" reverse tcp:18765 tcp:18765
  else
    "$adb" reverse --remove tcp:18765 2>/dev/null || true
  fi
  classes="io.github.pathgao.housheng.ClefConfigurationTest,$classes,io.github.pathgao.housheng.ModelClientTest,io.github.pathgao.housheng.ModelPipelineTest"
elif [ "$#" -gt 0 ]; then
  echo "usage: scripts/test-device.sh [--model [direct|usb]]" >&2
  exit 2
fi
# The online build contains every test class, including the model ones.
app=io.github.pathgao.housheng.online
scripts/build-local.sh :app:assembleOnlineDebug :app:assembleOnlineDebugAndroidTest :fixture:assembleDebug :fixture:assembleDebugAndroidTest --console=plain
"$adb" install -r app/build/outputs/apk/online/debug/app-online-debug.apk
"$adb" install -r fixture/build/outputs/apk/debug/fixture-debug.apk
"$adb" install -r app/build/outputs/apk/androidTest/online/debug/app-online-debug-androidTest.apk
"$adb" install -r fixture/build/outputs/apk/androidTest/debug/fixture-debug-androidTest.apk
if [ "${1:-}" = "--model" ]; then
  ADB="$adb" python3 validation/clef/install_test_config.py
fi
# Notification permission is granted through the fixture's normal system prompt.
# Xiaomi may deny shell permission grants even when USB debugging is enabled.
mkdir -p .tools
"$adb" shell am instrument -w -r -e modelTransport "$transport" -e class "$classes" "$app.test/androidx.test.runner.AndroidJUnitRunner" > .tools/device-test.log
cat .tools/device-test.log
result=0
grep -Eq '^OK \([1-9][0-9]* tests?\)' .tools/device-test.log || result=1
# Run recovery in the fixture process so its exit leaves Housheng connected.
"$adb" shell am instrument -w -e housheng "$app" io.github.pathgao.housheng.fixture.test/androidx.test.runner.AndroidJUnitRunner > .tools/service-recovery.log
cat .tools/service-recovery.log
grep -Eq '^OK \([1-9][0-9]* tests?\)' .tools/service-recovery.log || result=1
exit "$result"

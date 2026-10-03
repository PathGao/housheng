#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -d .tools/jdk ]; then
  JAVA_HOME=$(find "$PWD/.tools/jdk" -type d -path '*/Contents/Home' | head -1)
  export JAVA_HOME
fi
if [ -d .tools/android-sdk ]; then
  ANDROID_HOME="$PWD/.tools/android-sdk"
  export ANDROID_HOME
fi
export ANDROID_USER_HOME="$PWD/.tools/android-user"
export GRADLE_USER_HOME="$PWD/.gradle-home"
exec ./gradlew "$@"

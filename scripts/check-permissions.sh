#!/bin/sh
# Release permission contract: offline requests nothing, online only INTERNET. Any other change must be reviewed here.
# usage: scripts/check-permissions.sh <aapt2> <offline.apk> <online.apk>
set -eu
check() {
  # Assigned first so an aapt2 failure fails the script instead of comparing empty output.
  actual=$("$1" dump permissions "$2")
  if [ "$actual" != "$3" ]; then
    printf '::error::%s 的权限与预期不符\n%s\n' "$2" "$actual"
    exit 1
  fi
}
check "$1" "$2" "package: io.github.pathgao.housheng"
check "$1" "$3" "package: io.github.pathgao.housheng.online
uses-permission: name='android.permission.INTERNET'"

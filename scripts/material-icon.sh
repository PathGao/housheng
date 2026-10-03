#!/bin/sh
# Converts Material Symbols Rounded (Apache-2.0) to VectorDrawables: scripts/material-icon.sh name [fill]
set -eu
cd "$(dirname "$0")/.."
name=$1
suffix=${2:+_fill1}
path=$(curl -sfL "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/$name/materialsymbolsrounded/${name}${suffix}_24px.svg" | sed -n 's/.*<path d="\([^"]*\)".*/\1/p')
[ -n "$path" ]
cat > "app/src/main/res/drawable/ic_${name}${suffix}.xml" <<XML
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="960" android:viewportHeight="960" android:tint="?android:attr/textColorPrimary">
    <group android:translateY="960"><path android:fillColor="@android:color/white" android:pathData="$path"/></group>
</vector>
XML

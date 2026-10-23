#!/bin/bash
# Shared by the perf scripts: adb, the app's package, where results go, and gestures that first
# make sure the app is what is on screen. Sourced, not run.
#
# Every tap and swipe goes through `go`: it checks the app is in front and stops the whole run
# otherwise. The phone drops off USB now and then for a second; a script that went on tapping
# blindly then tapped the launcher (and opened Google search) while the user watched.

PERF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$PERF_DIR/.." && pwd)"
OUT="$PERF_DIR/out"
mkdir -p "$OUT"
PKG=com.example.myapplication

# adb: $ADB, else the SDK's from local.properties, else the one on PATH.
if [ -z "$ADB" ]; then
    sdk=$(grep -E '^sdk.dir=' "$ROOT/local.properties" 2>/dev/null | cut -d= -f2- | sed 's/\\:/:/g; s/\\\\/\//g')
    if [ -n "$sdk" ] && command -v cygpath >/dev/null 2>&1; then sdk=$(cygpath -u "$sdk"); fi
    if [ -n "$sdk" ] && [ -x "$sdk/platform-tools/adb.exe" ]; then ADB="$sdk/platform-tools/adb.exe"
    elif [ -n "$sdk" ] && [ -x "$sdk/platform-tools/adb" ]; then ADB="$sdk/platform-tools/adb"
    else ADB=adb; fi
fi

# Git Bash rewrites arguments that look like paths (/sdcard, /data/...): not for adb.
a() { MSYS_NO_PATHCONV=1 "$ADB" "$@"; }

# A path as Windows programs (python, adb push/pull) take it, under Git Bash; as it is elsewhere.
native() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }

stop() { echo "STOPPED: $*" >&2; exit 1; }
connected() { a get-state 2>/dev/null | grep -q device; }
in_app() { a shell dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity" | grep -q "$PKG/"; }
# A gesture, only with the app on screen: go tap X Y | go swipe X1 Y1 X2 Y2 MS
go() { in_app || stop "the app is not on screen"; a shell input "$@" || stop "adb"; }

# The screen's size, and a point given as fractions of it — the scripts were written on a
# 1008×2244 Pixel 9 Pro XL, and scale to another screen through this.
read SCREEN_W SCREEN_H < <(a shell wm size 2>/dev/null | tail -1 | grep -oE '[0-9]+x[0-9]+' | tr 'x' ' ')
SCREEN_W=${SCREEN_W:-1008}; SCREEN_H=${SCREEN_H:-2244}
at() { echo "$(( SCREEN_W * $1 / 1008 )) $(( SCREEN_H * $2 / 2244 ))"; }
# Home's toolbar: the service tabs and the search button, at the foot of the screen.
TAB_SOUNDCLOUD=$(at 108 2078); TAB_YANDEX=$(at 224 2078); TAB_YOUTUBE=$(at 341 2078); TAB_MINE=$(at 457 2078)

# Starts the app and waits until it is in front.
launch() {
    connected || stop "no phone"
    a shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    for _ in 1 2 3 4 5 6 7 8 9 10; do in_app && break; sleep 1; done
    in_app || stop "the app did not open"
    sleep 4
}

# A tab of home, on its hero (a swipe down brings the feed back to it).
open_tab() {
    go tap "$@"; sleep 2.5
    go swipe $(at 500 900) $(at 500 1700) 150; sleep 2
}

# Down from the hero to the shelves.
to_shelves() { go swipe $(at 500 1500) $(at 500 900) 200; sleep 1.5; }

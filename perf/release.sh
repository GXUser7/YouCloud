#!/bin/bash
# The release build on the phone, as users have it: built (R8), installed over whatever is there
# — both build types are signed with the same key on this machine, so the data stays — and
# compiled ahead of time from its baseline profiles, as the phone would do after a while by
# itself. Measure the main thread on this, never on a debug build: Compose there is several
# times slower, and what lags in it may not lag at all.
#   perf/release.sh            (JAVA_HOME as gradle wants it; see the README)
source "$(dirname "$0")/lib.sh"
connected || stop "no phone"
cd "$ROOT" || exit 1
./gradlew :app:assembleRelease --console=plain -q || stop "build"
APK=$(ls app/build/outputs/apk/release/*.apk | head -1)
a install -r -d "$(native "$APK")" | tail -1 | grep -q Success || stop "install"
launch
sleep 6
a shell am force-stop $PKG
a shell cmd package compile -m speed-profile -f $PKG | tail -1
launch
echo "release build on the phone: $APK"

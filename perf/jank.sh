#!/bin/bash
# Frames, scenario by scenario: home's YouTube and Yandex tabs still and scrolled, and a mix's
# list scrolled. For each, gfxinfo's summary (janky frames, percentiles, slow UI thread / slow
# issue draw commands) and where the last 120 frames' time went (frames.py).
#   perf/jank.sh [label]        results in perf/out/jank_<label>.txt
source "$(dirname "$0")/lib.sh"
LABEL=${1:-run}
REPORT="$OUT/jank_$LABEL.txt"
: > "$REPORT"

summary() {
    a shell dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames:|50th percentile|90th percentile|99th percentile|Slow UI thread|Slow issue draw" | tr -s ' ' | tr '\n' ';'
}
measure() { # name, then the function that runs while measuring
    local name=$1; shift
    a shell dumpsys gfxinfo $PKG reset >/dev/null
    "$@"
    a shell dumpsys gfxinfo $PKG framestats > "$OUT/framestats.txt"
    { echo "== $name"; summary; echo; python "$(native "$PERF_DIR/frames.py")" "$(native "$OUT/framestats.txt")" "$name"; } >> "$REPORT"
}
idle() { sleep 5; }
scroll() {
    for _ in 1 2 3 4; do
        go swipe $(at 500 1700) $(at 500 900) 250; sleep 0.6
        go swipe $(at 500 900) $(at 500 1700) 250; sleep 0.6
    done
}
# Down a page's list and partly back: never to its top, where a pull down closes the page.
pagescroll() {
    for _ in 1 2 3 4; do go swipe $(at 500 1800) $(at 500 500) 200; sleep 0.7; done
    for _ in 1 2; do go swipe $(at 500 700) $(at 500 1500) 200; sleep 0.7; done
}

in_app || launch
open_tab $TAB_YOUTUBE
measure "YouTube hero, still" idle
to_shelves
measure "YouTube shelves, still" idle
measure "YouTube shelves, scrolled" scroll
open_tab $TAB_YANDEX
measure "Yandex «Моя форма», still" idle
to_shelves
measure "Yandex shelves, scrolled" scroll
open_tab $TAB_SOUNDCLOUD
go tap $(at 500 900); sleep 3   # the mix in front of the carousel
measure "A mix, still" idle
measure "A mix, scrolled" pagescroll
cat "$REPORT"

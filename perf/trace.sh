#!/bin/bash
# A system trace of the app (atrace: gfx, view, hwui) over [seconds], while [gesture] runs, then
# the time each thread spent in each slice (slices.py) and the longest frames of the main thread
# and the RenderThread, slice by slice (longframes.py).
#   perf/trace.sh <label> [seconds] [still|scroll|fling]
# still: nothing moves on screen — what the app costs sitting there.
# scroll: an even scroll up and down. fling: fast flings, as a thumb does through home's shelves.
source "$(dirname "$0")/lib.sh"
LABEL=${1:-run}; SECONDS_=${2:-6}; GESTURE=${3:-still}

in_app || stop "open the app on the screen to trace first"
pid=$(a shell pidof $PKG | tr -d '\r')
a shell "atrace -t $SECONDS_ -b 65536 -a $PKG gfx view hwui > /data/local/tmp/trace.txt 2>/dev/null" &
sleep 0.8
case $GESTURE in
    scroll) for _ in 1 2 3 4; do go swipe $(at 500 1700) $(at 500 900) 250; sleep 0.6; go swipe $(at 500 900) $(at 500 1700) 250; sleep 0.6; done ;;
    fling) for _ in 1 2 3 4; do go swipe $(at 500 1800) $(at 500 300) 90; sleep 0.45; go swipe $(at 500 300) $(at 500 1800) 90; sleep 0.45; done ;;
esac
wait
TRACE="$OUT/trace_$LABEL.txt"
a pull /data/local/tmp/trace.txt "$(native "$TRACE")" >/dev/null 2>&1 || stop "adb pull"
echo "== where the time went ($LABEL)"
python "$(native "$PERF_DIR/slices.py")" "$(native "$TRACE")" $pid 4 | head -30
echo "== the longest main-thread frames"
python "$(native "$PERF_DIR/longframes.py")" "$(native "$TRACE")" $pid e.myapplication "Choreographer#doFrame" | head -40
echo "== the longest RenderThread frames"
python "$(native "$PERF_DIR/longframes.py")" "$(native "$TRACE")" $pid RenderThread Drawing | head -30

#!/bin/bash
# The power of each of the phone's rails (CPU clusters, GPU, display, memory…) over ten seconds,
# the app sitting still on a tab of home, read from the Pixel's on-device power monitor through
# Perfetto (android.power, collect_power_rails). The fair measure of what the app costs at rest:
# CPU percentages mislead — a thread on the little cores shows a large share at a low clock — and
# the GPU's time per frame stays the same as its clock follows the load.
#   perf/energy.sh <label> [youtube|yandex|soundcloud|mine]
# Compare builds by running it on each, alternately, twice or more: one run is noisy.
source "$(dirname "$0")/lib.sh"
LABEL=${1:-run}
case ${2:-youtube} in
    yandex) TAB=$TAB_YANDEX ;; soundcloud) TAB=$TAB_SOUNDCLOUD ;; mine) TAB=$TAB_MINE ;; *) TAB=$TAB_YOUTUBE ;;
esac
in_app || launch
open_tab $TAB
sleep 2
CFG="$OUT/power.cfg"
cat > "$CFG" <<'EOF'
buffers { size_kb: 8192 }
data_sources { config { name: "android.power" android_power_config { battery_poll_ms: 1000 collect_power_rails: true } } }
duration_ms: 10000
EOF
a push "$(native "$CFG")" /data/local/tmp/power.cfg >/dev/null || stop "adb push"
a shell "cat /data/local/tmp/power.cfg | perfetto --txt -c - -o /data/misc/perfetto-traces/power.pftrace" >/dev/null 2>&1 || stop "perfetto"
in_app || stop "the app left the screen while measuring"
TRACE="$OUT/power_$LABEL.pftrace"
a pull /data/misc/perfetto-traces/power.pftrace "$(native "$TRACE")" >/dev/null 2>&1 || stop "adb pull"
echo "== $LABEL"
python "$(native "$PERF_DIR/rails.py")" "$(native "$TRACE")"

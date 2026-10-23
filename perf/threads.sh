#!/bin/bash
# CPU time of each of the app's threads over five seconds, as a share of one core: which thread is
# busy while nothing happens on screen. A thread on the little cores shows more than its energy
# is worth (see energy.sh).
#   perf/threads.sh
source "$(dirname "$0")/lib.sh"
in_app || stop "open the app on the screen first"
pid=$(a shell pidof $PKG | tr -d '\r')
snap() { a shell "cd /proc/$pid/task; for t in *; do echo \$t \$(cut -d' ' -f14,15 \$t/stat 2>/dev/null) \$(cat \$t/comm 2>/dev/null); done"; }
snap > "$OUT/threads0.txt"; sleep 5; snap > "$OUT/threads1.txt"
python - "$(native "$OUT/threads0.txt")" "$(native "$OUT/threads1.txt")" <<'EOF'
import sys
def load(p):
    d = {}
    for line in open(p, encoding='utf-8', errors='replace'):
        parts = line.split()
        if len(parts) >= 4 and parts[1].isdigit():
            d[parts[0]] = (int(parts[1]) + int(parts[2]), ' '.join(parts[3:]))
    return d
a, b = load(sys.argv[1]), load(sys.argv[2])
rows = sorted(((b[t][0] - a.get(t, (0, ''))[0], b[t][1]) for t in b), reverse=True)
print(f'{sum(r[0] for r in rows) / 5:5.1f}%  all threads (100% = one core)')
for ticks, name in rows[:10]:
    if ticks > 0:
        print(f'{ticks / 5:5.1f}%  {name}')
EOF

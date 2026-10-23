#!/bin/bash
# Ten frames of the screen taken one after another (raw screencap, about ten a second) while a
# gesture runs, laid side by side in perf/out/burst_<label>.png: how a transition looks midway —
# a page coming in, one pulled away — which a single screenshot never catches.
#   perf/burst.sh <label> tap X Y | swipe X1 Y1 X2 Y2 MS
# Coordinates as on a 1008×2244 screen (see `at` in lib.sh).
source "$(dirname "$0")/lib.sh"
LABEL=$1; shift
in_app || stop "open the app on the screen first"
if [ "$1" = tap ]; then GESTURE="input tap $(at $2 $3)"; else GESTURE="input swipe $(at $2 $3) $(at $4 $5) $6"; fi
a shell "rm -f /data/local/tmp/burst*.raw; $GESTURE & for i in 0 1 2 3 4 5 6 7 8 9; do screencap /data/local/tmp/burst\$i.raw; done; wait"
for i in 0 1 2 3 4 5 6 7 8 9; do a pull /data/local/tmp/burst$i.raw "$(native "$OUT/burst$i.raw")" >/dev/null 2>&1; done
python - "$(native "$OUT")" "$LABEL" <<'EOF'
import os, struct, sys
from PIL import Image
out, label = sys.argv[1], sys.argv[2]
tiles = []
for i in range(10):
    raw = open(os.path.join(out, f'burst{i}.raw'), 'rb').read()
    w, h, _ = struct.unpack('<III', raw[:12])
    pixels = raw[len(raw) - w * h * 4:]
    tiles.append(Image.frombuffer('RGBA', (w, h), pixels, 'raw', 'RGBA', 0, 1).convert('RGB').resize((w // 5, h // 5)))
w, h = tiles[0].size
sheet = Image.new('RGB', (w * 5 + 20, h * 2 + 5), 'white')
for k, tile in enumerate(tiles):
    sheet.paste(tile, ((k % 5) * (w + 5), (k // 5) * (h + 5)))
path = os.path.join(out, f'burst_{label}.png')
sheet.save(path)
print(path)
EOF

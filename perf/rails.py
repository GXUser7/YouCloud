"""Energy per power rail from a Perfetto trace with android.power rails (no libraries: a tiny
protobuf reader). Prints the average power of each rail over the trace, in milliwatts."""
import sys
from collections import defaultdict


def varint(b, i):
    shift = result = 0
    while True:
        c = b[i]
        i += 1
        result |= (c & 0x7F) << shift
        if c < 0x80:
            return result, i
        shift += 7


def fields(b):
    i = 0
    while i < len(b):
        key, i = varint(b, i)
        num, wt = key >> 3, key & 7
        if wt == 0:
            v, i = varint(b, i)
        elif wt == 1:
            v = int.from_bytes(b[i:i + 8], 'little'); i += 8
        elif wt == 2:
            n, i = varint(b, i)
            v = b[i:i + n]; i += n
        elif wt == 5:
            v = int.from_bytes(b[i:i + 4], 'little'); i += 4
        else:
            raise ValueError(wt)
        yield num, v


data = open(sys.argv[1], 'rb').read()
names = {}
samples = defaultdict(list)  # index -> [(ms, energy uWs)]
for num, packet in fields(data):
    if num != 1:
        continue
    for pnum, pv in fields(packet):
        if pnum != 40 or not isinstance(pv, bytes):
            continue
        for rnum, rv in fields(pv):
            if rnum == 1:
                d = dict(fields(rv))
                names[d.get(1, 0)] = (d.get(2, b'').decode(), d.get(3, b'').decode())
            elif rnum == 2:
                d = dict(fields(rv))
                samples[d.get(1, 0)].append((d.get(2, 0), d.get(3, 0)))
total = 0.0
rows = []
for index, points in samples.items():
    points.sort()
    if len(points) < 2:
        continue
    (t0, e0), (t1, e1) = points[0], points[-1]
    mw = (e1 - e0) / max(t1 - t0, 1)  # uWs per ms = mW
    rail, subsystem = names.get(index, (str(index), ''))
    rows.append((mw, rail, subsystem))
for mw, rail, subsystem in sorted(rows, reverse=True):
    print(f'{mw:8.1f} mW  {rail:20s} {subsystem}')
    total += mw
print(f'{total:8.1f} mW  total')

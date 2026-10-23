"""The longest RenderThread frames of an atrace dump, and what was in them."""
import re
import sys
from collections import defaultdict

path, pid = sys.argv[1], sys.argv[2]
thread_name = sys.argv[3] if len(sys.argv) > 3 else 'RenderThread'
top_name = sys.argv[4] if len(sys.argv) > 4 else 'Drawing'
rx = re.compile(r'^\s*(.+?)-(\d+)\s+\(\s*(\d+)\)\s+\[\d+\]\s+\S+\s+([\d.]+): tracing_mark_write: ([BE])\|(\d+)(?:\|(.*))?$')
stack = []
frames = []  # (duration, start, children list)
current = None
for line in open(path, encoding='utf-8', errors='replace'):
    m = rx.match(line)
    if not m or m.group(6) != pid or m.group(1).strip() != thread_name:
        continue
    _, tid, _, ts, kind, _, name = m.groups()
    ts = float(ts)
    if kind == 'B':
        stack.append([name or '', ts, []])
    elif stack:
        name, start, kids = stack.pop()
        node = (ts - start, name, kids)
        if stack:
            stack[-1][2].append(node)
        if name.startswith(top_name):
            frames.append(node)


def show(node, depth, limit):
    dur, name, kids = node
    if dur * 1000 < limit and depth > 0:
        return
    print('  ' * depth + f'{dur * 1000:6.2f} ms  {name[:90]}')
    merged = defaultdict(lambda: [0.0, 0, None])
    for k in kids:
        key = k[1][:60]
        merged[key][0] += k[0]
        merged[key][1] += 1
        merged[key][2] = k
    for key, (total, count, sample) in sorted(merged.items(), key=lambda kv: -kv[1][0])[:8]:
        if count > 1:
            if total * 1000 >= limit:
                print('  ' * (depth + 1) + f'{total * 1000:6.2f} ms  x{count} {key}')
        else:
            show(sample, depth + 1, limit)


frames.sort(key=lambda n: -n[0])
print(f'{len(frames)} frames; median {sorted(f[0] for f in frames)[len(frames) // 2] * 1000:.2f} ms')
for f in frames[:5]:
    show(f, 0, 0.4)
    print()

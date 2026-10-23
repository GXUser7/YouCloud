"""Sums an atrace text dump's slices of one process, by thread and name (self and total time)."""
import re
import sys
from collections import defaultdict

path, pid = sys.argv[1], sys.argv[2]
depth_filter = int(sys.argv[3]) if len(sys.argv) > 3 else 99
line_re = re.compile(r'^\s*(.+?)-(\d+)\s+\(\s*(\d+)\)\s+\[\d+\]\s+\S+\s+([\d.]+): tracing_mark_write: ([BE])\|(\d+)(?:\|(.*))?$')
stacks = defaultdict(list)
total = defaultdict(float)
count = defaultdict(int)
first = last = None
for line in open(path, encoding='utf-8', errors='replace'):
    m = line_re.match(line)
    if not m:
        continue
    thread, tid, tgid, ts, kind, mpid, name = m.groups()
    if mpid != pid:
        continue
    ts = float(ts)
    first = ts if first is None else first
    last = ts
    stack = stacks[tid]
    if kind == 'B':
        stack.append((name or '', ts, thread))
    elif stack:
        n, start, th = stack.pop()
        if len(stack) < depth_filter:
            key = (th.strip(), n[:70], len(stack))
            total[key] += ts - start
            count[key] += 1
span = (last - first) if first is not None else 1
print(f'span {span:.2f}s')
for (thread, name, depth), t in sorted(total.items(), key=lambda kv: -kv[1])[:45]:
    print(f'{t / span * 100:6.1f}%  {count[(thread, name, depth)] / span:6.1f}/s  d{depth} {thread[:16]:16s} {name}')

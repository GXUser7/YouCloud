"""Reads `dumpsys gfxinfo <pkg> framestats` and says where a frame's time goes."""
import sys
import statistics

text = open(sys.argv[1], encoding='utf-8', errors='replace').read()
label = sys.argv[2] if len(sys.argv) > 2 else ''
lines = text.splitlines()
header = None
rows = []
for line in lines:
    if line.startswith('Flags,'):
        header = line.strip().strip(',').split(',')
        continue
    if header and line and line[0].isdigit():
        parts = line.strip().strip(',').split(',')
        if len(parts) >= len(header):
            row = dict(zip(header, (int(p) for p in parts[:len(header)])))
            if row['Flags'] == 0:
                rows.append(row)
    if line.startswith('---PROFILEDATA---') and rows and header:
        pass


def ms(a, b):
    return [(r[b] - r[a]) / 1e6 for r in rows if r[b] > 0 and r[a] > 0]


def med(xs):
    return statistics.median(xs) if xs else float('nan')


def p90(xs):
    return sorted(xs)[int(len(xs) * 0.9)] if xs else float('nan')


if not rows:
    print(label, 'no frames')
    sys.exit()
span = (rows[-1]['IntendedVsync'] - rows[0]['IntendedVsync']) / 1e9
print(f'{label}: {len(rows)} frames over {span:.1f}s = {len(rows) / max(span, 0.001):.0f}/s')
for name, a, b in [
    ('input+anim (withFrameNanos)', 'IntendedVsync', 'PerformTraversalsStart'),
    ('layout+record', 'PerformTraversalsStart', 'SyncQueued'),
    ('UI thread total', 'IntendedVsync', 'SyncQueued'),
    ('RenderThread sync', 'SyncStart', 'IssueDrawCommandsStart'),
    ('RenderThread draw', 'IssueDrawCommandsStart', 'SwapBuffers'),
    ('GPU after issue', 'IssueDrawCommandsStart', 'GpuCompleted'),
    ('whole frame', 'IntendedVsync', 'FrameCompleted'),
]:
    xs = ms(a, b)
    print(f'  {name:28s} median {med(xs):6.2f} ms   p90 {p90(xs):6.2f} ms')

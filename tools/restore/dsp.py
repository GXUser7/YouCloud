"""
The signal processing the restoration models are trained around, exactly as the app does it
(app/src/main/java/com/example/myapplication/player/restore/): frames, bands, the cutoff a lossy
codec leaves and the band copied up over it. Kept in step with the Kotlin by hand; the tests in
app/src/test check both against the same numbers.
"""
import numpy as np

N = 1024          # frame
H = 256           # hop: a quarter, so a shift by a multiple of 4 bins keeps its phase
EPS = 1e-10


def edges(step):
    """Band edges in Hz: coarse below 4 kHz, every [step] Hz above, to 22.05 kHz."""
    e = [0, 250, 500, 1000, 1500, 2000, 3000]
    f = 4000
    while f < 22050 - step / 2:
        e.append(f)
        f += step
    e.append(22050)
    return np.array(e, dtype=np.float64)


LIGHT_EDGES = edges(500)
STRONG_EDGES = edges(250)


def window():
    # Periodic Hann's square root, for analysis and synthesis alike: their product, a Hann,
    # overlaps at a quarter frame to a constant 2.
    n = np.arange(N)
    return np.sqrt(0.5 - 0.5 * np.cos(2 * np.pi * n / N))


def stft(x):
    """x: (samples,) → (frames, N/2+1) complex. Frame t covers [t*H, t*H+N)."""
    w = window()
    frames = 1 + (len(x) - N) // H
    idx = np.arange(N)[None, :] + H * np.arange(frames)[:, None]
    return np.fft.rfft(x[idx] * w, axis=1)


def istft(spec, length):
    w = window()
    out = np.zeros(length + N)
    frames = np.fft.irfft(spec, n=N, axis=1) * w
    for t in range(spec.shape[0]):
        out[t * H:t * H + N] += frames[t]
    return out[:length] * 0.5


def band_bins(edges_hz, rate):
    """Per band, its first bin and the one past its last."""
    hz = rate / N
    lo = np.ceil(edges_hz[:-1] / hz).astype(int)
    hi = np.ceil(edges_hz[1:] / hz).astype(int)
    hi[-1] = min(hi[-1], N // 2 + 1)
    return lo, hi


def band_db(power, edges_hz, rate, from_bin=0):
    """Mean power of each band's bins (those at or past [from_bin]), in dB. power: (frames, bins)."""
    lo, hi = band_bins(edges_hz, rate)
    out = np.full((power.shape[0], len(lo)), np.nan)
    for b in range(len(lo)):
        a = max(lo[b], from_bin)
        if a < hi[b]:
            out[:, b] = 10 * np.log10(power[:, a:hi[b]].mean(axis=1) + EPS)
    return out


# The cutoff: the steepest fall of the long-term spectrum, if it is a cliff the sound never climbs
# back over. A codec's low-pass drops 40 dB and more within a few hundred hertz; music by itself
# fades a few dB over as many.
DETECT_SPAN = 8            # bins either side of the edge compared (~350 Hz)
DETECT_CLIFF_DB = 25.0     # how much lower it is just past the edge than just before
DETECT_STAYS_DB = 15.0     # nothing above the edge comes back within this of the level before it
LOW_QUALITY_HZ = 18000.0   # a cutoff lower than this is a lossy file's, and worth restoring


def detect_cutoff(power_db_long, rate):
    """
    power_db_long: the long-term mean power per bin, in dB. Returns the cutoff in Hz, or None when
    the sound reaches the top (or there is too little of it to tell).
    """
    hz = rate / N
    s = power_db_long
    ref = s[int(2000 / hz):int(8000 / hz)].mean()
    if ref < -90:
        return None
    top = min(int(22050 / hz), len(s) - 1)
    span = DETECT_SPAN
    best, edge = -1e9, -1
    for k in range(int(6000 / hz), top - span - 2):
        drop = s[k - span:k].mean() - s[k + 2:k + 2 + span].mean()
        if drop > best:
            best, edge = drop, k
    if best < DETECT_CLIFF_DB:
        return None
    before = s[edge - span:edge].mean()
    if s[edge + 2:top].max() > before - DETECT_STAYS_DB:
        return None
    k = edge - span
    while k < edge + 2 and s[k] >= before - 10:
        k += 1
    return k * hz


# A codec's low-pass doesn't stop dead at the cutoff: the half kilohertz under it is already
# fading. The copy starts that far under it, rising over it, and is copied from under it too:
# a multiple of 4 bins, so the shift still keeps the phase.
FILL = 12


def patch_plan(cutoff_bin, rate):
    """The band copied up: its width W (a multiple of 4 bins), and the bin the copy starts at."""
    hz = rate / N
    start = cutoff_bin - FILL
    w = min(start // 2, int(5000 / hz))
    w -= w % 4
    return w, start


def source(k, start, w):
    """The bins [k] (at or over [start]) are copied from: a band's width at a time, down under it."""
    return k - w * (1 + (k - start) // w)


def anchor_db(power, cutoff_bin, rate):
    """The level of the 2 kHz just under the cutoff, per frame: what the bands over it are measured from."""
    hz = rate / N
    a = max(1, cutoff_bin - int(2000 / hz))
    return 10 * np.log10(power[:, a:cutoff_bin].mean(axis=1) + EPS)


def features(power, cutoff_hz, edges_hz, rate):
    """The model's input per frame: each band under the cutoff against the anchor, which bands those
    are, the anchor's own level and where the cutoff is."""
    hz = rate / N
    kc = int(round(cutoff_hz / hz))
    lo, hi = band_bins(edges_hz, rate)
    valid = (hi <= kc).astype(np.float32)
    db = band_db(power, edges_hz, rate)
    a = anchor_db(power, kc, rate)
    rel = np.where(valid[None, :] > 0, (db - a[:, None]) / 20.0, 0.0)
    rel = np.clip(rel, -4, 4)
    level = np.clip((a + 50.0) / 30.0, -3, 3)
    x = np.concatenate([
        rel,
        np.broadcast_to(valid, rel.shape),
        level[:, None],
        np.full((rel.shape[0], 1), cutoff_hz / 22050.0),
    ], axis=1)
    return x.astype(np.float32), a, kc


def targets(power_orig, anchor, kc, edges_hz, rate):
    """What the bands over the cutoff had, against the anchor; NaN where nothing is to be made."""
    db = band_db(power_orig, edges_hz, rate, from_bin=kc)
    return np.clip((db - anchor[:, None]) / 20.0, -5, 2).astype(np.float32)


def prior(edges_hz, cutoff_hz, at_cutoff_db, per_octave_db):
    """The fixed fall-off over the cutoff, per band, in the model's units (20 dB over the anchor)."""
    centers = (edges_hz[:-1] + edges_hz[1:]) / 2
    octaves = np.log2(np.maximum(centers, cutoff_hz + 1) / cutoff_hz)
    return (at_cutoff_db + per_octave_db * octaves) / 20


def blend(pred, edges_hz, cutoff_hz, header):
    """The model's answer half and half with the fixed fall-off its file carries: on songs neither
    heard, the two together come closer than either alone."""
    p = header.get("prior")
    if not p:
        return pred
    w = p["weight"]
    return w * pred + (1 - w) * prior(edges_hz, cutoff_hz, p["atCutoffDb"], p["perOctaveDb"])


NOISE_LOW = 0.1
NOISE_HIGH = 0.4
MAX_BOOST_DB = 24.0


def frame_gains(mid_spec, kc, pred_row, anchor, edges_hz, rate, previous=None):
    """
    One frame's gains in dB for the bins from the cutoff up: what each band should be ([pred_row],
    20 dB units over [anchor]) against what the copy of the mid brings, the same over the whole
    band (bins past the last band hold its gain), eased from the frame before ([previous]: attack
    0.6, release 0.25).
    """
    hz = rate / N
    bins = N // 2 + 1
    top = min(bins, int(22050 / hz) + 1)
    w, start = patch_plan(kc, rate)
    lo, hi = band_bins(edges_hz, rate)
    k = np.arange(kc, top)
    src = source(k, start, w)
    p_mid = np.abs(mid_spec[src]) ** 2
    g = np.full(len(k), np.nan)
    for b in range(len(lo)):
        a, z = max(lo[b], kc), min(hi[b], top)
        if a >= z or np.isnan(pred_row[b]):
            continue
        have = 10 * np.log10(p_mid[a - kc:z - kc].mean() + EPS)
        want = anchor + 20 * pred_row[b]
        g[a - kc:z - kc] = np.clip(want - have, -60, MAX_BOOST_DB)
    if np.isnan(g).all():
        return previous
    for i in range(1, len(g)):
        if np.isnan(g[i]):
            g[i] = g[i - 1]
    first = np.argmax(~np.isnan(g))
    g[:first] = g[first]
    if previous is not None:
        g = np.where(g > previous, previous + 0.6 * (g - previous), previous + 0.25 * (g - previous))
    return g


def synthesize(specs, kc, pred, anchor, edges_hz, rate, rng=None):
    """
    The band over the cutoff, made from the one under it: copied up in steps of its width, each band
    then set to the level [pred] says (20 dB units over [anchor]), the same gain on every channel,
    a little noise in its phase; under the cutoff, over the codec's fade, rising to it. specs: list
    of (frames, bins) complex, one per channel. Returns the same shape: only what is made.
    """
    rng = rng or np.random.default_rng(0)
    hz = rate / N
    bins = N // 2 + 1
    top = min(bins, int(22050 / hz) + 1)
    w, start = patch_plan(kc, rate)
    out = [np.zeros_like(s) for s in specs]
    if w < 8:
        return out
    k = np.arange(start, top)
    src = source(k, start, w)
    mid = sum(specs) / len(specs)
    noise_mix = NOISE_LOW + (NOISE_HIGH - NOISE_LOW) * np.clip(k - kc, 0, None) / max(1, top - kc)
    ramp = np.clip((k - start + 1) / (FILL + 1), 0, 1)
    gains = None
    for t in range(specs[0].shape[0]):
        gains = frame_gains(mid[t], kc, pred[t], anchor[t], edges_hz, rate, gains)
        if gains is None:
            continue
        # Under the cutoff, the gain at it.
        full = np.concatenate([np.full(kc - start, gains[0]), gains])
        # Noise in the phase takes some of the energy with it: given back.
        g = 10 ** (full / 20) / np.sqrt((1 - noise_mix) ** 2 + noise_mix ** 2)
        phase = np.exp(2j * np.pi * rng.random(len(k)))
        for c, s in enumerate(specs):
            p = s[t, src]
            mixed = (1 - noise_mix) * p + noise_mix * np.abs(p) * phase
            out[c][t, start:top] = mixed * g * ramp
    return out

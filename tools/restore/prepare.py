"""
Training pairs for the restoration models: lossless music (MUSDB18-HQ's mixtures, and remixes of
their stems at random levels), made lossy the way the services' streams are — MP3 and AAC at
96–160 kbps through ffmpeg, and plain low-passes — and, frame by frame, what the model sees of the
lossy one against what the lossless one had over its cutoff.

    python prepare.py <dir of musdb18HQ parquet shards> <out dir>
"""
import io
import os
import subprocess
import sys
import tempfile
from concurrent.futures import ProcessPoolExecutor

import numpy as np
import pyarrow.parquet as pq
import soundfile as sf

import dsp

RATE = 44100
VALIDATION = {"Music Delta - Gospel", "Dark Ride - Burning Bridges"}
CODECS = [
    ("mp3", "96k"), ("mp3", "128k"), ("mp3", "160k"),
    ("aac", "96k"), ("aac", "128k"),
]


def songs_in(shards):
    """Each whole song in the shards (its mixture and four stems), one at a time: all of them at
    once do not fit in memory."""
    rows = {}
    for f in shards:
        t = pq.read_table(f, columns=["path", "instrument"])
        for i, (path, inst) in enumerate(zip(t.column("path").to_pylist(), t.column("instrument").to_pylist())):
            rows.setdefault(path.split("/")[-2], {})[inst] = (f, i)
    for song, parts in sorted(rows.items()):
        if not {"mixture", "vocals", "drums", "bass", "other"} <= parts.keys():
            continue
        stems = {}
        for inst, (f, i) in parts.items():
            audio = pq.read_table(f, columns=["audio"]).column("audio")[i].as_py()
            data, rate = sf.read(io.BytesIO(audio["bytes"]), dtype="float32", always_2d=True)
            assert rate == RATE
            stems[inst] = data
        yield song, stems


def remix(stems, rng):
    parts = []
    for name in ("vocals", "drums", "bass", "other"):
        if rng.random() < 0.2:
            continue
        parts.append(stems[name] * 10 ** (rng.uniform(-10, 6) / 20))
    if not parts:
        parts = [stems["drums"]]
    mix = sum(parts)
    return (mix / max(1e-3, np.abs(mix).max()) * rng.uniform(0.5, 0.98)).astype(np.float32)


def codec(x, kind, bitrate):
    with tempfile.TemporaryDirectory() as d:
        src, enc, dec = os.path.join(d, "a.wav"), os.path.join(d, "b." + ("mp3" if kind == "mp3" else "m4a")), os.path.join(d, "c.wav")
        sf.write(src, x, RATE, subtype="FLOAT")
        args = ["-c:a", "libmp3lame"] if kind == "mp3" else ["-c:a", "aac"]
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src, *args, "-b:a", bitrate, enc], check=True)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", enc, "-c:a", "pcm_f32le", dec], check=True)
        y, _ = sf.read(dec, dtype="float32", always_2d=True)
    return align(x, y)


def lowpass(x, cutoff):
    """A brick wall at [cutoff], a channel at a time, over a length the FFT is fast at: a song's own
    length is often all but prime, and numpy's FFT then takes gigabytes."""
    import scipy.fft
    n = scipy.fft.next_fast_len(x.shape[0], real=True)
    out = np.empty_like(x)
    f = np.fft.rfftfreq(n, 1 / RATE)
    for c in range(x.shape[1]):
        spec = scipy.fft.rfft(x[:, c], n=n)
        spec[f >= cutoff] = 0
        out[:, c] = scipy.fft.irfft(spec, n=n)[:x.shape[0]]
    return out


def align(x, y):
    """The decoded file starts where the original does: a codec's delay, if any, taken off."""
    n = min(len(x), len(y), RATE * 20)
    a, b = x[RATE:n, 0], y[RATE:n, 0]
    best, lag = -1, 0
    for d in range(-2048, 2049, 1):
        if d >= 0:
            c = np.dot(a[:len(a) - 2048], b[d:d + len(a) - 2048])
        else:
            c = np.dot(a[-d:-d + len(a) - 2048], b[:len(a) - 2048])
        if c > best:
            best, lag = c, d
    if lag > 0:
        y = y[lag:]
    elif lag < 0:
        y = np.concatenate([np.zeros((-lag, y.shape[1]), np.float32), y])
    m = min(len(x), len(y))
    return y[:m]


def power_mid(x):
    """The mid's power per frame and bin, worked out a block of frames at a time: a whole song's
    spectrum at once, in double precision, is gigabytes."""
    mid = x.mean(axis=1)
    frames = 1 + (len(mid) - dsp.N) // dsp.H
    out = np.empty((frames, dsp.N // 2 + 1), np.float32)
    block = 4096
    for f0 in range(0, frames, block):
        f1 = min(frames, f0 + block)
        s = dsp.stft(mid[f0 * dsp.H:(f1 - 1) * dsp.H + dsp.N])
        out[f0:f1] = s.real ** 2 + s.imag ** 2
    return out


def job(args):
    name, version, x, kind, setting, out = args
    tag = f"lp{int(setting)}" if kind == "lowpass" else f"{kind}{setting}"
    done = os.path.join(out, f"{name}__{version}__{tag}.npz")
    if os.path.exists(done) and (name not in VALIDATION_SAFE or os.path.exists(done[:-4] + ".wav")):
        return f"{name}/{version}/{tag}: made before"
    if kind == "lowpass":
        y = lowpass(x, setting)
        tag = f"lp{int(setting)}"
    else:
        y = codec(x, kind, setting)
        tag = f"{kind}{setting}"
    x = x[:len(y)]
    p_orig, p_deg = power_mid(x), power_mid(y)
    long_db = 10 * np.log10(p_deg.mean(axis=0) + dsp.EPS)
    cutoff = dsp.detect_cutoff(long_db, RATE)
    if cutoff is None or cutoff >= dsp.LOW_QUALITY_HZ:
        return f"{name}/{version}/{tag}: cutoff {cutoff}, skipped"
    record = {"cutoff": np.float32(cutoff)}
    for grid, edges in (("light", dsp.LIGHT_EDGES), ("strong", dsp.STRONG_EDGES)):
        feats, anchor, kc = dsp.features(p_deg, cutoff, edges, RATE)
        record[f"x_{grid}"] = feats
        record[f"y_{grid}"] = dsp.targets(p_orig, anchor, kc, edges, RATE)
    # Silence teaches nothing: frames under -70 dB at the anchor are left out.
    keep = 10 * np.log10(p_deg.mean(axis=1) + dsp.EPS) > -70
    record = {k: (v[keep] if getattr(v, "ndim", 0) == 2 else v) for k, v in record.items()}
    np.savez_compressed(os.path.join(out, f"{name}__{version}__{tag}.npz"), **record)
    if name in VALIDATION_SAFE:
        sf.write(os.path.join(out, f"{name}__{version}__{tag}.wav"), y, RATE, subtype="FLOAT")
        sf.write(os.path.join(out, f"{name}__{version}__orig.wav"), x, RATE, subtype="FLOAT")
    return f"{name}/{version}/{tag}: cutoff {cutoff:.0f} Hz, {keep.sum()} frames"


VALIDATION_SAFE = {s.replace(" ", "_").replace("'", "") for s in VALIDATION}


def main():
    shards, out = sys.argv[1], sys.argv[2]
    os.makedirs(os.path.join(out, "train"), exist_ok=True)
    os.makedirs(os.path.join(out, "val"), exist_ok=True)
    rng = np.random.default_rng(7)
    shard_files = sorted(os.path.join(shards, f) for f in os.listdir(shards) if f.endswith(".parquet"))
    with ProcessPoolExecutor(2) as pool:
        for song, stems in songs_in(shard_files):
            safe = song.replace(" ", "_").replace("'", "")
            val = song in VALIDATION
            versions = [("mix", stems["mixture"])]
            if not val:
                versions += [(f"remix{i}", remix(stems, rng)) for i in range(2)]
            del stems
            jobs = []
            for version, x in versions:
                dest = os.path.join(out, "val" if val else "train")
                for kind, br in CODECS:
                    jobs.append((safe, version, x, kind, br, dest))
                jobs.append((safe, version, x, "lowpass", float(rng.uniform(11000, 17500)), dest))
            for line in pool.map(job, jobs):
                print(line, flush=True)


if __name__ == "__main__":
    main()

"""
How close each way of filling in the top comes to the lossless original, on songs no model trained
on: the band over the cutoff made through the whole chain (the copy, the gains, the easing), then
compared with what the original had there.

    python evaluate.py <pairs dir> <light .ycrm> <strong .ycrm> [<picture .png>]

Two numbers each: how far off the bands' loudness is (dB, what the models predict), and the
log-spectral distance over the top (dB, every bin, fine structure too). "As it is" is the lossy file
untouched — what is heard with restoration off.
"""
import glob
import os
import sys

import numpy as np
import soundfile as sf
import torch

import dsp
from vectors import read_model

SECONDS = 60
RATE = 44100


def tilt_predictor(coef):
    def predict(feats, edges):
        cutoff = float(feats[0, -1]) * 22050
        centers = (edges[:-1] + edges[1:]) / 2
        octs = np.log2(np.maximum(centers, cutoff + 1) / cutoff)
        return np.broadcast_to((coef[0] + coef[1] * octs) / 20, (len(feats), len(centers))).copy()
    return predict


def model_predictor(net, header):
    """The model's answer, blended with the fixed fall-off its file carries, as the app does."""
    def predict(feats, edges):
        with torch.no_grad():
            out, _ = net(torch.from_numpy(feats)[None])
        return dsp.blend(out[0].numpy(), edges, float(feats[0, -1]) * 22050, header)
    return predict


def restore(y, cutoff, edges, predict):
    specs = [dsp.stft(y[:, c]) for c in range(y.shape[1])]
    mid = sum(specs) / len(specs)
    power = np.abs(mid) ** 2
    feats, anchor, kc = dsp.features(power, cutoff, edges, RATE)
    pred = predict(feats, edges)
    made = dsp.synthesize(specs, kc, pred, anchor, edges, RATE)
    hf = np.stack([dsp.istft(m, len(y)) for m in made], axis=1)
    return y + hf, kc


def scores(x, out, kc):
    """Band loudness error and log-spectral distance over the top, on frames with sound."""
    po = np.abs(dsp.stft(x.mean(axis=1))) ** 2
    pr = np.abs(dsp.stft(out.mean(axis=1))) ** 2
    top = int(20000 / (RATE / dsp.N))
    keep = 10 * np.log10(po.mean(axis=1) + dsp.EPS) > -60
    po, pr = po[keep], pr[keep]
    bo = dsp.band_db(po, dsp.LIGHT_EDGES, RATE, from_bin=kc)
    br = dsp.band_db(pr, dsp.LIGHT_EDGES, RATE, from_bin=kc)
    m = ~np.isnan(bo)
    band = np.abs(np.clip(br[m], -100, None) - np.clip(bo[m], -100, None)).mean()
    lo = 10 * np.log10(po[:, kc:top] + 1e-7)
    lr = 10 * np.log10(pr[:, kc:top] + 1e-7)
    lsd = np.sqrt(((lo - lr) ** 2).mean(axis=1)).mean()
    return band, lsd


def main():
    pairs, light_path, strong_path = sys.argv[1:4]
    picture = sys.argv[4] if len(sys.argv) > 4 else None
    light, light_header = read_model(light_path)
    strong, strong_header = read_model(strong_path)
    # The fixed fall-off, fitted as train.py fits it.
    from train import load, tilt_baseline
    xs, ys = load(os.path.join(pairs, "train"), "light", limit=3_000_000)
    coef = tilt_baseline(xs, ys, dsp.LIGHT_EDGES)
    del xs, ys
    ways = [
        ("as it is", None, None),
        ("fixed fall-off (DSP)", dsp.LIGHT_EDGES, tilt_predictor(coef)),
        ("light model", dsp.LIGHT_EDGES, model_predictor(light, light_header)),
        ("strong model", dsp.STRONG_EDGES, model_predictor(strong, strong_header)),
    ]
    totals = {name: [] for name, _, _ in ways}
    shown = None
    for path in sorted(glob.glob(os.path.join(pairs, "val", "*.wav"))):
        if path.endswith("orig.wav"):
            continue
        name = os.path.basename(path)[:-4]
        song, version, tag = name.split("__")
        x, _ = sf.read(os.path.join(pairs, "val", f"{song}__{version}__orig.wav"), dtype="float32", always_2d=True)
        y, _ = sf.read(path, dtype="float32", always_2d=True)
        start = min(len(y) // 3, max(0, len(y) - SECONDS * RATE))
        x, y = x[start:start + SECONDS * RATE], y[start:start + SECONDS * RATE]
        long_db = 10 * np.log10((np.abs(dsp.stft(y.mean(axis=1))) ** 2).mean(axis=0) + dsp.EPS)
        cutoff = dsp.detect_cutoff(long_db, RATE)
        if cutoff is None:
            continue
        line = [f"{song[:24]:24} {tag:8} {cutoff / 1000:5.1f} kHz"]
        outs = {}
        for way, edges, predict in ways:
            kc = int(round(cutoff / (RATE / dsp.N)))
            out = y if predict is None else restore(y, cutoff, edges, predict)[0]
            outs[way] = out
            band, lsd = scores(x, out, kc)
            totals[way].append((band, lsd))
            line.append(f"{band:5.1f}/{lsd:5.1f}")
        print("  ".join(line), flush=True)
        if shown is None and tag == "mp3128k":
            shown = (song, x, y, outs, cutoff)
    print()
    for way, _, _ in ways:
        values = np.array(totals[way])
        print(f"{way:22} band error {values[:, 0].mean():5.2f} dB   log-spectral distance {values[:, 1].mean():5.2f} dB")
    if picture and shown:
        draw(picture, *shown)


def draw(path, song, x, y, outs, cutoff):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    seg = slice(10 * RATE, 16 * RATE)
    panels = [("Lossless original", x), ("MP3 128 kbps (as streamed)", y),
              ("Light model", outs["light model"]), ("Strong model", outs["strong model"])]
    fig, axes = plt.subplots(1, 4, figsize=(16, 4.2), sharey=True)
    for ax, (title, sig) in zip(axes, panels):
        p = np.abs(dsp.stft(sig[seg].mean(axis=1))) ** 2
        db = 10 * np.log10(p.T + 1e-10)
        ax.imshow(db, origin="lower", aspect="auto", vmin=db.max() - 90, vmax=db.max(),
                  extent=[0, 6, 0, RATE / 2000], cmap="magma")
        ax.set_title(title)
        ax.set_xlabel("s")
        ax.axhline(cutoff / 1000, color="w", lw=0.6, ls="--")
    axes[0].set_ylabel("kHz")
    fig.suptitle(f"{song} — held-out song, cutoff {cutoff / 1000:.1f} kHz")
    fig.tight_layout()
    fig.savefig(path, dpi=110)


if __name__ == "__main__":
    main()

"""
Trains a restoration model on the pairs prepare.py made, and writes it as the app reads it.

    python train.py <pairs dir> light|strong <out .bin>

Both are a GRU over frames: every 5.8 ms it reads the bands under the cutoff and says, for each
band over it, how loud it was in the lossless original, against the 2 kHz just under the cutoff.
The light one: 43 bands, 96 units, one layer — about 68 thousand weights. The strong one: 79
bands (250 Hz wide over 4 kHz), 192 units, two layers — about half a million.
"""
import glob
import json
import os
import struct
import sys
import time

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

import dsp

CONFIGS = {
    "light": dict(edges=dsp.LIGHT_EDGES, hidden=96, layers=1, epochs=24),
    "strong": dict(edges=dsp.STRONG_EDGES, hidden=192, layers=2, epochs=24),
}
SEQ = 192


class Net(nn.Module):
    def __init__(self, bands, hidden, layers):
        super().__init__()
        self.inp = nn.Linear(2 * bands + 2, hidden)
        self.gru = nn.GRU(hidden, hidden, num_layers=layers, batch_first=True)
        self.out = nn.Linear(hidden, bands)

    def forward(self, x, h=None):
        y, h = self.gru(torch.tanh(self.inp(x)), h)
        return self.out(y), h


# At most this many frames are kept, in pieces from every file: all of it is more than memory holds.
MAX_FRAMES = 8_000_000
PIECE = 4096


def load(dirname, grid, limit=None):
    # Half precision in memory, and, past [limit] frames, pieces of each file at random.
    files = sorted(glob.glob(os.path.join(dirname, "*.npz")))
    rng = np.random.default_rng(11)
    sizes = []
    for f in files:
        with np.load(f) as d:
            sizes.append(d[f"y_{grid}"].shape[0])
    keep = 1.0 if limit is None else min(1.0, limit / max(1, sum(sizes)))
    xs, ys = [], []
    for f, n in zip(files, sizes):
        d = np.load(f)
        x, y = d[f"x_{grid}"], d[f"y_{grid}"]
        if keep < 1.0:
            starts = np.arange(0, max(1, n - PIECE), PIECE)
            chosen = rng.choice(starts, size=max(1, int(round(len(starts) * keep))), replace=False)
            for s0 in sorted(chosen):
                xs.append(x[s0:s0 + PIECE].astype(np.float16))
                ys.append(y[s0:s0 + PIECE].astype(np.float16))
        else:
            xs.append(x.astype(np.float16))
            ys.append(y.astype(np.float16))
    return xs, ys


BATCHES_PER_EPOCH = 600


def batches(xs, ys, batch, rng):
    """An epoch: windows of [SEQ] frames at random places in random files."""
    sizes = np.array([len(x) for x in xs], dtype=np.float64)
    pick = sizes / sizes.sum()
    for _ in range(BATCHES_PER_EPOCH):
        files = rng.choice(len(xs), size=batch, p=pick)
        starts = [rng.integers(0, len(xs[i]) - SEQ) for i in files]
        x = np.stack([xs[i][s:s + SEQ] for i, s in zip(files, starts)]).astype(np.float32)
        y = np.stack([ys[i][s:s + SEQ] for i, s in zip(files, starts)]).astype(np.float32)
        yield torch.from_numpy(x), torch.from_numpy(y)


def masked_loss(pred, y):
    mask = ~torch.isnan(y)
    target = torch.nan_to_num(y)
    err = F.smooth_l1_loss(pred, target, reduction="none", beta=0.25)
    return (err * mask).sum() / mask.sum().clamp(min=1)


@torch.no_grad()
def evaluate(net, xs, ys):
    total, count = 0.0, 0
    for x, y in zip(xs, ys):
        pred, _ = net(torch.from_numpy(x.astype(np.float32))[None])
        pred = pred[0].numpy()
        y = y.astype(np.float32)
        m = ~np.isnan(y)
        total += np.abs(pred[m] - y[m]).sum() * 20
        count += m.sum()
    return total / max(1, count)


def tilt_baseline(xs, ys, edges):
    """The best a fixed fall-off can do: dB under the anchor as a line in octaves over the cutoff,
    fitted to the training data — what a plain DSP band replication would set."""
    lo, hi = edges[:-1], edges[1:]
    centers = (lo + hi) / 2
    rows, vals = [], []
    for x, y in zip(xs, ys):
        cutoff = float(x[0, -1]) * 22050
        octs = np.log2(np.maximum(centers, cutoff + 1) / cutoff)
        # Every eighth frame: plenty for a line, and the whole of it is gigabytes.
        y = y[::8].astype(np.float32)
        m = ~np.isnan(y)
        rows.append(np.broadcast_to(octs, y.shape)[m].astype(np.float32))
        vals.append(y[m] * 20)
    o, v = np.concatenate(rows), np.concatenate(vals)
    a = np.stack([np.ones_like(o), o], axis=1)
    coef, *_ = np.linalg.lstsq(a, v, rcond=None)
    return coef


def tilt_error(coef, xs, ys, edges):
    centers = (edges[:-1] + edges[1:]) / 2
    total, count = 0.0, 0
    for x, y in zip(xs, ys):
        cutoff = float(x[0, -1]) * 22050
        octs = np.log2(np.maximum(centers, cutoff + 1) / cutoff)
        pred = coef[0] + coef[1] * octs
        y = y.astype(np.float32)
        m = ~np.isnan(y)
        total += np.abs(np.broadcast_to(pred, y.shape)[m] - y[m] * 20).sum()
        count += m.sum()
    return total / count


def export(net, cfg, name, path, metrics, coef=None):
    """
    YCRM v1: "YCRM", version, header length, a JSON header (the bands, the shape of the network,
    the tensors in order) and the tensors as little-endian float32, in that order.
    """
    sd = {k: v.detach().numpy().astype("<f4") for k, v in net.state_dict().items()}
    names = ["inp.weight", "inp.bias"]
    for layer in range(cfg["layers"]):
        names += [f"gru.weight_ih_l{layer}", f"gru.weight_hh_l{layer}", f"gru.bias_ih_l{layer}", f"gru.bias_hh_l{layer}"]
    names += ["out.weight", "out.bias"]
    header = {
        "name": name,
        "edges": [float(e) for e in cfg["edges"]],
        "hidden": cfg["hidden"],
        "layers": cfg["layers"],
        "frame": dsp.N,
        "hop": dsp.H,
        "tensors": [{"name": n, "shape": list(sd[n].shape)} for n in names],
        "metrics": metrics,
    }
    if coef is not None:
        # The fixed fall-off, blended half and half with the model's answer (see dsp.blend).
        header["prior"] = {"atCutoffDb": float(coef[0]), "perOctaveDb": float(coef[1]), "weight": 0.5}
    blob = json.dumps(header).encode()
    with open(path, "wb") as f:
        f.write(b"YCRM")
        f.write(struct.pack("<ii", 1, len(blob)))
        f.write(blob)
        for n in names:
            f.write(sd[n].tobytes())


def main():
    pairs, name, out = sys.argv[1], sys.argv[2], sys.argv[3]
    cfg = CONFIGS[name]
    torch.manual_seed(1)
    torch.set_num_threads(4)
    rng = np.random.default_rng(1)
    xs, ys = load(os.path.join(pairs, "train"), name, limit=MAX_FRAMES)
    vx, vy = load(os.path.join(pairs, "val"), name)
    bands = len(cfg["edges"]) - 1
    print(f"{name}: {sum(map(len, xs))} training frames in {len(xs)} files, {sum(map(len, vx))} to check against")
    coef = tilt_baseline(xs, ys, cfg["edges"])
    base = tilt_error(coef, vx, vy, cfg["edges"])
    print(f"fixed fall-off: {coef[0]:.1f} dB at the cutoff, {coef[1]:.1f} dB an octave → {base:.2f} dB off on held-out songs")
    net = Net(bands, cfg["hidden"], cfg["layers"])
    print("weights:", sum(p.numel() for p in net.parameters()))
    opt = torch.optim.AdamW(net.parameters(), lr=2e-3, weight_decay=1e-4)
    steps = cfg["epochs"] * BATCHES_PER_EPOCH
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=2e-3, total_steps=steps + 10, pct_start=0.1)
    best, best_state = 1e9, None
    for epoch in range(cfg["epochs"]):
        t0 = time.time()
        net.train()
        losses = []
        for x, y in batches(xs, ys, 32, rng):
            pred, _ = net(x)
            loss = masked_loss(pred, y)
            opt.zero_grad()
            loss.backward()
            nn.utils.clip_grad_norm_(net.parameters(), 1.0)
            opt.step()
            sched.step()
            losses.append(loss.item())
        net.eval()
        err = evaluate(net, vx, vy)
        if err < best:
            best, best_state = err, {k: v.clone() for k, v in net.state_dict().items()}
        print(f"epoch {epoch + 1}: train {np.mean(losses):.4f}, held-out {err:.2f} dB ({time.time() - t0:.0f} s)", flush=True)
    net.load_state_dict(best_state)
    metrics = {"heldOutBandErrorDb": round(float(best), 2), "fixedFallOffErrorDb": round(float(base), 2)}
    export(net, cfg, name, out, metrics, coef)
    print("written", out, os.path.getsize(out), "bytes", metrics)


if __name__ == "__main__":
    main()

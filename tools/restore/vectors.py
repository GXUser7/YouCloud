"""
Numbers the app's Kotlin must reproduce (RestoreDspTest): a 128 kbps MP3's long-term spectrum and
its cutoff, one frame's model input, the light model's answer to it, and the gains that follow.

    python vectors.py <light .ycrm> <out .json>
"""
import json
import struct
import sys

import numpy as np
import torch

import dsp
import prepare
from train import Net


def read_model(path):
    with open(path, "rb") as f:
        assert f.read(4) == b"YCRM"
        version, length = struct.unpack("<ii", f.read(8))
        header = json.loads(f.read(length))
        net = Net(len(header["edges"]) - 1, header["hidden"], header["layers"])
        state = {}
        for t in header["tensors"]:
            count = int(np.prod(t["shape"]))
            state[t["name"]] = torch.from_numpy(np.frombuffer(f.read(4 * count), dtype="<f4").reshape(t["shape"]).copy())
        net.load_state_dict(state)
        return net.eval(), header


def main():
    net, header = read_model(sys.argv[1])
    edges = np.array(header["edges"])
    rng = np.random.default_rng(3)
    rate = 44100
    x = (rng.standard_normal((rate * 12, 2)) * 0.1).astype(np.float32)
    spec = np.fft.rfft(x, axis=0)
    f = np.fft.rfftfreq(len(x), 1 / rate)
    spec /= np.sqrt(np.maximum(f, 20))[:, None]
    x = np.fft.irfft(spec, n=len(x), axis=0).astype(np.float32)
    x /= np.abs(x).max() * 1.2
    y = prepare.codec(x, "mp3", "128k")
    mid = y.mean(axis=1)
    s = dsp.stft(mid)
    power = np.abs(s) ** 2
    long_db = 10 * np.log10(power.mean(axis=0) + dsp.EPS)
    cutoff = dsp.detect_cutoff(long_db, rate)
    t = 300
    feats, anchor, kc = dsp.features(power[t:t + 1], cutoff, edges, rate)
    with torch.no_grad():
        out, _ = net(torch.from_numpy(feats)[None])
    pred = out[0, 0].numpy()
    gains = dsp.frame_gains(s[t], kc, pred, anchor[0], edges, rate)
    json.dump({
        "rate": rate,
        "longDb": long_db.tolist(),
        "cutoff": cutoff,
        "power": power[t].tolist(),
        "midRe": s[t].real.tolist(),
        "midIm": s[t].imag.tolist(),
        "features": feats[0].tolist(),
        "anchor": float(anchor[0]),
        "modelOut": pred.tolist(),
        "gains": gains.tolist(),
    }, open(sys.argv[2], "w"))
    print("cutoff", cutoff, "kc", kc, "anchor", anchor[0])


if __name__ == "__main__":
    main()

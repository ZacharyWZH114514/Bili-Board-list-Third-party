import sys, os, glob
import numpy as np
from PIL import Image


def content_bbox(arr, thresh=40.0):
    h, w, _ = arr.shape
    corners = np.concatenate([
        arr[:40, :40, :].reshape(-1, 3),
        arr[:40, -40:, :].reshape(-1, 3),
        arr[-40:, :40, :].reshape(-1, 3),
        arr[-40:, -40:, :].reshape(-1, 3),
    ], axis=0)
    bgm = corners.mean(axis=0)
    d = np.abs(arr - bgm).sum(axis=2)
    ys = np.where(d.max(axis=1) > thresh)[0]
    xs = np.where(d.max(axis=0) > thresh)[0]
    if len(ys) == 0 or len(xs) == 0:
        return 0, 0, w - 1, h - 1
    return int(xs[0]), int(ys[0]), int(xs[-1]), int(ys[-1])


def run(src, outdir, pad=2):
    os.makedirs(outdir, exist_ok=True)
    n = 0
    for path in sorted(glob.glob(os.path.join(src, "*.png"))):
        im = Image.open(path).convert("RGB")
        arr = np.asarray(im).astype(np.float32)
        h, w, _ = arr.shape
        x0, y0, x1, y1 = content_bbox(arr)
        x0 = max(0, x0 - pad); y0 = max(0, y0 - pad)
        x1 = min(w - 1, x1 + pad); y1 = min(h - 1, y1 + pad)
        out = os.path.join(outdir, os.path.basename(path))
        im.crop((x0, y0, x1 + 1, y1 + 1)).save(out)
        n += 1
        print("  %-28s %dx%d" % (os.path.basename(path), x1 - x0 + 1, y1 - y0 + 1))
    print("total %d" % n)


if __name__ == "__main__":
    run(sys.argv[1], sys.argv[2])

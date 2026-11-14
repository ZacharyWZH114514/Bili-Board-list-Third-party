import sys
import numpy as np
from PIL import Image

# 诊断 4：卡片是内嵌的，左边缘固定在 x≈30。
# 只在 x 18..48 这个窄带里找竖向边缘：卡片行有边缘，卡片间隙没有。

def edge_signal(arr, xl=(18, 48), xr=(1032, 1062)):
    def s(rng):
        a = arr[:, rng[0]:rng[1], :]
        return np.abs(np.diff(a, axis=1)).sum(axis=2).max(axis=1)
    return np.maximum(s(xl), s(xr))

def bands(mask, min_h=60, gap=4):
    out, start, last = [], None, None
    for i, v in enumerate(mask):
        if v:
            if start is None: start = i
            last = i
        else:
            if start is not None and i - last > gap:
                if last - start + 1 >= min_h: out.append((start, last))
                start = None
    if start is not None and last - start + 1 >= min_h:
        out.append((start, last))
    return out

path = sys.argv[1]
arr = np.asarray(Image.open(path).convert("RGB")).astype(np.float32)
h, w, _ = arr.shape
sig = edge_signal(arr)
print("image %dx%d  edge max=%.0f p50=%.0f p90=%.0f" % (w, h, sig.max(), np.percentile(sig,50), np.percentile(sig,90)))
for thr in (10, 16, 24, 34):
    bs = bands(sig > thr)
    print("\nthr %d -> %d bands" % (thr, len(bs)))
    for i,(a,b) in enumerate(bs):
        print("   #%d y %5d..%5d h=%4d" % (i+1, a, b, b-a+1))

import sys, os
import numpy as np
from PIL import Image

# 诊断：给一张截图，找出「卡片」的纵向区间
# 卡片左右边缘有描边/高光，会在该行产生强的横向梯度；
# 背景行没有这种边缘，梯度平缓。

def load(path):
    im = Image.open(path).convert("RGB")
    return im, np.asarray(im).astype(np.int16)

def row_edge_strength(arr, x_left=(8, 70), x_right=(1010, 1072)):
    """每行在左右两侧的最大横向梯度"""
    def strength(rng):
        a = arr[:, rng[0]:rng[1], :]           # (h, w, 3)
        d = np.abs(np.diff(a, axis=1)).sum(axis=2)  # (h, w-1)
        return d.max(axis=1)
    return np.maximum(strength(x_left), strength(x_right))

def bands(mask, min_len=60, gap=6):
    out, start, last = [], None, None
    for i, v in enumerate(mask):
        if v:
            if start is None:
                start = i
            last = i
        else:
            if start is not None and i - last > gap:
                if last - start + 1 >= min_len:
                    out.append((start, last))
                start = None
    if start is not None and last - start + 1 >= min_len:
        out.append((start, last))
    return out

path = sys.argv[1]
im, arr = load(path)
h, w, _ = arr.shape
print(f"图像 {w}x{h}")

st = row_edge_strength(arr)
print("梯度统计: min=%d p50=%d p90=%d p99=%d max=%d" % (
    st.min(), np.percentile(st, 50), np.percentile(st, 90), np.percentile(st, 99), st.max()))

for thr in (60, 90, 120, 150):
    m = st > thr
    bs = bands(m)
    print(f"\n阈值 {thr}: {len(bs)} 段")
    for a, b in bs:
        print(f"   y {a:5d}..{b:5d}   高 {b-a+1:4d}")

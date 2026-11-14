import sys
import numpy as np
from PIL import Image

# 诊断 2：用「每行的细节量」找卡片。
# 卡片里有文字/图片 -> 行内标准差大；卡片之间的间隙是纯背景 -> 接近 0。

def analyze(path):
    im = Image.open(path).convert("RGB")
    arr = np.asarray(im).astype(np.float32)
    h, w, _ = arr.shape
    # 只看中间区域，避开状态栏/底部导航
    body = arr[:, 40:w-40, :]
    rowstd = body.std(axis=(1, 2))
    return h, w, rowstd

def bands(mask, min_len=60, gap=10):
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
h, w, rowstd = analyze(path)
print("image %dx%d" % (w, h))
print("rowstd: min=%.1f p25=%.1f p50=%.1f p75=%.1f p90=%.1f max=%.1f" % (
    rowstd.min(), np.percentile(rowstd, 25), np.percentile(rowstd, 50),
    np.percentile(rowstd, 75), np.percentile(rowstd, 90), rowstd.max()))

for thr in (1.5, 3.0, 5.0, 8.0):
    bs = bands(rowstd > thr)
    print("\nthr %.1f -> %d bands" % (thr, len(bs)))
    for i, (a, b) in enumerate(bs):
        print("   #%d y %5d..%5d  h=%4d" % (i+1, a, b, b-a+1))

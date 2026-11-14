import sys
import numpy as np
from PIL import Image

# 诊断 3：卡片是内嵌的，左右有固定边距。
# 比较「卡片内边距区」(x 45..70) 与「页面背景」(x 0..12) 的色差：
# 在卡片内 -> 明显有色差；在卡片间隙 -> 两者都是背景，色差接近 0。

def bands(mask, min_len=50, gap=8):
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
arr = np.asarray(Image.open(path).convert("RGB")).astype(np.float32)
h, w, _ = arr.shape

bg   = arr[:, 0:12, :].mean(axis=1)     # (h,3) 背景
card = arr[:, 45:70, :].mean(axis=1)    # (h,3) 卡片内边距
diff = np.abs(bg - card).sum(axis=1)    # (h,)  0..765

print("image %dx%d" % (w, h))
print("diff: min=%.0f p10=%.0f p50=%.0f p90=%.0f max=%.0f" % (
    diff.min(), np.percentile(diff,10), np.percentile(diff,50),
    np.percentile(diff,90), diff.max()))

for thr in (8, 14, 20, 28, 40):
    bs = bands(diff > thr)
    print("\nthr %d -> %d bands" % (thr, len(bs)))
    for i,(a,b) in enumerate(bs):
        print("   #%d y %5d..%5d  h=%4d" % (i+1, a, b, b-a+1))

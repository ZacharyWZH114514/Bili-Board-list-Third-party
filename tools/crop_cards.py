import sys, os
import numpy as np
from PIL import Image

# 把整页截图切成一张张「卡片」图。
#
# 原理：这个应用的卡片是内嵌的，左右边缘固定在 x≈30 / x≈1050。
# 只在「边缘预期位置」的窄带里找竖向边缘：
#   - 卡片行：x=30 处背景->卡片 有明显跳变
#   - 卡片之间的间隙：整行都是背景，没有跳变
# 于是每一段连续有边缘的行 = 一张卡片。
#
# 裁切：纵向收到卡片自身上下边界，横向收到卡片自身左右边缘，不留背景。

THR = 10.0
MIN_H = 100
GAP = 4
MERGE_GAP = 40     # 带与带的间距小于此值就并成一张（列表行 vs 独立卡片）


def card_x(arr):
    """自适应找卡片左右边缘的 x。

    不能用「总强度最大的列」——那会落到封面/图片的边界上，只框住行内一小块。
    正确做法：找**在最多行上都稳定出现**的竖线（卡片边框在整张卡片的每一行都在）。
    """
    h, w, _ = arr.shape
    g = np.abs(np.diff(arr, axis=1)).sum(axis=2)          # (h, w-1) 每行每列的竖向边缘强度
    hits = (g > 12).sum(axis=0)                            # 每列有多少行「有边缘」
    left_rng = slice(8, max(9, w // 4))
    right_rng = slice(min(w - 9, 3 * w // 4), w - 1)
    left = 8 + int(np.argmax(hits[left_rng]))
    right = min(w - 9, 3 * w // 4) + int(np.argmax(hits[right_rng]))
    if right - left < w // 3 or hits[left] < h // 20 or hits[right] < h // 20:
        return 30, w - 30
    return left, right

def edge_signal(arr, xs=None):
    """只在卡片左右边缘附近找竖向边缘：卡片行有跳变，卡片间隙没有"""
    if xs is None:
        xs = card_x(arr)
    l, r = xs
    def s(c):
        a = arr[:, max(0, c - 10): c + 10, :]
        if a.shape[1] < 2:
            return np.zeros(arr.shape[0])
        return np.abs(np.diff(a, axis=1)).sum(axis=2).max(axis=1)
    return np.maximum(s(l), s(r))


def bands(mask, min_h=MIN_H, gap=GAP):
    out, start, last = [], None, None
    for i, v in enumerate(mask):
        if v:
            if start is None:
                start = i
            last = i
        else:
            if start is not None and i - last > gap:
                if last - start + 1 >= min_h:
                    out.append((start, last))
                start = None
    if start is not None and last - start + 1 >= min_h:
        out.append((start, last))
    return out


def merge_bands(bs, merge_gap=MERGE_GAP):
    """把间距很小的相邻带并成一张：列表行并成一整块，独立卡片不受影响"""
    if not bs:
        return bs
    out = [list(bs[0])]
    for a, b in bs[1:]:
        if a - out[-1][1] - 1 < merge_gap:
            out[-1][1] = b
        else:
            out.append([a, b])
    return [tuple(x) for x in out]


def content_bbox(arr):
    """整页没有内嵌卡片时（引导页等），退回「内容包围盒」"""
    h, w, _ = arr.shape
    # 只在正文区域里算包围盒：跳过状态栏(顶 92px)与底部导航(底 240px)，
    # 否则这两个「也是内容」的条会把包围盒撑到整屏
    top, bottom = 92, h - 240
    body = arr[top:bottom, :, :]
    x0, y0, x1, y1 = _bbox(body)
    return x0, y0 + top, x1, y1 + top


def _bbox(arr):
    h, w, _ = arr.shape
    # 背景色：取四角小块的平均（引导页是整屏渐变，边缘就是背景）
    corners = np.concatenate([
        arr[:40, :40, :].reshape(-1, 3),
        arr[:40, -40:, :].reshape(-1, 3),
        arr[-40:, :40, :].reshape(-1, 3),
        arr[-40:, -40:, :].reshape(-1, 3),
    ], axis=0)
    bgm = corners.mean(axis=0)
    d = np.abs(arr - bgm).sum(axis=2)
    ys = np.where(d.max(axis=1) > 40)[0]
    xs = np.where(d.max(axis=0) > 40)[0]
    if len(ys) == 0 or len(xs) == 0:
        return 0, 0, w - 1, h - 1
    return int(xs[0]), int(ys[0]), int(xs[-1]), int(ys[-1])


def horiz_edges(arr, y0, y1):
    """在卡片中部取几行，找左右边缘的 x"""
    ys = [(y0 + y1) // 2 + d for d in (-20, 0, 20)]
    ys = [y for y in ys if y0 <= y <= y1]
    row = arr[ys, :, :].mean(axis=0)                  # (w,3)
    g = np.abs(np.diff(row, axis=0)).sum(axis=1)      # (w-1,)
    left = 10 + int(np.argmax(g[10:70]))
    right = 1010 + int(np.argmax(g[1010:1070]))
    if right - left < 200:
        return 30, arr.shape[1] - 30
    return left, right


def expand_bands(arr, bs, eps=2.0):
    """把每段纵向扩到真正的卡片边界：一直扩到该行几乎没有细节（纯背景）为止"""
    h, w, _ = arr.shape
    body = arr[:, 40:w - 40, :]
    std = body.std(axis=(1, 2))
    out = []
    for a, b in bs:
        # 向上扩
        y = a
        while y > 0 and std[y - 1] > eps:
            y -= 1
        a2 = y
        # 向下扩
        y = b
        while y < h - 1 and std[y + 1] > eps:
            y += 1
        b2 = y
        if out and a2 <= out[-1][1]:      # 和上一段重叠就并起来
            out[-1][1] = max(out[-1][1], b2)
        else:
            out.append([a2, b2])
    return [tuple(x) for x in out]

def crop_cards(path, outdir, prefix):
    im = Image.open(path).convert("RGB")
    arr = np.asarray(im).astype(np.float32)
    h, w, _ = arr.shape
    bs = merge_bands(bands(edge_signal(arr) > THR))
    # 丢掉贴在最底部、且很矮的碎片（那是被屏幕边缘切掉半截的行）
    if bs and bs[-1][1] >= h - 4 and (bs[-1][1] - bs[-1][0] + 1) < 100:
        bs = bs[:-1]
    os.makedirs(outdir, exist_ok=True)
    made = []
    if False:
        x0, y0, x1, y1 = content_bbox(arr)
        out = os.path.join(outdir, "%s_1.png" % prefix)
        im.crop((x0, y0, x1 + 1, y1 + 1)).save(out)
        return [(out, x1 - x0 + 1, y1 - y0 + 1)]
    for i, (a, b) in enumerate(bs):
        # 纵向收紧 2px，避开圆角外的背景
        y0, y1 = max(0, a + 1), min(h - 1, b - 1)
        if y1 - y0 + 1 < MIN_H:
            continue
        x0, x1 = horiz_edges(arr, y0, y1)
        out = os.path.join(outdir, "%s_%d.png" % (prefix, i + 1))
        im.crop((x0, y0, x1 + 1, y1 + 1)).save(out)
        made.append((out, x1 - x0 + 1, y1 - y0 + 1))
    if not made:
        x0, y0, x1, y1 = content_bbox(arr)
        out = os.path.join(outdir, "%s_1.png" % prefix)
        im.crop((x0, y0, x1 + 1, y1 + 1)).save(out)
        made.append((out, x1 - x0 + 1, y1 - y0 + 1))
    return made


if __name__ == "__main__":
    path, outdir, prefix = sys.argv[1], sys.argv[2], sys.argv[3]
    made = crop_cards(path, outdir, prefix)
    print("%s -> %d cards" % (os.path.basename(path), len(made)))
    for out, cw, ch in made:
        print("   %-16s %4dx%d" % (os.path.basename(out), cw, ch))

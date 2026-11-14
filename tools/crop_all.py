import sys, os, glob
from crop_cards import crop_cards

# 批量：对一套截图逐张切卡片
src, outdir = sys.argv[1], sys.argv[2]
os.makedirs(outdir, exist_ok=True)

total = 0
rows = []
for path in sorted(glob.glob(os.path.join(src, "*.png"))):
    stem = os.path.splitext(os.path.basename(path))[0]
    made = crop_cards(path, outdir, stem)
    total += len(made)
    rows.append((stem, len(made), [f"{cw}x{ch}" for _, cw, ch in made]))

print("screens=%d  cards=%d" % (len(rows), total))
for stem, n, sizes in rows:
    print("  %-26s %d  %s" % (stem, n, ", ".join(sizes)))

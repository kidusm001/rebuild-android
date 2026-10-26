import sys, os
from PIL import Image, ImageDraw, ImageFont
before, after, out, scale = sys.argv[1], sys.argv[2], sys.argv[3], float(sys.argv[4])
names = sys.argv[5:]
def font(sz):
    for f in ["/usr/share/fonts/TTF/Roboto-Medium.ttf", "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf"]:
        try: return ImageFont.truetype(f, sz)
        except Exception: pass
    return ImageFont.load_default()
F = font(28)
cells = []
for n in names:
    for label, d in (("Before", before), ("After", after)):
        p = os.path.join(d, n + ".png")
        if os.path.exists(p): cells.append((label, Image.open(p).convert("RGB")))
w = int(cells[0][1].width * scale); h = int(cells[0][1].height * scale); pad = 30; top = 70
sheet = Image.new("RGB", (pad + len(cells) * (w + pad), top + h + pad), (236, 234, 230))
d = ImageDraw.Draw(sheet)
for i, (label, im) in enumerate(cells):
    x = pad + i * (w + pad)
    d.text((x, 20), label, fill=(30, 27, 22), font=F)
    sheet.paste(im.resize((w, h), Image.LANCZOS), (x, top))
sheet.save(out, optimize=True); print(out, sheet.size)

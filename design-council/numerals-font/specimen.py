"""Render numeral-font candidates on Rebuild's surfaces and measure them.

Usage: python -I specimen.py <fonts_dir> <out_dir>
"""
import io
import json
import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from PIL import Image, ImageDraw, ImageFont, features

FONTS, OUT = Path(sys.argv[1]), Path(sys.argv[2])
OUT.mkdir(parents=True, exist_ok=True)
INST = OUT / "instances"
INST.mkdir(exist_ok=True)
RAQM = features.check("raqm")

SYS = Path("/usr/share/fonts/TTF")
ROBOTO_BODY = str(SYS / "Roboto-Regular.ttf")
ROBOTO_SEMI = str(SYS / "Roboto-Medium.ttf")

# id, label, file, axes for a static bold instance
CANDIDATES = [
    ("A", "Platform sans-serif-condensed (Roboto Condensed, today)", SYS / "RobotoCondensed-Bold.ttf", None),
    ("B", "Roboto Condensed (current Google Fonts build)", FONTS / "RobotoCondensed-VF.ttf", {"wght": 700}),
    ("C", "Roboto Flex, width 75", FONTS / "RobotoFlex-VF.ttf", {"wght": 700, "wdth": 75, "opsz": 36}),
    ("D", "Barlow Semi Condensed Bold", FONTS / "BarlowSemiCondensed-Bold.ttf", None),
    ("E", "Barlow Condensed Bold", FONTS / "BarlowCondensed-Bold.ttf", None),
    ("F", "IBM Plex Sans Condensed Bold", FONTS / "IBMPlexSansCondensed-Bold.ttf", None),
    ("G", "Sofia Sans Condensed Bold", FONTS / "SofiaSansCondensed-VF.ttf", {"wght": 700}),
    ("H", "Fira Sans Condensed Bold", FONTS / "FiraSansCondensed-Bold.ttf", None),
    ("I", "Archivo, width 75", FONTS / "Archivo-VF.ttf", {"wght": 700, "wdth": 75}),
    ("J", "Saira Condensed Bold", FONTS / "SairaCondensed-Bold.ttf", None),
    ("K", "Oswald Bold", FONTS / "Oswald-VF.ttf", {"wght": 700}),
    ("L", "Big Shoulders Bold", FONTS / "BigShoulders-VF.ttf", {"wght": 700, "opsz": 48}),
    ("M", "Inter Bold (not condensed, reference)", FONTS / "Inter-VF.ttf", {"wght": 700, "opsz": 32}),
]

SUBSET_TEXT = "0123456789:.,-–−+×/%kgblmisRIRWDBx "

LIGHT = dict(canvas="#F3EFE9", card="#FFFFFF", ink="#1C1915", muted="#4F483F", dock="#FFDFA3",
             onDock="#231500", accent="#6B4E0E", name="light")
DARK = dict(canvas="#0F0E0D", card="#1D1B19", ink="#EDE6DD", muted="#CFC5B7", dock="#EBA42A",
            onDock="#231500", accent="#FFB224", name="dark")

PX = 3  # px per dp/sp (xxhdpi)


def make_instance(cid, path, axes):
    if not path.exists():
        return None
    if axes is None:
        return str(path)
    f = TTFont(path)
    names = {a.axisTag for a in f["fvar"].axes}
    pinned = {k: v for k, v in axes.items() if k in names}
    inst = instancer.instantiateVariableFont(f, pinned)
    out = INST / f"{cid}.ttf"
    inst.save(out)
    return str(out)


def measure(path):
    f = TTFont(path)
    cmap = f.getBestCmap()
    hmtx = f["hmtx"]
    upm = f["head"].unitsPerEm
    digits = [hmtx[cmap[ord(d)]][0] for d in "0123456789" if ord(d) in cmap]
    default_tabular = len(set(digits)) == 1
    has_tnum = False
    if "GSUB" in f and f["GSUB"].table.FeatureList:
        has_tnum = any(fr.FeatureTag == "tnum" for fr in f["GSUB"].table.FeatureList.FeatureRecord)
    missing = [c for c in "×–−:." if ord(c) not in cmap]
    # subset size: numerals + symbols, keep tnum/lnum
    opts = subset.Options()
    opts.layout_features = ["tnum", "lnum", "kern", "case"]
    opts.flavor = None
    sub = subset.Subsetter(opts)
    g = TTFont(path)
    sub.populate(text=SUBSET_TEXT)
    sub.subset(g)
    buf = io.BytesIO()
    g.save(buf)
    return dict(
        upm=upm,
        default_figures_tabular=default_tabular,
        has_tnum_feature=has_tnum,
        tabular_after_tnum=default_tabular or has_tnum,
        missing_glyphs=missing,
        full_kb=round(Path(path).stat().st_size / 1024, 1),
        subset_kb=round(len(buf.getvalue()) / 1024, 1),
    )


def font(path, sp):
    return ImageFont.truetype(path, int(sp * PX), layout_engine=ImageFont.Layout.RAQM if RAQM else ImageFont.Layout.BASIC)


def draw_text(d, xy, text, f, fill, tnum=True):
    kw = {"features": ["tnum"]} if (RAQM and tnum) else {}
    d.text(xy, text, font=f, fill=fill, **kw)
    return d.textbbox(xy, text, font=f, **kw)


def text_w(f, text, tnum=True):
    kw = {"features": ["tnum"]} if (RAQM and tnum) else {}
    return f.getlength(text, **kw)


def panel(path, label, theme):
    """One phone-width (360 dp) column showing the surfaces where Numerals are used."""
    W, H = 360 * PX, 520 * PX
    im = Image.new("RGB", (W, H), theme["canvas"])
    d = ImageDraw.Draw(im)
    g = 20 * PX
    body = ImageFont.truetype(ROBOTO_BODY, 14 * PX)
    small = ImageFont.truetype(ROBOTO_BODY, 12 * PX)
    semi = ImageFont.truetype(ROBOTO_SEMI, 16 * PX)
    y = 12 * PX
    d.text((g, y), label, font=semi, fill=theme["ink"])
    y += 28 * PX

    # 1. Rest dock: small "Rest" + displaySmall countdown
    d.rounded_rectangle((0, y, W, y + 76 * PX), radius=24 * PX, fill=theme["dock"])
    d.text((g, y + 10 * PX), "Rest", font=small, fill=theme["onDock"])
    draw_text(d, (g, y + 24 * PX), "2:50", font(path, 36), theme["onDock"])
    d.text((W - 120 * PX, y + 30 * PX), "+30 s", font=semi, fill=theme["onDock"])
    y += 88 * PX

    # 2. Full rest countdown, displayLarge 64 sp, two frames stacked to show digit stability
    for t in ("1:08", "1:11"):
        draw_text(d, (g, y), t, font(path, 64), theme["ink"])
        y += 68 * PX
    y += 6 * PX

    # 3. Ledger: last time (body) vs today (ledger value, numerals 18 sp)
    d.rounded_rectangle((g - 8 * PX, y, W - g + 8 * PX, y + 112 * PX), radius=20 * PX, fill=theme["card"])
    d.text((g, y + 8 * PX), "Set   Last time            Today", font=small, fill=theme["muted"])
    rows = [("1", "37.5 kg/DB × 10", "42.5 kg/DB × 10"), ("2", "37.5 kg/DB × 9", "42.5 kg/DB × 9"),
            ("3", "102.5 kg × 8", "107.5 kg × 6")]
    ry = y + 28 * PX
    for s, last, today in rows:
        d.text((g, ry + 2 * PX), s, font=body, fill=theme["ink"])
        d.text((g + 34 * PX, ry + 2 * PX), last, font=body, fill=theme["muted"])
        draw_text(d, (g + 170 * PX, ry), today, font(path, 18), theme["accent"])
        ry += 27 * PX
    y += 124 * PX

    # 4. Stat tiles: displaySmall 36 sp in three 1/3-width tiles
    tw = (W - 2 * g - 16 * PX) // 3
    for i, (v, cap) in enumerate((("12,480", "kg volume"), ("47:05", "duration"), ("18", "sets"))):
        x = g + i * (tw + 8 * PX)
        d.rounded_rectangle((x, y, x + tw, y + 76 * PX), radius=12 * PX, fill=theme["card"])
        draw_text(d, (x + 10 * PX, y + 8 * PX), v, font(path, 30), theme["ink"])
        d.text((x + 10 * PX, y + 52 * PX), cap, font=small, fill=theme["muted"])
    return im


WIDTH_STRINGS = ("2:50", "1:08:42", "107.5 kg × 12", "12,480")
BASE = {}


def width_check(path):
    """Width of key strings with tnum at 100 sp, as a percentage of today's font (candidate A)."""
    out = {}
    for text in WIDTH_STRINGS:
        w = text_w(font(path, 100), text)
        BASE.setdefault(text, w)
        out[text] = round(100 * w / BASE[text])
    return out


def main():
    results = []
    sheets = {"light": [], "dark": []}
    for cid, label, path, axes in CANDIDATES:
        p = make_instance(cid, path, axes)
        if p is None:
            results.append(dict(id=cid, label=label, error="font file not downloaded"))
            continue
        m = measure(p)
        m.update(id=cid, label=label, width_vs_today_pct=width_check(p))
        results.append(m)
        for theme in (LIGHT, DARK):
            im = panel(p, f"{cid}  {label}", theme)
            im.save(OUT / f"{cid}-{theme['name']}.png")
            sheets[theme["name"]].append(im)
    for name, ims in sheets.items():
        # contact sheets of 4 columns, scaled down for overview only
        cols = 4
        w, h = ims[0].size
        rows = (len(ims) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * w, rows * h), "#808080")
        for i, im in enumerate(ims):
            sheet.paste(im, ((i % cols) * w, (i // cols) * h))
        sheet.thumbnail((cols * w // 2, rows * h // 2))
        sheet.save(OUT / f"overview-{name}.png")
    (OUT / "metrics.json").write_text(json.dumps(dict(raqm=RAQM, candidates=results), indent=2, ensure_ascii=False))
    print(json.dumps(dict(raqm=RAQM), indent=1))
    for r in results:
        if "error" in r:
            print(r["id"], r["error"])
            continue
        print(f'{r["id"]} tabular={r["tabular_after_tnum"]} missing={r["missing_glyphs"]} '
              f'full={r["full_kb"]}KB subset={r["subset_kb"]}KB width_vs_today%={r["width_vs_today_pct"]}')


main()

#!/usr/bin/env python3
"""Builds Rebuild's bundled Archivo fonts into app/src/main/res/font, and its licence into assets.

Words use Archivo at width 100 in four static weights; numerals use Archivo at width 80, Bold. The design council's
reasoning is in design-council/app-fonts/decision.md. Run it again only to change what the fonts contain:

    python3 -m venv /tmp/fontenv && /tmp/fontenv/bin/pip install fonttools==4.62.1
    /tmp/fontenv/bin/python scripts/fonts/build_archivo.py

The source is pinned by commit and SHA-256, so the output is reproducible. On top of a plain instance and subset,
each file gets three changes:

- Roboto's vertical metrics (ascent 0.928 em, descent 0.244 em), so text sits on the same baseline as the Roboto
  every layout was tuned for. Archivo's own line box is shorter and dropped large text (the rest-end count) lower.
- ↳ ● ○ drawn to match each weight's stem, so drop rows and chart legends never fall back to a system font.
- The narrow no-break space Android 14 puts before AM/PM (U+202F), mapped to the thin space, and a zero-width space
  (U+200B), so formatted times never fall back either.
- In the numerals file only, a wider space (0.40 of a figure), so "42.5 kg" doesn't run together.
"""
import hashlib
import io
import math
import sys
import urllib.request
from pathlib import Path

from fontTools import subset
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

COMMIT = "6c70c829f09ea345d3590406693220ea35c6553f"
BASE = f"https://raw.githubusercontent.com/google/fonts/{COMMIT}/ofl/archivo/"
SOURCE = ("Archivo%5Bwdth,wght%5D.ttf", "0e094a7d3c7c4c25cf1310c4b30014f1dae9332220b1c2c88f4fa996f0b05053")
LICENCE = ("OFL.txt", "108b4e57c9c796d3d38d0428ca7ee39de47ad93187302718d9b2d8864b9b716b")

ROOT = Path(__file__).resolve().parents[2]
FONT_DIR = ROOT / "app/src/main/res/font"
ASSETS = ROOT / "app/src/main/assets/licenses"

# file name -> (wdth, wght, numerals, family, style)
OUTPUTS = {
    "archivo_regular": (100, 400, False, "Archivo", "Regular"),
    "archivo_medium": (100, 500, False, "Archivo", "Medium"),
    "archivo_semibold": (100, 600, False, "Archivo", "SemiBold"),
    "archivo_bold": (100, 700, False, "Archivo", "Bold"),
    "archivo_numerals": (80, 700, True, "Archivo Numerals", "Bold"),
}

# Basic Latin, Latin-1, Latin Extended-A, general punctuation, and the few symbols the UI uses.
UNICODES = [*range(0x20, 0x7F), *range(0xA0, 0x180), *range(0x2000, 0x2070), 0x20AC, 0x2122, 0x2212,
            0x2190, 0x2192, 0x200B, 0x202F, 0x21B3, 0x25CB, 0x25CF]
FEATURES = ["kern", "liga", "calt", "ccmp", "locl", "mark", "mkmk", "tnum", "lnum", "case", "zero"]

ROBOTO_ASCENT, ROBOTO_DESCENT = 0.928, 0.244
NUMERAL_SPACE = 0.40  # of a tabular figure


def fetch(name, sha):
    data = urllib.request.urlopen(BASE + name, timeout=60).read()
    if sha and hashlib.sha256(data).hexdigest() != sha:
        sys.exit(f"{name}: SHA-256 mismatch, refusing to build")
    return data


def map_spaces(font):
    """Characters Archivo lacks that formatting inserts, drawn with glyphs it has."""
    cmap = font.getBestCmap()
    extra = {0x202F: cmap[0x2009], 0x200B: cmap[0xFEFF]}
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap.update(extra)


def roboto_metrics(font):
    upm = font["head"].unitsPerEm
    asc, desc = round(ROBOTO_ASCENT * upm), round(ROBOTO_DESCENT * upm)
    font["hhea"].ascent, font["hhea"].descent, font["hhea"].lineGap = asc, -desc, 0
    os2 = font["OS/2"]
    os2.sTypoAscender, os2.sTypoDescender, os2.sTypoLineGap = asc, -desc, 0


def stem(font):
    """The vertical stem of "l", which the drawn glyphs use as their stroke."""
    glyf = font["glyf"]
    g = glyf[font.getBestCmap()[ord("l")]]
    g.recalcBounds(glyf)
    return g.xMax - g.xMin


def circle(pen, cx, cy, r, clockwise=True):
    """A circle from eight quadratic arcs, starting at the top."""
    steps = [90 - 45 * i for i in range(8)] if clockwise else [90 + 45 * i for i in range(8)]
    k = r / math.cos(math.radians(22.5))
    pen.moveTo((round(cx), round(cy + r)))
    for a in steps:
        b = a - 45 if clockwise else a + 45
        mid = math.radians((a + b) / 2)
        pen.qCurveTo((round(cx + k * math.cos(mid)), round(cy + k * math.sin(mid))),
                     (round(cx + r * math.cos(math.radians(b))), round(cy + r * math.sin(math.radians(b)))))
    pen.closePath()


def add_glyph(font, name, codepoint, draw, advance):
    pen = TTGlyphPen(None)
    draw(pen)
    glyph = pen.glyph()
    order = font.getGlyphOrder()
    if name not in order:
        font.setGlyphOrder(order + [name])
    font["glyf"][name] = glyph
    glyph.recalcBounds(font["glyf"])
    font["hmtx"][name] = (advance, glyph.xMin)
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap[codepoint] = name


def add_symbols(font):
    upm = font["head"].unitsPerEm
    os2 = font["OS/2"]
    cap, x = os2.sCapHeight, os2.sxHeight
    t = stem(font)
    side = round(0.08 * upm)

    # ● and ○: about the x-height across, centred on it, so they read as chart markers beside lowercase words.
    d = round(1.05 * x)
    r, cy = d / 2, x / 2
    add_glyph(font, "uni25CF", 0x25CF, lambda p: circle(p, side + r, cy, r), d + 2 * side)
    ring = round(t * 0.85)

    def draw_ring(p):
        circle(p, side + r, cy, r)
        circle(p, side + r, cy, r - ring, clockwise=False)

    add_glyph(font, "uni25CB", 0x25CB, draw_ring, d + 2 * side)

    # ↳: down from cap height, then right into an arrowhead around the middle of the x-height.
    w = round(0.62 * upm)
    left, right = side, side + w
    c = round(0.42 * x)
    h = round(t * 0.9) // 2
    head_len, head_half = round(0.32 * x), round(0.27 * x)

    def draw_arrow(p):
        p.moveTo((left, c - h))
        for pt in ((left, cap), (left + 2 * h, cap), (left + 2 * h, c + h), (right - head_len, c + h),
                   (right - head_len, c + head_half), (right, c), (right - head_len, c - head_half),
                   (right - head_len, c - h)):
            p.lineTo(pt)
        p.closePath()

    add_glyph(font, "uni21B3", 0x21B3, draw_arrow, w + 2 * side)


def widen_spaces(font):
    cmap = font.getBestCmap()
    figure = font["hmtx"][cmap[ord("0")]][0]  # Archivo's default figures are proportional; take tnum's width
    gsub = font["GSUB"].table
    for record in gsub.FeatureList.FeatureRecord:
        if record.FeatureTag == "tnum":
            for index in record.Feature.LookupListIndex:
                for st in gsub.LookupList.Lookup[index].SubTable:
                    mapping = getattr(st, "mapping", None) or getattr(getattr(st, "ExtSubTable", None), "mapping", {})
                    if cmap[ord("0")] in mapping:
                        figure = font["hmtx"][mapping[cmap[ord("0")]]][0]
    width = round(NUMERAL_SPACE * figure)
    for cp in (0x20, 0xA0):
        name = cmap[cp]
        font["hmtx"][name] = (width, font["hmtx"][name][1])


def set_names(font, family, style):
    name = font["name"]
    full = f"{family} {style}" if style != "Regular" else family
    ps = f"{family}-{style}".replace(" ", "")
    for nid in (16, 17, 25):  # typographic family/subfamily and variations prefix no longer apply
        name.removeNames(nameID=nid)
    for nid, value in ((1, family if style in ("Regular", "Bold") else f"{family} {style}"),
                       (2, style if style in ("Regular", "Bold") else "Regular"), (3, f"{ps};{COMMIT[:7]}"),
                       (4, full), (6, ps)):
        name.setName(value, nid, 3, 1, 0x409)
        name.setName(value, nid, 1, 0, 0)
    if style not in ("Regular", "Bold"):
        name.setName(family, 16, 3, 1, 0x409)
        name.setName(style, 17, 3, 1, 0x409)
    os2, head = font["OS/2"], font["head"]
    bold = style == "Bold"
    italic, bold_bit, regular_bit = 1, 1 << 5, 1 << 6
    os2.fsSelection &= ~(italic | bold_bit | regular_bit)
    os2.fsSelection |= bold_bit if bold else regular_bit if style == "Regular" else 0
    head.macStyle = (head.macStyle & ~1) | (1 if bold else 0)


def build(source, wdth, wght, numerals, family, style):
    font = instancer.instantiateVariableFont(TTFont(io.BytesIO(source)), {"wdth": wdth, "wght": wght})
    set_names(font, family, style)
    roboto_metrics(font)
    map_spaces(font)
    add_symbols(font)
    if numerals:
        widen_spaces(font)
    options = subset.Options()
    options.layout_features = FEATURES
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.notdef_outline = True
    options.glyph_names = False
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=UNICODES)
    subsetter.subset(font)
    return font


def main():
    source = fetch(*SOURCE)
    FONT_DIR.mkdir(parents=True, exist_ok=True)
    for name, spec in OUTPUTS.items():
        path = FONT_DIR / f"{name}.ttf"
        build(source, *spec).save(path)
        print(f"{path.relative_to(ROOT)}: {path.stat().st_size // 1024} KB")
    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / "Archivo-OFL.txt").write_bytes(fetch(*LICENCE))
    print(f"{(ASSETS / 'Archivo-OFL.txt').relative_to(ROOT)}")


if __name__ == "__main__":
    main()

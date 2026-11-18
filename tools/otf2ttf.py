"""CFF2 -> TrueType (glyf) conversion for the embedded serif subset.

Android 8.0 (minSdk 26) ships FreeType 2.8, which has no CFF2 support; the
variable-font instancer emits a static CFF2 table. Converting to quadratic
outlines keeps the serif renderable on every supported API level.

Used by tools/build_app_fonts.py; also runnable standalone:
    python tools/otf2ttf.py in.otf out.ttf
"""

import sys

from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont, newTable


def glyphs_to_quadratic(glyphs, max_err=1.0, reverse_direction=True):
    quad = {}
    for name in glyphs.keys():
        tt_pen = TTGlyphPen(glyphs)
        cu2qu_pen = Cu2QuPen(tt_pen, max_err, reverse_direction=reverse_direction)
        glyphs[name].draw(cu2qu_pen)
        quad[name] = tt_pen.glyph()
    return quad


def otf_to_ttf(font, max_err=1.0):
    assert font.sfntVersion == "OTTO"
    assert "CFF2" in font or "CFF " in font
    order = font.getGlyphOrder()
    glyph_set = font.getGlyphSet()

    font["loca"] = newTable("loca")
    glyf = font["glyf"] = newTable("glyf")
    glyf.glyphOrder = order
    glyf.glyphs = glyphs_to_quadratic(glyph_set, max_err=max_err)

    for tag in ("CFF2", "CFF ", "VORG"):
        if tag in font:
            del font[tag]

    glyf.compile(font)

    font["maxp"] = maxp = newTable("maxp")
    maxp.tableVersion = 0x00010000
    maxp.maxZones = 1
    maxp.maxTwilightPoints = 0
    maxp.maxStorage = 0
    maxp.maxFunctionDefs = 0
    maxp.maxInstructionDefs = 0
    maxp.maxStackElements = 0
    maxp.maxSizeOfInstructions = 0
    maxp.maxComponentElements = max(
        (len(g.components) if hasattr(g, "components") else 0) for g in glyf.glyphs.values()
    )
    maxp.compile(font)

    post = font["post"]
    post.formatType = 2.0
    post.extraNames = []
    post.mapping = {}
    post.glyphOrder = order

    font.sfntVersion = "\000\001\000\000"


if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    font = TTFont(src)
    otf_to_ttf(font)
    font.save(dst)
    print("wrote", dst)

# Builds the app's font assets deterministically.
#
# Two typefaces, kept as separate files:
#   Han   -> 思源宋体 Source Han Serif SC (OFL 1.1), subset + glyf conversion
#   Latin -> Timeless Serif Text (Timeless Free Font License v1.3)
#
# They are NOT merged. The Timeless license forbids modifying or renaming the
# font files, so the two TTFs are shipped byte-for-byte as published and
# combined at runtime with Typeface.CustomFallbackBuilder (API 29+), Han as the
# base (correct CJK metrics) and Timeless as the per-glyph fallback for Latin.
# See ui/theme/ScriptTypeface.kt.
#
# Steps:
# 1) downloads+caches the official 通用规范汉字表 level-1 list (3500 chars)
# 2) instances Source Han Serif SC VF, subsets to CJK-only charset, CFF2->glyf
#    (every codepoint Timeless owns is removed so the two cmaps stay disjoint)
# 3) copies the two Timeless Serif Text statics into assets/fonts untouched
# 4) archives licenses + writes NOTICE, prints a size report
import json, os, pathlib, shutil, subprocess, sys, urllib.request

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from otf2ttf import otf_to_ttf  # noqa: E402

ROOT = pathlib.Path(r"D:\_DDL_List")
LIST_TXT = ROOT / "tools/hanzi_gsc1.txt"
LIST_URL = ("https://cdn.jsdelivr.net/gh/ben-hua/general_standard_chinese@"
            "main/gsc_level_1.txt")
FONT_OUT = ROOT / "app/src/main/assets/fonts"
DOCS_OUT = ROOT / "docs/fonts"
SERIF_VF = ROOT / "font/SourceHanSerifSC-VF.otf"
TIMELESS_DIR = ROOT / "font/Timeless/Serif-Text/static/ttf"

if not LIST_TXT.exists():
    LIST_TXT.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve(LIST_URL, LIST_TXT)

hanzi = []
for line in LIST_TXT.read_text(encoding="utf-8").splitlines():
    parts = line.strip().split()
    if len(parts) >= 2 and parts[0].isdigit():
        hanzi.append(parts[1])
hanzi = "".join(hanzi)
assert len(set(hanzi)) == 3500, f"gsc parse fail: {len(set(hanzi))}"

# CJK side: the 3500 common hanzi, CJK punctuation, fullwidth forms, and the
# handful of symbols that only the Han font carries (⋯ ″ ′ → ← × ° ＋).
CJK_RANGES = [(0x3000, 0x303F), (0xFF01, 0xFF5E)]
cjk_extra = "\u22ef\u2033\u2032\u2192\u2190\u00d7\u00b0\u2212\u2013\u2014\u00b7"
cjk_chars = set(hanzi) | set(cjk_extra)
for a, b in CJK_RANGES:
    cjk_chars |= {chr(c) for c in range(a, b + 1)}

# The Latin faces: Timeless Serif Text Regular (400) / Semibold (600).
LATIN_FACES = [
    # out name,             source file,                        han weight
    ("timeless_serif_400", "TimelessSerif-TextRegular.ttf", 400),
    ("timeless_serif_600", "TimelessSerif-TextSemibold.ttf", 600),
]

# cmaps must be disjoint: Timeless owns every codepoint it has (Latin, digits,
# punctuation, arrows), the Han side only fills what is left. This is what
# makes the runtime fallback unambiguous.
latin_cps = set()
for _out, _src, _hw in LATIN_FACES:
    latin_cps |= set(TTFont(TIMELESS_DIR / _src).getBestCmap())
cjk_chars = {c for c in cjk_chars if ord(c) not in latin_cps}
cjk_text = "".join(sorted(cjk_chars))

tmp_cjk = ROOT / "tools/_cjk_charset.txt"
tmp_cjk.write_text(cjk_text, encoding="utf-8")


def run(args):
    result = subprocess.run([sys.executable] + args, capture_output=True, text=True,
                            encoding="utf-8", errors="replace")
    if result.returncode:
        sys.stderr.write(result.stdout or "")
        sys.stderr.write(result.stderr or "")
        raise SystemExit(result.returncode)


def strip_variable_tables(font):
    for tag in ("fvar", "gvar", "avar", "cvar", "HVAR", "VVAR", "MVAR",
                "STAT", "VORG", "DSIG"):
        if tag in font:
            del font[tag]
    return font


FONT_OUT.mkdir(parents=True, exist_ok=True)
DOCS_OUT.mkdir(parents=True, exist_ok=True)
work = ROOT / "tools/_fontbuild"
work.mkdir(exist_ok=True)
report = {}

# ---- Latin side: shipped exactly as published -------------------------
for out_name, src, _weight in LATIN_FACES:
    src_path = TIMELESS_DIR / src
    dst = FONT_OUT / f"{out_name}.ttf"
    shutil.copyfile(src_path, dst)
    assert dst.read_bytes() == src_path.read_bytes(), f"{out_name} was altered"
    report[out_name] = round(dst.stat().st_size / 1024)

# ---- Han side: one glyf TTF per weight --------------------------------
# 先子集再实例化：55MB 的 VF 直接实例化要几分钟，子集到 3.5k 字后秒级完成。
han_ttf = {}
vf = TTFont(SERIF_VF)
vf_sub_opts = subset.Options()
vf_sub_opts.layout_features = []
vf_sub_opts.notdef_outline = True
vf_sub_opts.name_IDs = ["*"]
vf_sub = subset.Subsetter(options=vf_sub_opts)
vf_sub.populate(text=cjk_text)
vf_sub.subset(vf)
vf_lean = work / "serif_vf_subset.otf"
vf.save(vf_lean)
print("han VF subset:", vf_lean.stat().st_size // 1024, "KB", flush=True)

for weight in (400, 600):
    inst = work / f"serif_{weight}.otf"
    run(["-m", "fontTools.varLib.instancer", str(vf_lean),
         f"wght={weight}", "-o", str(inst), "--no-recalc-timestamp"])
    font = strip_variable_tables(TTFont(inst))
    # 汉字不需要字距/连字；布局表已在子集阶段清空，GDEF 里残留的
    # ItemVariationStore 也一并丢掉。
    for tag in ("GDEF", "GPOS", "GSUB", "BASE", "vhea", "vmtx"):
        if tag in font:
            del font[tag]
    otf_to_ttf(font)
    dst = FONT_OUT / f"han_serif_{weight}.ttf"
    font.save(dst)
    han_ttf[weight] = dst
    report[f"han_serif_{weight}"] = round(dst.stat().st_size / 1024)

# ---- licenses + notice ------------------------------------------------
(DOCS_OUT / "NOTICE.md").write_text(
    "# Embedded font licenses\n\n"
    "- **Source Han Serif SC** (思源宋体, VF instance-subset, CFF2->glyf): "
    "SIL Open Font License 1.1, copyright (c) Adobe. Full text: "
    "`LICENSE-SourceHanSerif-OFL.txt` "
    "(https://github.com/adobe-fonts/source-han-serif/blob/release/LICENSE.txt)\n"
    "- **Timeless Serif Text** (Latin glyphs, `timeless_serif_400/600.ttf`): "
    "Timeless Free Font License v1.3, copyright (c) Timeless Ventures Private "
    "Limited. The files are embedded byte-for-byte as published — no merging, "
    "subsetting or renaming — and are combined with the Han face at runtime "
    "only. The name table declares v1.2; this project follows v1.3 (the current "
    "version), as permitted by section 8.4. Full text: `LICENSE-Timeless.txt` "
    "(https://www.timeless.co/type/license).\n"
    "- The Han subset and the Timeless statics are separate files; nothing in "
    "`app/src/main/assets/fonts/` is a derivative of a Timeless font.\n"
    "- On API 28 and below there is no `Typeface.CustomFallbackBuilder`, so "
    "Latin falls back to the Source Han Serif built-in Latin; Chinese is always "
    "the Han face.\n",
    encoding="utf-8")

for leftover in (tmp_cjk,):
    leftover.unlink(missing_ok=True)
shutil.rmtree(work, ignore_errors=True)

report["total_kb"] = sum(v for v in report.values())
print(json.dumps(report, indent=1))

# Builds the app's res/font assets deterministically.
# 1) downloads+caches the official 通用规范汉字表 level-1 list (3500 chars)
# 2) instances Source Han Sans CN VF at weights 400/500/600/700, subsets each
# 3) subsets Maple Mono Bold to the countdown/units charset
# 4) copies the two Timeless static TTFs
# 5) archives licenses + writes NOTICE, prints a size report
import base64, json, pathlib, shutil, subprocess, sys, urllib.request

ROOT = pathlib.Path(r"D:\_DDL_List")
LIST_TXT = ROOT / "tools/hanzi_gsc1.txt"
LIST_URL = ("https://cdn.jsdelivr.net/gh/ben-hua/general_standard_chinese@"
            "main/gsc_level_1.txt")
FONT_OUT = ROOT / "app/src/main/res/font"
DOCS_OUT = ROOT / "docs/fonts"

if not LIST_TXT.exists():
    LIST_TXT.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve(LIST_URL, LIST_TXT)

cjk = []
for line in LIST_TXT.read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if not line:
        continue
    parts = line.split()
    if len(parts) >= 2 and parts[0].isdigit():
        cjk.append(parts[1])
hanzi = "".join(cjk)
assert len(set(hanzi)) == 3500, f"gsc parse fail: {len(set(hanzi))}"

extra = "".join(chr(c) for c in range(0x20, 0x7F))        # ASCII printable
extra += "".join(chr(c) for c in range(0x3000, 0x3040))   # CJK punct (、。「」…)
extra += "".join(chr(c) for c in range(0xFF01, 0xFF60))   # fullwidth forms
extra += "−–—·▸，：;天小时日周分秒已完期限提剥剥" # extras (trim dupes below)
extra = "".join(sorted(set(extra)))

han_text = extra + "".join(sorted(set(hanzi)))
tmp = ROOT / "tools/_han_charset.txt"
tmp.write_text(han_text, encoding="utf-8")

data_text = extra + "".join(sorted(set("0123456789−:．·天小时日周分秒")))
tmp2 = ROOT / "tools/_maple_charset.txt"
tmp2.write_text("".join(sorted(set(data_text))), encoding="utf-8")

def run(args):
    subprocess.run([sys.executable] + args, check=True, capture_output=True)

FONT_OUT.mkdir(parents=True, exist_ok=True)
DOCS_OUT.mkdir(parents=True, exist_ok=True)
sanh = ROOT / "font/Timeless/SourceHanSansCN-VF.otf"
report = {}

for w in (400, 500, 600, 700):
    inst = ROOT / f"tools/_sanh_{w}.otf"
    run(["-m", "fontTools.varLib.instancer", str(sanh),
         f"wght={w}", "-o", str(inst)])
    dst = FONT_OUT / f"source_han_cn_{w}.otf"
    run(["-m", "fontTools.subset", str(inst), f"--text-file={tmp}",
         f"--output-file={dst}", "--layout-features=*"])
    report[f"source_han_cn_{w}"] = round(dst.stat().st_size / 1024)
    inst.unlink()

mroot = ROOT / "font/MapleMono-NF-CN-unhinted"
dst = FONT_OUT / "maple_mono_bold_data.ttf"
run(["-m", "fontTools.subset", str(mroot / "MapleMono-NF-CN-Bold.ttf"),
     f"--text-file={tmp2}", f"--output-file={dst}", "--layout-features=*"])
report["maple_mono_bold_data"] = round(dst.stat().st_size / 1024)

troot = ROOT / "font/Timeless"
pairs = [(troot / "Sans-Grotesk/static/ttf/TimelessSans-GroteskBold.ttf",
          FONT_OUT / "timeless_grotesk_bold.ttf"),
         (troot / "Serif-Text/static/ttf/TimelessSerif-TextRegular.ttf",
          FONT_OUT / "timeless_serif_text.ttf")]
for src, dst in pairs:
    shutil.copyfile(src, dst)
    report[dst.name] = round(dst.stat().st_size / 1024)

# license archival + notice
shutil.copyfile(mroot / "LICENSE.txt", DOCS_OUT / "LICENSE-MapleMono-OFL.txt")
tl_license = troot / "LICENSE.pdf"
(DOCS_OUT / "NOTICE.md").write_text(
    "# Embedded font licenses\n\n"
    "- **Source Han Sans CN** (VF instance-subset): SIL Open Font License 1.1, "
    "copyright (c) Adobe. License text: "
    "https://github.com/adobe-fonts/source-han-sans/blob/release/LICENSE.txt\n"
    "- **Maple Mono NF CN** (digit subset): SIL OFL 1.1 — see "
    "LICENSE-MapleMono-OFL.txt\n"
    "- **Timeless** (Sans-Grotesk Bold, Serif-Text Regular): Timeless Free Font "
    "License 1.2 — the official PDF was NOT present in font/Timeless at build "
    "time; **re-obtain and re-verify redistribution terms BEFORE any public "
    "distribution of the APK**\n"
    + ("\nAdditionally archived: LICENSE-Timeless.pdf\n"
       if tl_license.exists() else "\n"), encoding="utf-8")
if tl_license.exists():
    shutil.copyfile(tl_license, DOCS_OUT / "LICENSE-Timeless.pdf")

tmp.unlink(); tmp2.unlink()
report["total_kb"] = sum(report.values())
print(json.dumps(report, indent=1))

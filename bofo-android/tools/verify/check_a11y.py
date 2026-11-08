#!/usr/bin/env python3
"""Stage 5: accessibility + contrast checks for app-admin (screens are built in code). Prints failures + one summary line."""
import re, glob, sys, pathlib
root = pathlib.Path(__file__).resolve().parents[2]
src = root / "app-admin/src/main"
fails = []

# 1. every ImageView / ImageButton needs a content description
for f in glob.glob(str(src / "java/**/*.java"), recursive=True):
    t = open(f, encoding="utf-8").read()
    n_img = len(re.findall(r"new (?:android\.widget\.)?Image(?:View|Button)\(", t))
    n_desc = len(re.findall(r"setContentDescription\(", t))
    if n_img > n_desc: fails.append(f"{pathlib.Path(f).name}: {n_img} image view(s), {n_desc} content description(s)")

# 2. a card/row (not a Button) with a click listener must go through Ui.asButton
for f in glob.glob(str(src / "java/**/*.java"), recursive=True):
    if f.endswith("Ui.java"): continue
    for i, line in enumerate(open(f, encoding="utf-8").read().splitlines(), 1):
        m = re.search(r"\b(card|c)\.setOnClickListener\(", line)
        if m and f"Ui.asButton({m.group(1)})" not in line and "Ui.asButton(" not in open(f, encoding="utf-8").read().splitlines()[i-2] + line:
            fails.append(f"{pathlib.Path(f).name}:{i}: clickable card without Ui.asButton")

# 3. filter chips must use Ui.chip (spoken selected state)
for f in glob.glob(str(src / "java/**/*.java"), recursive=True):
    for i, line in enumerate(open(f, encoding="utf-8").read().splitlines(), 1):
        if re.search(r"Ui\.button\([^;]*(==|!decided|\bdecided\b)", line) and "Ui.chip" not in line:
            fails.append(f"{pathlib.Path(f).name}:{i}: selected-state button should be Ui.chip")

# 4. colour contrast of the text/background pairs the admin app actually uses (WCAG AA 4.5:1)
colors = dict(re.findall(r'<color name="(\w+)">(#[0-9A-Fa-f]{6})</color>', open(src / "res/values/colors.xml", encoding="utf-8").read()))
def lum(h):
    r, g, b = [int(h[i:i+2], 16) / 255 for i in (1, 3, 5)]
    f = lambda c: c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
def cr(a, b):
    x, y = sorted((lum(a), lum(b)), reverse=True); return (x + 0.05) / (y + 0.05)
pairs = [("bofo_text", "bofo_bg"), ("bofo_text_2", "bofo_bg"), ("bofo_text_2", "bofo_white"), ("bofo_success", "bofo_success_tint"), ("bofo_success", "bofo_white"),
         ("bofo_error", "bofo_error_tint"), ("bofo_error", "bofo_bg"), ("bofo_error", "bofo_white"), ("bofo_card_ink", "bofo_warn_tint"), ("bofo_text_2", "bofo_icon_bg"),
         ("bofo_primary", "bofo_bg"), ("bofo_white", "bofo_primary"),
         ("bofo_primary", "bofo_primary_tint"), ("bofo_text_2", "bofo_primary_tint")]
for a, b in pairs:
    r = cr(colors[a], colors[b])
    if r < 4.5: fails.append(f"contrast {a} on {b}: {r:.2f} (< 4.5)")

for x in fails: print("FAIL", x)
print(f"SUMMARY: admin a11y check, {len(fails)} failure(s)")
sys.exit(1 if fails else 0)

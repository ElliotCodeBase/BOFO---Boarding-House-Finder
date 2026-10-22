"""Jump-loop sprite sheet for the loading screen. Reuses the mascot shapes from build.py.
Output: bofo-jump-spritesheet.png (16 frames in one row, 256x256 each, transparent) + bofo-jump-preview.gif"""
from PIL import Image, ImageDraw
import build

FW = FH = 256
GROUND = 232
UNIT = 0.95                      # pixels per design-grid unit at rest

def base(expr):
    return build.to_png(build.shapes(expr), 1024)

happy, cheer = base("happy"), base("waving")
BOX = happy.getbbox()            # same crop for both faces so they line up
happy, cheer = happy.crop(BOX), cheer.crop(BOX)
BW, BH = happy.size
K = UNIT * 200 / 1024            # 1024px render covers the 200-unit grid

# (squash-x, squash-y, height above ground in px, use cheerful face)   -> loops seamlessly
FRAMES = [
    (1.00, 1.00,   0, False),   # 0 rest
    (1.06, 0.93,   0, False),   # 1 anticipation
    (1.11, 0.85,   0, False),   # 2 deepest crouch
    (0.94, 1.10,   8, False),   # 3 launch (stretch)
    (0.95, 1.07,  26, False),   # 4 rising
    (0.98, 1.03,  41, True),    # 5 near apex
    (1.00, 1.00,  47, True),    # 6 apex
    (1.00, 1.00,  44, True),    # 7 start falling
    (0.98, 1.03,  34, True),    # 8
    (0.95, 1.07,  20, False),   # 9 falling (stretch)
    (0.95, 1.08,   6, False),   # 10 about to land
    (1.12, 0.84,   0, False),   # 11 impact squash
    (1.06, 0.92,   0, False),   # 12 rebound
    (0.98, 1.03,   3, False),   # 13 tiny settle hop
    (1.01, 0.99,   0, False),   # 14
    (1.00, 1.00,   0, False),   # 15
]
MAXH = 47

def frame(sx, sy, h, cheerful):
    im = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
    # ground shadow: smaller and fainter the higher the mascot is
    t = h / MAXH
    sw, sh = 118 * (1 - 0.35 * t), 15 * (1 - 0.35 * t)
    sh_layer = Image.new("RGBA", (FW * 4, FH * 4), (0, 0, 0, 0))
    ImageDraw.Draw(sh_layer).ellipse(((FW/2 - sw/2) * 4, (GROUND - sh/2 + 4) * 4, (FW/2 + sw/2) * 4, (GROUND + sh/2 + 4) * 4),
                                     fill=(74, 42, 143, int(70 * (1 - 0.5 * t))))
    im.alpha_composite(sh_layer.resize((FW, FH), Image.LANCZOS))
    src = cheer if cheerful else happy
    w, hh = round(BW * K * sx), round(BH * K * sy)
    sprite = src.resize((w, hh), Image.LANCZOS)
    im.alpha_composite(sprite, (round(FW / 2 - w / 2), round(GROUND - hh - h)))
    return im

frames = [frame(*f) for f in FRAMES]
sheet = Image.new("RGBA", (FW * len(frames), FH), (0, 0, 0, 0))
for i, f in enumerate(frames): sheet.paste(f, (i * FW, 0))
sheet.save("bofo-jump-spritesheet.png", optimize=True)

# GIF preview on the splash background colour (GIF has no partial transparency)
bg = (248, 245, 253, 255)
gif = []
for f in frames:
    c = Image.new("RGBA", (FW, FH), bg); c.alpha_composite(f); gif.append(c.convert("P", palette=Image.ADAPTIVE, colors=128))
gif[0].save("bofo-jump-preview.gif", save_all=True, append_images=gif[1:], duration=62, loop=0, disposal=2)
print(sheet.size, len(frames), "frames")

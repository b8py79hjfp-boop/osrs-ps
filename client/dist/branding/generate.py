#!/usr/bin/env python3
"""Generates the placeholder installer icons (app.png, app.ico, app.icns) next to this script. Needs Pillow.

Neutral stand-ins until the project picks a name and artwork (docs/plans/phase-6.md, "Needs from the user"). Replace
the three files with the real icon, keeping the names. No RuneLite or Jagex artwork is used.
"""
import os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SIZE = 1024
BG, RING, MARK = (32, 36, 46, 255), (214, 168, 64, 255), (240, 240, 240, 255)


def draw():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((32, 32, SIZE - 32, SIZE - 32), radius=200, fill=BG)
    d.ellipse((192, 192, SIZE - 192, SIZE - 192), outline=RING, width=64)
    # clock hands: a tick
    c = SIZE // 2
    d.line((c, c, c, 300), fill=MARK, width=56)
    d.line((c, c, 690, 600), fill=MARK, width=56)
    d.ellipse((c - 48, c - 48, c + 48, c + 48), fill=RING)
    return img


img = draw()
img.resize((512, 512), Image.LANCZOS).save(os.path.join(HERE, "app.png"))
img.save(os.path.join(HERE, "app.ico"), sizes=[(s, s) for s in (16, 32, 48, 64, 128, 256)])
img.save(os.path.join(HERE, "app.icns"))

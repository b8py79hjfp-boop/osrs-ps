#!/usr/bin/env python3
"""Generates the neutral placeholder branding images in resources/ (needs Pillow).

These are stand-ins until the project picks a name, icon and splash (docs/plans/phase-6.md, "Needs from the user").
Replace the PNGs in resources/ with the real artwork, keeping file names and sizes; build.sh copies the whole
resources/ tree over the fork's client resources. No RuneLite artwork is used.
"""
import os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "resources", "net", "runelite", "client")
SCALE = 8  # supersample for smooth edges

BG = (32, 36, 46, 255)
RING = (214, 168, 64, 255)
MARK = (240, 240, 240, 255)


def canvas(size):
    return Image.new("RGBA", (size * SCALE, size * SCALE), (0, 0, 0, 0))


def finish(img, size, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.resize((size, size), Image.LANCZOS).save(path, optimize=True)


def emblem(size, ring_width):
    """A dark disc with a gold ring and a hollow diamond."""
    s = size * SCALE
    img = canvas(size)
    d = ImageDraw.Draw(img)
    d.ellipse([0, 0, s - 1, s - 1], fill=RING)
    rw = ring_width * SCALE
    d.ellipse([rw, rw, s - 1 - rw, s - 1 - rw], fill=BG)
    c = s // 2
    r = s // 3
    d.polygon([(c, c - r), (c + r, c), (c, c + r), (c - r, c)], fill=MARK)
    r2 = r // 2
    d.polygon([(c, c - r2), (c + r2, c), (c, c + r2), (c - r2, c)], fill=BG)
    return img


def glyph_chat(size):
    s = size * SCALE
    img = canvas(size)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([s // 8, s // 6, s - s // 8, s - s // 3], radius=s // 8, fill=MARK)
    d.polygon([(s // 4, s - s // 3 - 2), (s // 4, s - s // 6), (s // 2, s - s // 3 - 2)], fill=MARK)
    return img


def glyph_flag(size):
    s = size * SCALE
    img = canvas(size)
    d = ImageDraw.Draw(img)
    d.rectangle([s // 4, s // 6, s // 4 + s // 12, s - s // 6], fill=MARK)
    d.polygon([(s // 4 + s // 12, s // 6), (s - s // 5, s // 3), (s // 4 + s // 12, s // 2)], fill=MARK)
    return img


finish(emblem(128, 6), 128, os.path.join(OUT, "ui", "runelite_128.png"))
finish(emblem(16, 1), 16, os.path.join(OUT, "ui", "runelite_16.png"))
finish(emblem(200, 8), 200, os.path.join(OUT, "ui", "runelite_splash.png"))
finish(glyph_chat(30), 30, os.path.join(OUT, "plugins", "info", "discord_icon.png"))
finish(glyph_flag(30), 30, os.path.join(OUT, "plugins", "info", "github_icon.png"))

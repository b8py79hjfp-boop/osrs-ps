#!/usr/bin/env python3
"""Generates the client branding images in resources/ (needs Pillow): the Lotus emblem (lotus.py) as window icon and
splash, plus two neutral info-panel glyphs. build.sh copies the whole resources/ tree over the fork's client
resources; file names and sizes must stay. No RuneLite artwork is used.
"""
import os
import sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lotus  # noqa: E402

# The splash wordmark font; any bold serif works. Without one the splash shows the emblem only.
SPLASH_FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"

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


lotus.emblem(128).save(os.path.join(OUT, "ui", "runelite_128.png"), optimize=True)
lotus.small_icon(16).save(os.path.join(OUT, "ui", "runelite_16.png"), optimize=True)
lotus.splash(200, SPLASH_FONT if os.path.isfile(SPLASH_FONT) else None).save(
    os.path.join(OUT, "ui", "runelite_splash.png"), optimize=True)
finish(glyph_chat(30), 30, os.path.join(OUT, "plugins", "info", "discord_icon.png"))
finish(glyph_flag(30), 30, os.path.join(OUT, "plugins", "info", "github_icon.png"))

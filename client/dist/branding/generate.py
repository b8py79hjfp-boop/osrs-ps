#!/usr/bin/env python3
"""Generates the installer icons (app.png, app.ico, app.icns) next to this script from the Lotus emblem
(client/runelite/branding/lotus.py). Needs Pillow. No RuneLite or Jagex artwork is used.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "runelite", "branding"))
import lotus  # noqa: E402

img = lotus.emblem(1024, supersample=2)
img.resize((512, 512)).save(os.path.join(HERE, "app.png"))
img.save(os.path.join(HERE, "app.ico"), sizes=[(s, s) for s in (16, 32, 48, 64, 128, 256)])
img.save(os.path.join(HERE, "app.icns"))

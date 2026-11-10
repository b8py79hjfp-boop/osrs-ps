"""The Lotus emblem, drawn in code (Pillow). Shared by branding/generate.py (client icon and splash) and
client/dist/branding/generate.py (installer icons). Original artwork; no RuneLite or Jagex material.

The emblem is a dark disc in a gold ring. Inside it a lotus flower (seven petals, rose to blush gradient, gold
centre) rises over a still-water line.
"""
import math
from PIL import Image, ImageDraw, ImageFilter

NIGHT_TOP = (28, 24, 52)
NIGHT_BOTTOM = (12, 14, 30)
GOLD = (222, 178, 82)
GOLD_DARK = (150, 108, 40)
PETAL_BASE = (196, 58, 118)
PETAL_TIP = (255, 196, 222)
PETAL_BACK_BASE = (140, 40, 98)
PETAL_BACK_TIP = (236, 150, 190)
WATER = (130, 196, 226)


def _vertical_gradient(w, h, top, bottom):
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
        for x in range(w):
            px[x, y] = c
    return img


def _petal_points(base, length, width, angle_deg, steps=48):
    """A pointed petal from `base`, `length` long, rotated `angle_deg` from straight up."""
    bx, by = base
    a = math.radians(angle_deg)
    pts_l, pts_r = [], []
    for i in range(steps + 1):
        s = i / steps
        half = width * (math.sin(math.pi * s) ** 0.85) * (1 - 0.35 * s)
        y = -length * s
        for sign, pts in ((-1, pts_l), (1, pts_r)):
            x = sign * half
            rx = x * math.cos(a) - y * math.sin(a)
            ry = x * math.sin(a) + y * math.cos(a)
            pts.append((bx + rx, by + ry))
    return pts_l + pts_r[::-1]


def _paste_petal(img, base, length, width, angle, c_base, c_tip):
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).polygon(_petal_points(base, length, width, angle), fill=255)
    # Gradient along the petal's own axis: dark at the base, light at the tip.
    grad = Image.new("RGBA", (w, h))
    gpx = grad.load()
    a = math.radians(angle)
    ux, uy = math.sin(a), -math.cos(a)
    bbox = mask.getbbox()
    if bbox is None:
        return
    x0, y0, x1, y1 = bbox
    for y in range(y0, y1):
        for x in range(x0, x1):
            t = ((x - base[0]) * ux + (y - base[1]) * uy) / length
            t = min(1.0, max(0.0, t))
            gpx[x, y] = tuple(int(c_base[i] + (c_tip[i] - c_base[i]) * t) for i in range(3)) + (255,)
    img.paste(grad, (0, 0), mask)
    # A soft centre vein.
    # Blended on its own layer: drawing a translucent line straight onto img would replace its alpha.
    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    tip = (base[0] + ux * length * 0.82, base[1] + uy * length * 0.82)
    vein = tuple(min(255, c + 30) for c in c_tip) + (110,)
    ImageDraw.Draw(overlay).line([base, tip], fill=vein, width=max(1, int(width * 0.06)))
    img.alpha_composite(overlay)


def flower(img, cx, cy, scale):
    """Draws the flower with its petals rising from (cx, cy); `scale` is the centre petal's length."""
    L = scale
    back = [(-62, 0.80, 0.30), (62, 0.80, 0.30), (-36, 0.92, 0.32), (36, 0.92, 0.32)]
    front = [(-50, 0.70, 0.30), (50, 0.70, 0.30), (-22, 0.88, 0.30), (22, 0.88, 0.30), (0, 1.0, 0.31)]
    for ang, ln, wd in back:
        _paste_petal(img, (cx, cy), L * ln, L * wd, ang, PETAL_BACK_BASE, PETAL_BACK_TIP)
    for ang, ln, wd in front[:-1]:
        _paste_petal(img, (cx, cy), L * ln, L * wd, ang, PETAL_BASE, PETAL_TIP)
    # Gold seed pod and stamens, partly behind the centre petal.
    d = ImageDraw.Draw(img)
    r = L * 0.07
    for k in range(-3, 4):
        x = cx + k * r * 0.7
        d.line([(x, cy - r * 0.2), (x + k * r * 0.25, cy - r * 1.9)], fill=GOLD + (255,), width=max(1, int(r * 0.22)))
    d.ellipse([cx - r * 1.5, cy - r * 1.0, cx + r * 1.5, cy + r * 0.4], fill=GOLD + (255,))
    ang, ln, wd = front[-1]
    _paste_petal(img, (cx, cy), L * ln, L * wd, ang, PETAL_BASE, PETAL_TIP)


def emblem(size, supersample=8, ring=0.055):
    """The round emblem at `size` px (square RGBA, transparent outside the disc)."""
    s = size * supersample
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    disc_mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(disc_mask).ellipse([0, 0, s - 1, s - 1], fill=255)
    img.paste(_vertical_gradient(s, s, GOLD, GOLD_DARK), (0, 0), disc_mask)
    rw = int(s * ring)
    inner = Image.new("L", (s, s), 0)
    ImageDraw.Draw(inner).ellipse([rw, rw, s - 1 - rw, s - 1 - rw], fill=255)
    img.paste(_vertical_gradient(s, s, NIGHT_TOP, NIGHT_BOTTOM), (0, 0), inner)

    art = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    cx, cy = s / 2, s * 0.64
    # Glow behind the flower.
    glow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    gr = s * 0.30
    ImageDraw.Draw(glow).ellipse([cx - gr, cy - gr * 1.5, cx + gr, cy + gr * 0.3], fill=(255, 120, 180, 70))
    art = Image.alpha_composite(art, glow.filter(ImageFilter.GaussianBlur(s * 0.06)))
    # Still water: two thin lines under the flower.
    d = ImageDraw.Draw(art)
    lw = max(1, int(s * 0.012))
    d.line([(s * 0.24, cy + s * 0.07), (s * 0.76, cy + s * 0.07)], fill=WATER + (170,), width=lw)
    d.line([(s * 0.34, cy + s * 0.12), (s * 0.66, cy + s * 0.12)], fill=WATER + (90,), width=lw)
    flower(art, cx, cy + s * 0.03, s * 0.42)
    art_masked = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    art_masked.paste(art, (0, 0), Image.composite(art.getchannel("A"), Image.new("L", (s, s), 0), inner))
    img = Image.alpha_composite(img, art_masked)
    return img.resize((size, size), Image.LANCZOS)


def small_icon(size, supersample=8):
    """For 16 px and similar: the flower alone on the disc, no water or glow, so it stays legible."""
    s = size * supersample
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(img).ellipse([0, 0, s - 1, s - 1], fill=NIGHT_TOP + (255,))
    flower(img, s / 2, s * 0.80, s * 0.62)
    return img.resize((size, size), Image.LANCZOS)


def splash(size, font_path=None, supersample=4):
    """Emblem over a LOTUS wordmark, square, transparent background."""
    from PIL import ImageFont
    s = size * supersample
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    em = emblem(int(size * 0.74), supersample=8).resize((int(s * 0.74), int(s * 0.74)), Image.LANCZOS)
    img.paste(em, (int((s - em.width) / 2), 0), em)
    if font_path:
        font = ImageFont.truetype(font_path, int(s * 0.16))
        d = ImageDraw.Draw(img)
        text = "LOTUS"
        spacing = s * 0.025
        widths = [d.textlength(ch, font=font) for ch in text]
        total = sum(widths) + spacing * (len(text) - 1)
        x = (s - total) / 2
        y = s * 0.78
        for ch, w in zip(text, widths):
            d.text((x + s * 0.006, y + s * 0.006), ch, font=font, fill=(0, 0, 0, 160))
            d.text((x, y), ch, font=font, fill=GOLD + (255,))
            x += w + spacing
    return img.resize((size, size), Image.LANCZOS)

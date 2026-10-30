"""Builds the Amazon Appstore image assets from the app icon and palette."""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = "/scratch/work/lgk/store/amazon/"
FONT_DIR = "/scratch/work/fonts/"
SRC_ICON = "/scratch/work/lgk/screenshots/icon-512.png"

icon = Image.open(SRC_ICON).convert("RGBA")
icon.resize((512, 512), Image.LANCZOS).save(OUT + "icon-512.png")
icon.resize((114, 114), Image.LANCZOS).save(OUT + "icon-114.png")
print("icons:", Image.open(OUT + "icon-512.png").size, Image.open(OUT + "icon-114.png").size)

W, H = 1024, 500
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))


def lin_grad(w, h, c1, c2, p1, p2):
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    dx, dy = p2[0] - p1[0], p2[1] - p1[1]
    t = np.clip(((xx - p1[0]) * dx + (yy - p1[1]) * dy) / (dx * dx + dy * dy), 0, 1)[..., None]
    arr = np.array(c1, np.float32) + (np.array(c2, np.float32) - np.array(c1, np.float32)) * t
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


img.alpha_composite(lin_grad(W, H, (5, 10, 34, 255), (27, 7, 51, 255), (0, 0), (W * 0.72, H)))


def orb(size, color, alpha, stops=(0.0, 0.42, 0.72, 1.0), falloff=(1.0, 0.45, 0.12, 0.0)):
    n = size
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    c = (n - 1) / 2.0
    dist = np.clip(np.sqrt((xx - c) ** 2 + (yy - c) ** 2) / (n / 2.0), 0, 1)
    a = np.zeros((n, n), np.float32)
    for i in range(len(stops) - 1):
        m = (dist >= stops[i]) & (dist <= stops[i + 1])
        span = max(1e-6, stops[i + 1] - stops[i])
        a[m] = (falloff[i] + (falloff[i + 1] - falloff[i]) * ((dist[m] - stops[i]) / span)) * alpha
    arr = np.zeros((n, n, 4), np.uint8)
    arr[..., 0], arr[..., 1], arr[..., 2] = color
    arr[..., 3] = np.clip(a, 0, 255).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


for color, cx, cy, r, alpha in [((43, 107, 255), 120, -60, 520, 150),
                                ((255, 62, 165), 980, 120, 460, 130),
                                ((25, 224, 192), 60, 520, 440, 110),
                                ((255, 176, 46), 900, 470, 360, 90)]:
    tile = orb(int(r * 2), color, alpha).resize((int(r * 2), int(r * 2)), Image.LANCZOS)
    tmp = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    tmp.alpha_composite(tile, (int(cx - r), int(cy - r)))
    img.alpha_composite(tmp)

# glass panel behind the text, matching the app's key material
panel = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ImageDraw.Draw(panel).rounded_rectangle([36, 60, W - 36, H - 60], radius=34,
                                        fill=(255, 255, 255, 26),
                                        outline=(255, 255, 255, 70), width=2)
img.alpha_composite(panel)

ic = icon.resize((132, 132), Image.LANCZOS)
img.alpha_composite(ic, (84, 184))

d = ImageDraw.Draw(img)
title = ImageFont.truetype(FONT_DIR + "Roboto-Bold.ttf", 58)
sub = ImageFont.truetype(FONT_DIR + "Roboto-Regular.ttf", 26)
chip = ImageFont.truetype(FONT_DIR + "Roboto-Medium.ttf", 22)

d.text((248, 190), "Liquid Glass Keyboard", font=title, fill=(255, 255, 255, 255))
d.text((250, 262), "A glassy, fully offline keyboard for Android", font=sub,
       fill=(216, 224, 244, 255))

chips = ["No internet permission", "No ads", "No tracking"]
x = 250
for c in chips:
    tw = d.textlength(c, font=chip)
    box = [x, 312, x + tw + 40, 312 + 52]
    # shapes must be composited as their own layer: ImageDraw on RGBA writes raw
    # alpha instead of blending, which turns translucent fills into opaque white
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle(box, radius=26, fill=(255, 255, 255, 34),
                                            outline=(255, 255, 255, 95), width=2)
    img.alpha_composite(layer)
    d.text((x + 20, 312 + 26), c, font=chip, fill=(255, 255, 255, 255), anchor="lm")
    x += tw + 56

img.convert("RGB").save(OUT + "promo-1024x500.png", quality=95)
print("promo:", Image.open(OUT + "promo-1024x500.png").size)

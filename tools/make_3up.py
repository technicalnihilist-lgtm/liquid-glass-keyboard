"""Builds a 16:9 three-up image: three screens side by side on one canvas."""
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

SHOTS = "/scratch/work/lgk/store/amazon/"
FONT_DIR = "/scratch/work/fonts/"
OUT = SHOTS + "LiquidGlassKeyboard_3up_1920x1080.png"

W, H = 1920, 1080


def font(name, size):
    return ImageFont.truetype(FONT_DIR + name + ".ttf", size)


def lin_grad(w, h, c1, c2, p1, p2):
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    dx, dy = p2[0] - p1[0], p2[1] - p1[1]
    t = np.clip(((xx - p1[0]) * dx + (yy - p1[1]) * dy) / (dx * dx + dy * dy), 0, 1)[..., None]
    arr = np.array(c1, np.float32) + (np.array(c2, np.float32) - np.array(c1, np.float32)) * t
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def orb(size, color, alpha):
    n = size
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    c = (n - 1) / 2.0
    dist = np.clip(np.sqrt((xx - c) ** 2 + (yy - c) ** 2) / (n / 2.0), 0, 1)
    stops, falloff = (0.0, 0.42, 0.72, 1.0), (1.0, 0.45, 0.12, 0.0)
    a = np.zeros((n, n), np.float32)
    for i in range(3):
        m = (dist >= stops[i]) & (dist <= stops[i + 1])
        span = max(1e-6, stops[i + 1] - stops[i])
        a[m] = (falloff[i] + (falloff[i + 1] - falloff[i]) * ((dist[m] - stops[i]) / span)) * alpha
    arr = np.zeros((n, n, 4), np.uint8)
    arr[..., 0], arr[..., 1], arr[..., 2] = color
    arr[..., 3] = np.clip(a, 0, 255).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
img.alpha_composite(lin_grad(W, H, (5, 10, 34, 255), (27, 7, 51, 255), (0, 0), (W * 0.72, H)))
for color, cx, cy, r, alpha in [((43, 107, 255), 130, -80, 780, 150),
                                ((255, 62, 165), 1860, 220, 700, 135),
                                ((25, 224, 192), 60, 1150, 680, 115),
                                ((255, 176, 46), 1800, 1040, 540, 95)]:
    tile = orb(int(r * 2), color, alpha).resize((int(r * 2), int(r * 2)), Image.LANCZOS)
    tmp = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    tmp.alpha_composite(tile, (int(cx - r), int(cy - r)))
    img.alpha_composite(tmp)

d = ImageDraw.Draw(img)
d.text((W / 2, 28), "One keyboard. Every page.", font=font("Roboto-Medium", 32),
       fill=(255, 255, 255, 235), anchor="ma")

panels = [
    ("01-keyboard.png", 810, 132),   # letters, raised the least
    ("04-accent.png", 856, 92),      # accent picker, centre and tallest
    ("03-numbers.png", 810, 132),    # numbers page
]

heights = [p[1] for p in panels]
widths = [int(round(h * 1200 / 1920)) for h in heights]
gap = 66
total = sum(widths) + gap * 2
x = (W - total) // 2

for (shot, ph, top), pw in zip(panels, widths):
    y = top
    # shadow
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([x + 10, y + 22, x + pw + 10, y + ph + 22],
                                             radius=26, fill=(0, 0, 0, 135))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(22)))

    pic = Image.open(SHOTS + shot).convert("RGB").resize((pw, ph), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (pw, ph), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, pw - 1, ph - 1], radius=26, fill=255)
    pic.putalpha(mask)
    img.alpha_composite(pic, (x, y))

    frame = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle([x, y, x + pw, y + ph], radius=26,
                                            outline=(255, 255, 255, 105), width=2)
    img.alpha_composite(frame)
    x += pw + gap

img.convert("RGB").save(OUT, quality=95)
print("wrote", OUT, Image.open(OUT).size)

import os, math
from PIL import Image, ImageDraw, ImageFilter

SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
BASE = 512
OUT = "/scratch/work/lgk/app/res"
SS = 4  # supersample


def radial(size, color, cx, cy, r, alpha):
    """Soft radial glow as an RGBA layer."""
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = layer.load()
    n = size
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - cx, y - cy) / r
            if d >= 1:
                continue
            a = int(alpha * (1 - d) ** 1.6)
            if a > 0:
                px[x, y] = (color[0], color[1], color[2], a)
    return layer


def make_icon(round_icon=False):
    n = BASE * SS
    # background gradient
    bg = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    grad = Image.new("RGBA", (n, n))
    gp = grad.load()
    c1 = (10, 15, 44)
    c2 = (27, 11, 58)
    for y in range(n):
        for x in range(n):
            t = (x / n * 0.35 + y / n * 0.65)
            gp[x, y] = (
                int(c1[0] + (c2[0] - c1[0]) * t),
                int(c1[1] + (c2[1] - c1[1]) * t),
                int(c1[2] + (c2[2] - c1[2]) * t),
                255,
            )
    # orbs
    orbs = [
        ((43, 107, 255), n * 0.18, n * 0.10, n * 0.62, 150),
        ((255, 62, 165), n * 0.92, n * 0.30, n * 0.55, 135),
        ((25, 224, 192), n * 0.20, n * 0.92, n * 0.52, 120),
    ]
    small = n // 6
    for color, cx, cy, r, a in orbs:
        g = radial(small, color, small * (cx / n), small * (cy / n), max(1, small * (r / n)), a)
        g = g.resize((n, n), Image.BICUBIC).filter(ImageFilter.GaussianBlur(n // 90))
        bg = Image.alpha_composite(bg, g)
    bg = Image.alpha_composite(bg, grad) if False else Image.alpha_composite(bg, Image.new("RGBA", (n, n), (0, 0, 0, 0)))

    base = Image.alpha_composite(Image.new("RGBA", (n, n), (0, 0, 0, 0)), bg)
    # paint gradient under orbs
    under = grad.copy()
    img = Image.alpha_composite(under, bg)

    keys = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(keys)
    kw, kh = n * 0.19, n * 0.145
    gap = n * 0.035
    rows = [3, 3, 2]
    y = n * 0.40
    for ri, count in enumerate(rows):
        total = count * kw + (count - 1) * gap
        x = (n - total) / 2
        for i in range(count):
            box = [x, y, x + kw, y + kh]
            d.rounded_rectangle(box, radius=n * 0.045, fill=(255, 255, 255, 58),
                                outline=(255, 255, 255, 115), width=max(1, n // 220))
            d.rounded_rectangle([x + n * 0.022, y + n * 0.014, x + kw - n * 0.022, y + n * 0.030],
                                radius=n * 0.008, fill=(255, 255, 255, 120))
            x += kw + gap
        y += kh + gap
    img = Image.alpha_composite(img, keys)

    # mask
    mask = Image.new("L", (n, n), 0)
    md = ImageDraw.Draw(mask)
    if round_icon:
        md.ellipse([0, 0, n - 1, n - 1], fill=255)
    else:
        md.rounded_rectangle([0, 0, n - 1, n - 1], radius=n * 0.235, fill=255)
    img.putalpha(mask)

    return img.resize((BASE, BASE), Image.LANCZOS)


icon = make_icon(False)
round_icon = make_icon(True)
for bucket, size in SIZES.items():
    folder = os.path.join(OUT, "mipmap-" + bucket)
    os.makedirs(folder, exist_ok=True)
    icon.resize((size, size), Image.LANCZOS).save(os.path.join(folder, "ic_launcher.png"))
    round_icon.resize((size, size), Image.LANCZOS).save(os.path.join(folder, "ic_launcher_round.png"))

icon.save("/scratch/work/lgk/screenshots/icon-512.png")
print("icons written")

"""Builds the Amazon promo video: 1280x720, H.264, six cards with slow zoom."""
import os
import subprocess
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

FONT_DIR = "/scratch/work/fonts/"
SHOTS = "/scratch/work/lgk/store/amazon/"
ICON = "/scratch/work/lgk/screenshots/icon-512.png"
WORK = "/scratch/work/vid/"
OUT = SHOTS + "promo-1280x720.mp4"

W, H = 1280, 720
os.makedirs(WORK, exist_ok=True)


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


def backdrop():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    img.alpha_composite(lin_grad(W, H, (5, 10, 34, 255), (27, 7, 51, 255), (0, 0), (W * 0.72, H)))
    for color, cx, cy, r, alpha in [((43, 107, 255), 90, -40, 620, 150),
                                    ((255, 62, 165), 1240, 150, 540, 135),
                                    ((25, 224, 192), 40, 760, 520, 115),
                                    ((255, 176, 46), 1180, 700, 420, 95)]:
        tile = orb(int(r * 2), color, alpha).resize((int(r * 2), int(r * 2)), Image.LANCZOS)
        tmp = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        tmp.alpha_composite(tile, (int(cx - r), int(cy - r)))
        img.alpha_composite(tmp)
    return img


def glass_card(img, box, radius=26, fill=(255, 255, 255, 26), outline=(255, 255, 255, 70)):
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=2)
    img.alpha_composite(layer)


def screenshot_panel(img, path, x, y, height, radius=18):
    shot = Image.open(path).convert("RGB")
    w = int(round(height * shot.width / shot.height))
    shot = shot.resize((w, height), Image.LANCZOS)

    # soft shadow behind the device
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([x + 8, y + 16, x + w + 8, y + height + 16],
                                             radius=radius, fill=(0, 0, 0, 120))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(18)))

    mask = Image.new("L", (w, height), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, height - 1], radius=radius, fill=255)
    shot = shot.convert("RGBA")
    shot.putalpha(mask)
    img.alpha_composite(shot, (x, y))

    frame = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle([x, y, x + w, y + height], radius=radius,
                                            outline=(255, 255, 255, 90), width=2)
    img.alpha_composite(frame)
    return w


def text_block(img, x, y, kicker, headline, sub, head_size=44):
    d = ImageDraw.Draw(img)
    if kicker:
        f = font("Roboto-Medium", 17)
        tw = d.textlength(kicker, font=f)
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(layer).rounded_rectangle([x, y, x + tw + 34, y + 38], radius=19,
                                                fill=(255, 255, 255, 34),
                                                outline=(255, 255, 255, 95), width=2)
        img.alpha_composite(layer)
        d.text((x + 17, y + 19), kicker, font=f, fill=(255, 255, 255, 240), anchor="lm")
        y += 56

    fh = font("Roboto-Bold", head_size)
    for i, line in enumerate(headline.split("\n")):
        d.text((x, y), line, font=fh, fill=(255, 255, 255, 255))
        y += int(head_size * 1.18)
    y += 10
    fs = font("Roboto-Regular", 21)
    for line in sub.split("\n"):
        d.text((x, y), line, font=fs, fill=(206, 216, 240, 255))
        y += 30


def card_title(path):
    img = backdrop()
    d = ImageDraw.Draw(img)
    icon = Image.open(ICON).convert("RGBA").resize((128, 128), Image.LANCZOS)
    img.alpha_composite(icon, (86, 150))
    text_block(img, 86, 306, "", "Liquid Glass\nKeyboard",
               "A glassy, fully offline keyboard for Android", head_size=52)
    img.convert("RGB").save(path)


def card_screen(path, shot, kicker, headline, sub):
    img = backdrop()
    w = screenshot_panel(img, shot, 800, 50, 620)
    text_block(img, 86, 190, kicker, headline, sub)
    img.convert("RGB").save(path)


def card_end(path):
    img = backdrop()
    d = ImageDraw.Draw(img)
    icon = Image.open(ICON).convert("RGBA").resize((112, 112), Image.LANCZOS)
    img.alpha_composite(icon, (86, 128))
    text_block(img, 86, 262, "", "No internet\npermission",
               "Nothing you type can leave the phone.\nFree, MIT licensed, open source.", head_size=50)
    chips = ["No ads", "No tracking", "No accounts"]
    x, y = 86, 520
    f = font("Roboto-Medium", 19)
    for c in chips:
        tw = d.textlength(c, font=f)
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(layer).rounded_rectangle([x, y, x + tw + 40, y + 48], radius=24,
                                                fill=(255, 255, 255, 34),
                                                outline=(255, 255, 255, 95), width=2)
        img.alpha_composite(layer)
        d.text((x + 20, y + 24), c, font=f, fill=(255, 255, 255, 245), anchor="lm")
        x += tw + 56
    img.convert("RGB").save(path)


cards = []
p = WORK + "c0.png"
card_title(p)
cards.append(p)
for i, (shot, kicker, head, sub) in enumerate([
    ("01-keyboard.png", "GEN 2 GLASS", "Layered glass", "Specular rims, caustic light\nand contact shadows."),
    ("03-numbers.png", "LAYOUTS", "Numbers and\nsymbols", "Plus an emoji page\nand 48 characters."),
    ("04-accent.png", "ACCENTS", "Hold a key", "Slide to e with an accent\nand let go."),
    ("05-light.png", "THEMES", "Dark or light", "Or follow the system.\nThree key heights."),
], start=1):
    p = WORK + f"c{i}.png"
    card_screen(p, SHOTS + shot, kicker, head, sub)
    cards.append(p)
p = WORK + "c5.png"
card_end(p)
cards.append(p)

print("cards:", len(cards))

ffmpeg = __import__("imageio_ffmpeg").get_ffmpeg_exe()
segments = []
for i, c in enumerate(cards):
    seg = WORK + f"seg{i}.mp4"
    vf = ("scale=1408:792,"
          "zoompan=z='min(zoom+0.00055,1.09)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'"
          ":d=1:s=1280x720:fps=30,"
          "fade=t=in:st=0:d=0.45,fade=t=out:st=2.75:d=0.45,format=yuv420p")
    subprocess.run([ffmpeg, "-y", "-loop", "1", "-framerate", "30", "-t", "3.5", "-i", c,
                    "-vf", vf, "-c:v", "libx264", "-preset", "medium",
                    "-b:v", "3000k", "-maxrate", "4000k", "-bufsize", "6000k",
                    "-pix_fmt", "yuv420p", "-r", "30", seg],
                   check=True, capture_output=True)
    segments.append(seg)
    print("segment", i, "done")

lst = WORK + "list.txt"
with open(lst, "w") as f:
    for s in segments:
        f.write("file '%s'\n" % s)

# Amazon requires 1200 kbps or higher; concat alone can undershoot on simple
# frames, so the joined file is re-encoded with an explicit target bitrate.
joined = WORK + "joined.mp4"
subprocess.run([ffmpeg, "-y", "-f", "concat", "-safe", "0", "-i", lst,
                "-c", "copy", joined], check=True, capture_output=True)
subprocess.run([ffmpeg, "-y", "-i", joined, "-c:v", "libx264", "-preset", "medium",
                "-b:v", "3000k", "-minrate", "1500k", "-maxrate", "4000k",
                "-bufsize", "6000k", "-pix_fmt", "yuv420p", "-r", "30",
                "-movflags", "+faststart", OUT], check=True, capture_output=True)
print("wrote", OUT, os.path.getsize(OUT), "bytes")

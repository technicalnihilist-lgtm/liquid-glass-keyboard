"""Renders screenshots that mirror KeyboardView's Gen-2 glass maths."""
import math
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONT_DIR = "/scratch/work/fonts/"
OUT = os.environ.get("OUT_DIR", "/scratch/work/lgk/screenshots/")

# Canvas is chosen by pixel width and design width in dp, so one script renders
# both the phone set (1080 px / 360 dp) and the tablet set Amazon wants
# (1920 x 2560 px / 800 dp wide).
W = int(os.environ.get("W_PX", "1080"))
H = int(os.environ.get("H_PX", "2400"))
DP_W = float(os.environ.get("DP_W", "360"))
D = W / DP_W


def dp(v):
    return int(round(v * D))


def font(name, size_dp):
    return ImageFont.truetype(FONT_DIR + name + ".ttf", max(8, int(round(size_dp * D))))


F_LETTER = font("Roboto-Medium", 21)
F_FN = font("Roboto-Medium", 15)
F_TITLE = font("Roboto-Bold", 26)
F_BODY = font("Roboto-Regular", 15)
F_SMALL = font("Roboto-Regular", 12)


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def hsl(h, s, l, alpha=255):
    h = h % 360
    c = (1 - abs(2 * l - 1)) * s
    x = c * (1 - abs(((h / 60.0) % 2) - 1))
    m = l - c / 2
    seg = int(h // 60)
    r, g, b = [(c, x, 0), (x, c, 0), (0, c, x), (0, x, c), (x, 0, c), (c, 0, x)][seg]
    return (int(round((r + m) * 255)), int(round((g + m) * 255)),
            int(round((b + m) * 255)), alpha)


def with_alpha(color, a):
    return (color[0], color[1], color[2], int(max(0, min(255, a))))


DARK = dict(
    dark=True,
    bg1=(5, 10, 34), bg2=(27, 7, 51),
    ink=(255, 255, 255, 255), ink_dim=(255, 255, 255, 140),
    key_top=(255, 255, 255, 0x5C), key_mid=(255, 255, 255, 0x17), key_bot=(255, 255, 255, 0x07),
    rim_top=(255, 255, 255, 0xC7), rim_bot=(255, 255, 255, 0x10),
    edge=(255, 255, 255, 0x70), spec=(255, 255, 255, 0xD9),
    kb_top=(255, 255, 255, 0x2B), kb_mid=(255, 255, 255, 0x12), kb_bot=(255, 255, 255, 0x05),
    kb_edge=(255, 255, 255, 0x52), kb_spec=(255, 255, 255, 0x99),
    shadow=(0, 0, 0, 0x3D), contact=(0, 0, 0, 0x26),
    top_light=(255, 255, 255, 0x44), caustic=(255, 255, 255, 0x2C),
    inner=(0, 0, 0, 0x26),
    orbs=[((43, 107, 255), 0.58), ((255, 62, 165), 0.50), ((25, 224, 192), 0.44), ((255, 176, 46), 0.36)],
)

LIGHT = dict(
    dark=False,
    bg1=(198, 217, 255), bg2=(247, 217, 246),
    ink=(11, 18, 48, 255), ink_dim=(11, 18, 48, 140),
    key_top=(255, 255, 255, 0xE0), key_mid=(255, 255, 255, 0xA8), key_bot=(255, 255, 255, 0x5C),
    rim_top=(255, 255, 255, 0xFF), rim_bot=(255, 255, 255, 0x2E),
    edge=(255, 255, 255, 0xEB), spec=(255, 255, 255, 0xFF),
    kb_top=(255, 255, 255, 0x47), kb_mid=(255, 255, 255, 0x21), kb_bot=(255, 255, 255, 0x0F),
    kb_edge=(255, 255, 255, 0x5C), kb_spec=(255, 255, 255, 0xFF),
    shadow=(0, 0, 0, 0x26), contact=(0, 0, 0, 0x1A),
    top_light=(255, 255, 255, 0x66), caustic=(255, 255, 255, 0x40),
    inner=(0, 0, 0, 0x14),
    orbs=[((43, 107, 255), 0.35), ((255, 62, 165), 0.30), ((25, 224, 192), 0.26), ((255, 176, 46), 0.22)],
)


# ------------------------------------------------------------------ gradients

def lin_grad(w, h, stops, p1=(0, 0), p2=None):
    """Multi-stop linear gradient along p1 -> p2. stops = [(pos, rgba)]."""
    if p2 is None:
        p2 = (0, h)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    dx, dy = p2[0] - p1[0], p2[1] - p1[1]
    denom = dx * dx + dy * dy or 1.0
    t = np.clip(((xx - p1[0]) * dx + (yy - p1[1]) * dy) / denom, 0, 1)
    arr = np.zeros((h, w, 4), np.float32)
    for i in range(len(stops) - 1):
        p0, c0 = stops[i]
        p1_, c1 = stops[i + 1]
        m = (t >= p0) & (t <= p1_)
        span = max(1e-6, p1_ - p0)
        f = ((t - p0) / span)[..., None]
        c0 = np.array(c0, np.float32)
        c1 = np.array(c1, np.float32)
        seg = c0 + (c1 - c0) * f
        arr[m] = seg[m]
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def radial(size, color, alpha, stops=(0.0, 0.42, 0.72, 1.0), falloff=(1.0, 0.45, 0.12, 0.0)):
    n = max(2, int(size))
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    c = (n - 1) / 2.0
    dist = np.clip(np.sqrt((xx - c) ** 2 + (yy - c) ** 2) / (n / 2.0), 0, 1)
    a = np.zeros((n, n), np.float32)
    for i in range(len(stops) - 1):
        p0, f0 = stops[i], falloff[i]
        p1_, f1 = stops[i + 1], falloff[i + 1]
        m = (dist >= p0) & (dist <= p1_)
        span = max(1e-6, p1_ - p0)
        a[m] = (f0 + (f1 - f0) * ((dist[m] - p0) / span)) * alpha
    arr = np.zeros((n, n, 4), np.uint8)
    arr[..., 0], arr[..., 1], arr[..., 2] = color[0], color[1], color[2]
    arr[..., 3] = np.clip(a, 0, 255).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def round_mask(w, h, r, extra=0):
    w, h, extra = int(w), int(h), int(extra)
    m = Image.new("L", (w, h + extra), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w - 1, h + extra - 1], radius=r, fill=255)
    return m.crop((0, 0, w, h))


def ring_mask(w, h, r, width):
    m = Image.new("L", (int(w), int(h)), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, int(w) - 1, int(h) - 1], radius=r,
                                        outline=255, width=max(1, int(round(width))))
    return m


def apply_mask(layer, mask):
    layer = layer.copy()
    a = np.array(layer.split()[-1], np.float32) * (np.array(mask, np.float32) / 255.0)
    layer.putalpha(Image.fromarray(a.astype(np.uint8)))
    return layer


# ------------------------------------------------------------------ backdrop

def draw_backdrop(img, pal, w, h, dim=1.0):
    sky = lin_grad(int(w), int(h), [(0.0, with_alpha(pal["bg1"], 255 * dim)),
                                    (1.0, with_alpha(pal["bg2"], 255 * dim))],
                   (0, 0), (w * 0.72, h))
    layer = Image.new("RGBA", (int(w), int(h)), (0, 0, 0, 0))
    layer.alpha_composite(sky)
    m = max(w, h)
    positions = [(0.02, 0.00, 0.64), (1.00, 0.22, 0.56), (0.05, 1.02, 0.60), (0.95, 0.88, 0.44)]
    for (cx, cy, rr), (color, alpha) in zip(positions, pal["orbs"]):
        size = int(m * rr * 2)
        tile = radial(size, color, alpha * 255 * dim)
        tile = tile.resize((size, size), Image.LANCZOS)
        tmp = Image.new("RGBA", (int(w), int(h)), (0, 0, 0, 0))
        tmp.alpha_composite(tile, (int(cx * w - size / 2), int(cy * h - size / 2)))
        layer.alpha_composite(tmp)
    img.alpha_composite(layer, (0, 0))


# ------------------------------------------------------------------ keyboard

ROWS = {
    "letters": [
        [("q", 1), ("w", 1), ("e", 1), ("r", 1), ("t", 1), ("y", 1), ("u", 1), ("i", 1), ("o", 1), ("p", 1)],
        [("", 0.5), ("a", 1), ("s", 1), ("d", 1), ("f", 1), ("g", 1), ("h", 1), ("j", 1), ("k", 1), ("l", 1), ("", 0.5)],
        [("\u21E7", 1.5, "fn"), ("z", 1), ("x", 1), ("c", 1), ("v", 1), ("b", 1), ("n", 1), ("m", 1), ("\u232B", 1.5, "fn")],
        [("123", 1.3, "fn"), ("\u263A", 1, "fn"), (",", 0.9), ("space", 3.9, "fn"), (".", 0.9), ("return", 1.9, "fn")],
    ],
    "numbers": [
        [("1", 1), ("2", 1), ("3", 1), ("4", 1), ("5", 1), ("6", 1), ("7", 1), ("8", 1), ("9", 1), ("0", 1)],
        [("-", 1), ("/", 1), (":", 1), (";", 1), ("(", 1), (")", 1), ("\u20B9", 1), ("&", 1), ("@", 1), ("\"", 1)],
        [("#+=", 1.5, "fn"), (".", 1), (",", 1), ("?", 1), ("!", 1), ("'", 1), ("\u232B", 1.5, "fn")],
        [("ABC", 1.5, "fn"), ("\u263A", 1, "fn"), (",", 0.9), ("space", 3.7, "fn"), (".", 0.9), ("return", 1.9, "fn")],
    ],
}

GAP, SIDE, BAND = dp(6), dp(6), dp(44)
PAD_TOP, PAD_BOT, KBC = dp(10), dp(9), dp(30)
KH, KEY_R, NAV = dp(52), dp(9), dp(24)


def layout(rows, w):
    out, y = [], BAND + PAD_TOP
    avail = w - 2 * SIDE
    for row in rows:
        total = sum(k[1] for k in row)
        unit = (avail - GAP * (len(row) - 1)) / total
        x, rects = SIDE, []
        for k in row:
            kw = unit * k[1]
            rects.append((x, y, x + kw, y + KH))
            x += kw + GAP
        out.append(rects)
        y += KH + GAP
    return out


def soft_shadow(img, box, rad, color, dy, blur):
    x0, y0, x1, y1 = [int(round(v)) for v in box]
    pad = int(blur * 3)
    w, h = x1 - x0 + pad * 2, y1 - y0 + pad * 2
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle(
        [pad, pad + dy, pad + (x1 - x0), pad + (y1 - y0) + dy], radius=rad, fill=color)
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    img.alpha_composite(layer, (x0 - pad, y0 - pad))


def draw_key(img, pal, box, label, kind="ch", pressed=False, hue=205.0,
             shift_lit=False, intensity=1.0):
    x0, y0, x1, y1 = [int(round(v)) for v in box]
    w, h = x1 - x0, y1 - y0
    if w <= 4 or h <= 4 or not label:
        return

    soft_shadow(img, box, KEY_R, pal["shadow"], dp(3), dp(5))
    soft_shadow(img, box, KEY_R, pal["contact"], dp(1.4), dp(1.4))

    body = lin_grad(w, h, [(0.0, pal["key_top"]), (0.45, pal["key_mid"]), (1.0, pal["key_bot"])])
    tmp = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    tmp.alpha_composite(body)

    # light source, upper left
    tw = int(w * 1.9)
    tl = radial(tw, (255, 255, 255), pal["top_light"][3])
    tl = tl.resize((tw, tw), Image.LANCZOS)
    tmp.alpha_composite(tl, (int(w * 0.26 - tw / 2), int(h * 0.10 - tw / 2)))

    # caustic pool at the base
    cw = int(w * 1.7)
    ca = radial(cw, (255, 255, 255), pal["caustic"][3])
    ca = ca.resize((cw, cw), Image.LANCZOS)
    tmp.alpha_composite(ca, (int(w / 2 - cw / 2), int(h * 1.18 - cw / 2)))

    # inner shadow along the bottom
    ib = lin_grad(w, h, [(0.0, (0, 0, 0, 0)), (1.0, pal["inner"])],
                  (0, h - dp(9)), (0, h))
    tmp.alpha_composite(ib)

    if pressed:
        tint = lin_grad(w, h, [(0.0, hsl(hue, 1, 0.92, int(120 * intensity))),
                               (1.0, hsl(hue, 1, 0.62, int(55 * intensity)))])
        tmp.alpha_composite(tint)
        bw = int(w * 2.1)
        bloom = radial(bw, hsl(hue, 1, 0.97)[:3], 150 * intensity,
                       stops=(0.0, 0.5, 1.0), falloff=(1.0, 0.35, 0.0))
        bloom = bloom.resize((bw, bw), Image.LANCZOS)
        tmp.alpha_composite(bloom, (int(w / 2 - bw / 2), int(h / 2 - bw / 2)))

    if shift_lit:
        tmp.alpha_composite(lin_grad(w, h, [(0.0, (255, 255, 255, 0xF2)),
                                            (1.0, (255, 255, 255, 0xB8))]))

    tmp = apply_mask(tmp, round_mask(w, h, KEY_R))
    img.alpha_composite(tmp, (x0, y0))

    # inner rim, bright top fading down
    rim = lin_grad(w, h, [(0.0, pal["rim_top"]), (1.0, pal["rim_bot"])])
    rim = apply_mask(rim, ring_mask(w, h, KEY_R, dp(1.1)))
    img.alpha_composite(rim, (x0, y0))

    d = ImageDraw.Draw(img)
    edge = hsl(hue, 1, 0.86, int(245 * intensity)) if pressed else pal["edge"]
    d.rounded_rectangle([x0, y0, x1, y1], radius=KEY_R, outline=edge, width=max(1, int(dp(0.9))))

    if pressed:
        for i in range(3):
            spread = dp(1.5 + i * 5) * intensity
            d.rounded_rectangle([x0 - spread, y0 - spread, x1 + spread, y1 + spread],
                                radius=KEY_R + spread,
                                outline=hsl(hue, 1, 0.74, int(140 * intensity / (i + 1.4))),
                                width=max(1, int(dp(1.2))))

    # specular line along the top edge
    inset = KEY_R * 1.15
    spec_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(spec_layer).line(
        [inset, dp(2), w - inset, dp(2)],
        fill=hsl(hue, 1, 1.0, int(200 * intensity)) if pressed else pal["spec"],
        width=max(1, int(dp(1.3))))
    spec_layer = spec_layer.filter(ImageFilter.GaussianBlur(dp(0.7)))
    img.alpha_composite(spec_layer, (x0, y0))

    d = ImageDraw.Draw(img)
    if label in ("\u21E7", "\u232B", "\u263A"):
        draw_icon(d, label, (x0, y0, x1, y1), (11, 18, 48) if shift_lit else pal["ink"])
        return

    f = F_FN if kind == "fn" else F_LETTER
    if label == "space":
        f = font("Roboto-Regular", 15)
    color = (11, 18, 48) if shift_lit else pal["ink"]
    maxw = w - dp(10)
    tw2 = d.textlength(label, font=f)
    if tw2 > maxw > 0:
        f = ImageFont.truetype(f.path, max(8, int(f.size * maxw / tw2)))
    asc, desc = f.getmetrics()
    baseline = (y0 + y1) / 2 + (asc - desc) / 2
    if pal["dark"]:
        sh = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ImageDraw.Draw(sh).text((w / 2, baseline - y0 + dp(0.8)), label, font=f,
                                fill=(0, 0, 0, 0x40), anchor="ms")
        img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(dp(1.1))), (x0, y0))
    d.text(((x0 + x1) / 2, baseline), label, font=f, fill=color, anchor="ms")


def draw_icon(d, label, box, color):
    x0, y0, x1, y1 = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    w, h = x1 - x0, y1 - y0
    if label == "\u21E7":
        s = min(w, h) * 0.27
        d.polygon([(cx, cy - s), (cx + s * 0.88, cy - s * 0.02), (cx + s * 0.34, cy - s * 0.02),
                   (cx + s * 0.34, cy + s * 0.74), (cx - s * 0.34, cy + s * 0.74),
                   (cx - s * 0.34, cy - s * 0.02), (cx - s * 0.88, cy - s * 0.02)], fill=color)
    elif label == "\u232B":
        s = min(w, h) * 0.24
        lw = max(1, int(dp(1.7)))
        pts = [(cx - s * 1.25, cy), (cx - s * 0.35, cy - s * 0.88), (cx + s * 1.02, cy - s * 0.88),
               (cx + s * 1.34, cy - s * 0.72), (cx + s * 1.34, cy + s * 0.72),
               (cx + s * 1.02, cy + s * 0.88), (cx - s * 0.35, cy + s * 0.88),
               (cx - s * 1.25, cy)]
        d.line(pts, fill=color, width=lw, joint="curve")
        d.line([cx + s * 0.02, cy - s * 0.34, cx + s * 0.62, cy + s * 0.30], fill=color, width=lw)
        d.line([cx + s * 0.02, cy + s * 0.30, cx + s * 0.62, cy - s * 0.34], fill=color, width=lw)
    else:
        s = min(w, h) * 0.26
        lw = max(1, int(dp(1.7)))
        d.ellipse([cx - s, cy - s, cx + s, cy + s], outline=color, width=lw)
        r = dp(1.5)
        for dx in (-0.40, 0.40):
            ex, ey = cx + s * dx, cy - s * 0.30
            d.ellipse([ex - r, ey - r, ex + r, ey + r], fill=color)
        d.arc([cx - s * 0.58, cy - s * 0.22, cx + s * 0.58, cy + s * 0.60], 25, 155,
              fill=color, width=lw)


def draw_plate(img, pal, w, h):
    ph = h - BAND
    veil = lin_grad(w, ph, [(0.0, pal["kb_top"]), (0.42, pal["kb_mid"]), (1.0, pal["kb_bot"])])
    sheen = lin_grad(w, dp(26), [(0.0, (255, 255, 255, 0x1A)), (1.0, (255, 255, 255, 0x00))])
    under = lin_grad(w, dp(6), [(0.0, (0, 0, 0, 0x40)), (1.0, (0, 0, 0, 0x00))])
    plate = Image.new("RGBA", (w, ph), (0, 0, 0, 0))
    plate.alpha_composite(veil)
    plate.alpha_composite(sheen, (0, 0))
    plate.alpha_composite(under, (0, 0))
    plate = apply_mask(plate, round_mask(w, ph, KBC, extra=KBC))
    img.alpha_composite(plate, (0, BAND))

    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, BAND, w - 1, h + KBC], radius=KBC, outline=pal["kb_edge"],
                        width=max(1, int(dp(1))))
    spec = Image.new("RGBA", (w, ph), (0, 0, 0, 0))
    ImageDraw.Draw(spec).line([KBC * 0.65, dp(1.1), w - KBC * 0.65, dp(1.1)],
                              fill=pal["kb_spec"], width=max(1, int(dp(1.7))))
    img.alpha_composite(spec.filter(ImageFilter.GaussianBlur(dp(1.1))), (0, BAND))


def keyboard_height(nrows):
    return BAND + PAD_TOP + nrows * KH + (nrows - 1) * GAP + PAD_BOT + NAV


def render_keyboard(base, pal, rows, hue=205.0, press=None, shift_on=False,
                    accent_popup=None, dim=1.0):
    w = base.size[0]
    kb_h = int(round(keyboard_height(len(rows))))
    top = base.size[1] - kb_h
    view = Image.new("RGBA", (w, kb_h), (0, 0, 0, 0))
    draw_backdrop(view, pal, w, kb_h, dim=dim)
    view = apply_mask(view, round_mask(w, kb_h, KBC, extra=KBC))
    draw_plate(view, pal, w, kb_h)

    rects = layout(rows, w)
    for r, row in enumerate(rows):
        for c, k in enumerate(row):
            label = k[0]
            kind = k[2] if len(k) > 2 else "ch"
            if not label:
                continue
            if shift_on and kind == "ch":
                label = label.upper()
            pressed = press == (r, c)
            lit = kind == "fn" and k[0] == "\u21E7" and shift_on and not pressed
            draw_key(view, pal, rects[r][c], label, kind, pressed=pressed, hue=hue,
                     shift_lit=lit)

    if accent_popup and press:
        r, c = press
        box = rects[r][c]
        bw, bh = dp(44), KH * 0.95
        total = len(accent_popup) * bw + (len(accent_popup) - 1) * dp(3)
        x = max(dp(4), min(w - dp(4) - total, (box[0] + box[2]) / 2 - total / 2))
        y = max(dp(2), box[1] - bh - dp(8))
        for i, a in enumerate(accent_popup):
            sel = i == 1
            b = [x, y, x + bw, y + bh]
            soft_shadow(view, b, dp(16), (0, 0, 0, 0x44), dp(5), dp(4))
            fill = lin_grad(int(bw), int(bh),
                            [(0.0, hsl(hue, 1, 0.88, 245) if sel else (255, 255, 255, 0xF0)),
                             (1.0, hsl(hue, 1, 0.64, 225) if sel else (242, 245, 255, 0xD9))])
            fill = apply_mask(fill, round_mask(int(bw), int(bh), dp(16)))
            view.alpha_composite(fill, (int(x), int(y)))
            d = ImageDraw.Draw(view)
            d.rounded_rectangle(b, radius=dp(16),
                                outline=hsl(hue, 1, 0.72, 255) if sel else (255, 255, 255, 0xE6),
                                width=max(1, int(dp(1.2))))
            asc, desc = F_LETTER.getmetrics()
            d.text(((b[0] + b[2]) / 2, (b[1] + b[3]) / 2 + (asc - desc) / 2), a,
                   font=F_LETTER, fill=hexc("#0B1230"), anchor="ms")
            x += bw + dp(3)

    base.alpha_composite(view, (0, top))
    return top


# ------------------------------------------------------------------ app mock

def app_screen(size, dark=True, kb_top=None):
    w, h = size
    img = Image.new("RGBA", size, hexc("#0B1020") if dark else hexc("#F2F4FA"))
    d = ImageDraw.Draw(img)
    ink = (255, 255, 255) if dark else hexc("#0B1230")
    sub = (255, 255, 255, 150) if dark else (11, 18, 48, 140)
    d.text((dp(22), dp(36)), "Messages", font=F_TITLE, fill=ink)
    d.text((dp(22), dp(70)), "Aarav \u00b7 online", font=F_SMALL, fill=sub)

    f = F_BODY
    asc, desc = f.getmetrics()

    def draw_bubble(x, y, text, bw, right=False, accent=False):
        lines = text.split("\n")
        need = max(d.textlength(l, font=f) for l in lines) + dp(28)
        bw = max(bw, need)
        bw = min(bw, w - dp(40))
        bh = dp(18) + len(lines) * (asc + desc) + dp(4)
        box = [x - bw, y, x, y + bh] if right else [x, y, x + bw, y + bh]
        fill = (43, 107, 255, 235) if accent else ((255, 255, 255, 26) if dark else (255, 255, 255, 225))
        d.rounded_rectangle(box, radius=dp(18), fill=fill)
        color = (255, 255, 255) if (accent or dark) else hexc("#0B1230")
        ty = y + dp(11)
        for line in lines:
            d.text((box[0] + dp(14), ty), line, font=f, fill=color)
            ty += asc + desc
        return bh

    # Stack messages upward from the input bar so the mock fills a phone frame
    # and a tall tablet frame alike.
    messages = [
        ("Typing on glass is strange at first.", 300, False, False),
        ("Does it work offline though?", 260, True, False),
        ("Completely. No internet permission at all.", 320, False, False),
        ("Okay, that's actually impressive.", 280, True, False),
        ("Try the new keyboard yet?", 250, False, False),
        ("It's glass. And offline.", 236, True, True),
        ("Second generation \u2014 everything\nbehind it blurs through.", 300, False, False),
        ("No internet permission either, so\nnothing can leave the phone.", 310, True, False),
        ("Then it really is private.", 240, False, False),
    ]
    base = (kb_top if kb_top is not None else h) - dp(64)
    y = base - dp(14)
    for text, bw, right, accent in reversed(messages):
        lines = text.split("\n")
        bh = dp(18) + len(lines) * (asc + desc) + dp(4)
        if y - bh < dp(108):
            break
        y -= bh
        draw_bubble(dp(20) if not right else w - dp(20), y, text, bw, right, accent)
        y -= dp(12)
    return img


def input_bar(img, y, text, dark=True):
    w = img.size[0]
    d = ImageDraw.Draw(img)
    bar = [dp(14), y, w - dp(14), y + dp(50)]
    d.rounded_rectangle(bar, radius=dp(25),
                        fill=(255, 255, 255, 22) if dark else (255, 255, 255, 235),
                        outline=(255, 255, 255, 45) if dark else (0, 0, 0, 20),
                        width=max(1, int(dp(1))))
    f = font("Roboto-Regular", 17)
    ink = (255, 255, 255) if dark else hexc("#0B1230")
    d.text((bar[0] + dp(20), (bar[1] + bar[3]) / 2), text, font=f, fill=ink, anchor="lm")
    tw = d.textlength(text, font=f)
    cx = bar[0] + dp(20) + tw + dp(2)
    d.line([cx, bar[1] + dp(12), cx, bar[3] - dp(12)], fill=(120, 190, 255, 255),
           width=max(1, int(dp(2))))


def compose(name, pal, rows, typed, press=None, shift_on=False, accent_popup=None,
            dark_app=True, blur_behind=False):
    base = Image.new("RGBA", (W, H), hexc("#0B1020"))
    kb_h = int(round(keyboard_height(len(rows))))
    app = app_screen((W, H), dark=dark_app, kb_top=H - kb_h)
    input_bar(app, H - kb_h - dp(64), typed, dark=dark_app)
    if blur_behind:
        app = app.filter(ImageFilter.GaussianBlur(dp(3)))
    base.alpha_composite(app)

    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    render_keyboard(layer, pal, rows, hue=318.0 if press else 205.0, press=press,
                    shift_on=shift_on, accent_popup=accent_popup,
                    dim=0.85 if blur_behind else 1.0)
    base.alpha_composite(layer)

    d = ImageDraw.Draw(base)
    py = H - dp(9)
    d.rounded_rectangle([W / 2 - dp(50), py, W / 2 + dp(50), py + dp(3.5)],
                        radius=dp(3), fill=(255, 255, 255, 95) if pal["dark"] else (0, 0, 0, 70))
    base.convert("RGB").save(OUT + name, quality=95)
    print("wrote", name)


# ------------------------------------------------------------------ setup

def wrap(d, text, f, maxw):
    words, lines, cur = text.split(" "), [], ""
    for wd in words:
        t = (cur + " " + wd).strip()
        if d.textlength(t, font=f) <= maxw or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = wd
    if cur:
        lines.append(cur)
    return lines


def comp_round(img, box, radius, fill=None, outline=None, width=1):
    w, h = int(round(box[2] - box[0])), int(round(box[3] - box[1]))
    if w <= 0 or h <= 0:
        return
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle([0, 0, w - 1, h - 1], radius=radius,
                                            fill=fill, outline=outline, width=width)
    img.alpha_composite(layer, (int(round(box[0])), int(round(box[1]))))


def comp_ellipse(img, box, fill=None, outline=None, width=1):
    w, h = int(round(box[2] - box[0])), int(round(box[3] - box[1]))
    if w <= 0 or h <= 0:
        return
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse([0, 0, w - 1, h - 1], fill=fill, outline=outline, width=width)
    img.alpha_composite(layer, (int(round(box[0])), int(round(box[1]))))


def comp_text(img, xy, text, f, color, anchor=None):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).text(xy, text, font=f, fill=color, anchor=anchor)
    img.alpha_composite(layer)


def slider_block(img, x, y, w, pal, label, value, pct):
    d = ImageDraw.Draw(img)
    f = font("Roboto-Regular", 16)
    fv = font("Roboto-Medium", 15)
    comp_text(img, (x, y), label, f, pal["ink"])
    comp_text(img, (x + w, y), value, fv, pal["ink_dim"], anchor="ra")
    ty = y + dp(26)
    track = [x, ty, x + w, ty + dp(8)]
    comp_round(img, track, dp(4), fill=(255, 255, 255, 56) if pal["dark"] else (11, 18, 48, 40))
    fill_w = max(dp(4), int(w * pct))
    comp_round(img, [x, ty, x + fill_w, ty + dp(8)], dp(4), fill=(255, 255, 255, 235) if pal["dark"] else (255, 255, 255, 255))
    thumb_cx = x + fill_w
    comp_round(img, [thumb_cx - dp(15), ty - dp(8), thumb_cx + dp(15), ty + dp(16)], dp(12),
               fill=(255, 255, 255, 255), outline=(0, 0, 0, 40), width=1)


def switch_row(img, x, y, w, pal, label, on):
    comp_text(img, (x, y + dp(8)), label, font("Roboto-Regular", 16), pal["ink"])
    sw_w, sw_h = dp(54), dp(32)
    sx = x + w - sw_w
    fill = (52, 199, 89, 255) if on else ((255, 255, 255, 60) if pal["dark"] else (11, 18, 48, 40))
    comp_round(img, [sx, y + dp(4), sx + sw_w, y + dp(4) + sw_h], dp(16), fill=fill,
               outline=(255, 255, 255, 90) if pal["dark"] else (255, 255, 255, 220), width=1)
    tx = sx + sw_w - dp(16) if on else sx + dp(16)
    comp_ellipse(img, [tx - dp(13), y + dp(7), tx + dp(13), y + dp(33)], fill=(255, 255, 255, 255))


def segment_row(img, x, y, w, pal, labels, selected):
    seg = [x, y, x + w, y + dp(46)]
    comp_round(img, seg, dp(23), fill=(255, 255, 255, 30) if pal["dark"] else (255, 255, 255, 150),
               outline=(255, 255, 255, 80) if pal["dark"] else (255, 255, 255, 230), width=1)
    bw = (w - dp(8)) / len(labels)
    for i, lab in enumerate(labels):
        bx = x + dp(4) + i * bw
        if i == selected:
            comp_round(img, [bx, y + dp(4), bx + bw, y + dp(42)], dp(19),
                       fill=(255, 255, 255, 90) if pal["dark"] else (255, 255, 255, 235),
                       outline=(255, 255, 255, 130) if pal["dark"] else (255, 255, 255, 255), width=1)
        comp_text(img, (bx + bw / 2, y + dp(23)), lab, font("Roboto-Medium", 15),
                  pal["ink"], anchor="mm")


def render_setup(name, pal):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_backdrop(img, pal, W, H)
    dm = ImageDraw.Draw(Image.new("RGB", (8, 8)))
    ink = pal["ink"]
    card_fill = (255, 255, 255, 34) if pal["dark"] else (255, 255, 255, 160)
    card_edge = (255, 255, 255, 82) if pal["dark"] else (255, 255, 255, 235)
    sub_ink = (255, 255, 255, 180) if pal["dark"] else (11, 18, 48, 165)
    status_ink = (255, 255, 255, 228) if pal["dark"] else ink
    sec_ink = (255, 255, 255, 175) if pal["dark"] else (11, 18, 48, 145)
    x = dp(22)
    inner = dp(18)

    def card(y0, h):
        comp_round(img, [x, y0, W - x, y0 + h], dp(26), fill=card_fill, outline=card_edge,
                   width=max(1, int(dp(1))))
        spec = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(spec).line([x + dp(30), y0 + dp(1.1), W - x - dp(30), y0 + dp(1.1)],
                                  fill=(255, 255, 255, 90 if pal["dark"] else 200),
                                  width=max(1, int(dp(1.6))))
        img.alpha_composite(spec.filter(ImageFilter.GaussianBlur(dp(1.0))))

    def section(y0, text):
        comp_text(img, (x + inner, y0), text, font("Roboto-Bold", 12), sec_ink)
        return y0 + dp(22)

    def button(y0, label):
        box = [x + inner, y0, W - x - inner, y0 + dp(50)]
        comp_round(img, box, dp(15),
                   fill=(255, 255, 255, 58) if pal["dark"] else (255, 255, 255, 205),
                   outline=(255, 255, 255, 107) if pal["dark"] else (255, 255, 255, 235),
                   width=max(1, int(dp(1))))
        comp_text(img, ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2), label,
                  font("Roboto-Medium", 16), ink, anchor="mm")
        return y0 + dp(58)

    y = dp(40)
    comp_text(img, (x, y), "Liquid Glass", font("Roboto-Bold", 30), ink)
    y += dp(40)
    comp_text(img, (x, y), "Generation 3 — tune the glass yourself",
              font("Roboto-Regular", 15), sub_ink)
    y += dp(26)
    f_status = font("Roboto-Regular", 14)
    for line in wrap(dm, "Enabled. Tap \u201cChoose keyboard\u201d and pick Liquid Glass.",
                     f_status, W - 2 * x):
        comp_text(img, (x, y), line, f_status, status_ink)
        y += dp(18)
    y += dp(12)

    # SETUP
    card(y, dp(160))
    cy = section(y + dp(14), "SETUP")
    cy = button(cy, "1 \u00b7 Enable keyboard")
    button(cy, "2 \u00b7 Choose keyboard")
    y += dp(160) + dp(10)

    # TRY IT
    card(y, dp(120))
    cy = section(y + dp(14), "TRY IT")
    field = [x + inner, cy, W - x - inner, cy + dp(74)]
    comp_round(img, field, dp(18),
               fill=(255, 255, 255, 26) if pal["dark"] else (255, 255, 255, 215),
               outline=(255, 255, 255, 64) if pal["dark"] else (255, 255, 255, 240),
               width=max(1, int(dp(1))))
    comp_text(img, (field[0] + dp(14), field[1] + dp(13)), "Tap here and start typing\u2026",
              font("Roboto-Regular", 17),
              (255, 255, 255, 120) if pal["dark"] else (11, 18, 48, 110))
    y += dp(120) + dp(10)

    # GLASS
    card(y, dp(272))
    cy = section(y + dp(14), "GLASS")
    sx = x + inner
    sw = W - 2 * x - 2 * inner
    slider_block(img, sx, cy, sw, pal, "Transparency", "14%", 0.31)
    slider_block(img, sx, cy + dp(56), sw, pal, "Frosting", "22 px", 0.44)
    slider_block(img, sx, cy + dp(112), sw, pal, "Colour boost", "170%", 0.35)
    switch_row(img, sx, cy + dp(168), sw, pal, "Floating colours", True)
    y += dp(272) + dp(10)

    # KEYBOARD
    card(y, dp(216))
    cy = section(y + dp(14), "KEYBOARD")
    slider_block(img, sx, cy, sw, pal, "Key height", "52 px", 0.44)
    slider_block(img, sx, cy + dp(56), sw, pal, "Space between keys", "6 px", 0.33)
    switch_row(img, sx, cy + dp(112), sw, pal, "Glow on press", True)
    y += dp(216) + dp(10)

    # APPEARANCE
    card(y, dp(296))
    cy = section(y + dp(14), "APPEARANCE")
    segment_row(img, sx, cy, sw, pal, ["Auto", "Light", "Dark"], 0)
    slider_block(img, sx, cy + dp(58), sw, pal, "Bounce", "70%", 0.7)
    switch_row(img, sx, cy + dp(114), sw, pal, "Key click sound", True)
    switch_row(img, sx, cy + dp(160), sw, pal, "Haptic tap", True)

    img.convert("RGB").save(OUT + name, quality=95)
    print("wrote", name)


compose("01-keyboard.png", DARK, ROWS["letters"], "Meet the glass keyboard",
        press=(2, 4))
compose("02-shift.png", DARK, ROWS["letters"], "Hello there", press=(2, 3), shift_on=True)
compose("03-numbers.png", DARK, ROWS["numbers"], "https://")
compose("04-accent.png", DARK, ROWS["letters"], "caf", press=(2, 6),
        accent_popup=["n", "\u00f1", "\u0144"])
compose("05-light.png", LIGHT, ROWS["letters"], "Light glass", press=(1, 3), dark_app=False)
render_setup("06-setup.png", DARK)
print("done")

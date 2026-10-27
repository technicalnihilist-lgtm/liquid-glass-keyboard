"""Renders marketing screenshots that mirror KeyboardView's exact drawing maths."""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont

FONT_DIR = "/scratch/work/fonts/"
OUT = "/scratch/work/lgk/screenshots/"

D = 3.0  # px per dp (xxhdpi)
W, H = 1080, 2400


def dp(v):
    return int(round(v * D))


def font(name, size_dp):
    return ImageFont.truetype(FONT_DIR + name + ".ttf", int(round(size_dp * D)))


F_LETTER = font("Roboto-Medium", 21)
F_FN = font("Roboto-Medium", 15)
F_ICON = font("Roboto-Medium", 21)
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


class Palette:
    def __init__(self, dark=True):
        self.dark = dark
        if dark:
            self.bg1, self.bg2 = hexc("#0A0F2C"), hexc("#1B0B3A")
            self.ink, self.sub = hexc("#FFFFFF"), (255, 255, 255, 153)
            self.key_top, self.key_bot = (255, 255, 255, 0x42), (255, 255, 255, 0x12)
            self.key_edge, self.key_hl = (255, 255, 255, 0x61), (255, 255, 255, 0xB3)
            self.kb_top, self.kb_bot, self.kb_edge = ((255, 255, 255, 0x1F),
                                                      (255, 255, 255, 0x08),
                                                      (255, 255, 255, 0x38))
        else:
            self.bg1, self.bg2 = hexc("#CFE0FF"), hexc("#F4D6F5")
            self.ink, self.sub = hexc("#0B1230"), (11, 18, 48, 140)
            self.key_top, self.key_bot = (255, 255, 255, 0xB3), (255, 255, 255, 0x33)
            self.key_edge, self.key_hl = (255, 255, 255, 0xE6), (255, 255, 255, 0xFF)
            self.kb_top, self.kb_bot, self.kb_edge = ((255, 255, 255, 0x1F),
                                                      (255, 255, 255, 0x08),
                                                      (255, 255, 255, 0x38))
        self.orb1, self.orb2 = hexc("#2B6BFF"), hexc("#FF3EA5")
        self.orb3, self.orb4 = hexc("#19E0C0"), hexc("#FFB02E")


def lin_grad(w, h, c1, c2, p1=(0, 0), p2=None):
    """Linear gradient image of size w x h projected on the p1->p2 axis."""
    if p2 is None:
        p2 = (0, h)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    dx, dy = p2[0] - p1[0], p2[1] - p1[1]
    denom = dx * dx + dy * dy
    t = ((xx - p1[0]) * dx + (yy - p1[1]) * dy) / denom
    t = np.clip(t, 0, 1)[..., None]
    c1 = np.array(c1, np.float32)
    c2 = np.array(c2, np.float32)
    arr = c1 + (c2 - c1) * t
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def radial(size, color, alpha, power=1.0):
    """Radial glow tile: full alpha at centre fading to 0 at the edge."""
    n = size
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    c = (n - 1) / 2.0
    dist = np.sqrt((xx - c) ** 2 + (yy - c) ** 2) / (n / 2.0)
    t = np.clip(dist, 0, 1)
    # stops: alpha at 0, 0.42a at 0.45, 0 at 1
    a = np.where(t < 0.45, alpha * (1 - (t / 0.45) * 0.58), alpha * 0.42 * (1 - (t - 0.45) / 0.55))
    a = np.clip(a, 0, 255) ** power if power != 1.0 else np.clip(a, 0, 255)
    arr = np.zeros((n, n, 4), np.uint8)
    arr[..., 0] = color[0]
    arr[..., 1] = color[1]
    arr[..., 2] = color[2]
    arr[..., 3] = a.astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def paste(img, layer, mask=None):
    if mask is not None:
        layer = layer.copy()
        if layer.size != mask.size:
            layer = layer.resize(mask.size, Image.LANCZOS)
        a = np.array(layer.split()[-1], np.float32) * (np.array(mask, np.float32) / 255.0)
        layer.putalpha(Image.fromarray(a.astype(np.uint8)))
    img.alpha_composite(layer)


def round_mask(w, h, r, top_only=False, bottom_extra=0):
    w, h, bottom_extra = int(w), int(h), int(bottom_extra)
    m = Image.new("L", (w, h + bottom_extra), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, w - 1, h + bottom_extra - 1], radius=r, fill=255)
    return m.crop((0, 0, w, h))


def draw_backdrop(img, x0, y0, w, h, pal):
    """GlassBackdrop.draw: sky gradient plus four orbs."""
    sky = lin_grad(int(w), int(h), pal.bg1, pal.bg2, (0, 0), (w * 0.7, h))
    layer = Image.new("RGBA", (int(w), int(h)), (0, 0, 0, 0))
    layer.alpha_composite(sky)
    m = max(w, h)
    k = 1.0 if pal.dark else 0.55
    orbs = [(0.02 * w, 0.00 * h, 0.62 * m, pal.orb1, 0.55 * k),
            (1.00 * w, 0.22 * h, 0.55 * m, pal.orb2, 0.48 * k),
            (0.05 * w, 1.02 * h, 0.60 * m, pal.orb3, 0.42 * k),
            (0.95 * w, 0.90 * h, 0.42 * m, pal.orb4, 0.34 * k)]
    for cx, cy, r, color, a in orbs:
        size = int(r * 2)
        if size < 4:
            continue
        tile = radial(size, color, int(255 * a))
        tile = tile.resize((size, size), Image.LANCZOS)
        tmp = Image.new("RGBA", (int(w), int(h)), (0, 0, 0, 0))
        tmp.alpha_composite(tile, (int(cx - size / 2), int(cy - size / 2)))
        layer.alpha_composite(tmp)
    img.alpha_composite(layer, (int(x0), int(y0)))


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

GAP, SIDE, BAND, PAD_TOP, PAD_BOT, KBC = dp(6), dp(6), dp(46), dp(10), dp(9), dp(26)
KH = dp(52)
KEY_R = dp(11)
NAV = dp(24)


def layout(rows, w):
    out = []
    y = BAND + PAD_TOP
    avail = w - 2 * SIDE
    for row in rows:
        total = sum(k[1] for k in row)
        gaps = GAP * (len(row) - 1)
        unit = (avail - gaps) / total
        x = SIDE
        rects = []
        for k in row:
            kw = unit * k[1]
            rects.append((x, y, x + kw, y + KH))
            x += kw + GAP
        out.append(rects)
        y += KH + GAP
    return out


def draw_key(img, pal, box, label, kind="ch", shift_on=False, intensity=0.0, hue=200.0):
    x0, y0, x1, y1 = [int(round(v)) for v in box]
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0 or not label:
        return

    # drop shadow
    for dy, a in ((dp(3), 0x1C), (dp(6), 0x10)):
        sh = Image.new("RGBA", (w, h + int(dp(8))), (0, 0, 0, 0))
        ImageDraw.Draw(sh).rounded_rectangle([0, int(dy), w - 1, h - 1 + int(dy)],
                                             radius=KEY_R, fill=(0, 0, 0, a))
        img.alpha_composite(sh, (x0, y0))

    if intensity > 0:
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        r = int(w * 0.95)
        tile = radial(r * 2, hsl(hue, 1, 0.68), int(95 * intensity))
        tmp = Image.new("RGBA", (int(r * 2), int(r * 2)), (0, 0, 0, 0))
        tmp.alpha_composite(tile)
        img.alpha_composite(tmp, (int(cx - r), int(cy - r)))

    # body
    top = (255, 255, 255, 0xE6) if shift_on else pal.key_top
    bot = (255, 255, 255, 0x8C) if shift_on else pal.key_bot
    body = lin_grad(w, h, top, bot)
    if intensity > 0:
        glow = lin_grad(w, h, hsl(hue, 1, 0.85, int(150 * intensity)),
                        hsl(hue, 1, 0.65, int(70 * intensity)))
        body.alpha_composite(glow)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=KEY_R, fill=255)
    body.putalpha(Image.fromarray(
        (np.array(body.split()[-1], np.float32) * (np.array(mask, np.float32) / 255.0)).astype(np.uint8)))
    img.alpha_composite(body, (x0, y0))

    d = ImageDraw.Draw(img)
    edge = hsl(hue, 1, 0.78, int(235 * intensity)) if intensity > 0 else pal.key_edge
    d.rounded_rectangle([x0, y0, x1, y1], radius=KEY_R, outline=edge, width=max(1, int(dp(1))))
    if intensity > 0:
        for i in range(3):
            spread = dp(1.5 + i * 5) * intensity
            d.rounded_rectangle([x0 - spread, y0 - spread, x1 + spread, y1 + spread],
                                radius=KEY_R + spread,
                                outline=hsl(hue, 1, 0.72, int(130 * intensity / (i + 1.4))),
                                width=max(1, int(dp(1.2))))

    # top highlight
    inset = KEY_R * 0.95
    d.line([x0 + inset, y0 + dp(2.2), x1 - inset, y0 + dp(2.2)],
           fill=(255, 255, 255, 255) if shift_on else pal.key_hl,
           width=max(1, int(dp(1.4))))

    if label in ("\u21E7", "\u232B", "\u263A"):
        draw_icon(d, label, (x0, y0, x1, y1), color=(11, 18, 48) if shift_on else pal.ink)
        return

    if kind == "fn":
        f = F_ICON if label in ("\u21E7", "\u232B") else F_FN
        color = hexc("#0B1230") if shift_on else pal.ink
        if label == "space":
            f = font("Roboto-Regular", 15)
    else:
        f = F_LETTER
        color = hexc("#0B1230") if shift_on else pal.ink

    maxw = w - dp(10)
    tw = d.textlength(label, font=f)
    if tw > maxw > 0:
        size = f.size * maxw / tw
        f = ImageFont.truetype(f.path, max(8, int(size)))

    ascent, descent = f.getmetrics()
    baseline = (y0 + y1) / 2 + (ascent - descent) / 2
    d.text(((x0 + x1) / 2, baseline), label, font=f, fill=color, anchor="ms")


def draw_icon(d, label, box, color):
    x0, y0, x1, y1 = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    w, h = x1 - x0, y1 - y0
    if label == "\u21E7":            # shift arrow
        s = min(w, h) * 0.27
        pts = [(cx, cy - s), (cx + s * 0.88, cy - s * 0.02), (cx + s * 0.34, cy - s * 0.02),
               (cx + s * 0.34, cy + s * 0.74), (cx - s * 0.34, cy + s * 0.74),
               (cx - s * 0.34, cy - s * 0.02), (cx - s * 0.88, cy - s * 0.02)]
        d.polygon(pts, fill=color)
    elif label == "\u232B":          # backspace
        s = min(w, h) * 0.24
        lw = max(1, int(dp(1.7)))
        pts = [(cx - s * 1.25, cy), (cx - s * 0.35, cy - s * 0.88), (cx + s * 1.02, cy - s * 0.88),
               (cx + s * 1.34, cy - s * 0.72), (cx + s * 1.34, cy + s * 0.72),
               (cx + s * 1.02, cy + s * 0.88), (cx - s * 0.35, cy + s * 0.88),
               (cx - s * 1.25, cy)]
        d.line(pts, fill=color, width=lw, joint="curve")
        d.line([cx + s * 0.02, cy - s * 0.34, cx + s * 0.62, cy + s * 0.30], fill=color, width=lw)
        d.line([cx + s * 0.02, cy + s * 0.30, cx + s * 0.62, cy - s * 0.34], fill=color, width=lw)
    else:                              # emoji face
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
    plate = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ph = h - BAND
    grad = lin_grad(w, ph, pal.kb_top, pal.kb_bot)
    mask = round_mask(w, ph, KBC, bottom_extra=int(KBC))
    grad.putalpha(Image.fromarray(
        (np.array(grad.split()[-1], np.float32) * (np.array(mask, np.float32) / 255.0)).astype(np.uint8)))
    plate.alpha_composite(grad, (0, BAND))
    d = ImageDraw.Draw(plate)
    d.rounded_rectangle([0, BAND, w - 1, h + KBC], radius=KBC, outline=pal.kb_edge,
                        width=max(1, int(dp(1))))
    plate = plate.crop((0, 0, w, h))
    img.alpha_composite(plate)


def keyboard_height(nrows):
    return BAND + PAD_TOP + nrows * KH + (nrows - 1) * GAP + PAD_BOT + NAV


def render_keyboard(base, pal, rows, hue=200.0, press=None, shift_on=False,
                    accent_popup=None):
    """Draws the keyboard onto `base` at the bottom of the image."""
    w = base.size[0]
    kb_h = int(round(keyboard_height(len(rows))))
    top = base.size[1] - kb_h
    view = Image.new("RGBA", (w, kb_h), (0, 0, 0, 0))
    draw_backdrop(view, 0, 0, w, kb_h, pal)
    mask = round_mask(w, kb_h, KBC, bottom_extra=int(KBC))
    a = np.array(view.split()[-1], np.float32) * (np.array(mask, np.float32) / 255.0)
    view.putalpha(Image.fromarray(a.astype(np.uint8)))
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
            hit = press is not None and press == (r, c)
            draw_key(view, pal, rects[r][c], label, kind, shift_on=shift_on,
                     intensity=1.0 if hit else 0.0, hue=hue)

    # accent popup above the pressed key
    if accent_popup and press:
        r, c = press
        box = rects[r][c]
        alts = accent_popup
        bw, bh = dp(44), KH * 0.95
        total = len(alts) * bw + (len(alts) - 1) * dp(3)
        x = (box[0] + box[2]) / 2 - total / 2
        x = max(dp(4), min(w - dp(4) - total, x))
        y = box[1] - bh - dp(8)
        d = ImageDraw.Draw(view)
        for i, a in enumerate(alts):
            b = [x, y, x + bw, y + bh]
            sel = i == 1
            bb = lin_grad(int(bw), int(bh),
                          hsl(hue, 1, 0.86, 240) if sel else (255, 255, 255, 242),
                          hsl(hue, 1, 0.66, 220) if sel else (244, 246, 255, 230))
            m2 = Image.new("L", (int(bw), int(bh)), 0)
            ImageDraw.Draw(m2).rounded_rectangle([0, 0, bw - 1, bh - 1], radius=dp(15), fill=255)
            bb.putalpha(Image.fromarray(
                (np.array(bb.split()[-1], np.float32) * (np.array(m2, np.float32) / 255.0)).astype(np.uint8)))
            view.alpha_composite(bb, (int(x), int(y)))
            d.rounded_rectangle(b, radius=dp(15),
                                outline=hsl(hue, 1, 0.72, 255) if sel else (255, 255, 255, 204),
                                width=max(1, int(dp(1.2))))
            f = F_LETTER
            asc, desc = f.getmetrics()
            d.text(((b[0] + b[2]) / 2, (b[1] + b[3]) / 2 + (asc - desc) / 2), a,
                   font=f, fill=hexc("#0B1230"), anchor="ms")
            x += bw + dp(3)

    base.alpha_composite(view, (0, top))
    return top


# --------------------------------------------------------------- app mocks

def app_header(img, title, subtitle=None, dark=True):
    d = ImageDraw.Draw(img)
    bg = hexc("#0B1020") if dark else hexc("#F3F5FB")
    img.paste(Image.new("RGBA", img.size, bg), (0, 0))
    ink = hexc("#FFFFFF") if dark else hexc("#0B1230")
    sub = (255, 255, 255, 150) if dark else (11, 18, 48, 140)
    d.text((dp(22), dp(38)), title, font=F_TITLE, fill=ink)
    if subtitle:
        d.text((dp(22), dp(72)), subtitle, font=F_SMALL, fill=sub)


def bubble(img, x, y, text, w, right=False, dark=True, accent=False):
    d = ImageDraw.Draw(img)
    f = F_BODY
    asc, desc = f.getmetrics()
    h = dp(20) + (asc + desc)
    lines = text.split("\n")
    h = dp(18) + len(lines) * (asc + desc) + dp(4)
    box = [x - w, y, x, y + h] if right else [x, y, x + w, y + h]
    if accent:
        fill = (43, 107, 255, 235)
    else:
        fill = (255, 255, 255, 26) if dark else (255, 255, 255, 220)
    d.rounded_rectangle(box, radius=dp(18), fill=fill)
    color = hexc("#FFFFFF") if (accent or dark) else hexc("#0B1230")
    ty = y + dp(11)
    for line in lines:
        d.text((box[0] + dp(14), ty), line, font=f, fill=color)
        ty += asc + desc


def compose(name, pal, rows, typed, title, subtitle, press=None, shift_on=False,
            accent_popup=None, dark_app=True, input_hint="Message"):
    base = Image.new("RGBA", (W, H), hexc("#0B1020"))
    kb_top = render_keyboard(base, pal, rows, hue=320.0 if press else 200.0,
                            press=press, shift_on=shift_on, accent_popup=accent_popup)

    app = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    app_header(app, title, subtitle, dark=dark_app)
    ink = hexc("#FFFFFF") if dark_app else hexc("#0B1230")
    d = ImageDraw.Draw(app)

    bubble(app, dp(20), dp(112), "Try the new keyboard yet?", dp(240), dark=dark_app)
    bubble(app, W - dp(20), dp(180), "It's glassy. And offline.", dp(228), right=True,
           dark=dark_app, accent=True)

    # input bar
    bar_y = kb_top - dp(64)
    bar = [dp(14), bar_y, W - dp(14), bar_y + dp(50)]
    d.rounded_rectangle(bar, radius=dp(25), fill=(255, 255, 255, 22) if dark_app else (255, 255, 255, 230),
                        outline=(255, 255, 255, 45) if dark_app else (0, 0, 0, 20),
                        width=max(1, int(dp(1))))
    f = font("Roboto-Regular", 17)
    if typed:
        d.text((bar[0] + dp(20), (bar[1] + bar[3]) / 2), typed, font=f, fill=ink, anchor="lm")
        tw = d.textlength(typed, font=f)
        cx = bar[0] + dp(20) + tw + dp(2)
        d.line([cx, bar[1] + dp(12), cx, bar[3] - dp(12)], fill=(120, 190, 255, 255),
               width=max(1, int(dp(2))))
    else:
        d.text((bar[0] + dp(20), (bar[1] + bar[3]) / 2), input_hint, font=f,
               fill=(255, 255, 255, 110) if dark_app else (11, 18, 48, 110), anchor="lm")

    base.alpha_composite(app)
    # re-composite the keyboard on top so it is never covered
    kb_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    render_keyboard(kb_layer, pal, rows, hue=320.0 if press else 200.0, press=press,
                    shift_on=shift_on, accent_popup=accent_popup)
    base.alpha_composite(kb_layer)

    # gesture pill
    d = ImageDraw.Draw(base)
    py = H - dp(9)
    d.rounded_rectangle([W / 2 - dp(50), py, W / 2 + dp(50), py + dp(3.5)],
                        radius=dp(3), fill=(255, 255, 255, 90) if pal.dark else (0, 0, 0, 70))
    base.convert("RGB").save(OUT + name, quality=95)
    print("wrote", name)


# --------------------------------------------------------------- setup screen

def wrap(d, text, f, maxw):
    words, lines, cur = text.split(" "), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if d.textlength(t, font=f) <= maxw or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = w
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


def comp_text(img, xy, text, font, color, anchor=None):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).text(xy, text, font=font, fill=color, anchor=anchor)
    img.alpha_composite(layer)


def render_setup(name, pal):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_backdrop(img, 0, 0, W, H, pal)
    dm = ImageDraw.Draw(Image.new("RGB", (8, 8)))
    ink = pal.ink
    card_fill = (255, 255, 255, 34) if pal.dark else (255, 255, 255, 150)
    card_edge = (255, 255, 255, 77) if pal.dark else (255, 255, 255, 235)
    sub_ink = (255, 255, 255, 180) if pal.dark else (11, 18, 48, 165)
    status_ink = (255, 255, 255, 228) if pal.dark else ink
    sec_ink = (255, 255, 255, 175) if pal.dark else (11, 18, 48, 145)
    radio = hexc("#7FB0FF")
    f_body = font("Roboto-Regular", 15)
    x = dp(22)

    def radio_row(y0, label, sel):
        cx = x + dp(20)
        cy = y0 + dp(5)
        comp_ellipse(img, [cx, cy, cx + dp(15), cy + dp(15)], outline=radio,
                     width=max(1, int(dp(1.6))))
        if sel:
            comp_ellipse(img, [cx + dp(4.2), cy + dp(4.2), cx + dp(10.8), cy + dp(10.8)], fill=radio)
        comp_text(img, (cx + dp(26), cy + dp(7.5)), label, f_body, ink, anchor="lm")

    def card(y0, h):
        comp_round(img, [x, y0, W - x, y0 + h], dp(24), fill=card_fill, outline=card_edge,
                   width=max(1, int(dp(1))))

    def section(y0, text):
        comp_text(img, (x + dp(16), y0), text, font("Roboto-Bold", 12), sec_ink)
        return y0 + dp(20)

    y = dp(40)
    comp_text(img, (x, y), "Liquid Glass", font("Roboto-Bold", 30), ink)
    y += dp(40)
    comp_text(img, (x, y), "A glassy, fully offline keyboard", font("Roboto-Regular", 15), sub_ink)
    y += dp(24)
    f_status = font("Roboto-Regular", 14)
    for line in wrap(dm, "Enabled. Tap \u201cChoose keyboard\u201d and pick Liquid Glass.",
                     f_status, W - 2 * x):
        comp_text(img, (x, y), line, f_status, status_ink)
        y += dp(18)
    y += dp(12)

    card(y, dp(164))
    cy = section(y + dp(14), "SETUP")
    for label in ("1 \u00b7 Enable keyboard", "2 \u00b7 Choose keyboard"):
        box = [x + dp(16), cy, W - x - dp(16), cy + dp(50)]
        comp_round(img, box, dp(15),
                   fill=(255, 255, 255, 52) if pal.dark else (255, 255, 255, 200),
                   outline=(255, 255, 255, 100) if pal.dark else (255, 255, 255, 235),
                   width=max(1, int(dp(1))))
        comp_text(img, ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2), label,
                  font("Roboto-Medium", 16), ink, anchor="mm")
        cy += dp(56)
    y += dp(164) + dp(10)

    card(y, dp(136))
    cy = section(y + dp(14), "TRY IT")
    field = [x + dp(16), cy, W - x - dp(16), cy + dp(88)]
    comp_round(img, field, dp(17),
               fill=(255, 255, 255, 26) if pal.dark else (255, 255, 255, 210),
               outline=(255, 255, 255, 64) if pal.dark else (255, 255, 255, 240),
               width=max(1, int(dp(1))))
    comp_text(img, (field[0] + dp(14), field[1] + dp(13)), "Tap here and start typing\u2026",
              font("Roboto-Regular", 17),
              (255, 255, 255, 120) if pal.dark else (11, 18, 48, 110))
    y += dp(136) + dp(10)

    card(y, dp(224))
    cy = section(y + dp(14), "APPEARANCE")
    for label, sel in (("Match system", False), ("Dark glass", True), ("Light glass", False)):
        radio_row(cy, label, sel)
        cy += dp(25)
    cy = section(cy + dp(10), "KEY HEIGHT")
    for label, sel in (("Small", False), ("Medium", True), ("Large", False)):
        radio_row(cy, label, sel)
        cy += dp(25)
    y += dp(224) + dp(10)

    card(y, dp(94))
    cy = section(y + dp(14), "FEEDBACK")
    for label in ("Key click sound", "Haptic tap"):
        radio_row(cy, label, True)
        cy += dp(25)

    img.convert("RGB").save(OUT + name, quality=95)
    print("wrote", name)


dark = Palette(True)
light = Palette(False)

compose("01-keyboard.png", dark, ROWS["letters"], "Meet the liquid glass keyboard",
        "Messages", "Aarav \u00b7 online", press=(2, 4), shift_on=False)
compose("02-shift.png", dark, ROWS["letters"], "Hello there", "Messages",
        "Aarav \u00b7 online", press=(2, 3), shift_on=True)
compose("03-numbers.png", dark, ROWS["numbers"], "https://", "Browser", "Search or type a URL",
        input_hint="Search or type a URL")
compose("04-accent.png", dark, ROWS["letters"], "caf", "Notes", "Draft \u00b7 saved on device",
        press=(2, 6), accent_popup=["n", "\u00f1", "\u0144"])
compose("05-light.png", light, ROWS["letters"], "Light glass, dark glass", "Messages",
        "Aarav \u00b7 online", press=(1, 3), dark_app=False)
render_setup("06-setup.png", dark)
print("done")

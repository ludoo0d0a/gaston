#!/usr/bin/env python3
"""Generate all Gaston Play Store + Android launcher assets using Pillow."""

from PIL import Image, ImageDraw, ImageFont
import os, math

# ─── brand palette ────────────────────────────────────────────────────────────
BG       = (45,  27,  78)    # #2D1B4E  dark purple
BG2      = (22,  10,  48)    # deeper bg for gradient bottom
ACCENT1  = (124, 77,  255)   # #7C4DFF  bright violet
ACCENT2  = (179, 136, 255)   # #B388FF  lavender
ACCENT3  = (209, 196, 233)   # #D1C4E9  mist
SPHERE   = (107, 78,  170)   # #6B4EAA  sphere
WHITE    = (255, 255, 255)
GOLD     = (255, 210, 40)    # fuel price
EV_GREEN = (64,  196, 130)   # EV accent
CARD_BG  = (60,  38,  105)   # card surface
DARK_BAR = (18,  8,   40)    # status / nav bar

ASSETS_DIR = "playstore-assets"
RES_DIR    = "androidApp/src/main/res"

os.makedirs(ASSETS_DIR, exist_ok=True)

# ─── fonts ────────────────────────────────────────────────────────────────────
_FONT_PATH_BOLD   = "/System/Library/Fonts/HelveticaNeue.ttc"
_FONT_PATH_REGULAR = "/System/Library/Fonts/Helvetica.ttc"

def font(size, bold=False):
    path = _FONT_PATH_BOLD if bold else _FONT_PATH_REGULAR
    try:
        return ImageFont.truetype(path, size)
    except Exception:
        return ImageFont.load_default()

# ─── helpers ──────────────────────────────────────────────────────────────────
def gradient_bg(img_size, top_color=BG, bottom_color=BG2):
    """Return an RGB Image filled with a vertical gradient."""
    w, h = img_size
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(h - 1, 1)
        c = tuple(int(top_color[i] + t * (bottom_color[i] - top_color[i])) for i in range(3))
        d.line([(0, y), (w, y)], fill=c)
    return img

def circle(d, cx, cy, r, fill=None, outline=None, width=2):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill, outline=outline, width=width)

def rounded_rect(d, x0, y0, x1, y1, r=18, fill=None, outline=None, width=1):
    d.rounded_rectangle([x0, y0, x1, y1], radius=r, fill=fill, outline=outline, width=width)

def text_center(d, text, cx, y, fnt, fill=WHITE):
    bb = d.textbbox((0, 0), text, font=fnt)
    tw = bb[2] - bb[0]
    d.text((cx - tw // 2, y), text, font=fnt, fill=fill)

def text_right(d, text, rx, y, fnt, fill=WHITE):
    bb = d.textbbox((0, 0), text, font=fnt)
    tw = bb[2] - bb[0]
    d.text((rx - tw, y), text, font=fnt, fill=fill)

def draw_pin(d, cx, cy, size, color_outer=WHITE, color_inner=SPHERE, bolt=True):
    """Map pin: circle head + pointed tail + optional lightning bolt."""
    hr = int(size * 0.40)
    tail_h = int(size * 0.58)
    tw_half = int(hr * 0.54)

    # Head
    d.ellipse([cx - hr, cy - hr, cx + hr, cy + hr], fill=color_outer)
    # Tail
    d.polygon([
        (cx - tw_half, cy + hr - 3),
        (cx + tw_half, cy + hr - 3),
        (cx,           cy + hr + tail_h),
    ], fill=color_outer)
    # Inner
    ir = int(hr * 0.52)
    d.ellipse([cx - ir, cy - ir, cx + ir, cy + ir], fill=color_inner)
    if bolt:
        # Lightning bolt (pixel-perfect at any size)
        b = ir * 0.60
        pts = [
            (int(cx + b * 0.22),  int(cy - b)),
            (int(cx - b * 0.14),  int(cy - b * 0.05)),
            (int(cx + b * 0.32),  int(cy - b * 0.05)),
            (int(cx - b * 0.22),  int(cy + b)),
            (int(cx + b * 0.14),  int(cy + b * 0.05)),
            (int(cx - b * 0.32),  int(cy + b * 0.05)),
        ]
        d.polygon(pts, fill=GOLD)


# ─── 1. LAUNCHER ICON ────────────────────────────────────────────────────────
def draw_station_icon(d, cx, cy, size, color=WHITE, cutout_color=BG):
    """Draw a modern, bold station pump silhouette with a lightning bolt cutout."""
    s = size
    # Main body
    w = s * 0.55
    h = s * 0.85
    x0, y0 = cx - w/2, cy - h/2
    x1, y1 = cx + w/2, cy + h/2
    d.rounded_rectangle([x0, y0, x1, y1], radius=s*0.08, fill=color)

    # The hose/nozzle on the side
    hw = s * 0.12
    hh = h * 0.6
    hx0 = x1 - s*0.02
    hy0 = y0 + h * 0.1
    hx1 = x1 + hw
    hy1 = hy0 + hh
    d.rounded_rectangle([hx0, hy0, hx1, hy1], radius=s*0.04, fill=color)

    # Nozzle tip / connection
    d.rectangle([hx0 - s*0.05, hy0, hx1 - s*0.02, hy0 + s*0.1], fill=color)

    # Cutout: Screen
    sw = w * 0.65
    sh = h * 0.25
    sx0, sy0 = cx - sw/2, y0 + h * 0.12
    sx1, sy1 = cx + sw/2, sy0 + sh
    d.rounded_rectangle([sx0, sy0, sx1, sy1], radius=s*0.04, fill=cutout_color)

    # Cutout: Lightning bolt in the lower part
    bw = w * 0.35
    bh = h * 0.35
    bcx, bcy = cx, y1 - h * 0.3

    pts = [
        (bcx + bw * 0.2, bcy - bh * 0.5),
        (bcx - bw * 0.4, bcy + bh * 0.05),
        (bcx + bw * 0.1, bcy + bh * 0.05),
        (bcx - bw * 0.2, bcy + bh * 0.5),
        (bcx + bw * 0.4, bcy - bh * 0.05),
        (bcx - bw * 0.1, bcy - bh * 0.05),
    ]
    d.polygon(pts, fill=cutout_color)

def make_icon(size=512):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img, "RGBA")
    cx = cy = size // 2

    # Rounded-square background
    d.rounded_rectangle([0, 0, size, size], radius=size // 8, fill=BG)

    # Subtle radial gradient overlay (brighter centre)
    for step in range(int(size * 0.50), 0, -1):
        t = step / (size * 0.50)
        alpha = int(30 * (1 - t))
        d.ellipse([cx - step, cy - step, cx + step, cy + step],
                  fill=(*ACCENT1, alpha))

    # Concentric glow rings
    rings = [(0.44, ACCENT1, 45), (0.36, ACCENT2, 65), (0.28, ACCENT3, 85)]
    for r_f, col, alpha in rings:
        r  = int(size * r_f)
        sw = max(2, size // 90)
        d.ellipse([cx - r, cy - r, cx + r, cy + r],
                  outline=(*col, alpha), width=sw)

    # Central sphere
    sr = int(size * 0.215)
    for step in range(sr, 0, -1):
        t   = step / sr
        col = tuple(int(SPHERE[i] * t + ACCENT1[i] * (1 - t)) for i in range(3))
        d.ellipse([cx - step, cy - step, cx + step, cy + step], fill=col)

    # Main Station Icon
    icon_size = int(size * 0.26)
    draw_station_icon(d, cx, cy, icon_size, color=WHITE, cutout_color=SPHERE)

    # Soft highlight on sphere top-left
    h_r = max(3, int(size * 0.035))
    h_cx = cx - int(size * 0.08)
    h_cy = cy - int(size * 0.12)
    d.ellipse([h_cx - h_r, h_cy - h_r, h_cx + h_r, h_cy + h_r],
              fill=(*WHITE, 60))

    return img


# ─── 2. FEATURE GRAPHIC ───────────────────────────────────────────────────────
def make_feature(w=1024, h=500):
    img = gradient_bg((w, h), BG, BG2)
    d   = ImageDraw.Draw(img, "RGBA")

    # Right-side decorative rings
    rcx, rcy = int(w * 0.80), h // 2
    for r_f, col, alpha in [(0.55, ACCENT1, 18), (0.42, ACCENT2, 28), (0.30, ACCENT3, 38)]:
        r = int(h * r_f)
        d.ellipse([rcx - r, rcy - r, rcx + r, rcy + r],
                  outline=(*col, alpha), width=3)

    # Decorative station icons (right side)
    for px, py, sz, col in [
        (int(w * 0.71), int(h * 0.30), 40, ACCENT1),
        (int(w * 0.83), int(h * 0.55), 30, EV_GREEN),
        (int(w * 0.91), int(h * 0.28), 25, GOLD),
        (int(w * 0.76), int(h * 0.72), 22, ACCENT2),
    ]:
        draw_station_icon(d, px, py, sz, color=col, cutout_color=BG)

    # Left text block
    tx = int(w * 0.055)
    ty = int(h * 0.14)

    fn_big  = font(int(h * 0.32), bold=True)
    fn_tag  = font(int(h * 0.105))
    fn_sub  = font(int(h * 0.072))

    d.text((tx, ty),                               "Gaston",                          font=fn_big,  fill=WHITE)
    # Accent underline
    d.rectangle([tx, ty + int(h * 0.365), tx + int(w * 0.295), ty + int(h * 0.380)],
                fill=ACCENT1)
    d.text((tx, ty + int(h * 0.42)),               "Fuel & EV stations · Real prices", font=fn_tag,  fill=ACCENT2)
    d.text((tx, ty + int(h * 0.61)),               "Nearby · On-route · Android Auto ready",
           font=fn_sub, fill=ACCENT3)

    return img


# ─── shared screenshot chrome ─────────────────────────────────────────────────
W, H = 1080, 1920

def screenshot_base(title="Gaston", back=True):
    img = gradient_bg((W, H), BG, BG2)
    d   = ImageDraw.Draw(img, "RGBA")

    # Status bar
    d.rectangle([0, 0, W, 72], fill=DARK_BAR)
    fn_status = font(28)
    d.text((40, 22), "9:41", font=fn_status, fill=WHITE)
    text_right(d, "●●●  WiFi  🔋", W - 32, 22, fn_status, fill=ACCENT3)

    # App bar
    bar_top = 72
    bar_h   = 128
    d.rectangle([0, bar_top, W, bar_top + bar_h], fill=(*BG, 245))

    fn_title = font(58, bold=True)
    if back:
        d.text((40, bar_top + 30), "‹", font=font(72), fill=ACCENT2)
    d.text((back and 120 or 48, bar_top + 34), title, font=fn_title, fill=WHITE)

    content_top = bar_top + bar_h + 12
    return img, d, content_top


def station_card(d, x, y, cw, ch, name, label, value, val_color, sub="", badge=None):
    rounded_rect(d, x, y, x + cw, y + ch, r=20, fill=CARD_BG)
    fn_n = font(int(ch * 0.195))
    fn_l = font(int(ch * 0.155))
    fn_v = font(int(ch * 0.245), bold=True)
    fn_s = font(int(ch * 0.135))
    d.text((x + 28, y + int(ch * 0.10)), name, font=fn_n, fill=WHITE)
    d.text((x + 28, y + int(ch * 0.47)), label, font=fn_l, fill=ACCENT3)
    text_right(d, value, x + cw - 28, y + int(ch * 0.30), fn_v, fill=val_color)
    if sub:
        d.text((x + 28, y + int(ch * 0.70)), sub, font=fn_s, fill=ACCENT2)
    if badge:
        bx = x + cw - 28 - 180
        by = y + int(ch * 0.65)
        rounded_rect(d, bx, by, bx + 170, by + 44, r=22, fill=val_color)
        fn_b = font(int(ch * 0.13))
        d.text((bx + 14, by + 10), badge, font=fn_b, fill=DARK_BAR)


def cta_button(d, label, color=ACCENT1, text_color=WHITE):
    btn_h = 108
    y = H - 160
    rounded_rect(d, 48, y, W - 48, y + btn_h, r=54, fill=color)
    fn = font(52, bold=True)
    text_center(d, label, W // 2, y + 24, fn, fill=text_color)


# ─── screenshot 1: Map view with pinned stations ──────────────────────────────
def make_ss_map():
    img, d, top = screenshot_base("Gaston", back=False)

    # Map placeholder
    map_y, map_h = top + 8, int(H * 0.46)
    rounded_rect(d, 20, map_y, W - 20, map_y + map_h, r=28, fill=(30, 16, 60))

    # Subtle grid (roads)
    for i in range(1, 5):
        yy = map_y + int(map_h * i / 5)
        d.line([(20, yy), (W - 20, yy)], fill=(50, 30, 85), width=2)
    for i in range(1, 5):
        xx = 20 + int((W - 40) * i / 5)
        d.line([(xx, map_y), (xx, map_y + map_h)], fill=(50, 30, 85), width=2)

    # Station pins
    pins = [
        (int(W * 0.26), map_y + int(map_h * 0.36), ACCENT1, True,  "1.89€"),
        (int(W * 0.55), map_y + int(map_h * 0.52), EV_GREEN, False, "350kW"),
        (int(W * 0.73), map_y + int(map_h * 0.28), GOLD,    False, "1.74€"),
        (int(W * 0.38), map_y + int(map_h * 0.72), ACCENT2, True,  "1.91€"),
    ]
    for px, py, col, bolt, price_lbl in pins:
        draw_pin(d, px, py, 44, color_outer=col, color_inner=BG, bolt=bolt)
        fn_price = font(28)
        bb = d.textbbox((0, 0), price_lbl, font=fn_price)
        tw = bb[2] - bb[0]
        lx = px - tw // 2
        rounded_rect(d, lx - 8, py - 86, lx + tw + 8, py - 52, r=10, fill=(*DARK_BAR, 200))
        d.text((lx, py - 82), price_lbl, font=fn_price, fill=WHITE)

    # Legend strip
    rounded_rect(d, 28, map_y + map_h - 72, 340, map_y + map_h - 16, r=14, fill=(*DARK_BAR, 200))
    draw_pin(d, 66, map_y + map_h - 46, 16, color_outer=ACCENT1, color_inner=BG, bolt=False)
    d.text((90, map_y + map_h - 62), "Fuel", font=font(30), fill=WHITE)
    draw_pin(d, 196, map_y + map_h - 46, 16, color_outer=EV_GREEN, color_inner=BG, bolt=False)
    d.text((220, map_y + map_h - 62), "Electric", font=font(30), fill=WHITE)

    # List
    list_y = map_y + map_h + 32
    d.text((40, list_y), "Nearest stations", font=font(52, bold=True), fill=WHITE)

    card_h = int(H * 0.088)
    rows = [
        ("Shell · 1.2 km",    "SP95",      "1.89 €/L", GOLD,     "Diesel · GPL · Shop"),
        ("Ionity · 0.8 km",   "DC 350 kW", "0.69 €/kWh", EV_GREEN, "CCS2 · CHAdeMO · 6/8 free"),
        ("Leclerc · 2.1 km",  "SP95-E10",  "1.74 €/L", GOLD,     "Diesel"),
    ]
    for i, (name, lbl, val, col, sub) in enumerate(rows):
        cy = list_y + 72 + i * (card_h + 18)
        station_card(d, 20, cy, W - 40, card_h, name, lbl, val, col, sub)

    cta_button(d, "Search along route")
    return img


# ─── screenshot 2: fuel prices detail ────────────────────────────────────────
def make_ss_prices():
    img, d, top = screenshot_base("Station detail")

    hc_h = int(H * 0.19)
    rounded_rect(d, 20, top, W - 20, top + hc_h, r=24, fill=CARD_BG)
    d.text((48, top + 22),  "Total · Autoroute A6",            font=font(56, bold=True), fill=WHITE)
    d.text((48, top + 96),  "12 route de Chagny  ·  0.4 km",  font=font(38), fill=ACCENT3)
    d.text((48, top + 146), "Open · Shop · Toilet · Rest area", font=font(34), fill=ACCENT2)

    sec_y = top + hc_h + 36
    d.text((40, sec_y), "Fuel prices", font=font(52, bold=True), fill=WHITE)
    d.rectangle([40, sec_y + 62, W - 40, sec_y + 64], fill=ACCENT1)

    fuels = [
        ("SP95",       "1.89 €/L", GOLD),
        ("SP95-E10",   "1.74 €/L", GOLD),
        ("SP98",       "1.98 €/L", GOLD),
        ("Diesel",     "1.69 €/L", ACCENT2),
        ("Diesel+",    "1.75 €/L", ACCENT2),
        ("GPL",        "0.92 €/L", EV_GREEN),
    ]
    rh = int(H * 0.073)
    fn_n = font(40)
    fn_v = font(52, bold=True)
    for i, (name, val, col) in enumerate(fuels):
        ry = sec_y + 80 + i * (rh + 12)
        rounded_rect(d, 20, ry, W - 20, ry + rh, r=16, fill=CARD_BG)
        d.text((48, ry + int(rh * 0.24)), name, font=fn_n, fill=WHITE)
        text_right(d, val, W - 48, ry + int(rh * 0.12), fn_v, fill=col)

    d.text((40, sec_y + 80 + len(fuels) * (rh + 12) + 8),
           "Last updated 2 hours ago", font=font(34), fill=ACCENT3)

    cta_button(d, "Navigate here")
    return img


# ─── screenshot 3: EV charging ────────────────────────────────────────────────
def make_ss_ev():
    img, d, top = screenshot_base("EV Charging")

    hc_h = int(H * 0.19)
    rounded_rect(d, 20, top, W - 20, top + hc_h, r=24, fill=CARD_BG)
    d.text((48, top + 22), "Ionity · Aire de Beaune",    font=font(56, bold=True), fill=WHITE)
    d.text((48, top + 96), "Autoroute A6  ·  0.8 km",   font=font(38), fill=ACCENT3)
    # EV badge
    bx = W - 280
    rounded_rect(d, bx, top + 26, bx + 226, top + 80, r=26, fill=EV_GREEN)
    d.text((bx + 16, top + 38), "⚡ 350 kW", font=font(38, bold=True), fill=DARK_BAR)
    d.text((48, top + 146), "6 / 8 connectors available", font=font(34), fill=EV_GREEN)

    sec_y = top + hc_h + 36
    d.text((40, sec_y), "Connectors", font=font(52, bold=True), fill=WHITE)
    d.rectangle([40, sec_y + 62, W - 40, sec_y + 64], fill=EV_GREEN)

    connectors = [
        ("CCS2 (Combo)",  "350 kW DC", "0.69 €/kWh", "4 available  ·  fast charge"),
        ("CHAdeMO",       "50 kW DC",  "0.59 €/kWh", "2 available"),
        ("Type 2 AC",     "22 kW AC",  "0.45 €/kWh", "2 available"),
    ]
    rh = int(H * 0.116)
    fn_n = font(44)
    fn_pw = font(34)
    fn_v  = font(52, bold=True)
    fn_av = font(34)
    for i, (con, power, price, avail) in enumerate(connectors):
        ry = sec_y + 80 + i * (rh + 16)
        rounded_rect(d, 20, ry, W - 20, ry + rh, r=16, fill=CARD_BG)
        d.text((48, ry + 18), con,   font=fn_n,  fill=WHITE)
        d.text((48, ry + 74), power, font=fn_pw, fill=ACCENT3)
        text_right(d, price, W - 48, ry + 22, fn_v,  fill=EV_GREEN)
        d.text((48, ry + rh - 52), avail, font=fn_av, fill=EV_GREEN)

    cta_button(d, "Navigate to charger", color=EV_GREEN, text_color=DARK_BAR)
    return img


# ─── screenshot 4: filters ────────────────────────────────────────────────────
def make_ss_filters():
    img, d, top = screenshot_base("Filters")

    fn_sec   = font(48, bold=True)
    fn_chip  = font(38)
    fn_small = font(32)

    y = top + 12

    def section(label):
        nonlocal y
        d.text((40, y), label, font=fn_sec, fill=WHITE)
        d.rectangle([40, y + 58, W - 40, y + 60], fill=ACCENT1)
        y += 78

    def chips(items, active_indices, color=ACCENT1, text_dark=False):
        nonlocal y
        x   = 40
        rh  = int(H * 0.066)
        row_start_y = y
        for i, item in enumerate(items):
            bb = d.textbbox((0, 0), item, font=fn_chip)
            cw = bb[2] - bb[0] + 48
            active = i in active_indices
            fill = color if active else CARD_BG
            tc   = DARK_BAR if (active and text_dark) else (WHITE if active else ACCENT3)
            rounded_rect(d, x, y, x + cw, y + rh, r=rh // 2, fill=fill)
            if not active:
                rounded_rect(d, x, y, x + cw, y + rh, r=rh // 2,
                             outline=(*ACCENT1, 80), width=2)
            d.text((x + 24, y + int(rh * 0.20)), item, font=fn_chip, fill=tc)
            x += cw + 16
            if x > W - 200:
                x = 40
                y += rh + 12
        y += rh + 24

    section("Energy type")
    chips(["All", "Fuel ⛽", "Electric ⚡", "GPL", "H₂"], active_indices=[0])
    section("Connectors (EV)")
    chips(["CCS2", "CHAdeMO", "Type 2", "Tesla"], active_indices=[0, 2], color=EV_GREEN)
    section("Min. power (EV)")
    chips(["Any", "≥ 22 kW", "≥ 50 kW", "≥ 150 kW", "≥ 350 kW"], active_indices=[2], color=EV_GREEN)
    section("Services")
    chips(["Shop", "Toilet", "Rest area", "Parking", "Restaurant"], active_indices=[])

    cta_button(d, "Apply filters")
    return img


# ─── screenshot 5: android auto ───────────────────────────────────────────────
def make_ss_auto():
    img, d, top = screenshot_base("Android Auto", back=False)

    # Darker auto-style sub-header
    ah = 90
    d.rectangle([0, top, W, top + ah], fill=DARK_BAR)
    d.text((40, top + 18), "Nearby stations", font=font(44, bold=True), fill=ACCENT3)
    text_right(d, "⚙", W - 48, top + 14, font(54), fill=ACCENT2)

    y = top + ah + 16
    items = [
        ("Shell · A6 sortie 24",    "SP95: 1.89€  ·  1.2 km",       ACCENT1, True,  "Tap to navigate"),
        ("Ionity · Beaune Est",      "⚡ 350 kW · 0.69€/kWh · 0.8 km", EV_GREEN, False, "6/8 available"),
        ("Leclerc Chagny",           "SP95-E10: 1.74€  ·  3.4 km",    GOLD,    True,  ""),
        ("TotalEnergies · A6 Nord",  "SP95: 1.91€  ·  5.1 km",        ACCENT2, True,  ""),
        ("Fastned · Beaune",         "⚡ 300 kW · 0.65€/kWh · 6.2 km", EV_GREEN, False, ""),
    ]
    card_h = int(H * 0.128)
    fn_n  = font(46, bold=True)
    fn_d  = font(34)
    fn_s  = font(30)
    fn_ar = font(80)
    for i, (name, detail, col, is_fuel, hint) in enumerate(items):
        cy = y + i * (card_h + 14)
        rounded_rect(d, 16, cy, W - 16, cy + card_h, r=22, fill=CARD_BG)
        draw_pin(d, 76, cy + card_h // 2 - 8, 32,
                 color_outer=col, color_inner=CARD_BG, bolt=not is_fuel)
        d.text((140, cy + int(card_h * 0.12)), name,   font=fn_n, fill=WHITE)
        d.text((140, cy + int(card_h * 0.52)), detail, font=fn_d, fill=ACCENT3)
        if hint:
            d.text((140, cy + int(card_h * 0.76)), hint, font=fn_s, fill=col)
        text_right(d, "›", W - 40, cy + int(card_h * 0.22), fn_ar, fill=ACCENT1)

    d.text((40, H - 96),
           "Designed for safe in-car use with Android Auto™",
           font=font(30), fill=(*ACCENT3, 160))
    return img


# ─── android launcher icons ───────────────────────────────────────────────────
DENSITIES = {
    "mipmap-mdpi":     48,
    "mipmap-hdpi":     72,
    "mipmap-xhdpi":    96,
    "mipmap-xxhdpi":   144,
    "mipmap-xxxhdpi":  192,
}

def save_launcher_icons(master: Image.Image):
    for density, size in DENSITIES.items():
        out_dir = os.path.join(RES_DIR, density)
        os.makedirs(out_dir, exist_ok=True)

        resized = master.resize((size, size), Image.LANCZOS)

        # Legacy flat PNG (ic_launcher.png) – RGBA → RGB on dark bg
        flat = Image.new("RGB", (size, size), BG)
        flat.paste(resized, mask=resized.split()[3])
        flat.save(os.path.join(out_dir, "ic_launcher.png"), "PNG")

        # Round variant – circular crop
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
        round_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        round_img.paste(flat, mask=mask)
        round_img.save(os.path.join(out_dir, "ic_launcher_round.png"), "PNG")

        print(f"  {density}: ic_launcher.png + ic_launcher_round.png ({size}×{size})")

    # Adaptive icon foreground (API 26+). We keep the full artwork as-is (including the rounded
    # square), which guarantees the app uses the latest brand icon even if it isn't a "perfect"
    # masked adaptive foreground.
    out_v26 = os.path.join(RES_DIR, "mipmap-anydpi-v26")
    os.makedirs(out_v26, exist_ok=True)
    fg_size = 432  # recommended adaptive icon foreground size
    fg = master.resize((fg_size, fg_size), Image.LANCZOS)
    fg.save(os.path.join(out_v26, "ic_launcher_foreground.png"), "PNG")
    print(f"  mipmap-anydpi-v26: ic_launcher_foreground.png ({fg_size}×{fg_size})")


# ─── main ──────────────────────────────────────────────────────────────────────
def main():
    print("\n── Gaston asset generator ──────────────────────────────")

    print("\n[1/4] App icon (512×512) …")
    icon_master = make_icon(1024)          # generate at 1024, save 512
    icon_512    = icon_master.resize((512, 512), Image.LANCZOS)
    icon_512.save(f"{ASSETS_DIR}/icon-512.png", "PNG")
    print("      → playstore-assets/icon-512.png")

    print("\n[2/4] Feature graphic (1024×500) …")
    fg = make_feature(1024, 500)
    fg.save(f"{ASSETS_DIR}/feature-graphic-1024x500.png", "PNG")
    print("      → playstore-assets/feature-graphic-1024x500.png")

    print("\n[3/4] Screenshots — skipped (use scripts/regenerate_screenshots.sh)")

    print("\n[4/4] Android launcher icons …")
    save_launcher_icons(icon_master)

    print("\n── Done ─────────────────────────────────────────────────\n")


if __name__ == "__main__":
    main()

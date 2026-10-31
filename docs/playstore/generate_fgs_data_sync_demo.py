#!/usr/bin/env python3
"""Render docs/playstore/fgs-data-sync-demo.mp4 — Play Console dataSync FGS walkthrough.

Uses Gaston copy and launcher icon. Prefer replacing with an adb screenrecord of
the same steps on a device once FGS is restored (see docs/map-download-fgs.md).
"""
from __future__ import annotations

import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / "fgs-data-sync-demo.mp4"
ICON = ROOT / "playstore-assets" / "icon-512.png"
W, H = 720, 1280
FPS = 10

BG = (247, 249, 252)
SURFACE = (255, 255, 255)
ON_SURFACE = (28, 27, 31)
MUTED = (96, 101, 110)
PRIMARY = (21, 101, 192)
PRIMARY_C = (227, 242, 253)
OUTLINE = (196, 199, 206)
SHADE = (32, 36, 40, 210)
WHITE = (255, 255, 255)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [
        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial Unicode.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        p = Path(path)
        if p.exists():
            return ImageFont.truetype(str(p), size)
    return ImageFont.load_default()


def load_icon(size: int) -> Image.Image:
    img = Image.open(ICON).convert("RGBA")
    img = img.resize((size, size), Image.Resampling.LANCZOS)
    return img


def rounded(draw: ImageDraw.ImageDraw, xy, r, fill, outline=None, width=1):
    draw.rounded_rectangle(xy, radius=r, fill=fill, outline=outline, width=width)


def status_bar(draw: ImageDraw.ImageDraw, y0: int = 0):
    draw.rectangle((0, y0, W, y0 + 52), fill=BG)
    f = font(22, bold=True)
    draw.text((28, y0 + 14), "9:41", fill=ON_SURFACE, font=f)
    draw.text((W - 150, y0 + 14), "5G  88%", fill=ON_SURFACE, font=f)


def top_bar(draw: ImageDraw.ImageDraw, title: str):
    draw.rectangle((0, 52, W, 132), fill=SURFACE)
    draw.text((28, 78), "←", fill=ON_SURFACE, font=font(32, True))
    draw.text((88, 82), title, fill=ON_SURFACE, font=font(30, True))


def finger(img: Image.Image, cx: int, cy: int):
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    d.ellipse((cx - 28, cy - 28, cx + 28, cy + 28), fill=(21, 101, 192, 70), outline=(21, 101, 192, 180), width=3)
    return Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")


def settings_base(progress_pct: int | None, installed: bool) -> Image.Image:
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    status_bar(d)
    top_bar(d, "Settings")
    y = 156
    d.text((28, y), "Offline maps by region", fill=ON_SURFACE, font=font(26, True))
    y += 40
    d.text(
        (28, y),
        "Download maps for your usual regions for offline\nnavigation, both on the phone and in Android Auto",
        fill=MUTED,
        font=font(20),
    )
    y += 78
    rounded(d, (24, y, W - 24, y + 430), 24, SURFACE, OUTLINE, 1)
    d.text((44, y + 20), "Offline Maps (Mapsforge)", fill=ON_SURFACE, font=font(22, True))
    active = "ile-de-france.map" if installed else "None"
    d.text((44, y + 58), f"Active Vector Map: {active}", fill=MUTED, font=font(20))
    used = "200 MB" if installed else "0 B"
    d.text((44, y + 88), f"Storage used: {used}", fill=MUTED, font=font(18))

    row_y = y + 130
    rounded(d, (40, row_y, W - 40, row_y + 118), 16, PRIMARY_C if not installed else (232, 245, 233), PRIMARY if not installed else (46, 125, 50), 2)
    d.text((56, row_y + 22), "Île-de-France (Paris)", fill=ON_SURFACE, font=font(22, True))
    if installed:
        sub = "France · Installed (200 MB)"
        btn = "Refresh"
    elif progress_pct is None:
        sub = "France · Available (~200 MB)"
        btn = "Download"
    else:
        sub = f"Downloading ile-de-france.map…  {progress_pct}%"
        btn = "…"
    d.text((56, row_y + 56), sub, fill=MUTED, font=font(18))
    bw = 168
    bx0, by0, bx1, by1 = W - 48 - bw, row_y + 34, W - 48, row_y + 86
    rounded(d, (bx0, by0, bx1, by1), 20, PRIMARY if btn != "…" else MUTED)
    tw = d.textlength(btn, font=font(18, True))
    d.text((bx0 + (bw - tw) / 2, by0 + 14), btn, fill=WHITE, font=font(18, True))
    if progress_pct is not None and not installed:
        bar_y = row_y + 92
        rounded(d, (56, bar_y, W - 56, bar_y + 10), 5, OUTLINE)
        fill_w = 56 + int((W - 112) * progress_pct / 100)
        rounded(d, (56, bar_y, fill_w, bar_y + 10), 5, PRIMARY)

    d.text((44, y + 270), "Search country, region, or name", fill=MUTED, font=font(18))
    rounded(d, (44, y + 304, W - 44, y + 358), 12, BG, OUTLINE, 1)
    d.text((60, y + 320), "Search…", fill=MUTED, font=font(18))
    rounded(d, (44, y + 376, 280, y + 416), 16, PRIMARY)
    d.text((64, y + 386), "Other regions…", fill=WHITE, font=font(16, True))
    return img, (bx0 + bw // 2, by0 + 26)


def warning_dialog(base: Image.Image) -> Image.Image:
    dim = Image.new("RGBA", (W, H), (0, 0, 0, 110))
    img = Image.alpha_composite(base.convert("RGBA"), dim)
    d = ImageDraw.Draw(img)
    box = (48, 340, W - 48, 940)
    rounded(d, box, 28, SURFACE)
    d.text((72, 372), "Map Download Warning", fill=ON_SURFACE, font=font(26, True))
    msg = (
        "You are about to download Île-de-France (Paris)\n"
        "(~200 MB).\n\n"
        "Downloading large map files over mobile data or\n"
        "roaming networks may incur significant data\n"
        "charges from your mobile operator. Do you want\n"
        "to continue?"
    )
    d.text((72, 430), msg, fill=ON_SURFACE, font=font(20))
    d.text((W - 280, 860), "Cancel", fill=MUTED, font=font(20, True))
    d.text((W - 160, 860), "Download", fill=PRIMARY, font=font(20, True))
    return img.convert("RGB"), (W - 90, 872)


def notification_card(img: Image.Image, text: str, pct: int | None, expanded: bool) -> Image.Image:
    out = img.convert("RGBA")
    d = ImageDraw.Draw(out)
    if expanded:
        shade = Image.new("RGBA", (W, H), (20, 24, 28, 200))
        out = Image.alpha_composite(out, shade)
        d = ImageDraw.Draw(out)
        d.rectangle((0, 0, W, 420), fill=(245, 246, 248, 255))
        status_bar(d)
        d.text((28, 70), "Notifications", fill=ON_SURFACE, font=font(28, True))
        card_y = 130
    else:
        card_y = 60
    rounded(d, (20, card_y, W - 20, card_y + 168), 28, SURFACE)
    icon = load_icon(56)
    out.paste(icon, (40, card_y + 28), icon)
    d = ImageDraw.Draw(out)
    d.text((112, card_y + 28), "Gaston", fill=MUTED, font=font(16, True))
    d.text((112, card_y + 54), "Offline map download", fill=ON_SURFACE, font=font(22, True))
    d.text((112, card_y + 88), text, fill=MUTED, font=font(18))
    if pct is not None:
        bar_y = card_y + 128
        rounded(d, (112, bar_y, W - 48, bar_y + 10), 5, OUTLINE)
        fill_w = 112 + int((W - 160) * pct / 100)
        rounded(d, (112, bar_y, fill_w, bar_y + 10), 5, PRIMARY)
    return out.convert("RGB")


def save_scene(frames: list[tuple[Image.Image, float]], img: Image.Image, seconds: float):
    frames.append((img, seconds))


def main() -> None:
    frames: list[tuple[Image.Image, float]] = []
    idle, download_xy = settings_base(None, False)
    save_scene(frames, idle, 2.2)
    save_scene(frames, finger(idle, *download_xy), 0.8)

    warn, confirm_xy = warning_dialog(idle)
    save_scene(frames, warn, 2.4)
    save_scene(frames, finger(warn, *confirm_xy), 0.7)

    for pct, dur in ((8, 0.8), (27, 0.8), (51, 0.8), (74, 0.8)):
        running, _ = settings_base(pct, False)
        peek = notification_card(running, f"Downloading ile-de-france.map…  {pct}%", pct, expanded=False)
        save_scene(frames, peek, dur)

    running, _ = settings_base(88, False)
    shade = notification_card(running, "Downloading ile-de-france.map…  88%", 88, expanded=True)
    save_scene(frames, shade, 3.0)

    done, _ = settings_base(None, True)
    success = notification_card(done, "ile-de-france.map downloaded successfully", None, expanded=True)
    save_scene(frames, success, 2.2)
    save_scene(frames, done, 2.4)

    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        concat = tmp_path / "concat.txt"
        lines = []
        idx = 0
        for img, seconds in frames:
            n = max(1, int(round(seconds * FPS)))
            for _ in range(n):
                fp = tmp_path / f"f{idx:04d}.png"
                img.save(fp)
                lines.append(f"file '{fp}'\n")
                idx += 1
        concat.write_text("".join(lines))
        ffmpeg = "ffmpeg"
        try:
            import imageio_ffmpeg
            ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
        except Exception:
            pass
        cmd = [
            ffmpeg, "-y", "-f", "concat", "-safe", "0", "-r", str(FPS), "-i", str(concat),
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "26", "-movflags", "+faststart",
            str(OUT),
        ]
        subprocess.check_call(cmd)
    print(f"wrote {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()

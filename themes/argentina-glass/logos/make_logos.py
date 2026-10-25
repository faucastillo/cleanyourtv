#!/usr/bin/env python3
"""White dock logos for glass_home, made from each app's own TV banner.

The banners are not in the repo (they are the apps' artwork): pull them from the TV first, e.g. the APK with
`pm path <package>` + `tools/tv.py --pull`, then the banner named by `aapt2 dump badging` (HBO Max keeps it in a
split APK, so a crop of its tile from `tools/tv.py --screenshot` stands in). Put them in one folder as
`<package>.png` and run (a `<package>.hires.png`, a large render of the logo on a clear background, wins over the
banner; YouTube's comes from Wikimedia Commons, File:YouTube_Logo_2017.svg, public domain):

    python3 make_logos.py <banner folder>          # writes <package>.png next to this script
    python3 make_logos.py <banner folder> --sheet sheet.png   # also a preview of all logos on grey

Each logo comes out white, trimmed to its ink; the launcher (MonoLogo.normalize) sizes them all to the same
optical size on the dock, so nothing here needs to match in size.
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))


def edge_colour(a):
    e = np.concatenate([a[:3].reshape(-1, 3), a[-3:].reshape(-1, 3), a[:, :3].reshape(-1, 3), a[:, -3:].reshape(-1, 3)])
    return np.median(e, axis=0)


def by_distance(a, near=40, far=110):
    d = np.sqrt(((a - edge_colour(a)) ** 2).sum(-1))
    return np.clip((d - near) / (far - near), 0, 1)


def by_whiteness(a, lo=150, hi=230):
    return np.clip((a.min(-1) - lo) / (hi - lo), 0, 1)


def by_light(a, lo=110, hi=200):
    lum = a @ np.array([0.299, 0.587, 0.114])
    return np.clip((lum - lo) / (hi - lo), 0, 1)


def by_dark(a, lo=120, hi=200):
    lum = a @ np.array([0.299, 0.587, 0.114])
    return np.clip((hi - lum) / (hi - lo), 0, 1)


def hbo(a):
    alpha = by_light(a, 150, 215)
    alpha[: int(alpha.shape[0] * 0.12)] = 0   # the focus sheen across the top of the screenshot crop
    return alpha


def tizentube(a):
    # Purple on a light vertical gradient: compare each pixel with its own row's background (the row's median), so
    # the gradient drops out; the pale play triangle stays a cut-out.
    lum = a @ np.array([0.299, 0.587, 0.114])
    row_bg = np.median(lum, axis=1, keepdims=True)
    alpha = np.clip((row_bg - lum - 40) / 60, 0, 1)
    h, w = alpha.shape
    alpha[int(h * 0.6):, int(w * 0.44):] = 0   # the "android tv" pill under the wordmark
    return alpha


RECIPES = {
    "ar.com.flow.androidtv": by_distance,
    "com.amazon.amazonvideo.livingroom": lambda a: by_whiteness(a, 110, 245),
    "com.disney.disneyplus": by_whiteness,
    "com.google.android.youtube.tv": by_distance,   # the play button keeps its triangle as a cut-out
    "com.kick.mobile": by_distance,
    "com.netflix.ninja": by_distance,
    "com.spotify.tv.android": by_distance,
    "com.wbd.stream": hbo,
}

# This TV's owner wants TizenTube to read as YouTube to guests: the YouTube logo with a small TizenTube play ring as a
# superscript, drawn as vector shapes so it stays smooth. Set to False for TizenTube's own wordmark (recipe above).
TIZENTUBE_AS_YOUTUBE = True
if not TIZENTUBE_AS_YOUTUBE:
    RECIPES["io.gh.reisxd.tizentube.cobalt"] = tizentube

# Logos are stored at least this wide, so the TV only ever scales them down, and at most MAX_STORE_W.
STORE_W = 960
MAX_STORE_W = 1920


def logo_on_clear(a):
    """A full-colour logo on a transparent background (a vector render): everything drawn is logo, except the near-white
    parts, which become cut-outs, as the white play triangle in YouTube's red button."""
    return 1 - by_whiteness(a, 200, 245)


def make(path, recipe):
    im = Image.open(path).convert("RGBA")
    a = np.asarray(im).astype(float)
    alpha = recipe(a[..., :3]) * (a[..., 3] / 255)
    ys, xs = np.where(alpha > 0.25)
    alpha = alpha[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    if alpha.shape[1] < STORE_W:
        alpha = smooth_upscale(alpha, STORE_W)
    logo = white(alpha)
    if logo.width > MAX_STORE_W:   # a large render: plenty at this size, and light for the TV to decode
        logo = logo.resize((MAX_STORE_W, round(logo.height * MAX_STORE_W / logo.width)), Image.LANCZOS)
    return logo


def white(alpha):
    out = np.zeros(alpha.shape + (4,), dtype=np.uint8)
    out[..., :3] = 255
    out[..., 3] = np.round(np.clip(alpha, 0, 1) * 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def smooth_upscale(alpha, width):
    """A small banner's shape at {width} px with smooth edges: the alpha is enlarged with a soft filter, then its edge is
    redrawn with a smoothstep around the half-way level, instead of stretching the original's stair-stepped pixels."""
    h, w = alpha.shape
    big = Image.fromarray(np.round(alpha * 255).astype(np.uint8), "L").resize(
        (width, max(1, round(h * width / w))), Image.BICUBIC)
    a = np.asarray(big) / 255
    t = np.clip((a - 0.32) / 0.36, 0, 1)
    return t * t * (3 - 2 * t)


def play_ring(size):
    """TizenTube's mark, simplified: a ring with a play triangle inside, drawn 4x larger and scaled down."""
    k = 4
    s = size * k
    img = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(img)
    stroke = round(s * 0.12)
    d.ellipse((stroke // 2, stroke // 2, s - stroke // 2 - 1, s - stroke // 2 - 1), outline=255, width=stroke)
    c = s / 2
    d.polygon([(c - s * 0.16, c - s * 0.22), (c - s * 0.16, c + s * 0.22), (c + s * 0.24, c)], fill=255)
    return np.asarray(img.resize((size, size), Image.LANCZOS)) / 255


def youtube_with_ring(youtube):
    """The YouTube logo with the play ring as a superscript after it, top-aligned."""
    yt = np.asarray(youtube)[..., 3] / 255
    h, w = yt.shape
    size = round(h * 0.44)
    gap = round(h * 0.12)
    alpha = np.zeros((h, w + gap + size))
    alpha[:, :w] = yt
    alpha[:size, w + gap:] = play_ring(size)
    return white(alpha)


def main():
    src = sys.argv[1]
    made = []
    for pkg, recipe in RECIPES.items():
        path = os.path.join(src, pkg + ".png")
        # A high-resolution render of the logo itself, when there is one (YouTube's banner is only 320 px): it wins over
        # the banner, read as a logo on a clear background.
        hires = os.path.join(src, pkg + ".hires.png")
        if os.path.isfile(hires):
            path, recipe = hires, logo_on_clear
        if not os.path.isfile(path):
            print(f"missing {path}", file=sys.stderr)
            continue
        logo = make(path, recipe)
        logo.save(os.path.join(HERE, pkg + ".png"), optimize=True)
        made.append((pkg, logo))
        print(f"{pkg}: {logo.size}")
        if pkg == "com.google.android.youtube.tv" and TIZENTUBE_AS_YOUTUBE:
            tt = youtube_with_ring(logo)
            tt.save(os.path.join(HERE, "io.gh.reisxd.tizentube.cobalt.png"), optimize=True)
            made.append(("io.gh.reisxd.tizentube.cobalt", tt))
            print(f"io.gh.reisxd.tizentube.cobalt (YouTube + ring): {tt.size}")
    if "--sheet" in sys.argv:
        sheet_path = sys.argv[sys.argv.index("--sheet") + 1]
        cell_w, cell_h = 480, 270
        sheet = Image.new("RGBA", (cell_w * 3, cell_h * 3), (70, 80, 92, 255))
        for i, (_, logo) in enumerate(made):
            norm = normalize(logo, cell_w, cell_h)
            sheet.alpha_composite(norm, ((i % 3) * cell_w, (i // 3) * cell_h))
        sheet.convert("RGB").save(sheet_path)


def normalize(logo, cw, ch, max_w=0.84, max_h=0.56, area_share=0.21, ink_ref=0.35, ink_exp=0.1):
    """The same rule as MonoLogo.normalize on the TV: equal bounding-box area, a gentle ink correction, fit the box."""
    alpha = np.asarray(logo)[..., 3] / 255
    w, h = logo.size
    solid = max(0.05, alpha.sum() / (w * h))
    by_area = (cw * ch * area_share / (w * h)) ** 0.5 * (ink_ref / solid) ** ink_exp
    scale = min(cw * max_w / w, ch * max_h / h, by_area)
    nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
    canvas = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    canvas.alpha_composite(logo.resize((nw, nh), Image.LANCZOS), ((cw - nw) // 2, (ch - nh) // 2))
    return canvas


if __name__ == "__main__":
    main()

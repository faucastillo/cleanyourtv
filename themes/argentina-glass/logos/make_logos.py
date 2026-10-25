#!/usr/bin/env python3
"""White dock logos for glass_home, made from each app's own TV banner.

The banners are not in the repo (they are the apps' artwork): pull them from the TV first, e.g. the APK with
`pm path <package>` + `tools/tv.py --pull`, then the banner named by `aapt2 dump badging` (HBO Max keeps it in a
split APK, so a crop of its tile from `tools/tv.py --screenshot` stands in). Put them in one folder as
`<package>.png` and run:

    python3 make_logos.py <banner folder>          # writes <package>.png next to this script
    python3 make_logos.py <banner folder> --sheet sheet.png   # also a preview of all logos on grey

Each logo comes out white, trimmed to its ink; the launcher (MonoLogo.normalize) sizes them all to the same
optical weight on the dock, so nothing here needs to match in size.
"""
import os
import sys

import numpy as np
from PIL import Image

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
    "io.gh.reisxd.tizentube.cobalt": tizentube,
}


def make(path, recipe):
    im = Image.open(path).convert("RGBA")
    a = np.asarray(im).astype(float)
    alpha = recipe(a[..., :3]) * (a[..., 3] / 255)
    ys, xs = np.where(alpha > 0.25)
    alpha = alpha[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    out = np.zeros(alpha.shape + (4,), dtype=np.uint8)
    out[..., :3] = 255
    out[..., 3] = np.round(alpha * 255).astype(np.uint8)
    logo = Image.fromarray(out, "RGBA")
    if logo.width < 480:   # small banners: upscale once here so the TV never stretches a tiny bitmap
        f = 480 / logo.width
        logo = logo.resize((480, max(1, round(logo.height * f))), Image.LANCZOS)
    return logo


def main():
    src = sys.argv[1]
    made = []
    for pkg, recipe in RECIPES.items():
        path = os.path.join(src, pkg + ".png")
        if not os.path.isfile(path):
            print(f"missing {path}", file=sys.stderr)
            continue
        logo = make(path, recipe)
        logo.save(os.path.join(HERE, pkg + ".png"), optimize=True)
        made.append((pkg, logo))
        print(f"{pkg}: {logo.size}")
    if "--sheet" in sys.argv:
        sheet_path = sys.argv[sys.argv.index("--sheet") + 1]
        cell_w, cell_h = 480, 270
        sheet = Image.new("RGBA", (cell_w * 3, cell_h * 3), (70, 80, 92, 255))
        for i, (_, logo) in enumerate(made):
            norm = normalize(logo, cell_w, cell_h)
            sheet.alpha_composite(norm, ((i % 3) * cell_w, (i // 3) * cell_h))
        sheet.convert("RGB").save(sheet_path)


def normalize(logo, cw, ch, max_w=0.72, max_h=0.46, ink_share=0.07):
    """The same rule as MonoLogo.normalize on the TV: fit the box, then even out the optical weight by ink area."""
    alpha = np.asarray(logo)[..., 3] / 255
    ink = alpha.sum()
    scale = min(cw * max_w / logo.width, ch * max_h / logo.height, (cw * ch * ink_share / max(ink, 1)) ** 0.5)
    w, h = max(1, round(logo.width * scale)), max(1, round(logo.height * scale))
    canvas = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    canvas.alpha_composite(logo.resize((w, h), Image.LANCZOS), ((cw - w) // 2, (ch - h) // 2))
    return canvas


if __name__ == "__main__":
    main()

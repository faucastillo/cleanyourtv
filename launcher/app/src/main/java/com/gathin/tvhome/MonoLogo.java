package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import android.util.Log;

import java.io.File;
import java.util.Arrays;

/**
 * White logos for glass_home's dock: the app's banner with its background taken out and what is left painted white,
 * as tvOS draws its top shelf apps on glass. The background is the banner's typical edge colour; a pixel's
 * whiteness grows with its distance from it. A hand-made {@code logos/<package>.png} in the theme folder wins.
 */
final class MonoLogo {
    static final String DIR = "logos";
    private static final int W = 320, H = 180;
    /** Colour distance (0-441) below which a pixel counts as background, and above which it is fully logo. */
    private static final float NEAR = 40f, FAR = 110f;

    private MonoLogo() {}

    /** The white logo, trimmed to its content; null when the banner is unusable (a flat colour, say). */
    static Bitmap of(Drawable banner, String pkg, File themeDir) {
        if (themeDir != null && pkg != null) {
            File custom = new File(new File(themeDir, DIR), pkg + ".png");
            if (custom.isFile()) {
                Bitmap b = BitmapFactory.decodeFile(custom.getPath());
                if (b != null) return normalize(b);
            }
        }
        if (banner == null) return null;
        try {
            Bitmap src = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(src);
            Drawable d = banner.getConstantState() != null ? banner.getConstantState().newDrawable().mutate() : banner;
            d.setBounds(0, 0, W, H);
            d.draw(c);
            int[] px = new int[W * H];
            src.getPixels(px, 0, W, 0, 0, W, H);
            src.recycle();
            // Logos on a flat background stand out by colour; on a gradient (Disney+) the edge colour is no guide,
            // but the logo is near white there, so whiteness picks it out instead.
            Bitmap logo = extract(px, false);
            if (logo == null) logo = extract(px, true);
            Log.d("TvHome.MonoLogo", pkg + (logo == null ? ": no logo" : ": " + logo.getWidth() + "x" + logo.getHeight()));
            return logo == null ? null : normalize(logo);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * White pixels with the logo's shape: by distance from the edge colour, or ({@code byWhiteness}) by how near white
     * each pixel is. Null when the result is not logo-like: nothing found, or covering most of the banner.
     */
    private static Bitmap extract(int[] source, boolean byWhiteness) {
        int[] px = new int[source.length];
        int bg = edgeColor(source);
        int br = (bg >> 16) & 0xFF, bgG = (bg >> 8) & 0xFF, bb = bg & 0xFF;
        int minX = W, minY = H, maxX = -1, maxY = -1, solid = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int p = source[y * W + x];
                int a = p >>> 24, r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                float t;
                if (byWhiteness) {
                    t = (Math.min(r, Math.min(g, b)) - 150f) / 80f;
                } else {
                    float dr = r - br, dg = g - bgG, db = b - bb;
                    t = ((float) Math.sqrt(dr * dr + dg * dg + db * db) - NEAR) / (FAR - NEAR);
                }
                int alpha = Math.round(Math.max(0f, Math.min(1f, t)) * a);
                px[y * W + x] = alpha << 24 | 0xFFFFFF;
                if (alpha > 60) {
                    solid++;
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0 || solid < W * H * 0.02f || (maxX - minX) * (maxY - minY) > W * H * 0.85f) return null;
        Bitmap out = Bitmap.createBitmap(px, W, H, Bitmap.Config.ARGB_8888);
        Bitmap trimmed = Bitmap.createBitmap(out, minX, minY, maxX - minX + 1, maxY - minY + 1);
        if (trimmed != out) out.recycle();
        return trimmed;
    }

    /** The canvas every dock logo is laid out on (the tile's 16:9), the box a logo must fit, and its common size. */
    static final int CANVAS_W = 480, CANVAS_H = 270;
    static final float MAX_W = 0.84f, MAX_H = 0.56f, AREA_SHARE = 0.21f;
    /** A gentle correction for how solid a logo is: a block wordmark a little smaller, a thin script a little larger. */
    static final float INK_REF = 0.35f, INK_EXP = 0.1f;

    /**
     * Places a trimmed logo on the 16:9 canvas at a common optical size: its bounding box covers {@link #AREA_SHARE}
     * of the canvas (so a wide wordmark and a stacked one look equally big), nudged by how much of that box is ink,
     * and never past the box that keeps it clear of the tile's edges.
     */
    static Bitmap normalize(Bitmap logo) {
        int w = logo.getWidth(), h = logo.getHeight();
        int[] px = new int[w * h];
        logo.getPixels(px, 0, w, 0, 0, w, h);
        double ink = 0;
        for (int p : px) ink += (p >>> 24) / 255.0;
        double solid = Math.max(0.05, ink / ((double) w * h));
        double byArea = Math.sqrt(CANVAS_W * CANVAS_H * AREA_SHARE / ((double) w * h)) * Math.pow(INK_REF / solid, INK_EXP);
        float scale = (float) Math.min(Math.min(CANVAS_W * MAX_W / w, CANVAS_H * MAX_H / h), byArea);
        int sw = Math.max(1, Math.round(w * scale)), sh = Math.max(1, Math.round(h * scale));
        Bitmap out = Bitmap.createBitmap(CANVAS_W, CANVAS_H, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(logo, null, new android.graphics.Rect((CANVAS_W - sw) / 2, (CANVAS_H - sh) / 2,
                (CANVAS_W - sw) / 2 + sw, (CANVAS_H - sh) / 2 + sh), new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        logo.recycle();
        return out;
    }

    /** The median colour of the banner's outer two-pixel frame, channel by channel. */
    private static int edgeColor(int[] px) {
        int n = 0;
        int[] r = new int[4 * (W + H) * 2], g = new int[r.length], b = new int[r.length];
        for (int ring = 0; ring < 2; ring++) {
            for (int x = 0; x < W; x++) {
                for (int y : new int[]{ring, H - 1 - ring}) {
                    int p = px[y * W + x];
                    r[n] = (p >> 16) & 0xFF; g[n] = (p >> 8) & 0xFF; b[n] = p & 0xFF; n++;
                }
            }
            for (int y = 0; y < H; y++) {
                for (int x : new int[]{ring, W - 1 - ring}) {
                    int p = px[y * W + x];
                    r[n] = (p >> 16) & 0xFF; g[n] = (p >> 8) & 0xFF; b[n] = p & 0xFF; n++;
                }
            }
        }
        return 0xFF000000 | median(r, n) << 16 | median(g, n) << 8 | median(b, n);
    }

    private static int median(int[] v, int n) {
        int[] copy = Arrays.copyOf(v, n);
        Arrays.sort(copy);
        return copy[n / 2];
    }
}

package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Frosted glass for glass_home: a blurred copy of the wallpaper, made once per picture at 1/{@link #SCALE} of the
 * screen, that glass panels draw the part of which lies behind them. Blurring once at that size costs a few
 * milliseconds per wallpaper change instead of a live blur on every frame, which a 1-2 GB TV cannot afford.
 */
final class Frost {
    /** The blur's size relative to the screen; the shader scales it back up with bilinear filtering. */
    static final int SCALE = 8;
    /** Box-blur radius in blur pixels; three passes approximate a Gaussian of about 40 screen pixels. */
    private static final int RADIUS = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final int[] location = new int[2];
    private final List<View> hosts = new ArrayList<>();
    private Bitmap bitmap;
    private float scale = SCALE;

    /** The blur for a new wallpaper (null when there is none); every registered panel redraws with it. */
    void set(Bitmap blurred, int screenWidth) {
        bitmap = blurred;
        if (blurred == null) {
            paint.setShader(null);
        } else {
            scale = screenWidth / (float) blurred.getWidth();
            paint.setShader(new BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        }
        for (View host : hosts) host.invalidate();
    }

    boolean ready() {
        return bitmap != null;
    }

    /** Redrawn whenever the blur changes. */
    void register(View host) {
        if (!hosts.contains(host)) hosts.add(host);
    }

    /** Fills {@code rect} (in {@code host}'s own coordinates) with the blur behind it; false when there is none. */
    boolean fill(Canvas canvas, RectF rect, float radius, View host) {
        if (bitmap == null) return false;
        host.getLocationInWindow(location);
        matrix.setScale(scale, scale);
        matrix.postTranslate(-location[0], -location[1]);
        paint.getShader().setLocalMatrix(matrix);
        canvas.drawRoundRect(rect, radius, radius, paint);
        return true;
    }

    /**
     * The blur of a decoded wallpaper as ImageView's CENTER_CROP shows it on a {@code screenWidth} x
     * {@code screenHeight} screen. Software bitmap in, software bitmap out (about 130 KB at 1080p); null on failure.
     */
    static Bitmap make(Bitmap source, int screenWidth, int screenHeight) {
        try {
            int w = Math.max(1, screenWidth / SCALE), h = Math.max(1, screenHeight / SCALE);
            float cover = Math.max(screenWidth / (float) source.getWidth(), screenHeight / (float) source.getHeight());
            float visibleW = screenWidth / cover, visibleH = screenHeight / cover;
            int left = Math.round((source.getWidth() - visibleW) / 2f), top = Math.round((source.getHeight() - visibleH) / 2f);
            Rect src = new Rect(left, top, left + Math.round(visibleW), top + Math.round(visibleH));
            // Two steps down, so the large reduction does not alias into sparkles the blur cannot hide.
            Bitmap half = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888);
            new Canvas(half).drawBitmap(source, src, new Rect(0, 0, w * 2, h * 2), new Paint(Paint.FILTER_BITMAP_FLAG));
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Paint tone = new Paint(Paint.FILTER_BITMAP_FLAG);
            // Glass reads livelier and keeps white text legible: a little more colour, a little less light.
            ColorMatrix saturation = new ColorMatrix();
            saturation.setSaturation(1.35f);
            ColorMatrix dim = new ColorMatrix();
            dim.setScale(0.82f, 0.82f, 0.82f, 1f);
            saturation.postConcat(dim);
            tone.setColorFilter(new ColorMatrixColorFilter(saturation));
            new Canvas(out).drawBitmap(half, null, new Rect(0, 0, w, h), tone);
            half.recycle();
            int[] pixels = new int[w * h];
            out.getPixels(pixels, 0, w, 0, 0, w, h);
            for (int pass = 0; pass < 3; pass++) {
                boxBlur(pixels, w, h, RADIUS, true);
                boxBlur(pixels, w, h, RADIUS, false);
            }
            out.setPixels(pixels, 0, w, 0, 0, w, h);
            return out;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /** One running-sum box blur along rows ({@code horizontal}) or columns, edges clamped. */
    static void boxBlur(int[] px, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        int[] line = new int[len];
        int span = 2 * r + 1;
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < len; i++) line[i] = px[horizontal ? l * w + i : i * w + l];
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int c = line[Math.max(0, Math.min(len - 1, i))];
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                px[horizontal ? l * w + i : i * w + l] = 0xFF000000 | (sr / span) << 16 | (sg / span) << 8 | (sb / span);
                int add = line[Math.min(len - 1, i + r + 1)], drop = line[Math.max(0, i - r)];
                sr += ((add >> 16) & 0xFF) - ((drop >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((drop >> 8) & 0xFF);
                sb += (add & 0xFF) - (drop & 0xFF);
            }
        }
    }
}

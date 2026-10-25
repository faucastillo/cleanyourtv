package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;

/**
 * glass_home's liquid glass, after Apple's: a clear pane over the blurred wallpaper (Frost) whose rim bends what is
 * behind it like a lens, with light catching the top-left and bottom-right of the edge and a thin bright line just
 * inside the top. Shared by every glass surface (cards, dock band, top buttons) and, without the blur, the focus
 * highlight. Only shader draws over the one blur Frost already made, so nothing is blurred per frame.
 */
final class LiquidGlass {
    /** How much the rim magnifies the background, and how wide the lensing band is. */
    private static final float LENS_ZOOM = 1.10f;
    private static final float LENS_DP = 12f;

    private final Frost frost;
    private final float density;
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallback = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix rimMatrix = new Matrix();
    private final RectF band = new RectF();
    private final RectF inner = new RectF();
    /** The rect the gradients were made for; they are rebuilt when it changes. */
    private final RectF made = new RectF();
    private boolean focused;
    /** A barely-there pane (the top buttons): mostly clear, no lens, a faint rim; minimal, it should not call for the eye. */
    private boolean subtle;

    LiquidGlass(Frost frost, float density) {
        this.frost = frost;
        this.density = density;
        rim.setStyle(Paint.Style.STROKE);
        highlight.setStyle(Paint.Style.STROKE);
        highlight.setStrokeCap(Paint.Cap.ROUND);
        fallback.setColor(0x40141820);
    }

    /** A brighter pane with stronger light on the rim: the focused top button, the focus highlight. */
    LiquidGlass focused(boolean on) {
        focused = on;
        made.setEmpty();
        return this;
    }

    LiquidGlass subtle(boolean on) {
        subtle = on;
        made.setEmpty();
        return this;
    }

    /** The whole pane: blur, lens rim, tint, shade, light. {@code host} is the view whose canvas this is. */
    void draw(Canvas c, RectF r, float radius, View host) {
        int blurAlpha = !subtle ? 255 : focused ? 200 : 110;
        boolean blurred = frost != null && frost.fill(c, r, radius, host, 1f, Paint.Style.FILL, 0f, blurAlpha);
        if (!blurred && !subtle) c.drawRoundRect(r, radius, radius, fallback);
        if (blurred && !subtle) {
            // The lens: the rim shows the background magnified about the pane's centre, strongest at the edge and
            // fading inwards, in three soft steps so no hard line shows.
            float lens = Math.min(LENS_DP * density, Math.min(r.width(), r.height()) / 4f);
            for (int i = 0; i < 3; i++) {
                float w = lens / 3f, o = w * i + w / 2f;
                band.set(r);
                band.inset(o, o);
                frost.fill(c, band, Math.max(0f, radius - o), host, 1f + (LENS_ZOOM - 1f) * (1f - i / 3f),
                        Paint.Style.STROKE, w + 0.5f, 230 - i * 70);
            }
        }
        drawSurface(c, r, radius);
    }

    /** Tint, shade and light without the blur, for panes that sit over content (the focus highlight). */
    void drawSurface(Canvas c, RectF r, float radius) {
        if (!r.equals(made)) build(r);
        c.drawRoundRect(r, radius, radius, tint);
        c.drawRoundRect(r, radius, radius, shade);
        drawRim(c, r, radius);
    }

    /** The specular rim and the inner top line alone (the photo card has a picture, not glass, inside it). */
    void drawRim(Canvas c, RectF r, float radius) {
        if (!r.equals(made)) build(r);
        float stroke = rim.getStrokeWidth();
        inner.set(r);
        inner.inset(stroke / 2f, stroke / 2f);
        c.drawRoundRect(inner, radius, radius, rim);
        // The inner line: a stroke just inside the rim, kept to the top quarter (clipRect stays on the GPU).
        float in = 2.5f * density;
        inner.set(r);
        inner.inset(in, in);
        c.save();
        c.clipRect(r.left, r.top, r.right, r.top + Math.min(r.height() * 0.25f, radius));
        c.drawRoundRect(inner, Math.max(0f, radius - in), Math.max(0f, radius - in), highlight);
        c.restore();
    }

    private void build(RectF r) {
        made.set(r);
        int top = subtle ? (focused ? 0x33FFFFFF : 0x0AFFFFFF) : focused ? 0x66FFFFFF : 0x24FFFFFF;
        int bottom = subtle ? (focused ? 0x14FFFFFF : 0x02FFFFFF) : focused ? 0x26FFFFFF : 0x06FFFFFF;
        tint.setShader(new LinearGradient(0, r.top, 0, r.bottom, top, bottom, Shader.TileMode.CLAMP));
        shade.setShader(new LinearGradient(0, r.top + r.height() * 0.55f, 0, r.bottom,
                0x00000000, subtle ? 0x00000000 : focused ? 0x0D000000 : 0x1F000000, Shader.TileMode.CLAMP));
        rim.setStrokeWidth((focused ? 1.8f : subtle ? 1f : 1.3f) * density);
        // SweepGradient starts at 3 o'clock and turns clockwise: 45 degrees is the bottom-right, 225 the top-left.
        int bright = focused ? 0xFFFFFFFF : subtle ? 0x8CFFFFFF : 0xE6FFFFFF,
                mid = focused ? 0x8CFFFFFF : subtle ? 0x2EFFFFFF : 0x59FFFFFF,
                dim = focused ? 0x40FFFFFF : subtle ? 0x0FFFFFFF : 0x1FFFFFFF;
        SweepGradient sweep = new SweepGradient(r.centerX(), r.centerY(),
                new int[]{mid, bright, dim, dim, mid, bright, mid, mid},
                new float[]{0f, 0.125f, 0.3f, 0.45f, 0.55f, 0.625f, 0.8f, 1f});
        // Stretch the sweep to the pane's shape, so the bright corners sit on the corners of a wide pane too.
        rimMatrix.setScale(1f, r.height() / Math.max(1f, r.width()), r.centerX(), r.centerY());
        sweep.setLocalMatrix(rimMatrix);
        rim.setShader(sweep);
        highlight.setStrokeWidth(density);
        highlight.setShader(new LinearGradient(r.left, 0, r.right, 0,
                new int[]{0x00FFFFFF, focused ? 0xCCFFFFFF : 0x8CFFFFFF, 0x26FFFFFF, 0x00FFFFFF},
                new float[]{0.04f, 0.3f, 0.75f, 0.96f}, Shader.TileMode.CLAMP));
    }
}

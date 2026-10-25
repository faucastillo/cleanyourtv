package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * A liquid-glass panel (glass_home): the blurred wallpaper behind it ({@link Frost}), a faint white sheen that
 * fades downwards, and a thin rim that is brightest along the top edge, as light catches a pane of glass.
 * Without a wallpaper it falls back to a plain translucent fill.
 */
final class FrostDrawable extends Drawable {
    private final Frost frost;
    private final View host;
    private final float radius;
    private final float stroke;
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallback = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF rimRect = new RectF();

    FrostDrawable(Frost frost, View host, float radiusPx, float density) {
        this.frost = frost;
        this.host = host;
        this.radius = radiusPx;
        this.stroke = Math.max(1f, density);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(stroke);
        fallback.setColor(0x40141820);
        frost.register(host);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        rect.set(bounds);
        rimRect.set(rect);
        rimRect.inset(stroke / 2f, stroke / 2f);
        sheen.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x2EFFFFFF, 0x0AFFFFFF, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x8CFFFFFF, 0x1FFFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (!frost.fill(canvas, rect, radius, host)) canvas.drawRoundRect(rect, radius, radius, fallback);
        canvas.drawRoundRect(rect, radius, radius, sheen);
        canvas.drawRoundRect(rimRect, radius, radius, rim);
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter colorFilter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

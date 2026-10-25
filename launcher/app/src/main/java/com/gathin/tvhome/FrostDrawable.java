package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

/** A liquid-glass panel (glass_home) as a View background: {@link LiquidGlass} over the blurred wallpaper. */
final class FrostDrawable extends Drawable {
    private final View host;
    private final float radius;
    private final LiquidGlass glass;
    private final RectF rect = new RectF();

    FrostDrawable(Frost frost, View host, float radiusPx, float density) {
        this(frost, host, radiusPx, density, false);
    }

    FrostDrawable(Frost frost, View host, float radiusPx, float density, boolean focused) {
        this(frost, host, radiusPx, density, focused, false);
    }

    /** {@code subtle}: the barely-there glass of the top buttons (LiquidGlass.subtle). */
    FrostDrawable(Frost frost, View host, float radiusPx, float density, boolean focused, boolean subtle) {
        this.host = host;
        this.radius = radiusPx;
        glass = new LiquidGlass(frost, density).focused(focused).subtle(subtle);
        if (frost != null) frost.register(host);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        rect.set(bounds);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        glass.draw(canvas, rect, Math.min(radius, Math.min(rect.width(), rect.height()) / 2f), host);
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter colorFilter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

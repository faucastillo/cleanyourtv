package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * A white dock logo in two copies: one at the tile's pixel size, drawn 1:1 at rest, and one at the focus zoom's size,
 * fitted into the same bounds so the zoom brings it back to 1:1. One drawable with a fixed size, so switching copies
 * on focus only redraws the tile: swapping bitmaps of different sizes into the ImageView relaid out the whole grid on
 * every focus move, and the glass row above the dock flickered with it.
 */
final class DualLogoDrawable extends Drawable {
    private final Bitmap rest, focused;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Rect dst = new Rect();
    private boolean zoomed;

    DualLogoDrawable(Bitmap rest, Bitmap focused) {
        this.rest = rest;
        this.focused = focused;
    }

    void setFocused(boolean on) {
        if (zoomed == on) return;
        zoomed = on;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (zoomed && focused != null) {
            canvas.drawBitmap(focused, null, b, paint);
        } else {
            dst.set(b.centerX() - rest.getWidth() / 2, b.centerY() - rest.getHeight() / 2, 0, 0);
            dst.right = dst.left + rest.getWidth();
            dst.bottom = dst.top + rest.getHeight();
            canvas.drawBitmap(rest, null, dst, null);
        }
    }

    @Override public int getIntrinsicWidth() { return rest.getWidth(); }
    @Override public int getIntrinsicHeight() { return rest.getHeight(); }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

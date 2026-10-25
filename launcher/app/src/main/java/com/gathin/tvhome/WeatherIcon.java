package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/** Flat, soft weather pictures drawn in code (no image assets): sun or moon, clouds, rain, snow, storm and fog. */
final class WeatherIcon extends Drawable {
    static final int CLEAR = 0, PARTLY = 1, CLOUDY = 2, FOG = 3, RAIN = 4, SNOW = 5, STORM = 6;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cloud = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int kind = CLOUDY;
    private boolean day = true;

    void set(int kind, boolean day) {
        this.kind = kind;
        this.day = day;
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(@NonNull Rect b) {
        cloud.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0xFFFFFFFF, 0xFFD9E2EE, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        float x = b.centerX() - s / 2f, y = b.centerY() - s / 2f;
        switch (kind) {
            case CLEAR:
                if (day) sun(c, x + s * .5f, y + s * .5f, s * .22f, true);
                else moon(c, x + s * .5f, y + s * .5f, s * .3f);
                break;
            case PARTLY:
                if (day) sun(c, x + s * .64f, y + s * .36f, s * .17f, true);
                else moon(c, x + s * .66f, y + s * .34f, s * .22f);
                cloud(c, x + s * .08f, y + s * .34f, s * .7f, cloud);
                break;
            case CLOUDY:
                paint.setShader(null);
                paint.setColor(0xFF9FB0C4);
                cloud(c, x + s * .3f, y + s * .2f, s * .6f, paint);
                cloud(c, x + s * .06f, y + s * .34f, s * .7f, cloud);
                break;
            case FOG:
                cloud(c, x + s * .1f, y + s * .14f, s * .7f, cloud);
                paint.setShader(null);
                paint.setColor(0xD9FFFFFF);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(s * .055f);
                for (int i = 0; i < 2; i++) {
                    float ly = y + s * (.74f + i * .12f);
                    c.drawLine(x + s * (.16f + i * .08f), ly, x + s * (.84f - i * .08f), ly, paint);
                }
                break;
            case RAIN:
            case SNOW:
            case STORM:
                cloud(c, x + s * .1f, y + s * .12f, s * .76f, cloud);
                paint.setShader(null);
                if (kind == STORM) {
                    paint.setColor(0xFFFFC83D);
                    paint.setStyle(Paint.Style.FILL);
                    path.reset();
                    path.moveTo(x + s * .52f, y + s * .62f);
                    path.lineTo(x + s * .38f, y + s * .82f);
                    path.lineTo(x + s * .5f, y + s * .82f);
                    path.lineTo(x + s * .42f, y + s * .98f);
                    path.lineTo(x + s * .64f, y + s * .74f);
                    path.lineTo(x + s * .52f, y + s * .74f);
                    path.lineTo(x + s * .6f, y + s * .62f);
                    path.close();
                    c.drawPath(path, paint);
                } else {
                    paint.setColor(kind == SNOW ? 0xFFFFFFFF : 0xFF7CC4FF);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setStrokeWidth(s * .055f);
                    for (int i = 0; i < 3; i++) {
                        float dx = x + s * (.32f + i * .18f), dy = y + s * .7f;
                        if (kind == SNOW) c.drawCircle(dx, dy + s * .08f, s * .045f, paint);
                        else c.drawLine(dx, dy, dx - s * .05f, dy + s * .16f, paint);
                    }
                }
                break;
            default:
                break;
        }
    }

    private void sun(Canvas c, float cx, float cy, float r, boolean rays) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFC83D);
        c.drawCircle(cx, cy, r, paint);
        if (!rays) return;
        paint.setStrokeWidth(r * .22f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.drawLine(cx + cos * r * 1.4f, cy + sin * r * 1.4f, cx + cos * r * 1.8f, cy + sin * r * 1.8f, paint);
        }
    }

    /** A crescent: a disc with an offset disc cut out of it. */
    private void moon(Canvas c, float cx, float cy, float r) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFD54A);
        path.reset();
        path.addCircle(cx, cy, r, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(cx - r * .55f, cy - r * .35f, r * .92f, Path.Direction.CW);
        path.op(cut, Path.Op.DIFFERENCE);
        c.drawPath(path, paint);
    }

    /** A cloud {@code w} wide whose top-left corner is at (x, y): three puffs on a rounded base. */
    private void cloud(Canvas c, float x, float y, float w, Paint p) {
        float h = w * .58f;
        path.reset();
        path.addRoundRect(new RectF(x, y + h * .45f, x + w, y + h), h * .28f, h * .28f, Path.Direction.CW);
        path.addCircle(x + w * .3f, y + h * .5f, h * .3f, Path.Direction.CW);
        path.addCircle(x + w * .56f, y + h * .36f, h * .38f, Path.Direction.CW);
        path.addCircle(x + w * .78f, y + h * .6f, h * .26f, Path.Direction.CW);
        path.setFillType(Path.FillType.WINDING);
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter colorFilter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

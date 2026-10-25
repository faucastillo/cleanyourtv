package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * glass_home's weather card: a liquid-glass panel with the sky's picture on the left and, right-aligned, the
 * temperature, the place and the conditions. It reads Open-Meteo (Weather) only between start() and stop().
 */
final class WeatherCard extends View {
    private final double latitude, longitude;
    private final String place;
    private final float density;
    private final WeatherIcon icon = new WeatherIcon();
    private final Paint temp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint name = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Weather.Now now;
    private boolean started;
    private boolean fetching;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (!started) return;
            fetchNow();
        }
    };

    WeatherCard(Context ctx, Frost frost, double latitude, double longitude, String place) {
        super(ctx);
        this.latitude = latitude;
        this.longitude = longitude;
        this.place = place;
        density = ctx.getResources().getDisplayMetrics().density;
        setBackground(new FrostDrawable(frost, this, 28 * density, density));
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        Typeface light = typeface(ctx, R.string.font_light), medium = typeface(ctx, R.string.font_medium);
        for (Paint p : new Paint[]{temp, name, detail}) {
            p.setColor(0xFFFFFFFF);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setShadowLayer(4 * density, 0, density, 0x40000000);
        }
        temp.setTypeface(light);
        name.setTypeface(medium);
        detail.setTypeface(medium);
        detail.setColor(0xD9FFFFFF);
        show(Weather.load(ctx));
    }

    private static Typeface typeface(Context ctx, int font) {
        try {
            return UiFont.get(ctx, font);
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    void start() {
        started = true;
        long age = now == null ? Long.MAX_VALUE : System.currentTimeMillis() - now.fetchedAt;
        handler.removeCallbacks(refresh);
        if (age >= Weather.REFRESH_MS) fetchNow();
        else handler.postDelayed(refresh, Weather.REFRESH_MS - age);
    }

    void stop() {
        started = false;
        handler.removeCallbacks(refresh);
    }

    void close() {
        stop();
        worker.shutdownNow();
    }

    private void fetchNow() {
        if (fetching) return;
        fetching = true;
        worker.execute(() -> {
            Weather.Now fresh = Weather.fetch(latitude, longitude);
            handler.post(() -> {
                fetching = false;
                if (fresh != null) {
                    Weather.save(getContext(), fresh);
                    show(fresh);
                }
                if (started) handler.postDelayed(refresh, fresh != null ? Weather.REFRESH_MS : Weather.RETRY_MS);
            });
        });
    }

    private void show(Weather.Now reading) {
        now = reading;
        if (reading != null) {
            icon.set(Weather.kind(reading.code), reading.day);
            setContentDescription(String.format(Locale.getDefault(), "%d °C, %s, %s",
                    Math.round(reading.temperature), place, Weather.describe(reading.code)));
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float pad = Math.min(36 * density, w * .08f);
        float iconSize = Math.min(h * .5f, w * .34f);
        int iconLeft = Math.round(pad), iconTop = Math.round((h - iconSize) / 2f);
        icon.setBounds(iconLeft, iconTop, Math.round(iconLeft + iconSize), Math.round(iconTop + iconSize));
        if (now != null) icon.draw(canvas);
        float right = w - pad;
        temp.setTextSize(Math.min(h * .26f, 64 * density));
        name.setTextSize(Math.min(h * .085f, 20 * density));
        detail.setTextSize(Math.min(h * .065f, 15 * density));
        float block = temp.getTextSize() * .85f + name.getTextSize() * 1.5f + detail.getTextSize() * 1.6f;
        float y = (h - block) / 2f + temp.getTextSize() * .85f;
        canvas.drawText(now == null ? "--°C" : Math.round(now.temperature) + "°C", right, y, temp);
        y += name.getTextSize() * 1.5f;
        canvas.drawText(place, right, y, name);
        y += detail.getTextSize() * 1.6f;
        canvas.drawText(now == null ? "Sin datos todavía" : Weather.describe(now.code), right, y, detail);
    }
}

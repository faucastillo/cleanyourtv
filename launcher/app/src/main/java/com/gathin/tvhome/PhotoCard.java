package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewOutlineProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * glass_home's photo card: the owner's own pictures from {@code photos/} in the theme folder (tools/photos.sh fills
 * it), one at a time, centre-cropped into a rounded card and crossfaded every few minutes. Pictures never leave the
 * TV. With an empty folder the card is a glass panel that says where to put them.
 */
final class PhotoCard extends View {
    static final String DIR = "photos";

    private final File dir;
    private final long interval;
    private final float density;
    private final float radius;
    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final LiquidGlass rim;
    private final TextPaint hint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rimRect = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final FrostDrawable empty;
    private Bitmap current, previous;
    private String shown = "";
    private float fade = 1f;
    private boolean started, loading;
    private int decodedFor;

    private final Runnable next = new Runnable() {
        @Override
        public void run() {
            if (started) advance();
        }
    };

    PhotoCard(Context ctx, Frost frost, File themeDir, int minutes) {
        super(ctx);
        dir = themeDir == null ? null : new File(themeDir, DIR);
        interval = Math.max(1, minutes) * 60_000L;
        density = ctx.getResources().getDisplayMetrics().density;
        radius = 32 * density;
        empty = new FrostDrawable(frost, this, radius, density);
        rim = new LiquidGlass(frost, density);
        hint.setColor(0xD9FFFFFF);
        hint.setTextSize(16 * density);
        hint.setShadowLayer(4 * density, 0, density, 0x40000000);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    void start() {
        started = true;
        if (current == null || decodedFor != getWidth()) advance();
        else handler.postDelayed(next, interval);
    }

    void stop() {
        started = false;
        handler.removeCallbacks(next);
    }

    void close() {
        stop();
        decoder.shutdownNow();
    }

    /** Lets the picture go when Home is in the background and Android needs the memory. */
    void release() {
        current = previous = null;
        shown = "";
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        empty.setBounds(0, 0, w, h);
        rimRect.set(0, 0, w, h);
        if (started && w > 0 && decodedFor != w) advance();
    }

    private List<File> photos() {
        File[] found = dir == null ? null : dir.listFiles((d, n) -> ThemeFiles.collectionImage(n));
        List<File> list = found == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(found));
        list.sort((a, b) -> a.getName().compareTo(b.getName()));
        return list;
    }

    private void advance() {
        handler.removeCallbacks(next);
        int w = getWidth(), h = getHeight();
        if (loading || w == 0 || h == 0) return;
        List<File> all = photos();
        if (all.isEmpty()) {
            current = previous = null;
            shown = "";
            invalidate();
            if (started) handler.postDelayed(next, interval);
            return;
        }
        File pick = all.get(random.nextInt(all.size()));
        if (all.size() > 1 && pick.getName().equals(shown)) pick = all.get((all.indexOf(pick) + 1) % all.size());
        File file = pick;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(file, w, h);
            post(() -> {
                loading = false;
                if (bitmap != null) {
                    previous = current;
                    current = bitmap;
                    shown = file.getName();
                    decodedFor = w;
                    crossfade();
                }
                if (started) handler.postDelayed(next, interval);
            });
        });
    }

    private void crossfade() {
        if (previous == null) {
            fade = 1f;
            invalidate();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f).setDuration(1200);
        a.addUpdateListener(v -> {
            fade = (float) v.getAnimatedValue();
            invalidate();
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                previous = null;
            }
        });
        a.start();
    }

    /** Sampled down to about the card's size; the HARDWARE copy keeps one copy in GPU memory only. */
    private static Bitmap decode(File file, int w, int h) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), o);
            if (decoded == null) return null;
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (current == null) {
            empty.draw(canvas);
            String text = "Agregá tus fotos en my-photos/ y corré tools/photos.sh";
            int width = Math.round(getWidth() * .7f);
            StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), hint, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).build();
            canvas.save();
            canvas.translate((getWidth() - width) / 2f, (getHeight() - layout.getHeight()) / 2f);
            layout.draw(canvas);
            canvas.restore();
            return;
        }
        if (previous != null) drawCropped(canvas, previous, 255);
        drawCropped(canvas, current, Math.round(255 * fade));
        rim.drawRim(canvas, rimRect, radius);
    }

    private void drawCropped(Canvas canvas, Bitmap bitmap, int alpha) {
        float scale = Math.max(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
        matrix.setScale(scale, scale);
        matrix.postTranslate((getWidth() - bitmap.getWidth() * scale) / 2f, (getHeight() - bitmap.getHeight() * scale) / 2f);
        image.setAlpha(alpha);
        canvas.drawBitmap(bitmap, matrix, image);
    }
}

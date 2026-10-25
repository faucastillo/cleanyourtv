package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.File;

/**
 * This fork's liquid-glass Home (a theme's {@code "glass_home"} object): frosted glass behind the dock and the cards,
 * white dock logos, a row with the owner's photos and the weather between the clock and the dock, and a quieter
 * header. Everything lives here so the upstream classes need only small hooks; without the key nothing changes.
 *
 * <pre>
 * "glass_home": {
 *   "frosted": true, "dock_logos": "white", "show_date": false, "clock_24h": true, "clock_size": 40,
 *   "caption_size": 11, "show_credits": false, "hide_grid_until_down": true, "photo_card": true, "photo_minutes": 10,
 *   "weather": { "latitude": -34.5889, "longitude": -58.4306, "place": "Palermo, CABA" }
 * }
 * </pre>
 */
final class GlassHome {

    /** The theme's glass_home settings; {@link #enabled} false when the key is absent. */
    static final class Options {
        final boolean enabled, frosted, whiteLogos, showDate, clock24h, photoCard, weather, showCredits, hideGridRows;
        final int clockSize, photoMinutes;
        final float captionSize;
        final double latitude, longitude;
        final String place;

        Options(JSONObject o) {
            enabled = o != null;
            JSONObject g = o == null ? new JSONObject() : o;
            frosted = g.optBoolean("frosted", true);
            whiteLogos = "white".equals(g.optString("dock_logos", ""));
            showDate = g.optBoolean("show_date", true);
            // On by default: a public theme keeps its pictures' attribution on screen unless it opts out.
            showCredits = g.optBoolean("show_credits", true);
            hideGridRows = g.optBoolean("hide_grid_until_down", true);
            clock24h = g.optBoolean("clock_24h", false);
            clockSize = g.optInt("clock_size", 0);
            captionSize = (float) g.optDouble("caption_size", 0);
            photoCard = g.optBoolean("photo_card", false);
            photoMinutes = g.optInt("photo_minutes", 10);
            JSONObject w = g.optJSONObject("weather");
            weather = w != null && w.has("latitude") && w.has("longitude");
            latitude = w == null ? 0 : w.optDouble("latitude", 0);
            longitude = w == null ? 0 : w.optDouble("longitude", 0);
            place = w == null ? "" : w.optString("place", "");
        }

        static Options read(Context ctx) {
            JSONObject overlay = Config.overlay(ctx);
            return new Options(overlay == null ? null : overlay.optJSONObject("glass_home"));
        }
    }

    private final Options options;
    private final RecyclerView grid;
    private final FrameLayout root;
    private final int columns;
    private final float density;
    private final FrameLayout row;
    private final PhotoCard photo;
    private final WeatherCard weather;
    private final int[] at = new int[2];
    private final int[] rootAt = new int[2];
    /** The dock tiles' top in root coordinates when the grid rests, and the header's bottom; 0 until placed. */
    private int restDockTop, headerBottom;
    /** hide_grid_until_down: the rows under the dock's current opacity, 0 while the dock rests. */
    private float gridAlpha;
    private int rowHeight;

    private GlassHome(Activity activity, FrameLayout root, RecyclerView grid, int columns, Frost frost, Options options,
                      TextView caption) {
        this.options = options;
        this.grid = grid;
        this.root = root;
        this.columns = columns;
        density = activity.getResources().getDisplayMetrics().density;
        quietHeader(activity, caption);
        if (frost != null) liquidPills(activity, frost);
        File themeDir = Config.dir(activity);
        photo = options.photoCard ? new PhotoCard(activity, frost, themeDir, options.photoMinutes) : null;
        weather = options.weather
                ? new WeatherCard(activity, frost, options.latitude, options.longitude, options.place) : null;
        if (photo == null && weather == null) {
            row = null;
        } else {
            row = new FrameLayout(activity);
            row.setClipChildren(false);
            row.setVisibility(View.INVISIBLE);   // until the dock has been laid out and the row placed above it
            if (photo != null) row.addView(photo);
            if (weather != null) row.addView(weather);
            // Under the main column (header and grid), so the focus glass and the tiles draw over the row.
            View main = (View) grid.getParent().getParent();
            root.addView(row, root.indexOfChild(main), new FrameLayout.LayoutParams(0, 0));
        }
        if (options.hideGridRows) {
            // Rows that scroll into view (or are rebound) take the current fade at once.
            grid.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(@NonNull View v) {
                    if (grid.getChildAdapterPosition(v) >= GlassHome.this.columns) fade(v, gridAlpha);
                }

                @Override
                public void onChildViewDetachedFromWindow(@NonNull View v) {
                    fade(v, 1f);
                }
            });
        }
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> grid.post(this::place));
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                follow();
            }
        });
    }

    /** Null without a theme's glass_home. */
    static GlassHome attach(Activity activity, FrameLayout root, RecyclerView grid, int columns, Frost frost,
                            Options options, TextView caption) {
        return options.enabled ? new GlassHome(activity, root, grid, columns, frost, options, caption) : null;
    }

    /** The clock alone, as on tvOS: no date or divider, an optional 24-hour face and size, and a quieter caption. */
    private void quietHeader(Activity a, TextView caption) {
        if (!options.showDate) {
            hide(a.findViewById(R.id.date));
            hide(a.findViewById(R.id.clockDivider));
            hide(a.findViewById(R.id.shamsiDate));
            hide(a.findViewById(R.id.lunarDate));
        }
        if (options.clock24h) {
            TextClock clock = a.findViewById(R.id.clock);
            clock.setFormat12Hour("HH:mm");
            clock.setFormat24Hour("HH:mm");
            hide(a.findViewById(R.id.ampm));
        }
        if (options.clockSize > 0) {
            ((TextView) a.findViewById(R.id.clock)).setTextSize(TypedValue.COMPLEX_UNIT_SP, options.clockSize);
        }
        if (caption != null && !options.showCredits) {
            // WallpaperPlayer only fades the caption's alpha, so GONE stays.
            caption.setVisibility(View.GONE);
        } else if (caption != null && options.captionSize > 0) {
            caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, options.captionSize);
            caption.setTextColor(0xA6FFFFFF);
            // On the buttons' line, just left of them, so it never lands on the cards below.
            View buttons = a.findViewById(R.id.topButtons);
            int[] b = new int[2];
            buttons.addOnLayoutChangeListener((v, l, t, r, bottom, ol, ot, or, ob) -> {
                View parent = (View) caption.getParent();
                v.getLocationInWindow(b);
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) caption.getLayoutParams();
                int end = parent.getWidth() - b[0] + Math.round(20 * density);
                int top = b[1] + (v.getHeight() - caption.getLineHeight()) / 2;
                if (p.getMarginEnd() != end || p.topMargin != top) {
                    p.setMarginEnd(end);
                    p.topMargin = top;
                    caption.setLayoutParams(p);
                }
            });
        }
    }

    /**
     * The rows' own fade, on top of the alpha MainActivity sets for reorder dimming. transitionAlpha is API 29; on older
     * TVs the rows simply stay visible.
     */
    private static void fade(View v, float alpha) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) v.setTransitionAlpha(alpha);
    }

    private static void hide(View v) {
        if (v != null) v.setVisibility(View.GONE);
    }

    /**
     * Sizes the row for the space between the header and the dock as it rests (the grid's top padding is the dock's
     * line), flush with the first and last dock tiles. Where the dock is now is follow()'s business.
     */
    private void place() {
        RecyclerView.ViewHolder first = grid.findViewHolderForAdapterPosition(0);
        RecyclerView.ViewHolder last = grid.findViewHolderForAdapterPosition(columns - 1);
        if (first == null || last == null) return;
        View firstCard = first.itemView.findViewById(R.id.card), lastCard = last.itemView.findViewById(R.id.card);
        if (firstCard == null || lastCard == null || firstCard.getWidth() == 0) return;
        root.getLocationInWindow(rootAt);
        grid.getLocationInWindow(at);
        // At rest the dock row's item starts on the padding line, below DockDecoration's 6dp offset.
        restDockTop = at[1] - rootAt[1] + grid.getPaddingTop() + Math.round(6 * density) + firstCard.getTop();
        rowHeight = first.itemView.getHeight();
        if (row == null) {
            follow();
            return;
        }
        firstCard.getLocationInWindow(at);
        int left = at[0] - rootAt[0];
        lastCard.getLocationInWindow(at);
        int right = at[0] - rootAt[0] + lastCard.getWidth();
        View top = ((ViewGroup) grid.getParent().getParent()).getChildAt(0);   // the clock and buttons
        top.getLocationInWindow(at);
        headerBottom = at[1] - rootAt[1] + top.getHeight();
        int y = headerBottom + Math.round(28 * density);
        int h = restDockTop - Math.round(44 * density) - y;
        int w = right - left;
        if (h < 80 * density || w <= 0) {
            row.setVisibility(View.INVISIBLE);
            return;
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) row.getLayoutParams();
        if (lp.leftMargin != left || lp.topMargin != y || lp.width != w || lp.height != h) {
            lp.leftMargin = left;
            lp.topMargin = y;
            lp.width = w;
            lp.height = h;
            row.setLayoutParams(lp);
            int gap = Math.round(28 * density);
            int photoW = weather == null ? w : photo == null ? 0 : Math.round((w - gap) * 0.58f);
            if (photo != null) photo.setLayoutParams(new FrameLayout.LayoutParams(photoW, h));
            if (weather != null) {
                FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(photo == null ? w : w - photoW - gap, h);
                wl.leftMargin = photo == null ? 0 : photoW + gap;
                weather.setLayoutParams(wl);
            }
        }
        follow();
    }

    /**
     * Keeps the row riding on the dock: when the dock scrolls up to show the rows below it, the row goes up with it
     * and fades out as it reaches the header, while the rows below the dock (hidden while it rests,
     * hide_grid_until_down) fade in. With the dock scrolled off screen the row is hidden.
     */
    private void follow() {
        if (restDockTop == 0) return;
        RecyclerView.ViewHolder first = grid.findViewHolderForAdapterPosition(0);
        View card = first == null ? null : first.itemView.findViewById(R.id.card);
        int shift;
        if (card == null) {
            shift = Integer.MIN_VALUE / 2;   // the dock is off screen: the grid is well under way
        } else {
            // From the layout, not the screen: the tiles' entrance animation moves them on screen but not in layout.
            RecyclerView.LayoutManager lm = grid.getLayoutManager();
            int decor = lm == null ? 0 : lm.getTopDecorationHeight(first.itemView);
            shift = Math.min(0, first.itemView.getTop() - decor - grid.getPaddingTop());
        }
        if (options.hideGridRows) {
            float over = Math.max(1f, rowHeight * 0.35f);
            float alpha = Math.max(0f, Math.min(1f, (-shift - 2 * density) / over));
            if (alpha != gridAlpha) {
                gridAlpha = alpha;
                for (int i = 0; i < grid.getChildCount(); i++) {
                    View child = grid.getChildAt(i);
                    if (grid.getChildAdapterPosition(child) >= columns) fade(child, alpha);
                }
            }
        }
        if (row == null) return;
        if (card == null) {
            row.setVisibility(View.INVISIBLE);
            return;
        }
        row.setTranslationY(shift);
        float fadeOver = Math.max(1f, row.getHeight() * 0.5f);
        row.setAlpha(Math.max(0f, 1f + shift / fadeOver));
        row.setVisibility(View.VISIBLE);
        if (weather != null) weather.invalidate();   // its glass samples the blur at its new place
    }

    /**
     * The top buttons as liquid glass: clear glass at rest, brighter glass with stronger light when focused, instead
     * of the solid white focused pill. Their text and icons stay white on it.
     */
    private void liquidPills(Activity a, Frost frost) {
        android.content.res.ColorStateList white = android.content.res.ColorStateList.valueOf(0xFFFFFFFF);
        for (int id : new int[]{R.id.btnInputs, R.id.btnWifi, R.id.btnReorder, R.id.btnLibraryTop, R.id.btnSettings}) {
            TextView pill = a.findViewById(id);
            if (pill == null) continue;
            android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
            float r = 21 * density;
            states.addState(new int[]{android.R.attr.state_focused}, new FrostDrawable(frost, pill, r, density, true));
            states.addState(new int[0], new FrostDrawable(frost, pill, r, density));
            pill.setBackground(states);
            pill.setTextColor(white);
            pill.setCompoundDrawableTintList(white);
        }
    }

    void start() {
        if (photo != null) photo.start();
        if (weather != null) weather.start();
    }

    void stop() {
        if (photo != null) photo.stop();
        if (weather != null) weather.stop();
    }

    void releaseForMemoryPressure() {
        if (photo != null) photo.release();
    }

    void close() {
        if (photo != null) photo.close();
        if (weather != null) weather.close();
    }
}

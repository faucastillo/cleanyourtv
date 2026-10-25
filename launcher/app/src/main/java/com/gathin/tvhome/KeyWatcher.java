package com.gathin.tvhome;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remote buttons without a key filter (a theme's {@code "remote_watch"}, this fork only). Some firmware logs every key
 * it sees ({@code WindowManager: interceptKeyTq keycode=N scancode=M}, Hisense/MediaTek Google TV 12); reading that
 * log tells which button was pressed without filtering any key, so a held volume key keeps its repeat, which every
 * key-filtering accessibility helper costs on Android 12 TVs (docs/devices/tcl-55p71b-g09-key-repeat.md). It cannot
 * swallow a press: map only buttons whose own target is gone (with launcherx disabled, its global keys).
 *
 * Needs READ_LOGS, granted once over ADB ({@code KEY_WATCH=1 launcher/build-and-install.sh}). Only key and
 * resumed-activity lines are asked for, parsed and dropped; nothing is stored.
 *
 * <pre>
 * "remote_watch": [ "scan:598:settings", "scan:469:com.google.android.youtube.tv",
 *                   "app:ar.com.flow.androidtv:scan:362:intent:...", "scan:362:intent:content://livetv#Intent;...;end" ]
 * </pre>
 * Targets are RemoteKeys.launch's (a package, settings, inputs, input:id) plus intent: URIs. An "app:" entry applies
 * while that app is in front and wins over the plain one.
 */
final class KeyWatcher {
    private static final String TAG = "TvHome.KeyWatcher";
    private static final Pattern KEY = Pattern.compile("interceptKeyTq keycode=(-?\\d+) scancode=(\\d+)");
    private static final Pattern RESUMED = Pattern.compile("wm_set_resumed_activity.*?\\[\\d+,([^/,\\]]+)/");
    /** Each press logs down and up, and a held key logs its repeats: lines closer than this are the same press. */
    private static final long SAME_PRESS_MS = 600;
    /** An intent sent to an app that was not running reaches its splash screen; it is sent again after these. */
    private static final long[] COLD_RETRIES_MS = {7_000, 14_000};

    private static KeyWatcher running;

    private final Context ctx;
    private final Map<Integer, String> plain = new HashMap<>();
    private final Map<String, Map<Integer, String>> perApp = new HashMap<>();
    private final Map<Integer, Long> lastLine = new HashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile String foreground = "";
    /** Bumped on every press, so a cold-start retry knows another button came after it. */
    private volatile int presses;

    private KeyWatcher(Context ctx, List<String> entries) {
        this.ctx = ctx.getApplicationContext();
        for (String entry : entries) {
            String e = entry.trim();
            if (e.startsWith("app:")) {
                int colon = e.indexOf(':', 4);
                if (colon < 0) continue;
                String pkg = e.substring(4, colon);
                Map<Integer, String> one = RemoteKeys.parseAppScancodes(single(e.substring(colon + 1)));
                if (!one.isEmpty()) perApp.computeIfAbsent(pkg, k -> new HashMap<>()).putAll(one);
            } else {
                plain.putAll(RemoteKeys.parseAppScancodes(single(e)));
            }
        }
    }

    private static List<String> single(String entry) {
        List<String> one = new ArrayList<>();
        one.add(entry);
        return one;
    }

    /** Starts the one reader for this process; nothing without entries or without READ_LOGS. */
    static synchronized void start(Context ctx, List<String> entries) {
        if (running != null || entries.isEmpty()) return;
        if (ctx.checkSelfPermission(Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_LOGS not granted: run launcher/build-and-install.sh with KEY_WATCH=1");
            return;
        }
        running = new KeyWatcher(ctx, entries);
        Thread t = new Thread(running::readForever, "remote-watch");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private void readForever() {
        while (true) {
            Process logcat = null;
            try {
                // -T 1: from now on, not the old buffer. Only the two tags are delivered past the filter.
                logcat = new ProcessBuilder("logcat", "-b", "main,events", "-v", "brief", "-T", "1",
                        "WindowManager:D", "wm_set_resumed_activity:I", "*:S").redirectErrorStream(true).start();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(logcat.getInputStream()))) {
                    String line;
                    while ((line = in.readLine()) != null) onLine(line);
                }
            } catch (Exception e) {
                Log.w(TAG, "log reader stopped: " + e.getMessage());
            } finally {
                if (logcat != null) logcat.destroy();
            }
            SystemClock.sleep(5_000);   // logd restarted or the reader died: start a new one
        }
    }

    private void onLine(String line) {
        Matcher resumed = RESUMED.matcher(line);
        if (resumed.find()) {
            foreground = resumed.group(1);
            return;
        }
        Matcher key = KEY.matcher(line);
        if (!key.find()) return;
        int scan = Integer.parseInt(key.group(2));
        long now = SystemClock.uptimeMillis();
        Long last = lastLine.put(scan, now);
        if (last != null && now - last < SAME_PRESS_MS) return;
        presses++;
        Map<Integer, String> forApp = perApp.get(foreground);
        String target = forApp != null && forApp.containsKey(scan) ? forApp.get(scan) : plain.get(scan);
        if (target == null) return;
        String from = foreground;
        int press = presses;
        main.post(() -> run(target, from, press));
    }

    private void run(String target, String from, int press) {
        if (!target.startsWith("intent:")) {
            RemoteKeys.launch(ctx, target);
            return;
        }
        Intent intent;
        try {
            intent = Intent.parseUri(target.substring("intent:".length()), Intent.URI_INTENT_SCHEME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } catch (URISyntaxException e) {
            Log.w(TAG, "bad intent target: " + target);
            return;
        }
        send(intent);
        // An app that was not in front starts at its own home and drops the request while it loads (Flow's guide):
        // ask again once it has, unless the owner has pressed something since.
        String pkg = intent.getPackage();
        if (pkg == null || pkg.equals(from)) return;
        for (long delay : COLD_RETRIES_MS) {
            main.postDelayed(() -> {
                if (presses == press && pkg.equals(foreground)) send(intent);
            }, delay);
        }
    }

    private void send(Intent intent) {
        try {
            ctx.startActivity(intent);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not start " + intent + ": " + e.getMessage());
        }
    }
}

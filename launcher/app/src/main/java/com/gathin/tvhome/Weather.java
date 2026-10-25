package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The weather now for glass_home's card, from Open-Meteo (no account or key). The only network request this fork's
 * launcher makes: one small GET, at most every {@link #REFRESH_MS} and only while Home is on screen. The last answer
 * is kept, so Home shows it at once after a restart.
 */
final class Weather {
    static final long REFRESH_MS = 30 * 60_000L;
    /** After a failure, try again sooner than the full interval. */
    static final long RETRY_MS = 5 * 60_000L;
    private static final String PREFS = "glass_weather";

    /** A reading; {@code code} is the WMO weather code Open-Meteo returns. */
    static final class Now {
        final double temperature;
        final int code;
        final boolean day;
        final long fetchedAt;

        Now(double temperature, int code, boolean day, long fetchedAt) {
            this.temperature = temperature;
            this.code = code;
            this.day = day;
            this.fetchedAt = fetchedAt;
        }
    }

    private Weather() {}

    /** Blocking: call off the UI thread. Null on any failure. */
    static Now fetch(double latitude, double longitude) {
        HttpURLConnection http = null;
        try {
            URL url = new URL(String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,weather_code,is_day&timezone=auto", latitude, longitude));
            http = (HttpURLConnection) url.openConnection();
            http.setConnectTimeout(10_000);
            http.setReadTimeout(10_000);
            http.setRequestProperty("User-Agent", "cleanyourtv-glass-home");
            if (http.getResponseCode() != 200) return null;
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream in = http.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0 && body.size() < 64 * 1024) body.write(buf, 0, n);
            }
            JSONObject current = new JSONObject(body.toString(StandardCharsets.UTF_8.name())).getJSONObject("current");
            return new Now(current.getDouble("temperature_2m"), current.getInt("weather_code"),
                    current.optInt("is_day", 1) == 1, System.currentTimeMillis());
        } catch (Exception e) {
            return null;
        } finally {
            if (http != null) http.disconnect();
        }
    }

    static void save(Context ctx, Now now) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putFloat("temperature", (float) now.temperature).putInt("code", now.code)
                .putBoolean("day", now.day).putLong("fetched", now.fetchedAt).apply();
    }

    static Now load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains("code")) return null;
        return new Now(p.getFloat("temperature", 0f), p.getInt("code", 0), p.getBoolean("day", true),
                p.getLong("fetched", 0));
    }

    /** The WMO code in words, in Spanish (this fork's TV). */
    static String describe(int code) {
        switch (code) {
            case 0: return "Despejado";
            case 1: return "Mayormente despejado";
            case 2: return "Parcialmente nublado";
            case 3: return "Nublado";
            case 45: case 48: return "Niebla";
            case 51: case 53: case 55: return "Llovizna";
            case 56: case 57: return "Llovizna helada";
            case 61: return "Lluvia débil";
            case 63: return "Lluvia";
            case 65: return "Lluvia fuerte";
            case 66: case 67: return "Lluvia helada";
            case 71: case 73: case 75: case 77: return "Nieve";
            case 80: case 81: return "Chaparrones";
            case 82: return "Chaparrones fuertes";
            case 85: case 86: return "Nevadas";
            case 95: return "Tormenta";
            case 96: case 99: return "Tormenta con granizo";
            default: return "";
        }
    }

    /** Which picture WeatherIcon draws for a WMO code. */
    static int kind(int code) {
        if (code == 0 || code == 1) return WeatherIcon.CLEAR;
        if (code == 2) return WeatherIcon.PARTLY;
        if (code == 3) return WeatherIcon.CLOUDY;
        if (code == 45 || code == 48) return WeatherIcon.FOG;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return WeatherIcon.SNOW;
        if (code >= 95) return WeatherIcon.STORM;
        if (code >= 51) return WeatherIcon.RAIN;
        return WeatherIcon.CLOUDY;
    }
}

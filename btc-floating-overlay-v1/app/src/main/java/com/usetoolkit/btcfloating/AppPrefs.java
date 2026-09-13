package com.usetoolkit.btcfloating;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class AppPrefs {
    private static final String PREF = "wantview_prefs";
    private static final String KEY_SYMBOLS = "symbols_v2";
    private static final String LEGACY_PREF = "btc_float_prefs";
    private static final String LEGACY_SYMBOLS = "symbols";
    private static final String KEY_ALPHA = "alpha";
    private static final String KEY_INTERVAL = "interval";
    private static final String KEY_COLLAPSED = "collapsed";

    private AppPrefs() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static List<String> getSymbols(Context c) {
        String raw = prefs(c).getString(KEY_SYMBOLS, null);
        if (raw != null) {
            try {
                JSONArray a = new JSONArray(raw);
                ArrayList<String> out = new ArrayList<>();
                for (int i = 0; i < a.length(); i++) {
                    String s = normalize(a.optString(i));
                    if (!s.isEmpty() && !out.contains(s)) out.add(s);
                }
                if (!out.isEmpty()) return out;
            } catch (Exception ignored) { }
        }

        Set<String> legacy = c.getSharedPreferences(LEGACY_PREF, Context.MODE_PRIVATE)
                .getStringSet(LEGACY_SYMBOLS, null);
        if (legacy != null && !legacy.isEmpty()) {
            ArrayList<String> migrated = new ArrayList<>();
            for (String s : new LinkedHashSet<>(legacy)) {
                String normalized = normalize(s);
                if (!normalized.isEmpty() && !migrated.contains(normalized)) migrated.add(normalized);
            }
            if (!migrated.isEmpty()) {
                setSymbols(c, migrated);
                return migrated;
            }
        }

        return new ArrayList<>(Arrays.asList(
                "BINANCE:BTCUSDT",
                "BINANCE:ETHUSDT",
                "BINANCE:SOLUSDT"
        ));
    }

    static void setSymbols(Context c, List<String> symbols) {
        JSONArray a = new JSONArray();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String s : symbols) {
            String normalized = normalize(s);
            if (!normalized.isEmpty()) unique.add(normalized);
        }
        for (String s : unique) a.put(s);
        prefs(c).edit().putString(KEY_SYMBOLS, a.toString()).apply();
    }

    static float getAlpha(Context c) {
        return prefs(c).getFloat(KEY_ALPHA, 0.92f);
    }

    static void setAlpha(Context c, float alpha) {
        prefs(c).edit().putFloat(KEY_ALPHA, alpha).apply();
    }

    static long getInterval(Context c) {
        return prefs(c).getLong(KEY_INTERVAL, 1000L);
    }

    static void setInterval(Context c, long ms) {
        prefs(c).edit().putLong(KEY_INTERVAL, ms).apply();
    }

    static boolean getCollapsed(Context c) {
        return prefs(c).getBoolean(KEY_COLLAPSED, false);
    }

    static void setCollapsed(Context c, boolean collapsed) {
        prefs(c).edit().putBoolean(KEY_COLLAPSED, collapsed).apply();
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String out = s.trim().toUpperCase(java.util.Locale.US);
        if (out.isEmpty()) return "";
        if (!out.contains(":")) out = "BINANCE:" + out;
        return out;
    }
}

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
    private static final String KEY_COLLAPSED_SYMBOL = "collapsed_symbol";
    private static final String KEY_TEXT_SIZE = "overlay_text_size";
    private static final String KEY_PANEL_WIDTH = "overlay_panel_width";
    private static final String KEY_POWER_MODE = "power_mode";
    private static final String KEY_MOBILE_SAVER = "mobile_saver";

    static final int POWER_REALTIME = 0;
    static final int POWER_BALANCED = 1;
    static final int POWER_ULTRA = 2;

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

        ArrayList<String> defaults = new ArrayList<>(Arrays.asList(
                "BINANCE:BTCUSDT",
                "BINANCE:ETHUSDT",
                "BINANCE:SOLUSDT"
        ));
        setSymbols(c, defaults);
        return defaults;
    }

    static void setSymbols(Context c, List<String> symbols) {
        JSONArray a = new JSONArray();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (symbols != null) {
            for (String s : symbols) {
                String normalized = normalize(s);
                if (!normalized.isEmpty()) unique.add(normalized);
            }
        }
        for (String s : unique) a.put(s);

        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit().putString(KEY_SYMBOLS, a.toString());
        String collapsed = normalize(p.getString(KEY_COLLAPSED_SYMBOL, ""));
        if (!unique.contains(collapsed)) {
            if (unique.isEmpty()) e.remove(KEY_COLLAPSED_SYMBOL);
            else e.putString(KEY_COLLAPSED_SYMBOL, unique.iterator().next());
        }
        e.apply();
    }

    static float getAlpha(Context c) {
        return clamp(prefs(c).getFloat(KEY_ALPHA, 0.92f), 0.20f, 1.00f);
    }

    static void setAlpha(Context c, float alpha) {
        prefs(c).edit().putFloat(KEY_ALPHA, clamp(alpha, 0.20f, 1.00f)).apply();
    }

    static long getInterval(Context c) {
        long v = prefs(c).getLong(KEY_INTERVAL, 1000L);
        return v == 500L || v == 3000L ? v : 1000L;
    }

    static void setInterval(Context c, long ms) {
        prefs(c).edit().putLong(KEY_INTERVAL, ms == 500L || ms == 3000L ? ms : 1000L).apply();
    }

    static boolean getCollapsed(Context c) {
        return prefs(c).getBoolean(KEY_COLLAPSED, false);
    }

    static void setCollapsed(Context c, boolean collapsed) {
        prefs(c).edit().putBoolean(KEY_COLLAPSED, collapsed).apply();
    }

    static String getCollapsedSymbol(Context c) {
        List<String> symbols = getSymbols(c);
        if (symbols.isEmpty()) return "";
        String saved = normalize(prefs(c).getString(KEY_COLLAPSED_SYMBOL, ""));
        if (symbols.contains(saved)) return saved;
        String fallback = symbols.get(0);
        setCollapsedSymbol(c, fallback);
        return fallback;
    }

    static void setCollapsedSymbol(Context c, String symbol) {
        String normalized = normalize(symbol);
        if (normalized.isEmpty()) prefs(c).edit().remove(KEY_COLLAPSED_SYMBOL).apply();
        else prefs(c).edit().putString(KEY_COLLAPSED_SYMBOL, normalized).apply();
    }

    static int getTextSize(Context c) {
        return clamp(prefs(c).getInt(KEY_TEXT_SIZE, 13), 11, 19);
    }

    static void setTextSize(Context c, int sp) {
        prefs(c).edit().putInt(KEY_TEXT_SIZE, clamp(sp, 11, 19)).apply();
    }

    static int getPanelWidth(Context c) {
        return clamp(prefs(c).getInt(KEY_PANEL_WIDTH, 280), 220, 360);
    }

    static void setPanelWidth(Context c, int dp) {
        prefs(c).edit().putInt(KEY_PANEL_WIDTH, clamp(dp, 220, 360)).apply();
    }

    static int getPowerMode(Context c) {
        int mode = prefs(c).getInt(KEY_POWER_MODE, POWER_BALANCED);
        if (mode < POWER_REALTIME || mode > POWER_ULTRA) return POWER_BALANCED;
        return mode;
    }

    static void setPowerMode(Context c, int mode) {
        if (mode < POWER_REALTIME || mode > POWER_ULTRA) mode = POWER_BALANCED;
        prefs(c).edit().putInt(KEY_POWER_MODE, mode).apply();
    }

    static boolean getMobileSaver(Context c) {
        return prefs(c).getBoolean(KEY_MOBILE_SAVER, true);
    }

    static void setMobileSaver(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_MOBILE_SAVER, enabled).apply();
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String out = s.trim().toUpperCase(java.util.Locale.US);
        if (out.isEmpty()) return "";
        if (!out.contains(":")) out = "BINANCE:" + out;
        return out;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}

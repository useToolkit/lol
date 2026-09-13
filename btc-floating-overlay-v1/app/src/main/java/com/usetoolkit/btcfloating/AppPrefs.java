package com.usetoolkit.btcfloating;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class AppPrefs {
    private static final String PREF = "btc_float_prefs";
    private static final String KEY_SYMBOLS = "symbols";
    private static final String KEY_ALPHA = "alpha";
    private static final String KEY_INTERVAL = "interval";

    private AppPrefs() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static List<String> getSymbols(Context c) {
        Set<String> saved = prefs(c).getStringSet(KEY_SYMBOLS, null);
        if (saved == null || saved.isEmpty()) {
            return new ArrayList<>(Arrays.asList("BTCUSDT", "ETHUSDT", "SOLUSDT"));
        }
        return new ArrayList<>(new LinkedHashSet<>(saved));
    }

    static void setSymbols(Context c, List<String> symbols) {
        prefs(c).edit().putStringSet(KEY_SYMBOLS, new LinkedHashSet<>(symbols)).apply();
    }

    static float getAlpha(Context c) {
        return prefs(c).getFloat(KEY_ALPHA, 0.88f);
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
}

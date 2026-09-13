package com.usetoolkit.btcfloating;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

final class NetworkUtil {
    static final int OFFLINE = 0;
    static final int WIFI = 1;
    static final int CELLULAR = 2;
    static final int OTHER = 3;

    private NetworkUtil() { }

    static int getType(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return OFFLINE;
            Network network = cm.getActiveNetwork();
            if (network == null) return OFFLINE;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return OFFLINE;
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return WIFI;
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return CELLULAR;
            return OTHER;
        } catch (Exception ignored) {
            return OTHER;
        }
    }

    static boolean isConnected(Context context) {
        return getType(context) != OFFLINE;
    }

    static boolean isCellular(Context context) {
        return getType(context) == CELLULAR;
    }

    static String label(Context context) {
        switch (getType(context)) {
            case WIFI: return "Wi‑Fi";
            case CELLULAR: return "모바일 데이터";
            case OTHER: return "기타 네트워크";
            default: return "오프라인";
        }
    }
}

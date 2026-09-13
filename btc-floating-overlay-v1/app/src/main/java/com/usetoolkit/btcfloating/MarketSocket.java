package com.usetoolkit.btcfloating;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

final class MarketSocket {
    interface Listener {
        void onPrice(String symbol, double price, double changePct);
        void onState(String state);
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private WebSocket socket;
    private List<String> lastSymbols;
    private boolean manualClose;
    private int retryCount;

    MarketSocket(Listener listener) {
        this.listener = listener;
    }

    synchronized void connect(List<String> symbols) {
        closeInternal(false);
        if (symbols == null || symbols.isEmpty()) return;
        lastSymbols = symbols;
        manualClose = false;
        StringBuilder streams = new StringBuilder();
        for (String s : symbols) {
            if (streams.length() > 0) streams.append('/');
            streams.append(s.toLowerCase(Locale.US)).append("@miniTicker");
        }
        String url = "wss://stream.binance.com:9443/stream?streams=" + streams;
        Request req = new Request.Builder().url(url).build();
        listener.onState("connecting");
        socket = client.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                retryCount = 0;
                listener.onState("connected");
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject root = new JSONObject(text);
                    JSONObject data = root.getJSONObject("data");
                    String symbol = data.getString("s");
                    double close = data.getDouble("c");
                    double open = data.getDouble("o");
                    double change = open == 0 ? 0 : ((close - open) / open) * 100.0;
                    listener.onPrice(symbol, close, change);
                } catch (Exception ignored) { }
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                listener.onState("disconnected");
                scheduleReconnect();
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                listener.onState("closed");
                if (!manualClose) scheduleReconnect();
            }
        });
    }

    synchronized void disconnect() {
        manualClose = true;
        closeInternal(true);
    }

    private synchronized void closeInternal(boolean clear) {
        if (socket != null) {
            socket.cancel();
            socket = null;
        }
        if (clear) lastSymbols = null;
    }

    private void scheduleReconnect() {
        if (manualClose || lastSymbols == null || lastSymbols.isEmpty()) return;
        long delay = Math.min(30000L, 1000L * (1L << Math.min(retryCount++, 5)));
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            if (!manualClose && lastSymbols != null) connect(lastSymbols);
        }, delay);
    }
}

package com.usetoolkit.btcfloating;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
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
            .pingInterval(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final SecureRandom random = new SecureRandom();
    private WebSocket socket;
    private List<String> lastSymbols;
    private boolean manualClose;
    private int retryCount;
    private String session;
    private int generation;

    MarketSocket(Listener listener) {
        this.listener = listener;
    }

    synchronized void connect(List<String> symbols) {
        generation++;
        final int gen = generation;
        closeInternal(false);
        if (symbols == null || symbols.isEmpty()) return;
        lastSymbols = new ArrayList<>(symbols);
        manualClose = false;
        session = randomSession();

        Request req = new Request.Builder()
                .url("wss://data.tradingview.com/socket.io/websocket")
                .header("Origin", "https://www.tradingview.com")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36")
                .build();
        listener.onState("connecting");
        socket = client.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                if (gen != generation) return;
                retryCount = 0;
                listener.onState("connected");
                sendMessage("set_auth_token", new JSONArray().put("unauthorized_user_token"));
                sendMessage("quote_create_session", new JSONArray().put(session));
                JSONArray fields = new JSONArray()
                        .put(session)
                        .put("lp")
                        .put("ch")
                        .put("chp");
                sendMessage("quote_set_fields", fields);

                JSONArray add = new JSONArray().put(session);
                for (String s : lastSymbols) add.put(s);
                sendMessage("quote_add_symbols", add);
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                if (gen != generation) return;
                parseFrames(text);
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (gen != generation) return;
                listener.onState("disconnected");
                scheduleReconnect(gen);
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                if (gen != generation) return;
                listener.onState("closed");
                if (!manualClose) scheduleReconnect(gen);
            }
        });
    }

    synchronized void disconnect() {
        generation++;
        manualClose = true;
        closeInternal(true);
    }

    private synchronized void closeInternal(boolean clear) {
        handler.removeCallbacksAndMessages(null);
        if (socket != null) {
            socket.cancel();
            socket = null;
        }
        if (clear) lastSymbols = null;
    }

    private void parseFrames(String text) {
        try {
            int pos = 0;
            boolean found = false;
            while (pos < text.length()) {
                int start = text.indexOf("~m~", pos);
                if (start < 0) break;
                int lenStart = start + 3;
                int lenEnd = text.indexOf("~m~", lenStart);
                if (lenEnd < 0) break;
                int len = Integer.parseInt(text.substring(lenStart, lenEnd));
                int payloadStart = lenEnd + 3;
                int payloadEnd = payloadStart + len;
                if (payloadEnd > text.length()) payloadEnd = text.length();
                String payload = text.substring(payloadStart, payloadEnd);
                handlePayload(payload);
                found = true;
                pos = payloadEnd;
            }
            if (!found && text.startsWith("{")) handlePayload(text);
        } catch (Exception ignored) { }
    }

    private void handlePayload(String payload) {
        if (payload == null || payload.isEmpty()) return;
        if (payload.startsWith("~h~")) {
            WebSocket ws = socket;
            if (ws != null) ws.send(frame(payload));
            return;
        }
        try {
            JSONObject msg = new JSONObject(payload);
            String method = msg.optString("m");
            if ("qsd".equals(method)) {
                JSONArray p = msg.optJSONArray("p");
                if (p == null || p.length() < 2) return;
                JSONObject q = p.optJSONObject(1);
                if (q == null) return;
                String symbol = q.optString("n", "").toUpperCase(Locale.US);
                JSONObject v = q.optJSONObject("v");
                if (symbol.isEmpty() || v == null || !v.has("lp") || v.isNull("lp")) return;
                double price = v.optDouble("lp", Double.NaN);
                if (Double.isNaN(price)) return;
                double change = v.optDouble("chp", 0.0);
                listener.onPrice(symbol, price, change);
            } else if ("critical_error".equals(method) || "protocol_error".equals(method)) {
                listener.onState("restricted");
            }
        } catch (Exception ignored) { }
    }

    private synchronized void sendMessage(String method, JSONArray params) {
        WebSocket ws = socket;
        if (ws == null) return;
        try {
            JSONObject body = new JSONObject();
            body.put("m", method);
            body.put("p", params);
            ws.send(frame(body.toString()));
        } catch (Exception ignored) { }
    }

    private String frame(String payload) {
        int len = payload.getBytes(StandardCharsets.UTF_8).length;
        return "~m~" + len + "~m~" + payload;
    }

    private String randomSession() {
        final String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder b = new StringBuilder("qs_");
        for (int i = 0; i < 12; i++) b.append(chars.charAt(random.nextInt(chars.length())));
        return b.toString();
    }

    private void scheduleReconnect(int gen) {
        if (gen != generation || manualClose || lastSymbols == null || lastSymbols.isEmpty()) return;
        long delay = Math.min(30000L, 1000L * (1L << Math.min(retryCount++, 5)));
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            if (gen == generation && !manualClose && lastSymbols != null) connect(lastSymbols);
        }, delay);
    }
}

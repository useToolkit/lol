package com.usetoolkit.btcfloating;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class SymbolSearchClient {
    interface SearchCallback {
        void onResult(List<Item> items);
        void onError(String message);
    }

    static final class Item {
        final String fullName;
        final String symbol;
        final String exchange;
        final String description;
        final String type;

        Item(String fullName, String symbol, String exchange, String description, String type) {
            this.fullName = fullName;
            this.symbol = symbol;
            this.exchange = exchange;
            this.description = description;
            this.type = type;
        }
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    private Call currentCall;

    synchronized void search(String query, SearchCallback callback) {
        if (currentCall != null) currentCall.cancel();
        String q = query == null ? "" : query.trim();
        if (q.length() < 1) {
            callback.onResult(new ArrayList<>());
            return;
        }

        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("symbol-search.tradingview.com")
                .addPathSegment("symbol_search")
                .addPathSegment("v3")
                .addQueryParameter("text", q)
                .addQueryParameter("hl", "1")
                .addQueryParameter("exchange", "")
                .addQueryParameter("lang", "en")
                .addQueryParameter("search_type", "undefined")
                .addQueryParameter("domain", "production")
                .build();

        Request req = new Request.Builder()
                .url(url)
                .header("Origin", "https://www.tradingview.com")
                .header("Referer", "https://www.tradingview.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36")
                .build();

        currentCall = client.newCall(req);
        currentCall.enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException e) {
                if (!call.isCanceled()) callback.onError(e.getMessage() == null ? "검색 연결 오류" : e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("TradingView 검색 응답 " + response.code());
                    response.close();
                    return;
                }
                String body = response.body() == null ? "" : response.body().string();
                response.close();
                try {
                    callback.onResult(parse(body));
                } catch (Exception e) {
                    callback.onError("검색 결과 해석 오류");
                }
            }
        });
    }

    synchronized void cancel() {
        if (currentCall != null) currentCall.cancel();
        currentCall = null;
    }

    private List<Item> parse(String body) throws Exception {
        JSONArray symbols;
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.startsWith("[")) {
            symbols = new JSONArray(trimmed);
        } else {
            JSONObject root = new JSONObject(trimmed);
            symbols = root.optJSONArray("symbols");
        }
        ArrayList<Item> out = new ArrayList<>();
        if (symbols == null) return out;
        Set<String> seen = new LinkedHashSet<>();
        int limit = Math.min(symbols.length(), 40);
        for (int i = 0; i < limit; i++) {
            JSONObject o = symbols.optJSONObject(i);
            if (o == null) continue;
            String symbol = clean(o.optString("symbol"));
            String exchange = clean(o.optString("exchange"));
            String prefix = clean(o.optString("prefix"));
            String description = clean(o.optString("description"));
            String type = clean(o.optString("type"));
            String venue = prefix.isEmpty() ? exchange : prefix;
            if (symbol.isEmpty()) continue;
            String fullName;
            if (symbol.contains(":")) fullName = symbol.toUpperCase(java.util.Locale.US);
            else if (!venue.isEmpty()) fullName = (venue + ":" + symbol).toUpperCase(java.util.Locale.US);
            else continue;
            if (!seen.add(fullName)) continue;
            out.add(new Item(fullName, symbol, exchange.isEmpty() ? venue : exchange, description, type));
        }
        return out;
    }

    private String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim();
    }
}

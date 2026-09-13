package com.usetoolkit.btcfloating;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 16, 30);
    private static final int CARD = Color.rgb(17, 28, 45);
    private static final int MUTED = Color.rgb(151, 166, 188);
    private static final int ACCENT = Color.rgb(45, 171, 255);

    private final long[] intervals = {3000L, 1000L, 500L};
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private LinearLayout symbolList;
    private LinearLayout searchResults;
    private TextView searchStatus;
    private SymbolSearchClient searchClient;
    private Runnable pendingSearch;
    private int searchVersion;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        searchClient = new SymbolSearchClient();
        buildUi();
        requestNotificationPermission();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(36));
        scroll.addView(root);

        TextView brand = text("Wantview", 30, true);
        root.addView(brand);
        TextView sub = text("Markets, always with you", 14, false);
        sub.setTextColor(MUTED);
        sub.setPadding(0, 0, 0, dp(18));
        root.addView(sub);

        Button overlay = button("플로팅 가격창 시작 / 새로고침", true);
        overlay.setOnClickListener(v -> startOverlay());
        root.addView(overlay, lpMatch(dp(56)));

        Button stop = button("플로팅 가격창 종료", false);
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        LinearLayout.LayoutParams stopLp = lpMatch(dp(50));
        stopLp.topMargin = dp(8);
        root.addView(stop, stopLp);

        root.addView(section("TradingView 종목 검색"));
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("AAPL, BTCUSDT, GOLD, USDJPY…");
        search.setHintTextColor(Color.rgb(105, 125, 151));
        search.setTextColor(Color.WHITE);
        search.setTextSize(17);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(roundRect(CARD, Color.rgb(49, 68, 94), 14));
        root.addView(search, lpMatch(dp(54)));

        searchStatus = text("검색어를 입력하면 TradingView 종목을 찾습니다.", 12, false);
        searchStatus.setTextColor(MUTED);
        searchStatus.setPadding(dp(2), dp(8), 0, dp(4));
        root.addView(searchStatus);

        searchResults = new LinearLayout(this);
        searchResults.setOrientation(LinearLayout.VERTICAL);
        root.addView(searchResults);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable e) { scheduleSearch(e.toString()); }
        });

        root.addView(section("플로팅에 표시할 종목"));
        symbolList = new LinearLayout(this);
        symbolList.setOrientation(LinearLayout.VERTICAL);
        root.addView(symbolList);
        refreshSymbols();

        root.addView(section("투명도"));
        SeekBar alpha = new SeekBar(this);
        alpha.setMax(80);
        alpha.setProgress(Math.round((AppPrefs.getAlpha(this) - 0.2f) * 100f));
        alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                AppPrefs.setAlpha(MainActivity.this, 0.2f + progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        root.addView(alpha);

        root.addView(section("표시 갱신 속도"));
        Spinner spinner = new Spinner(this);
        String[] labels = {"절전 3초", "기본 1초 (추천)", "빠름 0.5초"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(Color.WHITE);
                v.setPadding(dp(12), 0, dp(12), 0);
                return v;
            }
        };
        spinner.setAdapter(adapter);
        long current = AppPrefs.getInterval(this);
        spinner.setSelection(current == 3000 ? 0 : current == 500 ? 2 : 1);
        spinner.setBackground(roundRect(CARD, Color.rgb(49, 68, 94), 12));
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                AppPrefs.setInterval(MainActivity.this, intervals[position]);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        root.addView(spinner, lpMatch(dp(52)));

        TextView note = text("TradingView 심볼 검색과 시세 세션을 사용합니다. 로그인하지 않은 공개 세션은 거래소 정책에 따라 시세가 지연되거나 일부 종목이 제한될 수 있습니다. 플로팅 창의 − / + 버튼으로 접기·펼치기가 가능합니다.", 12, false);
        note.setPadding(0, dp(22), 0, 0);
        note.setTextColor(MUTED);
        root.addView(note);

        setContentView(scroll);
    }

    private void scheduleSearch(String raw) {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        if (searchClient != null) searchClient.cancel();
        searchResults.removeAllViews();
        String query = raw == null ? "" : raw.trim();
        int version = ++searchVersion;
        if (query.isEmpty()) {
            searchStatus.setText("검색어를 입력하면 TradingView 종목을 찾습니다.");
            return;
        }
        searchStatus.setText("검색 준비 중…");
        pendingSearch = () -> {
            searchStatus.setText("TradingView에서 검색 중…");
            searchClient.search(query, new SymbolSearchClient.SearchCallback() {
                @Override public void onResult(List<SymbolSearchClient.Item> items) {
                    runOnUiThread(() -> {
                        if (version != searchVersion) return;
                        renderSearchResults(items);
                    });
                }

                @Override public void onError(String message) {
                    runOnUiThread(() -> {
                        if (version != searchVersion) return;
                        searchStatus.setText("검색 실패 · " + message);
                    });
                }
            });
        };
        searchHandler.postDelayed(pendingSearch, 450L);
    }

    private void renderSearchResults(List<SymbolSearchClient.Item> items) {
        searchResults.removeAllViews();
        if (items == null || items.isEmpty()) {
            searchStatus.setText("검색 결과가 없습니다.");
            return;
        }
        searchStatus.setText("검색 결과 " + items.size() + "개 · 원하는 종목을 추가하세요.");
        List<String> selected = AppPrefs.getSymbols(this);
        int max = Math.min(20, items.size());
        for (int i = 0; i < max; i++) {
            SymbolSearchClient.Item item = items.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(8), dp(8));
            row.setBackground(roundRect(CARD, Color.rgb(40, 57, 80), 12));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView top = text(item.symbol + "  ·  " + item.exchange, 15, true);
            info.addView(top);
            String detail = item.description;
            if (item.type != null && !item.type.isEmpty()) detail += (detail.isEmpty() ? "" : "  ·  ") + item.type;
            TextView desc = text(detail, 11, false);
            desc.setTextColor(MUTED);
            info.addView(desc);
            row.addView(info, new LinearLayout.LayoutParams(0, dp(54), 1f));

            Button add = button(selected.contains(item.fullName) ? "추가됨" : "+ 추가", true);
            add.setEnabled(!selected.contains(item.fullName));
            add.setOnClickListener(v -> {
                List<String> now = AppPrefs.getSymbols(this);
                if (!now.contains(item.fullName)) now.add(item.fullName);
                AppPrefs.setSymbols(this, now);
                refreshSymbols();
                add.setText("추가됨");
                add.setEnabled(false);
            });
            row.addView(add, new LinearLayout.LayoutParams(dp(84), dp(42)));
            LinearLayout.LayoutParams rowLp = lpMatch(dp(70));
            rowLp.bottomMargin = dp(7);
            searchResults.addView(row, rowLp);
        }
    }

    private void refreshSymbols() {
        if (symbolList == null) return;
        symbolList.removeAllViews();
        List<String> list = AppPrefs.getSymbols(this);
        if (list.isEmpty()) {
            TextView empty = text("표시할 종목이 없습니다. 위 검색에서 추가하세요.", 13, false);
            empty.setTextColor(MUTED);
            symbolList.addView(empty, lpMatch(dp(48)));
            return;
        }
        for (String s : list) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(6), dp(8), dp(6));
            row.setBackground(roundRect(CARD, Color.rgb(40, 57, 80), 12));
            LinearLayout names = new LinearLayout(this);
            names.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(symbolPart(s), 16, true);
            names.addView(name);
            TextView full = text(s, 11, false);
            full.setTextColor(MUTED);
            names.addView(full);
            row.addView(names, new LinearLayout.LayoutParams(0, dp(52), 1f));
            Button remove = button("삭제", false);
            remove.setOnClickListener(v -> {
                List<String> now = AppPrefs.getSymbols(this);
                now.remove(s);
                AppPrefs.setSymbols(this, now);
                refreshSymbols();
            });
            row.addView(remove, new LinearLayout.LayoutParams(dp(72), dp(42)));
            LinearLayout.LayoutParams rowLp = lpMatch(dp(66));
            rowLp.bottomMargin = dp(7);
            symbolList.addView(row, rowLp);
        }
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "'다른 앱 위에 표시' 권한을 켠 뒤 다시 시작 버튼을 눌러주세요.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent service = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
        Toast.makeText(this, "Wantview 플로팅을 시작했습니다.", Toast.LENGTH_SHORT).show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private TextView section(String s) {
        TextView t = text(s, 17, true);
        t.setTextColor(Color.rgb(118, 207, 255));
        t.setPadding(0, dp(24), 0, dp(9));
        return t;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s == null ? "" : s);
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(roundRect(primary ? Color.rgb(19, 117, 237) : CARD,
                primary ? Color.rgb(62, 174, 255) : Color.rgb(56, 73, 98), 12));
        return b;
    }

    private GradientDrawable roundRect(int color, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private String symbolPart(String full) {
        int p = full.indexOf(':');
        return p >= 0 && p + 1 < full.length() ? full.substring(p + 1) : full;
    }

    private LinearLayout.LayoutParams lpMatch(int h) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        searchHandler.removeCallbacksAndMessages(null);
        if (searchClient != null) searchClient.cancel();
        super.onDestroy();
    }
}

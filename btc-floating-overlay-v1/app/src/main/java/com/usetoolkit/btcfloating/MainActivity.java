package com.usetoolkit.btcfloating;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
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
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 16, 30);
    private static final int CARD = Color.rgb(17, 28, 45);
    private static final int CARD_2 = Color.rgb(22, 36, 57);
    private static final int MUTED = Color.rgb(151, 166, 188);
    private static final int ACCENT = Color.rgb(45, 171, 255);
    private static final int GREEN = Color.rgb(76, 219, 143);
    private static final int RED = Color.rgb(255, 105, 116);

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private LinearLayout symbolList;
    private LinearLayout searchResults;
    private TextView searchStatus;
    private TextView selectedSummary;
    private TextView permissionState;
    private TextView previewText;
    private TextView previewMeta;
    private LinearLayout previewBox;
    private TextView alphaValue;
    private TextView fontValue;
    private TextView widthValue;
    private Button speed500;
    private Button speed1000;
    private Button speed3000;
    private EditText searchInput;
    private SymbolSearchClient searchClient;
    private Runnable pendingSearch;
    private int searchVersion;
    private int dragFrom = -1;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        searchClient = new SymbolSearchClient();
        buildUi();
        requestNotificationPermission();
    }

    @Override protected void onResume() {
        super.onResume();
        updatePermissionState();
    }

    private void buildUi() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(26));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        screen.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text("Wantview", 29, true);
        brandText.addView(brand);
        TextView sub = text("Markets, always with you", 13, false);
        sub.setTextColor(MUTED);
        brandText.addView(sub);
        brandRow.addView(brandText, new LinearLayout.LayoutParams(0, dp(58), 1f));

        permissionState = pill("권한 확인", false);
        permissionState.setOnClickListener(v -> openOverlayPermissionIfNeeded());
        brandRow.addView(permissionState, new LinearLayout.LayoutParams(dp(116), dp(38)));
        root.addView(brandRow);

        TextView intro = text("보고 싶은 시장만 골라 화면 위에 가볍게 띄우세요.", 14, false);
        intro.setTextColor(Color.rgb(201, 213, 229));
        intro.setPadding(0, dp(8), 0, dp(18));
        root.addView(intro);

        LinearLayout searchCard = card();
        searchCard.addView(cardTitle("종목 검색", "TradingView 심볼을 검색해 바로 추가합니다."));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("AAPL, BTCUSDT, GOLD, USDJPY…");
        searchInput.setHintTextColor(Color.rgb(105, 125, 151));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTextSize(16);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setPadding(dp(14), 0, dp(10), 0);
        searchInput.setBackground(roundRect(Color.rgb(12, 23, 39), Color.rgb(49, 68, 94), 14));
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(54), 1f));

        Button clear = button("×", false);
        clear.setTextSize(20);
        clear.setContentDescription("검색어 지우기");
        clear.setOnClickListener(v -> {
            searchInput.setText("");
            searchInput.requestFocus();
        });
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(dp(50), dp(50));
        clearLp.leftMargin = dp(8);
        searchRow.addView(clear, clearLp);
        searchCard.addView(searchRow);

        searchStatus = text("검색어를 입력하면 TradingView 종목을 찾습니다.", 12, false);
        searchStatus.setTextColor(MUTED);
        searchStatus.setPadding(dp(2), dp(9), 0, dp(5));
        searchCard.addView(searchStatus);

        searchResults = new LinearLayout(this);
        searchResults.setOrientation(LinearLayout.VERTICAL);
        searchCard.addView(searchResults);
        root.addView(searchCard, cardLp());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable e) { scheduleSearch(e.toString()); }
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                scheduleSearch(searchInput.getText().toString());
                return true;
            }
            return false;
        });

        LinearLayout selectedCard = card();
        selectedCard.addView(cardTitle("플로팅 종목", "≡ 를 길게 눌러 순서를 바꾸고, 대표 종목을 고르세요."));
        selectedSummary = text("", 12, false);
        selectedSummary.setTextColor(MUTED);
        selectedSummary.setPadding(0, 0, 0, dp(8));
        selectedCard.addView(selectedSummary);
        symbolList = new LinearLayout(this);
        symbolList.setOrientation(LinearLayout.VERTICAL);
        selectedCard.addView(symbolList);
        root.addView(selectedCard, cardLp());
        refreshSymbols();

        LinearLayout displayCard = card();
        displayCard.addView(cardTitle("플로팅 모양", "설정은 실행 중인 플로팅에도 즉시 반영됩니다."));

        previewMeta = text("미리보기", 11, false);
        previewMeta.setTextColor(MUTED);
        displayCard.addView(previewMeta);
        previewBox = new LinearLayout(this);
        previewBox.setOrientation(LinearLayout.VERTICAL);
        previewBox.setPadding(dp(12), dp(10), dp(12), dp(10));
        previewBox.setBackground(roundRect(Color.rgb(9, 18, 31), Color.rgb(43, 78, 118), 14));
        previewText = text("", 13, true);
        previewBox.addView(previewText);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(dp(280), LinearLayout.LayoutParams.WRAP_CONTENT);
        previewLp.topMargin = dp(6);
        previewLp.bottomMargin = dp(16);
        displayCard.addView(previewBox, previewLp);

        alphaValue = settingLabel(displayCard, "투명도");
        SeekBar alpha = new SeekBar(this);
        alpha.setMax(80);
        alpha.setProgress(Math.round(AppPrefs.getAlpha(this) * 100f) - 20);
        alpha.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                AppPrefs.setAlpha(MainActivity.this, (20 + progress) / 100f);
                updatePreview();
                broadcastConfig(false);
            }
        });
        displayCard.addView(alpha, lpMatch(dp(42)));

        fontValue = settingLabel(displayCard, "글자 크기");
        SeekBar font = new SeekBar(this);
        font.setMax(8);
        font.setProgress(AppPrefs.getTextSize(this) - 11);
        font.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                AppPrefs.setTextSize(MainActivity.this, 11 + progress);
                updatePreview();
                broadcastConfig(false);
            }
        });
        displayCard.addView(font, lpMatch(dp(42)));

        widthValue = settingLabel(displayCard, "플로팅 폭");
        SeekBar width = new SeekBar(this);
        width.setMax(140);
        width.setProgress(AppPrefs.getPanelWidth(this) - 220);
        width.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                AppPrefs.setPanelWidth(MainActivity.this, 220 + progress);
                updatePreview();
                broadcastConfig(false);
            }
        });
        displayCard.addView(width, lpMatch(dp(42)));

        TextView speedTitle = settingLabel(displayCard, "화면 갱신");
        speedTitle.setPadding(0, dp(6), 0, dp(8));
        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        speed500 = speedButton("0.5초", 500L);
        speed1000 = speedButton("1초 · 추천", 1000L);
        speed3000 = speedButton("3초 · 절전", 3000L);
        speedRow.addView(speed500, speedLp());
        speedRow.addView(speed1000, speedLp());
        speedRow.addView(speed3000, speedLp());
        displayCard.addView(speedRow, lpMatch(dp(48)));
        updateSpeedButtons();
        updatePreview();
        root.addView(displayCard, cardLp());

        LinearLayout infoCard = card();
        TextView infoTitle = text("데이터 안내", 14, true);
        infoCard.addView(infoTitle);
        TextView note = text("TradingView 심볼 검색과 공개 시세 세션을 사용합니다. 로그인하지 않은 세션은 거래소 정책에 따라 지연되거나 일부 유료 데이터가 제한될 수 있습니다. 화면이 꺼지면 연결을 멈춰 배터리를 절약합니다.", 12, false);
        note.setTextColor(MUTED);
        note.setPadding(0, dp(7), 0, 0);
        infoCard.addView(note);
        root.addView(infoCard, cardLp());

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(12), dp(10), dp(12), dp(12));
        bottom.setBackground(roundRect(Color.rgb(10, 20, 34), Color.rgb(36, 57, 82), 0));

        Button stop = button("종료", false);
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            Toast.makeText(this, "Wantview 플로팅을 종료했습니다.", Toast.LENGTH_SHORT).show();
        });
        bottom.addView(stop, new LinearLayout.LayoutParams(0, dp(54), 0.72f));

        Button start = button("적용하고 플로팅 시작", true);
        start.setTextSize(14);
        start.setOnClickListener(v -> startOverlay());
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(0, dp(54), 1.72f);
        startLp.leftMargin = dp(9);
        bottom.addView(start, startLp);
        screen.addView(bottom, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(screen);
        updatePermissionState();
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
        searchHandler.postDelayed(pendingSearch, 350L);
    }

    private void renderSearchResults(List<SymbolSearchClient.Item> items) {
        searchResults.removeAllViews();
        if (items == null || items.isEmpty()) {
            searchStatus.setText("검색 결과가 없습니다.");
            return;
        }
        int max = Math.min(12, items.size());
        searchStatus.setText("검색 결과 " + items.size() + "개 · 상위 " + max + "개 표시");
        List<String> selected = AppPrefs.getSymbols(this);
        for (int i = 0; i < max; i++) {
            SymbolSearchClient.Item item = items.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(11), dp(9), dp(7), dp(9));
            row.setBackground(roundRect(CARD_2, Color.rgb(40, 57, 80), 12));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView top = text(item.symbol + "  ·  " + item.exchange, 15, true);
            info.addView(top);
            String detail = item.description == null ? "" : item.description;
            if (item.type != null && !item.type.isEmpty()) detail += (detail.isEmpty() ? "" : "  ·  ") + item.type;
            TextView desc = text(detail, 11, false);
            desc.setTextColor(MUTED);
            desc.setMaxLines(2);
            info.addView(desc);
            row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            boolean exists = selected.contains(item.fullName);
            Button add = button(exists ? "추가됨" : "+ 추가", true);
            add.setEnabled(!exists);
            add.setAlpha(exists ? 0.45f : 1f);
            add.setOnClickListener(v -> {
                List<String> now = AppPrefs.getSymbols(this);
                if (!now.contains(item.fullName)) now.add(item.fullName);
                AppPrefs.setSymbols(this, now);
                refreshSymbols();
                updatePreview();
                broadcastConfig(true);
                add.setText("추가됨");
                add.setEnabled(false);
                add.setAlpha(0.45f);
            });
            row.addView(add, new LinearLayout.LayoutParams(dp(82), dp(44)));
            LinearLayout.LayoutParams rowLp = lpMatchWrap();
            rowLp.bottomMargin = dp(7);
            searchResults.addView(row, rowLp);
        }
    }

    private void refreshSymbols() {
        if (symbolList == null) return;
        symbolList.removeAllViews();
        List<String> list = AppPrefs.getSymbols(this);
        String representative = AppPrefs.getCollapsedSymbol(this);
        selectedSummary.setText(list.size() + "개 표시 · 대표: " + (representative.isEmpty() ? "없음" : symbolPart(representative)));

        if (list.isEmpty()) {
            TextView empty = text("표시할 종목이 없습니다. 위 검색에서 추가하세요.", 13, false);
            empty.setTextColor(MUTED);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            symbolList.addView(empty, lpMatch(dp(56)));
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            final String s = list.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(7), dp(7), dp(6), dp(7));
            row.setBackground(roundRect(CARD_2, Color.rgb(40, 57, 80), 12));

            TextView drag = text("≡", 23, true);
            drag.setGravity(Gravity.CENTER);
            drag.setTextColor(Color.rgb(119, 205, 255));
            drag.setContentDescription("길게 눌러 순서 변경");
            drag.setOnLongClickListener(v -> {
                dragFrom = index;
                ClipData data = ClipData.newPlainText("wantview-symbol", s);
                v.startDragAndDrop(data, new View.DragShadowBuilder(row), null, 0);
                return true;
            });
            row.addView(drag, new LinearLayout.LayoutParams(dp(42), dp(50)));

            LinearLayout names = new LinearLayout(this);
            names.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(symbolPart(s), 15, true);
            names.addView(name);
            TextView full = text(s, 10, false);
            full.setTextColor(MUTED);
            full.setSingleLine(true);
            names.addView(full);
            row.addView(names, new LinearLayout.LayoutParams(0, dp(50), 1f));

            boolean isRep = s.equals(representative);
            Button rep = button(isRep ? "대표 ✓" : "대표", isRep);
            rep.setTextSize(11);
            rep.setOnClickListener(v -> {
                AppPrefs.setCollapsedSymbol(this, s);
                refreshSymbols();
                updatePreview();
                broadcastConfig(false);
            });
            row.addView(rep, new LinearLayout.LayoutParams(dp(68), dp(42)));

            Button remove = button("×", false);
            remove.setTextSize(19);
            remove.setContentDescription(s + " 삭제");
            remove.setOnClickListener(v -> {
                List<String> now = AppPrefs.getSymbols(this);
                now.remove(s);
                AppPrefs.setSymbols(this, now);
                refreshSymbols();
                updatePreview();
                broadcastConfig(true);
            });
            LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(dp(48), dp(42));
            removeLp.leftMargin = dp(5);
            row.addView(remove, removeLp);

            row.setOnDragListener((v, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return event.getClipDescription() != null;
                    case DragEvent.ACTION_DRAG_ENTERED:
                        row.setBackground(roundRect(Color.rgb(22, 55, 82), ACCENT, 12));
                        return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                        row.setBackground(roundRect(CARD_2, Color.rgb(40, 57, 80), 12));
                        return true;
                    case DragEvent.ACTION_DROP:
                        if (dragFrom >= 0) {
                            List<String> now = AppPrefs.getSymbols(this);
                            if (dragFrom < now.size()) {
                                String moved = now.remove(dragFrom);
                                int target = Math.max(0, Math.min(index, now.size()));
                                now.add(target, moved);
                                AppPrefs.setSymbols(this, now);
                                dragFrom = -1;
                                refreshSymbols();
                                updatePreview();
                                broadcastConfig(false);
                            }
                        }
                        return true;
                    case DragEvent.ACTION_DRAG_ENDED:
                        row.setBackground(roundRect(CARD_2, Color.rgb(40, 57, 80), 12));
                        return true;
                }
                return true;
            });

            LinearLayout.LayoutParams rowLp = lpMatch(dp(66));
            rowLp.bottomMargin = dp(7);
            symbolList.addView(row, rowLp);
        }
    }

    private void updatePreview() {
        if (previewText == null || previewBox == null) return;
        int textSize = AppPrefs.getTextSize(this);
        int width = AppPrefs.getPanelWidth(this);
        float alpha = AppPrefs.getAlpha(this);
        String rep = AppPrefs.getCollapsedSymbol(this);
        List<String> symbols = AppPrefs.getSymbols(this);

        String first = rep.isEmpty() ? "BTCUSDT" : symbolPart(rep);
        String second = symbols.size() > 1 ? symbolPart(symbols.get(1)) : "ETHUSDT";
        previewText.setText(first + "   105,432.1   +1.24%\n" + second + "   3,872.45   -0.31%");
        previewText.setTextSize(textSize);
        previewText.setLineSpacing(0f, 1.25f);
        previewBox.setAlpha(alpha);
        int maxWidth = getResources().getDisplayMetrics().widthPixels - dp(72);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) previewBox.getLayoutParams();
        if (lp != null) {
            lp.width = Math.min(dp(width), maxWidth);
            previewBox.setLayoutParams(lp);
        }
        previewMeta.setText("대표 " + first + " · " + width + "dp · " + textSize + "sp");
        alphaValue.setText("투명도   " + Math.round(alpha * 100) + "%");
        fontValue.setText("글자 크기   " + textSize + "sp");
        widthValue.setText("플로팅 폭   " + width + "dp");
    }

    private Button speedButton(String label, long interval) {
        Button b = button(label, AppPrefs.getInterval(this) == interval);
        b.setTextSize(11);
        b.setOnClickListener(v -> {
            AppPrefs.setInterval(this, interval);
            updateSpeedButtons();
            broadcastConfig(false);
        });
        return b;
    }

    private LinearLayout.LayoutParams speedLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private void updateSpeedButtons() {
        long current = AppPrefs.getInterval(this);
        styleSpeedButton(speed500, current == 500L);
        styleSpeedButton(speed1000, current == 1000L);
        styleSpeedButton(speed3000, current == 3000L);
    }

    private void styleSpeedButton(Button b, boolean selected) {
        if (b == null) return;
        b.setBackground(roundRect(selected ? Color.rgb(19, 117, 237) : CARD_2,
                selected ? ACCENT : Color.rgb(50, 67, 91), 11));
        b.setTextColor(Color.WHITE);
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlayPermissionIfNeeded();
            Toast.makeText(this, "'다른 앱 위에 표시'를 허용한 뒤 다시 눌러주세요.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent service = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
        Toast.makeText(this, "Wantview 플로팅을 시작했습니다.", Toast.LENGTH_SHORT).show();
    }

    private void broadcastConfig(boolean reconnect) {
        Intent i = new Intent(OverlayService.ACTION_CONFIG_CHANGED);
        i.setPackage(getPackageName());
        i.putExtra(OverlayService.EXTRA_RECONNECT, reconnect);
        sendBroadcast(i);
    }

    private void openOverlayPermissionIfNeeded() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void updatePermissionState() {
        if (permissionState == null) return;
        boolean granted = Settings.canDrawOverlays(this);
        permissionState.setText(granted ? "플로팅 권한 ✓" : "권한 설정 필요");
        permissionState.setTextColor(granted ? GREEN : Color.rgb(255, 189, 93));
        permissionState.setBackground(roundRect(
                granted ? Color.rgb(16, 57, 46) : Color.rgb(58, 43, 25),
                granted ? Color.rgb(44, 112, 85) : Color.rgb(124, 87, 42), 20));
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setBackground(roundRect(CARD, Color.rgb(37, 55, 79), 16));
        return c;
    }

    private LinearLayout cardTitle(String title, String desc) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title, 17, true);
        t.setTextColor(Color.rgb(126, 211, 255));
        box.addView(t);
        TextView d = text(desc, 12, false);
        d.setTextColor(MUTED);
        d.setPadding(0, dp(3), 0, dp(11));
        box.addView(d);
        return box;
    }

    private TextView settingLabel(LinearLayout parent, String title) {
        TextView t = text(title, 13, true);
        t.setTextColor(Color.rgb(211, 222, 236));
        t.setPadding(0, dp(6), 0, 0);
        parent.addView(t, lpMatch(dp(30)));
        return t;
    }

    private TextView pill(String label, boolean primary) {
        TextView t = text(label, 11, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(roundRect(primary ? Color.rgb(19, 117, 237) : CARD_2,
                primary ? ACCENT : Color.rgb(55, 75, 101), 20));
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
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(7), 0, dp(7), 0);
        b.setBackground(roundRect(primary ? Color.rgb(19, 117, 237) : CARD_2,
                primary ? ACCENT : Color.rgb(56, 73, 98), 12));
        return b;
    }

    private GradientDrawable roundRect(int color, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(stroke == Color.TRANSPARENT ? 0 : dp(1), stroke);
        return d;
    }

    private String symbolPart(String full) {
        if (full == null) return "";
        int p = full.indexOf(':');
        String s = p >= 0 && p + 1 < full.length() ? full.substring(p + 1) : full;
        return s.length() > 18 ? s.substring(0, 18) : s;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        return lp;
    }

    private LinearLayout.LayoutParams lpMatch(int h) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h);
    }

    private LinearLayout.LayoutParams lpMatchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        searchHandler.removeCallbacksAndMessages(null);
        if (searchClient != null) searchClient.cancel();
        super.onDestroy();
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) { }
        @Override public void onStopTrackingTouch(SeekBar seekBar) { }
    }
}

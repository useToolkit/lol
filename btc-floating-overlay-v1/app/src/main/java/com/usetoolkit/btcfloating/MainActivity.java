package com.usetoolkit.btcfloating;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout symbolList;
    private final long[] intervals = {3000L, 1000L, 500L};

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestNotificationPermission();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(root);

        TextView title = text("BTC Floating Price", 26, true);
        root.addView(title);
        TextView sub = text("TradingView의 BINANCE 심볼 기준 · Binance WebSocket", 13, false);
        sub.setTextColor(Color.DKGRAY);
        root.addView(sub);

        Button overlay = new Button(this);
        overlay.setText("플로팅 가격창 시작 / 다시 시작");
        overlay.setOnClickListener(v -> startOverlay());
        root.addView(overlay, lpMatch(dp(56)));

        Button stop = new Button(this);
        stop.setText("플로팅 가격창 종료");
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        root.addView(stop, lpMatch(dp(52)));

        root.addView(section("종목"));
        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText input = new EditText(this);
        input.setHint("예: BTCUSDT");
        input.setSingleLine(true);
        addRow.addView(input, new LinearLayout.LayoutParams(0, dp(52), 1f));
        Button add = new Button(this);
        add.setText("추가");
        add.setOnClickListener(v -> {
            String s = input.getText().toString().trim().toUpperCase(Locale.US);
            if (!s.matches("[A-Z0-9]{5,20}")) {
                Toast.makeText(this, "BINANCE 심볼 형식으로 입력하세요. 예: BTCUSDT", Toast.LENGTH_SHORT).show();
                return;
            }
            List<String> list = AppPrefs.getSymbols(this);
            if (!list.contains(s)) list.add(s);
            AppPrefs.setSymbols(this, list);
            input.setText("");
            refreshSymbols();
        });
        addRow.addView(add, new LinearLayout.LayoutParams(dp(86), dp(52)));
        root.addView(addRow);

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
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        long current = AppPrefs.getInterval(this);
        spinner.setSelection(current == 3000 ? 0 : current == 500 ? 2 : 1);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                AppPrefs.setInterval(MainActivity.this, intervals[position]);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        root.addView(spinner, lpMatch(dp(52)));

        TextView note = text("※ 현재 V1은 BINANCE:BTCUSDT 같은 Binance 현물 심볼을 지원합니다. 가격은 TradingView 화면을 긁는 방식이 아니라 동일 원천 거래소의 실시간 WebSocket을 사용합니다.", 12, false);
        note.setPadding(0, dp(20), 0, 0);
        note.setTextColor(Color.GRAY);
        root.addView(note);

        setContentView(scroll);
    }

    private void refreshSymbols() {
        if (symbolList == null) return;
        symbolList.removeAllViews();
        List<String> list = AppPrefs.getSymbols(this);
        for (String s : list) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(s, 17, true);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(48), 1f));
            Button remove = new Button(this);
            remove.setText("삭제");
            remove.setOnClickListener(v -> {
                List<String> now = AppPrefs.getSymbols(this);
                now.remove(s);
                AppPrefs.setSymbols(this, now);
                refreshSymbols();
            });
            row.addView(remove, new LinearLayout.LayoutParams(dp(78), dp(46)));
            symbolList.addView(row);
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
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private TextView section(String s) {
        TextView t = text(s, 18, true);
        t.setPadding(0, dp(22), 0, dp(8));
        return t;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.BLACK);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lpMatch(int h) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

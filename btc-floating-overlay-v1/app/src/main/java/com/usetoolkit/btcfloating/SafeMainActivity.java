package com.usetoolkit.btcfloating;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SafeMainActivity extends MainActivity {
    private static final int CARD = Color.rgb(17, 28, 45);
    private static final int CARD_2 = Color.rgb(22, 36, 57);
    private static final int MUTED = Color.rgb(151, 166, 188);
    private static final int ACCENT = Color.rgb(45, 171, 255);

    private Button realtimeButton;
    private Button balancedButton;
    private Button ultraButton;
    private Button mobileSaverButton;
    private TextView networkState;
    private TextView powerSummary;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySafeInsets();
        installPowerControls();
    }

    @Override protected void onResume() {
        super.onResume();
        updatePowerUi();
    }

    private void applySafeInsets() {
        View content = findViewById(android.R.id.content);
        if (content == null) return;
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight();
            int bottom = insets.getSystemWindowInsetBottom();
            v.setPadding(left, top, right, bottom);
            return insets;
        });
        content.requestApplyInsets();
    }

    private void installPowerControls() {
        LinearLayout root = findScrollRoot();
        if (root == null) return;
        removeLegacyRefreshControls(root);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(roundRect(CARD, Color.rgb(37, 55, 79), 16));

        TextView title = text("배터리 / 시세 모드", 17, true);
        title.setTextColor(Color.rgb(126, 211, 255));
        card.addView(title);

        TextView desc = text("시세 속도와 배터리 사용량을 상황에 맞게 선택합니다.", 12, false);
        desc.setTextColor(MUTED);
        desc.setPadding(0, dp(3), 0, dp(9));
        card.addView(desc);

        networkState = text("", 12, false);
        networkState.setTextColor(MUTED);
        networkState.setPadding(0, 0, 0, dp(9));
        card.addView(networkState);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        realtimeButton = modeButton("실시간", AppPrefs.POWER_REALTIME);
        balancedButton = modeButton("균형 · 추천", AppPrefs.POWER_BALANCED);
        ultraButton = modeButton("초절전", AppPrefs.POWER_ULTRA);
        row.addView(realtimeButton, modeLp());
        row.addView(balancedButton, modeLp());
        row.addView(ultraButton, modeLp());
        card.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        mobileSaverButton = button("", false);
        mobileSaverButton.setTextSize(12);
        mobileSaverButton.setOnClickListener(v -> {
            AppPrefs.setMobileSaver(this, !AppPrefs.getMobileSaver(this));
            updatePowerUi();
            broadcastPowerChange();
        });
        LinearLayout.LayoutParams saverLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        saverLp.topMargin = dp(9);
        card.addView(mobileSaverButton, saverLp);

        powerSummary = text("", 12, false);
        powerSummary.setTextColor(MUTED);
        powerSummary.setPadding(dp(2), dp(9), dp(2), 0);
        card.addView(powerSummary);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(12);
        int insertAt = Math.max(0, root.getChildCount() - 1);
        root.addView(card, insertAt, cardLp);
        updatePowerUi();
    }

    private LinearLayout findScrollRoot() {
        View content = findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return null;
        ViewGroup contentGroup = (ViewGroup) content;
        if (contentGroup.getChildCount() == 0) return null;
        View screen = contentGroup.getChildAt(0);
        if (!(screen instanceof ViewGroup)) return null;
        ViewGroup screenGroup = (ViewGroup) screen;
        for (int i = 0; i < screenGroup.getChildCount(); i++) {
            View child = screenGroup.getChildAt(i);
            if (child instanceof ScrollView) {
                ScrollView scroll = (ScrollView) child;
                if (scroll.getChildCount() > 0 && scroll.getChildAt(0) instanceof LinearLayout) {
                    return (LinearLayout) scroll.getChildAt(0);
                }
            }
        }
        return null;
    }

    private void removeLegacyRefreshControls(View root) {
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView && "화면 갱신".contentEquals(((TextView) child).getText())) {
                group.removeViewAt(i);
                if (i < group.getChildCount()) group.removeViewAt(i);
                return;
            }
            removeLegacyRefreshControls(child);
        }
    }

    private Button modeButton(String label, int mode) {
        Button b = button(label, AppPrefs.getPowerMode(this) == mode);
        b.setTextSize(11);
        b.setOnClickListener(v -> {
            AppPrefs.setPowerMode(this, mode);
            updatePowerUi();
            broadcastPowerChange();
        });
        return b;
    }

    private LinearLayout.LayoutParams modeLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private void updatePowerUi() {
        if (realtimeButton == null || balancedButton == null || ultraButton == null) return;
        int mode = AppPrefs.getPowerMode(this);
        styleMode(realtimeButton, mode == AppPrefs.POWER_REALTIME);
        styleMode(balancedButton, mode == AppPrefs.POWER_BALANCED);
        styleMode(ultraButton, mode == AppPrefs.POWER_ULTRA);

        boolean mobileSaver = AppPrefs.getMobileSaver(this);
        if (mobileSaverButton != null) {
            mobileSaverButton.setText("모바일 데이터 자동 절전  " + (mobileSaver ? "ON" : "OFF"));
            mobileSaverButton.setBackground(roundRect(
                    mobileSaver ? Color.rgb(16, 57, 46) : CARD_2,
                    mobileSaver ? Color.rgb(44, 112, 85) : Color.rgb(56, 73, 98), 11));
        }

        String network = NetworkUtil.label(this);
        boolean cellular = NetworkUtil.isCellular(this);
        if (networkState != null) networkState.setText("현재 네트워크  ·  " + network);
        if (powerSummary == null) return;

        if (mode == AppPrefs.POWER_REALTIME) {
            powerSummary.setText("0.5초 화면 갱신 · WebSocket 상시 연결 · 가장 빠른 시세 반영");
        } else if (mode == AppPrefs.POWER_BALANCED) {
            if (cellular && mobileSaver) {
                powerSummary.setText("모바일 데이터: 3초 화면 갱신 · WebSocket 상시 연결 · UI 부하 절감");
            } else {
                powerSummary.setText("1초 화면 갱신 · WebSocket 상시 연결 · 속도와 배터리 균형");
            }
        } else {
            long sec = cellular && mobileSaver ? 45L : 20L;
            powerSummary.setText("약 " + sec + "초마다 4초만 시세 수신 · 대기 중에는 WebSocket 연결 해제");
        }
    }

    private void styleMode(Button b, boolean selected) {
        b.setBackground(roundRect(selected ? Color.rgb(19, 117, 237) : CARD_2,
                selected ? ACCENT : Color.rgb(50, 67, 91), 11));
        b.setTextColor(Color.WHITE);
    }

    private void broadcastPowerChange() {
        Intent i = new Intent(OverlayService.ACTION_CONFIG_CHANGED);
        i.setPackage(getPackageName());
        i.putExtra(OverlayService.EXTRA_RECONNECT, true);
        sendBroadcast(i);
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
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
        b.setGravity(Gravity.CENTER);
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

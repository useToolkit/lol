package com.usetoolkit.btcfloating;

import android.content.Intent;
import android.app.Notification;
import android.app.NotificationManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.service.notification.StatusBarNotification;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
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
    private Button chipEnabledButton;
    private Button chipOnlyButton;
    private TextView chipDiagnostics;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySafeInsets();
        installPowerControls();
        installChipControls();
    }

    @Override protected void onResume() {
        super.onResume();
        updatePowerUi();
        updateChipUi();
        updateChipDiagnostics();
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

    private void installChipControls() {
        LinearLayout root = findScrollRoot();
        if (root == null) return;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(roundRect(CARD, Color.rgb(37, 55, 79), 16));

        TextView title = text("상태표시줄 가격 칩 · 실험", 17, true);
        title.setTextColor(Color.rgb(126, 211, 255));
        card.addView(title);

        TextView note = text("갤럭시 S26 / Android 16에서 숫자 1개만 칩으로 요청합니다. 상태표시줄 노출 여부는 삼성 시스템이 결정하며, 금융 시세는 제한될 수 있습니다.", 12, false);
        note.setTextColor(MUTED);
        note.setPadding(0, dp(7), 0, dp(12));
        card.addView(note);

        chipEnabledButton = button("", true);
        chipEnabledButton.setOnClickListener(v -> {
            boolean next = !AppPrefs.getChipEnabled(this);
            AppPrefs.setChipEnabled(this, next);
            if (!next && AppPrefs.getChipOnly(this)) AppPrefs.setChipOnly(this, false);
            updateChipUi();
            broadcastChipChange();
        });
        card.addView(chipEnabledButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        chipOnlyButton = button("", false);
        chipOnlyButton.setOnClickListener(v -> {
            boolean next = !AppPrefs.getChipOnly(this);
            AppPrefs.setChipOnly(this, next);
            if (next) AppPrefs.setChipEnabled(this, true);
            updateChipUi();
            broadcastChipChange();
        });
        LinearLayout.LayoutParams onlyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        onlyLp.topMargin = dp(8);
        card.addView(chipOnlyButton, onlyLp);

        Button notificationSettings = button("갤럭시 알림 설정 열기", false);
        notificationSettings.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(intent);
            } catch (Exception ex) {
                android.widget.Toast.makeText(this, "설정 → 알림 → Wantview를 확인하세요.", android.widget.Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        settingsLp.topMargin = dp(8);
        card.addView(notificationSettings, settingsLp);

        Button promotedSettings = button("실시간 알림 칩 권한 설정 열기", false);
        promotedSettings.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT < 36) {
                android.widget.Toast.makeText(this, "Android 16 이상에서 지원되는 설정입니다.", android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            try {
                Intent intent = new Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS");
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(intent);
            } catch (Exception ex) {
                android.widget.Toast.makeText(this, "이 기기는 실시간 알림 별도 설정 화면을 제공하지 않습니다.", android.widget.Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams promoteLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        promoteLp.topMargin = dp(8);
        card.addView(promotedSettings, promoteLp);

        chipDiagnostics = text("", 12, false);
        chipDiagnostics.setTextColor(Color.rgb(205, 217, 229));
        chipDiagnostics.setPadding(0, dp(10), 0, dp(6));
        card.addView(chipDiagnostics);

        Button inspect = button("알림 상태 다시 검사", false);
        inspect.setOnClickListener(v -> updateChipDiagnostics());
        card.addView(inspect, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        TextView caution = text("상태표시줄 가격 칩은 시스템이 승인한 알림만 표시됩니다. 금융 시세는 칩 승격이 제한될 수 있으며 일반 알림창 시세는 별도로 확인 가능합니다.", 11, false);
        caution.setTextColor(MUTED);
        caution.setPadding(0, dp(10), 0, 0);
        card.addView(caution);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(12);
        root.addView(card, Math.min(2, root.getChildCount()), cardLp);
        updateChipUi();
        updateChipDiagnostics();
    }

    private void updateChipUi() {
        if (chipEnabledButton == null || chipOnlyButton == null) return;
        boolean enabled = AppPrefs.getChipEnabled(this);
        boolean only = AppPrefs.getChipOnly(this);
        chipEnabledButton.setText("상태표시줄 가격 칩 요청  " + (enabled ? "ON" : "OFF"));
        chipOnlyButton.setText("플로팅 숨기고 알림만 사용  " + (only ? "ON" : "OFF"));
        chipEnabledButton.setBackground(roundRect(enabled ? Color.rgb(16, 57, 46) : CARD_2,
                enabled ? Color.rgb(44, 112, 85) : Color.rgb(56, 73, 98), 11));
        chipOnlyButton.setBackground(roundRect(only ? Color.rgb(19, 117, 237) : CARD_2,
                only ? ACCENT : Color.rgb(56, 73, 98), 11));
    }

    private void updateChipDiagnostics() {
        if (chipDiagnostics == null) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean granted = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean notificationsEnabled = nm != null && nm.areNotificationsEnabled();
        String promotedAllowed = Build.VERSION.SDK_INT < 36 ? "미지원 (Android 16 필요)" : "확인 불가";
        if (Build.VERSION.SDK_INT >= 36 && nm != null) {
            try {
                Object value = nm.getClass().getMethod("canPostPromotedNotifications").invoke(nm);
                promotedAllowed = Boolean.TRUE.equals(value) ? "시스템 허용" : "차단 / 미허용";
            } catch (Exception ignored) { promotedAllowed = "기기에서 조회 불가"; }
        }
        boolean posted = false;
        String promotable = "알림 미게시";
        String promoted = "알림 미게시";
        if (nm != null) {
            try {
                for (StatusBarNotification sbn : nm.getActiveNotifications()) {
                    if (sbn.getId() == 102) {
                        posted = true;
                        Notification notification = sbn.getNotification();
                        promotable = "시스템 진단 불가";
                        if (Build.VERSION.SDK_INT >= 36) {
                            try {
                                Object result = Notification.class.getMethod("hasPromotableCharacteristics").invoke(notification);
                                promotable = Boolean.TRUE.equals(result) ? "조건 충족" : "조건 미충족";
                            } catch (Exception ignored) { }
                        }
                        promoted = "확인 불가";
                        try {
                            int flag = Notification.class.getField("FLAG_PROMOTED_ONGOING").getInt(null);
                            promoted = (notification.flags & flag) != 0 ? "승격됨" : "승격되지 않음";
                        } catch (Exception ignored) { }
                    }
                }
            } catch (Exception ignored) { }
        }
        chipDiagnostics.setText("Android API " + Build.VERSION.SDK_INT
                + "\n일반 알림 권한: " + (granted && notificationsEnabled ? "정상" : "차단됨")
                + "\n실시간 알림 권한: " + promotedAllowed
                + "\n가격 알림 게시: " + (posted ? "확인됨" : "확인 안 됨")
                + "\n승격 형식: " + promotable
                + "\n실제 승격 상태: " + promoted);
    }

    private void broadcastChipChange() {
        Intent i = new Intent(OverlayService.ACTION_CONFIG_CHANGED);
        i.setPackage(getPackageName());
        i.putExtra(OverlayService.EXTRA_RECONNECT, false);
        sendBroadcast(i);
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

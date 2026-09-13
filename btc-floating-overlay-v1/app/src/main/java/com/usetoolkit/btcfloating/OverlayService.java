package com.usetoolkit.btcfloating;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OverlayService extends Service implements MarketSocket.Listener {
    public static final String ACTION_CONFIG_CHANGED = "com.usetoolkit.btcfloating.CONFIG_CHANGED";
    public static final String EXTRA_RECONNECT = "reconnect";
    private static final String CHANNEL = "wantview_float_channel";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<String, Quote> quotes = new LinkedHashMap<>();

    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private LinearLayout panel, header, quoteBox;
    private TextView headerText, toggle, status;
    private GradientDrawable panelBackground;
    private MarketSocket market;
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback networkCallback;

    private boolean screenOn = true;
    private boolean collapsed;
    private boolean rendererRunning;
    private boolean ultraSleeping;
    private boolean ultraRenderPending;
    private int networkType = NetworkUtil.OFFLINE;

    private final Runnable renderer = new Runnable() {
        @Override public void run() {
            if (!rendererRunning || !screenOn || AppPrefs.getPowerMode(OverlayService.this) == AppPrefs.POWER_ULTRA) return;
            renderQuotes();
            ui.postDelayed(this, effectiveRenderInterval());
        }
    };

    private final Runnable ultraWake = new Runnable() {
        @Override public void run() {
            if (!screenOn || AppPrefs.getPowerMode(OverlayService.this) != AppPrefs.POWER_ULTRA) return;
            networkType = NetworkUtil.getType(OverlayService.this);
            if (networkType == NetworkUtil.OFFLINE) {
                ultraSleeping = true;
                setStatus("초절전 · 오프라인", Color.rgb(255, 184, 92));
                ui.postDelayed(this, 15000L);
                return;
            }
            ultraSleeping = false;
            setStatus("TradingView · 초절전 수신 중", Color.rgb(101, 218, 153));
            connectMarket();
            ui.removeCallbacks(ultraSleep);
            ui.postDelayed(ultraSleep, 4000L);
        }
    };

    private final Runnable ultraSleep = new Runnable() {
        @Override public void run() {
            if (AppPrefs.getPowerMode(OverlayService.this) != AppPrefs.POWER_ULTRA) return;
            renderQuotes();
            if (market != null) market.disconnect();
            ultraSleeping = true;
            long cycle = effectiveUltraCycle();
            setStatus("TradingView · 초절전 · " + (cycle / 1000L) + "초 간격", Color.rgb(156, 174, 198));
            ui.removeCallbacks(ultraWake);
            ui.postDelayed(ultraWake, Math.max(5000L, cycle - 4000L));
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                screenOn = false;
                stopRenderer();
                stopUltra();
                if (market != null) market.disconnect();
                setStatus("화면 꺼짐 · 완전 절전", Color.rgb(156, 174, 198));
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                screenOn = true;
                applyPowerPolicy();
            }
        }
    };

    private final BroadcastReceiver configReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            collapsed = AppPrefs.getCollapsed(OverlayService.this);
            applyDisplayPrefs();
            renderQuotes();
            if (intent != null && intent.getBooleanExtra(EXTRA_RECONNECT, false)) {
                quotes.clear();
                applyPowerPolicy();
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(101, buildNotification());

        IntentFilter screen = new IntentFilter();
        screen.addAction(Intent.ACTION_SCREEN_OFF);
        screen.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, screen);

        IntentFilter config = new IntentFilter(ACTION_CONFIG_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(configReceiver, config, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(configReceiver, config);

        market = new MarketSocket(this);
        collapsed = AppPrefs.getCollapsed(this);
        networkType = NetworkUtil.getType(this);
        registerNetworkCallback();
        createOverlay();
        applyPowerPolicy();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        collapsed = AppPrefs.getCollapsed(this);
        applyDisplayPrefs();
        applyPowerPolicy();
        return START_STICKY;
    }

    private void createOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setMinimumHeight(0);
        panelBackground = new GradientDrawable();
        panelBackground.setColor(Color.rgb(10, 18, 31));
        panelBackground.setStroke(dp(1), Color.rgb(43, 78, 118));
        panel.setBackground(panelBackground);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(0);

        headerText = new TextView(this);
        headerText.setTextColor(Color.WHITE);
        headerText.setTypeface(null, Typeface.BOLD);
        headerText.setSingleLine(true);
        headerText.setIncludeFontPadding(false);
        headerText.setGravity(Gravity.CENTER_VERTICAL);
        headerText.setMinHeight(0);
        headerText.setMinimumHeight(0);
        headerText.setPadding(dp(1), 0, dp(3), 0);
        header.addView(headerText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        toggle = new TextView(this);
        toggle.setGravity(Gravity.CENTER);
        toggle.setTextColor(Color.rgb(104, 205, 255));
        toggle.setTypeface(null, Typeface.BOLD);
        toggle.setIncludeFontPadding(false);
        toggle.setMinHeight(0);
        toggle.setMinimumHeight(0);
        toggle.setPadding(0, 0, 0, 0);
        toggle.setOnClickListener(v -> {
            collapsed = !collapsed;
            AppPrefs.setCollapsed(this, collapsed);
            applyDisplayPrefs();
            renderQuotes();
        });
        header.addView(toggle, new LinearLayout.LayoutParams(dp(24), LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        enableDrag(header);

        status = new TextView(this);
        status.setTextColor(Color.rgb(156, 174, 198));
        status.setSingleLine(true);
        status.setIncludeFontPadding(false);
        status.setMinHeight(0);
        status.setMinimumHeight(0);
        panel.addView(status);

        quoteBox = new LinearLayout(this);
        quoteBox.setOrientation(LinearLayout.VERTICAL);
        quoteBox.setMinimumHeight(0);
        panel.addView(quoteBox);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(10);
        params.y = dp(120);
        wm.addView(panel, params);
        applyDisplayPrefs();
    }

    private void applyDisplayPrefs() {
        if (panel == null) return;

        int textSize = AppPrefs.getTextSize(this);
        panel.setAlpha(AppPrefs.getAlpha(this));
        quoteBox.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        status.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        toggle.setText(collapsed ? "+" : "−");

        headerText.setTextSize(Math.max(11, textSize));
        toggle.setTextSize(collapsed ? Math.max(12, textSize + 1) : Math.max(16, textSize + 5));
        status.setTextSize(Math.max(8, textSize - 3));

        int headerHeight;
        int toggleWidth;
        if (collapsed) {
            // Ultra-slim ticker: actual glyph height + only 2dp total breathing room.
            headerHeight = Math.max(textHeightPx(Math.max(11, textSize), true), textHeightPx(Math.max(12, textSize + 1), true)) + dp(2);
            toggleWidth = Math.max(dp(18), measuredTextWidth("+", Math.max(12, textSize + 1), true) + dp(7));
            panel.setPadding(dp(5), 0, dp(4), 0);
            panelBackground.setCornerRadius(headerHeight / 2f);
        } else {
            headerHeight = dp(Math.max(27, textSize + 15));
            toggleWidth = dp(Math.max(28, textSize + 18));
            panel.setPadding(dp(8), dp(4), dp(8), dp(5));
            panelBackground.setCornerRadius(dp(13));
        }

        LinearLayout.LayoutParams hp = (LinearLayout.LayoutParams) header.getLayoutParams();
        hp.height = headerHeight;
        header.setLayoutParams(hp);

        LinearLayout.LayoutParams tp = (LinearLayout.LayoutParams) toggle.getLayoutParams();
        tp.width = toggleWidth;
        tp.height = headerHeight;
        toggle.setLayoutParams(tp);

        LinearLayout.LayoutParams htp = (LinearLayout.LayoutParams) headerText.getLayoutParams();
        htp.height = headerHeight;
        headerText.setLayoutParams(htp);

        resizeWindow(headerHeight);
    }

    private int textHeightPx(int sp, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(sp(sp));
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        Paint.FontMetricsInt fm = p.getFontMetricsInt();
        return Math.max(1, fm.descent - fm.ascent);
    }

    private int measuredTextWidth(String text, int sp, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(sp(sp));
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return (int) Math.ceil(p.measureText(text == null ? "" : text));
    }

    private void enableDrag(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float downX, downY;
            boolean moved;

            @Override public boolean onTouch(View v, MotionEvent e) {
                if (params == null) return false;
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = params.x;
                        startY = params.y;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (Math.abs(dx) > dp(3) || Math.abs(dy) > dp(3)) moved = true;
                        params.x = startX - (int) dx;
                        params.y = Math.max(0, startY + (int) dy);
                        try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return moved;
                }
                return false;
            }
        });
    }

    private void registerNetworkCallback() {
        try {
            cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return;
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) { networkChanged(); }
                @Override public void onLost(Network network) { networkChanged(); }
                @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) { networkChanged(); }
            };
            cm.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception ignored) { }
    }

    private void networkChanged() {
        ui.post(() -> {
            int next = NetworkUtil.getType(this);
            if (next == networkType) return;
            networkType = next;
            if (screenOn) applyPowerPolicy();
        });
    }

    private void applyPowerPolicy() {
        networkType = NetworkUtil.getType(this);
        stopRenderer();
        stopUltra();
        if (market != null) market.disconnect();

        if (!screenOn) {
            setStatus("화면 꺼짐 · 완전 절전", Color.rgb(156, 174, 198));
            return;
        }
        if (networkType == NetworkUtil.OFFLINE) {
            setStatus("오프라인 · 연결 대기", Color.rgb(255, 184, 92));
            renderQuotes();
            return;
        }

        if (AppPrefs.getPowerMode(this) == AppPrefs.POWER_ULTRA) {
            startUltra();
        } else {
            connectMarket();
            startRenderer();
            updateModeStatus();
        }
    }

    private void startRenderer() {
        stopRenderer();
        rendererRunning = true;
        ui.post(renderer);
    }

    private void stopRenderer() {
        rendererRunning = false;
        ui.removeCallbacks(renderer);
    }

    private void startUltra() {
        stopUltra();
        ultraSleeping = false;
        ui.post(ultraWake);
    }

    private void stopUltra() {
        ui.removeCallbacks(ultraWake);
        ui.removeCallbacks(ultraSleep);
        ultraSleeping = false;
        ultraRenderPending = false;
    }

    private long effectiveRenderInterval() {
        if (AppPrefs.getPowerMode(this) == AppPrefs.POWER_REALTIME) return 500L;
        return networkType == NetworkUtil.CELLULAR && AppPrefs.getMobileSaver(this) ? 3000L : 1000L;
    }

    private long effectiveUltraCycle() {
        return networkType == NetworkUtil.CELLULAR && AppPrefs.getMobileSaver(this) ? 45000L : 20000L;
    }

    private void connectMarket() {
        if (!screenOn || market == null || networkType == NetworkUtil.OFFLINE) return;
        market.connect(AppPrefs.getSymbols(this));
    }

    @Override public void onPrice(String symbol, double price, double changePct) {
        synchronized (quotes) {
            quotes.put(symbol, new Quote(price, changePct));
        }
        if (AppPrefs.getPowerMode(this) == AppPrefs.POWER_ULTRA && screenOn && !ultraSleeping && !ultraRenderPending) {
            ultraRenderPending = true;
            ui.postDelayed(() -> {
                ultraRenderPending = false;
                if (screenOn && AppPrefs.getPowerMode(this) == AppPrefs.POWER_ULTRA) renderQuotes();
            }, 500L);
        }
    }

    @Override public void onState(String state) {
        ui.post(() -> {
            int mode = AppPrefs.getPowerMode(this);
            if (mode == AppPrefs.POWER_ULTRA && ultraSleeping && ("closed".equals(state) || "disconnected".equals(state))) return;
            if ("restricted".equals(state)) {
                setStatus("TradingView · 일부 데이터 제한", Color.rgb(255, 184, 92));
            } else if ("connected".equals(state)) {
                if (mode == AppPrefs.POWER_ULTRA) setStatus("TradingView · 초절전 수신 중", Color.rgb(101, 218, 153));
                else updateModeStatus();
            } else if ("connecting".equals(state)) {
                setStatus("TradingView · 연결 중…", Color.rgb(156, 174, 198));
            } else {
                setStatus("TradingView · " + state, Color.rgb(156, 174, 198));
            }
        });
    }

    private void updateModeStatus() {
        String net = NetworkUtil.label(this);
        if (AppPrefs.getPowerMode(this) == AppPrefs.POWER_REALTIME) {
            setStatus("TradingView · 실시간 · " + net, Color.rgb(101, 218, 153));
        } else {
            String extra = networkType == NetworkUtil.CELLULAR && AppPrefs.getMobileSaver(this) ? " · 모바일 절전" : "";
            setStatus("TradingView · 균형 · " + net + extra, Color.rgb(101, 218, 153));
        }
    }

    private void setStatus(String text, int color) {
        ui.post(() -> {
            if (status == null) return;
            status.setText(text);
            status.setTextColor(color);
            resizeWindow(currentHeaderHeight());
        });
    }

    private void renderQuotes() {
        if (panel == null || quoteBox == null) return;
        List<String> symbols = AppPrefs.getSymbols(this);

        if (collapsed) {
            renderCollapsed(symbols);
            resizeWindow(currentHeaderHeight());
            return;
        }

        int size = AppPrefs.getTextSize(this);
        headerText.setText("Wantview");
        headerText.setTextColor(Color.WHITE);
        quoteBox.removeAllViews();

        synchronized (quotes) {
            for (String symbol : symbols) {
                Quote q = quotes.get(symbol);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int h = dp(Math.max(22, size + 10));

                TextView name = new TextView(this);
                name.setText(prettySymbol(symbol));
                name.setTextColor(Color.WHITE);
                name.setTextSize(size);
                name.setIncludeFontPadding(false);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                name.setGravity(Gravity.CENTER_VERTICAL);
                name.setTypeface(null, Typeface.BOLD);
                row.addView(name, new LinearLayout.LayoutParams(0, h, 0.41f));

                TextView value = new TextView(this);
                value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                value.setTextSize(size);
                value.setIncludeFontPadding(false);
                value.setSingleLine(true);
                value.setTypeface(null, Typeface.BOLD);
                if (q == null) {
                    value.setText("--");
                    value.setTextColor(Color.rgb(172, 185, 204));
                } else {
                    value.setText(String.format(Locale.US, "%s  %+.2f%%", formatPrice(q.price), q.change));
                    value.setTextColor(q.change >= 0 ? Color.rgb(75, 220, 142) : Color.rgb(255, 101, 111));
                }
                row.addView(value, new LinearLayout.LayoutParams(0, h, 0.59f));
                quoteBox.addView(row);
            }
        }
        resizeWindow(currentHeaderHeight());
    }

    private void renderCollapsed(List<String> symbols) {
        int size = AppPrefs.getTextSize(this);
        headerText.setTextSize(Math.max(11, size));
        headerText.setSingleLine(true);

        if (symbols.isEmpty()) {
            headerText.setText("Wantview");
            headerText.setTextColor(Color.WHITE);
            return;
        }

        String rep = AppPrefs.getCollapsedSymbol(this);
        if (rep.isEmpty() || !symbols.contains(rep)) rep = symbols.get(0);
        Quote q;
        synchronized (quotes) {
            q = quotes.get(rep);
        }

        if (q == null) {
            headerText.setText(prettySymbol(rep) + "  --");
            headerText.setTextColor(Color.WHITE);
        } else {
            headerText.setText(String.format(Locale.US, "%s  %s  %+.2f%%", prettySymbol(rep), formatPrice(q.price), q.change));
            headerText.setTextColor(q.change >= 0 ? Color.rgb(75, 220, 142) : Color.rgb(255, 101, 111));
        }
    }

    private int currentHeaderHeight() {
        if (header == null || header.getLayoutParams() == null || header.getLayoutParams().height <= 0) {
            int size = AppPrefs.getTextSize(this);
            if (collapsed) {
                return Math.max(textHeightPx(Math.max(11, size), true), textHeightPx(Math.max(12, size + 1), true)) + dp(2);
            }
            return dp(Math.max(27, size + 15));
        }
        return header.getLayoutParams().height;
    }

    private void resizeWindow(int headerHeight) {
        if (params == null || wm == null || panel == null) return;

        int desired = desiredWidth();
        int screenMax = (int) (getResources().getDisplayMetrics().widthPixels * 0.96f);
        if (collapsed) {
            params.width = Math.min(screenMax, Math.max(dp(105), desired));
            // Explicit height removes all residual layout/font padding from the collapsed ticker.
            params.height = headerHeight;
        } else {
            params.width = Math.max(dp(145), Math.min(dp(AppPrefs.getPanelWidth(this)), desired));
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        }

        try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
    }

    private int desiredWidth() {
        int size = AppPrefs.getTextSize(this);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(sp(size));

        int toggleWidth = collapsed
                ? Math.max(dp(18), measuredTextWidth("+", Math.max(12, size + 1), true) + dp(7))
                : dp(Math.max(28, size + 18));

        float max = p.measureText(collapsed ? headerText.getText().toString() : "Wantview")
                + toggleWidth + dp(collapsed ? 10 : 18);

        if (!collapsed) {
            synchronized (quotes) {
                for (String symbol : AppPrefs.getSymbols(this)) {
                    Quote q = quotes.get(symbol);
                    String value = q == null ? "--" : String.format(Locale.US, "%s  %+.2f%%", formatPrice(q.price), q.change);
                    max = Math.max(max, p.measureText(prettySymbol(symbol) + "   " + value) + dp(20));
                }
            }
        }
        return (int) Math.ceil(max);
    }

    private String prettySymbol(String full) {
        int i = full.indexOf(':');
        String s = i >= 0 ? full.substring(i + 1) : full;
        return s.length() > 15 ? s.substring(0, 15) : s;
    }

    private String formatPrice(double p) {
        if (Math.abs(p) >= 1000) return new DecimalFormat("#,##0.##").format(p);
        if (Math.abs(p) >= 1) return new DecimalFormat("#,##0.####").format(p);
        return new DecimalFormat("0.########").format(p);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Wantview Floating", NotificationManager.IMPORTANCE_LOW);
            c.setSound(null, null);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    private Notification buildNotification() {
        Intent i = new Intent(this, SafeMainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setContentTitle("Wantview")
                .setContentText("플로팅 시세 표시 중")
                .setSmallIcon(R.drawable.ic_stat_wantview)
                .setContentIntent(pi)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    @Override public void onDestroy() {
        stopRenderer();
        stopUltra();
        ui.removeCallbacksAndMessages(null);
        if (market != null) market.disconnect();
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) { }
        try { unregisterReceiver(configReceiver); } catch (Exception ignored) { }
        try { if (cm != null && networkCallback != null) cm.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) { }
        if (wm != null && panel != null) {
            try { wm.removeView(panel); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private float sp(int v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }

    private static final class Quote {
        final double price;
        final double change;

        Quote(double price, double change) {
            this.price = price;
            this.change = change;
        }
    }
}

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
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
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
    private static final String CHANNEL = "wantview_float_channel";
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Quote> quotes = new LinkedHashMap<>();
    private WindowManager wm;
    private LinearLayout panel;
    private LinearLayout quoteBox;
    private TextView status;
    private TextView headerText;
    private TextView toggle;
    private WindowManager.LayoutParams params;
    private MarketSocket market;
    private boolean screenOn = true;
    private boolean collapsed;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                screenOn = false;
                if (market != null) market.disconnect();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                screenOn = true;
                connectMarket();
            }
        }
    };

    private final Runnable renderer = new Runnable() {
        @Override public void run() {
            renderQuotes();
            uiHandler.postDelayed(this, Math.max(500L, AppPrefs.getInterval(OverlayService.this)));
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(101, buildNotification());
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, filter);
        market = new MarketSocket(this);
        collapsed = AppPrefs.getCollapsed(this);
        createOverlay();
        connectMarket();
        uiHandler.post(renderer);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (panel != null) panel.setAlpha(AppPrefs.getAlpha(this));
        quotes.clear();
        connectMarket();
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
        panel.setPadding(dp(11), dp(7), dp(11), dp(9));
        panel.setAlpha(AppPrefs.getAlpha(this));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(10, 18, 31));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.rgb(43, 78, 118));
        panel.setBackground(bg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        headerText = new TextView(this);
        headerText.setText("Wantview");
        headerText.setTextColor(Color.WHITE);
        headerText.setTextSize(14);
        headerText.setTypeface(null, android.graphics.Typeface.BOLD);
        headerText.setPadding(dp(2), dp(2), dp(12), dp(2));
        header.addView(headerText, new LinearLayout.LayoutParams(0, dp(34), 1f));

        toggle = new TextView(this);
        toggle.setGravity(Gravity.CENTER);
        toggle.setTextColor(Color.rgb(104, 205, 255));
        toggle.setTextSize(21);
        toggle.setTypeface(null, android.graphics.Typeface.BOLD);
        toggle.setOnClickListener(v -> {
            collapsed = !collapsed;
            AppPrefs.setCollapsed(this, collapsed);
            applyCollapsed();
            renderQuotes();
        });
        header.addView(toggle, new LinearLayout.LayoutParams(dp(38), dp(34)));
        panel.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));
        enableDrag(header);

        status = new TextView(this);
        status.setText("TradingView · 연결 중…");
        status.setTextColor(Color.rgb(156, 174, 198));
        status.setTextSize(10);
        status.setPadding(dp(2), 0, dp(2), dp(4));
        panel.addView(status);

        quoteBox = new LinearLayout(this);
        quoteBox.setOrientation(LinearLayout.VERTICAL);
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
        applyCollapsed();
    }

    private void applyCollapsed() {
        if (quoteBox == null || status == null || toggle == null) return;
        quoteBox.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        status.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        toggle.setText(collapsed ? "+" : "−");
        if (!collapsed && headerText != null) {
            headerText.setText("Wantview");
            headerText.setTextColor(Color.WHITE);
        }
        if (wm != null && panel != null && params != null) {
            try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
        }
    }

    private void enableDrag(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float downX, downY;
            boolean moved;

            @Override public boolean onTouch(View v, MotionEvent e) {
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
                        params.y = startY + (int) dy;
                        if (wm != null && panel != null) wm.updateViewLayout(panel, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        return moved;
                }
                return false;
            }
        });
    }

    private void connectMarket() {
        if (!screenOn || market == null) return;
        market.connect(AppPrefs.getSymbols(this));
    }

    @Override public void onPrice(String symbol, double price, double changePct) {
        synchronized (quotes) {
            quotes.put(symbol, new Quote(price, changePct));
        }
    }

    @Override public void onState(String state) {
        uiHandler.post(() -> {
            if (status == null) return;
            if ("connected".equals(state)) {
                status.setText("TradingView · 연결됨");
                status.setTextColor(Color.rgb(101, 218, 153));
            } else if ("restricted".equals(state)) {
                status.setText("TradingView · 일부 데이터 제한");
                status.setTextColor(Color.rgb(255, 184, 92));
            } else {
                status.setText("TradingView · " + state);
                status.setTextColor(Color.rgb(156, 174, 198));
            }
        });
    }

    private void renderQuotes() {
        if (panel == null || quoteBox == null) return;
        panel.setAlpha(AppPrefs.getAlpha(this));
        List<String> symbols = AppPrefs.getSymbols(this);
        if (collapsed) {
            renderCollapsedHeader(symbols);
            return;
        }

        headerText.setText("Wantview");
        headerText.setTextColor(Color.WHITE);
        quoteBox.removeAllViews();
        synchronized (quotes) {
            for (String symbol : symbols) {
                Quote q = quotes.get(symbol);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(2), dp(3), dp(2), dp(3));

                TextView name = new TextView(this);
                name.setText(prettySymbol(symbol));
                name.setTextColor(Color.WHITE);
                name.setTextSize(13);
                name.setTypeface(null, android.graphics.Typeface.BOLD);
                row.addView(name, new LinearLayout.LayoutParams(dp(88), dp(28)));

                TextView value = new TextView(this);
                value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                value.setTextSize(13);
                value.setTypeface(null, android.graphics.Typeface.BOLD);
                if (q == null) {
                    value.setText("--");
                    value.setTextColor(Color.rgb(172, 185, 204));
                } else {
                    value.setText(String.format(Locale.US, "%s   %+.2f%%", formatPrice(q.price), q.change));
                    value.setTextColor(q.change >= 0 ? Color.rgb(75, 220, 142) : Color.rgb(255, 101, 111));
                }
                row.addView(value, new LinearLayout.LayoutParams(dp(142), dp(28)));
                quoteBox.addView(row);
            }
        }
        if (symbols.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("앱에서 종목을 추가하세요");
            empty.setTextColor(Color.LTGRAY);
            empty.setTextSize(12);
            empty.setPadding(dp(2), dp(4), dp(2), dp(4));
            quoteBox.addView(empty);
        }
    }

    private void renderCollapsedHeader(List<String> symbols) {
        if (headerText == null) return;
        if (symbols.isEmpty()) {
            headerText.setText("Wantview");
            headerText.setTextColor(Color.WHITE);
            return;
        }
        String first = symbols.get(0);
        Quote q;
        synchronized (quotes) { q = quotes.get(first); }
        if (q == null) {
            headerText.setText(prettySymbol(first) + "   --");
            headerText.setTextColor(Color.WHITE);
        } else {
            headerText.setText(String.format(Locale.US, "%s  %s  %+.2f%%",
                    prettySymbol(first), formatPrice(q.price), q.change));
            headerText.setTextColor(q.change >= 0 ? Color.rgb(75, 220, 142) : Color.rgb(255, 101, 111));
        }
    }

    private String prettySymbol(String full) {
        int p = full.indexOf(':');
        String s = p >= 0 && p + 1 < full.length() ? full.substring(p + 1) : full;
        if (s.length() > 12) s = s.substring(0, 12);
        return s;
    }

    private String formatPrice(double p) {
        if (Math.abs(p) >= 1000) return new DecimalFormat("#,##0.##").format(p);
        if (Math.abs(p) >= 1) return new DecimalFormat("#,##0.####").format(p);
        return new DecimalFormat("0.########").format(p);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Wantview Floating", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Wantview 플로팅 시세 표시 유지용");
            c.setSound(null, null);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    private Notification buildNotification() {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
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
        uiHandler.removeCallbacksAndMessages(null);
        if (market != null) market.disconnect();
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) { }
        if (wm != null && panel != null) {
            try { wm.removeView(panel); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
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

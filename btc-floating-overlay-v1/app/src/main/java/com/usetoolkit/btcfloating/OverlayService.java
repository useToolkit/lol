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
    private static final String CHANNEL = "btc_float_channel";
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Quote> quotes = new LinkedHashMap<>();
    private WindowManager wm;
    private LinearLayout panel;
    private TextView status;
    private WindowManager.LayoutParams params;
    private MarketSocket market;
    private boolean screenOn = true;

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
        panel.setPadding(dp(12), dp(9), dp(12), dp(9));
        panel.setAlpha(AppPrefs.getAlpha(this));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(24, 25, 28));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.rgb(65, 68, 74));
        panel.setBackground(bg);

        status = new TextView(this);
        status.setText("연결 중…");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(11);
        panel.addView(status);

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
        enableDrag(panel);
    }

    private void enableDrag(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float downX, downY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = params.x;
                        startY = params.y;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = startX - (int) (e.getRawX() - downX);
                        params.y = startY + (int) (e.getRawY() - downY);
                        if (wm != null && panel != null) wm.updateViewLayout(panel, params);
                        return true;
                }
                return false;
            }
        });
    }

    private void connectMarket() {
        if (!screenOn || market == null) return;
        List<String> symbols = AppPrefs.getSymbols(this);
        market.connect(symbols);
    }

    @Override public void onPrice(String symbol, double price, double changePct) {
        synchronized (quotes) {
            quotes.put(symbol, new Quote(price, changePct));
        }
    }

    @Override public void onState(String state) {
        uiHandler.post(() -> {
            if (status != null) {
                status.setText("connected".equals(state) ? "BINANCE · 실시간" : "BINANCE · " + state);
                status.setTextColor("connected".equals(state) ? Color.rgb(110, 220, 150) : Color.LTGRAY);
            }
        });
    }

    private void renderQuotes() {
        if (panel == null) return;
        panel.setAlpha(AppPrefs.getAlpha(this));
        while (panel.getChildCount() > 1) panel.removeViewAt(1);
        List<String> symbols = AppPrefs.getSymbols(this);
        synchronized (quotes) {
            for (String symbol : symbols) {
                Quote q = quotes.get(symbol);
                TextView row = new TextView(this);
                String base = prettySymbol(symbol);
                if (q == null) {
                    row.setText(base + "   --");
                    row.setTextColor(Color.WHITE);
                } else {
                    row.setText(String.format(Locale.US, "%s   %s   %+.2f%%", base, formatPrice(q.price), q.change));
                    row.setTextColor(q.change >= 0 ? Color.rgb(84, 214, 136) : Color.rgb(255, 107, 107));
                }
                row.setTextSize(15);
                row.setTypeface(null, android.graphics.Typeface.BOLD);
                row.setPadding(0, dp(3), 0, dp(3));
                panel.addView(row);
            }
        }
    }

    private String prettySymbol(String s) {
        if (s.endsWith("USDT")) return s.substring(0, s.length() - 4);
        return s;
    }

    private String formatPrice(double p) {
        if (p >= 1000) return new DecimalFormat("#,##0.##").format(p);
        if (p >= 1) return new DecimalFormat("#,##0.####").format(p);
        return new DecimalFormat("0.########").format(p);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Floating Price", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("실시간 플로팅 가격창 유지용");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    private Notification buildNotification() {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setContentTitle("BTC Floating Price")
                .setContentText("플로팅 시세 표시 중")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(pi)
                .setOngoing(true)
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

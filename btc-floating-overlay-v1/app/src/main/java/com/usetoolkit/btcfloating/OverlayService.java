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
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Quote> quotes = new LinkedHashMap<>();
    private WindowManager wm;
    private LinearLayout panel;
    private LinearLayout header;
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

    private final BroadcastReceiver configReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            collapsed = AppPrefs.getCollapsed(OverlayService.this);
            applyDisplayPrefs();
            renderQuotes();
            if (intent != null && intent.getBooleanExtra(EXTRA_RECONNECT, false)) {
                quotes.clear();
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

        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, screenFilter);

        IntentFilter configFilter = new IntentFilter(ACTION_CONFIG_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(configReceiver, configFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(configReceiver, configFilter);
        }

        market = new MarketSocket(this);
        collapsed = AppPrefs.getCollapsed(this);
        createOverlay();
        connectMarket();
        uiHandler.post(renderer);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        collapsed = AppPrefs.getCollapsed(this);
        applyDisplayPrefs();
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
        panel.setPadding(dp(8), dp(5), dp(8), dp(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(10, 18, 31));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.rgb(43, 78, 118));
        panel.setBackground(bg);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        headerText = new TextView(this);
        headerText.setText("Wantview");
        headerText.setTextColor(Color.WHITE);
        headerText.setTypeface(null, Typeface.BOLD);
        headerText.setSingleLine(true);
        headerText.setEllipsize(TextUtils.TruncateAt.END);
        headerText.setPadding(dp(1), 0, dp(5), 0);
        header.addView(headerText, new LinearLayout.LayoutParams(0, dp(30), 1f));

        toggle = new TextView(this);
        toggle.setGravity(Gravity.CENTER);
        toggle.setTextColor(Color.rgb(104, 205, 255));
        toggle.setTypeface(null, Typeface.BOLD);
        toggle.setContentDescription("플로팅 접기 또는 펼치기");
        toggle.setOnClickListener(v -> {
            collapsed = !collapsed;
            AppPrefs.setCollapsed(this, collapsed);
            applyCollapsed();
            renderQuotes();
        });
        header.addView(toggle, new LinearLayout.LayoutParams(dp(30), dp(30)));
        panel.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));
        enableDrag(header);

        status = new TextView(this);
        status.setText("TradingView · 연결 중…");
        status.setTextColor(Color.rgb(156, 174, 198));
        status.setPadding(dp(1), 0, dp(1), dp(2));
        status.setSingleLine(true);
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
        applyDisplayPrefs();
    }

    private void applyDisplayPrefs() {
        if (panel == null) return;
        int textSize = AppPrefs.getTextSize(this);
        panel.setAlpha(AppPrefs.getAlpha(this));

        int headerHeight = dp(Math.max(27, textSize + 16));
        int toggleWidth = dp(Math.max(28, textSize + 18));
        if (header != null) {
            LinearLayout.LayoutParams hp = (LinearLayout.LayoutParams) header.getLayoutParams();
            hp.height = headerHeight;
            header.setLayoutParams(hp);
        }
        if (headerText != null) {
            headerText.setTextSize(Math.max(11, textSize));
            LinearLayout.LayoutParams htp = (LinearLayout.LayoutParams) headerText.getLayoutParams();
            htp.height = headerHeight;
            headerText.setLayoutParams(htp);
        }
        if (toggle != null) {
            toggle.setTextSize(Math.max(17, textSize + 6));
            LinearLayout.LayoutParams tp = (LinearLayout.LayoutParams) toggle.getLayoutParams();
            tp.width = toggleWidth;
            tp.height = headerHeight;
            toggle.setLayoutParams(tp);
        }
        if (status != null) status.setTextSize(Math.max(8, textSize - 3));

        applyCollapsed();
        resizeWindow();
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
        resizeWindow();
    }

    private void enableDrag(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float downX, downY;
            boolean moved;

            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = params == null ? 0 : params.x;
                        startY = params == null ? 0 : params.y;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (params == null) return true;
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (Math.abs(dx) > dp(3) || Math.abs(dy) > dp(3)) moved = true;
                        params.x = startX - (int) dx;
                        params.y = Math.max(0, startY + (int) dy);
                        if (wm != null && panel != null) {
                            try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
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
            resizeWindow();
        });
    }

    private void renderQuotes() {
        if (panel == null || quoteBox == null) return;
        panel.setAlpha(AppPrefs.getAlpha(this));
        List<String> symbols = AppPrefs.getSymbols(this);
        if (collapsed) {
            renderCollapsedHeader(symbols);
            resizeWindow();
            return;
        }

        int textSize = AppPrefs.getTextSize(this);
        headerText.setText("Wantview");
        headerText.setTextSize(Math.max(11, textSize));
        headerText.setTextColor(Color.WHITE);
        quoteBox.removeAllViews();

        synchronized (quotes) {
            for (String symbol : symbols) {
                Quote q = quotes.get(symbol);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(1), dp(1), dp(1), dp(1));
                int rowHeight = dp(Math.max(23, textSize + 12));

                TextView name = new TextView(this);
                name.setText(prettySymbol(symbol));
                name.setTextColor(Color.WHITE);
                name.setTextSize(textSize);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                name.setTypeface(null, Typeface.BOLD);
                row.addView(name, new LinearLayout.LayoutParams(0, rowHeight, 0.41f));

                TextView value = new TextView(this);
                value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                value.setTextSize(textSize);
                value.setSingleLine(true);
                value.setTypeface(null, Typeface.BOLD);
                if (q == null) {
                    value.setText("--");
                    value.setTextColor(Color.rgb(172, 185, 204));
                } else {
                    value.setText(String.format(Locale.US, "%s  %+.2f%%", formatPrice(q.price), q.change));
                    value.setTextColor(q.change >= 0 ? Color.rgb(75, 220, 142) : Color.rgb(255, 101, 111));
                }
                row.addView(value, new LinearLayout.LayoutParams(0, rowHeight, 0.59f));
                quoteBox.addView(row);
            }
        }

        if (symbols.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("앱에서 종목을 추가하세요");
            empty.setTextColor(Color.LTGRAY);
            empty.setTextSize(textSize);
            empty.setPadding(dp(1), dp(3), dp(1), dp(3));
            quoteBox.addView(empty);
        }
        resizeWindow();
    }

    private void renderCollapsedHeader(List<String> symbols) {
        if (headerText == null) return;
        int textSize = AppPrefs.getTextSize(this);
        headerText.setTextSize(Math.max(11, textSize));
        if (symbols.isEmpty()) {
            headerText.setText("Wantview");
            headerText.setTextColor(Color.WHITE);
            return;
        }

        String representative = AppPrefs.getCollapsedSymbol(this);
        if (representative.isEmpty() || !symbols.contains(representative)) representative = symbols.get(0);
        Quote q;
        synchronized (quotes) { q = quotes.get(representative); }
        if (q == null) {
            headerText.setText(prettySymbol(representative) + "  --");
            headerText.setTextColor(Color.WHITE);
        } else {
            headerText.setText(String.format(Locale.US, "%s  %s  %+.2f%%",
                    prettySymbol(representative), formatPrice(q.price), q.change));
            headerText.setTextColor(q.change >= 0 ? Color.rgb(75, 220, 142) : Color.rgb(255, 101, 111));
        }
    }

    private void resizeWindow() {
        if (params == null || wm == null || panel == null) return;
        int desired = calculateDesiredWidth();
        int configuredMax = dp(AppPrefs.getPanelWidth(this));
        int minimum = dp(collapsed ? 120 : 145);
        params.width = Math.max(minimum, Math.min(configuredMax, desired));
        try { wm.updateViewLayout(panel, params); } catch (Exception ignored) { }
    }

    private int calculateDesiredWidth() {
        int textSize = AppPrefs.getTextSize(this);
        Paint main = new Paint(Paint.ANTI_ALIAS_FLAG);
        main.setTypeface(Typeface.DEFAULT_BOLD);
        main.setTextSize(sp(textSize));

        int toggleWidth = dp(Math.max(28, textSize + 18));
        float max = main.measureText(collapsed && headerText != null
                ? headerText.getText().toString() : "Wantview") + toggleWidth + dp(18);

        if (!collapsed) {
            Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
            small.setTypeface(Typeface.DEFAULT);
            small.setTextSize(sp(Math.max(8, textSize - 3)));
            if (status != null) max = Math.max(max, small.measureText(status.getText().toString()) + dp(18));

            List<String> symbols = AppPrefs.getSymbols(this);
            synchronized (quotes) {
                for (String symbol : symbols) {
                    Quote q = quotes.get(symbol);
                    String value = q == null ? "--" : String.format(Locale.US, "%s  %+.2f%%",
                            formatPrice(q.price), q.change);
                    String line = prettySymbol(symbol) + "    " + value;
                    max = Math.max(max, main.measureText(line) + dp(22));
                }
            }
            if (symbols.isEmpty()) {
                max = Math.max(max, main.measureText("앱에서 종목을 추가하세요") + dp(18));
            }
        }
        return (int) Math.ceil(max);
    }

    private String prettySymbol(String full) {
        int p = full.indexOf(':');
        String s = p >= 0 && p + 1 < full.length() ? full.substring(p + 1) : full;
        if (s.length() > 15) s = s.substring(0, 15);
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
        uiHandler.removeCallbacksAndMessages(null);
        if (market != null) market.disconnect();
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) { }
        try { unregisterReceiver(configReceiver); } catch (Exception ignored) { }
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

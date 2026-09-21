package com.usetoolkit.fuelpick;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 4101;
    private static final int GREEN = Color.rgb(0, 168, 104);
    private static final int BG = Color.rgb(246, 247, 248);
    private static final int TEXT = Color.rgb(30, 34, 38);
    private static final int SUB = Color.rgb(117, 124, 133);
    private static final String PREFS = "fuelpick_prefs";
    private static final String REMOTE_CONFIG_URL = "https://raw.githubusercontent.com/useToolkit/lol/fuelpick-app/fuelpick-config.json";

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final List<Station> nearby = new ArrayList<>();
    private final Map<String, Station> favoriteDetails = new HashMap<>();

    private SharedPreferences prefs;
    private LinearLayout root, content, favoritesBox, listBox, recommendedBox;
    private TextView locationText, fuelLabel, statusText, sortPrice, sortDistance;
    private ProgressBar progress;
    private Location currentLocation;
    private boolean sortByPrice = true;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        configureWindow();
        buildUi();
        requestLocationAndLoad();
    }

    private void configureWindow() {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), 0, dp(16), 0);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top; bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(dp(16), top + dp(8), dp(16), bottom + dp(8));
            return insets;
        });

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        titleWrap.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView title = text("주유소픽", 26, TEXT, true);
        locationText = text("현재 위치 확인 중", 13, SUB, false);
        titleWrap.addView(title); titleWrap.addView(locationText);
        header.addView(titleWrap);
        Button settingsBtn = button("설정", false);
        settingsBtn.setOnClickListener(v -> showSettings());
        header.addView(settingsBtn);
        root.addView(header, lpMatchWrap(0, 6));

        LinearLayout controlCard = card();
        LinearLayout fuelRow = row(); fuelRow.setGravity(Gravity.CENTER_VERTICAL);
        fuelLabel = text(fuelName(), 19, TEXT, true);
        fuelLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        fuelRow.addView(fuelLabel);
        Button refresh = button("새로고침", false);
        refresh.setOnClickListener(v -> requestLocationAndLoad());
        fuelRow.addView(refresh);
        controlCard.addView(fuelRow);

        LinearLayout sortRow = row(); sortRow.setPadding(0, dp(12), 0, 0);
        sortPrice = chip("가격순", true);
        sortDistance = chip("거리순", false);
        sortPrice.setOnClickListener(v -> { sortByPrice = true; updateSortChips(); renderAll(); });
        sortDistance.setOnClickListener(v -> { sortByPrice = false; updateSortChips(); renderAll(); });
        sortRow.addView(sortPrice, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams gapLp = new LinearLayout.LayoutParams(dp(8), 1);
        View gap = new View(this); sortRow.addView(gap, gapLp);
        sortRow.addView(sortDistance, new LinearLayout.LayoutParams(0, dp(44), 1));
        controlCard.addView(sortRow);
        root.addView(controlCard, lpMatchWrap(8, 8));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true); progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(12), 0, dp(24));
        scroll.addView(content);

        sectionTitle("강력추천");
        recommendedBox = new LinearLayout(this); recommendedBox.setOrientation(LinearLayout.VERTICAL); content.addView(recommendedBox);
        sectionTitle("즐겨찾기 비교");
        favoritesBox = new LinearLayout(this); favoritesBox.setOrientation(LinearLayout.VERTICAL); content.addView(favoritesBox);
        sectionTitle("주변 주유소");
        listBox = new LinearLayout(this); listBox.setOrientation(LinearLayout.VERTICAL); content.addView(listBox);
        statusText = text("", 13, SUB, false); statusText.setGravity(Gravity.CENTER); statusText.setPadding(0, dp(20),0,dp(20)); content.addView(statusText);

        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void sectionTitle(String s) {
        TextView t = text(s, 16, TEXT, true);
        t.setPadding(dp(2), dp(10), 0, dp(8));
        content.addView(t);
    }

    private void requestLocationAndLoad() {
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        LocationManager lm = (LocationManager)getSystemService(LOCATION_SERVICE);
        Location best = null;
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getAccuracy() < best.getAccuracy())) best = l;
            } catch (Exception ignored) {}
        }
        if (best != null) {
            currentLocation = best;
            locationText.setText("내 위치 기준 · 반경 5km");
            loadStations();
        } else {
            locationText.setText("위치를 찾는 중…");
            try {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, location -> {
                    currentLocation = location;
                    runOnUiThread(() -> { locationText.setText("내 위치 기준 · 반경 5km"); loadStations(); });
                }, null);
            } catch (Exception e) {
                showLocationError();
            }
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (hasLocationPermission()) requestLocationAndLoad();
            else showLocationError();
        }
    }

    private void showLocationError() {
        progress.setVisibility(View.GONE);
        statusText.setText("현재 위치 권한이 필요합니다. 설정에서 위치 권한을 허용해 주세요.");
        statusText.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
    }

    private void loadStations() {
        if (currentLocation == null) return;
        progress.setVisibility(View.VISIBLE);
        statusText.setText("주변 가격을 불러오는 중…");
        String fuel = fuelCode();

        executor.execute(() -> {
            try {
                String apiBase = resolveApiBase();
                if (apiBase.isEmpty()) throw new Exception("SERVER_PENDING");

                // 약 110m 단위로 위치를 반올림해 서버 로그에 정밀 위치를 보내지 않습니다.
                double lat = Math.round(currentLocation.getLatitude() * 1000.0) / 1000.0;
                double lng = Math.round(currentLocation.getLongitude() * 1000.0) / 1000.0;
                String url = apiBase + "/api/stations?lat=" +
                        String.format(Locale.US, "%.3f", lat) + "&lng=" +
                        String.format(Locale.US, "%.3f", lng) + "&fuel=" + enc(fuel);

                JSONObject json = getJson(url);
                List<Station> list = parseServerStations(json);
                synchronized (nearby) { nearby.clear(); nearby.addAll(list); }
                runOnUiThread(() -> { progress.setVisibility(View.GONE); renderAll(); });
                refreshMissingFavorites(apiBase, fuel);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    String msg = "SERVER_PENDING".equals(e.getMessage())
                            ? "가격 서버 연결 준비 중입니다. 앱 업데이트 없이 서버 연결 후 자동으로 활성화됩니다."
                            : "가격 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.\n" + safeMessage(e);
                    statusText.setText(msg);
                    recommendedBox.removeAllViews();
                    favoritesBox.removeAllViews();
                    listBox.removeAllViews();
                    recommendedBox.addView(emptyCard(msg));
                });
            }
        });
    }

    private String resolveApiBase() throws Exception {
        String cached = prefs.getString("api_base", "").trim();
        try {
            JSONObject config = getJson(REMOTE_CONFIG_URL + "?v=" + System.currentTimeMillis());
            String remote = config.optString("apiBase", "").trim();
            if (!remote.isEmpty()) {
                while (remote.endsWith("/")) remote = remote.substring(0, remote.length() - 1);
                prefs.edit().putString("api_base", remote).apply();
                return remote;
            }
        } catch (Exception ignored) {
            // 네트워크 일시 오류 시 마지막으로 성공한 서버 주소를 사용합니다.
        }
        return cached;
    }

    private void refreshMissingFavorites(String apiBase, String fuel) {
        Set<String> ids = getFavoriteIds();
        Set<String> nearbyIds = new HashSet<>();
        synchronized (nearby) { for (Station s: nearby) nearbyIds.add(s.id); }
        for (String id: ids) {
            if (nearbyIds.contains(id)) continue;
            executor.execute(() -> {
                try {
                    JSONObject json = getJson(apiBase + "/api/station?id=" + enc(id) + "&fuel=" + enc(fuel));
                    Station s = parseServerStation(json.optJSONObject("station"));
                    if (s != null && s.price > 0) {
                        if (currentLocation != null && s.lat != 0 && s.lng != 0) {
                            float[] d = new float[1];
                            Location.distanceBetween(currentLocation.getLatitude(), currentLocation.getLongitude(), s.lat, s.lng, d);
                            s.distance = d[0];
                        }
                        synchronized (favoriteDetails) { favoriteDetails.put(id, s); }
                        runOnUiThread(this::renderFavorites);
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    private List<Station> parseServerStations(JSONObject json) throws Exception {
        JSONArray arr = json.optJSONArray("stations");
        List<Station> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i=0;i<arr.length();i++) {
            Station station = parseServerStation(arr.getJSONObject(i));
            if (station != null && station.price > 0) {
                if (currentLocation != null && station.lat != 0 && station.lng != 0) {
                    float[] d = new float[1];
                    Location.distanceBetween(currentLocation.getLatitude(), currentLocation.getLongitude(), station.lat, station.lng, d);
                    station.distance = d[0];
                }
                out.add(station);
            }
        }
        return out;
    }

    private Station parseServerStation(JSONObject o) {
        if (o == null) return null;
        Station s = new Station();
        s.id = o.optString("id");
        s.name = o.optString("name");
        s.brand = o.optString("brand");
        s.address = o.optString("address");
        s.price = o.optInt("price", 0);
        s.distance = (float)o.optDouble("distance", 0);
        s.lat = o.optDouble("lat", 0);
        s.lng = o.optDouble("lng", 0);
        return s.id.isEmpty() ? null : s;
    }

    private void renderAll() {
        renderRecommendation(); renderFavorites(); renderList();
        statusText.setText(nearby.isEmpty() ? "반경 5km 안에서 판매가격이 확인된 주유소가 없습니다." : "오피넷 데이터 · 서버 캐시 5분 · 반경 5km · " + nearby.size() + "곳");
    }

    private void renderRecommendation() {
        recommendedBox.removeAllViews();
        Station best = bestStation();
        if (best == null) { recommendedBox.addView(emptyCard("추천할 주유소가 아직 없습니다.")); return; }

        LinearLayout c = stationCard(best, true, false);
        double total = effectiveCost(best);
        double travel = travelFuelCost(best);
        Station nearest = nearestStation();
        double saving = nearest == null ? 0 : effectiveCost(nearest) - total;

        String trip = roundTrip() ? "왕복" : "편도";
        String line1 = String.format(Locale.KOREA,
                "%.0fL 주유 + %s 이동비 포함 · 예상 총비용 %s원",
                fillLiters(), trip, new DecimalFormat("#,###").format(Math.round(total)));
        TextView reason = text(line1, 12, SUB, false);
        reason.setPadding(dp(12), 0, dp(12), dp(4));
        c.addView(reason);

        String line2;
        if (saving >= 50) {
            line2 = "가장 가까운 주유소 대비 약 " + new DecimalFormat("#,###").format(Math.round(saving)) + "원 절약 예상";
        } else {
            line2 = "단가와 이동 연료비를 합친 실질비용이 가장 낮습니다.";
        }
        TextView savingView = text(line2, 13, GREEN, true);
        savingView.setPadding(dp(12), 0, dp(12), dp(4));
        c.addView(savingView);

        TextView calc = text("이동비 " + new DecimalFormat("#,###").format(Math.round(travel)) +
                "원 추정 · 실연비 " + trimNumber(efficiency()) + "km/L 기준", 11, SUB, false);
        calc.setPadding(dp(12), 0, dp(12), dp(12));
        c.addView(calc);
        recommendedBox.addView(c, lpMatchWrap(0,8));
    }

    private Station bestStation() {
        List<Station> copy;
        synchronized (nearby) { copy = new ArrayList<>(nearby); }
        if (copy.isEmpty()) return null;
        Station best = null;
        double bestCost = Double.MAX_VALUE;
        for (Station station : copy) {
            double cost = effectiveCost(station);
            if (cost < bestCost) {
                bestCost = cost;
                best = station;
            }
        }
        return best;
    }

    private Station nearestStation() {
        List<Station> copy;
        synchronized (nearby) { copy = new ArrayList<>(nearby); }
        if (copy.isEmpty()) return null;
        return Collections.min(copy, Comparator.comparingDouble(a -> a.distance));
    }

    private double effectiveCost(Station station) {
        return station.price * fillLiters() + travelFuelCost(station);
    }

    private double travelFuelCost(Station station) {
        double km = station.distance / 1000.0;
        if (roundTrip()) km *= 2.0;
        return (km / Math.max(1.0, efficiency())) * localReferencePrice();
    }

    private double localReferencePrice() {
        List<Integer> prices = new ArrayList<>();
        synchronized (nearby) {
            for (Station station : nearby) if (station.price > 0) prices.add(station.price);
        }
        if (prices.isEmpty()) return fuelCode().equals("D047") ? 1600.0 : 1700.0;
        Collections.sort(prices);
        int mid = prices.size() / 2;
        if (prices.size() % 2 == 1) return prices.get(mid);
        return (prices.get(mid - 1) + prices.get(mid)) / 2.0;
    }

    private void renderFavorites() {
        favoritesBox.removeAllViews();
        Set<String> ids=getFavoriteIds();
        if(ids.isEmpty()){ favoritesBox.addView(emptyCard("☆를 눌러 비교할 주유소를 고정하세요.")); return; }
        List<Station> favs=new ArrayList<>();
        synchronized(nearby){ for(Station s:nearby) if(ids.contains(s.id)) favs.add(s); }
        synchronized(favoriteDetails){ for(String id:ids){ boolean exists=false; for(Station s:favs) if(s.id.equals(id)){exists=true;break;} if(!exists && favoriteDetails.containsKey(id)) favs.add(favoriteDetails.get(id)); }}
        Collections.sort(favs, Comparator.comparingInt(a->a.price));
        for(Station s:favs) favoritesBox.addView(stationCard(s,false,true),lpMatchWrap(0,8));
        if(favs.size()<ids.size()) favoritesBox.addView(text("일부 즐겨찾기 가격을 갱신 중입니다…",12,SUB,false));
    }

    private void renderList() {
        listBox.removeAllViews();
        List<Station> copy;
        synchronized(nearby){copy=new ArrayList<>(nearby);}
        if(sortByPrice) copy.sort(Comparator.comparingInt((Station s)->s.price).thenComparingDouble(s->s.distance));
        else copy.sort(Comparator.comparingDouble((Station s)->s.distance).thenComparingInt(s->s.price));
        Set<String> ids=getFavoriteIds();
        for(Station s:copy) listBox.addView(stationCard(s,false,ids.contains(s.id)),lpMatchWrap(0,8));
    }

    private LinearLayout stationCard(Station s, boolean recommended, boolean favorite) {
        LinearLayout c=card();
        if(recommended) c.setBackground(rounded(Color.WHITE,18,GREEN,2));
        LinearLayout top=row(); top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names=new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); names.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView nm=text(s.name,17,TEXT,true); nm.setMaxLines(1); names.addView(nm);
        names.addView(text(brandName(s.brand)+" · "+distanceText(s.distance),12,SUB,false));
        top.addView(names);
        TextView star=text(getFavoriteIds().contains(s.id)?"★":"☆",28,getFavoriteIds().contains(s.id)?GREEN:SUB,false);
        star.setGravity(Gravity.CENTER); star.setPadding(dp(8),0,dp(8),0); star.setOnClickListener(v->toggleFavorite(s)); top.addView(star,new LinearLayout.LayoutParams(dp(48),dp(48)));
        Button open=button("열기",true); open.setOnClickListener(v->openNaverMap(s)); top.addView(open);
        c.addView(top);
        LinearLayout priceRow=row(); priceRow.setGravity(Gravity.BOTTOM); priceRow.setPadding(0,dp(10),0,0);
        TextView price=text(new DecimalFormat("#,###").format(s.price)+"원",24,TEXT,true); priceRow.addView(price);
        priceRow.addView(text(" / L",13,SUB,false));
        c.addView(priceRow);
        return c;
    }

    private void toggleFavorite(Station s) {
        Set<String> ids=getFavoriteIds();
        if(ids.contains(s.id)) { ids.remove(s.id); synchronized(favoriteDetails){favoriteDetails.remove(s.id);} }
        else ids.add(s.id);
        prefs.edit().putStringSet("favorites", new HashSet<>(ids)).apply();
        renderAll();
    }

    private Set<String> getFavoriteIds(){ return new HashSet<>(prefs.getStringSet("favorites", Collections.emptySet())); }

    private void openNaverMap(Station s) {
        try {
            String uri=String.format(Locale.US,"nmap://place?lat=%.7f&lng=%.7f&name=%s&appname=%s",s.lat,s.lng,enc(s.name),getPackageName());
            Intent i=new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            i.setPackage("com.nhn.android.nmap");
            startActivity(i);
        } catch(ActivityNotFoundException e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id=com.nhn.android.nmap"))); }
            catch(Exception ex){ startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=com.nhn.android.nmap"))); }
        } catch(Exception e) { Toast.makeText(this,"네이버지도를 열 수 없습니다.",Toast.LENGTH_SHORT).show(); }
    }

    private void showSettings() {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(22),dp(8),dp(22),0);
        TextView fuelT=text("유종",14,SUB,true); box.addView(fuelT);
        LinearLayout fr=row(); fr.setPadding(0,dp(8),0,dp(18));
        Button gas=button("휘발유", fuelCode().equals("B027")); Button diesel=button("경유", fuelCode().equals("D047"));
        final String[] selected={fuelCode()};
        View.OnClickListener fuelClick=v->{selected[0]=(v==gas)?"B027":"D047"; styleButton(gas,selected[0].equals("B027")); styleButton(diesel,selected[0].equals("D047"));};
        gas.setOnClickListener(fuelClick);diesel.setOnClickListener(fuelClick);
        fr.addView(gas,new LinearLayout.LayoutParams(0,dp(46),1)); View gap=new View(this);fr.addView(gap,new LinearLayout.LayoutParams(dp(8),1)); fr.addView(diesel,new LinearLayout.LayoutParams(0,dp(46),1)); box.addView(fr);
        box.addView(text("강력추천 계산",14,SUB,true));

        LinearLayout inputs = row();
        inputs.setPadding(0, dp(8), 0, dp(10));

        LinearLayout litersWrap = new LinearLayout(this);
        litersWrap.setOrientation(LinearLayout.VERTICAL);
        litersWrap.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        litersWrap.addView(text("예상 주유량(L)",12,SUB,false));
        EditText liters = new EditText(this);
        liters.setText(trimNumber(fillLiters()));
        liters.setHint("40");
        liters.setSingleLine(true);
        liters.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        liters.setPadding(dp(12),0,dp(12),0);
        liters.setBackground(rounded(Color.rgb(243,245,246),12,Color.TRANSPARENT,0));
        litersWrap.addView(liters,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));
        inputs.addView(litersWrap);

        View inputGap = new View(this);
        inputs.addView(inputGap,new LinearLayout.LayoutParams(dp(8),1));

        LinearLayout efficiencyWrap = new LinearLayout(this);
        efficiencyWrap.setOrientation(LinearLayout.VERTICAL);
        efficiencyWrap.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        efficiencyWrap.addView(text("실연비(km/L)",12,SUB,false));
        EditText efficiencyInput = new EditText(this);
        efficiencyInput.setText(trimNumber(efficiency()));
        efficiencyInput.setHint("10");
        efficiencyInput.setSingleLine(true);
        efficiencyInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        efficiencyInput.setPadding(dp(12),0,dp(12),0);
        efficiencyInput.setBackground(rounded(Color.rgb(243,245,246),12,Color.TRANSPARENT,0));
        efficiencyWrap.addView(efficiencyInput,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));
        inputs.addView(efficiencyWrap);
        box.addView(inputs);

        LinearLayout tripRow = row();
        tripRow.setPadding(0,0,0,dp(18));
        Button oneWay = button("편도", !roundTrip());
        Button round = button("왕복", roundTrip());
        final boolean[] roundSelected = {roundTrip()};
        View.OnClickListener tripClick = v -> {
            roundSelected[0] = v == round;
            styleButton(oneWay, !roundSelected[0]);
            styleButton(round, roundSelected[0]);
        };
        oneWay.setOnClickListener(tripClick);
        round.setOnClickListener(tripClick);
        tripRow.addView(oneWay,new LinearLayout.LayoutParams(0,dp(44),1));
        View tripGap = new View(this);
        tripRow.addView(tripGap,new LinearLayout.LayoutParams(dp(8),1));
        tripRow.addView(round,new LinearLayout.LayoutParams(0,dp(44),1));
        box.addView(tripRow);

        TextView guide=text("주유 가격은 전용 서버가 오피넷에서 받아오므로 API 키 입력이 필요 없습니다. 추천은 주유비 + 이동 연료비의 예상 실질지출로 계산합니다.",12,SUB,false);
        guide.setPadding(0,dp(2),0,0);
        box.addView(guide);
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("설정").setView(box).setNegativeButton("취소",null).setNeutralButton("API 키 발급",null).setPositiveButton("저장",null).create();
        dlg.setOnShowListener(x->{
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.opinet.co.kr/user/custapi/custApiInfo.do"))));
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                double litersValue = parsePositive(liters.getText().toString(), 40.0);
                double efficiencyValue = parsePositive(efficiencyInput.getText().toString(), 10.0);
                prefs.edit()
                        .putString("fuel",selected[0])
                        .remove("api_key")
                        .putString("fill_liters",String.valueOf(litersValue))
                        .putString("efficiency",String.valueOf(efficiencyValue))
                        .putBoolean("round_trip",roundSelected[0])
                        .apply();
                fuelLabel.setText(fuelName());
                dlg.dismiss();
                requestLocationAndLoad();
            });
        });
        dlg.show();
    }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(urlText).openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","FuelPick/1.2");
        int code=c.getResponseCode(); InputStream in=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
        BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)); StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null)sb.append(line);br.close();c.disconnect();
        if(code<200||code>=300)throw new Exception("HTTP "+code);
        return new JSONObject(sb.toString());
    }

    private String fuelCode(){return prefs.getString("fuel","B027");}
    private String fuelName(){return fuelCode().equals("D047")?"경유":"휘발유";}
    private double fillLiters(){return parsePositive(prefs.getString("fill_liters","40"),40.0);}
    private double efficiency(){return parsePositive(prefs.getString("efficiency","10"),10.0);}
    private boolean roundTrip(){return prefs.getBoolean("round_trip",true);}
    private static double parsePositive(String value,double fallback){try{double d=Double.parseDouble(value);return d>0?d:fallback;}catch(Exception e){return fallback;}}
    private static String trimNumber(double d){if(Math.abs(d-Math.rint(d))<0.0001)return String.valueOf((long)Math.rint(d));return String.format(Locale.KOREA,"%.1f",d);}
    private static String brandName(String b){switch(b){case"SKE":return"SK에너지";case"GSC":return"GS칼텍스";case"HDO":return"HD현대오일뱅크";case"SOL":return"S-OIL";case"RTE":return"알뜰";case"RTX":return"고속도로알뜰";case"NHO":return"농협알뜰";default:return b==null||b.isEmpty()?"주유소":b;}}
    private static String distanceText(float m){return m<1000?Math.round(m)+"m":String.format(Locale.KOREA,"%.1fkm",m/1000f);}
    private static String safeMessage(Exception e){String s=e.getMessage();return s==null?e.getClass().getSimpleName():s;}
    private static String fmt(double d){return String.format(Locale.US,"%.3f",d);}
    private static String enc(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return s;}}

    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(14),dp(14),dp(14),dp(14));l.setBackground(rounded(Color.WHITE,18,Color.TRANSPARENT,0));l.setElevation(dp(1));return l;}
    private View emptyCard(String s){LinearLayout c=card();TextView t=text(s,13,SUB,false);t.setGravity(Gravity.CENTER);t.setPadding(0,dp(8),0,dp(8));c.addView(t);return c;}
    private View infoCard(String message,String action,Runnable r){LinearLayout c=card();c.addView(text(message,14,TEXT,false));Button b=button(action,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(44));lp.topMargin=dp(12);c.addView(b,lp);b.setOnClickListener(v->r.run());return c;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setIncludeFontPadding(false);return t;}
    private TextView chip(String s,boolean selected){TextView t=text(s,14,selected?Color.WHITE:TEXT,true);t.setGravity(Gravity.CENTER);t.setBackground(rounded(selected?GREEN:Color.WHITE,12,Color.TRANSPARENT,0));return t;}
    private void updateSortChips(){sortPrice.setTextColor(sortByPrice?Color.WHITE:TEXT);sortDistance.setTextColor(!sortByPrice?Color.WHITE:TEXT);sortPrice.setBackground(rounded(sortByPrice?GREEN:Color.WHITE,12,Color.TRANSPARENT,0));sortDistance.setBackground(rounded(!sortByPrice?GREEN:Color.WHITE,12,Color.TRANSPARENT,0));}
    private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setMinHeight(0);b.setMinWidth(0);b.setPadding(dp(14),0,dp(14),0);styleButton(b,primary);return b;}
    private void styleButton(Button b,boolean primary){b.setTextColor(primary?Color.WHITE:TEXT);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(rounded(primary?GREEN:Color.rgb(239,242,244),12,Color.TRANSPARENT,0));}
    private GradientDrawable rounded(int fill,int radius,int stroke,int sw){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(sw>0)g.setStroke(dp(sw),stroke);return g;}
    private LinearLayout.LayoutParams lpMatchWrap(int top,int bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.topMargin=dp(top);lp.bottomMargin=dp(bottom);return lp;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private static final class Station {
        String id="",name="",brand="",address="";
        int price;
        float distance;
        double lat,lng;
    }
}

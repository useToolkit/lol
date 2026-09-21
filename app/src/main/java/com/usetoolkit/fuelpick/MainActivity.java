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
        String key = prefs.getString("api_key", "").trim();
        if (key.isEmpty()) {
            progress.setVisibility(View.GONE);
            statusText.setText("오피넷 API 키가 필요합니다. 우상단 ‘설정’에서 한 번만 입력해 주세요.");
            recommendedBox.removeAllViews(); favoritesBox.removeAllViews(); listBox.removeAllViews();
            recommendedBox.addView(infoCard("API 키를 설정하면 현재 위치의 실제 주유 가격을 불러옵니다.", "설정 열기", this::showSettings));
            return;
        }
        if (currentLocation == null) return;
        progress.setVisibility(View.VISIBLE); statusText.setText("주변 가격을 불러오는 중…");
        double[] k = KatecConverter.wgs84ToKatec(currentLocation.getLatitude(), currentLocation.getLongitude());
        String fuel = fuelCode();
        executor.execute(() -> {
            try {
                String url = "https://www.opinet.co.kr/api/aroundAll.do?out=json&x=" + fmt(k[0]) + "&y=" + fmt(k[1]) +
                        "&radius=5000&sort=1&prodcd=" + fuel + "&certkey=" + enc(key);
                JSONObject json = getJson(url);
                List<Station> list = parseAround(json);
                synchronized (nearby) { nearby.clear(); nearby.addAll(list); }
                runOnUiThread(() -> { progress.setVisibility(View.GONE); renderAll(); });
                refreshMissingFavorites(key, fuel);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    statusText.setText("가격 조회에 실패했습니다. API 키와 네트워크 상태를 확인해 주세요.\n" + safeMessage(e));
                });
            }
        });
    }

    private void refreshMissingFavorites(String key, String fuel) {
        Set<String> ids = getFavoriteIds();
        Set<String> nearbyIds = new HashSet<>();
        synchronized (nearby) { for (Station s: nearby) nearbyIds.add(s.id); }
        for (String id: ids) {
            if (nearbyIds.contains(id)) continue;
            executor.execute(() -> {
                try {
                    JSONObject json = getJson("https://www.opinet.co.kr/api/detailById.do?out=json&id="+enc(id)+"&certkey="+enc(key));
                    Station s = parseDetail(json, fuel);
                    if (s != null) {
                        if (currentLocation != null) {
                            double[] wgs = KatecConverter.katecToWgs84(s.kx, s.ky);
                            float[] d = new float[1];
                            Location.distanceBetween(currentLocation.getLatitude(), currentLocation.getLongitude(), wgs[0], wgs[1], d);
                            s.distance = d[0];
                        }
                        synchronized (favoriteDetails) { favoriteDetails.put(id, s); }
                        runOnUiThread(this::renderFavorites);
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    private List<Station> parseAround(JSONObject json) throws Exception {
        JSONArray oils = json.getJSONObject("RESULT").optJSONArray("OIL");
        List<Station> out = new ArrayList<>();
        if (oils == null) return out;
        for (int i=0;i<oils.length();i++) {
            JSONObject o = oils.getJSONObject(i);
            Station s = new Station();
            s.id = o.optString("UNI_ID"); s.name = o.optString("OS_NM");
            s.brand = o.optString("POLL_DIV_CO", o.optString("POLL_DIV_CD"));
            s.price = o.optInt("PRICE", 0); s.distance = (float)o.optDouble("DISTANCE", 0);
            s.kx = o.optDouble("GIS_X_COOR", 0); s.ky = o.optDouble("GIS_Y_COOR", 0);
            if (!s.id.isEmpty() && s.price > 0) out.add(s);
        }
        return out;
    }

    private Station parseDetail(JSONObject json, String wantedFuel) throws Exception {
        JSONArray oils = json.getJSONObject("RESULT").optJSONArray("OIL");
        if (oils == null || oils.length()==0) return null;
        JSONObject o = oils.getJSONObject(0);
        Station s = new Station();
        s.id=o.optString("UNI_ID"); s.name=o.optString("OS_NM");
        s.brand=o.optString("POLL_DIV_CO", o.optString("POLL_DIV_CD"));
        s.address=o.optString("NEW_ADR", o.optString("VAN_ADR"));
        s.kx=o.optDouble("GIS_X_COOR",0); s.ky=o.optDouble("GIS_Y_COOR",0);
        Object p = o.opt("OIL_PRICE");
        if (p instanceof JSONArray) {
            JSONArray a=(JSONArray)p;
            for(int i=0;i<a.length();i++) if(wantedFuel.equals(a.getJSONObject(i).optString("PRODCD"))) s.price=a.getJSONObject(i).optInt("PRICE",0);
        } else if (p instanceof JSONObject) {
            JSONObject po=(JSONObject)p; if(wantedFuel.equals(po.optString("PRODCD"))) s.price=po.optInt("PRICE",0);
        }
        return s.price>0?s:null;
    }

    private void renderAll() {
        renderRecommendation(); renderFavorites(); renderList();
        statusText.setText(nearby.isEmpty() ? "반경 5km 안에서 판매가격이 확인된 주유소가 없습니다." : "오피넷 기준 · 반경 5km · " + nearby.size() + "곳");
    }

    private void renderRecommendation() {
        recommendedBox.removeAllViews();
        Station best = bestStation();
        if (best == null) { recommendedBox.addView(emptyCard("추천할 주유소가 아직 없습니다.")); return; }
        LinearLayout c = stationCard(best, true, false);
        TextView reason = text("가격 70% + 거리 30%를 함께 고려한 추천", 12, SUB, false);
        reason.setPadding(dp(12), 0, dp(12), dp(12)); c.addView(reason);
        recommendedBox.addView(c, lpMatchWrap(0,8));
    }

    private Station bestStation() {
        List<Station> copy;
        synchronized (nearby) { copy = new ArrayList<>(nearby); }
        if (copy.isEmpty()) return null;
        int minP=Integer.MAX_VALUE,maxP=Integer.MIN_VALUE; float minD=Float.MAX_VALUE,maxD=0;
        for(Station s:copy){ minP=Math.min(minP,s.price);maxP=Math.max(maxP,s.price);minD=Math.min(minD,s.distance);maxD=Math.max(maxD,s.distance); }
        Station best=null; double bestScore=Double.MAX_VALUE;
        for(Station s:copy){
            double pn=maxP==minP?0:(s.price-minP)/(double)(maxP-minP);
            double dn=maxD==minD?0:(s.distance-minD)/(double)(maxD-minD);
            double score=.70*pn+.30*dn;
            if(score<bestScore){bestScore=score;best=s;}
        }
        return best;
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
            double[] wgs=KatecConverter.katecToWgs84(s.kx,s.ky);
            String uri=String.format(Locale.US,"nmap://place?lat=%.7f&lng=%.7f&name=%s&appname=%s",wgs[0],wgs[1],enc(s.name),getPackageName());
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
        box.addView(text("오피넷 API 키",14,SUB,true));
        EditText key=new EditText(this); key.setText(prefs.getString("api_key","")); key.setHint("인증키 입력"); key.setSingleLine(true); key.setInputType(InputType.TYPE_CLASS_TEXT); key.setPadding(dp(12),0,dp(12),0); key.setBackground(rounded(Color.rgb(243,245,246),12,Color.TRANSPARENT,0)); box.addView(key,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
        TextView guide=text("오피넷 일반 API 인증키를 한 번 저장하면 기기에만 보관됩니다.",12,SUB,false); guide.setPadding(0,dp(8),0,0);box.addView(guide);
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("설정").setView(box).setNegativeButton("취소",null).setNeutralButton("API 키 발급",null).setPositiveButton("저장",null).create();
        dlg.setOnShowListener(x->{
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.opinet.co.kr/user/custapi/custApiInfo.do"))));
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{prefs.edit().putString("fuel",selected[0]).putString("api_key",key.getText().toString().trim()).apply();fuelLabel.setText(fuelName());dlg.dismiss();requestLocationAndLoad();});
        });
        dlg.show();
    }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(urlText).openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","FuelPick/1.0");
        int code=c.getResponseCode(); InputStream in=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
        BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)); StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null)sb.append(line);br.close();c.disconnect();
        if(code<200||code>=300)throw new Exception("HTTP "+code);
        return new JSONObject(sb.toString());
    }

    private String fuelCode(){return prefs.getString("fuel","B027");}
    private String fuelName(){return fuelCode().equals("D047")?"경유":"휘발유";}
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
        String id="",name="",brand="",address=""; int price; float distance; double kx,ky;
    }
}

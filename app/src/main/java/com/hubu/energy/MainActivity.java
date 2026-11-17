package com.hubu.energy;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.math.BigDecimal;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.ArrayDeque;

public class MainActivity extends Activity {
    static final String ENERGY_URL = "https://zhhq.hubu.edu.cn/wap/#/energy-topup/1";
    private static final String AIR_URL = "https://zhhq.hubu.edu.cn/wap/#/energy-topup/2";
    private static final String WATER_URL = "https://zhhq.hubu.edu.cn/wap/#/water-topup";
    private static final String TIMETABLE_URL = "https://jwxt.hubu.edu.cn/jsxsd/xskb/xskb_list.do";
    private static final String GRADES_URL = "https://jwxt.hubu.edu.cn/jsxsd/kscj/cjcx_query";
    private static final String LIBRARY_URL = "https://web.traceint.com/web/index.html#/pages/index/index?r=1792762748&n=6adb637c11938";
    private static final String LOCAL_URL = "file:///android_asset/www/index.html";
    private SharedPreferences prefs;
    private WebView dashboard, school;
    private FrameLayout shell;
    private LinearLayout schoolPanel;
    private TextView schoolStatus;
    private Button schoolReadButton;
    private boolean timetableConfirmed;
    private boolean schoolVisible, paused;
    private final java.util.Set<String> loginModes = new HashSet<>();
    private final ArrayDeque<String> batchQueue = new ArrayDeque<>();
    private final java.util.List<String> batchResults = new java.util.ArrayList<>();
    private JSONObject feeStates = new JSONObject();
    private boolean batch;
    private String destination = "home", readError = "";
    private int timetableRedirects;
    private String gradeTerm = "", gradeStatus = "idle", gradeMessage = "";
    private boolean gradeSubmitted, gradeTermsOnly;
    private volatile String syncStatus = "idle", syncMessage = "";
    private String queryMode = "lighting";
    private String reader;
    private long loginSeenAt;
    private final RefreshSession refresh = new RefreshSession();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::pollSchool;
    private XxtStore xxtStore;
    private XxtClient xxtClient;
    private XxtLogin xxtLogin;
    private boolean appStopped = true, destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(EnergyWidgetProvider.PREFS, MODE_PRIVATE);
        xxtStore = new XxtStore(prefs);
        xxtClient = new XxtClient(xxtStore, () -> runOnUiThread(() -> { if (!destroyed) {render(); updateWidgets();} }));
        getWindow().setStatusBarColor(Color.rgb(242, 246, 246));
        getWindow().setNavigationBarColor(Color.rgb(242, 246, 246));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0));
        shell = new FrameLayout(this);
        shell.setBackgroundColor(Color.rgb(242, 246, 246));
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets pad = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(pad.left, pad.top, pad.right, pad.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(shell);
        dashboard = new WebView(this);
        configure(dashboard);
        dashboard.setBackgroundColor(Color.rgb(242, 246, 246));
        dashboard.addJavascriptInterface(new DashboardBridge(), "EnergyApp");
        dashboard.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return true; }
            @Override public void onPageFinished(WebView view, String url) { render(); navigateDestination(); }
        });
        shell.addView(dashboard, new FrameLayout.LayoutParams(-1, -1));
        dashboard.loadUrl(LOCAL_URL);
        destination = intentPage(getIntent());
        try (InputStream stream = getAssets().open("www/reader.js")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = stream.read(buffer)) != -1) out.write(buffer, 0, n);
            reader = out.toString("UTF-8");
        } catch (Exception e) { reader = "(function(){return {error:'reader-unavailable'};})()"; }
    }

    private void configure(WebView web) {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    private boolean schoolHost(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null &&
                    (host.equals("hubu.edu.cn") || host.endsWith(".hubu.edu.cn"));
        } catch (Exception e) { return false; }
    }

    private void ensureSchool() {
        if (schoolPanel == null) {
            schoolPanel = new LinearLayout(this); schoolPanel.setOrientation(LinearLayout.VERTICAL);
            schoolPanel.setBackgroundColor(Color.WHITE);
            LinearLayout bar = new LinearLayout(this); bar.setPadding(dp(8), dp(6), dp(8), dp(6));
            Button back = new Button(this); back.setText("返回助手"); back.setOnClickListener(v -> leaveSchool());
            Button sync = new Button(this); schoolReadButton = sync; sync.setText("读取当前信息"); sync.setTextColor(Color.rgb(11,112,80)); sync.setOnClickListener(v -> readSchool());
            bar.addView(back, new LinearLayout.LayoutParams(0, dp(48), 1));
            bar.addView(sync, new LinearLayout.LayoutParams(0, dp(48), 1));
            schoolPanel.addView(bar);
            schoolStatus = new TextView(this); schoolStatus.setTextSize(12); schoolStatus.setTextColor(Color.rgb(85,104,97));
            schoolStatus.setPadding(dp(16), dp(4), dp(16), dp(10));
            schoolPanel.addView(schoolStatus);
            school = new WebView(this); configure(school);
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(school, asset("school-capture.js"), Collections.singleton("https://zhhq.hubu.edu.cn"));
            }
            school.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            // No native bridge is exposed to the remote school or SSO pages.
            CookieManager.getInstance().setAcceptCookie(true);
            school.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return blockNavigation(request.getUrl().toString()); }
                @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return blockNavigation(url); }
                private boolean blockNavigation(String url) {
                    if (schoolHost(url)) return false;
                    finishRefresh("error", "学校页面跳转到其他应用，暂时无法刷新。请打开学校页面处理后重试。");
                    return true;
                }
                @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                    refresh.navigated(); loginSeenAt = 0;
                    schoolStatus.setText("学校页面加载中…");
                }
                @Override public void onPageCommitVisible(WebView view, String url) {
                    if (url.equals(view.getUrl())) {
                        refresh.committed();
                        if (url.startsWith("https://zhhq.hubu.edu.cn/")) school.evaluateJavascript(asset("school-capture.js"), null);
                        schedulePoll(200);
                    }
                }
                @Override public void onPageFinished(WebView view, String url) {
                    CookieManager.getInstance().flush();
                    if (refresh.active() && refresh.interactive()) schoolStatus.setText("timetable".equals(queryMode) ? "请选择学期、周次「全部」，导入后离线保存。" : "请完成学校登录，读取成功后自动返回助手。");
                }
                @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame() && request.getUrl().toString().equals(view.getUrl())) finishRefresh("error", "刷新失败，请检查网络后重试。上次查询结果已保留。");
                }
                @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                    if (request.isForMainFrame() && request.getUrl().toString().equals(view.getUrl())) finishRefresh("error", "学校页面暂时无法访问，请稍后重试。上次查询结果已保留。");
                }
            });
            schoolPanel.addView(school, new LinearLayout.LayoutParams(-1, 0, 1));
            // Keep the school WebView measured behind the opaque dashboard so DOM fields can
            // be read during a refresh without taking the user into the school page.
            schoolPanel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            school.setFocusable(false); school.setFocusableInTouchMode(false);
            shell.addView(schoolPanel, 0, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    private void showSchool() {
        showSchool("lighting");
    }

    private void showSchool(String mode) {
        if (refresh.active()) return;
        ensureSchool();
        boolean sameSystem = academicMode(queryMode) == academicMode(mode);
        queryMode = mode;
        timetableConfirmed = false;
        schoolReadButton.setText("grades".equals(mode) ? "继续查询" : "timetable".equals(mode) ? "导入所选学期" : "读取当前信息");
        timetableRedirects = 0;
        boolean reuseLogin = sameSystem && isLoginPage(school.getUrl());
        beginRefresh(true);
        schoolVisible = true;
        schoolPanel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        school.setFocusable(true); school.setFocusableInTouchMode(true);
        dashboard.setVisibility(View.GONE); schoolPanel.bringToFront();
        if (reuseLogin) { refresh.navigated(); refresh.committed(); schedulePoll(200); }
        else loadModePage();
    }

    private void refreshMode(String mode) {
        if (refresh.active()) return;
        ensureSchool();
        queryMode = mode;
        beginRefresh(false);
        loadModePage();
    }

    private String label(String mode) {
        if ("grades".equals(mode)) return "成绩";
        if ("aircon".equals(mode)) return "空调";
        if ("water".equals(mode)) return "水费";
        if ("timetable".equals(mode)) return "课表";
        return "照明";
    }

    private void refreshAllFees() {
        if (refresh.active()) return;
        boolean needsLogin = loginModes.contains("lighting") || loginModes.contains("aircon") || loginModes.contains("water");
        batch = true; batchResults.clear(); batchQueue.clear();
        boolean hasAny = snapshot().has("energy") || readObjectPref("air_snapshot").has("energy") || readObjectPref("water_snapshot").has("money");
        if (!hasAny || needsLogin) {
            // The first total refresh must expose the official page so the user can log in.
            batchQueue.addAll(Arrays.asList("aircon", "water"));
            showSchool("lighting");
        } else {
            batchQueue.addAll(Arrays.asList("lighting", "aircon", "water"));
            nextFee();
        }
    }

    private void nextFee() {
        if (!batchQueue.isEmpty()) { if (schoolVisible) showDashboard(); refreshMode(batchQueue.removeFirst()); return; }
        batch = false;
        syncStatus = loginModes.contains("lighting") || loginModes.contains("aircon") || loginModes.contains("water") ? "login" : "success";
        for (String result : batchResults) if (result.contains("失败") && !"login".equals(syncStatus)) syncStatus = "error";
        syncMessage = android.text.TextUtils.join("；", batchResults);
        render();
        if (!batch && schoolVisible) showDashboard();
    }

    private String modeUrl() {
        if ("grades".equals(queryMode)) return GRADES_URL;
        if ("aircon".equals(queryMode)) return AIR_URL;
        if ("water".equals(queryMode)) return WATER_URL;
        if ("timetable".equals(queryMode)) return TIMETABLE_URL;
        return ENERGY_URL;
    }

    private void loadModePage() {
        school.stopLoading();
        String url = modeUrl();
        // Changing only the hash stays in the same SPA document. A fresh document
        // prevents stale lighting fields being saved as air-conditioning values.
        if (url.startsWith("https://zhhq.hubu.edu.cn/wap/#")) {
            url = url.replace("/wap/#", "/wap/?_assistant_query=" + SystemClock.elapsedRealtime() + "#");
        }
        school.loadUrl(url);
    }

    private void beginRefresh(boolean interactive) {
        refreshHandler.removeCallbacks(poll);
        refresh.begin(SystemClock.elapsedRealtime(), interactive);
        loginSeenAt = 0;
        readError = "";
        syncStatus = "refreshing";
        syncMessage = interactive ? "timetable".equals(queryMode) ? "登录教务系统后选择学期和「全部」周次。" : "完成学校登录后会自动查询。" : (batch ? "三项费用 · " : "") + "正在查询" + label(queryMode) + "…";
        if ("grades".equals(queryMode)) { gradeSubmitted=false; gradeStatus="refreshing"; gradeMessage=gradeTermsOnly?"正在获取学校学期列表…":"正在查询所选学期成绩…"; }
        if (!academicMode(queryMode)) try { feeStates.put(queryMode, "查询中"); } catch (Exception ignored) { }
        render(); schedulePoll(300);
    }

    private void schedulePoll(long delay) {
        refreshHandler.removeCallbacks(poll);
        if (refresh.active() && !paused) refreshHandler.postDelayed(poll, delay);
    }

    private boolean isLoginPage(String url) {
        if (!schoolHost(url)) return false;
        Uri uri = Uri.parse(url);
        return "sso.hubu.edu.cn".equals(uri.getHost()) ||
                (uri.getFragment() != null && uri.getFragment().matches("/login(?:[/?].*)?")) ||
                (uri.getPath() != null && uri.getPath().toLowerCase(java.util.Locale.ROOT).contains("/login"));
    }

    private boolean isEnergyPage(String url) {
        if (!schoolHost(url)) return false;
        Uri u = Uri.parse(url);
        return "zhhq.hubu.edu.cn".equals(u.getHost()) && u.getPath() != null && u.getPath().startsWith("/wap/") &&
                u.getFragment() != null && u.getFragment().matches("/energy-topup/[12](?:[/?].*)?");
    }

    private boolean isModePage(String url) {
        if (url == null) return false;
        if ("grades".equals(queryMode)) return url.startsWith("https://jwxt.hubu.edu.cn/jsxsd/kscj/cjcx_query") || url.startsWith("https://jwxt.hubu.edu.cn/jsxsd/kscj/cjcx_list");
        if ("timetable".equals(queryMode)) return url.startsWith("https://jwxt.hubu.edu.cn/jsxsd/xskb/");
        if ("water".equals(queryMode)) return schoolHost(url) && url.contains("/wap/") && url.contains("water-topup");
        return schoolHost(url) && url.startsWith("https://zhhq.hubu.edu.cn/wap/") && url.endsWith("/energy-topup/" + ("aircon".equals(queryMode) ? "2" : "1"));
    }

    private void readSchool() {
        if ("grades".equals(queryMode)) {
            if (!refresh.active()) { beginRefresh(true); loadModePage(); }
            else schedulePoll(0);
            return;
        }
        if (school == null || !isModePage(school.getUrl())) { schoolStatus.setText("请先完成登录，并打开对应的学校页面。"); return; }
        if ("timetable".equals(queryMode)) timetableConfirmed = true;
        if (!refresh.active()) {
            beginRefresh(true);
            refresh.navigated(); refresh.committed();
        }
        schedulePoll(0);
    }

    private void pollSchool() {
        if (!refresh.active() || paused || school == null) return;
        if (refresh.expired(SystemClock.elapsedRealtime())) {
            finishRefresh("error", readError.isEmpty() ? label(queryMode) + "查询超时，原记录已保留。可打开学校页面检查。" : readError);
            return;
        }
        schedulePoll(750);
        final RefreshSession.Read read = refresh.startRead();
        if (read == null) return;
        final String expectedUrl = school.getUrl();
        school.evaluateJavascript(queryScript(), result -> {
            if (!refresh.completeRead(read) || paused) return;
            try {
                if (expectedUrl == null || !expectedUrl.equals(school.getUrl()) || !schoolHost(expectedUrl)) return;
                JSONObject response = new JSONObject(result);
                if (response.optBoolean("passwordVisible") || response.optBoolean("loginControl") || response.optBoolean("auth")) {
                    if ("grades".equals(queryMode) && !refresh.interactive()) {
                        finishRefresh("login", "请在内置教务页面完成登录。");
                        showSchool("grades"); return;
                    }
                    if (refresh.interactive()) {
                        schoolStatus.setText("请在学校官方页面完成登录，成功后将继续读取。");
                    } else {
                        long now = SystemClock.elapsedRealtime();
                        if (loginSeenAt == 0) loginSeenAt = now;
                        if (now - loginSeenAt < 1000) return;
                        loginModes.add(queryMode);
                        finishRefresh("login", label(queryMode) + "需要重新登录，原记录已保留。");
                    }
                    return;
                }
                loginSeenAt = 0;
                if (academicMode(queryMode) && expectedUrl.startsWith("https://jwxt.hubu.edu.cn/jsxsd/framework/") && timetableRedirects++ < 2) {
                    school.loadUrl(modeUrl()); return;
                }
                if (!isModePage(expectedUrl)) return;
                JSONObject raw = response.optJSONObject("data");
                if (raw == null) return;
                String error = raw.optString("error");
                if (!error.isEmpty()) {
                    if ("grades".equals(queryMode)) { finishRefresh("error", "成绩页面结构不完整或已变化，请稍后重试。原成绩已保留。"); if (schoolVisible) showDashboard(); return; }
                    if ("select-all-weeks".equals(error)) readError = "请选择周次「全部」后读取，避免只导入一周。";
                    else if ("business".equals(error)) readError = label(queryMode) + "账户查询未成功，请在学校页面检查账户绑定。";
                    else if ("network".equals(error)) readError = "学校查询请求失败，请检查网络。";
                    else readError = "尚未读取到" + label(queryMode) + "完整信息，请在学校页面检查。";
                    if (schoolVisible) schoolStatus.setText(readError);
                    return;
                }
                if ("grades".equals(queryMode)) { handleGrades(raw); return; }
                if ("timetable".equals(queryMode)) {
                    if (!timetableConfirmed) {
                        JSONArray courses = raw.optJSONArray("courses");
                        schoolStatus.setText(courses != null && courses.length() > 0 ? "已识别 " + raw.optString("term") + " 学期的 " + courses.length() + " 条安排。确认学期和「全部」周次后，点顶部「导入所选学期」。" : "请选择学期和「全部」周次，再点顶部「导入所选学期」。");
                        return;
                    }
                    saveTimetable(raw); return;
                }
                if ("water".equals(queryMode)) { saveWater(raw); return; }
                saveEnergy(raw);
            } catch (Exception ignored) {
                // Wait for a complete page. Never overwrite saved data with a partial result.
            }
        });
    }

    private String asset(String name) {
        try (InputStream in = getAssets().open("www/" + name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] bytes = new byte[4096]; int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) { return "({error:'reader-unavailable'})"; }
    }

    private String queryScript() {
        String script = "grades".equals(queryMode) ? asset("grades-reader.js") : "timetable".equals(queryMode) ? asset("timetable-reader.js") : "water".equals(queryMode) ? asset("water-reader.js") : reader;
        return "(function(){var inputs=Array.prototype.slice.call(document.querySelectorAll('input'));" +
                "function visible(e){return !!e.getClientRects().length&&getComputedStyle(e).visibility!=='hidden';}" +
                "var password=inputs.some(function(e){return e.type==='password'&&visible(e);});" +
                "var login=Array.prototype.some.call(document.querySelectorAll('button,a,input[type=submit]'),function(e){return visible(e)&&/^(登\\s*录|统一身份认证|立即登录|账号登录)$/.test((e.innerText||e.value||'').trim());});" +
                "var captured=window.__hubuCapture&&window.__hubuCapture[" + JSONObject.quote(queryMode) + "];" +
                "var data=captured&&captured.data?captured.data:" + script + ";" +
                "if(captured&&captured.error&&(!data||data.error))data={error:captured.error};" +
                "return {data:data,auth:!!(captured&&captured.auth),passwordVisible:password,loginControl:login};})()";
    }

    private boolean academicMode(String mode) { return "timetable".equals(mode) || "grades".equals(mode); }

    private void startGrades(String term, boolean termsOnly) {
        if (refresh.active()) { toast("请等待当前查询完成"); return; }
        if (!(term.isEmpty() || term.matches("\\d{4}-\\d{4}-[12]"))) return;
        if (!termsOnly) {
            boolean known=false; JSONArray terms=readArrayPref("grade_terms");
            for(int i=0;i<terms.length();i++) if(terms.optJSONObject(i)!=null && term.equals(terms.optJSONObject(i).optString("value"))) known=true;
            if(!known) { toast("请先获取学校学期列表"); return; }
        }
        gradeTerm=term; gradeTermsOnly=termsOnly; timetableRedirects=0; destination="grades";
        refreshMode("grades");
    }

    private void handleGrades(JSONObject raw) throws Exception {
        if ("query".equals(raw.optString("stage"))) {
            JSONArray terms=raw.optJSONArray("terms");
            if(terms==null || terms.length()<2 || terms.length()>150) { finishRefresh("error","未获取到学校学期列表，原成绩已保留。"); return; }
            JSONArray clean=new JSONArray(); boolean known=false;
            for(int i=0;i<terms.length();i++) {
                JSONObject t=terms.getJSONObject(i); String value=t.optString("value"), label=t.optString("label");
                if(!(value.isEmpty() || value.matches("\\d{4}-\\d{4}-[12]")) || label.length()>80) return;
                clean.put(new JSONObject().put("value",value).put("label",label));
                if(gradeTerm.equals(value)) known=true;
            }
            if(!prefs.edit().putString("grade_terms",clean.toString()).commit()) { finishRefresh("error","学期列表保存失败，请重试。"); return; }
            if(gradeTermsOnly) { completeQuery("学期列表已更新，请选择学期查询成绩"); return; }
            if(!known) { finishRefresh("error","学校当前未提供所选学期，请更新列表后重试。"); if(schoolVisible)showDashboard(); return; }
            if(gradeSubmitted) return;
            gradeSubmitted=true;
            // Use the official query form and submit routine; keep cookies inside WebView.
            school.evaluateJavascript("(function(){var s=document.querySelector('#kksj'),f=document.forms['kscjQueryForm'],b=document.querySelector('#btn_query');if(!s||!f||!b)return;s.value="+JSONObject.quote(gradeTerm)+";document.querySelector('#kcxz').value='';document.querySelector('#kcmc').value='';document.querySelector('#xsfs').value='all';document.querySelector('#sfxsbcxq').checked=false;f.target='_self';b.click();})()", null);
            render(); return;
        }
        if(!gradeSubmitted || !"result".equals(raw.optString("stage")) || !raw.has("term") || !gradeTerm.equals(raw.getString("term"))) return;
        JSONArray rows=raw.optJSONArray("rows"); JSONObject summary=raw.optJSONObject("summary");
        if(rows==null || rows.length()>2000 || summary==null) return;
        String[] fields={"term","code","name","score","mark","credits","hours","gpa","retakeTerm","assessment","examType","attribute","category"};
        JSONArray clean=new JSONArray();
        for(int i=0;i<rows.length();i++) {
            JSONObject r=rows.getJSONObject(i), c=new JSONObject();
            if(r.optString("name").isEmpty() || !r.optString("term").matches("\\d{4}-\\d{4}-[12]") || (!gradeTerm.isEmpty() && !gradeTerm.equals(r.optString("term")))) return;
            for(String field:fields) {String v=r.optString(field);if(v.length()>300)return;c.put(field,v);} clean.put(c);
        }
        JSONObject stats=new JSONObject();
        for(String key:new String[]{"count","credits","gpa","average"}) if(summary.has(key) && number(summary.optString(key))!=null)stats.put(key,summary.getString(key));
        if(stats.has("count") && stats.optDouble("count")!=clean.length()) return;
        JSONObject saved=new JSONObject().put("term",gradeTerm).put("rows",clean).put("summary",stats).put("time",System.currentTimeMillis());
        JSONObject results=readObjectPref("grade_results"); results.put(gradeTerm.isEmpty()?"all":gradeTerm,saved);
        if(!prefs.edit().putString("grade_results",results.toString()).putBoolean("demo",false).commit()) { finishRefresh("error","成绩保存失败，原记录已保留。"); return; }
        destination="grades"; completeQuery(clean.length()==0?"所选学期暂无已发布成绩":"成绩已更新，本机离线保存");
    }

    private void saveEnergy(JSONObject raw) throws Exception {
        String energy = number(raw.optString("energy")), money = number(raw.optString("money"));
        String room = raw.optString("room").trim();
        if (energy == null || money == null || room.isEmpty() || room.length() > 100) return;
        JSONObject saved = new JSONObject();
        saved.put("energy", energy); saved.put("money", money);
        saved.put("subsidyEnergy", nullable(number(raw.optString("subsidyEnergy"))));
        saved.put("subsidyMoney", nullable(number(raw.optString("subsidyMoney"))));
        saved.put("room", room); saved.put("campus", raw.optString("campus", "湖北大学"));
        saved.put("time", System.currentTimeMillis());
        boolean air = "aircon".equals(queryMode);
        SharedPreferences.Editor edit = prefs.edit().putString(air ? "air_snapshot" : "snapshot", saved.toString()).putBoolean("demo", false);
        JSONArray old = readArrayPref(air ? "air_history" : "history"), next = new JSONArray(); next.put(saved);
        for (int i = 0; i < Math.min(old.length(), 499); i++) next.put(old.get(i));
        edit.putString(air ? "air_history" : "history", next.toString()).apply();
        completeQuery(label(queryMode) + "已更新");
    }

    private void saveWater(JSONObject raw) throws Exception {
        String money = number(raw.optString("money"));
        if (money == null) return;
        JSONObject saved = new JSONObject();
        // Keep the original decimal string, including three decimal places.
        saved.put("money", raw.optString("money"));
        saved.put("givenMoney", nullable(number(raw.optString("givenMoney"))));
        saved.put("projectName", raw.optString("projectName", "湖北大学"));
        saved.put("time", System.currentTimeMillis());
        prefs.edit().putString("water_snapshot", saved.toString()).putBoolean("demo", false).apply();
        completeQuery("水费已更新");
    }

    private void saveTimetable(JSONObject raw) throws Exception {
        JSONArray courses = raw.optJSONArray("courses");
        if (courses == null || courses.length() == 0 || courses.length() > 500 || raw.optString("term").isEmpty()) return;
        JSONArray clean = new JSONArray();
        for (int i = 0; i < courses.length(); i++) {
            JSONObject c = courses.getJSONObject(i);
            if (c.optInt("weekday") < 1 || c.optInt("weekday") > 7 || c.optString("name").isEmpty() || c.optString("weeks").isEmpty() || c.optString("sections").isEmpty()) return;
            clean.put(c);
        }
        String term = raw.optString("term");
        SharedPreferences.Editor edit = prefs.edit().putString("timetable", clean.toString()).putString("term", term)
                .putLong("timetable_revision", prefs.getLong("timetable_revision", 0) + 1).remove("timetable_edited")
                .putString("timetable_notes", raw.optJSONArray("notes") == null ? "[]" : raw.getJSONArray("notes").toString())
                .putString("timetable_slots", raw.optJSONArray("slots") == null ? "[]" : raw.getJSONArray("slots").toString())
                .putString("time_mode", raw.optString("timeMode")).putLong("timetable_imported", System.currentTimeMillis()).putBoolean("demo", false);
        if (!term.equals(prefs.getString("term", ""))) edit.remove("term_start");
        edit.apply();
        destination = "timetable";
        completeQuery("学期课表已导入，本机离线保存");
    }

    private void completeQuery(String message) {
        if ("grades".equals(queryMode)) destination="grades";
        loginModes.remove(queryMode);
        if (!academicMode(queryMode)) {
            loginModes.remove("lighting"); loginModes.remove("aircon"); loginModes.remove("water");
        }
        CookieManager.getInstance().flush(); updateWidgets();
        boolean inBatch = batch;
        finishRefresh("success", message);
        if (!inBatch) { showDashboard(); navigateDestination(); toast(message); }
    }
    private void finishRefresh(String status, String message) {
        if (!refresh.active()) return;
        refresh.stop(); refreshHandler.removeCallbacks(poll);
        syncStatus = status; syncMessage = message;
        if (schoolStatus != null) schoolStatus.setText(message);
        if (school != null) school.stopLoading();
        if ("grades".equals(queryMode)) { gradeStatus=status; gradeMessage=message; }
        if (!academicMode(queryMode)) try { feeStates.put(queryMode, "success".equals(status) ? "已更新" : "login".equals(status) ? "需登录" : "error".equals(status) ? "查询失败" : "已取消"); } catch (Exception ignored) { }
        if (batch) {
            batchResults.add(label(queryMode) + ("success".equals(status) ? "已更新" : "login".equals(status) ? "需登录" : "查询失败（旧结果保留）"));
            if ("login".equals(status)) {
                while (!batchQueue.isEmpty()) {
                    String skipped = batchQueue.removeFirst(); loginModes.add(skipped);
                    try { feeStates.put(skipped, "需登录"); } catch (Exception ignored) { }
                    batchResults.add(label(skipped) + "需登录");
                }
            }
            nextFee(); return;
        }
        render();
    }

    private void leaveSchool() {
        batch = false; batchQueue.clear();
        finishRefresh("idle", "已返回助手，查询结果已保留。");
        CookieManager.getInstance().flush();
        showDashboard();
    }

    static String number(String value) {
        if (value == null || !value.matches("-?\\d{1,9}(?:\\.\\d{1,8})?")) return null;
        try { return new BigDecimal(value).stripTrailingZeros().toPlainString(); } catch (Exception e) { return null; }
    }
    private Object nullable(String s) { return s == null ? JSONObject.NULL : s; }
    private JSONArray history() { try { return new JSONArray(prefs.getString("history", "[]")); } catch(Exception e) { return new JSONArray(); } }
    private JSONObject snapshot() { try { return new JSONObject(prefs.getString("snapshot", "{}")); } catch(Exception e) { return new JSONObject(); } }

    private JSONObject state() {
        JSONObject state = new JSONObject();
        try {
            boolean demo = prefs.getBoolean("demo", false);
            JSONObject current = snapshot();
            state.put("mode", demo ? "demo" : current.has("energy") ? "live" : "empty");
            state.put("current", current); state.put("history", history());
            state.put("lighting", current);
            state.put("aircon", readObjectPref("air_snapshot"));
            state.put("water", readObjectPref("water_snapshot"));
            state.put("gradeTerms", readArrayPref("grade_terms"));
            state.put("gradeResults", readObjectPref("grade_results"));
            state.put("gradeStatus", gradeStatus); state.put("gradeMessage", gradeMessage);
            state.put("timetable", readArrayPref("timetable"));
            state.put("term", prefs.getString("term", ""));
            state.put("termStart", prefs.getString("term_start", ""));
            state.put("timetableNotes", readArrayPref("timetable_notes"));
            state.put("timetableSlots", readArrayPref("timetable_slots"));
            state.put("timeMode", prefs.getString("time_mode", ""));
            state.put("timetableImported", prefs.getLong("timetable_imported", 0));
            state.put("timetableRevision", prefs.getLong("timetable_revision", 0));
            state.put("airHistory", readArrayPref("air_history"));
            state.put("feeStates", feeStates);
            JSONObject air = readObjectPref("air_snapshot");
            state.put("room", current.optString("room", air.optString("room", "")));
            state.put("campus", current.optString("campus", air.optString("campus", "湖北大学")));
            state.put("threshold", Double.parseDouble(prefs.getString("threshold", "10")));
            state.put("syncStatus", syncStatus); state.put("syncMessage", syncMessage);
            state.put("loginRequired", loginModes.contains("lighting") || loginModes.contains("aircon") || loginModes.contains("water"));
            state.put("native", true);
            JSONObject tasks = xxtStore.state(xxtClient.status, xxtClient.message);
            java.util.Iterator<String> taskKeys = tasks.keys();
            while (taskKeys.hasNext()) {String key=taskKeys.next();state.put(key,tasks.get(key));}
            state.put("tasksForeground",!paused&&!appStopped&&(xxtLogin==null||!xxtLogin.visible()));
        } catch (Exception ignored) { }
        return state;
    }
    private void showDashboard() {
        schoolVisible = false;
        if (schoolPanel != null) {
            schoolPanel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            school.setFocusable(false); school.setFocusableInTouchMode(false);
        }
        dashboard.setVisibility(View.VISIBLE); dashboard.bringToFront(); dashboard.requestFocus(); render();
    }
    private JSONObject readObjectPref(String key) {
        try { return new JSONObject(prefs.getString(key, "{}")); } catch (Exception e) { return new JSONObject(); }
    }
    private JSONArray readArrayPref(String key) {
        try { return new JSONArray(prefs.getString(key, "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    private void render() { if (dashboard != null) dashboard.evaluateJavascript("window.receiveState && window.receiveState(" + state().toString() + ")", null); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }

    private void editTimetable(String value) {
        JSONObject result = new JSONObject();
        try {
            if (prefs.getBoolean("demo", false)) throw new Exception("请退出示例模式后修改真实课表");
            if (value.length() > 1500000) throw new Exception("课表数据过大");
            JSONObject request = new JSONObject(value);
            if (request.getLong("revision") != prefs.getLong("timetable_revision", 0) || !request.getString("term").equals(prefs.getString("term", "")) || !sameJson(readArrayPref("timetable"), new JSONArray(request.getString("expected")))) throw new Exception("课表已有变化，请关闭编辑后重试");
            JSONArray next = request.getJSONArray("next");
            TimetableData.validateEdits(next);
            boolean saved = prefs.edit().putString("timetable", next.toString()).putLong("timetable_edited", System.currentTimeMillis()).putLong("timetable_revision", prefs.getLong("timetable_revision",0)+1).commit();
            if (!saved) throw new Exception("本机存储失败，请重试");
            render(); updateWidgets(); result.put("ok", true);
        } catch (Exception e) {
            try { result.put("ok", false); result.put("message", e.getMessage()==null?"保存失败，请重试":e.getMessage()); } catch(Exception ignored) { }
        }
        dashboard.evaluateJavascript("window.courseEditResult&&window.courseEditResult("+result.toString()+")", null);
    }
    private static boolean sameJson(Object a, Object b) throws Exception {
        if (a instanceof JSONObject && b instanceof JSONObject) {
            JSONObject x=(JSONObject)a,y=(JSONObject)b;if(x.length()!=y.length())return false;
            java.util.Iterator<String> keys=x.keys();while(keys.hasNext()){String key=keys.next();if(!y.has(key)||!sameJson(x.get(key),y.get(key)))return false;}return true;
        }
        if (a instanceof JSONArray && b instanceof JSONArray) {
            JSONArray x=(JSONArray)a,y=(JSONArray)b;if(x.length()!=y.length())return false;for(int i=0;i<x.length();i++)if(!sameJson(x.get(i),y.get(i)))return false;return true;
        }
        return a.equals(b);
    }
    private class DashboardBridge {
        @JavascriptInterface public String getState() { return state().toString(); }
        @JavascriptInterface public void action(String name, String value) {
            runOnUiThread(() -> {
                if (!LOCAL_URL.equals(dashboard.getUrl()) || schoolVisible || (xxtLogin!=null&&xxtLogin.visible())) return;
                switch (name) {
                    case "tasksLogin": showXxtLogin(); break;
                    case "tasksSync": if (prefs.getBoolean("demo",false)) toast("请退出示例模式后同步真实待办"); else if(!xxtStore.connected()||"login".equals(xxtClient.status))showXxtLogin();else xxtClient.sync(); break;
                    case "tasksLogout":
                        xxtClient.cancel();xxtClient.invalidateSession();xxtStore.clear(true);clearTaskUiState();XxtLogin.clearCookies();xxtClient.status="idle";xxtClient.message="已退出学习通，本机待办已清除";render();updateWidgets();break;
                    case "tasksClear": xxtClient.cancel();xxtStore.clear(false);clearTaskUiState();xxtClient.status="idle";xxtClient.message="学习通待办缓存已清除";render();updateWidgets();break;
                    case "tasksVisibility":
                        try{JSONObject change=new JSONObject(value);if("course".equals(change.optString("type")))xxtClient.cancel();xxtStore.visibility(change);render();updateWidgets();toast("显示设置已保存");}
                        catch(Exception e){toast("显示设置未保存，请重试");}break;
                    case "tasksReminder":
                        try {JSONObject r=new JSONObject(value);String hw=XxtStore.hours(r.getString("homework")),exam=XxtStore.hours(r.getString("exam"));prefs.edit().putBoolean("xxt_remind",r.getBoolean("enabled")).putString("xxt_hw_hours",hw).putString("xxt_exam_hours",exam).apply();render();toast("应用内提醒设置已保存");}
                        catch(Exception e){toast("请输入 0–720 小时内的正数，逗号分隔，最多 8 个时间");}break;
                    case "tasksWidget": pinWidget(true); break;
                    case "libraryWechat": openLibraryWechat(); break;
                    case "gradeTerms": startGrades("", true); break;
                    case "grades": startGrades(value, false); break;
                    case "timetableEdit": editTimetable(value); break;
                    case "sync":
                        refreshAllFees();
                        break;
                    case "lighting":
                        if (refresh.active()) break;
                        if (loginModes.contains("lighting")) showSchool("lighting"); else refreshMode("lighting");
                        break;
                    case "aircon":
                        if (refresh.active()) break;
                        if (loginModes.contains("aircon")) showSchool("aircon"); else refreshMode("aircon");
                        break;
                    case "water":
                        if (refresh.active()) break;
                        if (loginModes.contains("water")) showSchool("water"); else refreshMode("water");
                        break;
                    case "timetable":
                        if (refresh.active()) break;
                        showSchool("timetable");
                        break;
                    case "termStart":
                        try {
                            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT); format.setLenient(false);
                            java.util.Date date = format.parse(value);
                            java.util.Calendar cal = java.util.Calendar.getInstance(); cal.setTime(date);
                            if (!value.equals(format.format(date)) || cal.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.MONDAY) throw new Exception();
                            prefs.edit().putString("term_start", value).apply(); render(); updateWidgets(); toast("学期日期已保存");
                        } catch (Exception e) { toast("请选择学期第一周的周一日期"); }
                        break;
                    case "school": showSchool(); break;
                    case "demo": xxtClient.cancel();prefs.edit().putBoolean("demo", true).apply(); render(); updateWidgets(); break;
                    case "live": prefs.edit().putBoolean("demo", false).apply(); render(); updateWidgets(); break;
                    case "threshold":
                        try { double n = Double.parseDouble(value); if (Double.isInfinite(n) || Double.isNaN(n) || n<0 || n>1000) throw new Exception(); prefs.edit().putString("threshold", String.valueOf(n)).apply(); render(); updateWidgets(); toast("提醒阈值已保存"); } catch(Exception e) { toast("请输入 0–1000 之间的数值"); } break;
                    case "widget": pinWidget(false); break;
                    case "clear":
                        batch = false; batchQueue.clear();
                        finishRefresh("idle", "");
                        syncStatus = "idle"; syncMessage = "";
                        gradeStatus="idle"; gradeMessage="";
                        xxtClient.cancel();xxtStore.clear(false);clearTaskUiState();xxtClient.status="idle";xxtClient.message="";
                        prefs.edit().remove("snapshot").remove("history").remove("air_snapshot").remove("air_history").remove("water_snapshot").remove("grade_terms").remove("grade_results").remove("timetable").remove("term").remove("term_start").remove("timetable_notes").remove("timetable_slots").remove("time_mode").remove("timetable_imported").remove("timetable_edited").putLong("timetable_revision",prefs.getLong("timetable_revision",0)+1).putBoolean("demo", false).apply(); render(); updateWidgets(); toast("本机记录已清空"); break;
                }
            });
        }
    }
    private void navigateDestination() {
        if (dashboard != null) dashboard.evaluateJavascript("window.openPage&&window.openPage(" + JSONObject.quote(destination) + ")", null);
        destination = "home";
    }
    private void openLibraryWechat() {
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard==null) { toast("无法复制预约链接，请稍后重试"); return; }
        clipboard.setPrimaryClip(ClipData.newPlainText("湖北大学助手 · 图书馆预约",LIBRARY_URL));
        try {
            Intent intent=getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
            if(intent==null) { toast("预约链接已复制。未找到微信，请安装后在微信中打开链接。"); return; }
            startActivity(intent);
            toast("预约链接已复制，请粘贴到微信文件传输助手，发送后点击链接预约。");
        } catch(Exception e) { toast("预约链接已复制，未能打开微信，请手动进入微信打开链接。"); }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        destination = intentPage(intent);
        navigateDestination();
    }
    private static String intentPage(Intent intent) {String p=intent.getStringExtra("page");return "tasks".equals(p)?"tasks":"timetable".equals(p)?"timetable":"home";}
    private void clearTaskUiState() {
        dashboard.evaluateJavascript("(function(){try{Object.keys(localStorage).forEach(function(k){if(k.indexOf('hubu-task-seen:')===0||k.indexOf('hubu-reminder:')===0)localStorage.removeItem(k)})}catch(e){}})()",null);
    }
    private void showXxtLogin() {
        if(prefs.getBoolean("demo",false)){toast("请退出示例模式后连接学习通");return;}
        xxtClient.cancel();xxtClient.invalidateSession();
        if(xxtLogin==null)xxtLogin=new XxtLogin(this,shell,()->{dashboard.bringToFront();render();},()->xxtClient.sync());
        xxtLogin.show();
    }
    private void pinWidget(boolean tasks) {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported()) {
            manager.requestPinAppWidget(new ComponentName(this, tasks?TasksWidgetProvider.class:EnergyWidgetProvider.class), null, null);
        } else toast("请长按手机桌面 → 小组件 → 湖北大学助手");
    }
    private void updateWidgets() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        EnergyWidgetProvider.updateAll(this, manager, manager.getAppWidgetIds(new ComponentName(this, EnergyWidgetProvider.class)));
        TasksWidgetProvider.updateAll(this, manager, manager.getAppWidgetIds(new ComponentName(this, TasksWidgetProvider.class)));
    }
    @Override public void onBackPressed() { if(xxtLogin!=null&&xxtLogin.visible()){xxtLogin.hide();dashboard.bringToFront();render();}else if (schoolVisible) leaveSchool(); else super.onBackPressed(); }
    @Override protected void onPause() {
        super.onPause(); paused = true; refreshHandler.removeCallbacks(poll);
        render();
        if(dashboard!=null) dashboard.onPause(); if(school!=null) school.onPause(); CookieManager.getInstance().flush();
        if(xxtLogin!=null)xxtLogin.pause();
    }
    @Override protected void onResume() {
        super.onResume(); paused = false;
        if(dashboard!=null) dashboard.onResume(); if(school!=null) school.onResume(); schedulePoll(200);
        if(xxtLogin!=null)xxtLogin.resume();
        render();
    }
    @Override protected void onStart() {
        super.onStart();
        if(appStopped) {appStopped=false;if(xxtClient!=null&&xxtStore.connected()&&!prefs.getBoolean("demo",false)&&(xxtLogin==null||!xxtLogin.visible()))xxtClient.sync();}
    }
    @Override protected void onStop() {super.onStop();appStopped=true;if(xxtClient!=null)xxtClient.cancel();}
    @Override protected void onDestroy() {
        destroyed=true;if(xxtClient!=null)xxtClient.close();if(xxtLogin!=null)xxtLogin.destroy();
        refresh.stop(); refreshHandler.removeCallbacks(poll);
        if(dashboard!=null) dashboard.destroy(); if(school!=null) school.destroy(); super.onDestroy();
    }
}





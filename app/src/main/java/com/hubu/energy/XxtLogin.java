package com.hubu.energy;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import java.io.ByteArrayInputStream;

/** Official login has no JavascriptInterface and never reads form credentials. */
final class XxtLogin {
    private static final String LOGIN_TIP="请在学习通官方页面登录，完成后点击「已登录，开始同步」。助手不保存账号密码。";
    private final LinearLayout panel;private final WebView web;private final TextView tip;
    private boolean showing;
    XxtLogin(Activity a,FrameLayout shell,Runnable returned,Runnable sync) {
        panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(Color.WHITE);
        LinearLayout bar=new LinearLayout(a);Button back=new Button(a),done=new Button(a);
        back.setText("返回助手");done.setText("已登录，开始同步");
        back.setOnClickListener(v->{hide();returned.run();});done.setOnClickListener(v->{hide();returned.run();sync.run();});
        bar.addView(back,new LinearLayout.LayoutParams(0,-2,1));bar.addView(done,new LinearLayout.LayoutParams(0,-2,1));panel.addView(bar);
        tip=new TextView(a);tip.setText(LOGIN_TIP);tip.setTextColor(0xff365c60);tip.setPadding(20,12,20,12);panel.addView(tip);
        web=new WebView(a);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSaveFormData(false);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return block(r.getUrl().toString());}
            @Override public boolean shouldOverrideUrlLoading(WebView v,String u){return block(u);}
            boolean block(String u){if(XxtLoginPolicy.allowed(u))return false;tip.setText("此登录跳转暂未支持，请使用学习通官方页面的账号或学校登录方式。");return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                return XxtLoginPolicy.allowed(r.getUrl().toString())?null:new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
            void failed(WebResourceRequest r){
                if(r.isForMainFrame()||XxtLoginPolicy.captcha(r.getUrl().toString()))
                    tip.setText("官方登录页或验证组件加载失败，请检查网络后返回助手并重新打开登录。");
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError error){failed(r);}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){failed(r);}
            @Override public void onPageFinished(WebView v,String url){CookieManager.getInstance().flush();}
        });
        panel.addView(web,new LinearLayout.LayoutParams(-1,0,1));panel.setVisibility(View.GONE);shell.addView(panel,new FrameLayout.LayoutParams(-1,-1));
    }
    void show(){showing=true;tip.setText(LOGIN_TIP);panel.setVisibility(View.VISIBLE);panel.bringToFront();web.loadUrl("https://passport2.chaoxing.com/login?refer=https%3A%2F%2Fi.chaoxing.com%2Fbase");}
    void hide(){showing=false;web.stopLoading();panel.setVisibility(View.GONE);CookieManager.getInstance().flush();}
    boolean visible(){return showing;}
    void pause(){web.onPause();}
    void resume(){web.onResume();}
    void destroy(){web.destroy();}
    static void clearCookies(){
        CookieManager cm=CookieManager.getInstance();
        // Expire only Chaoxing cookies; don't clear school fees / academic SSO sessions.
        for(String host:XxtClient.HOSTS) {
            String raw=cm.getCookie("https://"+host);if(raw==null)continue;
            for(String cookie:raw.split(";")) {
                String name=cookie.split("=",2)[0].trim();if(name.isEmpty())continue;
                for(String domain:new String[]{"", "; Domain="+host,"; Domain=.chaoxing.com","; Domain=chaoxing.com"})
                    cm.setCookie("https://"+host,name+"=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure"+domain);
            }
        }cm.flush();
    }
}

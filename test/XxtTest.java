package com.hubu.energy;

import android.content.SharedPreferences;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

public class XxtTest {
    static void ok(boolean value,String msg){if(!value)throw new AssertionError(msg);}
    interface Attempt{void run()throws Exception;}
    static void rejects(Attempt fn,String msg)throws Exception{try{fn.run();}catch(Exception expected){return;}throw new AssertionError(msg);}
    static SharedPreferences prefs(){
        Map<String,Object> values=new HashMap<>();
        Object edit=Proxy.newProxyInstance(XxtTest.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(p,m,a)->{
            String n=m.getName();if(n.startsWith("put")){values.put((String)a[0],a[1]);return p;}if(n.equals("remove")){values.remove(a[0]);return p;}if(n.equals("apply"))return null;if(n.equals("commit"))return true;return p;
        });
        return (SharedPreferences)Proxy.newProxyInstance(XxtTest.class.getClassLoader(),new Class[]{SharedPreferences.class},(p,m,a)->{
            if(m.getName().equals("edit"))return edit;if(m.getName().equals("getAll"))return new HashMap<>(values);
            if(m.getName().startsWith("get"))return values.getOrDefault(a[0],a[1]);return null;
        });
    }
    public static void main(String[] args)throws Exception {
        long now=XxtStore.millis("2026-12-30T12:00:00+08:00");
        String courses="<ul><li><a href='stucoursemiddle?cpi=3&amp;clazzid=2&amp;courseid=1'><span class='course-name' title='测试课程'>测试课程</span></a></li></ul>";
        JSONArray cs=XxtParser.courses(courses+courses);ok(cs.length()==1,"course dedup");
        JSONObject c=cs.getJSONObject(0);
        String hw="<h1>作业列表</h1><li onclick='goTask(this)' data='https://mooc1.chaoxing.com/work?workId=5'><p class='overHidden2'>题一</p><span class='status'>待批阅</span></li>";
        JSONArray tasks=XxtParser.homework(hw+hw,c);ok(tasks.length()==1&&tasks.getJSONObject(0).getString("status").equals("已完成"),"homework status/dedup");
        ok(XxtParser.homework(hw.replace("待批阅","未完成"),c).getJSONObject(0).getString("status").equals("未完成"),"pending");
        ok(XxtParser.deadline("作答时间：12-30 10:00 至 01-02 23:59 互评时间：01-10 10:00",now).startsWith("2027-01-02"),"cross year");
        ok(XxtParser.deadline("作答时间：2026-12-30 10:00 至 2027-01-02 23:59",now).startsWith("2027-01-02"),"explicit year");
        ok(XxtParser.deadline("暂无截止时间",now).isEmpty(),"unknown time");
        ok(XxtParser.deadline("<h2>作业详情</h2><p>作答时间:10-13 15:34至10-18 15:34</p>",now).contains("10-18T15:34"),"live homework time shape");
        rejects(()->XxtParser.deadline("没有权限",now),"unknown detail preserves previous date");
        ok(XxtParser.date("2026-02-30 10:00",now,false).isEmpty(),"invalid date");
        String exam="<table><tr class='dataTr'><td>1</td><td>《测试课》期中</td><td>2027-01-02 14:00~16:00</td><td>120</td><td>未开始</td><td>未参加</td><td></td><td>在线</td></tr></table>";
        JSONObject ex=XxtParser.exams(exam,now).getJSONObject(0);ok(ex.getString("course").equals("测试课")&&ex.getString("deadline").contains("14:00"),"exam time");
        ok(XxtParser.exams(exam.replace("未参加","已交卷"),now).getJSONObject(0).getString("status").equals("已完成"),"exam done");
        JSONObject notice=XxtParser.notices("{\"notices\":{\"list\":[{\"id\":1,\"title\":\"未读\",\"isread\":\"0\"},{\"id\":2,\"isread\":1}],\"lastGetId\":\"next\",\"lastPage\":false}}");
        ok(notice.getJSONArray("items").length()==1&&!notice.getBoolean("last")&&notice.getString("cursor").equals("next"),"notice pagination/read");
        rejects(()->XxtParser.notices("{\"status\":false}"),"unknown response");
        rejects(()->XxtParser.courses("<h1>服务异常</h1>"),"unknown courses");
        rejects(()->XxtParser.homework("<h1>服务异常</h1>",c),"unknown homework");
        rejects(()->XxtParser.homework("<h1>作业列表</h1><li>页面已改版</li>",c),"title alone is not empty");
        rejects(()->XxtParser.exams("<h1>服务异常</h1>",now),"unknown exams");
        rejects(()->XxtParser.exams("<h1>考试列表</h1><table><tr>页面改版</tr></table>",now),"exam title alone is not empty");
        ok(XxtParser.exams("<table><th>考试名称</th></table>暂无符合条件的考试",now).length()==0,"live empty exams");
        rejects(()->XxtParser.checkSession("https://passport2.chaoxing.com/login",""),"expired");
        rejects(()->XxtParser.checkSession("https://i.chaoxing.com/","操作异常，请输入图片中的验证码"),"captcha");
        for(String url:new String[]{"http://i.chaoxing.com/base","https://i.chaoxing.com.evil.test/","https://evil.test@i.chaoxing.com/","https://i.chaoxing.com:8443/base"})ok(!XxtClient.allowed(url),"domain rejection");
        ok(XxtClient.allowed("https://mooc1.chaoxing.com/work?workId=1"),"allowed https");
        for(String host:XxtLoginPolicy.RESOURCE_HOSTS)ok(XxtLoginPolicy.allowed("https://"+host+"/load.min.js?t=1"),"official login resource");
        ok(XxtLoginPolicy.captcha("https://captcha.chaoxing.com/load.min.js"),"mobile SMS verification loader");
        ok(!XxtClient.allowed("https://captcha.chaoxing.com/load.min.js"),"captcha is login-only");
        for(String url:new String[]{"http://captcha.chaoxing.com/load.min.js","https://captcha.chaoxing.com.evil.test/","https://evil.test@captcha.chaoxing.com/","https://captcha.chaoxing.com:8443/","https://unknown.chaoxing.com/","file:///android_asset/index.html","javascript:alert(1)","not a url"})ok(!XxtLoginPolicy.allowed(url),"login resource rejection");
        XxtStore store=new XxtStore(prefs());store.select("accountA");
        JSONObject t=XxtParser.task("1","homework","旧作业","测试课","未完成").put("deadline","2027-01-02T12:00:00+08:00");
        JSONObject cache=new JSONObject().put("parts",new JSONObject().put("homework:c",new JSONObject().put("items",new JSONArray().put(t).put(t)).put("error","未更新")));
        store.save(cache);JSONArray pending=XxtStore.pending(store.cache(),now);ok(pending.length()==1&&pending.getJSONObject(0).getBoolean("stale"),"partial failure/dedup");
        store.select("accountB");ok(XxtStore.pending(store.cache(),now).length()==0,"account isolation");
        store.select("accountA");ok(XxtStore.pending(store.cache(),now).length()==1,"account cache restore");
        ok(XxtStore.pending(store.cache(),XxtStore.millis("2027-01-03T00:00:00+08:00")).length()==0,"expired removed");
        t.put("status","已完成");store.save(cache);ok(XxtStore.pending(store.cache(),now).length()==0,"completed removed");
        store.clear(true);ok(!store.connected()&&store.account().isEmpty(),"logout clears");
        ok(XxtStore.hours("1，24, 6,1").equals("24, 6, 1"),"settings normalized");rejects(()->XxtStore.hours("0, NaN"),"settings invalid");
        System.out.println("Xxt: courses, homework, deadlines, exams, notices, session, domain validation, partial failure, dedup, account switch, expiry and settings passed");
    }
}

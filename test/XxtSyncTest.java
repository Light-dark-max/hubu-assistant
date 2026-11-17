package com.hubu.energy;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Exercise the production queue/merge/cancellation code with transport fixtures, no network. */
public class XxtSyncTest {
    static class Response extends HttpURLConnection {
        final String body;final int code;final String location;volatile boolean disconnected;
        Response(String url,String body,int code,String location)throws Exception{super(new URL(url));this.body=body;this.code=code;this.location=location;}
        public void connect(){}public void disconnect(){disconnected=true;}public boolean usingProxy(){return false;}
        public int getResponseCode(){return code;}
        public Map<String,List<String>> getHeaderFields(){return Collections.emptyMap();}
        public String getHeaderField(String key){return "Location".equals(key)?location:null;}
        public InputStream getInputStream(){return new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        public OutputStream getOutputStream(){return new ByteArrayOutputStream();}
    }
    static final String COURSE="<div class='course'><a href='stucoursemiddle?courseid=1&amp;clazzid=2&amp;cpi=3'><span class='course-name'>测试课</span></a></div>";
    static class Fixtures implements XxtClient.Connections,XxtClient.Cookies {
        String uid="A",hwStatus="未交",deadline="2099-01-02 12:00",courseSource=COURSE;boolean badHomework,captcha,redirect,signatureExpired;
        int noticePages,homeworkCalls;AtomicInteger requests=new AtomicInteger();CountDownLatch entered,release;
        public String get(String url){return "_uid="+uid;}
        public void set(String url,String value){}
        public HttpURLConnection open(String url)throws Exception {
            requests.incrementAndGet();String body;
            if(url.equals(XxtClient.BASE)) {
                if(entered!=null){entered.countDown();release.await(3,TimeUnit.SECONDS);}
                if(redirect)return new Response(url,"",302,"https://evil.example/");
                body=captcha?"操作异常，请输入图片中的验证码":"var fid='1732';";
            }else if(url.contains("courselistdata"))body=courseSource;
            else if(url.contains("stucoursemiddle"))body="<input id='enc' value='a'><input id='workEnc' value='b'><input id='openc' value='c'>";
            else if(url.contains("/work/list")) {
                homeworkCalls++;
                if(signatureExpired){signatureExpired=false;body="签名校验失败";}
                else body=badHomework?"<h1>作业列表</h1>未知页面":"<li onclick='goTask(this)' data='https://mooc1.chaoxing.com/detail?workId=4'><p class='overHidden2'>测试作业</p><p class='status'>"+hwStatus+"</p></li>";
            }else if(url.contains("/detail?"))body="作业详情 作答时间:2098-12-30 12:00 至 "+deadline;
            else if(url.contains("examlist"))body="考试列表 暂无符合条件的考试";
            else if(url.contains("getNoticeList")) {
                noticePages++;boolean last=noticePages%2==0;
                body="{\"notices\":{\"list\":[{\"id\":\"n\",\"title\":\"通知\",\"isread\":0}],\"lastGetId\":\"next\",\"lastPage\":"+last+"}}";
            }else throw new AssertionError("Unexpected URL: "+url);
            return new Response(url,body,200,null);
        }
    }
    static void sync(XxtClient client)throws Exception {
        client.sync();long until=System.currentTimeMillis()+5000;
        while("refreshing".equals(client.status)&&System.currentTimeMillis()<until)Thread.sleep(5);
        XxtTest.ok(!"refreshing".equals(client.status),"sync timeout");
    }
    public static void main(String[] args)throws Exception {
        XxtStore store=new XxtStore(XxtTest.prefs());Fixtures f=new Fixtures();XxtClient client=new XxtClient(store,()->{},f,f,0);
        try {
            f.signatureExpired=true;sync(client);XxtTest.ok(client.status.equals("success"),"complete sync and signature retry");
            XxtTest.ok(store.cache().optBoolean("baselineReady"),"baseline only after successful round");
            XxtTest.ok(XxtStore.pending(store.cache(),System.currentTimeMillis()).length()==2,"homework + paginated notice dedup");
            f.badHomework=true;sync(client);XxtTest.ok(client.status.equals("partial"),"partial status");
            JSONArray tasks=XxtStore.pending(store.cache(),System.currentTimeMillis());XxtTest.ok(tasks.length()==2,"partial retains old tasks");
            boolean stale=false;for(int i=0;i<tasks.length();i++)if(tasks.getJSONObject(i).optString("kind").equals("homework"))stale=tasks.getJSONObject(i).optBoolean("stale");XxtTest.ok(stale,"old task labelled stale");
            f.badHomework=false;f.hwStatus="已完成";sync(client);XxtTest.ok(XxtStore.pending(store.cache(),System.currentTimeMillis()).length()==1,"completion removes pending and skips detail");
            int before=f.requests.get();f.captcha=true;sync(client);XxtTest.ok(client.status.equals("login")&&f.requests.get()==before+1,"captcha stops requests");
            f.captcha=false;f.redirect=true;before=f.requests.get();sync(client);XxtTest.ok(client.status.equals("error")&&f.requests.get()==before+1,"external redirect never followed");
            f.redirect=false;f.uid="B";f.badHomework=true;sync(client);XxtTest.ok(!store.cache().optBoolean("baselineReady"),"new account partial cannot establish baseline");
            XxtTest.ok(XxtStore.pending(store.cache(),System.currentTimeMillis()).length()==1,"new account cannot read previous homework");
            f.entered=new CountDownLatch(1);f.release=new CountDownLatch(1);client.sync();f.entered.await(2,TimeUnit.SECONDS);before=f.requests.get();client.sync();client.sync();client.cancel();store.clear(true);f.release.countDown();Thread.sleep(100);
            XxtTest.ok(f.requests.get()==before&&!store.connected()&&store.cache().length()==0,"single queue and cancellation cannot resurrect logout cache");
            System.out.println("Xxt sync: real queue, signature retry, partial merge, completion, pagination, captcha stop, redirects, account baseline, dedup and cancellation passed");
        }finally{client.close();}
        XxtStore periodStore=new XxtStore(XxtTest.prefs());Fixtures old=new Fixtures();
        old.courseSource=COURSE.replace("</div>","<p>开课时间：2000-03-01～2099-03-01</p></div>");
        XxtClient periodClient=new XxtClient(periodStore,()->{},old,old,0);
        try {
            sync(periodClient);XxtTest.ok(old.homeworkCalls==1&&periodStore.cache().optBoolean("courseScanComplete"),"first scan visits old course");
            sync(periodClient);XxtTest.ok(old.homeworkCalls==1&&periodStore.cache().optInt("coursesSkipped")==1,"subsequent scan sends no old homework requests");
            XxtTest.ok(periodStore.visible(System.currentTimeMillis()).length()==1,"old cached homework hidden from application/widget");
            periodStore.visibility(new JSONObject().put("type","course").put("id","1:2:3").put("mode","include"));
            sync(periodClient);XxtTest.ok(old.homeworkCalls==2,"manual include resumes sync");
            periodStore.visibility(new JSONObject().put("type","task").put("key","notice:n").put("hidden",true));
            sync(periodClient);XxtTest.ok(periodStore.visible(System.currentTimeMillis()).length()==1,"refresh cannot resurrect manual hidden notice");
            System.out.println("Xxt sync periods: first scan, skipped network, cached visibility, manual include and hidden refresh passed");
        }finally{periodClient.close();}
    }
}

package com.hubu.energy;

import android.webkit.CookieManager;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Single serial read-only queue; no service, alarms or background networking. */
final class XxtClient {
    static final String BASE="https://i.chaoxing.com/base";
    static final Set<String> HOSTS=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "passport2.chaoxing.com","i.chaoxing.com","mooc2-ans.chaoxing.com","mooc1.chaoxing.com","mooc1-api.chaoxing.com","notice.chaoxing.com")));
    interface Listener {void changed();}
    interface Connections {HttpURLConnection open(String url) throws Exception;}
    interface Cookies {String get(String url);void set(String url,String value);}
    private final XxtStore store;private final Listener listener;
    private final Connections connections;private final Cookies cookies;private final long interval;
    private final java.util.concurrent.ExecutorService queue=java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile HttpURLConnection active;
    private volatile int generation;
    private volatile boolean running;
    volatile String status="idle",message="";
    private long lastRequest;
    XxtClient(XxtStore s,Listener l){this(s,l,url->(HttpURLConnection)new URL(url).openConnection(),new Cookies(){
        public String get(String url){return CookieManager.getInstance().getCookie(url);}
        public void set(String url,String value){CookieManager.getInstance().setCookie(url,value);}
    },800);}
    XxtClient(XxtStore s,Listener l,Connections c,Cookies cookies,long interval){store=s;listener=l;connections=c;this.cookies=cookies;this.interval=interval;}
    static boolean allowed(String url) {
        try {URI u=new URI(url);return "https".equalsIgnoreCase(u.getScheme())&&HOSTS.contains(u.getHost())&&(u.getPort()==-1||u.getPort()==443)&&u.getRawUserInfo()==null;}catch(Exception e){return false;}
    }
    synchronized void cancel() {
        generation++;running=false;HttpURLConnection c=active;if(c!=null)c.disconnect();
        if("refreshing".equals(status)){status="idle";message="同步已暂停，已更新的部分和旧记录均已保留";}listener.changed();
    }
    synchronized void sync() {
        if(running)return;running=true;int token=++generation;status="refreshing";message="正在检查学习通登录…";listener.changed();
        queue.execute(()->run(token));
    }
    void close(){cancel();queue.shutdownNow();}
    private final Map<String,JSONObject> signatures=new HashMap<>();
    private String signatureAccount="";
    private volatile int sessionVersion;
    private int cachedSessionVersion;
    void invalidateSession(){sessionVersion++;}
    private void check(int token) throws InterruptedException {if(token!=generation||Thread.currentThread().isInterrupted())throw new InterruptedException();}
    private synchronized void progress(int token,String value) throws Exception {check(token);message=value;listener.changed();}
    private synchronized void save(int token,JSONObject cache) throws Exception {check(token);store.save(cache);listener.changed();}
    private static String hash(String value) throws Exception {
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();
    }
    static String query(String base,String...pairs) throws Exception {
        StringBuilder b=new StringBuilder(base);b.append(base.contains("?")?'&':'?');
        for(int i=0;i<pairs.length;i+=2){if(i>0)b.append('&');b.append(URLEncoder.encode(pairs[i],"UTF-8")).append('=').append(URLEncoder.encode(pairs[i+1],"UTF-8"));}return b.toString();
    }
    private String request(int token,String url,String form) throws Exception {
        for(int redirect=0;redirect<6;redirect++) {
            check(token);if(!allowed(url))throw new Exception("学习通跳转地址未获支持，已停止请求");
            long delay=interval-(System.currentTimeMillis()-lastRequest);if(delay>0)Thread.sleep(delay);check(token);lastRequest=System.currentTimeMillis();
            HttpURLConnection conn=connections.open(url);active=conn;
            conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(15000);conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Referer",url.contains("notice.chaoxing.com")?"https://notice.chaoxing.com/pc/notice/myNotice":BASE);
            String cookie=cookies.get(url);if(cookie!=null)conn.setRequestProperty("Cookie",cookie);
            try {
                if(form!=null){conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");try(OutputStream out=conn.getOutputStream()){out.write(form.getBytes(StandardCharsets.UTF_8));}}
                int code=conn.getResponseCode();check(token);
                synchronized(this){check(token);for(Map.Entry<String,List<String>> h:conn.getHeaderFields().entrySet())if("Set-Cookie".equalsIgnoreCase(h.getKey()))for(String v:h.getValue())cookies.set(url,v);}
                if(code>=300&&code<400) {
                    String location=conn.getHeaderField("Location");if(location==null)throw new Exception("学习通跳转缺少地址");
                    url=new URL(new URL(url),location).toString();if(!allowed(url))throw new Exception("学习通重定向地址未获支持");
                    XxtParser.checkSession(url,"");if(code==301||code==302||code==303)form=null;continue;
                }
                if(code==401)throw new XxtParser.SessionException("学习通登录已失效，请重新登录");
                if(code==403||code==429)throw new XxtParser.SessionException("学习通限制访问，请在官方登录页处理后重试");
                if(code!=200)throw new Exception("学习通返回 HTTP "+code);
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=conn.getInputStream()) {
                    byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){check(token);if(bytes.size()+n>4*1024*1024)throw new Exception("学习通响应过大");bytes.write(buffer,0,n);}
                }
                String html=bytes.toString("UTF-8");XxtParser.checkSession(url,html);return html;
            }finally{conn.disconnect();if(active==conn)active=null;}
        }
        throw new Exception("学习通跳转次数过多");
    }
    private void part(int token,JSONObject cache,String key,JSONArray items,String error) throws Exception {
        JSONObject parts=cache.optJSONObject("parts");if(parts==null){parts=new JSONObject();cache.put("parts",parts);}
        JSONObject p=parts.optJSONObject(key);if(p==null)p=new JSONObject().put("items",new JSONArray());
        if(items!=null)p.put("items",items).put("time",System.currentTimeMillis());
        p.put("error",error);parts.put(key,p);if(items!=null)cache.put("time",System.currentTimeMillis());save(token,cache);
    }
    private static String failure(Exception e){return e.getMessage()==null?"网络或解析失败，旧记录已保留":e.getMessage();}
    private JSONObject signature(int token,JSONObject c,boolean force) throws Exception {
        String id=c.getString("id");JSONObject sig=signatures.get(id);
        if(!force&&sig!=null&&System.currentTimeMillis()-sig.optLong("time")<300000)return sig;
        String page=request(token,query("https://mooc1.chaoxing.com/visit/stucoursemiddle","courseid",c.getString("courseid"),"clazzid",c.getString("clazzid"),"cpi",c.getString("cpi"),"ismooc2","1","v","2"),null);
        sig=new JSONObject();for(String field:new String[]{"enc","workEnc","openc"})sig.put(field,XxtParser.hidden(page,field));
        if(sig.optString("workEnc").isEmpty()||sig.optString("enc").isEmpty())throw new Exception("课程未提供作业签名，旧记录保留");
        sig.put("time",System.currentTimeMillis());signatures.put(id,sig);return sig;
    }
    private JSONArray homework(int token,JSONObject c) throws Exception {
        for(int attempt=0;attempt<2;attempt++) {
            JSONObject sig=signature(token,c,attempt>0);
            String html=request(token,query("https://mooc1.chaoxing.com/mooc2/work/list","courseId",c.getString("courseid"),"classId",c.getString("clazzid"),"cpi",c.getString("cpi"),"ut","s","openc",sig.getString("openc"),"enc",sig.getString("workEnc"),"t",String.valueOf(System.currentTimeMillis()),"stuenc",sig.getString("enc"),"isdisplaytable","2"),null);
            if(html.contains("签名")&&(html.contains("失效")||html.contains("校验失败")||html.contains("过期"))) {if(attempt==0)continue;throw new Exception("课程签名已失效，请稍后重新同步");}
            return XxtParser.homework(html,c);
        }
        throw new Exception("课程签名未更新");
    }
    private void run(int token) {
        int failed=0;JSONObject cache=null;
        try {
            String profile=request(token,BASE,null);
            String fid=XxtParser.match(profile,"[\"']?fid[\"']?\\s*[:=]\\s*[\"']?(\\d+)");
            String cookie=cookies.get(BASE);
            String uid=XxtParser.match(cookie==null?"":cookie,"(?:^|;\\s*)_uid=([^;]+)");
            if(uid.isEmpty())throw new XxtParser.SessionException("未检测到学习通账号，请完成官方登录");
            if(fid.isEmpty())throw new Exception("未识别当前账号学校标识，旧待办已保留");
            synchronized(this){
                check(token);String account=hash(uid);
                if(!account.equals(signatureAccount)||cachedSessionVersion!=sessionVersion){signatures.clear();signatureAccount=account;cachedSessionVersion=sessionVersion;}
                store.select(account);cache=store.cache();listener.changed();
            }
            progress(token,"正在读取课程列表…");
            try {
                JSONArray courses=XxtParser.courses(request(token,query("https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata","courseType","1","courseFid",fid,"courseTeacherid","0","courseOrderBy","0"),null));
                Set<String> current=new HashSet<>();int skipped=0;
                for(int i=0;i<courses.length();i++) {
                    JSONObject c=courses.getJSONObject(i);String key="homework:"+c.getString("id");current.add(key);
                    if(!XxtVisibility.included(c,store.rules(),cache.optBoolean("courseScanComplete"),System.currentTimeMillis())){skipped++;continue;}
                    progress(token,"课程 "+(i+1)+" / "+courses.length()+" · "+c.getString("name"));
                    try {
                        JSONArray tasks=homework(token,c);
                        boolean detailFailed=false;
                        for(int n=0;n<tasks.length();n++) {
                            JSONObject t=tasks.getJSONObject(n);if(XxtParser.done(t.optString("status"))){t.remove("url");continue;}
                            try {
                                String link=t.getString("url");link=new URL(new URL("https://mooc1.chaoxing.com"),link).toString();
                                t.put("deadline",XxtParser.deadline(request(token,link,null),System.currentTimeMillis()));
                            }catch(XxtParser.SessionException|InterruptedException e){throw e;}catch(Exception e){detailFailed=true;restoreDeadline(cache,key,t);t.put("detail","截止详情未更新；请以学习通为准");}
                            t.remove("url");
                        }
                        part(token,cache,key,tasks,detailFailed?"部分作业时间详情未更新":"");if(detailFailed)failed++;
                    }catch(XxtParser.SessionException|InterruptedException e){throw e;}catch(Exception e){failed++;part(token,cache,key,null,failure(e));}
                }
                JSONObject parts=cache.optJSONObject("parts");if(parts!=null){List<String> removed=new ArrayList<>();Iterator<String> keys=parts.keys();while(keys.hasNext()){String k=keys.next();if(k.startsWith("homework:")&&!current.contains(k))removed.add(k);}for(String k:removed)parts.remove(k);save(token,cache);}
                // Every course was attempted. Per-part failures remain recorded independently.
                cache.put("courseScanComplete",true);
                cache.put("coursesSkipped",skipped);part(token,cache,"courses",courses,"");
            }catch(XxtParser.SessionException|InterruptedException e){throw e;}catch(Exception e){failed++;part(token,cache,"courses",null,failure(e));}
            progress(token,"正在读取考试列表…");
            try{part(token,cache,"exams",XxtParser.exams(request(token,"https://mooc1-api.chaoxing.com/exam-ans/exam/test/examcode/examlist",null),System.currentTimeMillis()),"");}
            catch(XxtParser.SessionException|InterruptedException e){throw e;}catch(Exception e){failed++;part(token,cache,"exams",null,failure(e));}
            progress(token,"正在读取未读通知…");
            try {
                JSONArray notices=new JSONArray();String cursor="";Set<String> cursors=new HashSet<>();boolean finished=false;
                for(int n=0;n<20;n++) {
                    progress(token,"正在读取通知 · 第 "+(n+1)+" 页");
                    String form=query("", "type","0","notice_type","0","lastValue",cursor,"sort","0","folderUUID","","kw","","startTime","","endTime","","gKw","","gName","","year","","tag","","fidsCode","","filterSenderPuids","","filterTags","").substring(1);
                    JSONObject page=XxtParser.notices(request(token,"https://notice.chaoxing.com/pc/notice/getNoticeList",form));
                    JSONArray items=page.getJSONArray("items");for(int j=0;j<items.length();j++)notices.put(items.get(j));
                    if(page.getBoolean("last")){finished=true;break;}cursor=page.getString("cursor");if(cursor.isEmpty()||!cursors.add(cursor))throw new Exception("通知分页中断，旧通知已保留");
                }
                if(!finished)throw new Exception("通知超过本轮分页上限，旧通知已保留");
                part(token,cache,"notices",notices,"");
            }catch(XxtParser.SessionException|InterruptedException e){throw e;}catch(Exception e){failed++;part(token,cache,"notices",null,failure(e));}
            synchronized(this){
                check(token);if(failed==0)cache.put("baselineReady",true);
                cache.put("revision",cache.optLong("revision")+1);store.save(cache);
                status=failed==0?"success":"partial";message=failed==0?"同步完成，待办已离线保存":"同步完成；有 "+failed+" 部分未更新，已保留旧记录";
                if(cache.optInt("coursesSkipped")>0)message+=" · 已跳过 "+cache.optInt("coursesSkipped")+" 门往期或手动排除课程";
            }
        }catch(InterruptedException ignored){return;}
        catch(XxtParser.SessionException e){synchronized(this){if(token!=generation)return;status="login";message=e.getMessage();}}
        catch(Exception e){synchronized(this){if(token!=generation)return;status="error";message=failure(e);}}
        finally{synchronized(this){if(token==generation){running=false;listener.changed();}}}
    }
    private static void restoreDeadline(JSONObject cache,String key,JSONObject task) throws Exception {
        JSONObject parts=cache.optJSONObject("parts"),part=parts==null?null:parts.optJSONObject(key);JSONArray old=part==null?null:part.optJSONArray("items");
        if(old!=null)for(int i=0;i<old.length();i++){JSONObject t=old.getJSONObject(i);if(t.optString("id").equals(task.optString("id")))task.put("deadline",t.optString("deadline"));}
    }
}

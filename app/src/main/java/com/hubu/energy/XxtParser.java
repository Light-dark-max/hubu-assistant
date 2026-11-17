package com.hubu.energy;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/** Independent, read-only parsers. An unrecognized response is never an empty result. */
final class XxtParser {
    static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Shanghai");
    static String match(String source, String regex) {
        Matcher m=Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(source);
        return m.find()?m.group(1):"";
    }
    static String param(String url,String key) {
        try {
            String query=new URI(url).getRawQuery();
            if(query!=null) for(String p:query.split("&")) {
                String[] v=p.split("=",2);
                if(URLDecoder.decode(v[0],"UTF-8").equalsIgnoreCase(key)) return v.length>1?URLDecoder.decode(v[1],"UTF-8"):"";
            }
        } catch(Exception ignored) { }
        return "";
    }
    static String hidden(String html,String id) {
        Element e=Jsoup.parse(html).selectFirst("input[id="+id+"],input[name="+id+"]");
        return e==null?"":e.attr("value");
    }
    static void checkSession(String url,String html) throws Exception {
        if(url.contains("antispiderShowVerify") || html.contains("antispiderShowVerify") || html.contains("操作异常，请输入图片中的验证码")) throw new SessionException("学习通要求验证码或安全验证，请重新登录处理");
        Document d=Jsoup.parse(html);
        if(url.contains("passport2.chaoxing.com/login") || d.selectFirst("input[type=password]")!=null || (html.contains("用户登录") && html.contains("fanyalogin"))) throw new SessionException("学习通登录已失效，请重新登录");
    }
    static class SessionException extends Exception { SessionException(String s){super(s);} }
    static JSONArray courses(String html) throws Exception {
        Document d=Jsoup.parse(html); JSONArray out=new JSONArray(); Set<String> seen=new HashSet<>();
        for(Element e:d.select("[href*=stucoursemiddle], [onclick*=stucoursemiddle]")) {
            String raw=e.hasAttr("href")?e.attr("href"):e.attr("onclick");
            String url=raw.contains("stucoursemiddle")?raw.substring(raw.indexOf("stucoursemiddle")):raw;
            url=url.replaceAll("['\"].*$","");
            String cid=param(url,"courseid"),clazz=param(url,"clazzid"),cpi=param(url,"cpi");
            if(cid.isEmpty()||clazz.isEmpty()||cpi.isEmpty()) throw new Exception("课程标识不完整");
            String key=cid+":"+clazz+":"+cpi;if(!seen.add(key))continue;
            Element container=e, name=null;
            for(int n=0;n<4&&container!=null;n++,container=container.parent()) {name=container.selectFirst(".course-name");if(name!=null)break;}
            String title=name==null?e.text():name.hasAttr("title")?name.attr("title"):name.text();
            Element card=e.closest(".course");if(card==null)card=container==null?e:container;
            String info=card.text(),start=match(info,"开课时间[:：]\\s*(\\d{4}-\\d{1,2}-\\d{1,2})");
            out.put(new JSONObject().put("id",key).put("courseid",cid).put("clazzid",clazz).put("cpi",cpi).put("name",title.isEmpty()?"未命名课程":title)
                .put("startDate",start).put("ended",info.contains("课程已结束")));
        }
        if(out.length()==0 && !d.text().matches("(?s).*(暂无课程|没有课程|还没有课程|暂无相关课程).*")) throw new Exception("课程列表格式未识别，旧待办已保留");
        return out;
    }
    static boolean done(String s) {return s.matches("(?s).*(已完成|已提交|待批阅|已批阅|已批改|已交卷|已评分|已互评).*");}
    static JSONObject task(String id,String kind,String title,String course,String status) throws Exception {
        return new JSONObject().put("id",id).put("kind",kind).put("title",title).put("course",course).put("status",done(status)?"已完成":status).put("deadline","");
    }
    static JSONArray homework(String html,JSONObject course) throws Exception {
        Document d=Jsoup.parse(html);JSONArray out=new JSONArray();Set<String> seen=new HashSet<>();
        for(Element e:d.select("li[data]")) {
            if(!e.outerHtml().contains("goTask"))continue;
            String url=e.attr("data");if(url.startsWith("http%"))url=URLDecoder.decode(url,"UTF-8");
            Element name=e.selectFirst(".overHidden2"),status=e.selectFirst(".status");
            if(name==null || name.text().isEmpty())throw new Exception("作业标题格式已变化");
            String wid=param(url,"workId"),title=name.text(),id=course.getString("id")+":"+(wid.isEmpty()?title:wid);
            if(!seen.add(id))continue;
            out.put(task(id,"homework",title,course.getString("name"),status==null?"未完成":status.text()).put("courseId",course.getString("id")).put("url",url));
        }
        if(out.length()==0&&!d.text().matches("(?s).*(暂无作业|没有作业|已完成\\s*0\\s*/\\s*0).*"))throw new Exception("作业列表未识别");
        return out;
    }
    static String date(String text,long now,boolean last) {
        Matcher m=Pattern.compile("(?:(\\d{4})[-/年])?(\\d{1,2})[-/月](\\d{1,2})日?\\s+(\\d{1,2}):(\\d{2})").matcher(text);
        String result="";
        while(m.find()) {
            Calendar today=Calendar.getInstance(ZONE);today.setTimeInMillis(now);long best=0,distance=Long.MAX_VALUE;
            int y=m.group(1)==null?today.get(Calendar.YEAR):Integer.parseInt(m.group(1));
            for(int year=m.group(1)==null?y-1:y;year<=(m.group(1)==null?y+1:y);year++)try {
                Calendar c=Calendar.getInstance(ZONE);c.clear();c.setLenient(false);c.set(year,Integer.parseInt(m.group(2))-1,Integer.parseInt(m.group(3)),Integer.parseInt(m.group(4)),Integer.parseInt(m.group(5)));
                long v=c.getTimeInMillis(),diff=Math.abs(v-now);if(diff<distance){distance=diff;best=v;}
            }catch(Exception ignored){}
            if(best!=0){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.ROOT);f.setTimeZone(ZONE);result=f.format(new Date(best))+"+08:00";if(!last)return result;}
        }
        return result;
    }
    static String deadline(String html,long now) throws Exception {
        String text=Jsoup.parse(html).text();
        if(!text.contains("作业详情")&&!text.contains("作答时间")&&!text.contains("截止时间")&&!text.contains("结束时间"))throw new Exception("作业详情未识别，保留上次截止时间");
        String range=match(text,"(?:作答时间|截止时间|结束时间)[:：]\\s*(.{0,100})");
        // Restrict to the answer range; don't accidentally use a subsequent review period.
        range=range.split("(?:互评时间|批阅时间|得分|满分|题量)")[0];
        return date(range,now,true);
    }
    static JSONArray exams(String html,long now) throws Exception {
        Document d=Jsoup.parse(html);JSONArray out=new JSONArray();Set<String> seen=new HashSet<>();
        for(Element tr:d.select("tr.dataTr")) {
            Elements td=tr.select("td");if(td.size()<8)throw new Exception("考试表格格式已变化");
            String title=td.get(1).text(),id=td.get(0).text()+":"+title,course=match(title,"[《【]([^》】]+)[》】]");
            if(!seen.add(id))continue;
            out.put(task(id,"exam",title,course,td.get(5).text()).put("deadline",date(td.get(2).text(),now,false)).put("detail",td.get(4).text()+" · "+td.get(3).text()+" 分钟 · "+td.get(7).text()));
        }
        if(out.length()==0&&!d.text().matches("(?s).*(暂无考试|没有考试|暂无符合条件的考试).*"))throw new Exception("考试列表未识别");
        return out;
    }
    static JSONObject notices(String json) throws Exception {
        JSONObject payload=new JSONObject(json),p=payload.optJSONObject("notices");
        if(p==null||!(p.opt("list") instanceof JSONArray))throw new Exception("通知响应未识别");
        JSONArray list=p.getJSONArray("list"),out=new JSONArray();
        for(int i=0;i<list.length();i++) {
            JSONObject n=list.getJSONObject(i);Object read=n.opt("isread");
            if(read==null||read==JSONObject.NULL)throw new Exception("通知阅读状态缺失");
            boolean isread=Boolean.TRUE.equals(read)||"1".equals(String.valueOf(read))||"true".equalsIgnoreCase(String.valueOf(read));
            if(isread)continue;
            String title=n.optString("title","未命名通知"),id=n.optString("id",n.optString("noticeId",""));
            if(id.isEmpty())id=title+":"+n.optString("completeTime");
            out.put(task(id,"notice",title,n.optString("createrName","课程通知"),"未读").put("detail",Jsoup.parse(n.optString("content")).text())
                .put("publishedAt",noticeTime(n.opt("completeTime"))));
        }
        if(!p.has("lastPage"))throw new Exception("通知分页标识缺失");
        return new JSONObject().put("items",out).put("last",p.optBoolean("lastPage")||p.optInt("lastPage")==1).put("cursor",p.optString("lastGetId"));
    }
    static String noticeTime(Object value) {
        if(value==null||value==JSONObject.NULL)return "";
        String text=String.valueOf(value).trim();
        try {
            if(text.matches("\\d{10}|\\d{13}")) {
                long millis=Long.parseLong(text);if(text.length()==10)millis*=1000;
                SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.ROOT);f.setTimeZone(ZONE);
                return f.format(new Date(millis))+"+08:00";
            }
            // Never infer a year for notifications: old messages must not look new.
            if(!text.matches("^\\d{4}[-/年].*"))return "";
            if(text.matches("^\\d{4}-\\d{2}-\\d{2}T.*")) {
                if(XxtStore.millis(text)>0)return text;
                text=text.replace('T',' ');
            }
            if(text.matches("^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}$"))text+=" 00:00";
            return date(text,System.currentTimeMillis(),false);
        }catch(Exception e){return "";}
    }
}

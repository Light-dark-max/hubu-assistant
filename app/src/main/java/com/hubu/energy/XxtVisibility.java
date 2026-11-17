package com.hubu.energy;

import org.json.*;
import java.util.*;

/** Pure local display/sync policy. An unknown period stays included. */
final class XxtVisibility {
    static long termStart(long now) {
        Calendar c=Calendar.getInstance(XxtParser.ZONE);c.setTimeInMillis(now);
        int month=c.get(Calendar.MONTH)+1,year=c.get(Calendar.YEAR);
        // July openings commonly prepare the autumn term; January is still autumn.
        int start=month>=7?7:month>=2?2:7;if(month==1)year--;
        c.clear();c.set(year,start-1,1);return c.getTimeInMillis();
    }
    static String termLabel(long now) {
        Calendar c=Calendar.getInstance(XxtParser.ZONE);c.setTimeInMillis(termStart(now));
        return c.get(Calendar.YEAR)+"年"+(c.get(Calendar.MONTH)==6?"秋季":"春季");
    }
    static boolean old(JSONObject course,long now) {
        if(course.optBoolean("ended"))return true;
        String start=course.optString("startDate");
        long time=XxtStore.millis(start+"T00:00:00+08:00");
        return time>0&&time<termStart(now);
    }
    static String mode(JSONObject rules,String id) {
        JSONObject modes=rules.optJSONObject("courseModes");return modes==null?"auto":modes.optString(id,"auto");
    }
    static boolean included(JSONObject course,JSONObject rules,boolean firstScanComplete,long now) {
        String mode=mode(rules,course.optString("id"));
        return "include".equals(mode)||(!"exclude".equals(mode)&&(!firstScanComplete||!old(course,now)));
    }
    static JSONArray courses(JSONObject cache) {
        JSONObject parts=cache.optJSONObject("parts"),part=parts==null?null:parts.optJSONObject("courses");
        JSONArray items=part==null?null:part.optJSONArray("items");return items==null?new JSONArray():items;
    }
    static String key(JSONObject task){return task.optString("kind")+":"+task.optString("id");}
    static String reason(JSONObject task,JSONObject cache,JSONObject rules,long now) {
        JSONObject hidden=rules.optJSONObject("hidden");if(hidden!=null&&hidden.has(key(task)))return "手动隐藏";
        long published=XxtStore.millis(task.optString("publishedAt"));
        if("notice".equals(task.optString("kind"))&&rules.optBoolean("hideOldNotices",true)&&published>0&&published<termStart(now))return "往期通知";
        JSONArray courses=courses(cache);String id=task.optString("courseId"),name=task.optString("course");boolean matched=false;
        for(int i=0;i<courses.length();i++) {
            JSONObject c=courses.optJSONObject(i);if(c==null)continue;
            boolean same=!id.isEmpty()?id.equals(c.optString("id")):!name.isEmpty()&&name.equals(c.optString("name"));
            if(same){matched=true;if(included(c,rules,cache.optBoolean("courseScanComplete"),now))return "";}
        }
        return matched?"已排除课程":"";
    }
    static JSONArray tasks(JSONObject cache,JSONObject rules,long now,boolean suppressed) {
        JSONArray raw=XxtStore.pending(cache,now),out=new JSONArray();
        for(int i=0;i<raw.length();i++)try {
            JSONObject t=raw.getJSONObject(i);String reason=reason(t,cache,rules,now);
            if(suppressed!=reason.isEmpty()){if(suppressed)t.put("hiddenReason",reason);out.put(t);}
        }catch(Exception ignored){}
        return out;
    }
    static JSONArray courseState(JSONObject cache,JSONObject rules,long now) {
        JSONArray raw=courses(cache),out=new JSONArray();
        for(int i=0;i<raw.length();i++)try {
            JSONObject c=new JSONObject(raw.getJSONObject(i).toString());
            String start=c.optString("startDate");long startTime=XxtStore.millis(start+"T00:00:00+08:00");
            c.put("period",c.optBoolean("ended")?"官方标记已结束":startTime>0?termLabel(startTime)+" · 开课 "+start:"时期未知，暂保留");
            c.put("mode",mode(rules,c.optString("id"))).put("included",included(c,rules,cache.optBoolean("courseScanComplete"),now));out.put(c);
        }catch(Exception ignored){}
        return out;
    }
}

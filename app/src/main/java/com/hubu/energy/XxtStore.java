package com.hubu.energy;

import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Account-scoped snapshots, deliberately separate from fees and demo data. */
final class XxtStore {
    final SharedPreferences prefs;
    XxtStore(SharedPreferences p){prefs=p;}
    String account(){return prefs.getString("xxt_account","");}
    boolean connected(){return prefs.getBoolean("xxt_connected",false);}
    JSONObject cache(){try{return new JSONObject(prefs.getString("xxt_cache_"+account(),"{}"));}catch(Exception e){return new JSONObject();}}
    void select(String account) {
        prefs.edit().putString("xxt_account",account).putBoolean("xxt_connected",true).putBoolean("demo",false).apply();
    }
    void save(JSONObject cache){prefs.edit().putString("xxt_cache_"+account(),cache.toString()).apply();}
    JSONObject rules(){try{return new JSONObject(prefs.getString("xxt_rules_"+account(),"{}"));}catch(Exception e){return new JSONObject();}}
    JSONArray visible(long now){return XxtVisibility.tasks(cache(),rules(),now,false);}
    void visibility(JSONObject action) throws Exception {
        JSONObject rules=rules();String type=action.getString("type");
        if("task".equals(type)) {
            String key=action.getString("key");JSONObject hidden=rules.optJSONObject("hidden");if(hidden==null)hidden=new JSONObject();
            if(action.getBoolean("hidden"))hidden.put(key,true);else hidden.remove(key);rules.put("hidden",hidden);
        }else if("course".equals(type)) {
            String id=action.getString("id"),mode=action.getString("mode");
            if(!Arrays.asList("auto","include","exclude").contains(mode))throw new Exception("课程规则无效");
            JSONObject modes=rules.optJSONObject("courseModes");if(modes==null)modes=new JSONObject();
            if("auto".equals(mode))modes.remove(id);else modes.put(id,mode);rules.put("courseModes",modes);
        }else if("oldNotices".equals(type))rules.put("hideOldNotices",action.getBoolean("hidden"));
        else if("restore".equals(type))rules.remove("hidden");
        else throw new Exception("显示操作无效");
        prefs.edit().putString("xxt_rules_"+account(),rules.toString()).apply();
    }
    void clear(boolean logout) {
        SharedPreferences.Editor e=prefs.edit();
        e.putLong("xxt_storage_epoch",prefs.getLong("xxt_storage_epoch",0)+1);
        for(String key:prefs.getAll().keySet())if(key.startsWith("xxt_cache_"))e.remove(key);
        // Display preferences survive cache refresh and logout, scoped to each account.
        if(logout)e.remove("xxt_account").putBoolean("xxt_connected",false);
        e.apply();
    }
    static JSONArray pending(JSONObject cache,long now) {
        JSONArray out=new JSONArray();JSONObject parts=cache.optJSONObject("parts");Set<String> seen=new HashSet<>();
        if(parts==null)return out;
        Iterator<String> keys=parts.keys();while(keys.hasNext()) {
            String key=keys.next();if("courses".equals(key))continue;JSONObject part=parts.optJSONObject(key);if(part==null)continue;
            JSONArray items=part.optJSONArray("items");if(items==null)continue;
            for(int i=0;i<items.length();i++)try {
                JSONObject t=new JSONObject(items.getJSONObject(i).toString());
                long end=millis(t.optString("deadline"));
                if(XxtParser.done(t.optString("status")) || (end>0&&end<=now))continue;
                if(!seen.add(t.optString("kind")+":"+t.optString("id")))continue;
                JSONObject courseState=parts.optJSONObject("courses");
                boolean courseFailed=key.startsWith("homework:")&&courseState!=null&&!courseState.optString("error").isEmpty();
                if(!part.optString("error").isEmpty()||courseFailed)t.put("stale",true).put("detail",t.optString("detail")+" · 本轮未更新，保留上次记录");
                out.put(t);
            }catch(Exception ignored){}
        }
        return out;
    }
    static long millis(String date) {
        try {
            String value=date.endsWith("Z")?date.substring(0,date.length()-1)+"+0000":date.replaceFirst("([+-]\\d{2}):(\\d{2})$","$1$2");
            java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",Locale.ROOT);f.setLenient(false);
            java.text.ParsePosition pos=new java.text.ParsePosition(0);java.util.Date parsed=f.parse(value,pos);
            return parsed!=null&&pos.getIndex()==value.length()?parsed.getTime():0;
        }catch(Exception e){return 0;}
    }
    JSONObject state(String status,String message) {
        JSONObject out=new JSONObject(),cache=cache();
        try {
            long now=System.currentTimeMillis();JSONObject rules=rules();
            out.put("tasks",XxtVisibility.tasks(cache,rules,now,false)).put("tasksSuppressed",XxtVisibility.tasks(cache,rules,now,true));
            out.put("tasksCourses",XxtVisibility.courseState(cache,rules,now)).put("tasksCourseScanComplete",cache.optBoolean("courseScanComplete"));
            out.put("tasksCurrentTerm",XxtVisibility.termLabel(now)).put("tasksHideOldNotices",rules.optBoolean("hideOldNotices",true));
            out.put("tasksAccountId",account()).put("tasksLoggedIn",connected()&&!"login".equals(status));
            out.put("tasksLastSync",cache.optLong("time")).put("tasksSyncStatus",status).put("tasksSyncMessage",message);
            out.put("tasksBaselineReady",cache.optBoolean("baselineReady")).put("tasksCacheRevision",cache.optLong("revision"));
            out.put("tasksStorageEpoch",prefs.getLong("xxt_storage_epoch",0));
            out.put("tasksReminderEnabled",prefs.getBoolean("xxt_remind",true)).put("tasksReminderStart",prefs.getString("xxt_hw_hours","24, 6, 1")).put("tasksReminderEnd",prefs.getString("xxt_exam_hours","24, 1"));
            out.put("tasksParts",cache.optJSONObject("parts")==null?new JSONObject():cache.getJSONObject("parts"));
        }catch(Exception ignored){}
        return out;
    }
    static String hours(String value) throws Exception {
        TreeSet<Double> hours=new TreeSet<>(Collections.reverseOrder());
        for(String s:value.replace('，',',').split(",",-1)) {
            double n=Double.parseDouble(s.trim());if(Double.isNaN(n)||Double.isInfinite(n)||n<=0||n>720)throw new Exception();hours.add(n);
        }
        if(hours.size()>8)throw new Exception();StringBuilder b=new StringBuilder();
        for(double h:hours){if(b.length()>0)b.append(", ");b.append(h==(int)h?String.valueOf((int)h):String.valueOf(h));}return b.toString();
    }
}

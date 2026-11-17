package com.hubu.energy;

import org.json.*;

public class XxtVisibilityTest {
    static void ok(boolean v,String m){XxtTest.ok(v,m);}
    public static void main(String[] args)throws Exception {
        long now=XxtStore.millis("2026-10-27T12:00:00+08:00");
        String link="<a href='stucoursemiddle?courseid=1&amp;clazzid=2&amp;cpi=3'><span class='course-name'>旧课程</span></a>";
        JSONObject course=XxtParser.courses("<div class='course'><div class='cover'>"+link+"</div><div class='course-info'>开课时间：2026-03-08～2028-03-08</div></div>").getJSONObject(0);
        ok(course.getString("startDate").equals("2026-03-08"),"date belongs to full card, not first link parent");
        ok(XxtVisibility.old(course,now),"long availability does not make old course current");
        ok(XxtVisibility.included(course,new JSONObject(),false,now),"first scan includes old course");
        ok(!XxtVisibility.included(course,new JSONObject(),true,now),"later scan excludes old course");
        JSONObject current=new JSONObject(course.toString()).put("startDate","2026-07-08");
        ok(!XxtVisibility.old(current,now),"summer preparation retained for autumn");
        ok(!XxtVisibility.old(new JSONObject().put("id","u"),now),"unknown course period retained");
        ok(XxtVisibility.old(new JSONObject().put("ended",true),now),"official ended course");
        ok(XxtVisibility.termLabel(XxtStore.millis("2027-01-15T00:00:00+08:00")).equals("2026年秋季"),"January belongs to previous autumn");
        ok(XxtVisibility.termLabel(XxtStore.millis("2027-02-01T00:00:00+08:00")).equals("2027年春季"),"spring rollover");
        ok(XxtParser.noticeTime("2024-10-01 08:30:00").startsWith("2024-10-01"),"old notification year retained");
        ok(XxtParser.noticeTime(1727742600000L).startsWith("2024-10-01"),"milliseconds notification");
        ok(XxtParser.noticeTime("10-01 08:30").isEmpty(),"unknown notification year never inferred");
        XxtStore store=new XxtStore(XxtTest.prefs());store.select("A");
        JSONObject task=XxtParser.task("h","homework","旧作业","旧课程","未完成").put("courseId",course.getString("id"));
        JSONObject old=XxtParser.task("n","notice","旧通知","通知","未读").put("publishedAt","2024-10-01T08:30:00+08:00");
        JSONObject unknown=XxtParser.task("u","notice","未知日期通知","通知","未读");
        JSONObject cache=new JSONObject().put("courseScanComplete",true).put("parts",new JSONObject()
            .put("courses",new JSONObject().put("items",new JSONArray().put(course)))
            .put("homework:"+course.getString("id"),new JSONObject().put("items",new JSONArray().put(task)))
            .put("notices",new JSONObject().put("items",new JSONArray().put(old).put(unknown))));store.save(cache);
        ok(XxtVisibility.tasks(cache,store.rules(),now,false).length()==1,"old course + notice filtered in shared policy");
        store.visibility(new JSONObject().put("type","course").put("id",course.getString("id")).put("mode","include"));
        ok(XxtVisibility.tasks(cache,store.rules(),now,false).length()==2,"manual course reinclude");
        store.visibility(new JSONObject().put("type","oldNotices").put("hidden",false));
        store.visibility(new JSONObject().put("type","task").put("key","notice:u").put("hidden",true));
        store.save(cache);ok(XxtVisibility.tasks(store.cache(),store.rules(),now,true).length()==1,"hidden persists after sync cache replacement");
        store.select("B");ok(store.rules().length()==0,"display rules account isolation");store.select("A");
        store.clear(false);store.save(cache);ok(store.rules().optJSONObject("hidden").has("notice:u"),"cache clear preserves display choices");
        store.visibility(new JSONObject().put("type","restore"));ok(XxtVisibility.tasks(cache,store.rules(),now,false).length()==3,"restore manual hidden");
        System.out.println("Xxt visibility: periods, first/later scans, notification dates, hide/restore, cache persistence and account isolation passed");
    }
}

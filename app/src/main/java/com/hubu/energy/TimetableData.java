package com.hubu.energy;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

final class TimetableData {
    static int color(JSONObject course) {
        String color = course.optString("color");
        if (color.matches("#[0-9a-fA-F]{6}")) return android.graphics.Color.parseColor(color);
        int[] palette = {0xffbd76b2,0xff4cabc7,0xffcd9b3f,0xffb7a346,0xffd28d73,0xffab75c3};
        return palette[(int) ((course.optString("name").hashCode() & 0xffffffffL) % palette.length)];
    }
    static void validateEdits(JSONArray courses) throws Exception {
        if (courses.length() > 500) throw new Exception("课程安排不能超过 500 条");
        for (int i=0; i<courses.length(); i++) {
            JSONObject c = courses.getJSONObject(i);
            if (c.optString("name").trim().isEmpty() || c.optString("name").length()>100 || c.optString("teacher").length()>100 || c.optString("room").length()>100) throw new Exception("课程名称、教师或教室不正确");
            double day=c.optDouble("weekday",0);
            if (day<1 || day>7 || day!=Math.floor(day)) throw new Exception("星期不正确");
            if (c.has("color") && !c.optString("color").matches("#[0-9a-fA-F]{6}")) throw new Exception("课程颜色不正确");
            String sections=c.optString("sections");
            if (!sections.matches("\\d{1,2}(?:-\\d{1,2}){0,15}")) throw new Exception("节次不正确");
            int previous=0;
            for (String part:sections.split("-")) { int n=Integer.parseInt(part); if(n<1||n>16||previous>0&&n!=previous+1)throw new Exception("节次必须连续且在 1–16 节之间"); previous=n; }
            String rule=c.optString("weeks").replaceAll("\\s", "").replace('，',',').replace('、',',').replace('－','-').replace('—','-').replace('~','-').replace('至','-').replaceAll("[（(]([单双])周[）)]", "$1周");
            if (!rule.matches("\\d+(?:-\\d+)?(?:,\\d+(?:-\\d+)?)*(?:单周|双周)?")) throw new Exception("周次格式不正确");
            for (String part:rule.replaceAll("[单双]周$", "").split(",")) { String[] ends=part.split("-"); int first=Integer.parseInt(ends[0]),last=Integer.parseInt(ends[ends.length-1]); if(first<1||last>30||first>last)throw new Exception("周次必须在 1–30 之间"); }
            boolean any=false;for(int w=1;w<=30;w++)if(inWeek(rule,w)){any=true;break;}if(!any)throw new Exception("所选周次没有上课周");
        }
    }
    static int week(String start, Calendar today) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT); f.setLenient(false);
            Calendar first = Calendar.getInstance(); first.setTime(f.parse(start));
            if (first.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) return 0;
            Calendar a = new GregorianCalendar(TimeZone.getTimeZone("UTC")), b = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            a.clear(); b.clear();
            a.set(first.get(Calendar.YEAR), first.get(Calendar.MONTH), first.get(Calendar.DAY_OF_MONTH));
            b.set(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH));
            long days = (b.getTimeInMillis() - a.getTimeInMillis()) / 86400000L;
            return days < 0 ? -1 : (int) (days / 7) + 1;
        } catch (Exception e) { return 0; }
    }
    static boolean inWeek(String rule, int week) {
        if (week < 1) return false;
        if (rule.contains("单") && week % 2 == 0 || rule.contains("双") && week % 2 != 0) return false;
        Matcher m = Pattern.compile("(\\d+)(?:\\s*[-－—~至]\\s*(\\d+))?").matcher(rule);
        while (m.find()) {
            int first = Integer.parseInt(m.group(1)), last = m.group(2) == null ? first : Integer.parseInt(m.group(2));
            if (week >= first && week <= last) return true;
        }
        return false;
    }
    static int section(JSONObject course, boolean last) {
        Matcher m = Pattern.compile("\\d+").matcher(course.optString("sections"));
        int n = 0;
        while (m.find()) { n = Integer.parseInt(m.group()); if (!last) return n; }
        return n;
    }
    static List<JSONObject> today(SharedPreferences prefs, Calendar date, int week) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray courses = new JSONArray(prefs.getString("timetable", "[]"));
            int weekday = (date.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
            for (int i = 0; i < courses.length(); i++) {
                JSONObject c = courses.getJSONObject(i);
                if (c.optInt("weekday") == weekday && inWeek(c.optString("weeks"), week)) out.add(c);
            }
            Collections.sort(out, (a,b) -> Integer.compare(section(a,false),section(b,false)));
        } catch (Exception ignored) { }
        return out;
    }
    static String time(SharedPreferences prefs, JSONObject course, boolean end) {
        int target = section(course, end);
        try {
            JSONArray slots = new JSONArray(prefs.getString("timetable_slots", "[]"));
            for (int i = 0; i < slots.length(); i++) {
                JSONObject slot = slots.getJSONObject(i); JSONArray sections = slot.getJSONArray("sections");
                if (sections.length() > 0 && target == sections.getInt(end ? sections.length()-1 : 0)) {
                    String value = slot.optString(end ? "end" : "start"); if (!value.isEmpty()) return value;
                }
            }
        } catch (Exception ignored) { }
        return "第" + target + "节";
    }
}

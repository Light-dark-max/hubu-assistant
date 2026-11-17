package com.hubu.energy;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONObject;
import java.util.*;

public class EnergyWidgetProvider extends AppWidgetProvider {
    static final String PREFS = "energy_data";
    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (Intent.ACTION_DATE_CHANGED.equals(intent.getAction()) || Intent.ACTION_TIME_CHANGED.equals(intent.getAction()) || Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            updateAll(context, manager, manager.getAppWidgetIds(new android.content.ComponentName(context, EnergyWidgetProvider.class)));
        }
    }
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) { updateAll(context, manager, ids); }
    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) { updateAll(context,manager,new int[]{id}); }
    static void updateAll(Context context, AppWidgetManager manager, int[] ids) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Calendar now = Calendar.getInstance();
        int week = TimetableData.week(prefs.getString("term_start", ""), now);
        List<JSONObject> courses = TimetableData.today(prefs, now, week);
        String day = new String[]{"周日","周一","周二","周三","周四","周五","周六"}[now.get(Calendar.DAY_OF_WEEK)-1];
        PendingIntent open = PendingIntent.getActivity(context, 71, new Intent(context, MainActivity.class).putExtra("page", "timetable").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_energy);
            views.setTextViewText(R.id.widget_title, "▣ 今天 / " + day);
            views.setTextViewText(R.id.widget_week, week > 0 ? "第" + week + "周" : "学期课表");
            String empty = prefs.getString("timetable", "[]").equals("[]") ? "点击导入学期课表" : week == 0 ? "请先设置第一周的周一日期" : week < 0 ? "学期尚未开始" : courses.isEmpty() ? "今天没有已安排的课程" : "";
            views.setTextViewText(R.id.widget_empty, empty);
            views.setViewVisibility(R.id.widget_empty, empty.isEmpty() ? View.GONE : View.VISIBLE);
            views.removeAllViews(R.id.widget_courses);
            int height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 300);
            int limit = Math.max(1, Math.min(6, (height-45)/84));
            for (int i=0; i<Math.min(limit,courses.size()); i++) {
                JSONObject course = courses.get(i);
                RemoteViews row = new RemoteViews(context.getPackageName(),R.layout.widget_course);
                row.setTextViewText(R.id.course_start,TimetableData.time(prefs,course,false));
                row.setTextViewText(R.id.course_end,TimetableData.time(prefs,course,true));
                row.setTextViewText(R.id.course_name,course.optString("name"));
                row.setTextViewText(R.id.course_detail,"第"+course.optString("sections")+"节 | "+course.optString("room")+" | "+course.optString("teacher"));
                int[] vivid={0xff19a8dc,0xffe0b51a,0xffec59b8,0xffb969df};
                int accent=course.optString("color").matches("#[0-9a-fA-F]{6}")?TimetableData.color(course):vivid[i%vivid.length];
                row.setInt(R.id.course_color,"setBackgroundColor",accent);
                views.addView(R.id.widget_courses,row);
            }
            views.setTextViewText(R.id.widget_more,courses.size()>limit?"另有 "+(courses.size()-limit)+" 门课程":"");
            views.setViewVisibility(R.id.widget_more,courses.size()>limit?View.VISIBLE:View.GONE);
            views.setOnClickPendingIntent(R.id.widget_root,open);
            manager.updateAppWidget(id,views);
        }
    }
}

package com.hubu.energy;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Cache only: Android periodically redraws; this receiver never connects to the network. */
public class TasksWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){updateAll(c,m,ids);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle b){updateAll(c,m,new int[]{id});}
    @Override public void onReceive(Context c,Intent i) {
        super.onReceive(c,i);String a=i.getAction();
        if(Intent.ACTION_DATE_CHANGED.equals(a)||Intent.ACTION_TIME_CHANGED.equals(a)||Intent.ACTION_TIMEZONE_CHANGED.equals(a)||Intent.ACTION_BOOT_COMPLETED.equals(a)) {
            AppWidgetManager m=AppWidgetManager.getInstance(c);updateAll(c,m,m.getAppWidgetIds(new ComponentName(c,TasksWidgetProvider.class)));
        }
    }
    static void updateAll(Context c,AppWidgetManager manager,int[] ids) {
        if(ids.length==0)return;
        XxtStore store=new XxtStore(c.getSharedPreferences(EnergyWidgetProvider.PREFS,Context.MODE_PRIVATE));JSONObject cache=store.cache();
        JSONArray pending=store.visible(System.currentTimeMillis());List<JSONObject> tasks=new ArrayList<>();
        for(int i=0;i<pending.length();i++)if(pending.optJSONObject(i)!=null)tasks.add(pending.optJSONObject(i));
        Collections.sort(tasks,(a,b)->Long.compare(order(a),order(b)));
        PendingIntent open=PendingIntent.getActivity(c,72,new Intent(c,MainActivity.class).putExtra("page","tasks").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        for(int id:ids) {
            RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_tasks);
            v.setTextViewText(R.id.tasks_widget_count,tasks.size()+" 项待办");
            v.setTextViewText(R.id.tasks_widget_empty,store.connected()?"暂无待办 · 打开助手后同步":"点击打开助手，连接学习通");
            v.setViewVisibility(R.id.tasks_widget_empty,tasks.isEmpty()?View.VISIBLE:View.GONE);v.removeAllViews(R.id.tasks_widget_rows);
            int height=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,250),limit=Math.max(1,Math.min(6,(height-65)/76));
            for(int n=0;n<Math.min(limit,tasks.size());n++) {
                JSONObject t=tasks.get(n);RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_task);
                String kind=t.optString("kind"),label="exam".equals(kind)?"考试":"notice".equals(kind)?"通知":"作业";
                row.setTextViewText(R.id.task_widget_kind,label);row.setTextColor(R.id.task_widget_kind,"exam".equals(kind)?0xffb15b13:"notice".equals(kind)?0xff9864c1:0xff008b89);
                row.setTextViewText(R.id.task_widget_title,t.optString("title"));
                row.setTextViewText(R.id.task_widget_detail,t.optString("course")+" · "+(t.optBoolean("stale")?"未更新":t.optString("status","待处理")));
                row.setTextViewText(R.id.task_widget_time,"notice".equals(kind)?"未读":remaining(t.optString("deadline"),"exam".equals(kind)));
                v.addView(R.id.tasks_widget_rows,row);
            }
            long time=cache.optLong("time");SimpleDateFormat format=new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA);
            v.setTextViewText(R.id.tasks_widget_cache,(time>0?"同步于 "+format.format(new Date(time)):"尚未同步")+" · 本机缓存"+(tasks.size()>limit?" · 另有 "+(tasks.size()-limit)+" 项":""));
            v.setOnClickPendingIntent(R.id.tasks_widget_root,open);manager.updateAppWidget(id,v);
        }
    }
    private static long order(JSONObject t){long v=XxtStore.millis(t.optString("deadline"));return v==0?Long.MAX_VALUE:v;}
    static String remaining(String value,boolean exam) {
        long time=XxtStore.millis(value),delta=time-System.currentTimeMillis();if(time==0)return "时间未知";
        String label=delta<3600000?"不足1小时":delta<86400000?(long)Math.ceil(delta/3600000d)+"小时":(long)Math.ceil(delta/86400000d)+"天";
        return label+(exam?"开考":"截止");
    }
}

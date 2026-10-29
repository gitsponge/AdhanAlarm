package islam.adhanalarm.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;

import java.text.DateFormat;
import java.util.HashMap;
import java.util.Map;

import islam.adhanalarm.App;
import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.MainActivity;
import islam.adhanalarm.Prefs;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.ScheduleHandler;

/**
 * The next prayer and its time: wide, or a small tile from 2 by 1
 */
public class NextNotificationWidgetProvider extends AppWidgetProvider {

    static final int WIDE_DP = 250;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        updateAppWidget(context, appWidgetManager, appWidgetId); // Resized
    }

    public static void updateAll(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        for (int appWidgetId : appWidgetManager.getAppWidgetIds(new ComponentName(context, NextNotificationWidgetProvider.class))) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The launcher picks the layout that fits as the widget is resized
            Map<SizeF, RemoteViews> layouts = new HashMap<>();
            layouts.put(new SizeF(110, 40), build(context, false));
            layouts.put(new SizeF(WIDE_DP, 40), build(context, true));
            views = new RemoteViews(layouts);
        } else {
            int width = appWidgetManager.getAppWidgetOptions(appWidgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, WIDE_DP);
            views = build(context, width >= WIDE_DP);
        }
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static RemoteViews build(Context context, boolean wide) {
        SharedPreferences settings = App.getSettings(context);
        ScheduleHandler scheduleHandler = new ScheduleHandler(settings);
        DateFormat timeFormat = Prefs.getTimeFormat(context, settings);
        short next = scheduleHandler.getNextTimeIndex();

        RemoteViews views = new RemoteViews(context.getPackageName(), wide ? R.layout.widget_next_prayer : R.layout.widget_next_prayer_small);
        views.setOnClickPendingIntent(R.id.widget, PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        views.setTextViewText(R.id.widget_name, context.getString(CONSTANT.TIME_NAMES[next]));
        views.setTextViewText(R.id.widget_time, timeFormat.format(scheduleHandler.getTime(next).getTime()));
        if (wide) {
            views.setTextViewText(R.id.widget_label, context.getString(next == CONSTANT.SUNRISE ? R.string.fajr_ends : R.string.next_prayer));
            boolean arabic = Prefs.showArabicNames(context, settings);
            views.setViewVisibility(R.id.widget_arabic, arabic ? View.VISIBLE : View.GONE);
            views.setTextViewText(R.id.widget_arabic, context.getString(CONSTANT.ARABIC_NAMES[next]));
        }
        return views;
    }
}

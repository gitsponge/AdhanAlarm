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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import islam.adhanalarm.App;
import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.MainActivity;
import islam.adhanalarm.Prefs;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.ScheduleHandler;

/**
 * The next prayer and its time: wide, a small tile from 2 by 1, or a 1 by 1 tile
 */
public class NextNotificationWidgetProvider extends AppWidgetProvider {

    static final int WIDE_DP = 250;
    private static final int SMALL_DP = 110;
    private static final int TINY = 0, SMALL = 1, WIDE = 2;

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
            layouts.put(new SizeF(40, 40), build(context, TINY));
            layouts.put(new SizeF(SMALL_DP, 40), build(context, SMALL));
            layouts.put(new SizeF(WIDE_DP, 40), build(context, WIDE));
            views = new RemoteViews(layouts);
        } else {
            int width = appWidgetManager.getAppWidgetOptions(appWidgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, WIDE_DP);
            views = build(context, width >= WIDE_DP ? WIDE : width >= SMALL_DP ? SMALL : TINY);
        }
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static RemoteViews build(Context context, int size) {
        SharedPreferences settings = App.getSettings(context);
        ScheduleHandler scheduleHandler = new ScheduleHandler(settings);
        DateFormat timeFormat = Prefs.getTimeFormat(context, settings);
        short next = scheduleHandler.getNextTimeIndex();

        int layout = size == WIDE ? R.layout.widget_next_prayer : size == SMALL ? R.layout.widget_next_prayer_small : R.layout.widget_next_prayer_tiny;
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);
        views.setOnClickPendingIntent(R.id.widget, PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        views.setTextViewText(R.id.widget_name, context.getString(CONSTANT.TIME_NAMES[next]));
        Date time = scheduleHandler.getTime(next).getTime();
        if (size == TINY) {
            String[] clockAndPeriod = splitTime(timeFormat, time);
            views.setTextViewText(R.id.widget_time, clockAndPeriod[0]);
            views.setTextViewText(R.id.widget_period, clockAndPeriod[1]);
            views.setViewVisibility(R.id.widget_period, clockAndPeriod[1].isEmpty() ? View.GONE : View.VISIBLE);
        } else {
            views.setTextViewText(R.id.widget_time, timeFormat.format(time));
        }
        if (size == WIDE) {
            views.setTextViewText(R.id.widget_label, context.getString(next == CONSTANT.SUNRISE ? R.string.fajr_ends : R.string.next_prayer));
            boolean arabic = Prefs.showArabicNames(context, settings);
            views.setViewVisibility(R.id.widget_arabic, arabic ? View.VISIBLE : View.GONE);
            views.setTextViewText(R.id.widget_arabic, context.getString(CONSTANT.ARABIC_NAMES[next]));
        }
        return views;
    }

    /**
     * "1:01" and "p.m." apart, so the 1 by 1 tile can set them on two lines. In 24-hour time, or a
     * pattern this can't take apart, the time comes back whole with no period.
     */
    static String[] splitTime(DateFormat format, Date time) {
        if (format instanceof SimpleDateFormat) {
            SimpleDateFormat clock = (SimpleDateFormat) format.clone();
            String pattern = clock.toPattern();
            if (pattern.indexOf('a') >= 0 && pattern.indexOf('\'') < 0) {
                try {
                    clock.applyPattern(pattern.replaceAll("[\\s\u00A0\u202F]*a+[\\s\u00A0\u202F]*", "").trim());
                    SimpleDateFormat period = (SimpleDateFormat) format.clone();
                    period.applyPattern("a");
                    return new String[]{clock.format(time), period.format(time)};
                } catch (IllegalArgumentException ex) {
                    // Falls through to the whole time
                }
            }
        }
        return new String[]{format.format(time), ""};
    }
}

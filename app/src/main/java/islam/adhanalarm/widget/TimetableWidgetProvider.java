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
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;
import androidx.core.os.ConfigurationCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import islam.adhanalarm.App;
import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.MainActivity;
import islam.adhanalarm.Prefs;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.ScheduleHandler;

/**
 * Today's times: a grid of six from 4 by 2, a list in a 2 by 2
 */
public class TimetableWidgetProvider extends AppWidgetProvider {

    private static final int[] CELLS = {R.id.cell0, R.id.cell1, R.id.cell2, R.id.cell3, R.id.cell4, R.id.cell5};
    private static final int[] CELL_NAMES = {R.id.cell_name0, R.id.cell_name1, R.id.cell_name2, R.id.cell_name3, R.id.cell_name4, R.id.cell_name5};
    private static final int[] CELL_TIMES = {R.id.cell_time0, R.id.cell_time1, R.id.cell_time2, R.id.cell_time3, R.id.cell_time4, R.id.cell_time5};
    private static final int[] ROWS = {R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5};
    private static final int[] ROW_NAMES = {R.id.row_name0, R.id.row_name1, R.id.row_name2, R.id.row_name3, R.id.row_name4, R.id.row_name5};
    private static final int[] ROW_TIMES = {R.id.row_time0, R.id.row_time1, R.id.row_time2, R.id.row_time3, R.id.row_time4, R.id.row_time5};

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
        for (int appWidgetId : appWidgetManager.getAppWidgetIds(new ComponentName(context, TimetableWidgetProvider.class))) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Map<SizeF, RemoteViews> layouts = new HashMap<>();
            layouts.put(new SizeF(110, 110), build(context, false));
            layouts.put(new SizeF(NextNotificationWidgetProvider.WIDE_DP, 110), build(context, true));
            views = new RemoteViews(layouts);
        } else {
            int width = appWidgetManager.getAppWidgetOptions(appWidgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, NextNotificationWidgetProvider.WIDE_DP);
            views = build(context, width >= NextNotificationWidgetProvider.WIDE_DP);
        }
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static RemoteViews build(Context context, boolean grid) {
        SharedPreferences settings = App.getSettings(context);
        ScheduleHandler scheduleHandler = new ScheduleHandler(settings);
        DateFormat timeFormat = Prefs.getTimeFormat(context, settings);
        short next = scheduleHandler.getNextTimeIndex();
        boolean tomorrow = next == CONSTANT.NEXT_FAJR; // After isha the next prayer is tomorrow's fajr, which takes fajr's place
        if (tomorrow) next = CONSTANT.FAJR;
        Calendar now = Calendar.getInstance();

        RemoteViews views = new RemoteViews(context.getPackageName(), grid ? R.layout.widget_today : R.layout.widget_today_small);
        views.setOnClickPendingIntent(R.id.widget, PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        if (grid) {
            Locale locale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
            if (locale == null) locale = Locale.getDefault();
            views.setTextViewText(R.id.widget_date, new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(new Date()));
            views.setTextViewText(R.id.widget_hijri, scheduleHandler.getHijriDate(context));
        }
        for (short i = CONSTANT.FAJR; i <= CONSTANT.ISHAA; i++) {
            boolean isNext = i == next;
            short timeIndex = isNext && tomorrow ? CONSTANT.NEXT_FAJR : i;
            boolean isPast = !isNext && scheduleHandler.getTime(i).before(now);
            int name = grid ? CELL_NAMES[i] : ROW_NAMES[i];
            int time = grid ? CELL_TIMES[i] : ROW_TIMES[i];
            views.setTextViewText(name, context.getString(CONSTANT.TIME_NAMES[i]));
            views.setTextViewText(time, timeFormat.format(scheduleHandler.getTime(timeIndex).getTime()));
            views.setInt(grid ? CELLS[i] : ROWS[i], "setBackgroundResource", isNext ? (grid ? R.drawable.widget_cell_next : R.drawable.widget_row_next) : 0);
            setTextColor(context, views, name, isNext ? R.color.on_primary_container : isPast ? R.color.past : R.color.on_surface_variant);
            setTextColor(context, views, time, isNext ? R.color.on_primary_container : isPast ? R.color.past : R.color.on_surface);
        }
        return views;
    }

    /**
     * On Android 12+ the launcher picks the light or dark colour itself, so the widget follows its theme
     */
    private static void setTextColor(Context context, RemoteViews views, int viewId, int colour) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(viewId, "setTextColor", colour);
        } else {
            views.setTextColor(viewId, ContextCompat.getColor(context, colour));
        }
    }
}

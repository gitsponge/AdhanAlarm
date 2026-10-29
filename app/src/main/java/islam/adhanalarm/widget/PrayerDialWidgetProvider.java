package islam.adhanalarm.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.util.Calendar;

import islam.adhanalarm.App;
import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.MainActivity;
import islam.adhanalarm.Prefs;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.ScheduleHandler;

/**
 * Today's prayers as a 24-hour sun dial, set out like an app icon. Its hand moves every quarter of an hour,
 * but only while the phone is awake, so the dial costs no battery overnight.
 */
public class PrayerDialWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_TICK = "islam.adhanalarm.action.DIAL_TICK";
    private static final int REQUEST_TICK = 10;
    private static final int DEFAULT_DP = 60, MAX_DP = 240;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_TICK.equals(intent.getAction())) {
            updateAll(context);
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
        scheduleTicks(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        updateAppWidget(context, appWidgetManager, appWidgetId); // Resized
    }

    @Override
    public void onEnabled(Context context) {
        scheduleTicks(context);
    }

    @Override
    public void onDisabled(Context context) {
        ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).cancel(tick(context));
    }

    public static void updateAll(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, PrayerDialWidgetProvider.class));
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
        if (appWidgetIds.length > 0) {
            scheduleTicks(context); // Again after a restart, which clears alarms
        }
    }

    private static void scheduleTicks(Context context) {
        ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setInexactRepeating(AlarmManager.RTC,
                System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES, AlarmManager.INTERVAL_FIFTEEN_MINUTES, tick(context));
    }

    private static PendingIntent tick(Context context) {
        Intent intent = new Intent(context, PrayerDialWidgetProvider.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(context, REQUEST_TICK, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SharedPreferences settings = App.getSettings(context);
        ScheduleHandler schedule = new ScheduleHandler(settings);

        // The dial sits where the launcher puts an app icon, which takes about 74% of a cell's width and 63% of its height
        Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
        int widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        float dialDp = widthDp > 0 && heightDp > 0 ? Math.min(widthDp * 0.74f, heightDp * 0.63f) : DEFAULT_DP;
        int sizePx = Math.round(Math.max(40, Math.min(MAX_DP, dialDp)) * context.getResources().getDisplayMetrics().density);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_dial);
        views.setImageViewBitmap(R.id.dial, PrayerDial.draw(context, schedule, Calendar.getInstance(), sizePx));
        views.setOnClickPendingIntent(android.R.id.background, PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));

        short next = schedule.getNextTimeIndex();
        String time = Prefs.getTimeFormat(context, settings).format(schedule.getTime(next).getTime());
        // Read out as the next prayer and the Islamic date, which the moon shows
        String separator = context.getString(R.string.list_separator);
        views.setContentDescription(android.R.id.background, context.getString(R.string.next_prayer) + separator
                + context.getString(R.string.name_and_time, context.getString(CONSTANT.TIME_NAMES[next]), time)
                + separator + schedule.getHijriDate(context));
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}

package islam.adhanalarm.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import androidx.preference.PreferenceManager;

import islam.adhanalarm.MainActivity;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.ScheduleHandler;

public class NextNotificationWidgetProvider extends AppWidgetProvider {

    private static final int[] labels = new int[]{ R.string.fajr, R.string.sunrise, R.string.dhuhr, R.string.asr, R.string.maghrib, R.string.ishaa, R.string.next_fajr };

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    public static void updateAll(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

        ComponentName thisAppWidgetComponentName = new ComponentName(context, NextNotificationWidgetProvider.class);
        for (int appWidgetId : appWidgetManager.getAppWidgetIds(thisAppWidgetComponentName)) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private static void updateAppWidget(final Context context, final AppWidgetManager appWidgetManager, int appWidgetId) {

        final ScheduleHandler scheduleHandler = new ScheduleHandler(PreferenceManager.getDefaultSharedPreferences(context));

        final short nextTimeIndex = scheduleHandler.getNextTimeIndex();

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_next_notification);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_next_notification, pendingIntent);

        views.setTextViewText(R.id.time_name, context.getString(labels[nextTimeIndex]));
        views.setTextViewText(R.id.next_notification, scheduleHandler.getFormattedTime(nextTimeIndex));

        appWidgetManager.updateAppWidget(appWidgetId, views);
	}
}
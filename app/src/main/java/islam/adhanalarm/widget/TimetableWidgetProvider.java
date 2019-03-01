package islam.adhanalarm.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.RemoteViews;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.MainActivity;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.ScheduleHandler;

public class TimetableWidgetProvider extends AppWidgetProvider {

    private static final int[] times = new int[]{ R.id.fajr, R.id.sunrise, R.id.dhuhr, R.id.asr, R.id.maghrib, R.id.ishaa, R.id.next_fajr };
    private static final int[] labels = new int[]{ R.id.label_fajr, R.id.label_sunrise, R.id.label_dhuhr, R.id.label_asr, R.id.label_maghrib, R.id.label_ishaa, R.id.label_next_fajr };

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (CONSTANT.ACTION_UPDATE_PRAYER_TIME.equals(intent.getAction())) {
            onUpdate(context);
        }
        super.onReceive(context, intent);
    }

    private void onUpdate(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

        ComponentName thisAppWidgetComponentName = new ComponentName(context.getPackageName(),getClass().getName());
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidgetComponentName);
        onUpdate(context, appWidgetManager, appWidgetIds);
    }

    private void updateAppWidget(final Context context, final AppWidgetManager appWidgetManager, int appWidgetId) {

        final ScheduleHandler scheduleHandler = new ScheduleHandler(PreferenceManager.getDefaultSharedPreferences(context));

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_timetable);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), 0);
        views.setOnClickPendingIntent(R.id.today, pendingIntent);

        final boolean isRTL = context.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        final int rowSelectedStart = isRTL ? R.drawable.row_selected_right_thin : R.drawable.row_selected_left_thin;
        final int rowSelectedEnd = isRTL ? R.drawable.row_selected_left_thin : R.drawable.row_selected_right_thin;
        final int nextTimeIndex = scheduleHandler.getNextTimeIndex();
        for (short i = CONSTANT.FAJR; i <= CONSTANT.NEXT_FAJR; i++) {
            views.setTextViewText(times[i], scheduleHandler.getFormattedTime(i));
            try {
                views.setInt(labels[i], "setBackgroundResource", i == nextTimeIndex ? rowSelectedStart : R.drawable.row_divider);
                views.setInt(times[i], "setBackgroundResource", i == nextTimeIndex ? rowSelectedEnd : R.drawable.row_divider);
            } catch (Resources.NotFoundException ex) {
                views.setInt(labels[i], "setBackgroundResource", R.color.selectedRow);
                views.setInt(times[i], "setBackgroundResource", R.color.selectedRow);
            }
        }
        appWidgetManager.updateAppWidget(appWidgetId, views);
	}
}
package islam.adhanalarm.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.support.v4.content.WakefulBroadcastReceiver;

import java.util.Calendar;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.MainActivity;
import islam.adhanalarm.handler.ScheduleHandler;
import islam.adhanalarm.service.StartNotificationService;

public class StartNotificationReceiver extends WakefulBroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null) {
            if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
                intent.putExtra("isOnBootUp", true);
            }
            if (!intent.getAction().equals(CONSTANT.ACTION_UPDATE_PRAYER_TIME)) {
                StartNotificationService.enqueueWork(context, intent);
            }
        }
        setNextAlarm(context);
	}

	private static void setNextAlarm(Context context) {
        ScheduleHandler scheduleHandler = new ScheduleHandler(PreferenceManager.getDefaultSharedPreferences(context));

        short timeIndex = scheduleHandler.getNextTimeIndex();
        Calendar actualTime = scheduleHandler.getNextTime();

		if (Calendar.getInstance().after(actualTime)) return; // Somehow current time is greater than the prayer time

		Intent intent = new Intent(context, StartNotificationReceiver.class);
        intent.setAction(CONSTANT.ACTION_NOTIFY_PRAYER_TIME);
		intent.putExtra("timeIndex", timeIndex);
		intent.putExtra("actualTime", actualTime.getTimeInMillis());

        Intent infoIntent = new Intent(context, MainActivity.class);
        infoIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingInfoIntent = PendingIntent.getActivity(context, 0, infoIntent, 0);

		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		long triggerAtMillis = actualTime.getTimeInMillis();
		PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        am.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAtMillis, pendingInfoIntent), pendingIntent);
    }
}
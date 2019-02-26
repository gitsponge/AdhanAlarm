package islam.adhanalarm.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

import java.util.Calendar;

import islam.adhanalarm.App;
import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.handler.ScheduleHandler;
import islam.adhanalarm.service.StartNotificationService;

public class StartNotificationReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
	    Intent startNotification = new Intent(context, StartNotificationService.class);
		if (intent.getAction() == null || !intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            startNotification.putExtras(intent);
		}
        StartNotificationService.enqueueWork(context, startNotification);
        setNext(context);
        App.broadcastPrayerTimeUpdate();
	}

	public static void setNext(Context context) {
        ScheduleHandler scheduleHandler = new ScheduleHandler(PreferenceManager.getDefaultSharedPreferences(context));

        short timeIndex = scheduleHandler.getNextTimeIndex();
        Calendar actualTime = scheduleHandler.getNextTime();

		if(Calendar.getInstance().after(actualTime)) return; // Somehow current time is greater than the prayer time

		Intent intent = new Intent(context, StartNotificationReceiver.class);
		intent.setAction(CONSTANT.ACTION_NOTIFY_PRAYER_TIME);
		intent.putExtra("timeIndex", timeIndex);
		intent.putExtra("actualTime", actualTime.getTimeInMillis());

		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		long triggerAtMillis = actualTime.getTimeInMillis();
		PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        am.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent), pendingIntent);
    }
}
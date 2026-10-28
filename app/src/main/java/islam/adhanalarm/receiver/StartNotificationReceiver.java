package islam.adhanalarm.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;

import androidx.core.app.AlarmManagerCompat;

import java.util.Calendar;

import islam.adhanalarm.App;
import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.MainActivity;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.NotificationHandler;
import islam.adhanalarm.handler.ScheduleHandler;
import islam.adhanalarm.service.AdhanService;

public class StartNotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (CONSTANT.ACTION_NOTIFY_PRAYER_TIME.equals(action)) {
            notifyPrayerTime(context, intent);
        } else if (CONSTANT.ACTION_PLAY_BISMILLAH.equals(action)) {
            AdhanService.playBismillah(context);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) { // Once the phone is unlocked after starting
            if (App.getSettings(context).getBoolean("bismillahOnBootUp", false)) {
                setBismillahAlarm(context);
            }
        }
        // Boot (before and after the first unlock), app updates, clock and time zone changes all move the next prayer time
        App.broadcastPrayerTimeUpdate();
    }

    private static void notifyPrayerTime(Context context, Intent intent) {
        short timeIndex = intent.getShortExtra(CONSTANT.EXTRA_TIME_INDEX, (short) -1);
        if (timeIndex < CONSTANT.FAJR || timeIndex > CONSTANT.NEXT_FAJR) return;
        if (timeIndex == CONSTANT.NEXT_FAJR) {
            timeIndex = CONSTANT.FAJR; // Before the notification type lookup, so tomorrow's fajr uses the fajr setting
        }

        SharedPreferences settings = App.getSettings(context);
        short notificationType = intent.getShortExtra(CONSTANT.EXTRA_NOTIFICATION_TYPE, new ScheduleHandler(settings).getNotificationType(timeIndex));
        if (notificationType == CONSTANT.NOTIFICATION_NONE) return;

        if ((notificationType == CONSTANT.NOTIFICATION_PLAY || notificationType == CONSTANT.NOTIFICATION_BEEP) && isSoundAllowed(context, settings.getBoolean("playOnSilent", false))) {
            int resid = R.raw.beep;
            if (notificationType == CONSTANT.NOTIFICATION_PLAY) {
                resid = timeIndex == CONSTANT.FAJR ? R.raw.adhan_fajr : R.raw.adhan;
            }
            if (AdhanService.play(context, timeIndex, resid)) return; // The service shows the notification
        }
        NotificationHandler.notify(context, NotificationHandler.buildReminder(context, timeIndex));
    }

    private static boolean isSoundAllowed(Context context, boolean playOnSilent) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // Not on silent or vibrate unless the user chose to play anyway, and never while ringing or in a call
        return (playOnSilent || audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) && audioManager.getMode() == AudioManager.MODE_NORMAL;
    }

    public static void setNextAlarm(Context context) {
        ScheduleHandler scheduleHandler = new ScheduleHandler(App.getSettings(context));

        short timeIndex = scheduleHandler.getNextTimeIndex();
        Calendar actualTime = scheduleHandler.getNextTime();

        if (Calendar.getInstance().after(actualTime)) return; // Somehow current time is greater than the prayer time

        Intent intent = new Intent(context, StartNotificationReceiver.class);
        intent.setAction(CONSTANT.ACTION_NOTIFY_PRAYER_TIME);
        intent.putExtra(CONSTANT.EXTRA_TIME_INDEX, timeIndex);
        intent.putExtra(CONSTANT.EXTRA_ACTUAL_TIME, actualTime.getTimeInMillis());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(context, actualTime.getTimeInMillis(), pendingIntent);
    }

    public static void setAlarm(Context context, long triggerAtMillis, PendingIntent operation) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (AlarmManagerCompat.canScheduleExactAlarms(am)) {
            Intent infoIntent = new Intent(context, MainActivity.class);
            infoIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pendingInfoIntent = PendingIntent.getActivity(context, 0, infoIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManagerCompat.setAlarmClock(am, triggerAtMillis, pendingInfoIntent, operation);
        } else {
            // Without the exact alarm permission Android may deliver this a few minutes late
            AlarmManagerCompat.setAndAllowWhileIdle(am, AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
        }
    }

    private static void setBismillahAlarm(Context context) {
        // Android 15+ won't start media playback straight from the boot broadcast, so play it from a short alarm
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (!AlarmManagerCompat.canScheduleExactAlarms(am)) return;
        Intent intent = new Intent(context, StartNotificationReceiver.class);
        intent.setAction(CONSTANT.ACTION_PLAY_BISMILLAH);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManagerCompat.setExactAndAllowWhileIdle(am, AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, pendingIntent);
    }
}

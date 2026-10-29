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
import java.util.GregorianCalendar;

import islam.adhanalarm.App;
import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.MainActivity;
import islam.adhanalarm.Prefs;
import islam.adhanalarm.handler.NotificationHandler;
import islam.adhanalarm.handler.ScheduleHandler;
import islam.adhanalarm.service.AdhanService;

public class StartNotificationReceiver extends BroadcastReceiver {

    private static final int REQUEST_PRAYER = 0, REQUEST_BISMILLAH = 1, REQUEST_BEFORE = 2, REQUEST_NEW_DAY = 3;
    private static final String EXTRA_MINUTES = "minutes";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (CONSTANT.ACTION_NOTIFY_PRAYER_TIME.equals(action)) {
            notifyPrayerTime(context, intent);
        } else if (CONSTANT.ACTION_NOTIFY_REMINDER.equals(action)) {
            short timeIndex = intent.getShortExtra(CONSTANT.EXTRA_TIME_INDEX, (short) -1);
            if (timeIndex >= CONSTANT.FAJR && timeIndex <= CONSTANT.ISHAA) {
                NotificationHandler.notify(context, NotificationHandler.BEFORE_NOTIFICATION_ID, NotificationHandler.buildBefore(context, timeIndex,
                        intent.getLongExtra(CONSTANT.EXTRA_ACTUAL_TIME, System.currentTimeMillis()), intent.getIntExtra(EXTRA_MINUTES, 0)));
            }
        } else if (CONSTANT.ACTION_PLAY_BISMILLAH.equals(action)) {
            AdhanService.playBismillah(context);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) { // Once the phone is unlocked after starting
            if (App.getSettings(context).getBoolean(Prefs.BISMILLAH, false)) {
                setBismillahAlarm(context);
            }
        }
        // Boot (before and after the first unlock), app updates, clock and time zone changes, a new day and each alert all move the next times
        App.broadcastPrayerTimeUpdate();
    }

    private static void notifyPrayerTime(Context context, Intent intent) {
        short timeIndex = intent.getShortExtra(CONSTANT.EXTRA_TIME_INDEX, (short) -1);
        if (timeIndex < CONSTANT.FAJR || timeIndex > CONSTANT.NEXT_FAJR) return;
        if (timeIndex == CONSTANT.NEXT_FAJR) {
            timeIndex = CONSTANT.FAJR; // Before the notification type lookup, so tomorrow's fajr uses the fajr setting
        }
        long time = intent.getLongExtra(CONSTANT.EXTRA_ACTUAL_TIME, System.currentTimeMillis());
        SharedPreferences settings = App.getSettings(context);
        short notificationType = intent.getShortExtra(CONSTANT.EXTRA_NOTIFICATION_TYPE, Prefs.getAlert(settings, timeIndex));
        if (notificationType == CONSTANT.NOTIFICATION_NONE) return;
        if ((notificationType == CONSTANT.NOTIFICATION_PLAY || notificationType == CONSTANT.NOTIFICATION_BEEP) && isSoundAllowed(context, settings.getBoolean(Prefs.PLAY_ON_SILENT, false))) {
            boolean adhan = notificationType == CONSTANT.NOTIFICATION_PLAY && timeIndex != CONSTANT.SUNRISE;
            if (AdhanService.play(context, timeIndex, time, adhan)) return; // The service shows the notification
        }
        NotificationHandler.notify(context, NotificationHandler.buildReminder(context, timeIndex, time));
    }

    private static boolean isSoundAllowed(Context context, boolean playOnSilent) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // Not on silent or vibrate unless the user chose to play anyway, and never while ringing or in a call
        return (playOnSilent || audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) && audioManager.getMode() == AudioManager.MODE_NORMAL;
    }

    public static void setNextAlarm(Context context) {
        SharedPreferences settings = App.getSettings(context);
        ScheduleHandler scheduleHandler = new ScheduleHandler(settings);
        short timeIndex = scheduleHandler.getNextTimeIndex();
        Calendar actualTime = scheduleHandler.getNextTime();
        if (Calendar.getInstance().before(actualTime)) {
            Intent intent = new Intent(context, StartNotificationReceiver.class);
            intent.setAction(CONSTANT.ACTION_NOTIFY_PRAYER_TIME);
            intent.putExtra(CONSTANT.EXTRA_TIME_INDEX, timeIndex);
            intent.putExtra(CONSTANT.EXTRA_ACTUAL_TIME, actualTime.getTimeInMillis());
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, REQUEST_PRAYER, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            setAlarm(context, actualTime.getTimeInMillis(), pendingIntent);
        }
        setBeforeAlarm(context, settings, scheduleHandler);
        setNewDayAlarm(context);
    }

    /**
     * The earliest coming "remind me before", if any prayer has one
     */
    private static void setBeforeAlarm(Context context, SharedPreferences settings, ScheduleHandler scheduleHandler) {
        long now = System.currentTimeMillis();
        long earliest = Long.MAX_VALUE;
        short earliestIndex = -1;
        int earliestMinutes = 0;
        for (short i = CONSTANT.FAJR; i <= CONSTANT.NEXT_FAJR; i++) {
            short prayer = i == CONSTANT.NEXT_FAJR ? CONSTANT.FAJR : i;
            int minutes = Prefs.getReminder(settings, prayer);
            long at = scheduleHandler.getTime(i).getTimeInMillis() - minutes * 60000L;
            if (minutes > 0 && at > now && at < earliest) {
                earliest = at;
                earliestIndex = i;
                earliestMinutes = minutes;
            }
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, StartNotificationReceiver.class).setAction(CONSTANT.ACTION_NOTIFY_REMINDER);
        if (earliestIndex < 0) {
            alarmManager.cancel(PendingIntent.getBroadcast(context, REQUEST_BEFORE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            return;
        }
        intent.putExtra(CONSTANT.EXTRA_TIME_INDEX, earliestIndex == CONSTANT.NEXT_FAJR ? CONSTANT.FAJR : earliestIndex);
        intent.putExtra(CONSTANT.EXTRA_ACTUAL_TIME, scheduleHandler.getTime(earliestIndex).getTimeInMillis());
        intent.putExtra(EXTRA_MINUTES, earliestMinutes);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, REQUEST_BEFORE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (AlarmManagerCompat.canScheduleExactAlarms(alarmManager)) {
            AlarmManagerCompat.setExactAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, earliest, pendingIntent);
        } else {
            AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, earliest, pendingIntent);
        }
    }

    /**
     * Just after midnight, so widgets show the new date. It doesn't wake the phone.
     */
    private static void setNewDayAlarm(Context context) {
        GregorianCalendar midnight = new GregorianCalendar();
        midnight.add(Calendar.DAY_OF_MONTH, 1);
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 1);
        midnight.set(Calendar.SECOND, 0);
        Intent intent = new Intent(context, StartNotificationReceiver.class).setAction(CONSTANT.ACTION_NEW_DAY);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, REQUEST_NEW_DAY, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC, midnight.getTimeInMillis(), pendingIntent);
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
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, REQUEST_BISMILLAH, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManagerCompat.setExactAndAllowWhileIdle(am, AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, pendingIntent);
    }
}

package islam.adhanalarm.debug;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.receiver.StartNotificationReceiver;
import islam.adhanalarm.service.AdhanService;

/**
 * Sets a real prayer alarm a few seconds from now, so the whole path (exact alarm, notification and
 * adhan service) can be tested without waiting for a prayer time. Debug builds only, for example:
 *
 * adb shell am broadcast -a islam.adhanalarm.debug.TEST_ALARM -n islam.adhanalarm/.debug.TestAlarmReceiver --es prayer fajr --es type play --ei delay 10
 * adb shell am broadcast -a islam.adhanalarm.debug.STOP_ADHAN -n islam.adhanalarm/.debug.TestAlarmReceiver
 */
public class TestAlarmReceiver extends BroadcastReceiver {

    private static final List<String> PRAYERS = Arrays.asList("fajr", "sunrise", "dhuhr", "asr", "maghrib", "ishaa");
    private static final List<String> TYPES = Arrays.asList("none", "default", "beep", "play"); // CONSTANT.NOTIFICATION_*

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("islam.adhanalarm.debug.STOP_ADHAN".equals(intent.getAction())) {
            AdhanService.stop(context, true);
            return;
        }
        short timeIndex = (short) Math.max(0, PRAYERS.indexOf(getExtra(intent, "prayer", "dhuhr")));
        short notificationType = (short) Math.max(0, TYPES.indexOf(getExtra(intent, "type", "play")));
        int delaySeconds = intent.getIntExtra("delay", 10);

        Intent alarmIntent = new Intent(context, StartNotificationReceiver.class);
        alarmIntent.setAction(CONSTANT.ACTION_NOTIFY_PRAYER_TIME);
        alarmIntent.putExtra(CONSTANT.EXTRA_TIME_INDEX, timeIndex);
        alarmIntent.putExtra(CONSTANT.EXTRA_NOTIFICATION_TYPE, notificationType);

        // Its own request code, so the real next prayer alarm is left alone
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 1000, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        StartNotificationReceiver.setAlarm(context, System.currentTimeMillis() + delaySeconds * 1000L, pendingIntent);
        Log.i("AdhanAlarm", "Test alarm set: " + PRAYERS.get(timeIndex) + ", " + TYPES.get(notificationType) + ", in " + delaySeconds + "s");
    }

    private static String getExtra(Intent intent, String name, String defaultValue) {
        String value = intent.getStringExtra(name);
        return value != null ? value : defaultValue;
    }
}

package islam.adhanalarm.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;

import islam.adhanalarm.App;
import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.ScheduleHandler;
import islam.adhanalarm.receiver.HandleNotificationReceiver;

public class StartNotificationService extends JobIntentService {

    private static final int JOB_ID = 1501;
    private static final String CHANNEL_ID = "adhanalarm";
    private static final int NOTIFICATION_ID = 1651;

    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, StartNotificationService.class, JOB_ID, work);
    }

	@Override
	protected void onHandleWork(@NonNull Intent intent) {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

		short timeIndex = intent.getShortExtra("timeIndex", (short) -1);
		if (timeIndex == -1) {
		    // Got here from boot
			if (settings.getBoolean("bismillahOnBootUp", false)) {
                App.startMedia(R.raw.bismillah);
			}
		} else {
            if (timeIndex == CONSTANT.NEXT_FAJR) {
                timeIndex = CONSTANT.FAJR;
            }
            ScheduleHandler scheduleHandler = new ScheduleHandler(settings);
            short notificationType = scheduleHandler.getNotificationType(timeIndex);

            if (notificationType == CONSTANT.NOTIFICATION_NONE) {
                return;
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.icon_notification)
                    .setContentTitle((timeIndex != CONSTANT.SUNRISE ? getString(R.string.allahu_akbar) + ": " : "") + getString(R.string.time_for) + " " + getString(CONSTANT.TIME_NAMES[timeIndex]).toLowerCase())
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(PendingIntent.getBroadcast(this, 0, new Intent(CONSTANT.ACTION_NOTIFICATION_CLICKED, null, this, HandleNotificationReceiver.class), PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT))
                    .setDeleteIntent(PendingIntent.getBroadcast(this, 0, new Intent(CONSTANT.ACTION_NOTIFICATION_DELETED, null, this, HandleNotificationReceiver.class), PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT));

            int ringerMode = ((AudioManager) getSystemService(Context.AUDIO_SERVICE)).getRingerMode();
            int callState = ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).getCallState();

            if (ringerMode != AudioManager.RINGER_MODE_SILENT && ringerMode != AudioManager.RINGER_MODE_VIBRATE && callState == TelephonyManager.CALL_STATE_IDLE) {
                if (notificationType == CONSTANT.NOTIFICATION_PLAY || notificationType == CONSTANT.NOTIFICATION_BEEP) {
                    int resid = R.raw.beep;
                    if (notificationType == CONSTANT.NOTIFICATION_PLAY) {
                        resid = timeIndex == CONSTANT.FAJR ? R.raw.adhan_fajr : R.raw.adhan;
                    }
                    builder.addAction(android.R.drawable.stat_notify_call_mute, getString(R.string.stop), PendingIntent.getBroadcast(this, 0, new Intent(CONSTANT.ACTION_NOTIFICATION_STOPPED, null, this, HandleNotificationReceiver.class), PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT));
                    App.startMedia(resid);
                }
            }

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String channelId = "ADHAN_ALARM_CHANNEL_ID";
                NotificationChannel channel = new NotificationChannel(channelId, getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);
                builder.setChannelId(channelId);
            }
            notificationManager.notify(NOTIFICATION_ID, builder.build());
		}
	}
}
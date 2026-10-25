package islam.adhanalarm.handler;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.MainActivity;
import islam.adhanalarm.R;
import islam.adhanalarm.receiver.HandleNotificationReceiver;

public class NotificationHandler {

    public static final int NOTIFICATION_ID = 1651;

    private static final String CHANNEL_REMINDER = "reminder";
    private static final String CHANNEL_ADHAN = "adhan";
    private static final String OLD_CHANNEL_ID = "ADHAN_ALARM_CHANNEL_ID";

    public static void createChannels(Context context) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.deleteNotificationChannel(OLD_CHANNEL_ID);
        // A plain reminder uses the phone's notification sound
        notificationManager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_REMINDER, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_reminder))
                .build());
        // The app plays the adhan or beep itself, so this channel makes no sound of its own
        notificationManager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_ADHAN, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_adhan))
                .setSound(null, null)
                .build());
    }

    public static Notification buildReminder(Context context, short timeIndex) {
        return builder(context, timeIndex, CHANNEL_REMINDER).build();
    }

    public static Notification buildPlaying(Context context, short timeIndex) {
        return builder(context, timeIndex, CHANNEL_ADHAN)
                .setDeleteIntent(getBroadcast(context, CONSTANT.ACTION_NOTIFICATION_DELETED))
                .addAction(android.R.drawable.stat_notify_call_mute, context.getString(R.string.stop), getBroadcast(context, CONSTANT.ACTION_NOTIFICATION_STOPPED))
                .build();
    }

    /**
     * What stays in the shade once the adhan has finished: silent, and without the Stop button
     */
    public static Notification buildPlayed(Context context, short timeIndex) {
        return builder(context, timeIndex, CHANNEL_ADHAN)
                .setOnlyAlertOnce(true) // It replaces the playing notification, so don't alert a second time
                .build();
    }

    public static Notification buildBismillah(Context context) {
        return new NotificationCompat.Builder(context, CHANNEL_ADHAN)
                .setSmallIcon(R.drawable.icon_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setDeleteIntent(getBroadcast(context, CONSTANT.ACTION_NOTIFICATION_DELETED))
                .addAction(android.R.drawable.stat_notify_call_mute, context.getString(R.string.stop), getBroadcast(context, CONSTANT.ACTION_NOTIFICATION_DELETED))
                .build();
    }

    public static void notify(Context context, Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification);
    }

    private static NotificationCompat.Builder builder(Context context, short timeIndex, String channelId) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.icon_notification)
                .setContentTitle((timeIndex != CONSTANT.SUNRISE ? context.getString(R.string.allahu_akbar) + ": " : "") + context.getString(R.string.time_for) + " " + context.getString(CONSTANT.TIME_NAMES[timeIndex]).toLowerCase(Locale.getDefault()))
                .setPriority(CHANNEL_ADHAN.equals(channelId) ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    private static PendingIntent getBroadcast(Context context, String action) {
        return PendingIntent.getBroadcast(context, 0, new Intent(action, null, context, HandleNotificationReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}

package islam.adhanalarm.handler;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.Date;

import islam.adhanalarm.App;
import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.MainActivity;
import islam.adhanalarm.Prefs;
import islam.adhanalarm.R;
import islam.adhanalarm.receiver.HandleNotificationReceiver;

public class NotificationHandler {

    public static final int NOTIFICATION_ID = 1651;
    public static final int BEFORE_NOTIFICATION_ID = 1652;

    private static final String CHANNEL_REMINDER = "reminder";
    private static final String CHANNEL_ADHAN = "adhan";
    private static final String CHANNEL_BEFORE = "before";
    private static final String OLD_CHANNEL_ID = "ADHAN_ALARM_CHANNEL_ID";

    public static void createChannels(Context context) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.deleteNotificationChannel(OLD_CHANNEL_ID);
        // A plain reminder uses the phone's notification sound
        notificationManager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_REMINDER, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_reminder))
                .build());
        // The app plays the adhan or chime itself, so this channel makes no sound of its own
        notificationManager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_ADHAN, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_adhan))
                .setSound(null, null)
                .build());
        notificationManager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_BEFORE, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_before))
                .build());
    }

    public static Notification buildReminder(Context context, short timeIndex, long time) {
        return builder(context, timeIndex, time, CHANNEL_REMINDER).build();
    }

    public static Notification buildPlaying(Context context, short timeIndex, long time) {
        return builder(context, timeIndex, time, CHANNEL_ADHAN)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDeleteIntent(getBroadcast(context, CONSTANT.ACTION_NOTIFICATION_DELETED))
                .addAction(R.drawable.ic_stop, context.getString(R.string.stop), getBroadcast(context, CONSTANT.ACTION_NOTIFICATION_STOPPED))
                .build();
    }

    /**
     * What stays in the shade once the adhan has finished: silent, and without the Stop button
     */
    public static Notification buildPlayed(Context context, short timeIndex, long time) {
        return builder(context, timeIndex, time, CHANNEL_ADHAN)
                .setOnlyAlertOnce(true) // It replaces the playing notification, so don't alert a second time
                .build();
    }

    /**
     * The reminder some minutes before a prayer, or before sunrise ends fajr
     */
    public static Notification buildBefore(Context context, short timeIndex, long time, int minutes) {
        SharedPreferences settings = App.getSettings(context);
        String title = timeIndex == CONSTANT.SUNRISE
                ? context.getString(R.string.notification_before_sunrise, minutes)
                : context.getString(R.string.notification_before, context.getString(CONSTANT.TIME_NAMES[timeIndex]), minutes);
        return new NotificationCompat.Builder(context, CHANNEL_BEFORE)
                .setSmallIcon(R.drawable.icon_notification)
                .setColor(ContextCompat.getColor(context, R.color.brand))
                .setContentTitle(title)
                .setContentText(context.getString(R.string.notification_at, Prefs.getTimeFormat(context, settings).format(new Date(time))))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setWhen(time)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(getContentIntent(context))
                .build();
    }

    public static Notification buildBismillah(Context context) {
        return new NotificationCompat.Builder(context, CHANNEL_ADHAN)
                .setSmallIcon(R.drawable.icon_notification)
                .setColor(ContextCompat.getColor(context, R.color.brand))
                .setContentTitle(context.getString(R.string.app_name))
                .setDeleteIntent(getBroadcast(context, CONSTANT.ACTION_NOTIFICATION_DELETED))
                .addAction(R.drawable.ic_stop, context.getString(R.string.stop), getBroadcast(context, CONSTANT.ACTION_NOTIFICATION_DELETED))
                .build();
    }

    public static void notify(Context context, Notification notification) {
        notify(context, NOTIFICATION_ID, notification);
    }

    public static void notify(Context context, int id, Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat.from(context).notify(id, notification);
    }

    private static NotificationCompat.Builder builder(Context context, short timeIndex, long time, String channelId) {
        SharedPreferences settings = App.getSettings(context);
        String name = context.getString(CONSTANT.TIME_NAMES[timeIndex]);
        String title = Prefs.showArabicNames(context, settings) ? context.getString(R.string.name_with_arabic, name, context.getString(CONSTANT.ARABIC_NAMES[timeIndex])) : name;
        String text = timeIndex == CONSTANT.SUNRISE ? context.getString(R.string.notification_sunrise) : context.getString(R.string.notification_prayer, name);
        return new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.icon_notification)
                .setColor(ContextCompat.getColor(context, R.color.brand))
                .setContentTitle(title)
                .setContentText(text)
                .setWhen(time)
                .setShowWhen(true)
                .setPriority(CHANNEL_ADHAN.equals(channelId) ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(getContentIntent(context));
    }

    private static PendingIntent getContentIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent getBroadcast(Context context, String action) {
        return PendingIntent.getBroadcast(context, 0, new Intent(action, null, context, HandleNotificationReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}

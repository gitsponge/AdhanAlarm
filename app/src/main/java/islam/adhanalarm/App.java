package islam.adhanalarm;

import android.app.Application;
import android.content.Intent;
import android.media.MediaPlayer;
import android.support.v4.content.LocalBroadcastManager;

import islam.adhanalarm.receiver.StartNotificationReceiver;
import islam.adhanalarm.widget.NextNotificationWidgetProvider;
import islam.adhanalarm.widget.TimetableWidgetProvider;

public class App extends Application {

    private static App sInstance;

    private MediaPlayer mPlayer;

    private static StartNotificationReceiver sStartNotificationReceiver = new StartNotificationReceiver();
    private static TimetableWidgetProvider sTimetableWidgetProvider = new TimetableWidgetProvider();
    private static NextNotificationWidgetProvider sNextNotificationWidgetProvider = new NextNotificationWidgetProvider();

    public static void broadcastPrayerTimeUpdate() {
        LocalBroadcastManager.getInstance(sInstance).sendBroadcast(new Intent(CONSTANT.ACTION_UPDATE_PRAYER_TIME));
        sInstance.sendBroadcast(new Intent(CONSTANT.ACTION_UPDATE_PRAYER_TIME));
        sStartNotificationReceiver.onReceive(sInstance, new Intent(CONSTANT.ACTION_UPDATE_PRAYER_TIME));
        sTimetableWidgetProvider.onReceive(sInstance, new Intent(CONSTANT.ACTION_UPDATE_PRAYER_TIME));
        sNextNotificationWidgetProvider.onReceive(sInstance, new Intent(CONSTANT.ACTION_UPDATE_PRAYER_TIME));
    }

    public static void startMedia(int resid) {
        sInstance.mPlayer.stop();
        sInstance.mPlayer = MediaPlayer.create(sInstance, resid);
        sInstance.mPlayer.setScreenOnWhilePlaying(true);
        sInstance.mPlayer.start();
    }

    public static void stopMedia() {
        sInstance.mPlayer.stop();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mPlayer = MediaPlayer.create(this, R.raw.bismillah);
    }

    @Override
    public void onTerminate() {
        sInstance.mPlayer.stop();
        super.onTerminate();
    }
}
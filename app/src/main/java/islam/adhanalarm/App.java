package islam.adhanalarm;

import android.app.Application;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;

import islam.adhanalarm.receiver.StartNotificationReceiver;
import islam.adhanalarm.widget.NextNotificationWidgetProvider;
import islam.adhanalarm.widget.TimetableWidgetProvider;

public class App extends Application {

    private static App sInstance;

    private MediaPlayer mPlayer;

    private StartNotificationReceiver mStartNotificationReceiver = new StartNotificationReceiver();
    private TimetableWidgetProvider mTimetableWidgetProvider = new TimetableWidgetProvider();
    private NextNotificationWidgetProvider mNextNotificationWidgetProvider = new NextNotificationWidgetProvider();

    public static void broadcastPrayerTimeUpdate() {
        Intent intent = new Intent(CONSTANT.ACTION_UPDATE_PRAYER_TIME);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            LocalBroadcastManager.getInstance(sInstance).sendBroadcast(intent);
        }
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
        LocalBroadcastManager.getInstance(this).registerReceiver(mStartNotificationReceiver, new IntentFilter(CONSTANT.ACTION_NOTIFY_PRAYER_TIME));
        LocalBroadcastManager.getInstance(this).registerReceiver(mTimetableWidgetProvider, new IntentFilter(CONSTANT.ACTION_UPDATE_PRAYER_TIME));
        LocalBroadcastManager.getInstance(this).registerReceiver(mNextNotificationWidgetProvider, new IntentFilter(CONSTANT.ACTION_UPDATE_PRAYER_TIME));
    }

    @Override
    public void onTerminate() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mStartNotificationReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mTimetableWidgetProvider);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mNextNotificationWidgetProvider);
        sInstance.mPlayer.stop();
        super.onTerminate();
    }
}
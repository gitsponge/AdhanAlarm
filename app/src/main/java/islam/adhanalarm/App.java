package islam.adhanalarm;

import android.app.Application;

import java.util.ArrayList;
import java.util.List;

import islam.adhanalarm.handler.NotificationHandler;
import islam.adhanalarm.receiver.StartNotificationReceiver;
import islam.adhanalarm.widget.NextNotificationWidgetProvider;
import islam.adhanalarm.widget.TimetableWidgetProvider;

public class App extends Application {

    private static App sInstance;

    private static final List<PrayerTimeListener> sListeners = new ArrayList<>();

    /**
     * Schedules the next alarm and refreshes the widgets and any open screens. Call on the main thread.
     */
    public static void broadcastPrayerTimeUpdate() {
        StartNotificationReceiver.setNextAlarm(sInstance);
        TimetableWidgetProvider.updateAll(sInstance);
        NextNotificationWidgetProvider.updateAll(sInstance);
        for (PrayerTimeListener listener : new ArrayList<>(sListeners)) {
            listener.onPrayerTimeUpdated();
        }
    }

    public static void addListener(PrayerTimeListener listener) {
        sListeners.add(listener);
    }

    public static void removeListener(PrayerTimeListener listener) {
        sListeners.remove(listener);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        NotificationHandler.createChannels(this);
    }

    public interface PrayerTimeListener {

        /**
         * This method is called after the prayer times are recalculated
         */
        void onPrayerTimeUpdated();
    }
}

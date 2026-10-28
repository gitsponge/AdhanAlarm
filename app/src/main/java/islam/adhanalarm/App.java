package islam.adhanalarm;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.os.UserManagerCompat;
import androidx.preference.PreferenceManager;

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
        if (UserManagerCompat.isUserUnlocked(sInstance)) { // Widgets aren't available until the phone has been unlocked since it started
            TimetableWidgetProvider.updateAll(sInstance);
            NextNotificationWidgetProvider.updateAll(sInstance);
        }
        for (PrayerTimeListener listener : new ArrayList<>(sListeners)) {
            listener.onPrayerTimeUpdated();
        }
    }

    /**
     * The settings are kept in device protected storage, so after a restart the alarm can be set before the phone is unlocked
     */
    public static SharedPreferences getSettings(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return PreferenceManager.getDefaultSharedPreferences(context); // Android 6 has no direct boot
        }
        Context storage = context.createDeviceProtectedStorageContext();
        // Settings from before 3.0.2 are in normal storage, which can't be read until the phone is unlocked, so the move is tried each time
        storage.moveSharedPreferencesFrom(context, context.getPackageName() + "_preferences");
        return PreferenceManager.getDefaultSharedPreferences(storage);
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

        // Before 3.0.2 the settings screen saved the air pressure as 110 by mistake. The calculation assumes 1010 millibars.
        SharedPreferences settings = getSettings(this);
        if ("110".equals(settings.getString("pressure", null))) {
            settings.edit().putString("pressure", "1010").apply();
        }
    }

    public interface PrayerTimeListener {

        /**
         * This method is called after the prayer times are recalculated
         */
        void onPrayerTimeUpdated();
    }
}

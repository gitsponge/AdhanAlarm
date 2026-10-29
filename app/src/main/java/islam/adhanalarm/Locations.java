package islam.adhanalarm;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import islam.adhanalarm.handler.CityDatabase;

/**
 * Every change of location goes through here, so the place's name, the default method and the alarms follow it
 */
public final class Locations {

    /** A country is trusted for the default method if a city in it is this close */
    private static final double COUNTRY_KM = 200;

    /**
     * Saves a position from the phone or typed coordinates, and names it after the nearest city within 20 km
     */
    public static void save(Context context, final double latitude, final double longitude, @Nullable final Runnable done) {
        final Context appContext = context.getApplicationContext();
        final SharedPreferences settings = App.getSettings(appContext);
        settings.edit().putString(Prefs.LATITUDE, Double.toString(latitude)).putString(Prefs.LONGITUDE, Double.toString(longitude)).apply();
        App.broadcastPrayerTimeUpdate();
        CityDatabase.load(appContext, new Consumer<CityDatabase>() {
            @Override
            public void accept(CityDatabase database) {
                CityDatabase.City city = database.nearest(latitude, longitude);
                boolean near = city != null && city.distanceKm <= CityDatabase.NEAR_KM;
                String country = city != null && city.distanceKm <= COUNTRY_KM ? city.country : "";
                SharedPreferences.Editor editor = settings.edit()
                        .putString(Prefs.PLACE_NAME, near ? city.name : "")
                        .putString(Prefs.PLACE_COUNTRY, country);
                applyDefaultMethod(settings, editor, country);
                editor.apply();
                App.broadcastPrayerTimeUpdate();
                if (done != null) done.run();
            }
        });
    }

    /**
     * Saves a city chosen from the search
     */
    public static void save(Context context, CityDatabase.City city) {
        SharedPreferences settings = App.getSettings(context);
        SharedPreferences.Editor editor = settings.edit()
                .putString(Prefs.LATITUDE, Double.toString(city.latitude))
                .putString(Prefs.LONGITUDE, Double.toString(city.longitude))
                .putString(Prefs.PLACE_NAME, city.name)
                .putString(Prefs.PLACE_COUNTRY, city.country);
        applyDefaultMethod(settings, editor, city.country);
        editor.apply();
        App.broadcastPrayerTimeUpdate();
    }

    /**
     * Names the saved position if it has never been named, as after an update from 3.x
     */
    public static void nameIfNeeded(Context context) {
        SharedPreferences settings = App.getSettings(context);
        if (settings.contains(Prefs.PLACE_NAME) || !settings.contains(Prefs.LATITUDE)) return;
        save(context, Formats.getDouble(settings, Prefs.LATITUDE, Prefs.DEFAULT_LATITUDE), Formats.getDouble(settings, Prefs.LONGITUDE, Prefs.DEFAULT_LONGITUDE), null);
    }

    /** The Kaaba */
    public static final double KAABA_LATITUDE = 21.422487, KAABA_LONGITUDE = 39.826206;

    /**
     * The Qibla's bearing from true north along the shortest path over the Earth, in degrees
     */
    public static double qiblaBearing(double latitude, double longitude) {
        double from = Math.toRadians(latitude), to = Math.toRadians(KAABA_LATITUDE);
        double dLongitude = Math.toRadians(KAABA_LONGITUDE - longitude);
        double y = Math.sin(dLongitude) * Math.cos(to);
        double x = Math.cos(from) * Math.sin(to) - Math.sin(from) * Math.cos(to) * Math.cos(dLongitude);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private static void applyDefaultMethod(SharedPreferences settings, SharedPreferences.Editor editor, String country) {
        if (!settings.getBoolean(Prefs.METHOD_CHOSEN, false)) {
            editor.putString(Prefs.METHOD, CONSTANT.getDefaultMethod(country.isEmpty() ? java.util.Locale.getDefault().getCountry() : country));
        }
    }

    private Locations() {
    }
}

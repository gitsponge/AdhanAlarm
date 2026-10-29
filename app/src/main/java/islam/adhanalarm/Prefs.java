package islam.adhanalarm;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.ConfigurationCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * The settings' keys and defaults, in one place. Read them with App.getSettings().
 */
public final class Prefs {

    public static final String LATITUDE = "latitude", LONGITUDE = "longitude";
    public static final String ALTITUDE = "altitude", PRESSURE = "pressure", TEMPERATURE = "temperature";
    public static final String PLACE_NAME = "placeName", PLACE_COUNTRY = "placeCountry"; // Empty name: no city within 20 km
    public static final String AUTO_LOCATION = "autoLocation";
    public static final String METHOD = "method", METHOD_CHOSEN = "methodChosen", ASR = "asr", HIGH_LATITUDE = "highLatitude";
    public static final String ROUNDING = "roundingTypesIndex", HIJRI_ADJUST = "hijriAdjust";
    public static final String ALERT = "notificationMethod", REMINDER = "reminder", OFFSET = "offset"; // Followed by the prayer's index
    public static final String ADHAN_SOUND = "adhanSound", FAJR_ADHAN_SOUND = "fajrAdhanSound", SHORT_ADHAN = "shortAdhan";
    public static final String PLAY_ON_SILENT = "playOnSilent", BISMILLAH = "bismillahOnBootUp";
    public static final String TIME_FORMAT = "timeFormat", ARABIC_NAMES = "arabicNames", THEME = "theme";
    public static final String SETUP_DONE = "setupDone", VERSION = "settingsVersion";

    public static final String ASR_HANAFI = "hanafi";
    public static final double DEFAULT_LATITUDE = 43.67, DEFAULT_LONGITUDE = -79.417;

    private static final int CURRENT_VERSION = 4;

    public static String getMethod(SharedPreferences settings) {
        String method = settings.getString(METHOD, null);
        return method != null ? method : CONSTANT.getDefaultMethod(getCountry(settings));
    }

    /** The country of the chosen place, or of the phone's region when there's none */
    public static String getCountry(SharedPreferences settings) {
        String country = settings.getString(PLACE_COUNTRY, "");
        return !country.isEmpty() ? country : Locale.getDefault().getCountry();
    }

    public static boolean isHanafi(SharedPreferences settings) {
        return ASR_HANAFI.equals(settings.getString(ASR, ""));
    }

    public static String getHighLatitude(SharedPreferences settings) {
        return settings.getString(HIGH_LATITUDE, CONSTANT.HIGH_LATITUDE_SEVENTH);
    }

    public static short getAlert(SharedPreferences settings, int prayer) {
        return (short) parseInt(settings.getString(ALERT + prayer, null), CONSTANT.NOTIFICATION_DEFAULT);
    }

    public static void setAlert(SharedPreferences settings, int prayer, short alert) {
        settings.edit().putString(ALERT + prayer, Short.toString(alert)).apply();
    }

    public static int getReminder(SharedPreferences settings, int prayer) {
        return settings.getInt(REMINDER + prayer, 0);
    }

    public static int getOffset(SharedPreferences settings, int prayer) {
        return settings.getInt(OFFSET + prayer, 0);
    }

    public static int getHijriAdjust(SharedPreferences settings) {
        return parseInt(settings.getString(HIJRI_ADJUST, null), 0);
    }

    public static String getPlaceName(SharedPreferences settings) {
        return settings.getString(PLACE_NAME, "");
    }

    /** Arabic names show beside the others unless the app is already in Arabic */
    public static boolean showArabicNames(Context context, SharedPreferences settings) {
        Locale locale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
        return settings.getBoolean(ARABIC_NAMES, true) && (locale == null || !"ar".equals(locale.getLanguage()));
    }

    public static DateFormat getTimeFormat(Context context, SharedPreferences settings) {
        String format = settings.getString(TIME_FORMAT, "system");
        boolean is24Hour = "24".equals(format) || ("system".equals(format) && android.text.format.DateFormat.is24HourFormat(context));
        Locale locale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
        if (locale == null) locale = Locale.getDefault();
        return new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, is24Hour ? "Hm" : "hm"), locale);
    }

    public static void applyTheme(SharedPreferences settings) {
        String theme = settings.getString(THEME, "system");
        AppCompatDelegate.setDefaultNightMode("light".equals(theme) ? AppCompatDelegate.MODE_NIGHT_NO
                : "dark".equals(theme) ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    /**
     * Brings settings from earlier versions up to date, once
     */
    public static void migrate(SharedPreferences settings) {
        if (settings.getInt(VERSION, 0) >= CURRENT_VERSION) return;
        SharedPreferences.Editor editor = settings.edit();

        // 3.x stored the method as a list index, with Karachi once for each Asr
        String oldMethod = settings.getString("calculationMethodsIndex", null);
        if (oldMethod != null) {
            int index = parseInt(oldMethod, -1);
            if (index >= 0 && index < CONSTANT.OLD_METHOD_ORDER.length) {
                editor.putString(METHOD, CONSTANT.OLD_METHOD_ORDER[index]).putBoolean(METHOD_CHOSEN, true); // Their times stay exactly as they were
                if (index == CONSTANT.OLD_METHOD_KARACHI_HANAFI) editor.putString(ASR, ASR_HANAFI);
            }
            editor.remove("calculationMethodsIndex");
        }

        // 3.x's "Default" was always 12-hour, so it now follows the phone
        String oldFormat = settings.getString("timeFormatIndex", null);
        if (oldFormat != null) {
            editor.putString(TIME_FORMAT, "1".equals(oldFormat) ? "24" : "system").remove("timeFormatIndex");
        }

        // One offset for every time becomes the same adjustment on each
        String oldOffset = settings.getString("offsetMinutes", null);
        if (oldOffset != null) {
            int minutes = parseInt(oldOffset, 0);
            for (int prayer = CONSTANT.FAJR; prayer <= CONSTANT.ISHAA; prayer++) {
                if (minutes != 0) editor.putInt(OFFSET + prayer, minutes);
            }
            editor.remove("offsetMinutes");
        }

        // The settings screen saved the air pressure as 110 by mistake before 3.0.2; the calculation assumes 1010 millibars
        if ("110".equals(settings.getString(PRESSURE, null))) editor.putString(PRESSURE, "1010");

        // Someone who set a location in 3.x has nothing to set up, and keeps that location until they ask for updates
        if (settings.contains(LATITUDE) && !settings.contains(SETUP_DONE)) {
            editor.putBoolean(SETUP_DONE, true).putBoolean(AUTO_LOCATION, false);
        }

        editor.putInt(VERSION, CURRENT_VERSION).apply();
    }

    public static int parseInt(String value, int defaultValue) {
        try {
            return value != null ? Integer.parseInt(value.trim()) : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private Prefs() {
    }
}

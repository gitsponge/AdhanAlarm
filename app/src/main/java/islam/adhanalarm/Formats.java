package islam.adhanalarm;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Text the screens, widgets and notifications share
 */
public final class Formats {

    /** For example "51.51° N, 0.13° W" */
    public static String coordinates(Context context, double latitude, double longitude) {
        NumberFormat number = NumberFormat.getNumberInstance();
        number.setMinimumFractionDigits(2);
        number.setMaximumFractionDigits(2);
        return context.getString(R.string.coordinates,
                context.getString(latitude < 0 ? R.string.latitude_south : R.string.latitude_north, number.format(Math.abs(latitude))),
                context.getString(longitude < 0 ? R.string.longitude_west : R.string.longitude_east, number.format(Math.abs(longitude))));
    }

    public static String savedCoordinates(Context context, SharedPreferences settings) {
        return coordinates(context, getDouble(settings, Prefs.LATITUDE, Prefs.DEFAULT_LATITUDE), getDouble(settings, Prefs.LONGITUDE, Prefs.DEFAULT_LONGITUDE));
    }

    /** The place's name, or its coordinates when no city is near enough to name it */
    public static String place(Context context, SharedPreferences settings) {
        String name = Prefs.getPlaceName(settings);
        return name.isEmpty() ? savedCoordinates(context, settings) : name;
    }

    /** What the place's name means: found nearby with the bundled map, or not found */
    public static String placeNote(Context context, SharedPreferences settings) {
        return context.getString(Prefs.getPlaceName(settings).isEmpty() ? R.string.place_note_none : R.string.place_note_city);
    }

    /** For example "in 1 h 29 min" */
    public static String countdown(Context context, long millis) {
        long minutes = (millis + 59999) / 60000; // Round up, so it never says 0 before the time
        if (minutes <= 0) return context.getString(R.string.countdown_now);
        if (minutes < 60) return context.getString(R.string.countdown_minutes, (int) minutes);
        return context.getString(R.string.countdown_hours_minutes, (int) (minutes / 60), (int) (minutes % 60));
    }

    /** For example "+2 min" or "−3 min", or "None" for no adjustment */
    public static String adjustment(Context context, int minutes) {
        if (minutes == 0) return context.getString(R.string.adjust_none);
        String number = NumberFormat.getIntegerInstance().format(Math.abs(minutes));
        return context.getString(R.string.adjust_minutes, (minutes > 0 ? "+" : "−") + number);
    }

    /** One of the 16 compass points, for example "ESE" */
    public static String compassPoint(Context context, double degrees) {
        String[] points = context.getResources().getStringArray(R.array.compass_points);
        int index = (int) Math.round(((degrees % 360) + 360) % 360 / 22.5) % 16;
        return points[index];
    }

    public static String number(double value, int fractionDigits) {
        NumberFormat number = NumberFormat.getNumberInstance(Locale.getDefault());
        number.setMaximumFractionDigits(fractionDigits);
        number.setMinimumFractionDigits(0);
        return number.format(value);
    }

    /**
     * A number as people type it in their own language, with their digits (٥١٫٥) or a decimal comma (51,5),
     * in the plain form Java reads, or null if it isn't one
     */
    public static String plainNumber(String text) {
        if (text == null) return null;
        StringBuilder plain = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int digit = Character.digit(c, 10);
            if (digit >= 0) {
                plain.append((char) ('0' + digit));
            } else if (c == '.' || c == ',' || c == '\u066B') { // The last is the Arabic decimal point
                plain.append('.');
            } else if (c == '-' || c == '\u2212' || c == '+') {
                plain.append(c == '+' ? '+' : '-');
            } else if (!Character.isWhitespace(c) && !Character.isSpaceChar(c) && Character.getType(c) != Character.FORMAT) {
                return null; // Direction marks and spaces are left out; anything else isn't part of a number
            }
        }
        try {
            Double.parseDouble(plain.toString());
            return plain.toString();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static double getDouble(SharedPreferences settings, String key, double defaultValue) {
        try {
            String value = settings.getString(key, null);
            return value != null ? Double.parseDouble(value) : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private Formats() {
    }
}

package islam.adhanalarm.handler;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateFormat;

import net.sourceforge.jitl.Jitl;
import net.sourceforge.jitl.Method;
import net.sourceforge.jitl.Prayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.R;

public class ScheduleHandler {

    private SharedPreferences mSettings;
    private List<ScheduleListener> mCallbackList;

    private GregorianCalendar[] mSchedule = new GregorianCalendar[7];
    private boolean[] mExtremes = new boolean[7];
    private fi.joensuu.joyds1.calendar.Calendar mHijriDate;

    public ScheduleHandler(SharedPreferences settings) {
        mCallbackList = new ArrayList<>();
        mSettings = settings;

        // If there is no calculation method set, we select one based on the commonly used one in the country

        if (!mSettings.contains("calculationMethodsIndex")) {
            try {
                String country = Locale.getDefault().getISO3Country().toUpperCase();

                for (int i = 0; i < CONSTANT.CALCULATION_METHOD_COUNTRY_CODES.length; i++) {
                    if (Arrays.asList(CONSTANT.CALCULATION_METHOD_COUNTRY_CODES[i]).contains(country)) {
                        mSettings.edit().putString("calculationMethodsIndex", Integer.toString(i)).apply();
                        break;
                    }
                }
            } catch(Exception ex) {
                // Wasn't set, oh well we'll use DEFAULT_CALCULATION_METHOD later
            }
        }

        update();
    }

    public void update() {
        update(new GregorianCalendar());
    }

    /**
     * Works out the times around the given moment, which tests can choose
     */
    void update(Calendar now) {
        Method method = CONSTANT.CALCULATION_METHODS[getIndex("calculationMethodsIndex", CONSTANT.DEFAULT_CALCULATION_METHOD, CONSTANT.CALCULATION_METHODS.length)].copy();
        method.setRound(CONSTANT.ROUNDING_TYPES[getIndex("roundingTypesIndex", CONSTANT.DEFAULT_ROUNDING_TYPE, CONSTANT.ROUNDING_TYPES.length)]);

        // Far north in summer Isha can come after midnight. It still belongs to the evening before, so keep that evening's times until it has passed.
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);
        setSchedule(yesterday, method);
        if (!now.before(mSchedule[CONSTANT.ISHAA])) {
            setSchedule(now, method);
        }

        mHijriDate = new fi.joensuu.joyds1.calendar.IslamicCalendar();

        for (ScheduleListener callback : mCallbackList) {
            callback.onUpdated(this);
        }
    }

    private void setSchedule(Calendar day, Method method) {
        // Each day's times use that day's GMT offset, and the next fajr the next day's, in case daylight savings starts or ends overnight
        GregorianCalendar noon = new GregorianCalendar(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH), 12, 0);
        GregorianCalendar nextNoon = (GregorianCalendar) noon.clone();
        nextNoon.add(Calendar.DAY_OF_MONTH, 1);

        Prayer[] dayPrayers = new Jitl(getLocation(getGMTOffset(noon)), method).getPrayerTimes(noon).getPrayers();
        Prayer nextFajr = new Jitl(getLocation(getGMTOffset(nextNoon)), method).getNextDayFajr(noon);
        Prayer[] allTimes = new Prayer[]{dayPrayers[0], dayPrayers[1], dayPrayers[2], dayPrayers[3], dayPrayers[4], dayPrayers[5], nextFajr};

        for (short i = CONSTANT.FAJR; i <= CONSTANT.NEXT_FAJR; i++) {
            mSchedule[i] = new GregorianCalendar(noon.get(Calendar.YEAR), noon.get(Calendar.MONTH), noon.get(Calendar.DAY_OF_MONTH), allTimes[i].getHour(), allTimes[i].getMinute(), allTimes[i].getSecond());
            if (i == CONSTANT.NEXT_FAJR || (i > CONSTANT.FAJR && mSchedule[i].before(mSchedule[i - 1]))) {
                mSchedule[i].add(Calendar.DAY_OF_MONTH, 1); // Tomorrow's fajr, or a time that has gone past midnight
            }
            mExtremes[i] = allTimes[i].isExtreme();
        }

        int offset = getInt("offsetMinutes", 0);
        for (GregorianCalendar time : mSchedule) {
            time.add(Calendar.MINUTE, offset);
        }
    }

    public boolean getIsExtreme(short i) {
        return mExtremes[i];
    }

    public String getFormattedTime(short i) {
        boolean isAMPM = getInt("timeFormatIndex", CONSTANT.DEFAULT_TIME_FORMAT) == CONSTANT.DEFAULT_TIME_FORMAT;
        if (mSchedule[i] == null) {
            return "";
        }
        Date time = mSchedule[i].getTime();
        if (time == null) {
            return "";
        }
        String formattedTime = DateFormat.format(isAMPM ? "hh:mm a" : "HH:mm", time).toString();
        if (isAMPM && (formattedTime.startsWith("0") || formattedTime.startsWith("٠"))) {
            formattedTime = " " + formattedTime.substring(1);
        }
        if (getIsExtreme(i)) {
            formattedTime += " *";
        }
        return formattedTime;
    }

    public short getNextTimeIndex() {
        return getNextTimeIndex(new GregorianCalendar());
    }

    short getNextTimeIndex(Calendar now) {
        if(now.before(mSchedule[CONSTANT.FAJR])) return CONSTANT.FAJR;
        for(short i = CONSTANT.FAJR; i < CONSTANT.NEXT_FAJR; i++) {
            if(!now.before(mSchedule[i]) && now.before(mSchedule[i + 1])) {
                return ++i;
            }
        }
        return CONSTANT.NEXT_FAJR;
    }

    public GregorianCalendar getNextTime() {
        return mSchedule[getNextTimeIndex()];
    }

    public short getNotificationType(short i) {
        return (short) getInt("notificationMethod" + i, CONSTANT.NOTIFICATION_DEFAULT);
    }

    private boolean getIsCurrentlyAfterSunset() {
        Calendar now = new GregorianCalendar();
        Calendar maghrib = mSchedule[CONSTANT.MAGHRIB];
        // Only the same day's sunset starts the next Islamic date. After midnight the calendar date has moved on by itself.
        return now.after(maghrib) && now.get(Calendar.YEAR) == maghrib.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == maghrib.get(Calendar.DAY_OF_YEAR);
    }

    public String getCurrentHijriDate(Context context) {
        boolean addedDay = false;
        if(getIsCurrentlyAfterSunset()) {
            addedDay = true;
            mHijriDate.addDays(1);
        }
        String day = String.valueOf(mHijriDate.getDay());
        String month = context.getResources().getStringArray(R.array.hijri_months)[mHijriDate.getMonth() - 1];
        String year = String.valueOf(mHijriDate.getYear());
        if(addedDay) {
            mHijriDate.addDays(-1); // Revert to the day independent of sunset
        }
        return day + " " + month + ", " + year + " " + context.getResources().getString(R.string.anno_hegirae);
    }

    public net.sourceforge.jitl.astro.Location getLocation() {
        return getLocation(getGMTOffset(new GregorianCalendar()));
    }

    private net.sourceforge.jitl.astro.Location getLocation(double gmtOffset) {
        net.sourceforge.jitl.astro.Location location = new net.sourceforge.jitl.astro.Location(
                getFloat("latitude", 43.67f),
                getFloat("longitude", -79.417f),
                gmtOffset,
                0
        );
        location.setSeaLevel(Math.max(0, getFloat("altitude", 0)));
        location.setPressure(getFloat("pressure", 1010));
        location.setTemperature(getFloat("temperature", 10));
        return location;
    }

    // A setting that can't be read falls back to its default, so a bad value never stops the alarms

    private float getFloat(String key, float defaultValue) {
        String value = mSettings.getString(key, null);
        try {
            return value != null ? Float.parseFloat(value) : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private int getInt(String key, int defaultValue) {
        String value = mSettings.getString(key, null);
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private int getIndex(String key, int defaultValue, int count) {
        int index = getInt(key, defaultValue);
        return index >= 0 && index < count ? index : defaultValue;
    }

    private static double getGMTOffset(Calendar time) {
        int gmtOffset = time.getTimeZone().getOffset(time.getTimeInMillis());
        return gmtOffset / 3600000.0; // Not integer division, so half hour time zones like India's work
    }
    public static boolean getIsDaylightSavings() {
        Calendar now = new GregorianCalendar();
        return now.getTimeZone().inDaylightTime(now.getTime());
    }

    public void addListener(ScheduleListener callback) {
        mCallbackList.add(callback);
    }

    public interface ScheduleListener {

        /**
         * This method is called after the getScheduleHandler is updated
         */
        void onUpdated(ScheduleHandler today);
    }
}
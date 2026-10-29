package islam.adhanalarm.handler;

import android.content.Context;
import android.content.SharedPreferences;

import net.sourceforge.jitl.Jitl;
import net.sourceforge.jitl.Method;
import net.sourceforge.jitl.Prayer;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.Prefs;
import islam.adhanalarm.R;

public class ScheduleHandler {

    private SharedPreferences mSettings;
    private List<ScheduleListener> mCallbackList;

    private GregorianCalendar[] mSchedule = new GregorianCalendar[7];
    private boolean[] mExtremes = new boolean[7];
    private GregorianCalendar mPreviousIshaa; // The night before's, which comes before this schedule's fajr
    private fi.joensuu.joyds1.calendar.Calendar mHijriDate;

    public ScheduleHandler(SharedPreferences settings) {
        mCallbackList = new ArrayList<>();
        mSettings = settings;
        update();
    }

    public void update() {
        update(new GregorianCalendar());
    }

    /**
     * Works out the times around the given moment, which tests can choose
     */
    void update(Calendar now) {
        int roundingIndex = getIndex(Prefs.ROUNDING, CONSTANT.DEFAULT_ROUNDING_TYPE, CONSTANT.ROUNDING_TYPES.length);
        Method method = CONSTANT.createMethod(Prefs.getMethod(mSettings), Prefs.isHanafi(mSettings), Prefs.getHighLatitude(mSettings), CONSTANT.ROUNDING_TYPES[roundingIndex]);

        // Far north in summer Isha can come after midnight. It still belongs to the evening before, so keep that evening's times until it has passed.
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);
        setSchedule(yesterday, method);
        GregorianCalendar previousIshaa = mSchedule[CONSTANT.ISHAA];
        if (!now.before(mSchedule[CONSTANT.ISHAA])) {
            setSchedule(now, method);
            mPreviousIshaa = previousIshaa;
        } else {
            mPreviousIshaa = null;
        }

        mHijriDate = new fi.joensuu.joyds1.calendar.IslamicCalendar();
        mHijriDate.addDays(Prefs.getHijriAdjust(mSettings));

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

        // The method's own minutes, then each prayer's adjustment to match a mosque's timetable
        String methodName = Prefs.getMethod(mSettings);
        int[] methodAdjustments = CONSTANT.getMethodAdjustments(methodName);
        for (short i = CONSTANT.FAJR; i <= CONSTANT.NEXT_FAJR; i++) {
            short prayer = i == CONSTANT.NEXT_FAJR ? CONSTANT.FAJR : i;
            mSchedule[i].add(Calendar.MINUTE, methodAdjustments[prayer] + Prefs.getOffset(mSettings, prayer));
            if (CONSTANT.METHOD_DIYANET.equals(methodName)) {
                // As Diyanet's timetable does: to the nearest minute, but maghrib up, so a fast is never broken early
                roundToMinute(mSchedule[i], i == CONSTANT.MAGHRIB);
            }
        }
    }

    private static void roundToMinute(Calendar time, boolean up) {
        int seconds = time.get(Calendar.SECOND);
        time.set(Calendar.SECOND, 0);
        time.set(Calendar.MILLISECOND, 0);
        if (seconds > 0 && (up || seconds >= 30)) {
            time.add(Calendar.MINUTE, 1);
        }
    }

    public boolean getIsExtreme(short i) {
        return mExtremes[i];
    }

    public GregorianCalendar getTime(short i) {
        return mSchedule[i];
    }

    /**
     * The time before the given one: the night before's isha comes before fajr
     */
    public GregorianCalendar getPreviousTime(short i) {
        return i > CONSTANT.FAJR ? mSchedule[i - 1] : mPreviousIshaa;
    }

    public String getFormattedTime(short i, DateFormat format) {
        return format.format(mSchedule[i].getTime()) + (getIsExtreme(i) ? " *" : "");
    }

    public boolean hasExtremeTimes() {
        for (short i = CONSTANT.FAJR; i <= CONSTANT.ISHAA; i++) {
            if (mExtremes[i]) return true;
        }
        return false;
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

    /**
     * The prayer whose time it is now, or -1 between sunrise and dhuhr and before fajr
     */
    public short getCurrentPrayerIndex() {
        switch (getNextTimeIndex()) {
            case CONSTANT.SUNRISE: return CONSTANT.FAJR;
            case CONSTANT.ASR: return CONSTANT.DHUHR;
            case CONSTANT.MAGHRIB: return CONSTANT.ASR;
            case CONSTANT.ISHAA: return CONSTANT.MAGHRIB;
            case CONSTANT.NEXT_FAJR: return CONSTANT.ISHAA;
            default: return -1;
        }
    }

    public short getNotificationType(short i) {
        return Prefs.getAlert(mSettings, i);
    }

    private boolean getIsCurrentlyAfterSunset() {
        Calendar now = new GregorianCalendar();
        Calendar maghrib = mSchedule[CONSTANT.MAGHRIB];
        // Only the same day's sunset starts the next Islamic date. After midnight the calendar date has moved on by itself.
        return now.after(maghrib) && now.get(Calendar.YEAR) == maghrib.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == maghrib.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Today's Islamic date, which starts at sunset, for example "16 Jumādā al-Ūlā 1448"
     */
    public String getHijriDate(Context context) {
        boolean addedDay = false;
        if(getIsCurrentlyAfterSunset()) {
            addedDay = true;
            mHijriDate.addDays(1);
        }
        // In the language's own digits, like the times beside it: ١٦ in Arabic
        String day = String.format(Locale.getDefault(), "%d", mHijriDate.getDay());
        String month = context.getResources().getStringArray(R.array.hijri_months)[mHijriDate.getMonth() - 1];
        String year = String.format(Locale.getDefault(), "%d", mHijriDate.getYear());
        if(addedDay) {
            mHijriDate.addDays(-1); // Revert to the day independent of sunset
        }
        return context.getString(R.string.hijri_date, day, month, year);
    }

    /**
     * Today's day of the Islamic month, which starts at sunset
     */
    public int getHijriDay() {
        boolean afterSunset = getIsCurrentlyAfterSunset();
        if (afterSunset) {
            mHijriDate.addDays(1);
        }
        int day = mHijriDate.getDay();
        if (afterSunset) {
            mHijriDate.addDays(-1);
        }
        return day;
    }

    public net.sourceforge.jitl.astro.Location getLocation() {
        return getLocation(getGMTOffset(new GregorianCalendar()));
    }

    private net.sourceforge.jitl.astro.Location getLocation(double gmtOffset) {
        net.sourceforge.jitl.astro.Location location = new net.sourceforge.jitl.astro.Location(
                getFloat(Prefs.LATITUDE, (float) Prefs.DEFAULT_LATITUDE),
                getFloat(Prefs.LONGITUDE, (float) Prefs.DEFAULT_LONGITUDE),
                gmtOffset,
                0
        );
        location.setSeaLevel(Math.max(0, getFloat(Prefs.ALTITUDE, 0)));
        location.setPressure(getFloat(Prefs.PRESSURE, 1010));
        location.setTemperature(getFloat(Prefs.TEMPERATURE, 10));
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

    private int getIndex(String key, int defaultValue, int count) {
        int index = Prefs.parseInt(mSettings.getString(key, null), defaultValue);
        return index >= 0 && index < count ? index : defaultValue;
    }

    private static double getGMTOffset(Calendar time) {
        int gmtOffset = time.getTimeZone().getOffset(time.getTimeInMillis());
        return gmtOffset / 3600000.0; // Not integer division, so half hour time zones like India's work
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

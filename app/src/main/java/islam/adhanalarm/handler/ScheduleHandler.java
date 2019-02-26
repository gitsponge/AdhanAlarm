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
        String calculationMethodsIndex = mSettings.getString("calculationMethodsIndex", Integer.toString(CONSTANT.DEFAULT_CALCULATION_METHOD));
        Method method = CONSTANT.CALCULATION_METHODS[calculationMethodsIndex != null ? Integer.parseInt(calculationMethodsIndex) : CONSTANT.DEFAULT_CALCULATION_METHOD].copy();
        String roundingTypesIndex = mSettings.getString("roundingTypesIndex", Integer.toString(CONSTANT.DEFAULT_ROUNDING_TYPE));
        method.setRound(CONSTANT.ROUNDING_TYPES[roundingTypesIndex != null ? Integer.parseInt(roundingTypesIndex) : CONSTANT.DEFAULT_ROUNDING_TYPE]);

        net.sourceforge.jitl.astro.Location location = getLocation();

        GregorianCalendar day = new GregorianCalendar();

        Jitl itl = new Jitl(location, method);
        Prayer[] dayPrayers = itl.getPrayerTimes(day).getPrayers();
        Prayer[] allTimes = new Prayer[]{dayPrayers[0], dayPrayers[1], dayPrayers[2], dayPrayers[3], dayPrayers[4], dayPrayers[5], itl.getNextDayFajr(day)};

        String offsetMinutes = mSettings.getString("offsetMinutes", "0");
        int offset = offsetMinutes != null ? Integer.parseInt(offsetMinutes) : 0;
        for(short i = CONSTANT.FAJR; i <= CONSTANT.NEXT_FAJR; i++) { // Set the times on the getScheduleHandler
            mSchedule[i] = new GregorianCalendar(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH), allTimes[i].getHour(), allTimes[i].getMinute(), allTimes[i].getSecond());
            mSchedule[i].add(Calendar.MINUTE, offset);
            mExtremes[i] = allTimes[i].isExtreme();
        }
        mSchedule[CONSTANT.NEXT_FAJR].add(Calendar.DAY_OF_MONTH, 1); // Next fajr is tomorrow

        mHijriDate = new fi.joensuu.joyds1.calendar.IslamicCalendar();

        for (ScheduleListener callback : mCallbackList) {
            callback.onUpdated(this);
        }
    }

    public boolean getIsExtreme(short i) {
        return mExtremes[i];
    }

    public String getFormattedTime(short i) {
        String timeFormatIndex = mSettings.getString("timeFormatIndex", Integer.toString(CONSTANT.DEFAULT_TIME_FORMAT));
        boolean isAMPM = timeFormatIndex == null || Integer.parseInt(timeFormatIndex) == CONSTANT.DEFAULT_TIME_FORMAT;
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
        Calendar now = new GregorianCalendar();
        if(now.before(mSchedule[CONSTANT.FAJR])) return CONSTANT.FAJR;
        for(short i = CONSTANT.FAJR; i < CONSTANT.NEXT_FAJR; i++) {
            if(now.after(mSchedule[i]) && now.before(mSchedule[i + 1])) {
                return ++i;
            }
        }
        return CONSTANT.NEXT_FAJR;
    }

    public GregorianCalendar getNextTime() {
        return mSchedule[getNextTimeIndex()];
    }

    public short getNotificationType(short i) {
        String notificationMethod = mSettings.getString("notificationMethod" + i, Short.toString(CONSTANT.NOTIFICATION_DEFAULT));
        return Short.parseShort(notificationMethod);
    }

    private boolean getIsCurrentlyAfterSunset() {
        Calendar now = new GregorianCalendar();
        return now.after(mSchedule[CONSTANT.MAGHRIB]);
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
        String latitude = mSettings.getString("latitude", "43.67");
        String longitude = mSettings.getString("longitude", "-79.417");
        net.sourceforge.jitl.astro.Location location = new net.sourceforge.jitl.astro.Location(
                latitude != null ? Float.parseFloat(latitude) : 43.67f,
                longitude != null ? Float.parseFloat(longitude) : -79.417f,
                getGMTOffset(),
                0
        );
        String altitude = mSettings.getString("altitude", "0");
        String pressure = mSettings.getString("pressure", "1010");
        String temperature = mSettings.getString("temperature", "10");
        location.setSeaLevel(altitude == null || Float.parseFloat(altitude) < 0 ? 0 : Float.parseFloat(altitude));
        location.setPressure(pressure != null ? Float.parseFloat(pressure) : 1010);
        location.setTemperature(temperature != null ? Float.parseFloat(temperature) : 10);
        return location;
    }

    private static double getGMTOffset() {
        Calendar now = new GregorianCalendar();
        int gmtOffset = now.getTimeZone().getOffset(now.getTimeInMillis());
        return gmtOffset / 3600000;
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
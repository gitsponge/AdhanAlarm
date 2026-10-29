package islam.adhanalarm.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.FakeSettings;

public class ScheduleHandlerTest {

    private final TimeZone mOriginal = TimeZone.getDefault();

    @After
    public void restoreTimeZone() {
        TimeZone.setDefault(mOriginal);
    }

    private static ScheduleHandler at(String zone, Calendar now, Object... settings) {
        ScheduleHandler handler = new ScheduleHandler(new FakeSettings(settings));
        handler.update(now);
        return handler;
    }

    private static Calendar time(int year, int month, int day, int hour, int minute) {
        return new GregorianCalendar(year, month - 1, day, hour, minute);
    }

    private static String hhmm(Calendar time) {
        return String.format("%02d-%02d %02d:%02d", time.get(Calendar.MONTH) + 1, time.get(Calendar.DAY_OF_MONTH), time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE));
    }

    @Test
    public void ishaAfterMidnightStaysWithTheEvening() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"));
        Object[] edinburgh = {"latitude", "55.95", "longitude", "-3.19", "method", "mwl", "highLatitude", "goodDay"};

        Calendar afterMaghrib = time(2026, 6, 21, 22, 10);
        ScheduleHandler handler = at("Europe/London", afterMaghrib, edinburgh);
        assertEquals(CONSTANT.ISHAA, handler.getNextTimeIndex(afterMaghrib));
        assertEquals("06-22 00:08", hhmm(handler.getTime(CONSTANT.ISHAA)));

        Calendar beforeIsha = time(2026, 6, 22, 0, 5);
        handler = at("Europe/London", beforeIsha, edinburgh);
        assertEquals(CONSTANT.ISHAA, handler.getNextTimeIndex(beforeIsha));

        Calendar afterIsha = time(2026, 6, 22, 0, 9);
        handler = at("Europe/London", afterIsha, edinburgh);
        assertEquals(CONSTANT.FAJR, handler.getNextTimeIndex(afterIsha));
        assertEquals("06-22 00:08", hhmm(handler.getPreviousTime(CONSTANT.FAJR))); // The night before's isha leads into fajr
    }

    @Test
    public void oneSeventhOfTheNightKeepsIshaBeforeMidnight() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"));
        Calendar now = time(2026, 6, 21, 22, 10);
        ScheduleHandler handler = at("Europe/London", now, "latitude", "55.95", "longitude", "-3.19", "method", "mwl");
        assertEquals("06-21 22:58", hhmm(handler.getTime(CONSTANT.ISHAA)));
        assertEquals("06-21 03:31", hhmm(handler.getTime(CONSTANT.FAJR)));
        assertTrue(handler.hasExtremeTimes());
    }

    @Test
    public void ordinaryDaysHaveNoEstimatedTimes() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"));
        Calendar now = time(2026, 10, 28, 15, 12);
        ScheduleHandler handler = at("Europe/London", now, "latitude", "51.5074", "longitude", "-0.1278", "method", "mwl");
        assertFalse(handler.hasExtremeTimes());
        assertEquals("10-28 16:41", hhmm(handler.getTime(CONSTANT.MAGHRIB)));
        assertEquals(CONSTANT.MAGHRIB, handler.getNextTimeIndex(now));
    }

    @Test
    public void fajrIsRightOnTheNightTheClocksChange() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"));
        Object[] london = {"latitude", "51.51", "longitude", "-0.13", "method", "mwl"};
        Calendar beforeChange = time(2026, 3, 29, 0, 30);
        Calendar afterChange = time(2026, 3, 29, 3, 30);
        assertEquals(hhmm(at("Europe/London", afterChange, london).getTime(CONSTANT.FAJR)), hhmm(at("Europe/London", beforeChange, london).getTime(CONSTANT.FAJR)));
    }

    @Test
    public void eachPrayerHasItsOwnAdjustment() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"));
        Calendar now = time(2026, 10, 28, 12, 0);
        ScheduleHandler plain = at("Europe/London", now, "latitude", "51.5074", "longitude", "-0.1278");
        ScheduleHandler adjusted = at("Europe/London", now, "latitude", "51.5074", "longitude", "-0.1278", "offset4", 2, "offset0", -3);
        assertEquals(2 * 60000L, adjusted.getTime(CONSTANT.MAGHRIB).getTimeInMillis() - plain.getTime(CONSTANT.MAGHRIB).getTimeInMillis());
        assertEquals(-3 * 60000L, adjusted.getTime(CONSTANT.FAJR).getTimeInMillis() - plain.getTime(CONSTANT.FAJR).getTimeInMillis());
        assertEquals(-3 * 60000L, adjusted.getTime(CONSTANT.NEXT_FAJR).getTimeInMillis() - plain.getTime(CONSTANT.NEXT_FAJR).getTimeInMillis());
        assertEquals(plain.getTime(CONSTANT.DHUHR), adjusted.getTime(CONSTANT.DHUHR));
    }

    @Test
    public void hanafiAsrIsLater() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Karachi"));
        Calendar now = time(2026, 10, 28, 12, 0);
        ScheduleHandler standard = at("Asia/Karachi", now, "latitude", "24.86", "longitude", "67.01", "method", "karachi");
        ScheduleHandler hanafi = at("Asia/Karachi", now, "latitude", "24.86", "longitude", "67.01", "method", "karachi", "asr", "hanafi");
        assertTrue(hanafi.getTime(CONSTANT.ASR).after(standard.getTime(CONSTANT.ASR)));
        assertEquals(standard.getTime(CONSTANT.MAGHRIB), hanafi.getTime(CONSTANT.MAGHRIB));
    }

    @Test
    public void unreadableSettingsFallBackInsteadOfCrashing() {
        Calendar now = time(2026, 10, 28, 12, 0);
        for (String key : new String[]{"latitude", "longitude", "altitude", "pressure", "temperature", "roundingTypesIndex", "notificationMethod0"}) {
            for (String bad : new String[]{"", "-", ".", "x"}) {
                ScheduleHandler handler = at("UTC", now, key, bad);
                handler.getNextTimeIndex(now);
                handler.getNotificationType(CONSTANT.FAJR);
            }
        }
    }

    @Test
    public void countriesGetTheirUsualMethod() {
        assertEquals(CONSTANT.METHOD_KARACHI, CONSTANT.getDefaultMethod("PK"));
        assertEquals(CONSTANT.METHOD_ISNA, CONSTANT.getDefaultMethod("us"));
        assertEquals(CONSTANT.METHOD_UMM_AL_QURA, CONSTANT.getDefaultMethod("SA"));
        assertEquals(CONSTANT.METHOD_SINGAPORE, CONSTANT.getDefaultMethod("ID"));
        assertEquals(CONSTANT.METHOD_MWL, CONSTANT.getDefaultMethod("GB"));
        assertEquals(CONSTANT.METHOD_MWL, CONSTANT.getDefaultMethod(null));
    }
}

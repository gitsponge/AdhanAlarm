package islam.adhanalarm.clock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.handler.ScheduleHandler;
import islam.adhanalarm.handler.Schedules;
import islam.adhanalarm.widget.DialArt;

/**
 * What the prayer dial full screen says under the time, through a day and through a year
 */
public class ClockStateTest {

    private static final long MINUTE = 60000, DAY = 1440 * MINUTE;
    private final TimeZone mOriginal = TimeZone.getDefault();

    @After
    public void restoreTimeZone() {
        TimeZone.setDefault(mOriginal);
    }

    /** A day's times from minutes after midnight: fajr, sunrise, dhuhr, asr, maghrib, isha, then the next fajr */
    private static long[] day(int fajr, int sunrise, int dhuhr, int asr, int maghrib, int ishaa, int nextFajr) {
        return new long[]{fajr * MINUTE, sunrise * MINUTE, dhuhr * MINUTE, asr * MINUTE, maghrib * MINUTE, ishaa * MINUTE, nextFajr * MINUTE};
    }

    private static int hm(int hour, int minute) {
        return hour * 60 + minute;
    }

    // An autumn day: fajr's and maghrib's windows are short, the rest long
    private static final long[] AUTUMN = day(hm(6, 5), hm(7, 21), hm(12, 40), hm(15, 33), hm(17, 57), hm(19, 14), hm(30, 6));

    @Test
    public void beforeAShortWindowItSaysWhenItStarts() {
        ClockState state = ClockState.at(hm(16, 40) * MINUTE, AUTUMN, 0);
        assertEquals(ClockState.NEXT, state.kind);
        assertEquals(3, state.window); // Maghrib
        assertEquals(CONSTANT.MAGHRIB, state.to);
        assertEquals(77 * MINUTE, state.millis);
        assertTrue(state.shortWindow);
    }

    @Test
    public void insideAShortWindowItSaysWhenItEnds() {
        ClockState maghrib = ClockState.at(hm(18, 22) * MINUTE, AUTUMN, 0);
        assertEquals(ClockState.ENDS, maghrib.kind);
        assertEquals(3, maghrib.window);
        assertEquals(CONSTANT.ISHAA, maghrib.to);
        assertEquals(52 * MINUTE, maghrib.millis);

        ClockState fajr = ClockState.at(hm(6, 30) * MINUTE, AUTUMN, 0);
        assertEquals(ClockState.ENDS, fajr.kind);
        assertEquals(0, fajr.window);
        assertEquals(CONSTANT.SUNRISE, fajr.to); // Fajr's time ends at sunrise
    }

    @Test
    public void insideALongWindowItSaysWhatsNext() {
        ClockState dhuhr = ClockState.at(hm(13, 0) * MINUTE, AUTUMN, 0);
        assertEquals(ClockState.NEXT, dhuhr.kind);
        assertEquals(2, dhuhr.window); // Asr
        assertFalse(dhuhr.shortWindow);

        ClockState morning = ClockState.at(hm(8, 0) * MINUTE, AUTUMN, 0); // Between sunrise and dhuhr no prayer is due
        assertEquals(ClockState.NEXT, morning.kind);
        assertEquals(1, morning.window);

        ClockState evening = ClockState.at(hm(20, 0) * MINUTE, AUTUMN, 0);
        assertEquals(ClockState.NEXT, evening.kind);
        assertEquals(0, evening.window);
        assertEquals(CONSTANT.NEXT_FAJR, evening.to);
        assertTrue(evening.shortWindow); // Fajr's window is short
    }

    @Test
    public void aShortIshaFarNorthCountsDownToFajr() {
        // Midsummer far north: isha at 23:30, fajr at 1:00, so isha's window is an hour and a half
        long[] times = day(hm(1, 0), hm(3, 40), hm(13, 20), hm(17, 50), hm(22, 55), hm(23, 30), hm(25, 2));
        ClockState state = ClockState.at(hm(0, 15) * MINUTE, times, hm(23, 31) * MINUTE - DAY);
        assertEquals(ClockState.ENDS, state.kind);
        assertEquals(4, state.window);
        assertEquals(CONSTANT.FAJR, state.to);
        assertEquals(45 * MINUTE, state.millis);
    }

    @Test
    public void itDimsFromTheMiddleOfTheNightUntilFajr() {
        // Halfway from 17:57 to 6:06 is 0:01
        assertFalse(ClockState.isNight(hm(23, 59) * MINUTE, AUTUMN));
        assertTrue(ClockState.isNight(hm(24, 30) * MINUTE, AUTUMN));
        assertTrue(ClockState.isNight(hm(0, 30) * MINUTE, AUTUMN)); // After midnight, from the new day's times
        assertTrue(ClockState.isNight(hm(6, 4) * MINUTE, AUTUMN));
        assertFalse(ClockState.isNight(hm(6, 5) * MINUTE, AUTUMN));
        assertFalse(ClockState.isNight(hm(12, 0) * MINUTE, AUTUMN));
    }

    @Test
    public void throughAYearItAlwaysPointsAhead() {
        Object[][] places = {{"Europe/Oslo", "59.91", "10.75"}, {"Europe/London", "51.51", "-0.13"}, {"Asia/Singapore", "1.35", "103.82"}, {"Australia/Sydney", "-33.87", "151.21"}};
        for (Object[] place : places) {
            TimeZone.setDefault(TimeZone.getTimeZone((String) place[0]));
            for (int dayOfYear = 0; dayOfYear < 365; dayOfYear += 9) {
                for (int minute = 0; minute < 1440; minute += 17) {
                    Calendar now = new GregorianCalendar(2026, Calendar.JANUARY, 1, 0, 0);
                    now.add(Calendar.DAY_OF_YEAR, dayOfYear);
                    now.add(Calendar.MINUTE, minute);
                    ScheduleHandler schedule = Schedules.at(now, "latitude", place[1], "longitude", place[2], "method", "mwl");
                    long[] times = new long[CONSTANT.NEXT_FAJR + 1];
                    for (short i = CONSTANT.FAJR; i <= CONSTANT.NEXT_FAJR; i++) times[i] = schedule.getTime(i).getTimeInMillis();
                    Calendar previous = schedule.getPreviousTime(CONSTANT.FAJR);
                    long at = now.getTimeInMillis();
                    ClockState state = ClockState.at(at, times, previous != null ? previous.getTimeInMillis() : 0);
                    String what = place[0] + " day " + dayOfYear + " minute " + minute;
                    assertTrue(what + ": counts down to a time ahead", state.millis > 0);
                    assertTrue(what + ": to a time on the day's schedule", times[state.to] == at + state.millis);
                    if (state.kind == ClockState.NEXT) {
                        assertTrue(what + ": the next prayer, never sunrise", state.to != CONSTANT.SUNRISE);
                    } else {
                        assertTrue(what + ": only a short window ends", state.shortWindow);
                    }
                    if (state.night) {
                        assertTrue(what + ": dims only before fajr", at < times[CONSTANT.FAJR] || at >= times[CONSTANT.MAGHRIB]);
                    }
                }
            }
        }
        assertTrue(DialArt.isShort(119 * MINUTE));
    }
}

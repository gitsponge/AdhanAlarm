package islam.adhanalarm.handler;

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.FakeSettings;

/**
 * Checks the Diyanet method against Diyanet's own published timetable for Istanbul
 */
public class DiyanetTest {

    // Day of the month, then İmsak, Güneş, Öğle, İkindi, Akşam and Yatsı, as namazvakitleri.diyanet.gov.tr gives them
    private static final String[][] OCTOBER_NOVEMBER_2026 = {
            {"10-29", "05:58", "07:24", "12:53", "15:44", "18:12", "19:32"},
            {"10-30", "05:59", "07:25", "12:53", "15:43", "18:10", "19:31"},
            {"10-31", "06:01", "07:26", "12:53", "15:42", "18:09", "19:30"},
            {"11-01", "06:02", "07:28", "12:53", "15:41", "18:08", "19:29"},
            {"11-02", "06:03", "07:29", "12:53", "15:40", "18:07", "19:27"},
            {"11-03", "06:04", "07:30", "12:53", "15:39", "18:05", "19:26"},
            {"11-04", "06:05", "07:31", "12:53", "15:38", "18:04", "19:25"},
            {"11-05", "06:06", "07:32", "12:53", "15:37", "18:03", "19:24"},
            {"11-06", "06:07", "07:33", "12:53", "15:36", "18:02", "19:23"},
            {"11-07", "06:08", "07:35", "12:53", "15:35", "18:01", "19:22"},
            {"11-08", "06:09", "07:36", "12:53", "15:34", "18:00", "19:21"},
            {"11-09", "06:10", "07:37", "12:53", "15:34", "17:59", "19:21"},
            {"11-10", "06:11", "07:38", "12:53", "15:33", "17:58", "19:20"},
            {"11-11", "06:12", "07:39", "12:53", "15:32", "17:57", "19:19"},
            {"11-12", "06:13", "07:41", "12:53", "15:31", "17:56", "19:18"},
            {"11-13", "06:14", "07:42", "12:53", "15:30", "17:55", "19:17"},
            {"11-14", "06:15", "07:43", "12:53", "15:30", "17:54", "19:17"},
            {"11-15", "06:16", "07:44", "12:54", "15:29", "17:53", "19:16"},
            {"11-16", "06:17", "07:45", "12:54", "15:28", "17:52", "19:15"},
            {"11-17", "06:18", "07:47", "12:54", "15:28", "17:51", "19:15"},
            {"11-18", "06:19", "07:48", "12:54", "15:27", "17:51", "19:14"},
            {"11-19", "06:20", "07:49", "12:54", "15:27", "17:50", "19:13"},
            {"11-20", "06:21", "07:50", "12:55", "15:26", "17:49", "19:13"},
            {"11-21", "06:22", "07:51", "12:55", "15:26", "17:49", "19:12"},
            {"11-22", "06:23", "07:52", "12:55", "15:25", "17:48", "19:12"},
            {"11-23", "06:24", "07:53", "12:55", "15:25", "17:47", "19:11"},
            {"11-24", "06:25", "07:55", "12:56", "15:24", "17:47", "19:11"},
            {"11-25", "06:26", "07:56", "12:56", "15:24", "17:46", "19:11"},
            {"11-26", "06:27", "07:57", "12:56", "15:23", "17:46", "19:10"},
            {"11-27", "06:28", "07:58", "12:57", "15:23", "17:45", "19:10"},
            {"11-28", "06:29", "07:59", "12:57", "15:23", "17:45", "19:10"},
    };

    private final TimeZone mOriginal = TimeZone.getDefault();

    @After
    public void restoreTimeZone() {
        TimeZone.setDefault(mOriginal);
    }

    @Test
    public void matchesDiyanetsTimetableForIstanbul() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
        // Diyanet's own point for the city isn't published; 41° N, 29° E fits its timetable best
        assertTrue(exactTimes("41.0", "29.0") >= 170);
        assertTrue(exactTimes("41.01384", "28.94966") >= 150); // GeoNames' point for Istanbul, 4 km west
    }

    /**
     * How many of the month's times match exactly, after checking none is more than a minute out
     */
    private static int exactTimes(String latitude, String longitude) {
        FakeSettings istanbul = new FakeSettings("latitude", latitude, "longitude", longitude, "method", CONSTANT.METHOD_DIYANET);
        int exact = 0;
        for (String[] day : OCTOBER_NOVEMBER_2026) {
            int month = Integer.parseInt(day[0].substring(0, 2)), date = Integer.parseInt(day[0].substring(3));
            ScheduleHandler handler = new ScheduleHandler(istanbul);
            handler.update(new GregorianCalendar(2026, month - 1, date, 3, 0)); // Before fajr, so the schedule is that day's
            for (short i = CONSTANT.FAJR; i <= CONSTANT.ISHAA; i++) {
                Calendar time = handler.getTime(i);
                int ours = time.get(Calendar.HOUR_OF_DAY) * 60 + time.get(Calendar.MINUTE);
                int theirs = Integer.parseInt(day[i + 1].substring(0, 2)) * 60 + Integer.parseInt(day[i + 1].substring(3));
                assertTrue(day[0] + " prayer " + i + " is " + (ours - theirs) + " min out", Math.abs(ours - theirs) <= 1);
                if (ours == theirs) exact++;
            }
        }
        return exact;
    }
}

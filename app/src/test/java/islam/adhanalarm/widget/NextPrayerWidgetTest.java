package islam.adhanalarm.widget;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public class NextPrayerWidgetTest {

    private static String[] split(String pattern, Locale locale) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.set(2026, 9, 29, 13, 1, 0);
        Date oneOhOnePm = calendar.getTime();
        return NextNotificationWidgetProvider.splitTime(format, oneOhOnePm);
    }

    @Test
    public void twelveHourTimeSplitsIntoClockAndPeriod() {
        assertArrayEquals(new String[]{"1:01", "PM"}, split("h:mm a", Locale.US));
        assertArrayEquals(new String[]{"1:01", "PM"}, split("h:mm a", Locale.US)); // Newer Androids put a narrow space before PM
        assertArrayEquals(new String[]{"1:01", "오후"}, split("a h:mm", Locale.KOREA)); // The period first
    }

    @Test
    public void twentyFourHourTimeStaysWhole() {
        assertArrayEquals(new String[]{"13:01", ""}, split("HH:mm", Locale.UK));
        assertArrayEquals(new String[]{"13:01", ""}, split("H:mm", Locale.GERMANY));
    }
}

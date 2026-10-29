package islam.adhanalarm.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrayerDialTest {

    @Test
    public void noonIsAtTheTopAndTheDayRunsClockwise() {
        assertEquals(0, PrayerDial.angle(12, 0), 0.01);
        assertEquals(90, PrayerDial.angle(18, 0), 0.01); // Sunset side on the right
        assertEquals(180, PrayerDial.angle(0, 0), 0.01); // Midnight at the bottom
        assertEquals(270, PrayerDial.angle(6, 0), 0.01); // Dawn on the left
        assertEquals(70.0, PrayerDial.angle(16, 40), 0.01);
    }

    @Test
    public void windowsUnderTwoHoursAreShort() {
        assertTrue(PrayerDial.isShort(76 * 60000L)); // Fajr or Maghrib, about an hour and a quarter
        assertFalse(PrayerDial.isShort(2 * 60 * 60000L));
        assertFalse(PrayerDial.isShort(11 * 60 * 60000L)); // Isha's night
    }

    @Test
    public void theMoonFillsAndEmptiesThroughTheMonth() {
        assertTrue(PrayerDial.lit(1) > 0.05f && PrayerDial.lit(1) < 0.1f); // The first night's crescent, thin but there
        assertEquals(0.5f, PrayerDial.lit(7), 0.05f);
        assertTrue(PrayerDial.lit(14) > 0.9f && PrayerDial.lit(15) > 0.9f); // Full in the middle
        assertEquals(0.5f, PrayerDial.lit(22), 0.05f);
        assertEquals(0f, PrayerDial.lit(30), 0.001f); // Gone by the end
    }

    @Test
    public void theMoonIsLitFromTheSideItGrowsOn() {
        assertTrue(PrayerDial.isLitOnRight(5, 30)); // Growing, seen from the north
        assertFalse(PrayerDial.isLitOnRight(20, 30)); // Shrinking
        assertFalse(PrayerDial.isLitOnRight(5, -30)); // The other way round from the south
        assertTrue(PrayerDial.isLitOnRight(20, -30));
    }
}

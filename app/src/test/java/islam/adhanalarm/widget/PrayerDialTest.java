package islam.adhanalarm.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import islam.adhanalarm.CONSTANT;

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
    public void aBigDialGetsFinerRatherThanChunkier() {
        float dp = 2.625f;
        assertEquals(1 * dp, PrayerDial.unitFor(30 * PrayerDial.RIM_SHARE * dp, dp), 0.001); // A 60dp icon: a sixtieth is a dp
        assertEquals(0.5f * dp, PrayerDial.unitFor(15 * PrayerDial.RIM_SHARE * dp, dp), 0.001); // Smaller, it scales as a whole
        assertEquals(2 * dp, PrayerDial.unitFor(120 * PrayerDial.RIM_SHARE * dp, dp), 0.001); // Four times as wide, its marks only twice as thick
    }

    @Test
    public void theNextPrayersNameIsHighlighted() {
        assertEquals(0, DialTile.nextWindow(CONSTANT.FAJR));
        assertEquals(1, DialTile.nextWindow(CONSTANT.SUNRISE)); // Through fajr's window, dhuhr is the next prayer
        assertEquals(3, DialTile.nextWindow(CONSTANT.MAGHRIB));
        assertEquals(0, DialTile.nextWindow(CONSTANT.NEXT_FAJR));
        assertEquals(CONSTANT.NEXT_FAJR, DialTile.startOf(0, CONSTANT.NEXT_FAJR)); // After isha, fajr's name goes with tomorrow's time
        assertEquals(CONSTANT.FAJR, DialTile.startOf(0, CONSTANT.FAJR));
        assertEquals(CONSTANT.ISHAA, DialTile.startOf(4, CONSTANT.NEXT_FAJR));
    }

    @Test
    public void theSizeOnScreenIsDrawnSharpWhenMemoryIsShort() {
        double screen = 1.5 * 1080 * 2400 * 4 * 0.9; // A phone's widget memory, less a margin
        float[] fits = PrayerDialWidgetProvider.scales(new double[]{4.3e6, 7.4e6}, 0, screen);
        assertEquals(1, fits[0], 0);
        assertEquals(1, fits[1], 0);
        float[] tight = PrayerDialWidgetProvider.scales(new double[]{4.3e6, 12e6}, 0, screen); // A big tile, portrait and landscape
        assertEquals(1, tight[0], 0); // Portrait, as the phone is held, stays sharp
        assertTrue(tight[1] < 1 && tight[1] >= 0.5f);
        assertTrue(4.3e6 + 12e6 * tight[1] * tight[1] <= screen + 1);
        float[] huge = PrayerDialWidgetProvider.scales(new double[]{12e6, 12e6}, 1, screen); // Too much even for one
        assertEquals(huge[0], huge[1], 0);
        assertTrue(24e6 * huge[0] * huge[0] <= screen + 1);
    }

    @Test
    public void theMoonIsLitFromTheSideItGrowsOn() {
        assertTrue(PrayerDial.isLitOnRight(5, 30)); // Growing, seen from the north
        assertFalse(PrayerDial.isLitOnRight(20, 30)); // Shrinking
        assertFalse(PrayerDial.isLitOnRight(5, -30)); // The other way round from the south
        assertTrue(PrayerDial.isLitOnRight(20, -30));
    }
}

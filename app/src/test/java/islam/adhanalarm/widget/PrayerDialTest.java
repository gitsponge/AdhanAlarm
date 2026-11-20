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
        assertEquals(0, DialArt.nextWindow(CONSTANT.FAJR));
        assertEquals(1, DialArt.nextWindow(CONSTANT.SUNRISE)); // Through fajr's window, dhuhr is the next prayer
        assertEquals(3, DialArt.nextWindow(CONSTANT.MAGHRIB));
        assertEquals(0, DialArt.nextWindow(CONSTANT.NEXT_FAJR));
        assertEquals(CONSTANT.NEXT_FAJR, DialArt.startOf(0, CONSTANT.NEXT_FAJR)); // After isha, fajr's name goes with tomorrow's time
        assertEquals(CONSTANT.FAJR, DialArt.startOf(0, CONSTANT.FAJR));
        assertEquals(CONSTANT.ISHAA, DialArt.startOf(4, CONSTANT.NEXT_FAJR));
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

    @Test
    public void bigEnoughToRecogniseTheMoonGrowsWithTheFace() {
        float dp = 2.625f;
        // A 2x2 widget's face is about 57 dp from the centre; its moon used to be 9 dp across
        assertTrue(2 * PrayerDial.moonRadius(56.8f * dp) / dp >= 16);
        // The full-screen dial's is about 164 dp, for a moon about 49 dp across where it used to be 15
        assertEquals(49, 2 * PrayerDial.moonRadius(163.9f * dp) / dp, 0.5);
    }

    @Test
    public void theNeedlesTipStaysWithinTheRoomLeftRoundTheDial() {
        float dp = 2.625f;
        for (float rim = 30; rim <= 700; rim += 5) { // From a small widget's dial to a tablet's clock, in dp
            float unit = PrayerDial.unitFor(rim * dp, dp);
            assertTrue(rim + " dp", PrayerDial.tipReach(unit, dp) <= (DialArt.OVERHANG - 1) * dp + 0.001f);
        }
        float unit = PrayerDial.unitFor(90 * dp, dp); // A 3x3 widget's dial keeps the whole arrow
        assertEquals(PrayerDial.TIP_REACH * unit, PrayerDial.tipReach(unit, dp), 0.001);
    }

    @Test
    public void aNameWaitsWhileTheSunPassesOverIt() {
        // "Midnight" in a box 40 wide and 10 high, centred 70 below the centre
        assertTrue(PrayerDial.covers(0, 70, 10, -20, 65, 20, 75)); // The sun right on it
        assertTrue(PrayerDial.covers(26, 70, 10, -20, 65, 20, 75)); // Over its last letters
        assertFalse(PrayerDial.covers(35, 70, 10, -20, 65, 20, 75)); // Gone past
        assertFalse(PrayerDial.covers(27, 80, 8, -20, 65, 20, 75)); // Near its corner, but clear of it
    }

    @Test
    public void theSeasStayOnTheMoonAndAreClosed() {
        for (String data : new String[] {MoonFace.SEAS, MoonFace.DARKEST_SEAS}) {
            float x = 0, y = 0;
            int opened = 0, closed = 0;
            for (float[] step : MoonFace.steps(data)) {
                switch ((char) step[0]) {
                    case 'M': x = step[1]; y = step[2]; opened++; break;
                    case 'c': x += step[5]; y += step[6]; break;
                    case 'l': x += step[1]; y += step[2]; break;
                    default: closed++;
                }
                assertTrue(x + "," + y, Math.hypot(x, y) <= 100);
            }
            assertTrue(opened > 0);
            assertEquals(opened, closed);
        }
        // Kept light: a few kilobytes of path data, no pictures
        assertTrue(MoonFace.SEAS.length() + MoonFace.DARKEST_SEAS.length() < 4000);
    }

    @Test
    public void aQuartersNameSitsAsFarOutAsItGoesInsideTheFace() {
        // "Midnight" under the hub: its bottom corners on the edge, so a long name comes in a little
        float below = PrayerDial.reachInside(100, 0, 1, 30, 4);
        assertEquals(Math.sqrt(100 * 100 - 30 * 30) - 4, below, 0.01);
        // "6 a.m." beside it: its outer corners on the edge
        float beside = PrayerDial.reachInside(100, -1, 0, 15, 4);
        assertEquals(Math.sqrt(100 * 100 - 4 * 4) - 15, beside, 0.01);
        // Any way round, the far corner just touches the edge
        double radians = Math.toRadians(35);
        float cos = (float) Math.cos(radians), sin = (float) Math.sin(radians);
        float r = PrayerDial.reachInside(100, cos, sin, 20, 6);
        assertEquals(100, Math.hypot(r * cos + 20, r * sin + 6), 0.01);
    }
}

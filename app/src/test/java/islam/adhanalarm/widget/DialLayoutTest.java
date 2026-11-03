package islam.adhanalarm.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import islam.adhanalarm.handler.ScheduleHandler;
import islam.adhanalarm.handler.Schedules;

/**
 * The labels round the big dial, laid out for a year of real prayer times from the equator to the far
 * north and south, in the app's languages, at the sizes a phone's home screen gives a widget
 */
public class DialLayoutTest {

    private static final float DENSITY = 2.625f; // A common phone's pixels per dp
    // A home screen cell, in dp, as a common phone's launcher gives one
    private static final float CELL_WIDTH = 82, CELL_HEIGHT = 95.6f;

    // Places and their time zones, a week apart through a year
    private static final Object[][] PLACES = {
            {"Australia/Sydney", "-33.87", "151.21"}, {"Asia/Singapore", "1.35", "103.82"},
            {"Asia/Riyadh", "21.42", "39.83"}, {"Africa/Cairo", "30.04", "31.24"},
            {"Europe/Istanbul", "41.01", "28.98"}, {"Europe/London", "51.51", "-0.13"},
            {"Europe/Oslo", "59.91", "10.75"}, {"Atlantic/Reykjavik", "64.15", "-21.94"},
    };

    /** Each language's prayer names, from the app's own strings */
    private static final Map<String, String[]> NAMES = new LinkedHashMap<>();

    private final TimeZone mOriginal = TimeZone.getDefault();

    @BeforeClass
    public static void readNames() throws IOException {
        for (String folder : new String[]{"values", "values-ar", "values-de", "values-ru", "values-tr", "values-ms"}) {
            String strings = new String(Files.readAllBytes(Paths.get("src/main/res", folder, "strings.xml")), StandardCharsets.UTF_8);
            String[] names = new String[5];
            String[] keys = {"fajr", "dhuhr", "asr", "maghrib", "ishaa"};
            for (int i = 0; i < 5; i++) {
                Matcher matcher = Pattern.compile("<string name=\"" + keys[i] + "\">([^<]*)</string>").matcher(strings);
                assertTrue(folder + " " + keys[i], matcher.find());
                names[i] = matcher.group(1).replace("\\'", "'");
            }
            NAMES.put(folder, names);
        }
    }

    @After
    public void restoreTimeZone() {
        TimeZone.setDefault(mOriginal);
    }

    /**
     * A width for text a little wider than Roboto and Noto Naskh set it: measured at about 0.53 of the type's
     * size a letter for Latin names, 0.69 for Cyrillic, 0.44 for Arabic and 0.5 for times
     */
    private static float width(String text, float sp) {
        float ems = 0;
        for (char c : text.toCharArray()) {
            ems += c >= 0x0400 && c <= 0x04FF ? 0.7f : c >= 0x0600 && c <= 0x06FF ? 0.46f : Character.isLetter(c) ? 0.57f : 0.52f;
        }
        return ems * sp * DENSITY;
    }

    private static DialLayout.Label[] labels(ScheduleHandler schedule, String[] names, String[] times, float nameSp, int next) {
        DialLayout.Label[] labels = new DialLayout.Label[5];
        for (int i = 0; i < 5; i++) {
            float w = width(names[i], nameSp), h = nameSp * 1.22f * DENSITY;
            if (times != null) {
                w = Math.max(w, width(times[i], nameSp - 1));
                h += (nameSp - 1) * 1.22f * DENSITY;
            }
            labels[i] = new DialLayout.Label(PrayerDial.middleAngle(schedule, i), w, h, i == next);
        }
        return labels;
    }

    /** The dial's diameter in dp with every label placed, after checking the placing from first principles, or 0 */
    private static float layOut(float widthDp, float heightDp, DialLayout.Label[] labels, String what) {
        float shorter = Math.min(widthDp, heightDp);
        float pad = DialLayout.clamp(0.07f * shorter, 10, 16) * DENSITY;
        float left = pad, top = pad, right = widthDp * DENSITY - pad, bottom = heightDp * DENSITY - pad;
        DialLayout layout = new DialLayout(DENSITY);
        if (!layout.fit(left, top, right, bottom, labels)) return 0;
        float x = layout.centreX, y = layout.centreY, r = layout.radius, e = 1;
        assertTrue(what + ": dial inside", x - r >= left - e && x + r <= right + e && y - r >= top - e && y + r <= bottom + e);
        for (DialLayout.Label a : labels) {
            assertTrue(what + ": label inside", a.left >= left - e && a.right <= right + e && a.top >= top - e && a.bottom <= bottom + e);
            assertWithinReach(what, a, left, top, right, bottom, DENSITY);
            assertTrue(what + ": label off the dial", DialLayout.rectToPoint(a.left, a.top, a.right, a.bottom, x, y) >= r);
            assertEquals(what + ": pointer starts at the rim", r + 1.5f * DENSITY, Math.hypot(a.rimX - x, a.rimY - y), 0.5);
            assertTrue(what + ": pointer clear of the dial", DialLayout.segmentToPoint(a.elbowX, a.elbowY, a.endX, a.endY, x, y) >= r);
            assertTrue(what + ": pointer ends at its label", DialLayout.rectToPoint(a.left, a.top, a.right, a.bottom, a.endX, a.endY) <= 9 * DENSITY);
            for (DialLayout.Label b : labels) {
                if (a == b) continue;
                assertFalse(what + ": labels apart", a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom);
                assertFalse(what + ": pointer clear of other labels", DialLayout.segmentHitsRect(a.elbowX, a.elbowY, a.endX, a.endY, b.left, b.top, b.right, b.bottom));
            }
        }
        return 2 * r / DENSITY;
    }

    /**
     * The highlight round a label, and its pointer at the line's full width, reach no further past the box than
     * DialArt.OVERHANG, which the prayer clock keeps clear round its box so nothing is cut off at the screen's edge
     */
    private static void assertWithinReach(String what, DialLayout.Label a, float left, float top, float right, float bottom, float density) {
        float reach = DialArt.OVERHANG * density, px = DialLayout.PILL_X * density, py = DialLayout.PILL_Y * density;
        assertTrue(what + ": highlight on the screen", a.left - px >= left - reach && a.right + px <= right + reach
                && a.top - py >= top - reach && a.bottom + py <= bottom + reach);
        float stroke = DialArt.LEADER_WIDTH / 2 * density;
        for (float[] point : new float[][] {{a.rimX, a.rimY}, {a.elbowX, a.elbowY}, {a.endX, a.endY}}) {
            assertTrue(what + ": pointer on the screen", point[0] - stroke >= left - reach && point[0] + stroke <= right + reach
                    && point[1] - stroke >= top - reach && point[1] + stroke <= bottom + reach);
        }
    }

    @Test
    public void onAPhonesClockTheDialFillsTheWidthWithNothingCutOff() {
        // The prayer clock upright on a phone: the dial's space, less the clock's own margin
        float width = 412 - 2 * 20, height = 560;
        float reach = DialArt.OVERHANG * DENSITY;
        int full = 0, total = 0;
        for (Object[] place : PLACES) {
            TimeZone.setDefault(TimeZone.getTimeZone((String) place[0]));
            for (int day = 0; day < 365; day += 29) {
                Calendar noon = new GregorianCalendar(2026, Calendar.JANUARY, 1, 12, 0);
                noon.add(Calendar.DAY_OF_YEAR, day);
                ScheduleHandler schedule = Schedules.at(noon, "latitude", place[1], "longitude", place[2], "method", "mwl");
                for (int next = 0; next < 5; next++) {
                    DialLayout.Label[] labels = labels(schedule, NAMES.get("values"), new String[] {"5:12 a.m.", "11:40 a.m.", "2:28 p.m.", "4:51 p.m.", "6:09 p.m."}, 16, next);
                    DialLayout layout = new DialLayout(DENSITY);
                    float left = reach, top = reach, right = width * DENSITY - reach, bottom = height * DENSITY - reach;
                    String what = place[0] + " day " + day + " next " + next;
                    assertTrue(what + ": fits", layout.fit(left, top, right, bottom, labels));
                    for (DialLayout.Label a : labels) {
                        // Within the view itself, which is what clips the drawing
                        assertWithinReach(what, a, left, top, right, bottom, DENSITY);
                    }
                    total++;
                    if (2 * layout.radius / DENSITY > width - 2 * DialArt.OVERHANG - 1) full++;
                }
            }
        }
        // Mostly the dial touches the sides, where the highlight and the pointers would be cut off without the margin
        assertTrue("The dial as wide as it goes: " + full + " of " + total, full >= total * 0.8);
    }

    private static String clock(Calendar time, boolean twelveHour) {
        int hour = time.get(Calendar.HOUR_OF_DAY), minute = time.get(Calendar.MINUTE);
        return twelveHour ? String.format("%d:%02d %s", (hour + 11) % 12 + 1, minute, hour < 12 ? "a.m." : "p.m.")
                : String.format("%02d:%02d", hour, minute);
    }

    private static float nameSp(float widthDp, float heightDp) {
        return DialLayout.clamp(11 + (Math.min(widthDp, heightDp) - 150) / 60, 11, 14);
    }

    @Test
    public void everyDayAndPlaceFitsTheNamesAt2By2AndTimesFrom3By3() {
        float[][] sizes = {{2, 2}, {3, 3}, {4, 2}, {4, 4}};
        float[] smallest = new float[sizes.length];
        java.util.Arrays.fill(smallest, Float.MAX_VALUE);
        int layouts = 0;
        long started = System.nanoTime();
        for (Object[] place : PLACES) {
            TimeZone.setDefault(TimeZone.getTimeZone((String) place[0]));
            for (int day = 0; day < 365; day += 7) {
                Calendar noon = new GregorianCalendar(2026, Calendar.JANUARY, 1, 12, 0);
                noon.add(Calendar.DAY_OF_YEAR, day);
                ScheduleHandler schedule = Schedules.at(noon, "latitude", place[1], "longitude", place[2], "method", "mwl");
                for (Map.Entry<String, String[]> language : NAMES.entrySet()) {
                    boolean twelveHour = language.getKey().equals("values");
                    String[] times = new String[5], clocks = new String[5];
                    for (int i = 0; i < 5; i++) {
                        Calendar start = schedule.getTime(PrayerDial.STARTS[i]);
                        times[i] = clock(start, twelveHour);
                        clocks[i] = clock(start, false).replaceFirst("^0", "");
                    }
                    for (int s = 0; s < sizes.length; s++) {
                        float w = sizes[s][0] * CELL_WIDTH, h = sizes[s][1] * CELL_HEIGHT, sp = nameSp(w, h);
                        String what = place[0] + " day " + day + " " + language.getKey() + " " + (int) sizes[s][0] + "x" + (int) sizes[s][1];
                        float names = layOut(w, h, labels(schedule, language.getValue(), null, sp, 0), what + " names");
                        float full = layOut(w, h, labels(schedule, language.getValue(), times, sp, 3), what + " times");
                        float shortTimes = twelveHour ? layOut(w, h, labels(schedule, language.getValue(), clocks, sp, 3), what + " short") : -1;
                        layouts += twelveHour ? 3 : 2;
                        int level = DialArt.chooseLevel(full, shortTimes, names);
                        if (sizes[s][0] == 2) {
                            assertTrue(what + ": names fit, dial " + names, level >= DialArt.NAMES);
                        } else {
                            assertTrue(what + ": times fit, dial " + full + "/" + shortTimes, level >= DialArt.SHORT_TIMES);
                        }
                        float chosen = level == DialArt.NAMES ? names : level == DialArt.SHORT_TIMES ? shortTimes : full;
                        smallest[s] = Math.min(smallest[s], chosen);
                    }
                }
            }
        }
        double perLayout = (System.nanoTime() - started) / 1e6 / layouts;
        System.out.printf("Smallest dials (dp): 2x2 %.0f, 3x3 %.0f, 4x2 %.0f, 4x4 %.0f; %.2f ms a layout%n", smallest[0], smallest[1], smallest[2], smallest[3], perLayout);
        assertTrue("Fast enough to draw every few minutes: " + perLayout + " ms", perLayout < 20);
    }

    @Test
    public void onABigScreenTheNamesSitBesideTheirWindowsWithNoPointers() {
        // A 1080p TV's dial area, at its density, with names and times in TV-sized type
        float tvDensity = 2f, width = 560, height = 500;
        int fitted = 0, total = 0;
        for (Object[] place : PLACES) {
            TimeZone.setDefault(TimeZone.getTimeZone((String) place[0]));
            for (int day = 0; day < 365; day += 13) {
                Calendar noon = new GregorianCalendar(2026, Calendar.JANUARY, 1, 12, 0);
                noon.add(Calendar.DAY_OF_YEAR, day);
                ScheduleHandler schedule = Schedules.at(noon, "latitude", place[1], "longitude", place[2], "method", "mwl");
                for (String[] names : NAMES.values()) {
                    DialLayout.Label[] labels = new DialLayout.Label[5];
                    for (int i = 0; i < 5; i++) {
                        float w = Math.max(width(names[i], 17), width("12:40 p.m.", 16)) * tvDensity / DENSITY;
                        float h = (17 + 16) * 1.22f * tvDensity;
                        labels[i] = new DialLayout.Label(PrayerDial.middleAngle(schedule, i), w, h, i == 3);
                    }
                    DialLayout layout = new DialLayout(tvDensity);
                    total++;
                    if (!layout.fitAround(0, 0, width * tvDensity, height * tvDensity, labels)) continue;
                    fitted++;
                    String what = place[0] + " day " + day + " " + names[0];
                    for (DialLayout.Label a : labels) {
                        assertEquals(what + ": no pointer", DialLayout.AROUND, a.slot);
                        assertTrue(what + ": just outside the rim", DialLayout.rectToPoint(a.left, a.top, a.right, a.bottom, layout.centreX, layout.centreY) >= layout.radius + 8 * tvDensity);
                        assertTrue(what + ": on the screen", a.left >= -1 && a.top >= -1 && a.right <= width * tvDensity + 1 && a.bottom <= height * tvDensity + 1);
                        assertWithinReach(what, a, 0, 0, width * tvDensity, height * tvDensity, tvDensity);
                        for (DialLayout.Label b : labels) {
                            if (a != b) assertFalse(what + ": apart", a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom);
                        }
                    }
                    assertTrue(what + ": a big dial, " + 2 * layout.radius / tvDensity, 2 * layout.radius / tvDensity >= DialArt.AROUND_DIAL);
                }
            }
        }
        System.out.println("Around the ring fitted " + fitted + " of " + total);
        assertTrue("Around the ring fits nearly always on a TV: " + fitted + " of " + total, fitted >= total * 0.95);
    }

    @Test
    public void aSingleCellOrAStripHasNoRoomForNames() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"));
        ScheduleHandler schedule = Schedules.at(new GregorianCalendar(2026, Calendar.MARCH, 20, 12, 0), "latitude", "51.51", "longitude", "-0.13", "method", "mwl");
        String[] names = NAMES.get("values");
        for (float[] cells : new float[][]{{2, 1}, {1, 2}, {3, 1}, {4, 1}}) {
            float w = cells[0] * CELL_WIDTH, h = cells[1] * CELL_HEIGHT;
            float dial = Math.min(w, h) < DialArt.NAMES_DIAL ? 0 : layOut(w, h, labels(schedule, names, null, nameSp(w, h), 0), cells[0] + "x" + cells[1]);
            assertEquals(cells[0] + "x" + cells[1] + " stays an icon, dial " + dial, DialArt.NONE, DialArt.chooseLevel(0, -1, dial));
        }
    }

    @Test
    public void theLabelsStayPutAsTheNextPrayerChanges() {
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Cairo"));
        ScheduleHandler schedule = Schedules.at(new GregorianCalendar(2026, Calendar.JUNE, 21, 12, 0), "latitude", "30.04", "longitude", "31.24", "method", "egypt");
        String[] names = NAMES.get("values");
        float w = 3 * CELL_WIDTH, h = 3 * CELL_HEIGHT;
        List<String> placings = new ArrayList<>();
        for (int next = 0; next < 5; next++) {
            DialLayout.Label[] labels = labels(schedule, names, null, nameSp(w, h), next);
            assertTrue(layOut(w, h, labels, "next " + next) > 0);
            StringBuilder placing = new StringBuilder();
            for (DialLayout.Label label : labels) {
                placing.append(Math.round(label.left)).append(',').append(Math.round(label.top)).append(' ');
            }
            placings.add(placing.toString());
        }
        for (String placing : placings) {
            assertEquals(placings.get(0), placing);
        }
    }

    @Test
    public void aBiggerTileNeverMeansASmallerDial() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
        ScheduleHandler schedule = Schedules.at(new GregorianCalendar(2026, Calendar.DECEMBER, 21, 12, 0), "latitude", "41.01", "longitude", "28.98", "method", "diyanet");
        String[] names = NAMES.get("values-tr");
        float last = 0;
        for (float w = 160; w <= 360; w += 20) {
            float dial = layOut(w, w * 1.15f, labels(schedule, names, null, 12, 0), "width " + w);
            assertTrue("width " + w + ": dial " + dial + " after " + last, dial >= last - 1);
            last = dial;
        }
    }

    @Test
    public void namesGoInARowOrAColumnByWhereTheirWindowIs() {
        assertEquals("[0, 3, 2]", java.util.Arrays.toString(DialLayout.slotsFor(0))); // At noon above, or to either side on a wide tile
        assertEquals("[1, 3, 2]", java.util.Arrays.toString(DialLayout.slotsFor(180))); // At midnight below, or to either side
        int[] dusk = DialLayout.slotsFor(95); // Just after 6 p.m.: above, below or to the right
        assertEquals(3, dusk.length);
        assertEquals(DialLayout.RIGHT, dusk[2]);
        assertEquals(DialLayout.LEFT, DialLayout.slotsFor(270)[2]); // 6 a.m., to the left
    }

    @Test
    public void detailComesAndGoesWithTheDialsSize() {
        assertEquals(DialArt.TIMES, DialArt.chooseLevel(170, 172, 190)); // Full times cost the dial little
        assertEquals(DialArt.SHORT_TIMES, DialArt.chooseLevel(143, 169, 196)); // Dropping a.m. and p.m. buys a much bigger dial
        assertEquals(DialArt.TIMES, DialArt.chooseLevel(120, -1, 140)); // 24-hour times have nothing to drop
        assertEquals(DialArt.NAMES, DialArt.chooseLevel(0, 80, 115)); // Too small for times
        assertEquals(DialArt.NONE, DialArt.chooseLevel(0, 0, 70)); // Too small for names: set out like an icon
        assertNotNull(DialLayout.slotsFor(45));
    }

    @Test
    public void geometry() {
        assertEquals(5, DialLayout.rectToPoint(0, 0, 10, 10, 15, 5), 0.001);
        assertEquals(0, DialLayout.rectToPoint(0, 0, 10, 10, 5, 5), 0.001);
        assertEquals(5, DialLayout.segmentToPoint(0, 0, 10, 0, 5, 5), 0.001);
        assertTrue(DialLayout.segmentsCross(0, 0, 10, 10, 0, 10, 10, 0));
        assertFalse(DialLayout.segmentsCross(0, 0, 10, 0, 0, 1, 10, 1));
        assertTrue(DialLayout.segmentHitsRect(-5, 5, 15, 5, 0, 0, 10, 10));
        assertFalse(DialLayout.segmentHitsRect(-5, 15, 15, 15, 0, 0, 10, 10));
        if (DialLayout.clamp(5, 0, 1) != 1) fail();
    }
}

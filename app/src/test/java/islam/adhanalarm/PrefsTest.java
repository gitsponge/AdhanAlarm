package islam.adhanalarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrefsTest {

    @Test
    public void settingsFrom3xMoveAcross() {
        FakeSettings settings = new FakeSettings("calculationMethodsIndex", "2", "timeFormatIndex", "1", "offsetMinutes", "5",
                "pressure", "110", "latitude", "51.5", "longitude", "-0.1");
        Prefs.migrate(settings);
        assertEquals(CONSTANT.METHOD_KARACHI, settings.getString(Prefs.METHOD, null));
        assertTrue(Prefs.isHanafi(settings));
        assertTrue(settings.getBoolean(Prefs.METHOD_CHOSEN, false));
        assertEquals("24", settings.getString(Prefs.TIME_FORMAT, null));
        for (int prayer = CONSTANT.FAJR; prayer <= CONSTANT.ISHAA; prayer++) {
            assertEquals(5, Prefs.getOffset(settings, prayer));
        }
        assertEquals("1010", settings.getString(Prefs.PRESSURE, null));
        assertTrue(settings.getBoolean(Prefs.SETUP_DONE, false));
        assertFalse(settings.getBoolean(Prefs.AUTO_LOCATION, true)); // A hand-set place is never overwritten without asking
        assertFalse(settings.contains("calculationMethodsIndex"));
        assertFalse(settings.contains("offsetMinutes"));
    }

    @Test
    public void defaultTimeFormatFollowsThePhone() {
        FakeSettings settings = new FakeSettings("timeFormatIndex", "0", "calculationMethodsIndex", "4");
        Prefs.migrate(settings);
        assertEquals("system", settings.getString(Prefs.TIME_FORMAT, null));
        assertEquals(CONSTANT.METHOD_MWL, settings.getString(Prefs.METHOD, null));
        assertFalse(Prefs.isHanafi(settings));
    }

    @Test
    public void aNewInstallStillGetsSetUp() {
        FakeSettings settings = new FakeSettings();
        Prefs.migrate(settings);
        assertFalse(settings.getBoolean(Prefs.SETUP_DONE, false));
        assertEquals(4, settings.getInt(Prefs.VERSION, 0));
    }

    @Test
    public void migrationRunsOnce() {
        FakeSettings settings = new FakeSettings("calculationMethodsIndex", "0");
        Prefs.migrate(settings);
        settings.edit().putString(Prefs.METHOD, CONSTANT.METHOD_ISNA).apply();
        settings.edit().putString("calculationMethodsIndex", "0").apply();
        Prefs.migrate(settings);
        assertEquals(CONSTANT.METHOD_ISNA, settings.getString(Prefs.METHOD, null));
    }
}

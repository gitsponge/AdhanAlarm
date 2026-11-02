package islam.adhanalarm.handler;

import java.util.Calendar;

import islam.adhanalarm.FakeSettings;

/**
 * A day's prayer times worked out as the app would at a chosen moment, for tests outside this package
 */
public final class Schedules {

    private Schedules() {
    }

    public static ScheduleHandler at(Calendar now, Object... settings) {
        ScheduleHandler handler = new ScheduleHandler(new FakeSettings(settings));
        handler.update(now);
        return handler;
    }
}

package islam.adhanalarm.clock;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.widget.DialArt;

/**
 * What the prayer dial full screen says under the time: the next prayer and how long until it, or, inside a short
 * window, how long that prayer's time has left, since praying those on time is what the dial is for. Also
 * whether it's the quiet part of the night, from halfway between sunset and fajr (when isha's preferred time
 * ends) until fajr, when the clock dims. Pure arithmetic on the day's times, so it can be tested without a
 * phone.
 */
final class ClockState {

    /** It says when the next prayer comes, or when the current, short, window ends */
    static final int NEXT = 0, ENDS = 1;

    private static final short[] STARTS = {CONSTANT.FAJR, CONSTANT.DHUHR, CONSTANT.ASR, CONSTANT.MAGHRIB, CONSTANT.ISHAA};
    private static final short[] ENDS_AT = {CONSTANT.SUNRISE, CONSTANT.ASR, CONSTANT.MAGHRIB, CONSTANT.ISHAA, CONSTANT.NEXT_FAJR};
    private static final long DAY = 24 * 60 * 60 * 1000L;

    final int kind;
    /** The window it names, fajr's 0 to isha's 4 */
    final int window;
    /** The time it counts down to, as a CONSTANT index: the next prayer's, or the time that ends the window */
    final short to;
    /** How long until then */
    final long millis;
    /** Whether the window it names is a short one */
    final boolean shortWindow;
    final boolean night;

    private ClockState(int kind, int window, short to, long millis, boolean shortWindow, boolean night) {
        this.kind = kind;
        this.window = window;
        this.to = to;
        this.millis = millis;
        this.shortWindow = shortWindow;
        this.night = night;
    }

    /**
     * At the given moment, from the day's times in milliseconds (indexed FAJR to NEXT_FAJR) and the night
     * before's isha, or 0 where it isn't known
     */
    static ClockState at(long now, long[] times, long previousIshaa) {
        boolean night = isNight(now, times);

        // The window it's in: before fajr, the night before's isha
        int current = -1;
        long start = 0, end = 0;
        short endsAt = 0;
        if (now < times[CONSTANT.FAJR]) {
            start = previousIshaa > 0 ? previousIshaa : times[CONSTANT.ISHAA] - DAY;
            if (now >= start) {
                current = 4;
                end = times[CONSTANT.FAJR];
                endsAt = CONSTANT.FAJR;
            }
        } else {
            for (int i = 0; i < STARTS.length; i++) {
                if (now >= times[STARTS[i]] && now < times[ENDS_AT[i]]) {
                    current = i;
                    start = times[STARTS[i]];
                    end = times[ENDS_AT[i]];
                    endsAt = ENDS_AT[i];
                    break;
                }
            }
        }
        if (current >= 0 && DialArt.isShort(end - start)) {
            return new ClockState(ENDS, current, endsAt, end - now, true, night);
        }

        // Otherwise the next prayer, passing over sunrise
        short[] order = {CONSTANT.FAJR, CONSTANT.DHUHR, CONSTANT.ASR, CONSTANT.MAGHRIB, CONSTANT.ISHAA, CONSTANT.NEXT_FAJR};
        short next = CONSTANT.NEXT_FAJR;
        for (short time : order) {
            if (times[time] > now) {
                next = time;
                break;
            }
        }
        int window = next == CONSTANT.NEXT_FAJR ? 0 : indexOf(next);
        // Tomorrow's fajr runs about as long as today's
        long length = next == CONSTANT.NEXT_FAJR ? times[CONSTANT.SUNRISE] - times[CONSTANT.FAJR] : times[ENDS_AT[window]] - times[STARTS[window]];
        return new ClockState(NEXT, window, next, times[next] - now, DialArt.isShort(length), night);
    }

    /**
     * From halfway between sunset and fajr until fajr. Before fajr the sunset was yesterday's, which is near
     * enough today's less a day.
     */
    static boolean isNight(long now, long[] times) {
        long middle = (times[CONSTANT.MAGHRIB] + times[CONSTANT.NEXT_FAJR]) / 2;
        if (now >= middle && now < times[CONSTANT.NEXT_FAJR]) return true;
        long lastMiddle = (times[CONSTANT.MAGHRIB] - DAY + times[CONSTANT.FAJR]) / 2;
        return now >= lastMiddle && now < times[CONSTANT.FAJR];
    }

    private static int indexOf(short time) {
        for (int i = 0; i < STARTS.length; i++) {
            if (STARTS[i] == time) return i;
        }
        return 0;
    }
}

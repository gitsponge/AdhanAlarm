package islam.adhanalarm.clock;

import android.content.Context;

import java.text.DateFormat;
import java.util.Calendar;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.Formats;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.ScheduleHandler;

/**
 * The line under the full-screen dial's time, and the one under that, as the dial, the screen saver and the car
 * all show them: "Maghrib in 1 h 17 min" over "5:57 p.m., a short window", or inside a short window
 * "Maghrib ends in 52 min" over "until `Ishaa' at 7:14 p.m."
 */
public final class ClockWords {

    public final String headline, sub;
    /** The window the line names, fajr's 0 to isha's 4, and whether it's a short one */
    public final int window;
    public final boolean shortWindow;
    /** The quiet part of the night, from halfway between sunset and fajr until fajr */
    public final boolean night;

    private ClockWords(String headline, String sub, int window, boolean shortWindow, boolean night) {
        this.headline = headline;
        this.sub = sub;
        this.window = window;
        this.shortWindow = shortWindow;
        this.night = night;
    }

    public static ClockWords at(Context context, ScheduleHandler schedule, DateFormat format, Calendar now) {
        long[] times = new long[CONSTANT.NEXT_FAJR + 1];
        for (short i = CONSTANT.FAJR; i <= CONSTANT.NEXT_FAJR; i++) {
            times[i] = schedule.getTime(i).getTimeInMillis();
        }
        Calendar previousIshaa = schedule.getPreviousTime(CONSTANT.FAJR);
        ClockState state = ClockState.at(now.getTimeInMillis(), times, previousIshaa != null ? previousIshaa.getTimeInMillis() : 0);
        String name = context.getString(CONSTANT.TIME_NAMES[windowStart(state.window)]);
        String countdown = Formats.countdown(context, state.millis);
        String at = schedule.getFormattedTime(state.to, format);
        String headline, sub;
        if (state.kind == ClockState.ENDS) {
            headline = context.getString(R.string.clock_ends, name, countdown);
            sub = context.getString(R.string.clock_until, context.getString(CONSTANT.TIME_NAMES[state.to]), at);
        } else {
            headline = context.getString(R.string.name_and_time, name, countdown);
            sub = state.shortWindow ? context.getString(R.string.dial_short_window, at) : at;
        }
        return new ClockWords(headline, sub, state.window, state.shortWindow, state.night);
    }

    /** The time a window starts at, as a CONSTANT index */
    public static short windowStart(int window) {
        switch (window) {
            case 1:
                return CONSTANT.DHUHR;
            case 2:
                return CONSTANT.ASR;
            case 3:
                return CONSTANT.MAGHRIB;
            case 4:
                return CONSTANT.ISHAA;
            default:
                return CONSTANT.FAJR;
        }
    }
}

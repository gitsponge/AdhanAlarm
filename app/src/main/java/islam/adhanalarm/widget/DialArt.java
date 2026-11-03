package islam.adhanalarm.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.ScheduleHandler;

/**
 * The prayer dial with its labels, laid out in a box: each prayer named at the end of a pointer to its
 * window, with its time as room allows, and the next prayer's name highlighted. Optionally the Islamic date
 * sits underneath in small type. Everything is quieter than the dial: the names in cream, the times and the
 * date a step fainter but still easy to read, the pointers hairlines. The widget draws it into a picture; the
 * prayer clock and the screen saver draw it straight onto the screen.
 */
public final class DialArt {

    /** How much it shows: names, names with times but no a.m. or p.m., or names with full times */
    public static final int NONE = 0, NAMES = 1, SHORT_TIMES = 2, TIMES = 3;

    // The smallest dial, in dp, worth showing each with. Below the first, the widget is set out like an
    // icon. That one is low, so a 2 by 2 keeps its names even on a day when long names crowd one side.
    static final float NAMES_DIAL = 72, SHORT_TIMES_DIAL = 120, TIMES_DIAL = 140, DATE_DIAL = 200;
    /**
     * From this big a dial, in dp, the names sit just outside their windows with no pointers, where that
     * leaves the dial at least AROUND_SHARE of its size with pointers: a TV, a tablet, the clock on its side
     */
    static final float AROUND_DIAL = 260, AROUND_SHARE = 0.9f;
    /** The dial sizes, in dp, from which its face shows the quarters of the day, then names them, with the hours ticked */
    static final float QUARTERS_DIAL = 90, HOURS_DIAL = 150;
    /** Times keep their a.m. and p.m. only while that leaves the dial at least this share of its size without them */
    static final float FULL_TIMES_SHARE = 0.92f;
    /** Lines of type are this many times their size apart */
    private static final float LEADING = 1.22f;
    /** In dp: the space between the labels and the date under them, the pointers' width, and the highlight's corners */
    private static final float DATE_GAP = 8, PILL_CORNER = 10;
    static final float LEADER_WIDTH = 1;
    /**
     * How far past its box the art can reach, in dp: the highlight round a name at the box's edge, a pointer
     * turning beside a dial as wide as the box, or the sun's needle reaching past the rim, and a dp to spare for
     * the smoothing. A widget's tile has more margin than this round the box; on the screen, the box has to be
     * drawn in by it so nothing is cut off.
     */
    public static final float OVERHANG = Math.max(Math.max(DialLayout.PILL_X, DialLayout.ELBOW + LEADER_WIDTH / 2), PrayerDial.TIP_REACH_DP) + 1;

    private final Context mContext;
    private final ScheduleHandler mSchedule;
    private final Calendar mNow;
    private final float mDp;
    private final DialLayout mLayout;
    private final DialLayout.Label[] mLabels;
    private final String[] mNames, mShown, mQuarters;
    private final Paint mName, mTime, mDate;
    private final String mHijri;
    private final RectF mBox;
    private final int mLevel;

    private DialArt(Context context, ScheduleHandler schedule, Calendar now, float dp, Fitted fitted, String[] names, String[] shown,
                    Paint name, Paint time, Paint date, String hijri, RectF box, int level, String[] quarters) {
        mContext = context;
        mSchedule = schedule;
        mNow = now;
        mDp = dp;
        mLayout = fitted.layout;
        mLabels = fitted.labels;
        mNames = names;
        mShown = shown;
        mName = name;
        mTime = time;
        mDate = date;
        mHijri = hijri;
        mBox = box;
        mLevel = level;
        mQuarters = quarters;
    }

    /**
     * Lays the dial and its labels out in the box, with as much detail as leaves the dial big enough, or
     * returns null where not even the names fit. Sizes are in pixels; dp is pixels to a dp at the size it's
     * drawn, and nameSp the size of the names' type, which follows the phone's font size. With
     * dateUnderneath, the Islamic date goes under the labels where the dial stays big with it.
     */
    public static DialArt lay(Context context, ScheduleHandler schedule, DateFormat format, Calendar now, RectF box,
                              float dp, float nameSp, boolean dateUnderneath) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float scale = dp / metrics.density;
        Paint name = type(context, metrics, nameSp, scale, true, R.color.dial_cream);
        Paint time = type(context, metrics, nameSp - 1, scale, false, R.color.dial_ink_secondary);
        Paint date = type(context, metrics, 11, scale, false, R.color.dial_ink_secondary);

        short nextTime = schedule.getNextTimeIndex(now);
        int next = nextWindow(nextTime);
        int windows = PrayerDial.STARTS.length;
        String[] names = new String[windows], times = new String[windows], clocks = new String[windows];
        float[] theta = new float[windows];
        boolean periods = false;
        for (int i = 0; i < windows; i++) {
            short start = startOf(i, nextTime);
            Date at = schedule.getTime(start).getTime();
            String estimated = schedule.getIsExtreme(start) ? " *" : ""; // As the app marks a time worked out by the high latitude rule
            String[] clockAndPeriod = NextNotificationWidgetProvider.splitTime(format, at);
            names[i] = context.getString(CONSTANT.TIME_NAMES[start]);
            times[i] = format.format(at) + estimated;
            clocks[i] = clockAndPeriod[0] + estimated;
            periods |= !clockAndPeriod[1].isEmpty();
            theta[i] = PrayerDial.middleAngle(schedule, i);
        }

        // The most detail that leaves the dial big enough
        Fitted withTimes = fit(box, dp, names, times, name, time, theta, next);
        Fitted withClocks = periods ? fit(box, dp, names, clocks, name, time, theta, next) : null;
        Fitted withNames = fit(box, dp, names, null, name, time, theta, next);
        int level = chooseLevel(diameter(withTimes, dp), periods ? diameter(withClocks, dp) : -1, diameter(withNames, dp));
        if (level == NONE) return null;
        Fitted chosen = level == NAMES ? withNames : level == SHORT_TIMES ? withClocks : withTimes;
        String[] shown = level == NAMES ? null : level == SHORT_TIMES ? clocks : times;

        // The Islamic date underneath, where it leaves the dial big
        String hijri = null;
        if (dateUnderneath && shown != null) {
            String text = schedule.getHijriDate(context);
            float line = date.getTextSize() * LEADING;
            RectF above = new RectF(box.left, box.top, box.right, box.bottom - line - DATE_GAP * dp);
            Fitted withDate = fit(above, dp, names, shown, name, time, theta, next);
            if (diameter(withDate, dp) >= DATE_DIAL && date.measureText(text) <= box.width()) {
                hijri = text;
                chosen = withDate;
            }
        }
        // On a big screen, the names just outside their windows instead, with no pointers
        if (diameter(chosen, dp) >= AROUND_DIAL) {
            RectF space = hijri == null ? box : new RectF(box.left, box.top, box.right, box.bottom - date.getTextSize() * LEADING - DATE_GAP * dp);
            Fitted around = fitAround(space, dp, names, shown, name, time, theta, next);
            if (diameter(around, dp) >= Math.max(AROUND_DIAL, AROUND_SHARE * diameter(chosen, dp))) {
                chosen = around;
            }
        }
        return new DialArt(context, schedule, now, dp, chosen, names, shown, name, time, date, hijri, box, level, PrayerDial.quarterNames(context, format));
    }

    /** The dial alone, as on the widget at icon size, for a picture elsewhere such as the car's screen */
    public static android.graphics.Bitmap picture(Context context, ScheduleHandler schedule, Calendar now, int sizePx) {
        return PrayerDial.draw(context, schedule, now, sizePx);
    }

    /** Whether a window this long is one to pray promptly, which the dial shows in copper */
    public static boolean isShort(long windowMs) {
        return PrayerDial.isShort(windowMs);
    }

    /** The dial's diameter in dp */
    public float diameter() {
        return 2 * mLayout.radius / mDp;
    }

    public int level() {
        return mLevel;
    }

    /** Paints it, on whatever is already there */
    public void paint(Canvas canvas) {
        float diameter = diameter();
        int hours = diameter >= HOURS_DIAL ? PrayerDial.QUARTER_NAMES : diameter >= QUARTERS_DIAL ? PrayerDial.QUARTER_TICKS : PrayerDial.NO_HOURS;
        PrayerDial.paint(mContext, canvas, mLayout.centreX, mLayout.centreY, mLayout.radius, PrayerDial.unitFor(mLayout.radius, mDp), mDp, mSchedule, mNow, false, hours, mQuarters);
        Paint leader = new Paint(Paint.ANTI_ALIAS_FLAG);
        leader.setStyle(Paint.Style.STROKE);
        leader.setStrokeWidth(LEADER_WIDTH * mDp);
        leader.setStrokeCap(Paint.Cap.ROUND);
        leader.setStrokeJoin(Paint.Join.ROUND);
        int copper = ContextCompat.getColor(mContext, R.color.dial_copper), cream = ContextCompat.getColor(mContext, R.color.dial_cream);
        Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        pill.setColor(ContextCompat.getColor(mContext, R.color.dial_pill));
        Path path = new Path();
        for (int i = 0; i < mLabels.length; i++) {
            DialLayout.Label label = mLabels[i];
            // The pointer wears its window's colour; the words stay in the ink. Around the ring there's none.
            if (label.slot != DialLayout.AROUND) {
                leader.setColor(PrayerDial.isShort(mSchedule, i) ? copper : cream);
                path.rewind();
                path.moveTo(label.rimX, label.rimY);
                path.lineTo(label.elbowX, label.elbowY);
                path.lineTo(label.endX, label.endY);
                canvas.drawPath(path, leader);
            }
            if (label.next) {
                RectF bounds = new RectF(label.left - DialLayout.PILL_X * mDp, label.top - DialLayout.PILL_Y * mDp,
                        label.right + DialLayout.PILL_X * mDp, label.bottom + DialLayout.PILL_Y * mDp);
                float corner = Math.min(bounds.height() / 2, PILL_CORNER * mDp);
                canvas.drawRoundRect(bounds, corner, corner, pill);
            }
            // In a side column the words line up on the edge nearest the dial
            Paint.Align align = label.slot == DialLayout.LEFT ? Paint.Align.RIGHT : label.slot == DialLayout.RIGHT ? Paint.Align.LEFT : Paint.Align.CENTER;
            float x = align == Paint.Align.RIGHT ? label.right : align == Paint.Align.LEFT ? label.left : (label.left + label.right) / 2;
            float top = line(canvas, mNames[i], mName, align, x, label.top);
            if (mShown != null) {
                line(canvas, mShown[i], mTime, align, x, top);
            }
        }
        if (mHijri != null) {
            line(canvas, mHijri, mDate, Paint.Align.CENTER, mBox.centerX(), mBox.bottom - mDate.getTextSize() * LEADING);
        }
    }

    /**
     * The level of detail, given the diameters in dp of the dials that fit with full times, with times
     * without a.m. or p.m., and with names alone: 0 where nothing fits, and -1 for times with no a.m. or
     * p.m. to drop
     */
    static int chooseLevel(float times, float clocks, float names) {
        if (times >= TIMES_DIAL && (clocks < 0 || times >= FULL_TIMES_SHARE * clocks)) return TIMES;
        if (clocks < 0 && times >= SHORT_TIMES_DIAL) return TIMES; // 24-hour times are as short as they get
        if (clocks >= SHORT_TIMES_DIAL) return SHORT_TIMES;
        if (names >= NAMES_DIAL) return NAMES;
        return NONE;
    }

    /**
     * The window whose name is highlighted: the next prayer's. Through fajr, until sunrise, that's dhuhr.
     */
    public static int nextWindow(short nextTimeIndex) {
        switch (nextTimeIndex) {
            case CONSTANT.SUNRISE:
            case CONSTANT.DHUHR:
                return 1;
            case CONSTANT.ASR:
                return 2;
            case CONSTANT.MAGHRIB:
                return 3;
            case CONSTANT.ISHAA:
                return 4;
            default:
                return 0; // Fajr, this morning's or tomorrow's
        }
    }

    /** The time a window's name goes with: once tonight's isha is in, fajr's is tomorrow's */
    public static short startOf(int window, short nextTimeIndex) {
        return window == 0 && nextTimeIndex == CONSTANT.NEXT_FAJR ? CONSTANT.NEXT_FAJR : PrayerDial.STARTS[window];
    }

    /** A layout and the labels placed by it */
    private static final class Fitted {
        final DialLayout layout;
        final DialLayout.Label[] labels;

        Fitted(DialLayout layout, DialLayout.Label[] labels) {
            this.layout = layout;
            this.labels = labels;
        }
    }

    private static float diameter(Fitted fitted, float dp) {
        return fitted == null ? 0 : 2 * fitted.layout.radius / dp;
    }

    private static Fitted fitAround(RectF box, float dp, String[] names, String[] times, Paint name, Paint time, float[] theta, int next) {
        DialLayout.Label[] labels = labels(names, times, name, time, theta, next);
        DialLayout layout = new DialLayout(dp);
        return layout.fitAround(box.left, box.top, box.right, box.bottom, labels) ? new Fitted(layout, labels) : null;
    }

    private static Fitted fit(RectF box, float dp, String[] names, String[] times, Paint name, Paint time, float[] theta, int next) {
        DialLayout.Label[] labels = labels(names, times, name, time, theta, next);
        DialLayout layout = new DialLayout(dp);
        return layout.fit(box.left, box.top, box.right, box.bottom, labels) ? new Fitted(layout, labels) : null;
    }

    private static DialLayout.Label[] labels(String[] names, String[] times, Paint name, Paint time, float[] theta, int next) {
        DialLayout.Label[] labels = new DialLayout.Label[names.length];
        for (int i = 0; i < names.length; i++) {
            float width = name.measureText(names[i]), height = name.getTextSize() * LEADING;
            if (times != null) {
                width = Math.max(width, time.measureText(times[i]));
                height += time.getTextSize() * LEADING;
            }
            labels[i] = new DialLayout.Label(theta[i], width, height, i == next);
        }
        return labels;
    }

    /** One line of type, centred in its line's height; returns where the next line starts */
    private static float line(Canvas canvas, String text, Paint paint, Paint.Align align, float x, float top) {
        float height = paint.getTextSize() * LEADING;
        Paint.FontMetrics metrics = paint.getFontMetrics();
        paint.setTextAlign(align);
        canvas.drawText(text, x, top + height / 2 - (metrics.ascent + metrics.descent) / 2, paint);
        return top + height;
    }

    private static Paint type(Context context, DisplayMetrics metrics, float sp, float scale, boolean medium, int colour) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics) * scale);
        paint.setTypeface(medium ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
        paint.setColor(ContextCompat.getColor(context, colour));
        return paint;
    }
}

package islam.adhanalarm.widget;

import android.content.Context;
import android.graphics.Bitmap;
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
 * The prayer dial on its olive tile, for the widget from about 2 by 2 up. Each prayer is named at the end
 * of a pointer to its window, with its time as room allows, and the next prayer's name is highlighted. On
 * the biggest tiles the Islamic date sits underneath in small type. Everything is quieter than the dial:
 * the names in cream, the times and the date a step fainter but still easy to read, the pointers hairlines.
 * The tile itself is the layout's background; this draws what goes on it.
 */
final class DialTile {

    /** How much it shows: names, names with times but no a.m. or p.m., or names with full times */
    static final int NONE = 0, NAMES = 1, SHORT_TIMES = 2, TIMES = 3;

    // The smallest dial, in dp, worth showing each with. Below the first, the widget is set out like an
    // icon. That one is low, so a 2 by 2 keeps its names even on a day when long names crowd one side.
    static final float NAMES_DIAL = 72, SHORT_TIMES_DIAL = 120, TIMES_DIAL = 140, DATE_DIAL = 200;
    /** Times keep their a.m. and p.m. only while that leaves the dial at least this share of its size without them */
    static final float FULL_TIMES_SHARE = 0.92f;
    /** Lines of type are this many times their size apart */
    private static final float LEADING = 1.22f;
    /** In dp: the space between the labels and the date under them, the pointers' width, and the highlight's corners */
    private static final float DATE_GAP = 8, LEADER_WIDTH = 1, PILL_CORNER = 10;

    private DialTile() {
    }

    /**
     * The tile's picture for a widget of the given size in dp, drawn at the given share of full resolution
     * when the widget's memory is short, or null where there's no room for the names, so the widget is set
     * out like an icon instead
     */
    static Bitmap draw(Context context, ScheduleHandler schedule, DateFormat format, Calendar now, float widthDp, float heightDp, float scale) {
        float shorter = Math.min(widthDp, heightDp);
        if (shorter < NAMES_DIAL) return null; // No room for anything round the dial
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float dp = metrics.density * scale;
        int width = Math.round(widthDp * dp), height = Math.round(heightDp * dp);
        float pad = DialLayout.clamp(0.07f * shorter, 10, 16) * dp;
        RectF box = new RectF(pad, pad, width - pad, height - pad);

        // Type a little bigger on bigger tiles, and bigger again as the phone's font size asks
        float nameSp = DialLayout.clamp(11 + (shorter - 150) / 60, 11, 14);
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
        if (shown != null) {
            String text = schedule.getHijriDate(context);
            float line = date.getTextSize() * LEADING;
            RectF above = new RectF(box.left, box.top, box.right, box.bottom - line - DATE_GAP * dp);
            Fitted withDate = fit(above, dp, names, shown, name, time, theta, next);
            if (diameter(withDate, dp) >= DATE_DIAL && date.measureText(text) <= box.width()) {
                hijri = text;
                chosen = withDate;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Math.round(metrics.densityDpi * scale)); // Shown at the widget's size
        Canvas canvas = new Canvas(bitmap);
        DialLayout layout = chosen.layout;
        PrayerDial.paint(context, canvas, layout.centreX, layout.centreY, layout.radius, PrayerDial.unitFor(layout.radius, dp), schedule, now, false);
        paintLabels(context, canvas, chosen.labels, names, shown, name, time, schedule, dp);
        if (hijri != null) {
            line(canvas, hijri, date, Paint.Align.CENTER, width / 2f, box.bottom - date.getTextSize() * LEADING);
        }
        return bitmap;
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
    static int nextWindow(short nextTimeIndex) {
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
    static short startOf(int window, short nextTimeIndex) {
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

    private static Fitted fit(RectF box, float dp, String[] names, String[] times, Paint name, Paint time, float[] theta, int next) {
        DialLayout.Label[] labels = new DialLayout.Label[names.length];
        for (int i = 0; i < names.length; i++) {
            float width = name.measureText(names[i]), height = name.getTextSize() * LEADING;
            if (times != null) {
                width = Math.max(width, time.measureText(times[i]));
                height += time.getTextSize() * LEADING;
            }
            labels[i] = new DialLayout.Label(theta[i], width, height, i == next);
        }
        DialLayout layout = new DialLayout(dp);
        return layout.fit(box.left, box.top, box.right, box.bottom, labels) ? new Fitted(layout, labels) : null;
    }

    private static void paintLabels(Context context, Canvas canvas, DialLayout.Label[] labels, String[] names, String[] times,
                                    Paint name, Paint time, ScheduleHandler schedule, float dp) {
        Paint leader = new Paint(Paint.ANTI_ALIAS_FLAG);
        leader.setStyle(Paint.Style.STROKE);
        leader.setStrokeWidth(LEADER_WIDTH * dp);
        leader.setStrokeCap(Paint.Cap.ROUND);
        leader.setStrokeJoin(Paint.Join.ROUND);
        int copper = ContextCompat.getColor(context, R.color.dial_copper), cream = ContextCompat.getColor(context, R.color.dial_cream);
        Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        pill.setColor(ContextCompat.getColor(context, R.color.dial_pill));
        Path path = new Path();
        for (int i = 0; i < labels.length; i++) {
            DialLayout.Label label = labels[i];
            // The pointer wears its window's colour; the words stay in the ink
            leader.setColor(PrayerDial.isShort(schedule, i) ? copper : cream);
            path.rewind();
            path.moveTo(label.rimX, label.rimY);
            path.lineTo(label.elbowX, label.elbowY);
            path.lineTo(label.endX, label.endY);
            canvas.drawPath(path, leader);
            if (label.next) {
                RectF bounds = new RectF(label.left - DialLayout.PILL_X * dp, label.top - DialLayout.PILL_Y * dp,
                        label.right + DialLayout.PILL_X * dp, label.bottom + DialLayout.PILL_Y * dp);
                float corner = Math.min(bounds.height() / 2, PILL_CORNER * dp);
                canvas.drawRoundRect(bounds, corner, corner, pill);
            }
            // In a side column the words line up on the edge nearest the dial
            Paint.Align align = label.slot == DialLayout.LEFT ? Paint.Align.RIGHT : label.slot == DialLayout.RIGHT ? Paint.Align.LEFT : Paint.Align.CENTER;
            float x = align == Paint.Align.RIGHT ? label.right : align == Paint.Align.LEFT ? label.left : (label.left + label.right) / 2;
            float top = line(canvas, names[i], name, align, x, label.top);
            if (times != null) {
                line(canvas, times[i], time, align, x, top);
            }
        }
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

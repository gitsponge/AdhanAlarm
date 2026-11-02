package islam.adhanalarm.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import androidx.core.content.ContextCompat;

import java.util.Calendar;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.ScheduleHandler;

/**
 * A 24-hour sun dial in the app icon's olive: noon at the top, midnight at the bottom, and daylight from
 * sunrise to sunset as the lighter wedge. Each prayer's window runs around the rim at its true length, the
 * short ones in copper so they stand out, with only a hairline where no prayer is due. The sun rides a
 * hairline hand to show the time, dimmed while it is below the horizon, and the moon in the dark hub at the
 * centre shows how far through the Islamic month it is.
 */
final class PrayerDial {

    /** Each prayer's window, from its time to the time that ends it; isha's ends at the next fajr */
    static final short[] STARTS = {CONSTANT.FAJR, CONSTANT.DHUHR, CONSTANT.ASR, CONSTANT.MAGHRIB, CONSTANT.ISHAA};
    static final short[] ENDS = {CONSTANT.SUNRISE, CONSTANT.ASR, CONSTANT.MAGHRIB, CONSTANT.ISHAA, CONSTANT.NEXT_FAJR};

    /** A window shorter than this is one to pray promptly */
    static final long SHORT_MS = 2 * 60 * 60 * 1000;

    /** The days from one new moon to the next */
    private static final float LUNAR_MONTH = 29.53f;

    /** The dial is drawn in sixtieths of its width, each about a dp at the size of an app icon, so it scales as a whole */
    private static final float UNITS = 60;

    /** The rim's outer edge sits at this share of the dial's radius, leaving a margin of 1.5 sixtieths */
    static final float RIM_SHARE = 0.95f;

    private PrayerDial() {
    }

    /**
     * Degrees clockwise from the top: noon at the top, 6pm on the right, midnight at the bottom
     */
    static float angle(int hourOfDay, int minute) {
        return ((hourOfDay * 60 + minute) / 1440f * 360 + 180) % 360;
    }

    private static float angle(Calendar time) {
        return angle(time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE));
    }

    /**
     * Degrees clockwise from the top at the middle of a prayer's window
     */
    static float middleAngle(ScheduleHandler schedule, int window) {
        long start = schedule.getTime(STARTS[window]).getTimeInMillis(), end = schedule.getTime(ENDS[window]).getTimeInMillis();
        Calendar middle = Calendar.getInstance();
        middle.setTimeInMillis(start + (end - start) / 2);
        return angle(middle);
    }

    static boolean isShort(ScheduleHandler schedule, int window) {
        return isShort(schedule.getTime(ENDS[window]).getTimeInMillis() - schedule.getTime(STARTS[window]).getTimeInMillis());
    }

    /**
     * The size of one sixtieth for a dial whose rim has the given radius. Up to an app icon's size the dial
     * scales as a whole; past it its marks grow only with the square root, so a big dial gets finer rather
     * than chunkier.
     */
    static float unitFor(float rimRadius, float pixelsPerDp) {
        float width = 2 * rimRadius / RIM_SHARE, icon = UNITS * pixelsPerDp;
        return width <= icon ? width / UNITS : pixelsPerDp * (float) Math.sqrt(width / icon);
    }

    static boolean isShort(long windowMs) {
        return windowMs < SHORT_MS;
    }

    /**
     * How much of the moon is lit on the given day of the Islamic month, from a sliver on the first night to
     * full in the middle and back. It grows by the same amount each day, so even the first night's crescent shows.
     */
    static float lit(int hijriDay) {
        return 1 - Math.abs(1 - 2 * Math.min(hijriDay, LUNAR_MONTH) / LUNAR_MONTH);
    }

    /**
     * The moon is lit from the right while it grows, as it's seen north of the equator, and the other way round south of it
     */
    static boolean isLitOnRight(int hijriDay, double latitude) {
        return (hijriDay < LUNAR_MONTH / 2) == (latitude >= 0);
    }

    /** The dial on its own as a square bitmap, for the widget at an app icon's size */
    static Bitmap draw(Context context, ScheduleHandler schedule, Calendar now, int sizePx) {
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi); // Shown at exactly this size
        float centre = sizePx / 2f;
        paint(context, new Canvas(bitmap), centre, centre, centre * RIM_SHARE, sizePx / UNITS, schedule, now, true);
        return bitmap;
    }

    /**
     * Paints the dial centred on (x, y) with the rim's outer edge at the given radius, its marks measured in
     * the given unit, and the olive disc behind it only where it stands alone like an icon
     */
    static void paint(Context context, Canvas canvas, float x, float y, float rim, float unit, ScheduleHandler schedule, Calendar now, boolean disc) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (disc) {
            paint.setColor(ContextCompat.getColor(context, R.color.ic_launcher_background));
            canvas.drawCircle(x, y, rim + 1.5f * unit, paint);
        }

        // The face: night all round, daylight across the top
        float ringWidth = 5 * unit;
        float ringRadius = rim - ringWidth / 2;
        float face = ringRadius - ringWidth / 2 - 1.2f * unit;
        RectF faceBounds = new RectF(x - face, y - face, x + face, y + face);
        paint.setColor(ContextCompat.getColor(context, R.color.dial_night));
        canvas.drawCircle(x, y, face, paint);
        paint.setColor(ContextCompat.getColor(context, R.color.dial_day));
        canvas.drawArc(faceBounds, angle(schedule.getTime(CONSTANT.SUNRISE)) - 90, sweep(schedule, CONSTANT.SUNRISE, CONSTANT.MAGHRIB), true, paint);

        // The prayers around the rim, a hairline apart where one ends and the next begins, over a hairline where none is due
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.2f * unit);
        ring.setColor(ContextCompat.getColor(context, R.color.dial_track));
        canvas.drawCircle(x, y, ringRadius, ring);
        RectF ringBounds = new RectF(x - ringRadius, y - ringRadius, x + ringRadius, y + ringRadius);
        float gap = (float) Math.toDegrees(1.5f * unit / ringRadius);
        ring.setStrokeWidth(ringWidth);
        int copper = ContextCompat.getColor(context, R.color.dial_copper);
        int cream = ContextCompat.getColor(context, R.color.dial_cream);
        for (int i = 0; i < STARTS.length; i++) {
            ring.setColor(isShort(schedule, i) ? copper : cream);
            canvas.drawArc(ringBounds, angle(schedule.getTime(STARTS[i])) - 90 + gap / 2, Math.max(0, sweep(schedule, STARTS[i], ENDS[i]) - gap), false, ring);
        }

        // The time: the sun on a hairline hand, dimmed while it's below the horizon, where fajr and isha are reckoned from
        float sunRadius = 3.8f * unit;
        float sunDistance = face - sunRadius - 1.5f * unit;
        double radians = Math.toRadians(angle(now) - 90);
        float cos = (float) Math.cos(radians), sin = (float) Math.sin(radians);
        Paint hand = new Paint(Paint.ANTI_ALIAS_FLAG);
        hand.setColor(cream);
        hand.setStrokeWidth(1.3f * unit);
        hand.setStrokeCap(Paint.Cap.ROUND);
        float reach = sunDistance - sunRadius - 0.8f * unit;
        canvas.drawLine(x, y, x + reach * cos, y + reach * sin, hand);
        boolean daytime = !now.before(schedule.getTime(CONSTANT.SUNRISE)) && now.before(schedule.getTime(CONSTANT.MAGHRIB));
        paint.setColor(ContextCompat.getColor(context, daytime ? R.color.dial_sun : R.color.dial_sun_set));
        canvas.drawCircle(x + sunDistance * cos, y + sunDistance * sin, sunRadius, paint);

        // The dark hub the hand turns on, with the moon inside it, smaller than the sun
        paint.setColor(ContextCompat.getColor(context, R.color.dial_hub));
        canvas.drawCircle(x, y, 4.4f * unit, paint);
        drawMoon(canvas, x, y, 3 * unit, schedule.getHijriDay(), schedule.getLocation().getDegreeLat(), ContextCompat.getColor(context, R.color.dial_moon));
    }

    /**
     * The lit part of the moon as it is on the given day of the Islamic month; the hub behind it is its dark side
     */
    private static void drawMoon(Canvas canvas, float x, float y, float radius, int hijriDay, double latitude, int colour) {
        // Half the disc's edge on the lit side, then back along the shadow's edge: half an ellipse, bulging
        // toward the lit side while the moon is a crescent and away from it once it's more than half
        float lit = lit(hijriDay);
        float edge = Math.abs(1 - 2 * lit) * radius;
        RectF disc = new RectF(x - radius, y - radius, x + radius, y + radius);
        RectF shadow = new RectF(x - edge, y - radius, x + edge, y + radius);
        float back = lit < 0.5f ? -180 : 180;
        Path path = new Path();
        if (isLitOnRight(hijriDay, latitude)) {
            path.arcTo(disc, -90, 180, true); // Down the right
            path.arcTo(shadow, 90, back, false);
        } else {
            path.arcTo(disc, 90, 180, true); // Up the left
            path.arcTo(shadow, -90, back, false);
        }
        path.close();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(colour);
        canvas.drawPath(path, paint);
    }

    private static float sweep(ScheduleHandler schedule, short from, short to) {
        return (schedule.getTime(to).getTimeInMillis() - schedule.getTime(from).getTimeInMillis()) / 60000f / 1440 * 360;
    }
}

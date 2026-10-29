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
    private static final short[] STARTS = {CONSTANT.FAJR, CONSTANT.DHUHR, CONSTANT.ASR, CONSTANT.MAGHRIB, CONSTANT.ISHAA};
    private static final short[] ENDS = {CONSTANT.SUNRISE, CONSTANT.ASR, CONSTANT.MAGHRIB, CONSTANT.ISHAA, CONSTANT.NEXT_FAJR};

    /** A window shorter than this is one to pray promptly */
    static final long SHORT_MS = 2 * 60 * 60 * 1000;

    /** The days from one new moon to the next */
    private static final float LUNAR_MONTH = 29.53f;

    /** The dial is drawn in sixtieths of its width, each about a dp at the size of an app icon, so it scales as a whole */
    private static final float UNITS = 60;

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

    static Bitmap draw(Context context, ScheduleHandler schedule, Calendar now, int sizePx) {
        float unit = sizePx / UNITS;
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi); // Shown at exactly this size
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float centre = sizePx / 2f;

        paint.setColor(ContextCompat.getColor(context, R.color.ic_launcher_background));
        canvas.drawCircle(centre, centre, centre, paint);

        // The face: night all round, daylight across the top
        float ringWidth = 5 * unit;
        float ringRadius = centre - 1.5f * unit - ringWidth / 2;
        float face = ringRadius - ringWidth / 2 - 1.2f * unit;
        RectF faceBounds = new RectF(centre - face, centre - face, centre + face, centre + face);
        paint.setColor(ContextCompat.getColor(context, R.color.dial_night));
        canvas.drawCircle(centre, centre, face, paint);
        paint.setColor(ContextCompat.getColor(context, R.color.dial_day));
        canvas.drawArc(faceBounds, angle(schedule.getTime(CONSTANT.SUNRISE)) - 90, sweep(schedule, CONSTANT.SUNRISE, CONSTANT.MAGHRIB), true, paint);

        // The prayers around the rim, a hairline apart where one ends and the next begins, over a hairline where none is due
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.2f * unit);
        ring.setColor(ContextCompat.getColor(context, R.color.dial_track));
        canvas.drawCircle(centre, centre, ringRadius, ring);
        RectF ringBounds = new RectF(centre - ringRadius, centre - ringRadius, centre + ringRadius, centre + ringRadius);
        float gap = (float) Math.toDegrees(1.5f * unit / ringRadius);
        ring.setStrokeWidth(ringWidth);
        int copper = ContextCompat.getColor(context, R.color.dial_copper);
        int cream = ContextCompat.getColor(context, R.color.dial_cream);
        for (int i = 0; i < STARTS.length; i++) {
            long length = schedule.getTime(ENDS[i]).getTimeInMillis() - schedule.getTime(STARTS[i]).getTimeInMillis();
            ring.setColor(isShort(length) ? copper : cream);
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
        canvas.drawLine(centre, centre, centre + reach * cos, centre + reach * sin, hand);
        boolean daytime = !now.before(schedule.getTime(CONSTANT.SUNRISE)) && now.before(schedule.getTime(CONSTANT.MAGHRIB));
        paint.setColor(ContextCompat.getColor(context, daytime ? R.color.dial_sun : R.color.dial_sun_set));
        canvas.drawCircle(centre + sunDistance * cos, centre + sunDistance * sin, sunRadius, paint);

        // The dark hub the hand turns on, with the moon inside it, smaller than the sun
        paint.setColor(ContextCompat.getColor(context, R.color.dial_hub));
        canvas.drawCircle(centre, centre, 4.4f * unit, paint);
        drawMoon(canvas, centre, 3 * unit, schedule.getHijriDay(), schedule.getLocation().getDegreeLat(), ContextCompat.getColor(context, R.color.dial_moon));
        return bitmap;
    }

    /**
     * The lit part of the moon as it is on the given day of the Islamic month; the hub behind it is its dark side
     */
    private static void drawMoon(Canvas canvas, float centre, float radius, int hijriDay, double latitude, int colour) {
        // Half the disc's edge on the lit side, then back along the shadow's edge: half an ellipse, bulging
        // toward the lit side while the moon is a crescent and away from it once it's more than half
        float lit = lit(hijriDay);
        float edge = Math.abs(1 - 2 * lit) * radius;
        RectF disc = new RectF(centre - radius, centre - radius, centre + radius, centre + radius);
        RectF shadow = new RectF(centre - edge, centre - radius, centre + edge, centre + radius);
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

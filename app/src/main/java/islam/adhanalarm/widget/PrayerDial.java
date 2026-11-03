package islam.adhanalarm.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.core.content.ContextCompat;
import androidx.core.os.ConfigurationCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.ScheduleHandler;

/**
 * A 24-hour sun dial in the app icon's olive: noon at the top, midnight at the bottom, and daylight from
 * sunrise to sunset as the lighter wedge. Each prayer's window runs around the rim at its true length, the
 * short ones in copper so they stand out, with only a hairline where no prayer is due. The sun rides a
 * hairline hand to show the time, dimmed while it is below the horizon, and the moon in the dark hub at the
 * centre shows how far through the Islamic month it is. Bigger than an app icon, the moon is drawn as the moon
 * and the sun marks the exact minute against the prayers.
 */
final class PrayerDial {

    /** Each prayer's window, from its time to the time that ends it; isha's ends at the next fajr */
    static final short[] STARTS = {CONSTANT.FAJR, CONSTANT.DHUHR, CONSTANT.ASR, CONSTANT.MAGHRIB, CONSTANT.ISHAA};
    static final short[] ENDS = {CONSTANT.SUNRISE, CONSTANT.ASR, CONSTANT.MAGHRIB, CONSTANT.ISHAA, CONSTANT.NEXT_FAJR};

    /** A window shorter than this is one to pray promptly */
    static final long SHORT_MS = 2 * 60 * 60 * 1000;

    /** The dial is drawn in sixtieths of its width, each about a dp at the size of an app icon, so it scales as a whole */
    private static final float UNITS = 60;
    /**
     * In units: how far in from the face's edge the quarters' names reach, just inside the hours' ticks, and
     * how far from the centre the nearest side of one has to stay, clear of the hub and its moon, at least
     */
    private static final float NAME_INSET = 2.8f, HUB_CLEAR = 8;
    /** The quarters numbered, from midnight round, where their names won't fit */
    private static final String[] NUMBERS = {"24", "6", "12", "18"};

    /** The rim's outer edge sits at this share of the dial's radius, leaving a margin of 1.5 sixtieths */
    static final float RIM_SHARE = 0.95f;

    /** Bigger than an app icon, the moon's radius as a share of the face's: it grows as a picture, not as a mark */
    static final float MOON_SHARE = 0.15f;
    /**
     * How far past the rim the needle's tip reaches, in units, and at most in dp, so that on a big dial it still
     * stays within the room left round the dial
     */
    static final float TIP_REACH = 2.9f, TIP_REACH_DP = 8;
    /** Bigger than an app icon, the sun's radius in units */
    private static final float SUN_RADIUS = 5;

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

    static float lit(int hijriDay) {
        return Moon.lit(hijriDay);
    }

    static boolean isLitOnRight(int hijriDay, double latitude) {
        return Moon.isLitOnRight(hijriDay, latitude);
    }

    /** The dial on its own as a square bitmap, for the widget at an app icon's size */
    static Bitmap draw(Context context, ScheduleHandler schedule, Calendar now, int sizePx) {
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi); // Shown at exactly this size
        float centre = sizePx / 2f;
        paint(context, new Canvas(bitmap), centre, centre, centre * RIM_SHARE, sizePx / UNITS, context.getResources().getDisplayMetrics().density, schedule, now, true, NO_HOURS, null);
        return bitmap;
    }

    /**
     * How the face shows it's a 24-hour clock: not at all, a tick at each quarter of the day, or the quarters
     * named, noon at the top, midnight at the bottom and the sixes between, with a fainter tick at every hour.
     * A 12-hour clock has no midnight at the bottom.
     */
    static final int NO_HOURS = 0, QUARTER_TICKS = 1, QUARTER_NAMES = 2;

    /**
     * The quarters' names, from midnight round: midnight and noon in words, and the sixes as times are
     * written, 6 a.m. and 6 p.m. on a 12-hour clock or 06:00 and 18:00 on a 24-hour one
     */
    static String[] quarterNames(Context context, DateFormat format) {
        DateFormat sixes = format;
        if (format instanceof SimpleDateFormat && ((SimpleDateFormat) format).toPattern().replaceAll("'[^']*'", "").matches(".*[hK].*")) {
            // On a 12-hour clock the hour alone, as it's said
            Locale locale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
            if (locale == null) locale = Locale.getDefault();
            sixes = new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "h"), locale);
        }
        Calendar six = Calendar.getInstance();
        six.set(Calendar.HOUR_OF_DAY, 6);
        six.set(Calendar.MINUTE, 0);
        String morning = sixes.format(six.getTime());
        six.set(Calendar.HOUR_OF_DAY, 18);
        String evening = sixes.format(six.getTime());
        return new String[] {context.getString(R.string.dial_midnight), morning, context.getString(R.string.dial_noon), evening};
    }

    /**
     * Paints the dial centred on (x, y) with the rim's outer edge at the given radius, its marks measured in
     * the given unit, the olive disc behind it only where it stands alone like an icon, and the hours as asked,
     * with the quarters' names from quarterNames where they're named
     */
    static void paint(Context context, Canvas canvas, float x, float y, float rim, float unit, float dp, ScheduleHandler schedule, Calendar now, boolean disc, int hours, String[] quarters) {
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

        // At an app icon's size, the moon is its lit part on the dark hub; bigger, it's drawn as the moon, a thin
        // dark ring of hub round it
        float moonRadius = disc ? 3 * unit : moonRadius(face);
        float hubRadius = disc ? 4.4f * unit : moonRadius + 1.2f * unit;

        // The time: the sun on a hairline hand, dimmed while it's below the horizon, where fajr and isha are reckoned from
        double radians = Math.toRadians(angle(now) - 90);
        float cos = (float) Math.cos(radians), sin = (float) Math.sin(radians);
        float sunRadius = (disc ? 3.8f : SUN_RADIUS) * unit;
        float sunDistance = face - sunRadius - 1.5f * unit;
        if (hours != NO_HOURS) {
            // A quarter's name the sun is passing over waits for it to move on, rather than show half hidden
            paintHours(context, canvas, x, y, face, unit, schedule, hours, quarters, Math.max(HUB_CLEAR * unit, hubRadius + 2 * unit),
                    x + sunDistance * cos, y + sunDistance * sin, sunRadius + 0.8f * unit);
        }
        boolean daytime = !now.before(schedule.getTime(CONSTANT.SUNRISE)) && now.before(schedule.getTime(CONSTANT.MAGHRIB));
        if (disc) {
            drawHand(canvas, x, y, cos, sin, sunDistance - sunRadius - 0.8f * unit, unit, cream);
            paint.setColor(ContextCompat.getColor(context, daytime ? R.color.dial_sun : R.color.dial_sun_set));
            canvas.drawCircle(x + sunDistance * cos, y + sunDistance * sin, sunRadius, paint);
        } else {
            paintSun(context, canvas, x, y, rim, unit, dp, cos, sin, sunRadius, sunDistance, daytime);
        }

        // The dark hub the hand turns on, with the moon inside it
        paint.setColor(ContextCompat.getColor(context, R.color.dial_hub));
        canvas.drawCircle(x, y, hubRadius, paint);
        if (disc) {
            drawMoon(canvas, x, y, moonRadius, schedule.getHijriDay(), schedule.getLocation().getDegreeLat(), ContextCompat.getColor(context, R.color.dial_moon));
        } else {
            MoonFace.draw(context, canvas, x, y, moonRadius, schedule.getHijriDay(), schedule.getLocation().getDegreeLat());
        }
    }

    /** Bigger than an app icon, the moon's radius on a face of the given radius */
    static float moonRadius(float face) {
        return MOON_SHARE * face;
    }

    /** How far past the rim the needle's tip reaches */
    static float tipReach(float unit, float dp) {
        return Math.min(TIP_REACH * unit, TIP_REACH_DP * dp);
    }

    private static void drawHand(Canvas canvas, float x, float y, float cos, float sin, float reach, float unit, int colour) {
        Paint hand = new Paint(Paint.ANTI_ALIAS_FLAG);
        hand.setColor(colour);
        hand.setStrokeWidth(1.3f * unit);
        hand.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(x, y, x + reach * cos, y + reach * sin, hand);
    }

    /**
     * The sun bigger than an app icon: shaded, with a glow by day, and a dark dot at its centre, the old sign for
     * the sun and a sundial's nodus, at the exact moment. A needle runs from it across the prayer ring, dark where
     * it marks the minute against the prayers, and ends past the ring in a cream arrow, as the 24-hour hand of a
     * GMT watch does, so its tip shows on the tile and on the clock's black.
     */
    private static void paintSun(Context context, Canvas canvas, float x, float y, float rim, float unit, float dp, float cos, float sin, float radius, float distance, boolean daytime) {
        float sx = x + distance * cos, sy = y + distance * sin;
        int hub = ContextCompat.getColor(context, R.color.dial_hub), cream = ContextCompat.getColor(context, R.color.dial_cream);

        float tip = tipReach(unit, dp), scale = tip / (TIP_REACH * unit);
        float base = rim + 0.5f * unit * scale;
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(hub);
        canvas.drawPath(strip(x, y, cos, sin, distance + radius + 0.5f * unit, base, 0.6f * unit, 0.5f * unit), fill);
        fill.setColor(cream);
        canvas.drawPath(strip(x, y, cos, sin, base, rim + tip, 1.25f * unit * scale, 0), fill);

        drawHand(canvas, x, y, cos, sin, distance - radius - 0.8f * unit, unit, cream);

        int sun = ContextCompat.getColor(context, daytime ? R.color.dial_sun : R.color.dial_sun_set);
        if (daytime) {
            float glow = radius + 2.6f * unit;
            Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
            halo.setShader(new RadialGradient(sx, sy, glow, new int[] {withAlpha(sun, 115), withAlpha(sun, 115), withAlpha(sun, 0)},
                    new float[] {0, radius / glow, 1}, Shader.TileMode.CLAMP));
            canvas.drawCircle(sx, sy, glow, halo);
        }
        Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        body.setShader(new RadialGradient(sx - 0.3f * radius, sy - 0.3f * radius, 1.3f * radius,
                new int[] {ContextCompat.getColor(context, daytime ? R.color.dial_sun_core : R.color.dial_sun_set_core), sun,
                        ContextCompat.getColor(context, daytime ? R.color.dial_sun_limb : R.color.dial_sun_set_limb)},
                new float[] {0, 0.5f, 1}, Shader.TileMode.CLAMP));
        canvas.drawCircle(sx, sy, radius, body);
        fill.setColor(hub);
        canvas.drawCircle(sx, sy, 0.8f * unit, fill);
    }

    /** A strip out from the centre along the given direction, from one distance to another, tapering from one half-width to the other */
    private static Path strip(float x, float y, float cos, float sin, float from, float to, float halfFrom, float halfTo) {
        float across = -sin, down = cos;
        Path path = new Path();
        path.moveTo(x + from * cos + halfFrom * across, y + from * sin + halfFrom * down);
        path.lineTo(x + to * cos + halfTo * across, y + to * sin + halfTo * down);
        if (halfTo > 0) path.lineTo(x + to * cos - halfTo * across, y + to * sin - halfTo * down);
        path.lineTo(x + from * cos - halfFrom * across, y + from * sin - halfFrom * down);
        path.close();
        return path;
    }

    /**
     * The hours just inside the face's edge, in the hub's dark ink over daylight and in cream over night, so
     * they read on either as the seasons move sunrise and sunset. Each quarter's name sits as far out as it
     * goes with its corners clear of the hours' ticks, so a long one comes in a little. Where a name won't fit
     * clear of the hub, the quarters are numbered 6, 12, 18 and 24 instead. A name within the given reach of
     * the sun's centre is left out until the sun has passed.
     */
    private static void paintHours(Context context, Canvas canvas, float x, float y, float face, float unit, ScheduleHandler schedule, int hours, String[] quarters, float clear,
                                   float sunX, float sunY, float sunReach) {
        int sunrise = minuteOfDay(schedule.getTime(CONSTANT.SUNRISE)), sunset = minuteOfDay(schedule.getTime(CONSTANT.MAGHRIB));
        int dark = ContextCompat.getColor(context, R.color.dial_hub), cream = ContextCompat.getColor(context, R.color.dial_cream);
        Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        tick.setStrokeCap(Paint.Cap.BUTT);
        Paint name = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        name.setTextSize(5.2f * unit);
        float edge = face - NAME_INSET * unit;
        String[] names = quarters != null && namesFit(name, quarters, edge, clear) ? quarters : NUMBERS;
        Rect[] inks = new Rect[4];
        for (int q = 0; q < 4; q++) {
            inks[q] = new Rect();
            name.getTextBounds(names[q], 0, names[q].length(), inks[q]);
        }
        for (int hour = 0; hour < 24; hour++) {
            boolean quarter = hour % 6 == 0;
            if (hours == QUARTER_TICKS && !quarter) continue;
            boolean day = hour * 60 >= sunrise && hour * 60 < sunset;
            double radians = Math.toRadians(angle(hour, 0) - 90);
            float cos = (float) Math.cos(radians), sin = (float) Math.sin(radians);
            if (hours == QUARTER_NAMES && quarter) {
                // The name stands in for its tick, its ink centred where it fits
                Rect ink = inks[hour / 6];
                float r = reachInside(edge, cos, sin, ink.width() / 2f, ink.height() / 2f);
                float cx = x + r * cos, cy = y + r * sin, halfWidth = ink.width() / 2f, halfHeight = ink.height() / 2f;
                if (!covers(sunX, sunY, sunReach, cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight)) {
                    name.setColor(withAlpha(day ? dark : cream, day ? 215 : 225));
                    canvas.drawText(names[hour / 6], cx - ink.exactCenterX(), cy - ink.exactCenterY(), name);
                }
                continue;
            }
            float length = (quarter ? 3f : 1.6f) * unit;
            tick.setStrokeWidth((quarter ? 0.6f : 0.4f) * unit);
            tick.setColor(withAlpha(day ? dark : cream, day ? 150 : 130));
            float from = face - 0.7f * unit;
            canvas.drawLine(x + from * cos, y + from * sin, x + (from - length) * cos, y + (from - length) * sin, tick);
        }
    }

    /** Whether a circle reaches into a box: the box's nearest point to its centre is inside it */
    static boolean covers(float cx, float cy, float radius, float left, float top, float right, float bottom) {
        float dx = cx - Math.max(left, Math.min(cx, right)), dy = cy - Math.max(top, Math.min(cy, bottom));
        return dx * dx + dy * dy < radius * radius;
    }

    /** Whether each quarter's name, from midnight round, fits in from the edge with its nearest side the given distance from the centre */
    private static boolean namesFit(Paint paint, String[] names, float edge, float clear) {
        Rect ink = new Rect();
        for (int q = 0; q < 4; q++) {
            paint.getTextBounds(names[q], 0, names[q].length(), ink);
            boolean upright = q % 2 == 0; // Midnight below the hub and noon above it; the sixes beside it
            float halfAlong = upright ? ink.height() / 2f : ink.width() / 2f;
            if (reachInside(edge, upright ? 0 : 1, upright ? 1 : 0, ink.width() / 2f, ink.height() / 2f) - halfAlong < clear) return false;
        }
        return true;
    }

    /**
     * How far from the centre, along the direction (cos, sin), a box of the given half width and height can
     * be centred with all of it inside the circle of the given radius
     */
    static float reachInside(float radius, float cos, float sin, float halfWidth, float halfHeight) {
        // The far corner's distance is the radius where (r|cos| + w)² + (r|sin| + h)² = radius²
        float b = Math.abs(cos) * halfWidth + Math.abs(sin) * halfHeight;
        float c = halfWidth * halfWidth + halfHeight * halfHeight - radius * radius;
        return -b + (float) Math.sqrt(Math.max(0, b * b - c));
    }

    private static int minuteOfDay(Calendar time) {
        return time.get(Calendar.HOUR_OF_DAY) * 60 + time.get(Calendar.MINUTE);
    }

    private static int withAlpha(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | (alpha << 24);
    }

    /**
     * The lit part of the moon as it is on the given day of the Islamic month; the hub behind it is its dark side
     */
    private static void drawMoon(Canvas canvas, float x, float y, float radius, int hijriDay, double latitude, int colour) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(colour);
        canvas.drawPath(Moon.litPath(x, y, radius, hijriDay, latitude), paint);
    }

    private static float sweep(ScheduleHandler schedule, short from, short to) {
        return (schedule.getTime(to).getTimeInMillis() - schedule.getTime(from).getTimeInMillis()) / 60000f / 1440 * 360;
    }
}

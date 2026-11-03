package islam.adhanalarm.widget;

import android.graphics.Path;
import android.graphics.RectF;

/**
 * The moon on a day of the Islamic month: how much of it is lit and from which side. The dial's hub and the
 * crescent beside the Islamic date on Today both draw it from here.
 */
public final class Moon {

    /** The days from one new moon to the next */
    private static final float LUNAR_MONTH = 29.53f;

    private Moon() {
    }

    /**
     * How much of the moon is lit on the given day of the Islamic month, from a sliver on the first night to
     * full in the middle and back. It grows by the same amount each day, so even the first night's crescent shows.
     */
    public static float lit(int hijriDay) {
        return 1 - Math.abs(1 - 2 * Math.min(hijriDay, LUNAR_MONTH) / LUNAR_MONTH);
    }

    /**
     * The moon is lit from the right while it grows, as it's seen north of the equator, and the other way round south of it
     */
    public static boolean isLitOnRight(int hijriDay, double latitude) {
        return (hijriDay < LUNAR_MONTH / 2) == (latitude >= 0);
    }

    /**
     * The lit part of a moon of the given radius centred on (x, y): half the disc's edge on the lit side, then
     * back along the shadow's edge, half an ellipse bulging toward the lit side while the moon is a crescent
     * and away from it once it's more than half
     */
    public static Path litPath(float x, float y, float radius, int hijriDay, double latitude) {
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
        return path;
    }
}

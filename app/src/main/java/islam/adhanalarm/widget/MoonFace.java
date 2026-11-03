package islam.adhanalarm.widget;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import islam.adhanalarm.R;

/**
 * The moon in the dial's hub once there's room for it to look like the moon: a ball lit from the sun's side,
 * with the dark seas everyone knows. Its night side glows faintly, the way earthshine lights it, so even the
 * first night's crescent sits in a whole round moon. It's drawn from a few shapes, with no pictures, and
 * south of the equator the whole of it is turned over, as the moon is seen there.
 */
final class MoonFace {

    /*
     * The seas, traced from NASA's map of the moon (Scientific Visualization Studio, CGI Moon Kit) as it's seen
     * north of the equator, north up: all of them, then their darkest parts. Path data on a moon of radius 100
     * centred on 0,0, y down: M moves to a point, c and l run curves and lines relative to the last one, z closes.
     */
    static final String SEAS =
            "M-52-78c-1 0-2 1-3 1c0 0-2 1-4 2c-7 3-21 17-24 25c-1 0-2 3-3 4c0 1-1 3-2 4c0 0 0 3-1 4c-4 8-5 15-3 20c2 9 2 10 2 14c0 5 "
            + "0 6 3 14c4 9 5 11 8 12c2 1 2 1 3 3c1 3 3 3 4 1c1 0 2-1 4-2c1 0 3-1 4-1c2-2 5 1 5 5c0 2 0 2-4 5c-3 2-3 5-1 9c4 9 10 10 16"
            + " 4c5-5 10-1 6 4c-3 2-2 5 1 4c0 0 0 0 0-2c0-3 0-3 2-4c1 0 2-1 3-2c1-1 2-1 5 0c8 5 21 0 18-6c-1-2-1-6 0-10c1-3 1-5 0-6c-1-"
            + "2-2-4-2-7c0-1 0-2-2-3c-1-2-2-3-3-5c-2-2-2-3-3-2c-2 0-2 7 0 11c0 3 1 3 0 4c-2 2-3 1-4-2c-1-1-2-2-2-4c-4-5-5-6-3-9c2-3 0-6"
            + "-5-7c-2 0-6-3-7-4c-1-1-3-4-3-4c-2 0-2-2 0-4c1-2 1-2-1-4c-1-2-1-9 0-11c2-3 8-3 11-2c3 1 5 1 9 0c7-1 11 1 10 7c0 7-1 8-5 8"
            + "c-7 0-10 5-7 9c0 2 1 4 2 5c2 6 7 6 15 0c1-1 2-1 3-1c1 0 2 0 6 3c3 1 5-1 5-4c0-1-2-3-3-3c0 0-3-3-4-4c-3-5 4-10 10-7c3 2 8"
            + " 4 10 3c1 0 4-1 4-2c0 0 7-7 8-7c0 0 1 1 1 3c0 3 2 10 3 13c4 5 5 6 9 8c4 1 5 2 5 6c0 6 3 3 7-5c3-6 4-7 8-5c0 1 2 2 3 2c2 "
            + "0 4 2 4 3c0 1 0 2 0 2c0 1 0 3 1 4c0 3 0 4 2 6c1 1 1 2 1 3c1 2 1 2 6 3c4 2 4 2 5-2c0-4 1-6 4-8c3-2 3-3 3-5c0-1 1-3 1-4c2-"
            + "4 2-3-4-7c-2 0-4-2-5-3c-2-1-5-3-5-3c-2 0-5-4-5-6c-1-2-1-3-2-4c-4-2-4-3-6-6c-1-5-5-7-8-6c-4 2-5 1-8-10c0-3-1-5-3-7c-2-2-3"
            + "-3-3-6c0-4-4-6-7-3c-1 2-3 2-8 0c-5-2-7-2-11 4c-1 1-2 3-3 3c-2 1-2 2-2 4c2 6-1 15-5 17c-1 1-3 2-4 2c-3 2-3 2-6 2c-6 0-7-2"
            + "-2-5c4-2 4-6 1-7c-6-4-6-7-1-9c4-2 6-6 4-10c-1-3-1-4 0-7c2-5-2-11-8-9c-2 1-2 1-4-1c-3-2-4-2-9 0c-3 1-5 2-7 2c-6 1-8 1-10 "
            + "5c-1 1-2 2-3 3c-1 0-2 1-3 2c-4 3-7 2-6-2c0-1 0-3 0-5c0-3 0-3 2-4c3-1 4-1 4-3c2-1 0-2-4-1zM-58-20c5 4 4 12-1 13c-2 1-6-3-"
            + "6-6c0-4 5-9 7-7zM-38 25c0 1 1 3 0 5c-1 2-1 4 1 4c3 0 4 2 2 4c-1 2-1 2-3 0c-1-1-2-1-3-1c0 0-2 0-2 0c0-1-2 0-2 1c-2 1-3 0-"
            + "5-1c0-3 1-4 4-4c1 0 2-2 3-4c0-3 1-4 2-4c1-1 2-1 3 0zM77-38c-2 2-2 3-1 8c0 1 1 3 1 4c2 11 12 10 12-1c0-7-7-13-12-11zM55 2"
            + "1c-3 2-3 7-2 9c2 5 8 3 5-1c-1-1-1-3-1-4c0-4 0-5-2-4z";
    static final String DARKEST_SEAS =
            "M-31-70c-3 2-4 2-5 2c-2 0-3 0-4 3c-1 2-2 3-3 3c-1 1-2 2-2 2c-1 1-1 2-3 2c-2 2-3 4-2 4c1 1 2 1 3-1c1-2 4-5 6-4c0 1 0 3 0 "
            + "5c-1 0-1 2-1 3c0 1-1 2-1 2c0 0-1 1-2 3c-1 2-1 2-5 3c-7 0-11 7-5 7c0 0 0 0 0 0c0 0 1 0 1 0c1 0 2-1 2-1c1-1 2-2 4-3c2-3 3-"
            + "3 6-1c2 1 3 0 2-3c-1-2-1-5 0-4c0 0 0 0 0 0c1-2 5 1 5 3c-1 2 3 4 6 4c3 0 5-4 4-5c-1-2 0-3 1-3c2 0 5-2 5-4c0 0 0 0 1 0c0 0"
            + " 0-1 0-1c0-1-2-3-3-3c0 0 0-2 0-4c0-2 0-3-1-6c-3-5-3-5-9-3zM-71-63c-3 2-5 5-6 6c0 1-1 3-2 3c-2 2-2 2-2 5c0 2-1 3-1 4c-2 2"
            + "-2 3-3 5c0 2 0 2-1 3c-3 3-3 3-4 7c-1 5-1 9 2 18c0 1 0 5 0 8c0 10 6 22 10 22c2 0 3 0 4 0c3 2 6 2 9 0c3-1 4 0 6 2c0 1 2 2 "
            + "3 3c1 1 2 2 2 3c2 2 6 3 7 1c1-2 1-4-1-5c-6-4-7-7-7-13c0-7-1-8-5-10c-6-1-10-9-7-16c2-7 1-14-2-15c-6-2-6-5-3-11c1-3 2-5 2-"
            + "6c0-2 0-2 2-4c2-1 3-2 4-3c0 0 0-1 0-1c0 0 0 0 0-2c0 0 0-1 0-1c0 0 0 0 0 0c0-1-1-1-2-1c-1 0 0-2 0-2c1-1 1-3 0-3c-1-1-2 0-"
            + "5 3zM18-53c-2 1-1 3 1 5c1 1 1 3-2 2c-2 0-3 1-4 4c-2 6-1 12 3 10c2 0 4 0 5 0c2 1 2 1 4 1c3-1 3-1 5 1c4 2 5 3 2 6c-3 4-3 8"
            + " 1 16c3 6 3 6 7 7c1 1 3 1 4 2c4 3 4 2 6-2c2-4 5-6 9-3c4 2 7-1 6-8c-1-2-1-2 0-4c1-2 1-3-1-5c-1-1-2-3-3-4c-2-4-3-4-5-3c-2 "
            + "1-4 1-8-1c-2-2-2-3-3-13c-1-4-5-9-7-8c-1 0-6 9-6 12c-2 6-5 6-5 0c0-3 0-6 0-7c0 0 0-1 1-1c2-1 3-4 1-6c0 0 0 0-3 0c-3 0-4 0"
            + "-5-2c-1-1-2-1-3 1zM82-32c-2 3-2 6 0 9c1 3 3 2 4-2c2-5-2-11-4-7zM7-26c-4 3-5 5-2 7c7 7 15 3 8-4c-4-3-4-4-6-3zM-9-23c-1 1-"
            + "2 2-2 4c0 1-1 3-1 3c-8 4-8 13 0 11c3 0 3 0 2-6c0-5 0-5 1-6c4 0 7-3 7-4c0-2-5-3-7-2zM74-5c-1 1-2 3-3 4c-1 1-2 3 0 5c0 1 1"
            + " 2 1 4c0 5 2 9 5 7c1-1 1-2 3-7c1-1 3-4 3-4c3-4 2-6-2-9c-4-2-5-2-7 0zM-42 13c-1 1-3 7-3 8c0 1 4 0 7-1c3 0 4-1 2-2c0-1-2-2"
            + "-2-4c-2-3-3-3-4-1zM-57 32c-6 2-7 5-3 9c1 1 2 2 3 3c3 4 8 0 5-5c0-1-1-3-1-4c-1-3-1-4-4-3zM-24 32c-2 2-2 4 1 5c2 1 0 4-3 4"
            + "c-3 0-3 3-1 4c1 0 7-1 8-2c2-1 1-5-1-5c-1 0-1-1-1-4c-1-2-1-2-3-2z";
    /** The brightest craters, Tycho, Copernicus and Kepler, on a moon of radius 1: their centre and radius */
    private static final float[][] BRIGHT = {{-0.14f, 0.69f, 0.045f}, {-0.34f, -0.17f, 0.035f}, {-0.61f, -0.14f, 0.025f}};

    /** How dark the seas are, then their darkest parts on top, on the lit side and in earthshine on the night side */
    private static final float SEA_DAY = 0.3f, DARKEST_DAY = 0.25f, SEA_NIGHT = 0.25f, DARKEST_NIGHT = 0.2f;

    private static volatile Path sSeas, sDarkest;

    private MoonFace() {
    }

    static void draw(Context context, Canvas canvas, float x, float y, float radius, int hijriDay, double latitude) {
        float lit = Moon.lit(hijriDay);
        boolean litOnRight = Moon.isLitOnRight(hijriDay, latitude);
        float turn = latitude < 0 ? -1 : 1;
        if (sSeas == null) sSeas = readPath(SEAS);
        if (sDarkest == null) sDarkest = readPath(DARKEST_SEAS);
        Matrix place = new Matrix();
        place.setScale(turn * radius / 100, turn * radius / 100);
        place.postTranslate(x, y);
        Path seas = new Path(), darkest = new Path();
        sSeas.transform(place, seas);
        sDarkest.transform(place, darkest);

        int saved = canvas.save();
        Path disc = new Path();
        disc.addCircle(x, y, radius, Path.Direction.CW);
        canvas.clipPath(disc);

        // The lit side, brightest toward the sun and darker toward the edge, so it reads as a ball
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float side = litOnRight ? 1 : -1;
        paint.setShader(new RadialGradient(x + side * (1 - lit) * 0.55f * radius, y - 0.18f * radius, 1.45f * radius,
                new int[] {colour(context, R.color.dial_moon_light), colour(context, R.color.dial_moon), colour(context, R.color.dial_moon_limb)},
                new float[] {0, 0.45f, 1}, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, paint);
        paint.setShader(null);
        seas(canvas, seas, darkest, radius, colour(context, R.color.dial_moon_sea), SEA_DAY, DARKEST_DAY, paint);
        paint.setColor(colour(context, R.color.dial_moon_light));
        paint.setAlpha(140);
        for (float[] crater : BRIGHT) {
            canvas.drawCircle(x + turn * crater[0] * radius, y + turn * crater[1] * radius, crater[2] * radius, paint);
        }

        // The night side, softened where it meets the day; the seas still show faintly in earthshine
        Path night = nightPath(x, y, radius, lit, litOnRight);
        Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
        dark.setShader(new RadialGradient(x, y - 0.2f * radius, 1.25f * radius,
                colour(context, R.color.dial_moon_night), colour(context, R.color.dial_moon_night_limb), Shader.TileMode.CLAMP));
        dark.setMaskFilter(new BlurMaskFilter(Math.max(0.5f, 0.07f * radius), BlurMaskFilter.Blur.NORMAL));
        canvas.drawPath(night, dark);
        canvas.clipPath(night);
        seas(canvas, seas, darkest, radius, colour(context, R.color.dial_hub), SEA_NIGHT, DARKEST_NIGHT, paint);
        canvas.restoreToCount(saved);
    }

    /** The seas, softened at their edges once they're big enough for it to show, and their darkest parts over them */
    private static void seas(Canvas canvas, Path seas, Path darkest, float radius, int colour, float seaTone, float darkestTone, Paint paint) {
        float soft = 0.04f * radius;
        paint.setMaskFilter(soft >= 0.5f ? new BlurMaskFilter(soft, BlurMaskFilter.Blur.NORMAL) : null);
        paint.setColor(colour);
        paint.setAlpha(Math.round(255 * seaTone));
        canvas.drawPath(seas, paint);
        paint.setAlpha(Math.round(255 * darkestTone));
        canvas.drawPath(darkest, paint);
        paint.setMaskFilter(null);
    }

    /**
     * The unlit part of the moon: the shadow's edge, half an ellipse as in Moon.litPath, closed round the dark
     * side well outside the disc, so a blur softens only the edge with the day
     */
    static Path nightPath(float x, float y, float radius, float lit, boolean litOnRight) {
        float edge = Math.abs(1 - 2 * lit) * radius, reach = 1.4f * radius;
        RectF shadow = new RectF(x - edge, y - radius, x + edge, y + radius);
        RectF outside = new RectF(x - reach, y - reach, x + reach, y + reach);
        float sweep = lit < 0.5f ? 180 : -180; // A crescent's shadow edge bulges toward the lit side, a fuller moon's away from it
        Path path = new Path();
        if (litOnRight) {
            path.moveTo(x, y + radius);
            path.lineTo(x, y + reach);
            path.arcTo(outside, 90, 180, false); // Round the left to the top
            path.lineTo(x, y - radius);
            path.arcTo(shadow, -90, sweep, false);
        } else {
            path.moveTo(x, y - radius);
            path.lineTo(x, y - reach);
            path.arcTo(outside, -90, 180, false); // Round the right to the bottom
            path.lineTo(x, y + radius);
            path.arcTo(shadow, 90, sweep, false);
        }
        path.close();
        return path;
    }

    /**
     * The path data as steps, each its letter then its numbers: M x y, c dx1 dy1 dx2 dy2 dx dy, l dx dy, or z.
     * A c or l can run on with more numbers, each set a step of its own.
     */
    static List<float[]> steps(String data) {
        List<float[]> steps = new ArrayList<>();
        int at = 0, length = data.length();
        char letter = 'M';
        while (at < length) {
            char c = data.charAt(at);
            if (c == ' ') {
                at++;
                continue;
            }
            if (Character.isLetter(c)) {
                letter = c;
                at++;
                if (letter == 'z') steps.add(new float[] {'z'});
                continue;
            }
            int count = letter == 'c' ? 6 : 2;
            float[] step = new float[count + 1];
            step[0] = letter;
            for (int n = 1; n <= count; n++) {
                while (data.charAt(at) == ' ') at++;
                int start = at;
                if (data.charAt(at) == '-') at++;
                while (at < length && Character.isDigit(data.charAt(at))) at++;
                step[n] = Integer.parseInt(data.substring(start, at));
            }
            steps.add(step);
        }
        return steps;
    }

    private static Path readPath(String data) {
        Path path = new Path();
        for (float[] step : steps(data)) {
            switch ((char) step[0]) {
                case 'M':
                    path.moveTo(step[1], step[2]);
                    break;
                case 'c':
                    path.rCubicTo(step[1], step[2], step[3], step[4], step[5], step[6]);
                    break;
                case 'l':
                    path.rLineTo(step[1], step[2]);
                    break;
                default:
                    path.close();
            }
        }
        return path;
    }

    private static int colour(Context context, int id) {
        return ContextCompat.getColor(context, id);
    }
}

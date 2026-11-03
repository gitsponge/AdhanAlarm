package islam.adhanalarm.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import islam.adhanalarm.R;
import islam.adhanalarm.widget.Moon;

/**
 * The moon as it is tonight, set in a line of text just before the Islamic date it stands for: the lit part
 * in moonlight, the shadow dark, a hairline round it, a little taller than the figures beside it
 */
public class MoonSpan extends ReplacementSpan {

    /** Its size, and the room either side of it, as shares of the text's size; the text's own spaces set it apart */
    private static final float SIZE = 0.78f, SIDE = 0.05f;

    private final int mHijriDay;
    private final double mLatitude;
    private final int mLit, mShadow, mOutline;

    public MoonSpan(Context context, int hijriDay, double latitude) {
        mHijriDay = hijriDay;
        mLatitude = latitude;
        mLit = ContextCompat.getColor(context, R.color.moon_lit);
        mShadow = ContextCompat.getColor(context, R.color.moon_shadow);
        mOutline = ContextCompat.getColor(context, R.color.moon_outline);
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
        return Math.round(paint.getTextSize() * (SIZE + 2 * SIDE));
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        float radius = paint.getTextSize() * SIZE / 2;
        // Centred on the figures, which stand about seven tenths of the type's size above the baseline
        float centreY = y - paint.getTextSize() * 0.35f;
        float centreX = x + paint.getTextSize() * SIDE + radius;
        Paint moon = new Paint(Paint.ANTI_ALIAS_FLAG);
        moon.setColor(mShadow);
        canvas.drawCircle(centreX, centreY, radius, moon);
        moon.setColor(mLit);
        canvas.drawPath(Moon.litPath(centreX, centreY, radius, mHijriDay, mLatitude), moon);
        moon.setStyle(Paint.Style.STROKE);
        moon.setStrokeWidth(Math.max(1, paint.getTextSize() * 0.055f));
        moon.setColor(mOutline);
        canvas.drawCircle(centreX, centreY, radius, moon);
    }
}

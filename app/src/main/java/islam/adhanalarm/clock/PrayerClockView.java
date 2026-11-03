package islam.adhanalarm.clock;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import java.text.DateFormat;
import java.util.Calendar;

import islam.adhanalarm.handler.ScheduleHandler;
import islam.adhanalarm.widget.DialArt;

/**
 * The prayer dial drawn straight onto the screen, as big as its space allows, with every prayer named and
 * timed on its pointer and the next one highlighted. The type grows a little with the dial, and with the
 * phone's font size.
 */
public class PrayerClockView extends View {

    private ScheduleHandler mSchedule;
    private DateFormat mFormat;
    private Calendar mNow;
    private DialArt mArt;

    public PrayerClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Before Android 9 the screen's hardware drawing skips blurs, which soften the moon's seas and its night side's edge
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /** Shows the day's dial at the given moment */
    public void show(ScheduleHandler schedule, DateFormat format, Calendar now) {
        mSchedule = schedule;
        mFormat = format;
        mNow = now;
        lay();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        lay();
    }

    private void lay() {
        if (mSchedule == null || getWidth() == 0 || getHeight() == 0) return;
        float dp = getResources().getDisplayMetrics().density;
        // With no tile round it, the box is drawn in so the highlight and the pointers stay on the screen
        float edge = DialArt.OVERHANG * dp;
        RectF box = new RectF(getPaddingLeft() + edge, getPaddingTop() + edge, getWidth() - getPaddingRight() - edge, getHeight() - getPaddingBottom() - edge);
        float shorter = Math.min(box.width(), box.height()) / dp;
        // Bigger on a TV, which is read from across the room
        boolean television = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION;
        float nameSp = television ? Math.max(18, Math.min(26, 19 + (shorter - 480) / 60)) : Math.max(14, Math.min(20, 15 + (shorter - 360) / 60));
        mArt = DialArt.lay(getContext(), mSchedule, mFormat, mNow, box, dp, nameSp, false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mArt != null) mArt.paint(canvas);
    }
}

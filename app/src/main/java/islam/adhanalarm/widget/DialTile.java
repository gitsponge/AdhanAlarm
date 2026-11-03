package islam.adhanalarm.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.DisplayMetrics;

import java.text.DateFormat;
import java.util.Calendar;

import islam.adhanalarm.handler.ScheduleHandler;

/**
 * The prayer dial widget from about 2 by 2 up: the dial and its labels on the olive tile, with the Islamic
 * date underneath on the biggest. The tile itself is the layout's background; this draws what goes on it.
 */
final class DialTile {

    private DialTile() {
    }

    /**
     * The tile's picture for a widget of the given size in dp, drawn at the given share of full resolution
     * when the widget's memory is short, or null where there's no room for the names, so the widget is set
     * out like an icon instead
     */
    static Bitmap draw(Context context, ScheduleHandler schedule, DateFormat format, Calendar now, float widthDp, float heightDp, float scale) {
        float shorter = Math.min(widthDp, heightDp);
        if (shorter < DialArt.NAMES_DIAL) return null; // No room for anything round the dial
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float dp = metrics.density * scale;
        int width = Math.round(widthDp * dp), height = Math.round(heightDp * dp);
        float pad = DialLayout.clamp(0.07f * shorter, 10, 16) * dp;
        // Type a little bigger on bigger tiles, and bigger again as the phone's font size asks
        float nameSp = DialLayout.clamp(11 + (shorter - 150) / 60, 11, 14);
        DialArt art = DialArt.lay(context, schedule, format, now, new RectF(pad, pad, width - pad, height - pad), dp, nameSp, true);
        if (art == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Math.round(metrics.densityDpi * scale)); // Shown at the widget's size
        art.paint(new Canvas(bitmap));
        return bitmap;
    }
}

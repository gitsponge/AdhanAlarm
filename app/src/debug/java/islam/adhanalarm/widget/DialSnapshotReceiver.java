package islam.adhanalarm.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;

import islam.adhanalarm.App;
import islam.adhanalarm.Prefs;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.ScheduleHandler;

/**
 * Draws the prayer dial widget at the sizes given in dp, on its tile, into files/dial, so the real drawing
 * can be checked on a phone without placing and resizing widgets. A size too small for a tile comes out
 * as the icon-sized dial. Debug builds only, for example:
 *
 * adb shell am broadcast -a islam.adhanalarm.debug.DIAL_SNAPSHOT -n islam.adhanalarm.dev/islam.adhanalarm.widget.DialSnapshotReceiver --es sizes 164x191,246x287 --es time 16:40 --ei hijri 3
 * adb exec-out run-as islam.adhanalarm.dev cat files/dial/164x191.png > dial.png
 */
public class DialSnapshotReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String sizes = intent.getStringExtra("sizes");
        if (sizes == null) sizes = "164x191,246x287,328x191,328x382";
        Calendar now = Calendar.getInstance();
        String time = intent.getStringExtra("time");
        if (time != null && time.matches("\\d{1,2}:\\d{2}")) {
            now.set(Calendar.HOUR_OF_DAY, Integer.parseInt(time.substring(0, time.indexOf(':'))));
            now.set(Calendar.MINUTE, Integer.parseInt(time.substring(time.indexOf(':') + 1)));
        }
        SharedPreferences settings = App.getSettings(context);
        // A day of the Islamic month to show the moon on, in place of today's: --ei hijri 3
        int hijri = intent.getIntExtra("hijri", 0);
        ScheduleHandler schedule = hijri < 1 ? new ScheduleHandler(settings) : new ScheduleHandler(settings) {
            @Override
            public int getHijriDay() {
                return hijri;
            }
        };
        DateFormat format = Prefs.getTimeFormat(context, settings);
        File folder = new File(context.getFilesDir(), "dial");
        if (!folder.isDirectory() && !folder.mkdirs()) return;
        float density = context.getResources().getDisplayMetrics().density;
        for (String size : sizes.split(",")) {
            String[] parts = size.trim().split("x");
            float width = Float.parseFloat(parts[0]), height = Float.parseFloat(parts[1]);
            long started = System.nanoTime();
            Bitmap picture = DialTile.draw(context, schedule, format, now, width, height, 1);
            long took = (System.nanoTime() - started) / 1000000;
            Bitmap out;
            if (picture != null) {
                out = Bitmap.createBitmap(picture.getWidth(), picture.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(out);
                Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
                tile.setColor(ContextCompat.getColor(context, R.color.ic_launcher_background));
                float corner = context.getResources().getDimension(R.dimen.widget_radius);
                canvas.drawRoundRect(new RectF(0, 0, out.getWidth(), out.getHeight()), corner, corner, tile);
                canvas.drawBitmap(picture, 0, 0, null);
            } else {
                out = PrayerDial.draw(context, schedule, now, Math.round(Math.min(width * 0.74f, height * 0.63f) * density));
            }
            File file = new File(folder, size.trim() + ".png");
            try (FileOutputStream stream = new FileOutputStream(file)) {
                out.compress(Bitmap.CompressFormat.PNG, 100, stream);
            } catch (IOException ex) {
                Log.w("AdhanAlarm", "Dial snapshot not saved", ex);
            }
            Log.i("AdhanAlarm", "Dial snapshot " + size.trim() + (picture != null ? " tile" : " icon") + " in " + took + " ms");
        }
    }
}

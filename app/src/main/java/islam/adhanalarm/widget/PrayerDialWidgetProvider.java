package islam.adhanalarm.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.SizeF;
import android.widget.RemoteViews;

import androidx.core.os.BundleCompat;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import islam.adhanalarm.App;
import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.Prefs;
import islam.adhanalarm.R;
import islam.adhanalarm.clock.ClockActivity;
import islam.adhanalarm.handler.ScheduleHandler;

/**
 * Today's prayers as a 24-hour sun dial. At the size of a single cell it's set out like an app icon, with
 * the app's name under it. From about 2 by 2 up it sits on an olive tile with each prayer named on a pointer
 * to its window, adding the times and then the Islamic date as it grows. Its hand moves every quarter of an
 * hour at icon size and every 5 minutes on a tile, but only while the phone is awake, so it costs no
 * battery overnight. A big tile takes a moment to draw, so it's drawn away from the screen's thread.
 */
public class PrayerDialWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_TICK = "islam.adhanalarm.action.DIAL_TICK";
    private static final int REQUEST_TICK = 10;
    private static final int DEFAULT_DP = 60, MAX_DP = 240;
    private static final long TILE_TICK = 5 * 60 * 1000;
    /** The most sizes a widget can be given layouts for */
    private static final int MAX_SIZES = 16;

    /** One at a time, in order, so a later drawing always lands last */
    private static final ExecutorService DRAWING = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_TICK.equals(intent.getAction())) {
            updateAll(context, goAsync());
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateAll(context, goAsync());
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        updateAll(context, goAsync()); // Resized, which can change how often the hand needs to move
    }

    @Override
    public void onDisabled(Context context) {
        ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).cancel(tick(context));
    }

    public static void updateAll(Context context) {
        updateAll(context, null);
    }

    /** Redraws every dial widget in the background, then lets the broadcast that asked for it finish */
    private static void updateAll(Context context, BroadcastReceiver.PendingResult broadcast) {
        Context app = context.getApplicationContext();
        DRAWING.execute(() -> {
            try {
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(app);
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(app, PrayerDialWidgetProvider.class));
                if (appWidgetIds.length == 0) return;
                SharedPreferences settings = App.getSettings(app);
                ScheduleHandler schedule = new ScheduleHandler(settings);
                DateFormat format = Prefs.getTimeFormat(app, settings);
                Calendar now = Calendar.getInstance();
                boolean anyTile = false;
                for (int appWidgetId : appWidgetIds) {
                    anyTile |= updateAppWidget(app, appWidgetManager, appWidgetId, schedule, format, now);
                }
                scheduleTicks(app, anyTile); // Again after a restart, which clears alarms
            } finally {
                if (broadcast != null) broadcast.finish();
            }
        });
    }

    private static void scheduleTicks(Context context, boolean often) {
        long interval = often ? TILE_TICK : AlarmManager.INTERVAL_FIFTEEN_MINUTES;
        ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setInexactRepeating(AlarmManager.RTC,
                System.currentTimeMillis() + interval, interval, tick(context));
    }

    private static PendingIntent tick(Context context) {
        Intent intent = new Intent(context, PrayerDialWidgetProvider.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(context, REQUEST_TICK, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Draws the widget for each size the launcher may show it at. True if any of them is a tile. */
    private static boolean updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId,
                                           ScheduleHandler schedule, DateFormat format, Calendar now) {
        Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
        List<SizeF> sizes = sizes(context, options);
        float[] scales = memoryScales(context, sizes, options);
        boolean[] tile = {false};
        RemoteViews views;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sizes.size() > 1) {
            // Portrait and landscape, or a foldable's screens: the launcher shows whichever fits
            Map<SizeF, RemoteViews> layouts = new ArrayMap<>();
            for (int i = 0; i < sizes.size(); i++) {
                SizeF size = sizes.get(i);
                // Keyed just inside its size, so that size picks it however the launcher rounds
                layouts.put(new SizeF(Math.max(1, size.getWidth() - 1), Math.max(1, size.getHeight() - 1)),
                        build(context, schedule, format, now, size, scales[i], tile));
            }
            views = new RemoteViews(layouts);
        } else {
            views = build(context, schedule, format, now, sizes.get(0), scales[0], tile);
        }
        appWidgetManager.updateAppWidget(appWidgetId, views);
        return tile[0];
    }

    private static RemoteViews build(Context context, ScheduleHandler schedule, DateFormat format, Calendar now, SizeF size, float scale, boolean[] tile) {
        float widthDp = size.getWidth(), heightDp = size.getHeight();
        Bitmap picture = widthDp > 0 ? DialTile.draw(context, schedule, format, now, widthDp, heightDp, scale) : null;
        RemoteViews views;
        if (picture != null) {
            views = new RemoteViews(context.getPackageName(), R.layout.widget_dial_tile);
            views.setImageViewBitmap(R.id.dial, picture);
            views.setContentDescription(android.R.id.background, describeAll(context, schedule, format, now));
            tile[0] = true;
        } else {
            // The dial sits where the launcher puts an app icon, which takes about 74% of a cell's width and 63% of its height
            float dialDp = widthDp > 0 && heightDp > 0 ? Math.min(widthDp * 0.74f, heightDp * 0.63f) : DEFAULT_DP;
            int sizePx = Math.round(Math.max(40, Math.min(MAX_DP, dialDp)) * context.getResources().getDisplayMetrics().density);
            views = new RemoteViews(context.getPackageName(), R.layout.widget_dial);
            views.setImageViewBitmap(R.id.dial, PrayerDial.draw(context, schedule, now, sizePx));
            views.setContentDescription(android.R.id.background, describeNext(context, schedule, format));
        }
        // Opens the dial full screen, as the prayer clock
        views.setOnClickPendingIntent(android.R.id.background, PendingIntent.getActivity(context, 0, new Intent(context, ClockActivity.class), PendingIntent.FLAG_IMMUTABLE));
        return views;
    }

    /**
     * The sizes in dp the launcher may show the widget at: each one it reports from Android 12, otherwise
     * the portrait or landscape size for how the phone is held now, or 0 by 0 where it says nothing
     */
    static List<SizeF> sizes(Context context, Bundle options) {
        List<SizeF> sizes = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List<SizeF> reported = BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF.class);
            if (reported != null) {
                for (SizeF size : reported) {
                    if (size != null && size.getWidth() > 0 && size.getHeight() > 0 && !sizes.contains(size) && sizes.size() < MAX_SIZES) {
                        sizes.add(size);
                    }
                }
            }
        }
        if (sizes.isEmpty()) {
            boolean landscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            int width = options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            int height = options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            sizes.add(width > 0 && height > 0 ? new SizeF(width, height) : new SizeF(0, 0));
        }
        return sizes;
    }

    /**
     * How sharp to draw the picture for each size. A widget's pictures together may take no more memory than
     * it takes to fill the screen one and a half times, which a big tile's portrait and landscape pictures
     * can pass. The one for how the phone is held now is drawn sharp.
     */
    private static float[] memoryScales(Context context, List<SizeF> sizes, Bundle options) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        double[] bytes = new double[sizes.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = sizes.get(i).getWidth() * metrics.density * sizes.get(i).getHeight() * metrics.density * 4;
        }
        return scales(bytes, currentSize(context, sizes, options), 1.5 * metrics.widthPixels * metrics.heightPixels * 4 * 0.9);
    }

    /**
     * Shares the memory out: everything sharp if it fits; otherwise the current size sharp and the others a
     * little softer; and only if that would leave them under half sharpness, all of them softer alike
     */
    static float[] scales(double[] bytes, int current, double budget) {
        float[] scales = new float[bytes.length];
        java.util.Arrays.fill(scales, 1);
        double total = 0, others = 0;
        for (int i = 0; i < bytes.length; i++) {
            total += bytes[i];
            if (i != current) others += bytes[i];
        }
        if (total <= budget) return scales;
        double left = budget - bytes[current];
        if (others > 0 && left / others >= 0.25) {
            for (int i = 0; i < scales.length; i++) {
                if (i != current) scales[i] = (float) Math.sqrt(left / others);
            }
        } else {
            java.util.Arrays.fill(scales, (float) Math.sqrt(budget / total));
        }
        return scales;
    }

    /** Which of the sizes the launcher shows now: the nearest to its size for how the phone is held */
    private static int currentSize(Context context, List<SizeF> sizes, Bundle options) {
        boolean landscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        float width = options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        float height = options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        int nearest = 0;
        float distance = Float.MAX_VALUE;
        for (int i = 0; i < sizes.size(); i++) {
            float d = Math.abs(sizes.get(i).getWidth() - width) + Math.abs(sizes.get(i).getHeight() - height);
            if (d < distance) {
                distance = d;
                nearest = i;
            }
        }
        return nearest;
    }

    /** Read out at icon size: the next prayer, and the Islamic date, which the moon shows */
    private static String describeNext(Context context, ScheduleHandler schedule, DateFormat format) {
        short next = schedule.getNextTimeIndex();
        String separator = context.getString(R.string.list_separator);
        return context.getString(R.string.next_prayer) + separator
                + context.getString(R.string.name_and_time, context.getString(CONSTANT.TIME_NAMES[next]), format.format(schedule.getTime(next).getTime()))
                + separator + schedule.getHijriDate(context);
    }

    /** Read out on a tile: the next prayer, every prayer's time and which are short, and the Islamic date */
    public static String describeAll(Context context, ScheduleHandler schedule, DateFormat format, Calendar now) {
        short nextTime = schedule.getNextTimeIndex(now);
        String separator = context.getString(R.string.list_separator);
        StringBuilder text = new StringBuilder(context.getString(R.string.next_prayer)).append(separator)
                .append(nameAndTime(context, schedule, format, DialArt.nextWindow(nextTime), nextTime));
        for (int i = 0; i < PrayerDial.STARTS.length; i++) {
            String prayer = nameAndTime(context, schedule, format, i, nextTime);
            text.append(separator).append(PrayerDial.isShort(schedule, i) ? context.getString(R.string.dial_short_window, prayer) : prayer);
        }
        return text.append(separator).append(schedule.getHijriDate(context)).toString();
    }

    private static String nameAndTime(Context context, ScheduleHandler schedule, DateFormat format, int window, short nextTime) {
        short start = DialArt.startOf(window, nextTime);
        return context.getString(R.string.name_and_time, context.getString(CONSTANT.TIME_NAMES[start]), format.format(schedule.getTime(start).getTime()));
    }
}

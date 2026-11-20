package islam.adhanalarm.clock;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.os.ConfigurationCompat;
import androidx.core.view.ViewCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import islam.adhanalarm.App;
import islam.adhanalarm.Prefs;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.ScheduleHandler;
import islam.adhanalarm.service.AdhanService;
import islam.adhanalarm.widget.NextNotificationWidgetProvider;
import islam.adhanalarm.widget.PrayerDialWidgetProvider;

/**
 * Keeps the prayer dial full screen up to date: the dial, the time, what's next, and the day. It changes just
 * after each minute turns, and drifts the whole picture a few pixels each minute so nothing stays lit in one
 * place. The dial full screen and the screen saver each host one.
 */
public final class ClockController implements App.PrayerTimeListener, AdhanService.PlaybackListener {

    /** Told when the quiet part of the night starts and ends */
    public interface Host {
        void onNightChanged(boolean night);
    }

    /** Where the picture sits each minute, in dp: a slow loop round its true place */
    private static final float[][] DRIFT = {{0, 0}, {2, 1}, {3, -1}, {1, -3}, {-1, -2}, {-3, -1}, {-2, 2}, {0, 3}};

    private final Context mContext;
    private final View mRoot;
    private final boolean mCanStop;
    private final Host mHost;
    private final SharedPreferences mSettings;
    private final PrayerClockView mDial;
    private final TextView mTime, mPeriod, mHeadline, mSub, mDates;
    private final View mDot, mStop;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            update();
            scheduleTick();
        }
    };
    private ScheduleHandler mSchedule;
    private Boolean mNight;

    /**
     * With canStop, a Stop button shows while the adhan plays; the screen saver can't take taps, so it has none
     */
    public ClockController(Context context, View root, boolean canStop, Host host) {
        mContext = context;
        mRoot = root;
        mCanStop = canStop;
        mHost = host;
        mSettings = App.getSettings(context);
        mDial = root.findViewById(R.id.clock_dial);
        mTime = root.findViewById(R.id.clock_time);
        mPeriod = root.findViewById(R.id.clock_period);
        mHeadline = root.findViewById(R.id.clock_headline);
        mSub = root.findViewById(R.id.clock_sub);
        mDates = root.findViewById(R.id.clock_dates);
        mDot = root.findViewById(R.id.clock_dot);
        mStop = root.findViewById(R.id.clock_stop);
        mStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AdhanService.stop(mContext, true);
            }
        });
    }

    public void start() {
        mSchedule = new ScheduleHandler(mSettings);
        App.addListener(this);
        AdhanService.addPlaybackListener(this);
        onPlaybackChanged(AdhanService.isPlaying());
        mNight = null;
        update();
        scheduleTick();
    }

    public void stop() {
        mHandler.removeCallbacks(mTick);
        App.removeListener(this);
        AdhanService.removePlaybackListener(this);
    }

    @Override
    public void onPrayerTimeUpdated() {
        update(); // The settings or the place changed
    }

    @Override
    public void onPlaybackChanged(boolean playing) {
        mStop.setVisibility(mCanStop && playing ? View.VISIBLE : View.GONE);
    }

    private void scheduleTick() {
        mHandler.removeCallbacks(mTick);
        mHandler.postDelayed(mTick, 60000 - System.currentTimeMillis() % 60000 + 250); // Just after the minute turns
    }

    private void update() {
        mSchedule.update();
        Calendar now = Calendar.getInstance();
        DateFormat format = Prefs.getTimeFormat(mContext, mSettings);

        // The time, with a.m. or p.m. small beside it
        String[] clockAndPeriod = NextNotificationWidgetProvider.splitTime(format, now.getTime());
        mTime.setText(clockAndPeriod[0]);
        mPeriod.setText(clockAndPeriod[1]);
        mPeriod.setVisibility(clockAndPeriod[1].isEmpty() ? View.GONE : View.VISIBLE);

        // What's next, or how long a short window has left, keyed by a dot in its window's colour
        ClockWords words = ClockWords.at(mContext, mSchedule, format, now);
        mHeadline.setText(words.headline);
        mSub.setText(words.sub);
        ViewCompat.setBackgroundTintList(mDot, ColorStateList.valueOf(ContextCompat.getColor(mContext, words.shortWindow ? R.color.dial_copper : R.color.dial_cream)));

        // The day in both calendars; the Islamic one turns at sunset
        Locale locale = ConfigurationCompat.getLocales(mContext.getResources().getConfiguration()).get(0);
        if (locale == null) locale = Locale.getDefault();
        String date = new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(new Date());
        mDates.setText(mContext.getString(R.string.date_line, date, mSchedule.getHijriDate(mContext)));

        mDial.show(mSchedule, format, now);
        mDial.setContentDescription(PrayerDialWidgetProvider.describeAll(mContext, mSchedule, format, now));

        // A slow drift, so nothing burns into the screen
        float dp = mContext.getResources().getDisplayMetrics().density;
        float[] drift = DRIFT[(int) (now.getTimeInMillis() / 60000 % DRIFT.length)];
        mRoot.setTranslationX(drift[0] * dp);
        mRoot.setTranslationY(drift[1] * dp);

        if (mNight == null || mNight != words.night) {
            mNight = words.night;
            mHost.onNightChanged(words.night);
        }
    }
}

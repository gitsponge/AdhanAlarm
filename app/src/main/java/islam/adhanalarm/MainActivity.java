package islam.adhanalarm;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.os.ConfigurationCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import islam.adhanalarm.handler.CityDatabase;
import islam.adhanalarm.handler.CompassHandler;
import islam.adhanalarm.handler.LocationHandler;
import islam.adhanalarm.handler.ScheduleHandler;
import islam.adhanalarm.view.QiblaCompassView;

public class MainActivity extends AppCompatActivity implements App.PrayerTimeListener, CompassHandler.Listener {

    private static final String STATE_TAB = "tab";
    private static final double MOVED_KM = 1; // A smaller move doesn't change the times
    private static final int[] ALERT_ICONS = {R.drawable.ic_alert_off, R.drawable.ic_alert_notification, R.drawable.ic_alert_chime, R.drawable.ic_alert_adhan};
    private static final int[] ALERT_NAMES = {R.string.alert_off, R.string.alert_notification, R.string.alert_chime, R.string.alert_adhan};

    private SharedPreferences mSettings;
    private ScheduleHandler mScheduleHandler;
    private LocationHandler mLocationHandler;
    private CompassHandler mCompassHandler;
    private View mToday, mQibla;
    private QiblaCompassView mCompass;
    private final View[] mRows = new View[CONSTANT.NEXT_FAJR + 1]; // Fajr to isha, then tomorrow's fajr
    private View mTomorrowLine;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private DateFormat mTimeFormat;
    private boolean mArabic;
    private boolean mResumed;
    private double mQiblaBearing;
    private float mHeading;
    private boolean mAligned;
    private String mStateShown = "";

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            if (!Calendar.getInstance().before(mScheduleHandler.getNextTime())) {
                mScheduleHandler.update(); // A prayer time has passed while the screen was open
            }
            refreshToday();
            scheduleTick();
        }
    };

    private final ActivityResultLauncher<String[]> mPermissionRequest = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
        @Override
        public void onActivityResult(Map<String, Boolean> result) {
            updateLocationIfAutomatic();
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        mSettings = App.getSettings(this);
        if (!mSettings.getBoolean(Prefs.SETUP_DONE, false)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        mToday = findViewById(R.id.today);
        mQibla = findViewById(R.id.qibla);
        mCompass = findViewById(R.id.qibla_compass);
        applyInsets();

        NavigationBarView navigation = findViewById(R.id.navigation);
        navigation.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                showTab(item.getItemId());
                return true;
            }
        });
        int tab = savedInstanceState != null ? savedInstanceState.getInt(STATE_TAB, R.id.navigation_today) : R.id.navigation_today;
        navigation.setSelectedItemId(tab);
        showTab(tab);

        View.OnClickListener openSettings = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        };
        for (int id : new int[]{R.id.settings_button, R.id.qibla_settings_button}) {
            View button = findViewById(id);
            if (button != null) button.setOnClickListener(openSettings);
        }
        findViewById(R.id.location_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, LocationActivity.class));
            }
        });

        buildRows();
        mScheduleHandler = new ScheduleHandler(mSettings);
        mCompassHandler = new CompassHandler(this);
        mLocationHandler = new LocationHandler(this);
        mLocationHandler.addListener(new LocationHandler.LocationListener() {
            @Override
            public void onUpdated(Location currentLocation) {
                onLocationFound(currentLocation);
            }
        });

        App.addListener(this);
        Locations.nameIfNeeded(this);
        if (savedInstanceState == null) {
            requestMissingPermissions();
        }
        App.broadcastPrayerTimeUpdate(); // Makes sure an alarm is set, for example straight after installing
    }

    private void applyInsets() {
        final View content = findViewById(R.id.content);
        final View rail = findViewById(R.id.rail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container), new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View view, @NonNull WindowInsetsCompat insets) {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                if (rail != null) {
                    // The rail pads its own top; the settings button at its foot clears the navigation bar
                    rail.setPadding(bars.left, 0, 0, bars.bottom);
                    content.setPadding(0, bars.top, bars.right, bars.bottom);
                } else {
                    content.setPadding(bars.left, bars.top, bars.right, 0); // The bottom bar pads itself
                }
                return insets;
            }
        });
    }

    private void showTab(int id) {
        boolean qibla = id == R.id.navigation_qibla;
        mToday.setVisibility(qibla ? View.GONE : View.VISIBLE);
        mQibla.setVisibility(qibla ? View.VISIBLE : View.GONE);
        updateCompassTracking();
    }

    private void buildRows() {
        LinearLayout list = findViewById(R.id.prayer_list);
        LayoutInflater inflater = getLayoutInflater();
        int density = (int) getResources().getDisplayMetrics().density;
        for (short i = CONSTANT.FAJR; i <= CONSTANT.NEXT_FAJR; i++) {
            if (i == CONSTANT.NEXT_FAJR) { // A line between today and tomorrow
                View divider = new View(this);
                divider.setBackgroundColor(MaterialColors.getColor(list, com.google.android.material.R.attr.colorOutlineVariant));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, density));
                params.topMargin = 4 * density;
                list.addView(divider, params);
                mTomorrowLine = divider;
            }
            View row = inflater.inflate(R.layout.prayer_row, list, false);
            list.addView(row);
            mRows[i] = row;
            View alert = row.findViewById(R.id.alert);
            if (i == CONSTANT.NEXT_FAJR) {
                alert.setVisibility(View.INVISIBLE); // Keeps the time in line with the others
                row.setMinimumHeight(44 * density);
            } else {
                final short prayer = i;
                alert.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (getSupportFragmentManager().findFragmentByTag("alert") == null) { // A quick double tap opens it once
                            PrayerAlertSheet.newInstance(prayer).show(getSupportFragmentManager(), "alert");
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onPrayerTimeUpdated() {
        mScheduleHandler.update();
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        mResumed = true;
        mScheduleHandler.update();
        refresh();
        scheduleTick();
        updateCompassTracking();
        updateLocationIfAutomatic();
    }

    @Override
    public void onPause() {
        mResumed = false;
        mHandler.removeCallbacks(mTick);
        updateCompassTracking();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        App.removeListener(this);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, mQibla != null && mQibla.getVisibility() == View.VISIBLE ? R.id.navigation_qibla : R.id.navigation_today);
    }

    private void scheduleTick() {
        mHandler.removeCallbacks(mTick);
        mHandler.postDelayed(mTick, 60000 - System.currentTimeMillis() % 60000 + 250); // Just after the minute turns
    }

    private void refresh() {
        mTimeFormat = Prefs.getTimeFormat(this, mSettings);
        mArabic = Prefs.showArabicNames(this, mSettings);
        Locale locale = ConfigurationCompat.getLocales(getResources().getConfiguration()).get(0);
        if (locale == null) locale = Locale.getDefault();
        String date = new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMMM"), locale).format(new Date());
        ((TextView) findViewById(R.id.today_date)).setText(getString(R.string.date_line, date, mScheduleHandler.getHijriDate(this)));

        String place = Formats.place(this, mSettings);
        MaterialButton location = findViewById(R.id.location_button);
        location.setText(place);
        location.setContentDescription(getString(R.string.location_change, place));
        ((TextView) findViewById(R.id.location_note)).setText(Formats.placeNote(this, mSettings));

        refreshToday();
        refreshQibla();
    }

    private void refreshToday() {
        if (mTimeFormat == null) return;
        Calendar now = Calendar.getInstance();
        short next = mScheduleHandler.getNextTimeIndex();
        short current = mScheduleHandler.getCurrentPrayerIndex();

        // The next prayer, or sunrise as the end of fajr's time
        String nextName = getString(CONSTANT.TIME_NAMES[next]);
        String nextTime = mScheduleHandler.getFormattedTime(next, mTimeFormat);
        String countdown = Formats.countdown(this, mScheduleHandler.getTime(next).getTimeInMillis() - now.getTimeInMillis());
        ((TextView) findViewById(R.id.hero_label)).setText(next == CONSTANT.SUNRISE ? R.string.fajr_ends : R.string.next_prayer);
        ((TextView) findViewById(R.id.hero_name)).setText(nextName);
        setArabic((TextView) findViewById(R.id.hero_arabic), CONSTANT.ARABIC_NAMES[next]);
        ((TextView) findViewById(R.id.hero_time)).setText(nextTime);
        ((TextView) findViewById(R.id.hero_countdown)).setText(countdown);
        findViewById(R.id.hero).setContentDescription(getString(next == CONSTANT.SUNRISE ? R.string.fajr_ends : R.string.next_prayer) + ", " + nextName + ", " + nextTime + ", " + countdown);

        Calendar previous = mScheduleHandler.getPreviousTime(next);
        LinearProgressIndicator progress = findViewById(R.id.hero_progress);
        TextView from = findViewById(R.id.hero_from);
        TextView to = findViewById(R.id.hero_to);
        if (previous != null) {
            long span = mScheduleHandler.getTime(next).getTimeInMillis() - previous.getTimeInMillis();
            long gone = now.getTimeInMillis() - previous.getTimeInMillis();
            progress.setProgressCompat((int) Math.max(0, Math.min(1000, span > 0 ? gone * 1000 / span : 0)), false);
            short previousIndex = next == CONSTANT.FAJR ? CONSTANT.ISHAA : (short) (next - 1);
            from.setText(getString(R.string.name_and_time, getString(CONSTANT.TIME_NAMES[previousIndex]), mTimeFormat.format(previous.getTime())));
            to.setText(getString(R.string.name_and_time, nextName, nextTime));
        }
        int captions = previous != null ? View.VISIBLE : View.GONE;
        progress.setVisibility(captions);
        ((View) from.getParent()).setVisibility(captions);

        int past = ContextCompat.getColor(this, R.color.past);
        int primary = MaterialColors.getColor(mToday, androidx.appcompat.R.attr.colorPrimary);
        int ink = MaterialColors.getColor(mToday, com.google.android.material.R.attr.colorOnSurface);
        int inkVariant = MaterialColors.getColor(mToday, com.google.android.material.R.attr.colorOnSurfaceVariant);
        Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        for (short i = CONSTANT.FAJR; i <= CONSTANT.NEXT_FAJR; i++) {
            View row = mRows[i];
            boolean tomorrow = i == CONSTANT.NEXT_FAJR;
            boolean isNext = i == next;
            boolean isPast = !tomorrow && mScheduleHandler.getTime(i).before(now);
            int text = isPast ? past : isNext ? primary : tomorrow ? inkVariant : ink;

            TextView name = row.findViewById(R.id.name);
            name.setText(tomorrow ? R.string.fajr_tomorrow : CONSTANT.TIME_NAMES[i]);
            name.setTextColor(text);
            name.setTypeface(isNext || i == current ? medium : Typeface.DEFAULT);
            row.findViewById(R.id.badge).setVisibility(i == current ? View.VISIBLE : View.GONE);

            TextView supporting = row.findViewById(R.id.supporting);
            supporting.setVisibility(i == CONSTANT.SUNRISE ? View.VISIBLE : View.GONE);
            supporting.setText(R.string.fajr_ends);
            supporting.setTextColor(isPast ? past : inkVariant);

            TextView arabic = row.findViewById(R.id.arabic);
            setArabic(arabic, CONSTANT.ARABIC_NAMES[i]);
            arabic.setTextColor(isPast ? past : inkVariant);

            TextView time = row.findViewById(R.id.time);
            time.setText(mScheduleHandler.getFormattedTime(i, mTimeFormat));
            time.setTextColor(text);
            time.setTypeface(isNext || i == current ? medium : Typeface.DEFAULT);
            row.setBackgroundResource(isNext ? R.drawable.row_next : 0);

            if (!tomorrow) {
                short alert = mScheduleHandler.getNotificationType(i);
                if (alert < CONSTANT.NOTIFICATION_NONE || alert > CONSTANT.NOTIFICATION_PLAY) alert = CONSTANT.NOTIFICATION_DEFAULT;
                MaterialButton button = row.findViewById(R.id.alert);
                button.setIconResource(ALERT_ICONS[alert]);
                button.setIconTint(ColorStateList.valueOf(alert == CONSTANT.NOTIFICATION_NONE ? past : primary));
                button.setContentDescription(getString(R.string.alert_open, getString(CONSTANT.TIME_NAMES[i]), getString(ALERT_NAMES[alert])));
            }
        }
        // The line between today and tomorrow, unless the highlighted row would sit right against it
        mTomorrowLine.setVisibility(next == CONSTANT.ISHAA || next == CONSTANT.NEXT_FAJR ? View.INVISIBLE : View.VISIBLE);

        // The widest time sets the time column, so the Arabic names beside it share a right edge
        int timeWidth = getResources().getDimensionPixelSize(R.dimen.time_column);
        for (View row : mRows) {
            TextView time = row.findViewById(R.id.time);
            timeWidth = Math.max(timeWidth, (int) Math.ceil(time.getPaint().measureText(time.getText().toString())) + time.getTotalPaddingLeft() + time.getTotalPaddingRight());
        }
        for (View row : mRows) {
            ((TextView) row.findViewById(R.id.time)).setMinWidth(timeWidth);
        }

        findViewById(R.id.extreme_note).setVisibility(mScheduleHandler.hasExtremeTimes() ? View.VISIBLE : View.GONE);
    }

    private void setArabic(TextView view, int name) {
        view.setVisibility(mArabic ? View.VISIBLE : View.GONE);
        view.setText(name);
    }

    private void refreshQibla() {
        double latitude = Formats.getDouble(mSettings, Prefs.LATITUDE, Prefs.DEFAULT_LATITUDE);
        double longitude = Formats.getDouble(mSettings, Prefs.LONGITUDE, Prefs.DEFAULT_LONGITUDE);
        mQiblaBearing = Locations.qiblaBearing(latitude, longitude);
        String name = Prefs.getPlaceName(mSettings);
        String coordinates = Formats.coordinates(this, latitude, longitude);
        ((TextView) findViewById(R.id.qibla_place)).setText(name.isEmpty() ? coordinates : getString(R.string.place_and_coordinates, name, coordinates));
        ((TextView) findViewById(R.id.qibla_bearing)).setText(getString(R.string.qibla_direction, Formats.number(Math.round(mQiblaBearing), 0), Formats.compassPoint(this, mQiblaBearing)));
        double distance = CityDatabase.distanceKm(latitude, longitude, Locations.KAABA_LATITUDE, Locations.KAABA_LONGITUDE);
        ((TextView) findViewById(R.id.qibla_distance)).setText(getString(R.string.makkah_distance, Formats.number(Math.round(distance), 0)));
        mCompassHandler.setLocation(latitude, longitude, Formats.getDouble(mSettings, Prefs.ALTITUDE, 0));
        mStateShown = "";
        showHeading(mHeading, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, false);
    }

    private void updateCompassTracking() {
        if (mCompassHandler == null) return;
        if (mResumed && mQibla.getVisibility() == View.VISIBLE) {
            if (!mCompassHandler.startTrackingOrientation(this)) {
                showNoCompass();
            }
        } else {
            mCompassHandler.stopTrackingOrientation(this);
        }
    }

    @Override
    public void onHeading(float heading, int accuracy) {
        showHeading(heading, accuracy, true);
    }

    private void showHeading(float heading, int accuracy, boolean fromSensor) {
        mHeading = heading;
        double difference = ((mQiblaBearing - heading + 540) % 360) - 180;
        boolean aligned = Math.abs(difference) <= (mAligned ? 5 : 3); // Harder to leave than to reach, so it doesn't flicker
        if (aligned && !mAligned && fromSensor) {
            mCompass.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
        }
        mAligned = aligned;
        mCompass.setDirections(heading, (float) mQiblaBearing, aligned);

        int degrees = (int) Math.round(Math.abs(difference));
        String state = aligned ? getString(R.string.qibla_facing)
                : getString(difference > 0 ? R.string.qibla_turn_right : R.string.qibla_turn_left, degrees);
        if (!state.equals(mStateShown)) {
            mStateShown = state;
            TextView text = findViewById(R.id.qibla_state_text);
            ImageView icon = findViewById(R.id.qibla_state_icon);
            int colour = MaterialColors.getColor(text, aligned ? com.google.android.material.R.attr.colorTertiary : com.google.android.material.R.attr.colorOnSurface);
            text.setText(state);
            text.setTextColor(colour);
            icon.setImageResource(aligned ? R.drawable.ic_check_circle : difference > 0 ? R.drawable.ic_turn_right : R.drawable.ic_turn_left);
            icon.setImageTintList(ColorStateList.valueOf(colour));
            mCompass.setContentDescription(getString(R.string.compass_description, Math.round(heading), Math.round(mQiblaBearing)));
        }

        boolean calibrate = fromSensor && accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW;
        findViewById(R.id.qibla_calibrate).setVisibility(calibrate ? View.VISIBLE : View.GONE);
        findViewById(R.id.qibla_accuracy_row).setVisibility(calibrate ? View.GONE : View.VISIBLE);
        findViewById(R.id.qibla_accuracy_divider).setVisibility(calibrate ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.qibla_accuracy)).setText(accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_HIGH ? R.string.accuracy_good : R.string.accuracy_fair);
    }

    private void showNoCompass() {
        TextView text = findViewById(R.id.qibla_state_text);
        text.setText(R.string.qibla_no_compass);
        ((ImageView) findViewById(R.id.qibla_state_icon)).setImageResource(R.drawable.ic_warning);
        findViewById(R.id.qibla_accuracy_row).setVisibility(View.GONE);
        findViewById(R.id.qibla_accuracy_divider).setVisibility(View.GONE);
        mStateShown = "";
    }

    private void updateLocationIfAutomatic() {
        if (mSettings.getBoolean(Prefs.AUTO_LOCATION, true) && LocationHandler.hasPermission(this)) {
            mLocationHandler.update();
        }
    }

    private void onLocationFound(Location location) {
        double latitude = location.getLatitude(), longitude = location.getLongitude();
        boolean saved = mSettings.contains(Prefs.LATITUDE);
        double moved = CityDatabase.distanceKm(Formats.getDouble(mSettings, Prefs.LATITUDE, 0), Formats.getDouble(mSettings, Prefs.LONGITUDE, 0), latitude, longitude);
        if (!saved || moved > MOVED_KM) {
            Locations.save(this, latitude, longitude, null);
        }
    }

    private void requestMissingPermissions() {
        List<String> permissions = new ArrayList<>();
        if (mSettings.getBoolean(Prefs.AUTO_LOCATION, true) && !LocationHandler.hasPermission(this)) {
            permissions.addAll(Arrays.asList(LocationHandler.PERMISSIONS));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            mPermissionRequest.launch(permissions.toArray(new String[0]));
        }
    }
}

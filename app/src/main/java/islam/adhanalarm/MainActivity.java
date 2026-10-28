package islam.adhanalarm;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.text.HtmlCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import net.sourceforge.jitl.Jitl;
import net.sourceforge.jitl.astro.Dms;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import islam.adhanalarm.handler.CompassHandler;
import islam.adhanalarm.handler.LocationHandler;
import islam.adhanalarm.handler.ScheduleHandler;
import islam.adhanalarm.view.QiblaCompassView;

public class MainActivity extends AppCompatActivity {

    private LocationHandler mLocationHandler;
    private CompassHandler mCompassHandler;
    private ScheduleHandler mScheduleHandler;

    private final App.PrayerTimeListener mUpdateTimeListener = new App.PrayerTimeListener() {
        @Override
        public void onPrayerTimeUpdated() {
            mScheduleHandler.update();
        }
    };

    private final ActivityResultLauncher<String[]> mPermissionRequest = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
        @Override
        public void onActivityResult(Map<String, Boolean> result) {
            if (LocationHandler.hasPermission(MainActivity.this)) {
                mLocationHandler.update();
            }
        }
    });

    private final ActivityResultLauncher<Intent> mSettingsRequest = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
        @Override
        public void onActivityResult(ActivityResult result) {
            App.broadcastPrayerTimeUpdate();
        }
    });

    private final NavigationBarView.OnItemSelectedListener mOnNavigationItemSelectedListener
            = new NavigationBarView.OnItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_today) {
                findViewById(R.id.qibla).setVisibility(View.GONE);
                findViewById(R.id.today).setVisibility(View.VISIBLE);
                return true;
            } else if (itemId == R.id.navigation_qibla) {
                findViewById(R.id.today).setVisibility(View.GONE);
                findViewById(R.id.qibla).setVisibility(View.VISIBLE);
                return true;
            }
            return false;
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_settings) {
            mSettingsRequest.launch(new Intent(getApplicationContext(), SettingsActivity.class));
            return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Android 15+ draws the app behind the system bars and the action bar (the top inset includes it), so keep the content clear of them
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container), new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View view, @NonNull WindowInsetsCompat insets) {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsetsCompat.CONSUMED;
            }
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(HtmlCompat.fromHtml("<font face='monospace'>" + getSupportActionBar().getTitle() + "</font>", HtmlCompat.FROM_HTML_MODE_LEGACY));
        }

        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setOnItemSelectedListener(mOnNavigationItemSelectedListener);

        mCompassHandler = new CompassHandler((QiblaCompassView) findViewById(R.id.qibla_compass), (TextView) findViewById(R.id.bearing_north), (TextView) findViewById(R.id.bearing_qibla));

        mScheduleHandler = new ScheduleHandler(App.getSettings(this));

        mScheduleHandler.addListener(new ScheduleHandler.ScheduleListener() {
            @Override
            public void onUpdated(ScheduleHandler scheduleHandler) {
                GridLayout today = findViewById(R.id.today);

                /* Set timetable */

                final boolean isRTL = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                final int rowSelectedStart = isRTL ? R.drawable.row_selected_right : R.drawable.row_selected_left;
                final int rowSelectedEnd = isRTL ? R.drawable.row_selected_left : R.drawable.row_selected_right;
                final int nextTimeIndex = scheduleHandler.getNextTimeIndex();
                for (short i = CONSTANT.FAJR; i <= CONSTANT.NEXT_FAJR; i++) {
                    TextView labelView = (TextView) today.getChildAt(i * 2);
                    TextView timeView = (TextView) today.getChildAt(i * 2 + 1);
                    timeView.setText(scheduleHandler.getFormattedTime(i));
                    try {
                        labelView.setBackgroundResource(i == nextTimeIndex ? rowSelectedStart : R.drawable.row_divider);
                        timeView.setBackgroundResource(i == nextTimeIndex ? rowSelectedEnd : R.drawable.row_divider);
                    } catch (Resources.NotFoundException ex) {
                        labelView.setBackgroundResource(R.color.selectedRow);
                        timeView.setBackgroundResource(R.color.selectedRow);
                    }
                }

                /* Set Hijri date */

                if (getSupportActionBar() != null) {
                    getSupportActionBar().setSubtitle(HtmlCompat.fromHtml("<font face='monospace'>" + scheduleHandler.getCurrentHijriDate(MainActivity.this) + "</font>", HtmlCompat.FROM_HTML_MODE_LEGACY));
                }

                /* Set Qibla */

                net.sourceforge.jitl.astro.Location location = scheduleHandler.getLocation();

                double qibla = Jitl.getNorthQibla(location).getDecimalValue(net.sourceforge.jitl.astro.Direction.NORTH);

                mCompassHandler.update(qibla, location.getDegreeLat(), location.getDegreeLong(), location.getSeaLevel());

                setDms(R.id.current_latitude_deg, R.id.current_latitude_min, R.id.current_latitude_sec, location.getDegreeLat());
                setDms(R.id.current_longitude_deg, R.id.current_longitude_min, R.id.current_longitude_sec, location.getDegreeLong());
                setDms(R.id.current_qibla_deg, R.id.current_qibla_min, R.id.current_qibla_sec, qibla);
            }
        });
        mScheduleHandler.update();

        App.addListener(mUpdateTimeListener);

        mLocationHandler = new LocationHandler(this);
        mLocationHandler.addListener(new LocationHandler.LocationListener() {
            @Override
            public void onUpdated(Location currentLocation) {
                SharedPreferences settings = App.getSettings(MainActivity.this);
                settings.edit().putString("latitude", Double.toString(currentLocation.getLatitude())).apply();
                settings.edit().putString("longitude", Double.toString(currentLocation.getLongitude())).apply();
                App.broadcastPrayerTimeUpdate();
            }
        });

        if (savedInstanceState == null) {
            requestMissingPermissions();
        }
        App.broadcastPrayerTimeUpdate(); // Makes sure an alarm is set, for example straight after installing
    }

    private void setDms(int degreeId, int minuteId, int secondId, double value) {
        // Dms floors negative angles (-1.5° is stored as -2° plus 30'), so show the sign with the absolute value
        Dms dms = new Dms(Math.abs(value));
        ((TextView) findViewById(degreeId)).setText((value < 0 ? "-" : "") + dms.getDegree());
        ((TextView) findViewById(minuteId)).setText(String.valueOf(dms.getMinute()));
        ((TextView) findViewById(secondId)).setText(new DecimalFormat("#.###").format(dms.getSecond()));
    }

    private void requestMissingPermissions() {
        List<String> permissions = new ArrayList<>();
        if (!LocationHandler.hasPermission(this)) {
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

    @Override
    protected void onDestroy() {
        App.removeListener(mUpdateTimeListener);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        mCompassHandler.startTrackingOrientation(this);
        super.onResume();
    }
    @Override
    public void onPause() {
        mCompassHandler.stopTrackingOrientation(this);
        super.onPause();
    }

}

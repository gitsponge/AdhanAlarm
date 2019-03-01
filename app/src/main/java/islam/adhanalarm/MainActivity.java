package islam.adhanalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import net.sourceforge.jitl.Jitl;
import net.sourceforge.jitl.astro.Dms;

import java.text.DecimalFormat;

import islam.adhanalarm.handler.CompassHandler;
import islam.adhanalarm.handler.LocationHandler;
import islam.adhanalarm.handler.ScheduleHandler;
import islam.adhanalarm.view.QiblaCompassView;

public class MainActivity extends AppCompatActivity {

    private LocationHandler mLocationHandler;
    private CompassHandler mCompassHandler;
    private ScheduleHandler mScheduleHandler;

    private final BroadcastReceiver mUpdateTimeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mScheduleHandler.update();
        }
    };

    private final BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_today:
                    findViewById(R.id.qibla).setVisibility(View.GONE);
                    findViewById(R.id.today).setVisibility(View.VISIBLE);
                    return true;
                case R.id.navigation_qibla:
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
        switch (item.getItemId()) {
            case R.id.menu_settings:
                Intent i = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivityForResult(i, CONSTANT.RESULT_SETTINGS);
                return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CONSTANT.RESULT_SETTINGS) {
            App.broadcastPrayerTimeUpdate();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(Html.fromHtml("<font face='monospace'>" + getSupportActionBar().getTitle() + "</font>"));
        }

        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        mCompassHandler = new CompassHandler((QiblaCompassView) findViewById(R.id.qibla_compass), (TextView) findViewById(R.id.bearing_north), (TextView) findViewById(R.id.bearing_qibla));

        mScheduleHandler = new ScheduleHandler(PreferenceManager.getDefaultSharedPreferences(this));

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
                    getSupportActionBar().setSubtitle(Html.fromHtml("<font face='monospace'>" + scheduleHandler.getCurrentHijriDate(MainActivity.this) + "</font>"));
                }

                /* Set Qibla */

                net.sourceforge.jitl.astro.Location location = scheduleHandler.getLocation();

                DecimalFormat df = new DecimalFormat("#.###");
                Dms latitude = new Dms(location.getDegreeLat());
                Dms longitude = new Dms(location.getDegreeLong());
                Dms qibla = Jitl.getNorthQibla(location);

                mCompassHandler.update(qibla.getDecimalValue(net.sourceforge.jitl.astro.Direction.NORTH));

                ((TextView) findViewById(R.id.current_latitude_deg)).setText(String.valueOf(latitude.getDegree()));
                ((TextView) findViewById(R.id.current_latitude_min)).setText(String.valueOf(latitude.getMinute()));
                ((TextView) findViewById(R.id.current_latitude_sec)).setText(df.format(latitude.getSecond()));
                ((TextView) findViewById(R.id.current_longitude_deg)).setText(String.valueOf(longitude.getDegree()));
                ((TextView) findViewById(R.id.current_longitude_min)).setText(String.valueOf(longitude.getMinute()));
                ((TextView) findViewById(R.id.current_longitude_sec)).setText(df.format(longitude.getSecond()));
                ((TextView) findViewById(R.id.current_qibla_deg)).setText(String.valueOf(qibla.getDegree()));
                ((TextView) findViewById(R.id.current_qibla_min)).setText(String.valueOf(qibla.getMinute()));
                ((TextView) findViewById(R.id.current_qibla_sec)).setText(df.format(qibla.getSecond()));
            }
        });
        mScheduleHandler.update();

        LocalBroadcastManager.getInstance(this).registerReceiver(mUpdateTimeReceiver, new IntentFilter(CONSTANT.ACTION_UPDATE_PRAYER_TIME));

        mLocationHandler = new LocationHandler(this);
        mLocationHandler.addListener(new LocationHandler.LocationListener() {
            @Override
            public void onUpdated(Location currentLocation) {
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                settings.edit().putString("latitude", Double.toString(currentLocation.getLatitude())).apply();
                settings.edit().putString("longitude", Double.toString(currentLocation.getLongitude())).apply();
                App.broadcastPrayerTimeUpdate();
            }
        });
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mUpdateTimeReceiver);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LocationHandler.REQUEST_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mLocationHandler.update();
            }
        }
    }

}
package islam.adhanalarm.handler;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import islam.adhanalarm.App;

public class LocationHandler {

    public static final int REQUEST_LOCATION = 0;

    private Context mContext;
    private List<LocationListener> mCallbackList;

    public LocationHandler(Context context) {
        mCallbackList = new ArrayList<>();
        mContext = context;
        checkPermissions();
    }

    private boolean checkPermissions() {
        int permissionCheck1 = ContextCompat.checkSelfPermission(mContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION);
        int permissionCheck2 = ContextCompat.checkSelfPermission(mContext,
                android.Manifest.permission.ACCESS_COARSE_LOCATION);
        if (mContext instanceof Activity && (permissionCheck1 != PackageManager.PERMISSION_GRANTED || permissionCheck2 != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions((Activity) mContext,
                    new String[] {
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION
            );
            return false;
        }
        return true;
    }

    public void update() {
        if (checkPermissions()) {
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setCostAllowed(true);

            LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
            Location currentLocation = null;

            try {
                if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(mContext, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                    currentLocation = locationManager.getLastKnownLocation(locationManager.getBestProvider(criteria, true));
                }
                if (currentLocation == null && PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(mContext, android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    criteria.setAccuracy(Criteria.ACCURACY_COARSE);
                    currentLocation = locationManager.getLastKnownLocation(locationManager.getBestProvider(criteria, true));
                }
                if (currentLocation != null) {
                    App.broadcastPrayerTimeUpdate();
                    for (LocationListener callback : mCallbackList) {
                        callback.onUpdated(currentLocation);
                    }
                }
            } catch(Exception ex) {
                // GPS and wireless networks are disabled
            }
        }
    }

    public void addListener(LocationListener callback) {
        mCallbackList.add(callback);
    }

    public interface LocationListener {

        /**
         * This method is called after the location is updated
         */
        void onUpdated(Location currentLocation);
    }
}
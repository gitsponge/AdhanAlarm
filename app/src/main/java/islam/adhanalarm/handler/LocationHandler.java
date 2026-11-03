package islam.adhanalarm.handler;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;

import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.util.Consumer;

import java.util.ArrayList;
import java.util.List;

public class LocationHandler {

    public static final String[] PERMISSIONS = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    /** A fix this old still counts as where the phone is now */
    private static final long RECENT_MS = 2 * 60 * 1000;
    /** As the app opens, a fix older than this may be from before a journey, so a fresh one is asked for too */
    private static final long STALE_MS = 10 * 60 * 1000;

    private Context mContext;
    private List<LocationListener> mCallbackList;

    public LocationHandler(Context context) {
        mCallbackList = new ArrayList<>();
        mContext = context;
    }

    /**
     * Precise or approximate location both work, since Android 12 lets people choose
     */
    public static boolean hasPermission(Context context) {
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    /**
     * Location services can be off even with the permission granted
     */
    public static boolean isEnabled(Context context) {
        return LocationManagerCompat.isLocationEnabled((LocationManager) context.getSystemService(Context.LOCATION_SERVICE));
    }

    /**
     * Keeps the place current as the app opens: the last fix any app got, straight away, and when that one is
     * old, as after a journey, a fresh one as well
     */
    public void update() {
        update(false);
    }

    /**
     * @param recent When asked for where the phone is now, an old fix is only used if no new one comes
     */
    public void update(final boolean recent) {
        boolean isFineGranted = PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION);
        boolean isCoarseGranted = PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (!isFineGranted && !isCoarseGranted) {
            return;
        }

        LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        Location currentLocation = null;

        try {
            // The most recent fix from any provider this app may use, which is precise enough for prayer times
            List<String> providers = locationManager.getProviders(true);
            for (String provider : providers) {
                Location location = locationManager.getLastKnownLocation(provider);
                if (location != null && (currentLocation == null || location.getTime() > currentLocation.getTime())) {
                    currentLocation = location;
                }
            }
            String provider = getFreshFixProvider(providers);
            final Location lastLocation = currentLocation;
            long age = lastLocation == null ? Long.MAX_VALUE : System.currentTimeMillis() - lastLocation.getTime();
            boolean askFresh = needsFreshFix(recent, age, provider != null);
            if (lastLocation != null && (!recent || !askFresh)) {
                notifyListeners(lastLocation);
            }
            if (askFresh) {
                // A single fresh fix
                LocationManagerCompat.getCurrentLocation(locationManager, provider, new CancellationSignal(), ContextCompat.getMainExecutor(mContext), new Consumer<Location>() {
                    @Override
                    public void accept(Location location) {
                        if (location != null) {
                            notifyListeners(location);
                        } else if (recent && lastLocation != null) {
                            notifyListeners(lastLocation); // None came, so the old one will have to do
                        }
                    }
                });
            }
        } catch(Exception ex) {
            // GPS and wireless networks are disabled
        }
    }

    /**
     * Whether to ask for a fresh fix: when asked for where the phone is now, unless the last fix is from the last
     * couple of minutes; as the app opens, once the last fix is old enough that the phone may have been carried
     * somewhere else
     */
    static boolean needsFreshFix(boolean recent, long ageMs, boolean canAsk) {
        return canAsk && ageMs >= (recent ? RECENT_MS : STALE_MS);
    }

    private static String getFreshFixProvider(List<String> providers) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && providers.contains(LocationManager.FUSED_PROVIDER)) {
            return LocationManager.FUSED_PROVIDER;
        }
        if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        }
        if (providers.contains(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        }
        return null; // Only the passive provider, which waits for other apps

    }

    private void notifyListeners(Location currentLocation) {
        for (LocationListener callback : mCallbackList) {
            callback.onUpdated(currentLocation);
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

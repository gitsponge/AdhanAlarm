package islam.adhanalarm.handler;

import android.content.Context;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.widget.TextView;

import islam.adhanalarm.view.QiblaCompassView;

public class CompassHandler {

    private QiblaCompassView mQiblaCompass;

    private double mQiblaDirection;

    private SensorListener orientationListener;
    private boolean isTrackingOrientation = false;

    public CompassHandler(QiblaCompassView qiblaCompass, TextView bearingNorth, TextView bearingQibla) {
        mQiblaCompass = qiblaCompass;
        mQiblaCompass.setConstants(bearingNorth, bearingNorth.getText(), bearingQibla, bearingQibla.getText());

        orientationListener = new SensorListener() {
            public void onSensorChanged(int s, float v[]) {
                float northDirection = v[android.hardware.SensorManager.DATA_X];
                mQiblaCompass.setDirections(northDirection, (float) mQiblaDirection);
            }
            public void onAccuracyChanged(int s, int a) {
            }
        };
    }

    public void update(double qiblaDirection) {
        mQiblaDirection = qiblaDirection;
    }

    public void startTrackingOrientation(Context context) {
        if(!isTrackingOrientation) isTrackingOrientation = ((SensorManager) context.getSystemService(Context.SENSOR_SERVICE)).registerListener(orientationListener, android.hardware.SensorManager.SENSOR_ORIENTATION);
    }
    public void stopTrackingOrientation(Context context) {
        if(isTrackingOrientation) ((SensorManager) context.getSystemService(Context.SENSOR_SERVICE)).unregisterListener(orientationListener);
        isTrackingOrientation = false;
    }
}
package islam.adhanalarm.handler;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.Surface;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import islam.adhanalarm.view.QiblaCompassView;

public class CompassHandler implements SensorEventListener {

    private QiblaCompassView mQiblaCompass;

    private double mQiblaDirection;

    private boolean isTrackingOrientation = false;
    private Display mDisplay;

    private final float[] mRotationMatrix = new float[9];
    private final float[] mRemappedMatrix = new float[9];
    private final float[] mOrientation = new float[3];
    private float[] mGravity;
    private float[] mGeomagnetic;

    public CompassHandler(QiblaCompassView qiblaCompass, TextView bearingNorth, TextView bearingQibla) {
        mQiblaCompass = qiblaCompass;
        mQiblaCompass.setConstants(bearingNorth, bearingNorth.getText(), bearingQibla, bearingQibla.getText());
    }

    public void update(double qiblaDirection) {
        mQiblaDirection = qiblaDirection;
    }

    public void startTrackingOrientation(Context context) {
        if (isTrackingOrientation) return;
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mDisplay = ContextCompat.getDisplayOrDefault(context);
        Sensor rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotationVector != null) {
            isTrackingOrientation = sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI);
        } else {
            // Phones without a rotation vector sensor: work it out from the accelerometer and magnetometer
            Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (accelerometer != null && magnetometer != null) {
                isTrackingOrientation = sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
                        & sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    public void stopTrackingOrientation(Context context) {
        if (isTrackingOrientation) ((SensorManager) context.getSystemService(Context.SENSOR_SERVICE)).unregisterListener(this);
        isTrackingOrientation = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
        } else {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                mGravity = event.values.clone();
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                mGeomagnetic = event.values.clone();
            }
            if (mGravity == null || mGeomagnetic == null || !SensorManager.getRotationMatrix(mRotationMatrix, null, mGravity, mGeomagnetic)) {
                return;
            }
        }

        // Keep north correct if the screen is rotated
        int axisX = SensorManager.AXIS_X;
        int axisY = SensorManager.AXIS_Y;
        int rotation = mDisplay != null ? mDisplay.getRotation() : Surface.ROTATION_0;
        if (rotation == Surface.ROTATION_90) {
            axisX = SensorManager.AXIS_Y;
            axisY = SensorManager.AXIS_MINUS_X;
        } else if (rotation == Surface.ROTATION_180) {
            axisX = SensorManager.AXIS_MINUS_X;
            axisY = SensorManager.AXIS_MINUS_Y;
        } else if (rotation == Surface.ROTATION_270) {
            axisX = SensorManager.AXIS_MINUS_Y;
            axisY = SensorManager.AXIS_X;
        }
        SensorManager.remapCoordinateSystem(mRotationMatrix, axisX, axisY, mRemappedMatrix);
        SensorManager.getOrientation(mRemappedMatrix, mOrientation);

        float northDirection = (float) ((Math.toDegrees(mOrientation[0]) + 360) % 360);
        mQiblaCompass.setDirections(northDirection, (float) mQiblaDirection);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}

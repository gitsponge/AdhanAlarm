package islam.adhanalarm.handler;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.Surface;

import androidx.core.content.ContextCompat;

/**
 * Turns the phone's sensors into a heading from true north, smoothed so the compass doesn't shake
 */
public class CompassHandler implements SensorEventListener {

    public interface Listener {
        /**
         * @param heading degrees clockwise from true north that the top of the phone points
         * @param accuracy one of SensorManager.SENSOR_STATUS_*
         */
        void onHeading(float heading, int accuracy);
    }

    private static final float SMOOTHING = 0.15f; // Share of each new reading

    private final Listener mListener;
    private float mDeclination;
    private boolean isTrackingOrientation = false;
    private Display mDisplay;

    private final float[] mRotationMatrix = new float[9];
    private final float[] mRemappedMatrix = new float[9];
    private final float[] mOrientation = new float[3];
    private float[] mGravity;
    private float[] mGeomagnetic;
    private double mSin, mCos;
    private boolean mHasHeading;
    private int mAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;

    public CompassHandler(Listener listener) {
        mListener = listener;
    }

    public void setLocation(double latitude, double longitude, double altitude) {
        // The sensors find magnetic north, which can be more than 10° from true north, so turn the compass by the difference here
        mDeclination = new GeomagneticField((float) latitude, (float) longitude, (float) altitude, System.currentTimeMillis()).getDeclination();
    }

    /**
     * Returns false if the phone has no compass
     */
    public boolean startTrackingOrientation(Context context) {
        if (isTrackingOrientation) return true;
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mDisplay = ContextCompat.getDisplayOrDefault(context);
        mHasHeading = false;
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
        return isTrackingOrientation;
    }

    public void stopTrackingOrientation(Context context) {
        if (isTrackingOrientation) ((SensorManager) context.getSystemService(Context.SENSOR_SERVICE)).unregisterListener(this);
        isTrackingOrientation = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
            mAccuracy = event.accuracy;
        } else {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                mGravity = event.values.clone();
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                mGeomagnetic = event.values.clone();
                mAccuracy = event.accuracy;
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

        // Smooth the direction as a point on a circle, so 359° and 1° average to 0° rather than 180°
        double heading = mOrientation[0] + Math.toRadians(mDeclination);
        if (mHasHeading) {
            mSin += SMOOTHING * (Math.sin(heading) - mSin);
            mCos += SMOOTHING * (Math.cos(heading) - mCos);
        } else {
            mSin = Math.sin(heading);
            mCos = Math.cos(heading);
            mHasHeading = true;
        }
        float degrees = (float) ((Math.toDegrees(Math.atan2(mSin, mCos)) + 360) % 360);
        mListener.onHeading(degrees, mAccuracy);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR || sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mAccuracy = accuracy;
        }
    }
}

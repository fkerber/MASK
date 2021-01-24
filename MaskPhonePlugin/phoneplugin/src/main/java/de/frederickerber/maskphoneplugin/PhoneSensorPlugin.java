package de.frederickerber.maskphoneplugin;


import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Messenger;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import de.frederickerber.maskcommons.ErrorCode;
import de.frederickerber.maskcommons.SensorType;
import de.frederickerber.maskplugin.SensorService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PhoneSensorPlugin extends SensorService implements SensorEventListener {

    private static final String TAG = "PhonePlugin";

    private SensorManager mSensorManager;
    private SparseArray<Sensor> mSensors;
    private final Set<Integer> mActiveSensors = new HashSet<>();
    private ArrayList<Integer> mSupportedSensorTypes;
    private final SparseIntArray mSensorFrequencies = new SparseIntArray();

    @Override
    public void onCreate() {
        serviceName = "maskphoneplugin";
        mSupportedSensorTypes = new ArrayList<>();

        Log.d(TAG, "build serial: " + Build.SERIAL);
        mConnectedDevices.add(Build.SERIAL);

        Log.d(TAG, "serviceName " + serviceName);
        broadcastDeviceConnected(serviceName, mConnectedDevices.indexOf(Build.SERIAL));

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensors = new SparseArray<>();
        List<Sensor> mSensorList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor s : mSensorList) {
            int sensorType = SensorType.fromAndroidSensor(s.getType());
            if (sensorType != -1) {
                mSensors.put(sensorType, s);
                mSupportedSensorTypes.add(sensorType);
            }
        }
    }

    /**
     * Called when the first client registers with the service.
     * If necessary you should connect to the sensor device here.
     *
     * @param client
     */
    @Override
    protected void firstClientConnected(Messenger client) {
        broadcastDeviceConnected(serviceName, mConnectedDevices.indexOf(Build.SERIAL));
    }

    /**
     *
     * Called when the last client disconnected.
     * This might happen shortly before onDestroy if the client also unbinds afterwards.
     */
    @Override
    protected void lastClientDisconnected() {
        //we can stop doing anything now
        mSensorManager.unregisterListener(this);
    }

    /**
     * Return an array listing the sensors supported by this device.
     * For efficiency, the first non-null list returned by this method is saved so
     * changing this list after initial population is not possible.
     *
     * @param deviceIdentifier The identifier of the device
     * @return An array of {@link SensorType} listing the supported sensors of this device.
     */
    @Override
    protected ArrayList<Integer> getSupportedSensors(String deviceIdentifier) {
        return mSupportedSensorTypes;
    }


    /**
     * Whether the sensor device is currently connected or not.
     * Used to inform clients about the device status upon connecting.
     *
     * @return {@code true} if the device is connected {@code false} otherwise.
     */
    @Override
    protected boolean isDeviceConnected() {
        return true; //the phone never disconnects
    }


    /**
     * Called when the first client subscribes to the sensor.
     * You probably want to activate/subscribe to the hardware sensor here.
     *
     * @param deviceIndex The index of the device we want to subscribed.
     * @param sensorType The type of the sensor the client subscribed to.
     * @param client     The client that subscribed. Use this to send error messages etc.
     * @param frequency  The client's desired sensor rate in Hertz. If possible, keep the sensor at the maximum frequency the clients want.
     */
    @Override
    protected void newSensorSubscription(int deviceIndex, int sensorType, Messenger client, int frequency) {

        if (getSupportedSensors(mConnectedDevices.get(deviceIndex)).contains(sensorType)) {
            Sensor sensor = mSensors.get(sensorType);
            int sensorRate = SensorManager.SENSOR_DELAY_NORMAL;
            if (frequency > 0) {
                sensorRate = 1000000 / frequency;
            }
            if (sensor != null) {
                if (!mActiveSensors.contains(sensorType)) {
                    //sensor is not active, activate at desired rate
                    mSensorManager.registerListener(this, sensor, sensorRate);
                    Log.d(TAG, "register Sensor " + sensor + "with rate " + sensorRate);
                    mActiveSensors.add(sensorType);
                    mSensorFrequencies.put(sensorType, frequency);
                } else {
                    //sensor is already active, check if we need to increase rate
                    if (getMaxFrequency(mConnectedDevices.get(deviceIndex),sensorType) > mSensorFrequencies.get(sensorType)) {
                        //we need to increase the rate
                        mSensorManager.unregisterListener(this, sensor);
                        mSensorManager.registerListener(this, sensor, sensorRate);
                        mSensorFrequencies.put(sensorType, frequency);
                    }
                }
            }
        } else {
            sendErrorMessage(client, ErrorCode.SENSOR_NOT_SUPPORTED, "Unsupported sensor: " + sensorType);
        }
    }


    /**
     * Called after the last client unsubscribed from a sensor.
     * You may want to deactivate/unsubscribe from the hardware here.
     *
     * @param deviceIndex The index of the device.
     * @param sensorType The type of the sensor which no longer has any subscriptions.
     */
    @Override
    protected void allSubscriptionsEnded(int deviceIndex, int sensorType) {
        Sensor sensor = mSensors.get(sensorType);
        mActiveSensors.remove(sensorType);
        Log.d(TAG, "All subscriptions ended");
        if (sensor != null) {
            mSensorManager.unregisterListener(this, sensor);
        }
    }

    /**
     * This method is called when the rate desired by a sensors subscribers decreased because
     * a subscription stopped. Try to reduce the sensor rate here to reduce power consumption.
     *
     * @param sensorType The type of sensor
     */
    @Override
    protected void sensorRateDecreased(String deviceIdentifier, int sensorType) {
        Sensor sensor = mSensors.get(sensorType);
        if (sensor != null && hasSubscribers(sensorType)) {
            int maxRate = getMaxFrequency(deviceIdentifier,sensorType);
            if (mSensorFrequencies.get(sensorType) > maxRate) {
                mSensorManager.unregisterListener(this, sensor);
                mSensorManager.registerListener(this, sensor, maxRate > 0 ? 1000000 / getMaxFrequency(deviceIdentifier,sensorType) : SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    }

    @Override
    public void onDestroy() {
        //unregister listeners to be sure
        mSensorManager.unregisterListener(this);
    }

    /**
     * This method is called whenever the values of the sensors has changed.
     *
     * @param sensorEvent
     */
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        long ts = sensorEvent.timestamp;
        float[] v = sensorEvent.values;
        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                broadcastAccelReading(ts,serviceName,0, v);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                broadcastMagneticFieldReading(ts,serviceName,0, v);
                break;
            case Sensor.TYPE_GYROSCOPE:
                broadcastGyroscopeReading(ts,serviceName,0, v);
                break;
            case Sensor.TYPE_LIGHT:
                broadcastLightReading(ts,serviceName,0, v[0]);
                break;
            case Sensor.TYPE_PRESSURE:
                broadcastPressureReading(ts,serviceName,0, v[0]);
                break;
            case Sensor.TYPE_PROXIMITY:
                broadcastProximityReading(ts,serviceName,0, v[0]);
                break;
            case Sensor.TYPE_GRAVITY:
                broadcastGravityReading(ts,serviceName,0, v);
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                broadcastLinearAccelerationReading(ts,serviceName,0, v);
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                broadcastRotationReading(ts,serviceName,0, v);
                break;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                broadcastRelativeHumidityReading(ts,serviceName,0, v[0]);
                break;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                broadcastTemperatureReading(ts,serviceName,0, v[0]);
                break;
            case Sensor.TYPE_HEART_RATE:
                broadcastHeartRateReading(ts,serviceName,0, v[0]);
                break;
            case Sensor.TYPE_STEP_DETECTOR:
                broadcastStepDetected(ts,serviceName,0);
                break;
            case Sensor.TYPE_STEP_COUNTER:
                broadcastStepCounterReading(ts,serviceName,0, v[0]);
                break;
            default:
                Log.d(TAG, "apparently registered for unknown sensor " + sensorEvent.sensor.getName());
                mSensorManager.unregisterListener(this, sensorEvent.sensor);

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }


}

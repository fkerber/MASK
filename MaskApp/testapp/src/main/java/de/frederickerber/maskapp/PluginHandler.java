package de.frederickerber.maskapp;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import de.frederickerber.maskcommons.SensorType;
import de.frederickerber.maskconnection.MaskConnection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class PluginHandler extends MaskConnection {


    private static final String TAG = "PluginHandler";
    private long lastAccelReading = 0;
    private long lastGyroReading = 0;
    private final ArrayList<String> mdevices = new ArrayList<String>();
    private final List<Integer> mDeviceIds = new ArrayList<Integer>();

    private final List<String> msupportedSensors = new ArrayList<>();
    private List<Boolean> subscriptionList;
    private List<Tuples> allpossibleSubsribtions;

    public Map<String, TextView> mAllTextViews;

    private boolean mConnected;

    private final PluginFragment pluginFragment;
    private final String serviceAction;
    private final String packageName;
    private final String className;


    public PluginHandler(PluginFragment fragment, String serviceAction, String packageName, String className) {
        this.pluginFragment = fragment;
        this.serviceAction = serviceAction;
        this.packageName = packageName;
        this.className = className;

    }

    @Override
    protected void onSensorList(ArrayList<Integer> supportedSensors) {
        Log.d(TAG, "received a sensor list");
        if (!supportedSensors.isEmpty()) {
            for (int i : supportedSensors) {
                String sensorTypeString = SensorType.toString(i);
                if (sensorTypeString != null) {
                    msupportedSensors.add(sensorTypeString);
                }
            }
        }

        //init all possible Subscriptions
        allpossibleSubsribtions = new ArrayList<>(msupportedSensors.size() * mDeviceIds.size());
        for (int i = 0; i < mDeviceIds.size(); i++) {
            for (int j = 0; j < msupportedSensors.size(); j++) {
                allpossibleSubsribtions.add(new Tuples(mDeviceIds.get(i), msupportedSensors.get(j)));
            }
        }

        subscriptionList = new ArrayList<>(allpossibleSubsribtions.size());
        for (int i = 0; i < allpossibleSubsribtions.size(); i++) {
            subscriptionList.add(false);
            Log.d(TAG, "subscriptionList: " + subscriptionList);
        }
    }

    @Override
    protected void onDeviceConnected(String serviceName, int deviceIndex) {

        getSupportedSensors();
        mConnected = true;
        mAllTextViews = new HashMap<>();

        mdevices.clear();
        mDeviceIds.clear();

        mdevices.add(serviceName + " " + deviceIndex);
        mDeviceIds.add(deviceIndex);

        pluginFragment.connectToPlugin(mdevices, msupportedSensors);
        pluginFragment.setStatusText("connected");

    }

    @Override
    protected void onDeviceDisconnected(String serviceName, int deviceIndex) {

        for (Map.Entry<String, TextView> entry : mAllTextViews.entrySet()) {
            pluginFragment.getLinearLayout().removeView(entry.getValue());
        }
        mConnected = false;
        pluginFragment.disconnectFromPlugin();
        pluginFragment.setStatusText("disconnected");

    }

    @Override
    protected void onServiceDisconnected(Exception e) {

        mConnected = false;
        for (Map.Entry<String, TextView> entry : mAllTextViews.entrySet()) {
            pluginFragment.getLinearLayout().removeView(entry.getValue());
        }
        if (pluginFragment != null) {
            pluginFragment.disconnectFromPlugin();
            pluginFragment.setStatusText("disconnected");
        }

    }

    @Override
    protected void onAccelerometerData(long timestamp, String serviceName, int deviceIndex, float[] values) {
        Log.d(TAG, String.format("received accelerometer data with delay %d ms " + serviceName, (timestamp - lastAccelReading) / 1000000));
        double frequency = timestamp != lastAccelReading ? 1000000000 / (timestamp - lastAccelReading) : 0;
        lastAccelReading = timestamp;
        mAllTextViews.get(deviceIndex + "#" + SensorType.ACCELEROMETER).setText(String.format(Locale.ENGLISH, "x: %.2f y: %.2f z: %.2f rate: %.2f Hz", values[0], values[1], values[2], frequency));
    }

    @Override
    protected void onMagneticFieldData(long timestamp, String serviceName, int deviceIndex, float[] magneticFieldReading) {
        Log.d(TAG, "received magnetic field data " + Arrays.toString(magneticFieldReading));
        mAllTextViews.get(deviceIndex + "#" + SensorType.MAGNETIC_FIELD).setText(String.format(Locale.ENGLISH, "x: %.2f y: %.2f z: %.2f", magneticFieldReading[0], magneticFieldReading[1], magneticFieldReading[2]));
    }

    @Override
    protected void onLinearAccelerationData(long timestamp, String serviceName, int deviceIndex, float[] linearAcceleration) {
        Log.d(TAG, "received liner acceleration " + Arrays.toString(linearAcceleration));
        mAllTextViews.get(deviceIndex + "#" + SensorType.LINEAR_ACCELERATION).setText(String.format(Locale.ENGLISH, "x: %.2f y: %.2f z: %.2f", linearAcceleration[0], linearAcceleration[1], linearAcceleration[2]));
    }

    @Override
    protected void onGravityData(long timestamp, String serviceName, int deviceIndex, float[] values) {
        Log.d(TAG, "received gravity data " + Arrays.toString(values));
        mAllTextViews.get(deviceIndex + "#" + SensorType.GRAVITY).setText(String.format(Locale.ENGLISH, "x: %.2f y: %.2f z: %.2f", values[0], values[1], values[2]));
    }

    @Override
    protected void onRotationData(long timestamp, String serviceName, int deviceIndex, float[] rotationVector) {
        Log.d(TAG, "received rotation data " + Arrays.toString(rotationVector));
        mAllTextViews.get(deviceIndex + "#" + SensorType.ROTATION_VECTOR).setText(String.format(Locale.ENGLISH, "x: %.2f y: %.2f z: %.2f", rotationVector[0], rotationVector[1], rotationVector[2]));
    }

    @Override
    protected void onGyroscopeData(long timestamp, String serviceName, int deviceIndex, float[] values) {
        Log.d(TAG, String.format("received gyroscope data with delay %d ms", (timestamp - lastGyroReading) / 1000000));
        double frequency = timestamp != lastGyroReading ? 1000000000 / (timestamp - lastGyroReading) : 0;
        lastGyroReading = timestamp;
        mAllTextViews.get(deviceIndex + "#" + SensorType.GYROSCOPE).setText(String.format(Locale.ENGLISH, "x: %.2f y: %.2f z: %.2f rate: %.2f Hz", values[0], values[1], values[2], frequency));
    }

    @Override
    protected void onStepCounterData(long timestamp, String serviceName, int deviceIndex, float stepCount) {
        Log.d(TAG, "received step count: " + stepCount);
        mAllTextViews.get(deviceIndex + "#" + SensorType.STEP_COUNTER).setText(String.format(Locale.ENGLISH, "%.0f steps measured", stepCount));
    }

    @Override
    protected void onStepDetected(long timestamp, String serviceName, int deviceIndex) {
        Log.d(TAG, "received step detected");
        mAllTextViews.get(deviceIndex + "#" + SensorType.STEP_DETECTOR).setText("Step detected");
    }

    @Override
    protected void onProximityData(long timestamp, String serviceName, int deviceIndex, float distance) {
        Log.d(TAG, "received distance: " + distance);
        mAllTextViews.get(deviceIndex + "#" + SensorType.PROXIMITY).setText(String.format(Locale.ENGLISH, "Distance: %.1f cm", distance));
    }

    @Override
    protected void onSkinResistanceData(long timestamp, String serviceName, int deviceIndex, float resistance) {
        Log.d(TAG, "received resistance: " + resistance);
        mAllTextViews.get(deviceIndex + "#" + SensorType.SKIN_RESISTANCE).setText(String.format(Locale.ENGLISH, "GSR: %.1f kOhm", resistance));
    }

    @Override
    protected void onAmbientLightLevel(long timestamp, String serviceName, int deviceIndex, float lightLevel) {
        Log.d(TAG, "light received " + lightLevel);
        mAllTextViews.get(deviceIndex + "#" + SensorType.LIGHT).setText(String.format(Locale.ENGLISH, "Light: %.1f ", lightLevel));
    }

    @Override
    protected void onPressureData(long timestamp, String serviceName, int deviceIndex, float pressure) {
        Log.d(TAG, "pressure data received: " + pressure);
        mAllTextViews.get(deviceIndex + "#" + SensorType.PRESSURE).setText(String.format(Locale.ENGLISH, "Pressure: %.1f ", pressure));
    }

    @Override
    protected void onHeartRateData(long timestamp, String serviceName, int deviceIndex, float rate) {
        mAllTextViews.get(deviceIndex + "#" + SensorType.HEART_RATE).setText("heart rate: " + (int) rate);
        Log.d(TAG, "received heart rate: " + (int) rate);
    }

    @Override
    protected void onHeartRateVariabilityData(long timestamp, String serviceName, int deviceIndex, float rrVariability) {
        mAllTextViews.get(deviceIndex + "#" + SensorType.HEART_RATE_VARIABILITY).setText("RR data: " + (int) rrVariability);
        Log.d(TAG, "received RR data: " + (int) rrVariability);

    }

    @Override
    protected void onTemperatureData(long timestamp, String serviceName, int deviceIndex, float temperature) {
        mAllTextViews.get(deviceIndex + "#" + SensorType.TEMPERATURE).setText(String.format(Locale.ENGLISH, "Temperatur: " + temperature));
        Log.d(TAG, "received temperature: " + temperature);

    }


    @Override
    protected void onSensorData(int sensorType, String serviceName, int deviceIndex, Bundle values) {
        Log.d(TAG, "received data from unknown sensor " + sensorType);
    }


    public List<Boolean> getSubscriptionList() {
        return subscriptionList;
    }


    public List<Tuples> getAllpossibleSubsribtions() {
        return allpossibleSubsribtions;
    }

    public boolean isConnected() {
        return mConnected;
    }


    public String getServiceAction() {
        return serviceAction;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }
}

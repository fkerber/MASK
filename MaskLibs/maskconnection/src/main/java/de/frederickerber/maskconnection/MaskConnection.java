package de.frederickerber.maskconnection;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import de.frederickerber.maskcommons.BundleKeys;
import de.frederickerber.maskcommons.SensorType;
import de.frederickerber.maskcommons.ServiceMsg;

import static de.frederickerber.maskcommons.BundleKeys.DEVICE_INDEX;

/**
 * The abstract base class to establish a connection to a sensor service.
 * It offers methods to connect and disconnect to and from a Mask sensor service and subscribe to sensors supported by the service.
 * There are several methods that are called when certain events happen such as a device (dis)connecting or sensor readings arriving..
 */
public abstract class MaskConnection {

    private final static String TAG = "MaskConnection";

    private Messenger mService = null;
    private boolean mIsBound;

    private Context mBindingContext;


    private static class IncomingHandler extends Handler {

        private final WeakReference<MaskConnection> mConnection;

        IncomingHandler(MaskConnection instance) {
            mConnection = new WeakReference<>(instance);
        }

        @Override
        public void handleMessage(Message msg){

            MaskConnection c = mConnection.get();
            if (c != null) {
                switch (msg.what) {
                    case ServiceMsg.DISCONNECT_SERVICE:
                        Log.d(TAG, "service should disconnect");
                        c.disconnectFromSensorService(null);
                        break;
                    case ServiceMsg.DEVICE_CONNECTED:
                        Log.d(TAG,"device connected msg received");
                        Bundle data = msg.getData();
                        if(data != null && data.containsKey(BundleKeys.SERVICE_NAME) && data.containsKey(DEVICE_INDEX)){
                            c.onDeviceConnected(data.getString(BundleKeys.SERVICE_NAME), data.getInt(DEVICE_INDEX));
                            break;
                        }
                        break;
                    case ServiceMsg.DEVICE_DISCONNECTED:
                        Log.d(TAG,"device disconnected msg received");
                        data = msg.getData();
                        if(data != null && data.containsKey(BundleKeys.SERVICE_NAME) && data.containsKey(DEVICE_INDEX)){
                            c.onDeviceDisconnected(data.getString(BundleKeys.SERVICE_NAME), data.getInt(DEVICE_INDEX));
                            break;
                        }
                        break;
                    case ServiceMsg.LIST_SENSORS: {
                        Log.d(TAG,"sensor list msg received");
                        data = msg.getData();
                        if (data != null && data.containsKey(BundleKeys.SUPPORTED_SENSORS)) {
                            String serviceName  = data.getString(BundleKeys.SERVICE_NAME);
                            ArrayList<Integer> sensors = data.getIntegerArrayList(BundleKeys.SUPPORTED_SENSORS);
                            if(sensors != null){
                                c.onSensorList(sensors);
                            }
                        } else {
                            Log.e(TAG, "empty sensor listing in service message");
                        }
                        break;
                    }
                    case ServiceMsg.SENSOR_DATA:
                        data = msg.getData();
                        if(data != null && data.containsKey(BundleKeys.SERVICE_NAME) && data.containsKey(DEVICE_INDEX)){
                            Log.d(TAG, "sensor data received on device: " + data.get(BundleKeys.DEVICE_INDEX));
                            c.handleDeviceData(msg.arg1, data.getInt(BundleKeys.DEVICE_INDEX), data);
                            break;
                        }else {
                            Log.e(TAG, "no sensor data in service message");
                            break;
                        }

                    case ServiceMsg.ERROR:
                        try {
                            String emsg = msg.getData().getString("message");
                            Log.e(TAG, "Error message: " + emsg);
                            Log.e(TAG, "Error code: " + msg.arg1);
                        } catch (ClassCastException e) {
                            Log.e(TAG, "received error message without text");
                        }
                        break;
                    default:
                        Log.d(TAG, String.format("unhandled message type: %d", msg.what));
                }
            }
        }
    }

    final private Messenger mMessenger = new Messenger(new IncomingHandler(this));

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mService = new Messenger(iBinder);
            Log.d(TAG, "attached to service");
            try {
                Message msg = Message.obtain(null, ServiceMsg.REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                Log.d(TAG, "failed to register with service");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
            Log.d(TAG, "service disconnected");
        }
    };

    /**
     * Connect to a sensor service.
     * @param context An application context the service is bound to. Refer to {@link Context#bindService(Intent, ServiceConnection, int)} for more information.
     * @param sensorServiceAction The name of the action used to create the binding intent.
     * @param packageName The fully qualified package name of the service.
     * @param className The fully qualified class name of the service.
     * @see Context
     * @see Context#bindService(Intent, ServiceConnection, int)
     */
    public void connectToSensorService(Context context, String sensorServiceAction, String packageName, String className) {

        Intent serviceIntent = new Intent(sensorServiceAction);
        serviceIntent.setClassName(packageName, className);
        context.bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
        mBindingContext = context;
        mIsBound = true;
        Log.d(TAG, String.format("binding to service %s", sensorServiceAction));
    }


    /**
     * Disconnect from the sensor service. Will call {@link #onServiceDisconnected(Exception e)}.
     * @param context The same activity context used to connect to the service.
     */
    public void disconnectFromSensorService(Context context) {
        if (context==null) {
            context = mBindingContext;
        }
        if (mIsBound) {
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null, ServiceMsg.UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                }
            }
            context.unbindService(mConnection);
            mIsBound = false;
            Log.v(TAG, "unbinding from service");
            onServiceDisconnected(null);
        }else{
            onServiceDisconnected(null);
        }
    }

    /**
     * Send a message to the plugin asking to receive readings from the specified sensor.
     * The sensor specified should be in the list of supported sensors received via {@link #onSensorList(ArrayList)}.
     * If successful, sensor readings will be received in the appropriate method such as {@link #onAccelerometerData(long, String, int, float[])}.
     *
     * @param deviceIndex The index of the device.
     * @param sensorType The sensor type to subscribe to.
     * @param frequency The desired frequency at which you would like to receive sensor readings, in Hz.
     */
    public void subscribeToSensor(int deviceIndex, int sensorType, int frequency){
        if(mIsBound){
            if(mService != null){
                try{

                    Message msg = Message.obtain(null, ServiceMsg.SUBSCRIBE_TO_SENSOR, sensorType,frequency);
                    msg.getData().putInt(DEVICE_INDEX, deviceIndex);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                    Log.d(TAG, "message subscribe to sensor send");
                } catch (RemoteException e){
                    onServiceDisconnected(e);
                }
            }
        }
    }

    /**
     * Send a message to the plugin asking to unsubscribe from the specified sensor.
     * You should call this as soon as data from that sensor is no longer needed to reduce system and battery load.
     *
     * @param deviceIndex The index of the device.
     * @param sensorType The sensor to unsubscribe from.
     */
    public void unsubscribeFromSensor(int deviceIndex, int sensorType){
        if(mIsBound){
            if(mService != null){
                try{
                    Message msg = Message.obtain(null, ServiceMsg.UNSUBSCRIBE_FROM_SENSOR,sensorType,-1);
                    msg.getData().putInt(DEVICE_INDEX, deviceIndex);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e){
                    onServiceDisconnected(e);
                }
            }
        }
    }

    /**
     * Send a message to the plugin indicating whether automatic device reconnectes should be attempted (if supported).
     * @param shouldReconnect true, iff automatic reconnects should be tried (if supported).
     */
    public void setAutomaticReconnect(boolean shouldReconnect){
        Log.d(TAG, "First");
        if(mIsBound){
            Log.d(TAG,"bound");
            if(mService != null){
                try{
                    Log.d(TAG,"service exists");
                    Message msg = Message.obtain(null, ServiceMsg.ATTEMPT_AUTOMATIC_RECONNECT,shouldReconnect?1:0,-1);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e){
                    Log.d(TAG, "Exception: " + e.getMessage());
                    onServiceDisconnected(e);
                }
            }
        }
    }

    /**
     * Send a message to the plugin asking for a list of supported sensors.
     *
     * {@link #onSensorList(ArrayList)} will be called when the plugin responds.
     */
    public void getSupportedSensors(){
        if(mIsBound){
            if(mService != null){
                try {
                    Message msg = Message.obtain(null, ServiceMsg.LIST_SENSORS);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e){
                }
            }
        }
    }

    /**
     * This method retrieves sensor readings from a {@link Bundle} depending on the {@link SensorType} and calls the appropriate method (e.g. {@link #onAccelerometerData(long, String, int, float[])}.
     *
     * @param sensorType The sensor type the readings come from
     * @param deviceIndex  The index of the device.
     * @param values The sensor readings
     */

    //TODO ServiceName mitgeben oder auch deviceIndex aus Bundle nehmen?
    private void handleDeviceData(int sensorType, int deviceIndex, Bundle values) {
        long ts = values.getLong(BundleKeys.TIMESTAMP_NANO, -1);
        float [] readings = null;

        String serviceName = values.getString(BundleKeys.SERVICE_NAME);

        if(values.containsKey(BundleKeys.SENSOR_READINGS_FLOAT_ARRAY)){
            readings = values.getFloatArray(BundleKeys.SENSOR_READINGS_FLOAT_ARRAY);
        }
        float reading = -1;
        boolean hasReading = false;
        if(values.containsKey(BundleKeys.SENSOR_READINGS_SINGLE_FLOAT)){
            hasReading = true;
            reading = values.getFloat(BundleKeys.SENSOR_READINGS_SINGLE_FLOAT,-1);
        }
        switch (sensorType) {
            case SensorType.ACCELEROMETER: {
                if (readings == null) {
                    Log.e(TAG, "no accelerometer readings in service message");
                    break;
                }
                onAccelerometerData(ts, serviceName, deviceIndex, readings);
                break;
            }
            case SensorType.GYROSCOPE: {
                if (readings == null) {
                    Log.e(TAG, "no gyroscope readings in service message");
                    break;
                }
                onGyroscopeData(ts,serviceName, deviceIndex, readings);
                break;
            }
            case SensorType.LIGHT: {
                if(!hasReading){
                    Log.e(TAG, "no light level reading in service message");
                    break;
                }
                onAmbientLightLevel(ts,serviceName, deviceIndex, reading);
                break;
            }
            case SensorType.GRAVITY:
                if(readings == null){
                    Log.e(TAG, "no gravity readings in service message");
                    break;
                }
                onGravityData(ts, serviceName, deviceIndex, readings);
                break;
            case SensorType.HEART_RATE:
                if(!hasReading){
                    Log.e(TAG, "no heart rate reading in service message");
                    break;
                }
                onHeartRateData(ts,serviceName, deviceIndex,reading);
                break;
            case SensorType.HEART_RATE_VARIABILITY:
                if(!hasReading){
                    Log.e(TAG, "no heart rate reading in service message");
                    break;
                }
                onHeartRateVariabilityData(ts,serviceName, deviceIndex,reading);
                break;

            case SensorType.LINEAR_ACCELERATION:
                if(readings == null){
                    Log.e(TAG,"no linear acceleration readings in service message");
                    break;
                }
                onLinearAccelerationData(ts,serviceName, deviceIndex,readings);
                break;
            case SensorType.MAGNETIC_FIELD:
                if(readings == null){
                    Log.e(TAG,"no magnetic field readings in service message");
                    break;
                }
                onMagneticFieldData(ts,serviceName, deviceIndex,readings);
                break;
            case SensorType.PRESSURE:
                if(!hasReading){
                    Log.e(TAG, "no pressure reading in service message");
                    break;
                }
                onPressureData(ts,serviceName, deviceIndex,reading);
                break;
            case SensorType.PROXIMITY:
                if(!hasReading){
                    Log.e(TAG, "no proximity reading in service message");
                    break;
                }
                onProximityData(ts,serviceName, deviceIndex,reading);
                break;
            case SensorType.RELATIVE_HUMIDITY:
                if(!hasReading){
                    Log.e(TAG, "no humidity reading in service message");
                    break;
                }
                onRelativeHumidityData(ts,serviceName, deviceIndex,reading);
                break;
            case SensorType.ROTATION_VECTOR:
                if(readings == null){
                    Log.e(TAG,"no rotation vector in service message");
                    break;
                }
                onRotationData(ts,serviceName, deviceIndex,readings);
                break;
            case SensorType.STEP_COUNTER:
                if(!hasReading){
                    Log.e(TAG, "no step count in service message");
                    break;
                }
                onStepCounterData(ts,serviceName, deviceIndex,reading);
                break;
            case SensorType.STEP_DETECTOR:
                onStepDetected(ts, serviceName, deviceIndex);
                break;
            case SensorType.TEMPERATURE:
                if(!hasReading){
                    Log.e(TAG, "no temperature reading in service message");
                    break;
                }
                onTemperatureData(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.SKIN_TEMPERATURE:
                if(!hasReading){
                    Log.e(TAG, "no skin temperature reading in service message");
                    break;
                }
                onSkinTemperature(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.UV_INDEX_LEVEL:
                if(!hasReading){
                    Log.e(TAG,"no uv reading in service message");
                    break;
                }
                onUVLevel(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.SKIN_RESISTANCE:
                if(!hasReading){
                    Log.e(TAG,"no skin resistance in service message");
                    break;
                }
                onSkinResistanceData(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.CALORIES:
                if(!hasReading){
                    Log.e(TAG,"no calories in service message");
                    break;
                }
                onCaloryData(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.FLOORS_ASCENDED:
                if(!hasReading){
                    Log.e(TAG, "no floor count in service message");
                    break;
                }
                onFloorsAscendedData(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.FLOORS_DESCENDED:
                if(!hasReading){
                    Log.e(TAG, "missing data for sensor "+sensorType);
                    break;
                }
                onFloorsDescendedData(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.ASCENT_RATE:
                if(!hasReading){
                    Log.e(TAG, "missing data for sensor "+sensorType);
                    break;
                }
                onAscentRateData(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.ELEVATION_GAIN:
                if(!hasReading){
                    Log.e(TAG, "missing data for sensor "+sensorType);
                    break;
                }
                onElevationGainData(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.ELEVATION_LOSS:
                if(!hasReading){
                    Log.e(TAG, "missing data for sensor "+sensorType);
                    break;
                }
                onElevationLossData(ts,serviceName, deviceIndex,reading);
                break;
            case SensorType.STEPS_ASCENDED:
                if(!hasReading){
                    Log.e(TAG, "missing data for sensor "+sensorType);
                    break;
                }
                onStepsAscendedData(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.STEPS_DESCENDED:
                if(!hasReading){
                    Log.e(TAG, "missing data for sensor "+sensorType);
                    break;
                }
                onStepsDescendedData(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.STEPPING_ELEVATION_GAIN:
                if(!hasReading){
                    Log.e(TAG, "missing data for sensor "+sensorType);
                    break;
                }
                onSteppingElevationGainData(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.STEPPING_ELEVATION_LOSS:
                if(!hasReading){
                    Log.e(TAG, "missing data for sensor "+sensorType);
                    break;
                }
                onSteppingElevationLossData(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.PACE:
                if(!hasReading){
                    Log.e(TAG, "missing data for sensor "+sensorType);
                    break;
                }
                onPaceData(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.SPEED:
                if(!hasReading){
                    Log.e(TAG, "missing data for sensor "+sensorType);
                    break;
                }
                onSpeedData(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.DISTANCE:
                if(!hasReading){
                    Log.e(TAG, "missing data for sensor "+sensorType);
                    break;
                }
                onDistanceData(ts, serviceName, deviceIndex, reading);
                break;
            case SensorType.COMPASS:
                if(!hasReading){
                    Log.e(TAG, "missing data for sensor "+sensorType);
                    break;
                }
                onCompassData(ts,serviceName, deviceIndex,  reading);
                break;
            case  SensorType.ECG:
                if(!hasReading){
                    Log.e(TAG, "missing data for sensort" + sensorType);
                    break;
                }
                onECGData(ts, serviceName, deviceIndex, reading);
            default:
                onSensorData(sensorType,serviceName, deviceIndex, values);
        }

    }




    /**
     * Invoked when the connected device sent a list of supported sensors.
     * @param supportedSensors A list of {@link SensorType} supported by the device.
     */
    protected abstract void onSensorList(ArrayList<Integer> supportedSensors);


    /**
     * Invoked when the connected device sent a ECG data reading
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * //TODO: ecg data readings
     * @param reading The values of the ecg data.
     */
    private void onECGData(long timestamp, String serviceName, int deviceIndex, float reading) {
    }
    /**
     * Invoked when the connected device sent an accelerometer reading
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param values The values of the acceleration on the axes of the device; in m/s². The number of axes depends on the sensor/device.
     */
    protected void onAccelerometerData(long timestamp,String serviceName, int deviceIndex, float[] values){}

    /**
     * Invoked when the connected device sent a gravity reading
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param values A vector indicating the direction and magnitude of gravity; in m/s²
     */
    protected void onGravityData(long timestamp, String serviceName, int deviceIndex, float[] values){}

    /**
     * Invoked when the connected device sent a gyroscope reading
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param values The value of the rotation on the axes of the device; in radians/second. The number of axes depends on the sensor/device.
     */
    protected void onGyroscopeData(long timestamp,String serviceName, int deviceIndex, float[] values){}

    /**
     * Invoked when the connected device sends a heart rate reading
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param heartRate The heart rate in beats per minute
     */
    protected void onHeartRateData(long timestamp,String serviceName, int deviceIndex, float heartRate){}

    /**
     * Invoked when the connected device sends a heart rate variability reading
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param rrVariability The heart rate in beats per minute
     */
    protected void onHeartRateVariabilityData(long timestamp,String serviceName, int deviceIndex, float rrVariability){}

    /**
     * Invoked when the connected device sent a measurement of the magnetic field
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param magneticFieldReading The ambient magnetic field measured in the different axes in µT
     */
    protected void onMagneticFieldData(long timestamp,String serviceName, int deviceIndex, float[] magneticFieldReading){}

    /**
     * Invoked when the connected device sent a linear acceleration reading
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param linearAcceleration A vector indicating acceleration along each axis, not including gravity in m/s²
     */
    protected void onLinearAccelerationData(long timestamp,String serviceName, int deviceIndex, float[] linearAcceleration){}

    /**
     * Invoked when the connected device sent a pressure reading
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param pressure The atmospheric pressure in hPa
     */
    protected void onPressureData(long timestamp,String serviceName, int deviceIndex, float pressure){}

    /**
     * Invoked when the connected device sent a proximity sensor reading.
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param distance Sensor distance in cm
     */
    protected void onProximityData(long timestamp,String serviceName, int deviceIndex, float distance){}

    /**
     * Invoked when the connected device sent a air humidity measurement
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param relativeHumidity Relative ambient air humidity in percent
     */
    protected void onRelativeHumidityData(long timestamp,String serviceName, int deviceIndex, float relativeHumidity){}

    /**
     *
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param rotationVector Orientation of the device as a combination of angle and axis
     */
    protected void onRotationData(long timestamp,String serviceName, int deviceIndex, float[] rotationVector){}

    /**
     *
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param stepCount The number of steps taken since the sensor started measuring.
     */
    protected void onStepCounterData(long timestamp,String serviceName, int deviceIndex, float stepCount){}

    /**
     *
     * @param timestamp The timestamp of when the step was detected; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     */
    protected void onStepDetected(long timestamp, String serviceName, int deviceIndex){}

    /**
     *
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param temperature Ambient temperature in degree Celsius
     */
    protected void onTemperatureData(long timestamp,String serviceName, int deviceIndex, float temperature){}

    /**
     * Invoked when the connected device sent an ambient light reading
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param lightLevel The ambient light level in lux
     */
    protected void onAmbientLightLevel(long timestamp,String serviceName, int deviceIndex, float lightLevel){}

    /**
     *
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param temperature The skin temperature of the wearer measured by the device in degree Celsius
     */
    protected void onSkinTemperature(long timestamp, String serviceName, int deviceIndex, float temperature){}

    /**
     * The UV level is expressed in steps:
     * 0: Low
     * 1: Moderate
     * 2: High
     * 3: Very High
     * 4: Extreme
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param uvLevel Level of UV radiation .
     * @see <a href="https://en.wikipedia.org/wiki/Ultraviolet_index">Wikipedia Article</a>
     */
    protected void onUVLevel(long timestamp,String serviceName, int deviceIndex, float uvLevel){}

    /**
     *
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param resistance Galvanic skin response measured in kiloohm
     */
    protected void onSkinResistanceData(long timestamp,String serviceName, int deviceIndex, float resistance){}

    /**
     * Usually measured since the device was last reset.
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param calories Number of kilocalories burned.
     */
    protected void onCaloryData(long timestamp,String serviceName, int deviceIndex, float calories){}

    /**
     * Usually measured since the device was last reset
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param floors Number of floors ascended.
     */
    protected void onFloorsAscendedData(long timestamp,String serviceName, int deviceIndex, float floors){}

    /**
     * Usually measured since the device was last reset
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param floors Number of floors descended.
     */
    protected void onFloorsDescendedData(long timestamp,String serviceName, int deviceIndex, float floors){}

    /**
     *
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param rate Current rate of ascent/descent in cm/s
     */
    protected void onAscentRateData(long timestamp,String serviceName, int deviceIndex, float rate){}

    /**
     * Usually measured since the device was last reset
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param gain Total elevation gain in centimeters.
     */
    protected void onElevationGainData(long timestamp,String serviceName, int deviceIndex, float gain){}

    /**
     * Usually measured since the device was last reset
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param loss Total elevation loss in centimeters
     */
    protected void onElevationLossData(long timestamp,String serviceName, int deviceIndex, float loss){}

    /**
     * Usually measured since the device was last reset
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param steps Total number of steps ascended
     */
    protected void onStepsAscendedData(long timestamp,String serviceName, int deviceIndex, float steps){}

    /**
     * Usually measured since the device was last reset
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param steps Total number of steps descended
     */
    protected void onStepsDescendedData(long timestamp,String serviceName, int deviceIndex, float steps){}

    /**
     * Usually measured since the device was last reset
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param gain Elevation gain in centimeters by taking steps
     */
    protected void onSteppingElevationGainData(long timestamp,String serviceName, int deviceIndex, float gain){}

    /**
     * Usually measured since the device was last reset
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param loss Elevation loss in centimeters by taking steps
     */
    protected void onSteppingElevationLossData(long timestamp,String serviceName, int deviceIndex, float loss){}

    /**
     *
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param pace Current pace of the device in ms/m
     */
    protected void onPaceData(long timestamp,String serviceName, int deviceIndex, float pace){}

    /**
     *
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param speed Current speed of the device in cm/s
     */
    protected void onSpeedData(long timestamp,String serviceName, int deviceIndex, float speed){}

    /**
     * Usually measured since the device was last reset
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param distance Distance traveled in cm
     */
    protected void onDistanceData(long timestamp,String serviceName, int deviceIndex, float distance){}

    /**
     *
     * @param timestamp The timestamp of when the measurement was taken; in nanoseconds
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param angle The angle relative to North
     */
    protected void onCompassData(long timestamp,String serviceName, int deviceIndex, float angle){}
    /**
     * Invoked when the connection encounters an unknown sensor type.
     * This can be used to receive custom data from your own plugin.
     * @param sensorType The sensor type submitted with this reading
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     * @param values A {@link Bundle} containing data sent by the plugin.
     */
    protected void onSensorData(int sensorType, String serviceName, int deviceIndex, Bundle values){}

    /**
     * Invoked when the (bluetooth) device connected.
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     */
    protected abstract void onDeviceConnected(String serviceName, int deviceIndex);

    /**
     * Invoked when the connection to the (bluetooth) device disconnected.
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     */
    protected abstract void onDeviceDisconnected(String serviceName, int deviceIndex);

    /**
     * Invoked when the plugin service disconnects.
     */
    protected abstract void onServiceDisconnected(Exception e);
}

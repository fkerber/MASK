package de.frederickerber.maskplugin;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;

import de.frederickerber.maskcommons.BundleKeys;
import de.frederickerber.maskcommons.ErrorCode;
import de.frederickerber.maskcommons.SensorType;
import de.frederickerber.maskcommons.ServiceMsg;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class SensorService extends Service {

    private static final String TAG = SensorService.class.getName();
    protected String serviceName;

    final Messenger mMessenger = new Messenger(new IncomingHandler(this));
    protected ArrayList<Messenger> mClients = new ArrayList<>();
    protected Map<String, SparseArray<List<Messenger>>> mSubscribers = new HashMap<>();
    protected Map<Messenger, Boolean> mShouldReconnects = new HashMap<>();
    protected Map<Messenger, Map<String, int[]>> mFrequencies = new HashMap<>();
    protected Map<String, int[]> mMaxFrequencies = new HashMap<>();
    //contains timestamps of last sensor reading sent to client
    protected Map<Messenger, Map<String, long[]>> mLastReadingSent = new HashMap<>();
    //contains client's desired delay in nanoseconds
    protected Map<Messenger, Map<String, long[]>> mDelay = new HashMap<>();
    protected List<String> mConnectedDevices = new ArrayList<>();

    private Bundle sensorList;

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        //client should have unregistered at this point.
        return false; //do not allow rebinding
    }

    /**
     * {@inheritDoc}
     * You should use this method to check the device's sensor capabilities
     * and create a list of supported sensors for {@link #getSupportedSensors(String)}.
     */
    @Override
    public abstract void onCreate();

    /**
     * Send a list of supported sensors to a client.
     *
     * @param client      The client to send the list to.
     * @param deviceIndex The index of the device.
     */
    public void sendSensorList(Messenger client, int deviceIndex) {
        if (sensorList == null) {

            ArrayList<Integer> suppSens = getSupportedSensors(mConnectedDevices.get(deviceIndex));

            if (suppSens != null) {
                sensorList = new Bundle();
                sensorList.putString(BundleKeys.SERVICE_NAME, serviceName);
                sensorList.putIntegerArrayList(BundleKeys.SUPPORTED_SENSORS, suppSens);
            } else {
                Log.d(TAG, "Could not get supported sensors (yet?)");
                return;
            }
        }
        Message msg = Message.obtain(null, ServiceMsg.LIST_SENSORS);
        msg.setData(sensorList);
        try {
            client.send(msg);
        } catch (RemoteException e) {
            removeClient(client);
        }
    }

    /**
     * Send a data bundle to subscribers of a certain sensor.
     * You may use this method for custom sensors not covered by the broadcastXReading methods.
     *
     * @param deviceIdentifier The identifier of the device.
     * @param sensorType       The {@link SensorType} of the reading.
     * @param data             The sensor data to broadcast in a key-value bundle.
     *                         If possible, use <code>BundleKeys.TIMESTAMP_NANO</code> for the timestamp key and
     *                         <code>BundleKeys.SENSOR_READINGS</code> for the measured values.
     */

    public void sendDataToSubscribers(String deviceIdentifier, int sensorType, Bundle data, long timestamp) {

        if (mSubscribers.get(deviceIdentifier) == null) {
            return;
        } else {
            List<Messenger> subscribers = mSubscribers.get(deviceIdentifier).get(sensorType);
            Log.d(TAG, "sendDataToSubscribers: subscribers: " + Arrays.toString(subscribers.toArray()));
            if (subscribers == null || subscribers.isEmpty()) {
                Log.v(TAG, "tried to broadcast sensor data without subscribers for sensor " + sensorType);
                allSubscriptionsEnded(mConnectedDevices.indexOf(deviceIdentifier), sensorType);
                return;
            }
            Log.v(TAG, "broadcasting sensor data for sensor " + sensorType);
            //looping backwards so we can remove elements while iterating
            for (int i = subscribers.size() - 1; i >= 0; i--) {
                Messenger c = subscribers.get(i);
                boolean send = true;
                //check client's preferred frequency
                if (mFrequencies.get(c) != null && mFrequencies.get(c).get(deviceIdentifier) != null && mFrequencies.get(c).get(deviceIdentifier)[sensorType] > 0) {
                    if (mLastReadingSent.get(c) != null && mLastReadingSent.get(c).get(deviceIdentifier) != null && mLastReadingSent.get(c).get(deviceIdentifier)[sensorType] > 0) {
                        long desiredDelay = mDelay.get(c).get(deviceIdentifier)[sensorType] / 1000000;
                        Log.v(TAG, String.format("desired delay for sensor %d is %d ms", sensorType, desiredDelay));
                        long lastSent = mLastReadingSent.get(c).get(deviceIdentifier)[sensorType];
                        long delay = (timestamp - lastSent) / 1000000;
                        boolean multiple = false;
                        if (delay != 0) {
                            multiple = desiredDelay % delay == 0;
                        }
                        if (multiple && delay < desiredDelay) {
                            send = false;
                            Log.v(TAG, String.format("dropping data because delay is only %d ms", delay));
                        }
                    }
                }
                if (send) {
                    Message msg = Message.obtain(null, ServiceMsg.SENSOR_DATA, sensorType, -1);
                    if (data != null) {
                        msg.setData(data);
                    }
                    try {
                        c.send(msg);
                        if (mLastReadingSent.get(c) == null) {
                            mLastReadingSent.put(c, new HashMap<String, long[]>());
                        }
                        if (mLastReadingSent.get(c).get(deviceIdentifier) != null) {
                            Log.v(TAG, String.format("sending data for sensor %d with delay %d ms", sensorType, (timestamp - mLastReadingSent.get(c).get(deviceIdentifier)[sensorType]) / 1000000));
                            mLastReadingSent.get(c).get(deviceIdentifier)[sensorType] = timestamp;
                        } else {
                            mLastReadingSent.get(c).put(deviceIdentifier, new long[SensorType.NUM_SENSORS + getNumCustomSensors()]);
                            mLastReadingSent.get(c).get(deviceIdentifier)[sensorType] = timestamp;
                        }
                    } catch (RemoteException e) {
                        Log.e(TAG, "Error in sendDataToSubscribers: " + e.getMessage());
                        mClients.remove(c);
                        subscribers.remove(i);
                        //we don't remove the client from other subscriber lists here
                        //it will happen through this method eventually or by the last client being removed
                    }
                }
            }
        }
        if (mClients.isEmpty()) {
            lastClientDisconnected();
        }
    }

    /**
     * Broadcast a single float measurement to subscribers of {@code sensorType}
     *
     * @param timestamp   The time the measurement was taken, in nanoseconds
     * @param sensorType  The type of sensor
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param value       The value measured
     */
    public void broadcastSingleFloat(long timestamp, int sensorType, String serviceName, int deviceIndex, float value) {
        Log.d(TAG, "mSubscriber " + mSubscribers);
        Log.d(TAG, "sensortype " + sensorType);

        if (mConnectedDevices.get(deviceIndex) == null) {
            return;
        }
        Bundle data = new Bundle();
        data.putLong(BundleKeys.TIMESTAMP_NANO, timestamp);
        data.putFloat(BundleKeys.SENSOR_READINGS_SINGLE_FLOAT, value);
        data.putString(BundleKeys.SERVICE_NAME, serviceName);
        data.putInt(BundleKeys.DEVICE_INDEX, deviceIndex);

        sendDataToSubscribers(mConnectedDevices.get(deviceIndex), sensorType, data, timestamp);
    }


    /**
     * Broadcast a float array representing a multi-dimensional measurement to subscribers of {@code sensorType}
     *
     * @param timestamp   The time the measurement was taken, in nanoseconds
     * @param sensorType  The type of sensor
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param values      The values measured
     */
    public void broadcastFloatArray(long timestamp, int sensorType, String serviceName, int deviceIndex, float[] values) {
        Log.d(TAG, "mSubscribers = :" + mSubscribers);
        Log.d(TAG, "deviceIndex = :" + deviceIndex);

        if (mConnectedDevices.get(deviceIndex) == null) {
            return;
        }
        Log.d(TAG, "broadcastFloatArray:");
        Bundle data = new Bundle();
        data.putLong(BundleKeys.TIMESTAMP_NANO, timestamp);
        data.putFloatArray(BundleKeys.SENSOR_READINGS_FLOAT_ARRAY, values);
        data.putString(BundleKeys.SERVICE_NAME, serviceName);
        data.putInt(BundleKeys.DEVICE_INDEX, deviceIndex);

        sendDataToSubscribers(mConnectedDevices.get(deviceIndex), sensorType, data, timestamp);
    }

    /**
     * Broadcast a skin temperature measurement to subscribers
     *
     * @param timestamp   The time the measurement was taken, in nanoseconds
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param value       The skin temperature measurement in degrees celsius
     */
    public void broadcastSkinTemperatureReading(long timestamp, String serviceName, int deviceIndex, float value) {
        broadcastSingleFloat(timestamp, SensorType.SKIN_TEMPERATURE, serviceName, deviceIndex, value);
    }

    /**
     * Broadcast a skin resistance measurement to subscribers
     *
     * @param timestamp   The time the measurement was taken at, in nanoseconds
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param value       Galvanic skin response measured in kiloohm
     */
    public void broadcastSkinResistanceReading(long timestamp, String serviceName, int deviceIndex, float value) {
        broadcastSingleFloat(timestamp, SensorType.SKIN_RESISTANCE, serviceName, deviceIndex, value);
    }

    /**
     * Broadcast the UV index level,
     *
     * @param timestamp   The time the measurement was taken at, in nanoseconds
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param value       Level of UV radiation, one of:
     *                    0: Low
     *                    1: Moderate
     *                    2: High
     *                    3: Very High
     *                    4: Extreme
     * @see <a href="https://en.wikipedia.org/wiki/Ultraviolet_index">Wikipedia Article</a>
     */
    public void broadcastUVIndexLevel(long timestamp, String serviceName, int deviceIndex, float value) {
        broadcastSingleFloat(timestamp, SensorType.UV_INDEX_LEVEL, serviceName, deviceIndex, value);
    }

    /**
     * Broadcast a Accelerometer reading to subscribers
     *
     * @param timestamp   The time the measurement was taken at, in nanoseconds
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param values      The values of the acceleration on the axes of the device; in m/s².
     */
    public void broadcastAccelReading(long timestamp, String serviceName, int deviceIndex, float[] values) {
        Log.d(TAG, "broadcastAccelReading");
        if (values == null || values.length < 1) {
            throw new IllegalArgumentException("values provided are null or empty");
        }
        broadcastFloatArray(timestamp, SensorType.ACCELEROMETER, serviceName, deviceIndex, values);
    }

    /**
     * Broadcast a Gyroscope reading to subscribers
     *
     * @param timestamp   The time the measurement was taken at, in nanoseconds
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param values      The value of the rotation on the axes of the device; in radians/second.
     */
    public void broadcastGyroscopeReading(long timestamp, String serviceName, int deviceIndex, float[] values) {
        if (values == null || values.length < 1) {
            throw new IllegalArgumentException("values provided are null or empty");
        }
        broadcastFloatArray(timestamp, SensorType.GYROSCOPE, serviceName, deviceIndex, values);
    }

    /**
     * Broadcast a gravity reading to subscribers
     *
     * @param timestamp   The time the measurement was taken at, in nanoseconds
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param values      A vector indicating the direction and magnitude of gravity; in m/s²
     */
    public void broadcastGravityReading(long timestamp, String serviceName, int deviceIndex, float[] values) {
        if (values == null || values.length < 1) {
            throw new IllegalArgumentException("values provided are null or empty");
        }
        broadcastFloatArray(timestamp, SensorType.GRAVITY, serviceName, deviceIndex, values);
    }

    /**
     * Broadcast a measurement of linear acceleration to subscribers
     *
     * @param timestamp   The time the measurement was taken at, in nanoseconds
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param values      A vector indicating acceleration along each axis, not including gravity in m/s²
     */
    public void broadcastLinearAccelerationReading(long timestamp, String serviceName, int deviceIndex, float[] values) {
        if (values == null || values.length < 1) {
            throw new IllegalArgumentException("values provided are null or empty");
        }
        broadcastFloatArray(timestamp, SensorType.LINEAR_ACCELERATION, serviceName, deviceIndex, values);
    }

    /**
     * Broadcast an ambient light level reading to subscribers
     *
     * @param timestamp   The time the measurement was taken at, in nanoseconds
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param lightLevel  The ambient light level in lux
     */
    public void broadcastLightReading(long timestamp, String serviceName, int deviceIndex, float lightLevel) {
        broadcastSingleFloat(timestamp, SensorType.LIGHT, serviceName, deviceIndex, lightLevel);
    }

    /**
     * Broadcast the amount of calories burned to subscribers, usually measured
     * since the device was last reset
     *
     * @param timestamp   The time the measurement was taken at, in nanoseconds
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param calories    Number of kilocalories burned
     */
    public void broadcastCaloriesBurned(long timestamp, String serviceName, int deviceIndex, float calories) {
        broadcastSingleFloat(timestamp, SensorType.CALORIES, serviceName, deviceIndex, calories);
    }

    /**
     * Broadcast a reading of the ambient magnetic field in micro-Tesla to subscribers.
     *
     * @param timestamp   The timestamp of the reading in nanoseconds
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param values      The measured ambient magnetic field on the different axes in µT
     */
    public void broadcastMagneticFieldReading(long timestamp, String serviceName, int deviceIndex, float[] values) {
        if (values == null || values.length < 1) {
            throw new IllegalArgumentException("values provided are null or empty");
        }
        broadcastFloatArray(timestamp, SensorType.MAGNETIC_FIELD, serviceName, deviceIndex, values);
    }

    /**
     * Broadcast a rotation reading to subscribers
     *
     * @param timestamp   The time the measurement was taken at, in nanoseconds
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param values      The value of the rotation on the axes of the device; in radians/second.
     */
    public void broadcastRotationReading(long timestamp, String serviceName, int deviceIndex, float[] values) {
        if (values == null || values.length < 1) {
            throw new IllegalArgumentException("values provided are null or empty");
        }
        broadcastFloatArray(timestamp, SensorType.ROTATION_VECTOR, serviceName, deviceIndex, values);
    }

    /**
     * Broadcast an atmospheric pressure measurement to subscribers
     *
     * @param timestamp   The time the measurement was taken at, in nanoseconds
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param pressure    The atmospheric pressure in hPa
     */
    public void broadcastPressureReading(long timestamp, String serviceName, int deviceIndex, float pressure) {
        broadcastSingleFloat(timestamp, SensorType.PRESSURE, serviceName, deviceIndex, pressure);
    }

    /**
     * Broadcast a proximity sensor reading to subscribers
     *
     * @param timestamp   The time the measurement was taken at, in nanoseconds
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param distance    Sensor distance in cm
     */
    public void broadcastProximityReading(long timestamp, String serviceName, int deviceIndex, float distance) {
        broadcastSingleFloat(timestamp, SensorType.PROXIMITY, serviceName, deviceIndex, distance);
    }

    /**
     * Broadcast an air humidity measurement to subscribers
     *
     * @param timestamp           The time the measurement was taken at, in nanoseconds
     * @param serviceName         The name of service
     * @param deviceIndex         The index of device
     * @param humidity_percentage Relative ambient air humidity in percent
     */
    public void broadcastRelativeHumidityReading(long timestamp, String serviceName, int deviceIndex, float humidity_percentage) {
        broadcastSingleFloat(timestamp, SensorType.RELATIVE_HUMIDITY, serviceName, deviceIndex, humidity_percentage);
    }

    /**
     * Broadcast a measurement of the ambient temperature to subscribers
     *
     * @param timestamp   The time the measurement was taken at, in nanoseconds
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param temperature Ambient temperature in degree celsius
     */
    public void broadcastTemperatureReading(long timestamp, String serviceName, int deviceIndex, float temperature) {
        broadcastSingleFloat(timestamp, SensorType.TEMPERATURE, serviceName, deviceIndex, temperature);
    }

    /**
     * Broadcast that a step was taken.
     *
     * @param timestamp   The time in nanoseconds when the step was taken.
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     */
    public void broadcastStepDetected(long timestamp, String serviceName, int deviceIndex) {
        if (mConnectedDevices.get(deviceIndex) == null) {
            return;
        }
        Bundle data = new Bundle();
        data.putLong(BundleKeys.TIMESTAMP_NANO, timestamp);
        data.putString(BundleKeys.SERVICE_NAME, serviceName);
        data.putInt(BundleKeys.DEVICE_INDEX, deviceIndex);
        sendDataToSubscribers(mConnectedDevices.get(deviceIndex), SensorType.STEP_DETECTOR, data, timestamp);
    }


    /**
     * Broadcast the heart rate in beats per minute to subscribers.
     *
     * @param timestamp   The timestamp in nanoseconds of when the measurement was taken.
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param heart_rate  The heart rate in bpm.
     */
    public void broadcastHeartRateReading(long timestamp, String serviceName, int deviceIndex, float heart_rate) {
        Log.d(TAG, "broadcastHeartReading");
        broadcastSingleFloat(timestamp, SensorType.HEART_RATE, serviceName, deviceIndex, heart_rate);
    }


    public void broadcastHeartRateVariabilityReading(long timestamp, String serviceName, int deviceIndex, float rr_value) {
        broadcastSingleFloat(timestamp, SensorType.HEART_RATE_VARIABILITY, serviceName, deviceIndex, rr_value);
    }

    /**
     * Broadcast the number of steps taken since measurement started.
     *
     * @param timestamp   The timestamp in nanoseconds of when the measurement was taken.
     * @param serviceName The name of service
     * @param deviceIndex The index of device
     * @param step_count  The number of steps.
     */
    public void broadcastStepCounterReading(long timestamp, String serviceName, int deviceIndex, float step_count) {
        broadcastSingleFloat(timestamp, SensorType.STEP_COUNTER, serviceName, deviceIndex, step_count);
    }

    /**
     * Removes a client from the list of clients and ends all its subscriptions.
     * If that client was the last one, {@link #lastClientDisconnected()} is called.
     *
     * @param client The client to remove.
     */
    protected void removeClient(Messenger client) {
        Log.d(TAG, "Removing client: " + client);

        if (mClients != null && client != null) {
            mClients.remove(client);
            mFrequencies.remove(client);
            mDelay.remove(client);
            mShouldReconnects.remove(client);

            for (String connectedDevice : mConnectedDevices) {
                ArrayList<Integer> supportedSensors = getSupportedSensors(connectedDevice);
                if (supportedSensors != null) {
                    for (int sensorType : supportedSensors) {
                        Log.d(TAG, "removeClient: " + "Device: " + connectedDevice + "sensorType: " + sensorType);
                        unsubscribeFromSensor(connectedDevice, sensorType, client);
                    }
                }
            }
            if (mClients.isEmpty()) {
                lastClientDisconnected();
            }
        }
    }

    /**
     * Send an error message to a client.
     *
     * @param client    The client who should receive the error message.
     * @param errorCode A suitable error code. See {@link ErrorCode}
     * @param message   A human-readable message about the error.
     */
    public void sendErrorMessage(Messenger client, int errorCode, @Nullable String message) {
        try {
            if (message != null && !message.isEmpty()) {
                Message msg = Message.obtain(null, ServiceMsg.ERROR, errorCode, 0);
                msg.getData().putString("message", message);
                client.send(msg);
            } else {
                client.send(Message.obtain(null, ServiceMsg.ERROR, errorCode, 0));
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error in sendErrorMessage: " + e.getMessage());
            removeClient(client);
        }
    }

    /**
     * Check whether a sensor has any subscribers.
     *
     * @param sensorType The type of sensor
     * @return {@code true} if there are any clients subscribed to the sensor, {@code false} otherwise.
     */
    public boolean hasSubscribers(int sensorType) {
        for (SparseArray s : mSubscribers.values()) {
            if (s.get(sensorType) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inform all clients that the sensor device disconnected e.g. because the bluetooth connection
     * broke.
     *
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     */
    public void broadcastDeviceDisconnected(String serviceName, int deviceIndex) {
        Log.d(TAG, "broadcastdeviceDisconnected mConnectedDevices: " + mConnectedDevices.get(deviceIndex));

        for (int i = mClients.size() - 1; i >= 0; i--) {
            try {
                Message msg = Message.obtain(null, ServiceMsg.DEVICE_DISCONNECTED);
                msg.getData().putString(BundleKeys.SERVICE_NAME, serviceName);
                msg.getData().putInt(BundleKeys.DEVICE_INDEX, deviceIndex);

                mClients.get(i).send(msg);

            } catch (RemoteException e) {
                Log.e(TAG, "Error in broadcastDeviceDisconnected: " + e.getMessage());
                removeClient(mClients.get(i));
            }
        }
    }

    /**
     * Inform all clients that the sensor device connected, e.g. because bluetooth connectivity was restored.
     *
     * @param serviceName The name of the service.
     * @param deviceIndex The index of the device.
     */

    public void broadcastDeviceConnected(String serviceName, int deviceIndex) {

        mMaxFrequencies.put(mConnectedDevices.get(deviceIndex), new int[SensorType.NUM_SENSORS + getNumCustomSensors()]);
        Log.d(TAG, "broadcastdeviceconnected mConnectedDevices: " + mConnectedDevices.get(deviceIndex));


        for (int i = mClients.size() - 1; i >= 0; i--) {
            try {
                Message msg = Message.obtain(null, ServiceMsg.DEVICE_CONNECTED);
                msg.getData().putString(BundleKeys.SERVICE_NAME, serviceName);
                Log.d(TAG, "serviceName in broadcastDeviceConnected" + serviceName);
                msg.getData().putInt(BundleKeys.DEVICE_INDEX, deviceIndex);
                Log.d(TAG, "broadcastDeviceConnected send msg : " + msg.getData().getInt(BundleKeys.DEVICE_INDEX));
                mClients.get(i).send(msg);

            } catch (RemoteException e) {
                Log.e(TAG, "Error in broadcastDeviceConnected: " + e.getMessage());
                removeClient(mClients.get(i));
            }
        }
    }

    /**
     * Send the list of sensors to all clients.
     *
     * @param deviceIndex The index of the device.
     */
    public void broadCastSensorList(int deviceIndex) {
        for (int i = mClients.size() - 1; i >= 0; i--) {
            sendSensorList(mClients.get(i), deviceIndex);
        }
    }

    /**
     * Called when the first client registers with the service.
     * If necessary, you should connect to the sensor device here.
     */
    protected abstract void firstClientConnected(Messenger client);

    /**
     * Called when the last client disconnected.
     * This might happen shortly before onDestroy if the client also unbinds afterwards.
     */
    protected abstract void lastClientDisconnected();

    /**
     * Return an array listing the sensors supported by this device.
     * For efficiency, the first non-null list returned by this method is saved so
     * changing this list after initial population is not possible.
     *
     * @param deviceIdentifier The identifier of the device
     * @return An array of {@link SensorType} listing the supported sensors.
     */
    protected abstract ArrayList<Integer> getSupportedSensors(String deviceIdentifier);

    /**
     * This method is called whenever a new client registers with the service.
     * The list of supported sensors and device connectivity information is sent to the client here.
     * If you want to implement additional functionality, remember to call super(client) first.
     *
     * @param client The client who registered.
     */
    void sendGreetings(Messenger client) {
    }

    /**
     * Whether the sensor device is currently connected or not.
     * Used to inform clients about the device status upon connecting.
     *
     * @return {@code true} if the device is connected {@code false} otherwise.
     */
    protected abstract boolean isDeviceConnected();

    /**
     * Called when the first client subscribes to the sensor.
     * You probably want to activate/subscribe to the hardware sensor here.
     *
     * @param deviceIndex The index of the device we want to subscribed.
     * @param sensorType  The type of the sensor the client subscribed to.
     * @param client      The client that subscribed. Use this to send error messages etc.
     * @param frequency   The client's desired sensor rate in Hertz. If possible, keep the sensor at the maximum frequency the clients want.
     */
    protected abstract void newSensorSubscription(int deviceIndex, int sensorType, Messenger client, int frequency);

    /**
     * Called after the last client unsubscribed from a sensor.
     * You may want to deactivate/unsubscribe from the hardware here.
     *
     * @param deviceIndex The index of the device.
     * @param sensorType  The type of the sensor which no longer has any subscriptions.
     */
    protected abstract void allSubscriptionsEnded(int deviceIndex, int sensorType);

    /**
     * Add a client to the list of subscribers for the specified sensor.
     * Calls newSensorSubscription if the client is the first one for this sensor.
     * If the client is already subscribed to the sensor, it will not be added again.
     * However, the frequency will be updated so this can be used to change the desired frequency without
     * unsubscribing.
     *
     * @param deviceIdentifier The identifier of the device we want to subscribed.
     * @param sensorType       The sensor the client wants to subscribe to
     * @param frequency        The frequency the client would like to receive events at, in Hertz.
     * @param client           The client that wants to subscribe
     */
    void subscribeToSensor(String deviceIdentifier, int sensorType, int frequency, Messenger client) {
        if (getSupportedSensors(deviceIdentifier) == null) {
            sendErrorMessage(client, ErrorCode.SERVICE_NOT_READY, "Plugin failed to provide a list of supported sensors");
            return;
        }
        if (client == null) {
            throw new IllegalArgumentException("client may not be null");
        }
        if (getSupportedSensors(deviceIdentifier).contains(sensorType)) {
            if (frequency > 0) {
                setFrequency(deviceIdentifier, sensorType, frequency, client);
                setDesiredDelay(deviceIdentifier, sensorType, frequency, client);
                if (sensorType < mMaxFrequencies.get(deviceIdentifier).length) {
                    mMaxFrequencies.get(deviceIdentifier)[sensorType] = Math.max(frequency, mMaxFrequencies.get(deviceIdentifier)[sensorType]);
                }
                if (mLastReadingSent.get(client) == null) {
                    mLastReadingSent.put(client, new HashMap<String, long[]>());
                    if (mLastReadingSent.get(client).get(deviceIdentifier) == null) {
                        mLastReadingSent.get(client).put(deviceIdentifier, new long[SensorType.NUM_SENSORS + getNumCustomSensors()]);
                    }
                }
            }
            if (mSubscribers.get(deviceIdentifier) == null) {
                mSubscribers.put(deviceIdentifier, new SparseArray<List<Messenger>>());
                Log.d(TAG, "put deviceIdentifier and new Sparsearray = " + mSubscribers);

            }
            if (mSubscribers.get(deviceIdentifier).get(sensorType) == null) {
                mSubscribers.get(deviceIdentifier).put(sensorType, new ArrayList<Messenger>());
                mSubscribers.get(deviceIdentifier).get(sensorType).add(client);
                Log.d(TAG, "mSubscriber = " + mSubscribers);
                newSensorSubscription(mConnectedDevices.indexOf(deviceIdentifier), sensorType, client, frequency);
            } else {
                if (!mSubscribers.get(deviceIdentifier).get(sensorType).contains(client)) {
                    mSubscribers.get(deviceIdentifier).get(sensorType).add(client);
                    newSensorSubscription(mConnectedDevices.indexOf(deviceIdentifier), sensorType, client, frequency);
                }
            }
        } else {
            sendErrorMessage(client, ErrorCode.SENSOR_NOT_SUPPORTED, "unsupported sensor: " + sensorType);
        }
    }

    /**
     * Sets a client's desired delay between events in nanoseconds
     *
     * @param deviceIdentifier The identifier for the device.
     * @param sensorType       The sensor to set the delay for
     * @param frequency        The frequency the client wants to receive events at, in Hertz
     * @param client           The client subscribing to the sensor
     */
    void setDesiredDelay(String deviceIdentifier, int sensorType, int frequency, Messenger client) {
        if (frequency > 0) {
            long desiredDelay = 1000000000 / frequency;
            if (mDelay.get(client) == null) {
                mDelay.put(client, new HashMap<String, long[]>());
            }
            if (mDelay.get(client).get(deviceIdentifier) == null) {
                long[] clientDelayArray = new long[SensorType.NUM_SENSORS + getNumCustomSensors()];
                clientDelayArray[sensorType] = desiredDelay;
                mDelay.get(client).put(deviceIdentifier, clientDelayArray);
            } else {
                mDelay.get(client).get(deviceIdentifier)[sensorType] = desiredDelay;
            }
        }
    }

    /**
     * Set the preferred event frequency for a sensor and client
     *
     * @param deviceIdentifier The identifier of device.
     * @param sensorType       The type of sensor
     * @param frequency        The frequency to receive events at, in Hertz
     * @param client           The client subscribing to the sensor
     */
    void setFrequency(String deviceIdentifier, int sensorType, int frequency, Messenger client) {
        if (frequency > 0) {
            if (mFrequencies.get(client) == null) {
                mFrequencies.put(client, new HashMap<String, int[]>());
            }
            if (mFrequencies.get(client).get(deviceIdentifier) == null) {
                int[] clientFrequencyArray = new int[SensorType.NUM_SENSORS + getNumCustomSensors()];
                clientFrequencyArray[sensorType] = frequency;
                mFrequencies.get(client).put(deviceIdentifier, clientFrequencyArray);
            } else {
                mFrequencies.get(client).get(deviceIdentifier)[sensorType] = frequency;
            }
        }
    }

    /**
     * Specify how many custom sensors (not available in the {@link SensorType} class)
     * your device supports. This is important for saving sensor rates for clients.
     *
     * @return The number of custom sensors the device supports.
     */
    protected int getNumCustomSensors() {
        return 0;
    }

    /**
     * Get the maximum frequency desired by subscribers of the given sensor.
     *
     * @param deviceIdentifier The identifier of the device.
     * @param sensorType       The sensor type
     * @return The maximum frequency in Hertz if there are frequency preferences for the sensor, 0 otherwise
     */
    protected int getMaxFrequency(String deviceIdentifier, int sensorType) {
        if (sensorType >= 0 && sensorType < mMaxFrequencies.get(deviceIdentifier).length) {
            return mMaxFrequencies.get(deviceIdentifier)[sensorType];
        }
        return 0;
    }

    /**
     * Get a subscriber's preferred event frequency for a given sensor type.
     *
     * @param client           The subscriber to the sensor
     * @param deviceIdentifier The identifier of the device.
     * @param sensorType       The type of sensor
     * @return The client's preferred frequency in Hertz if specified. A value of 0 indicates no preference.
     */
    protected int getFrequency(Messenger client, String deviceIdentifier, int sensorType) {
        if (mFrequencies.containsKey(client) && mFrequencies.get(client).containsKey(deviceIdentifier)) {
            int[] frequencies = mFrequencies.get(client).get(deviceIdentifier);
            if (frequencies != null && sensorType >= 0 && sensorType < frequencies.length) {
                return frequencies[sensorType];
            }
        }
        return 0;
    }

    /**
     * Recalculates the maximum frequency desired by subscribers of the specified sensor type.
     * Calls {@link #sensorRateDecreased(String, int)} for the sensor if the new maximum is lower.
     *
     * @param deviceIdentifier The identifier of the device.
     * @param sensorType       The sensor to recalculate the maximum frequency for.
     */
    void recalculateMaxFrequencies(String deviceIdentifier, int sensorType) {
        if (sensorType < 0 || sensorType > mMaxFrequencies.get(deviceIdentifier).length) {
            return;
        }
        int old = mMaxFrequencies.get(deviceIdentifier)[sensorType];
        int max = 0;
        for (Messenger c : mFrequencies.keySet()) {
            if (mFrequencies.get(c).get(deviceIdentifier) != null) {
                max = Math.max(max, mFrequencies.get(c).get(deviceIdentifier)[sensorType]);
            }
        }
        mMaxFrequencies.get(deviceIdentifier)[sensorType] = max;
        if (old > max) {
            sensorRateDecreased(deviceIdentifier, sensorType);
        }
    }

    /**
     * Remove a client from the list of subscribers for the specified sensor.
     * Calls allSubscriptionsEnded if the client was the last subscriber.
     *
     * @param deviceIdentifier The identifier of the device.
     * @param sensorType       The sensor the client subscribed to.
     * @param client           The subscriber.
     */
    void unsubscribeFromSensor(String deviceIdentifier, int sensorType, Messenger client) {
        if (mSubscribers.get(deviceIdentifier) != null && mSubscribers.get(deviceIdentifier).get(sensorType) != null && !mSubscribers.get(deviceIdentifier).get(sensorType).isEmpty()) {
            if (mSubscribers.get(deviceIdentifier).get(sensorType).contains(client)) {
                mSubscribers.get(deviceIdentifier).get(sensorType).remove(client);
                if (mFrequencies.get(client) != null && mFrequencies.get(client).get(deviceIdentifier) != null) {
                    mFrequencies.get(client).get(deviceIdentifier)[sensorType] = -1;
                    recalculateMaxFrequencies(deviceIdentifier, sensorType);
                }
                if (mDelay.get(client) != null && mDelay.get(client).get(deviceIdentifier) != null) {
                    mDelay.get(client).get(deviceIdentifier)[sensorType] = -1;
                }
                if (mSubscribers.get(deviceIdentifier).get(sensorType).isEmpty()) {

                    allSubscriptionsEnded(mConnectedDevices.indexOf(deviceIdentifier), sensorType);
                }
            }
        }
    }

    void updateAutomaticReconnectAttempt(Messenger client, int shouldReconnect) {
        Log.d(TAG, "Automatic Reconnect Attempt updated");
        mShouldReconnects.put(client, shouldReconnect != 0);
    }


    /**
     * This method is called when the rate desired by a sensors subscribers decreased because
     * a subscription stopped. Try to reduce the sensor rate here to reduce power consumption.
     *
     * @param deviceIdentifier The identifier of the device.
     * @param sensorType       The type of sensor
     */
    protected abstract void sensorRateDecreased(String deviceIdentifier, int sensorType);

    /**
     * Register a new client with the service. Attempts to send a list of supported sensors
     * and a 'device connected' event if the device is already connected.
     * The client will receive device connectivity events until it unregisters.
     * This is required before other things such as sensor subscription work for the client.
     *
     * @param client The client to register
     */
    protected void registerClient(Messenger client) {
        mClients.add(client);
        Log.d(TAG, "register client: " + client);
        if (mClients.size() == 1) {
            firstClientConnected(client);
        }
        sendGreetings(client);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract void onDestroy();

    private static class IncomingHandler extends Handler {

        private final WeakReference<SensorService> mService;

        IncomingHandler(SensorService instance) {
            mService = new WeakReference<>(instance);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage: msg: " + msg.what);
            SensorService s = mService.get();
            if (s != null) {
                if (msg.what == ServiceMsg.REGISTER_CLIENT) {
                    Log.d(TAG, "Register client msg received");
                    s.registerClient(msg.replyTo);
                } else {
                    if (!s.mClients.contains(msg.replyTo)) {
                        Log.d(TAG, "received message from unregistered client");
                        s.sendErrorMessage(msg.replyTo, ErrorCode.CLIENT_NOT_REGISTERED, "You need to register first");
                    } else {
                        switch (msg.what) {
                            case ServiceMsg.UNREGISTER_CLIENT:
                                Log.d(TAG, "Unregister client msg received");
                                if (msg.replyTo != null) {
                                    s.removeClient(msg.replyTo);
                                }
                                break;
                            case ServiceMsg.LIST_SENSORS:
                                Log.d(TAG, "List sensors msg received");
                                s.sendSensorList(msg.replyTo, msg.getData().getInt(BundleKeys.DEVICE_INDEX));
                                break;
                            case ServiceMsg.SUBSCRIBE_TO_SENSOR:
                                Log.d(TAG, "sensor sub msg received");
                                s.subscribeToSensor(s.mConnectedDevices.get(msg.getData().getInt(BundleKeys.DEVICE_INDEX)), msg.arg1, msg.arg2, msg.replyTo);
                                break;
                            case ServiceMsg.UNSUBSCRIBE_FROM_SENSOR:
                                Log.d(TAG, "sensor unsub msg received");
                                s.unsubscribeFromSensor(s.mConnectedDevices.get(msg.getData().getInt(BundleKeys.DEVICE_INDEX)), msg.arg1, msg.replyTo);
                                break;
                            case ServiceMsg.ATTEMPT_AUTOMATIC_RECONNECT:
                                Log.d(TAG, "automatic reconnect msg received");
                                s.updateAutomaticReconnectAttempt(msg.replyTo, msg.arg1);
                                break;
                            default:
                                Log.w(TAG, "received unknown message: " + msg.what);
                                s.sendErrorMessage(msg.replyTo, ErrorCode.UNKNOWN_MESSAGE, "unknown message type: " + msg.what);
                        }
                    }
                }
            }
        }
    }
}

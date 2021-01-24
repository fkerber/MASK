package de.frederickerber.maskcommons;


import android.hardware.Sensor;
import android.os.Build;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * This class contains integer codes for the various sensors supported.
 * There are also some methods to convert these types.
 */
public final class SensorType {
    public final static int ACCELEROMETER = 0;
    public final static int GYROSCOPE = 1;
    public final static int LIGHT = 2;
    public final static int LINEAR_ACCELERATION = 3;
    public final static int MAGNETIC_FIELD = 4;
    public final static int PRESSURE = 5;
    public final static int PROXIMITY = 6;
    public final static int RELATIVE_HUMIDITY = 7;
    public final static int ROTATION_VECTOR = 8;
    public final static int TEMPERATURE = 9;
    public final static int HEART_RATE = 10;
    public final static int HEART_RATE_VARIABILITY = 11;
    public final static int STEP_COUNTER = 12;
    public final static int GRAVITY = 13;
    public final static int STEP_DETECTOR = 14;
    public final static int SKIN_TEMPERATURE = 15;
    public final static int UV_INDEX_LEVEL = 16;
    public final static int SKIN_RESISTANCE = 17;
    public final static int CALORIES = 18;
    public final static int FLOORS_ASCENDED = 19;
    public final static int FLOORS_DESCENDED = 20;
    public final static int ASCENT_RATE = 21;
    public final static int ELEVATION_GAIN = 22;
    public final static int ELEVATION_LOSS = 23;
    public final static int STEPS_ASCENDED = 24;
    public final static int STEPS_DESCENDED = 25;
    public final static int STEPPING_ELEVATION_GAIN = 26;
    public final static int STEPPING_ELEVATION_LOSS = 27;
    public final static int PACE = 28;
    public final static int SPEED = 29;
    public final static int DISTANCE = 30;
    public final static int COMPASS = 31;
    public final static int ECG = 32;

    public final static int NUM_SENSORS = 33;

    //if you add a sensor, increase the value of NUM_SENSORS!!

    /**
     * Converts a sensor type integer to a byte array. Used by the Android Wear plugin..
     *
     * @param sensorType The sensor type to convert.
     * @return An array of bytes encoding the integer provided.
     */
    public static byte[] toByteArray(int sensorType) {
        int bytesInInteger = 4;
        /*if(Build.VERSION.SDK_INT >= 24){
            bytesInInteger = Integer.BYTES; // requires Java 8
        }*/
        return ByteBuffer.allocate(bytesInInteger).putInt(sensorType).array();
    }

    /**
     * The first float of the returned array is the sensor type
     *
     * @param sensorData A byte array containing a sensor type and measurements
     * @return A float array with the sensor type in position 0 and the measurements from position 1 if available
     */
    public static float[] fromSensorData(byte[] sensorData) {
        int bytesInLong = 8;
        /*if(Build.VERSION.SDK_INT >= 24){
            bytesInLong = Long.BYTES; //requires Java 8
        }*/
        int bytesInFloat = 4;
        /*if(Build.VERSION.SDK_INT >= 24){
            bytesInFloat = Float.BYTES; // requires Java 8
        }*/
        int nFloats = (sensorData.length - bytesInLong) / bytesInFloat;
        float[] res = new float[nFloats];
        ByteBuffer bb = ByteBuffer.wrap(sensorData);
        bb.getLong(); //throw away timestamp
        for (int i = 0; i < nFloats; i++) {
            //get sensor type and values
            res[i] = bb.getFloat();
        }
        return res;
    }

    /**
     * Encode a sensor reading in an array of bytes.
     *
     * @param sensorType The sensor type.
     * @param timestamp  The timestamp of the reading in nanoseconds.
     * @param sensorData A float array containing the sensor readings.
     * @return A byte array with the timestamp, sensor type and sensor data encoded in that order.
     */
    public static byte[] toByteArray(int sensorType, long timestamp, float[] sensorData) {
        int bytesInFloat = 4;
        /*if(Build.VERSION.SDK_INT >= 24){
            bytesInFloat = Float.BYTES; // requires Java 8
        }*/
        int bytesInLong = 8;
        /*if(Build.VERSION.SDK_INT >= 24){
            bytesInLong = Long.BYTES; // requires Java 8
        }*/
        ByteBuffer bb = ByteBuffer.allocate((sensorData.length + 1) * bytesInFloat + bytesInLong);
        bb.putLong(timestamp);
        bb.putFloat(sensorType);
        for (float f : sensorData) {
            bb.putFloat(f);
        }
        return bb.array();
    }

    /**
     * Encodes a list of integers/sensor types to a byte array.
     *
     * @param sensorTypeList A list of sensor types.
     * @return A byte array containing the sensor types.
     */
    public static byte[] toByteArray(List<Integer> sensorTypeList) {
        int bytesInInteger = 4;
        /*if(Build.VERSION.SDK_INT >= 24){
            bytesInInteger = Integer.BYTES; // requires Java 8
        }*/
        ByteBuffer bb = ByteBuffer.allocate(sensorTypeList.size() * bytesInInteger);
        for (Integer i : sensorTypeList) {
            bb.putInt(i);
        }
        return bb.array();
    }

    /**
     * Reads an integer from a byte array.
     *
     * @param byteArray A byte array containing at least one integer
     * @return The first bytes of the array interpreted as an integer.
     */
    public static int intFromByteArray(byte[] byteArray) {
        return ByteBuffer.wrap(byteArray).asIntBuffer().get();
    }

    /**
     * Converts a byte array to an integer list.
     *
     * @param byteArray A byte array containing at least one integer.     *
     * @return The byte array interpreted as a list of integers.
     */
    public static ArrayList<Integer> listFromByteArray(byte[] byteArray) {
        IntBuffer ib = ByteBuffer.wrap(byteArray).asIntBuffer();
        ArrayList<Integer> sensorTypes = new ArrayList<>();
        for (int i = 0; i < ib.capacity(); i++) {
            sensorTypes.add(ib.get(i));
        }
        return sensorTypes;
    }

    /**
     * Converts an Android {@link Sensor} type to a {@link SensorType} used by Mask.
     *
     * @param sensor An Android Sensor.
     * @return The equivalent sensor type used by Mask or -1 if it does not exist.
     */
    public static int fromAndroidSensor(int sensor) {
        switch (sensor) {
            case Sensor.TYPE_ACCELEROMETER:
                return ACCELEROMETER;
            case Sensor.TYPE_GYROSCOPE:
                return GYROSCOPE;
            case Sensor.TYPE_LIGHT:
                return LIGHT;
            case Sensor.TYPE_GRAVITY:
                return GRAVITY;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                return TEMPERATURE;
            case Sensor.TYPE_HEART_RATE:
                return HEART_RATE;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                return LINEAR_ACCELERATION;
            case Sensor.TYPE_MAGNETIC_FIELD:
                return MAGNETIC_FIELD;
            case Sensor.TYPE_PRESSURE:
                return PRESSURE;
            case Sensor.TYPE_PROXIMITY:
                return PROXIMITY;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                return RELATIVE_HUMIDITY;
            case Sensor.TYPE_ROTATION_VECTOR:
                return ROTATION_VECTOR;
            case Sensor.TYPE_STEP_COUNTER:
                return STEP_COUNTER;
            case Sensor.TYPE_STEP_DETECTOR:
                return STEP_DETECTOR;
            default:
                return -1;
        }
    }

    /**
     * Converts a SensorType constant to a type constant used by the android system such as
     * Sensor.TYPE_ACCELEROMETER.
     *
     * @param sensorType A sensor type constant from the {@link SensorType} class.
     * @return A sensor type constant usable by the android system, -1 if conversion failed.
     * @see {@link Sensor}
     */
    public static int toAndroidSensor(int sensorType) {
        switch (sensorType) {
            case ACCELEROMETER:
                return Sensor.TYPE_ACCELEROMETER;
            case GYROSCOPE:
                return Sensor.TYPE_GYROSCOPE;
            case LIGHT:
                return Sensor.TYPE_LIGHT;
            case LINEAR_ACCELERATION:
                return Sensor.TYPE_LINEAR_ACCELERATION;
            case MAGNETIC_FIELD:
                return Sensor.TYPE_MAGNETIC_FIELD;
            case PRESSURE:
                return Sensor.TYPE_PRESSURE;
            case PROXIMITY:
                return Sensor.TYPE_PROXIMITY;
            case RELATIVE_HUMIDITY:
                return Sensor.TYPE_RELATIVE_HUMIDITY;
            case ROTATION_VECTOR:
                return Sensor.TYPE_ROTATION_VECTOR;
            case TEMPERATURE:
                return Sensor.TYPE_AMBIENT_TEMPERATURE;
            case HEART_RATE:
                if (Build.VERSION.SDK_INT >= 20)
                    return Sensor.TYPE_HEART_RATE;
                else
                    return -1;
            case STEP_COUNTER:
                return Sensor.TYPE_STEP_COUNTER;
            case GRAVITY:
                return Sensor.TYPE_GRAVITY;
            case STEP_DETECTOR:
                return Sensor.TYPE_STEP_DETECTOR;
            default:
                return -1;
        }
    }


    public static String toString(int sensorType) {
        switch (sensorType) {
            case SensorType.ACCELEROMETER:
                return "Accelerometer";
            case SensorType.GYROSCOPE:
                return "Gyroscope";
            case SensorType.GRAVITY:
                return "Gravity";
            case SensorType.HEART_RATE:
                return "Heart Rate";
            case SensorType.HEART_RATE_VARIABILITY:
                return "Heart Rate Variability";
            case SensorType.LIGHT:
                return "Light";
            case SensorType.LINEAR_ACCELERATION:
                return "Linear Accelerometer";
            case SensorType.MAGNETIC_FIELD:
                return "Magnetic Field";
            case SensorType.PRESSURE:
                return "Pressure";
            case SensorType.PROXIMITY:
                return "Proximity";
            case SensorType.RELATIVE_HUMIDITY:
                return "Relative Humidity";
            case SensorType.ROTATION_VECTOR:
                return "Rotation Vector";
            case SensorType.STEP_COUNTER:
                return "Step Counter";
            case SensorType.STEP_DETECTOR:
                return "Step Detector";
            case SensorType.TEMPERATURE:
                return "Temperature";
            case SensorType.SKIN_RESISTANCE:
                return "Skin Resistance";

            default:
                return null;


        }
    }

    /**
     * Converts a String to a type constant used by the android system such as
     * Sensor.TYPE_ACCELEROMETER.
     *
     * @param sensor String which represent the sensor type
     * @return A sensor type see {@link Sensor} constant usable by the android system, -1 if conversion failed.
     * @
     */
    public static int fromStringToSensorType(String sensor) {
        switch (sensor) {
            case "Accelerometer":
                return SensorType.ACCELEROMETER;
            case "Gyroscope":
                return SensorType.GYROSCOPE;
            case "Gravity":
                return SensorType.GRAVITY;
            case "Heart Rate":
                return SensorType.HEART_RATE;
            case "Heart Rate Variability":
                return SensorType.HEART_RATE_VARIABILITY;
            case "Light":
                return SensorType.LIGHT;
            case "Linear Accelerometer":
                return SensorType.LINEAR_ACCELERATION;
            case "Magnetic Field":
                return SensorType.MAGNETIC_FIELD;
            case "Pressure":
                return SensorType.PRESSURE;
            case "Proximity":
                return SensorType.PROXIMITY;
            case "Relative Humidity":
                return SensorType.RELATIVE_HUMIDITY;
            case "Rotation Vector":
                return SensorType.ROTATION_VECTOR;
            case "Step Counter":
                return SensorType.STEP_COUNTER;
            case "Step Detector":
                return SensorType.STEP_DETECTOR;
            case "Temperature":
                return SensorType.TEMPERATURE;
            case "Skin Resistance":
                return SensorType.SKIN_RESISTANCE;

            default:
                return 0;


        }
    }
}





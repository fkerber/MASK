package de.frederickerber.maskcommons;

/**
 * Integer constants used for messages between service and clients.
 */
public final class ServiceMsg {
    public static final int REGISTER_CLIENT = 1;
    public static final int UNREGISTER_CLIENT = 2;
    public static final int LIST_SENSORS = 3;
    public static final int SUBSCRIBE_TO_SENSOR = 4;
    public static final int UNSUBSCRIBE_FROM_SENSOR = 5;
    public static final int SENSOR_DATA = 6;
    public static final int DEVICE_CONNECTED = 7;
    public static final int DEVICE_DISCONNECTED = 8;
    public static final int DEVICE_STATUS = 9;
    public static final int ERROR = 10;
    public static final int ATTEMPT_AUTOMATIC_RECONNECT = 11;
    public static final int DISCONNECT_SERVICE = 12;
}

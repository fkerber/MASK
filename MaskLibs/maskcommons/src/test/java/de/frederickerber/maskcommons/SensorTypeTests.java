package de.frederickerber.maskcommons;


import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

public class SensorTypeTests {

   @Test
    public void byteArrayConversion_isCorrect(){
        Integer[] intArr = new Integer[]{0,2,4,6,8,10,9,7,5,3,1,Integer.MAX_VALUE,Integer.MIN_VALUE};
        ArrayList<Integer> list = new ArrayList<>(Arrays.asList(intArr));
        assertTrue(SensorType.listFromByteArray(SensorType.toByteArray(list)).equals(list));
    }

    @Test
    public void sensorDataConversion_isCorrect(){
        float[] values = new float[]{0.5f,0.7f,0.9f,Float.MAX_VALUE,Float.MIN_VALUE};
        float[] valuesWithType = new float[]{1f,0.5f,0.7f,0.9f,Float.MAX_VALUE,Float.MIN_VALUE};
        int sensorType = 1;
        long timestamp = System.nanoTime();
        byte[] data = SensorType.toByteArray(sensorType,timestamp,values);
        assertEquals(ByteBuffer.wrap(data).getLong(),timestamp);
        assertArrayEquals(SensorType.fromSensorData(data), valuesWithType, 0.0f);
    }

    @Test
    public void intByteArrayConversion_isCorrect(){
        int i = 1;
        int j = Integer.MAX_VALUE;
        assertEquals(SensorType.intFromByteArray(SensorType.toByteArray(i)),i);
        assertEquals(SensorType.intFromByteArray(SensorType.toByteArray(j)),j);
    }

}

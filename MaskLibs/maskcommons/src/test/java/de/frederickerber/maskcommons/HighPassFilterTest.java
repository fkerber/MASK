package de.frederickerber.maskcommons;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;


public class HighPassFilterTest {

    @Test
    public void init_alpha_isSet() {
        HighPassFilter f = new HighPassFilter();
        f.init(0.5f);
    }

    @Test(expected = IllegalStateException.class)
    public void apply_rejects_uninitialized() {
        HighPassFilter f = new HighPassFilter();
        f.apply(0.5f, 0.5f, 0.5f);
    }

    @Test
    public void first_apply_isCorrect() {
        HighPassFilter f = new HighPassFilter();
        f.init(0.5f);
        float[] data = new float[]{0.5f, 1f, 0.0f};
        float[] result = f.apply(data);
        assertArrayEquals("first high pass application cannot change input", data, result, 0.0f);
    }

    @Test
    public void apply_isCorrect_3d() {
        float[] data1 = new float[]{-0.5f, 100f, 0.0f};
        float[] data2 = new float[]{0.5f, 0f, 100f};
        float[] result2 = new float[]{0.25f, 0f, 50f};
        float[] data3 = new float[]{1f, 50f, 10f};
        float[] result3 = new float[]{0.375f, 25f, -20};
        HighPassFilter f = new HighPassFilter();
        f.init(0.5f);
        assertArrayEquals(data1, f.apply(data1), 0.0f);
        assertArrayEquals(result2, f.apply(data2), 0.00001f);
        assertArrayEquals(result3, f.apply(data3), 0.00001f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void apply_rejects_dimension_change_2to1() {
        HighPassFilter f = new HighPassFilter();
        f.init(0.5f);
        f.apply(0.1f, 0.1f);
        f.apply(0.3f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void apply_rejects_dimension_change_2to3() {
        HighPassFilter f = new HighPassFilter();
        f.init(0.5f);
        f.apply(0.1f, 0.1f);
        f.apply(0.3f, 0.4f, 0.5f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void apply_rejects_null() {
        HighPassFilter f = new HighPassFilter();
        f.init(0.5f);
        f.apply(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void init_rejects_null() {
        HighPassFilter f = new HighPassFilter();
        f.init(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void init_rejects_empty() {
        HighPassFilter f = new HighPassFilter();
        f.init();
    }

}
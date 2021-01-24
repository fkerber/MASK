package de.frederickerber.maskcommons;

/**
 * A high-pass filter for n-dimensional data represented in a n-length float array.
 *
 * @see <a href="https://en.wikipedia.org/wiki/High-pass_filter#Algorithmic_implementation">Wikipedia article</a>
 * @see <a href="https://developer.android.com/reference/android/hardware/SensorEvent.html#values">Android documentation</a>
 */
public class HighPassFilter extends Filter {

    private float alpha;
    private float[] prevReading;
    private float[] prevReadingFiltered;
    private boolean init;

    /**
     * Set alpha to adjust the impact of prior output and current change in input. 0 <= alpha <= 1.
     * Refer to the wikipedia article for a detailed explanation.
     *
     * @param initParams A float array with alpha at index 0
     * @see <a href="https://en.wikipedia.org/wiki/High-pass_filter#Algorithmic_implementation">Wikipedia article</a>
     */
    @Override
    public void init(float... initParams) {
        if (initParams == null || initParams.length < 1) {
            throw new IllegalArgumentException("provide alpha");
        }
        alpha = initParams[0];
        init = true;
    }

    /**
     * Apply the filter on a new sensor reading. The filter has to be initialized first.
     *
     * @param data A n-dimensional sensor reading.
     * @return A float array of length n representing the n-dimensional filtered input.
     * @see <a href="https://developer.android.com/reference/android/hardware/SensorEvent.html#values">Android documentation</a>
     */
    @Override
    public float[] apply(float... data) {
        if (!init) {
            throw new IllegalStateException("initialize the filter first");
        }
        if (data == null) {
            throw new IllegalArgumentException("argument may not be null");
        }
        if (prevReading != null && prevReading.length != data.length) {
            throw new IllegalArgumentException("data dimension may not change");
        }
        if (prevReading == null || prevReadingFiltered == null) {
            prevReading = data;
            prevReadingFiltered = data;
            return data;
        }
        for (int i = 0; i < data.length; i++) {
            prevReadingFiltered[i] = alpha * (prevReadingFiltered[i] + data[i] - prevReading[i]);
        }
        prevReading = data;
        return prevReadingFiltered;
    }
}

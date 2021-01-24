package de.frederickerber.maskcommons;

/**
 * A low-pass filter for n-dimensional data represented in a n-length float array.
 * Use this to e.g. filter accelerometer readings depending on previous readings.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Low-pass_filter#Algorithmic_implementation">Wikipedia article</a>
 * @see <a href="https://developer.android.com/reference/android/hardware/SensorEvent.html#values">Android documentation</a>
 */
public class LowPassFilter extends Filter {

    private float alpha;
    private float[] prevReading;
    private boolean init = false;

    /**
     * Initialize this filter with the desired smoothing factor, alpha. 0 <= alpha <= 1
     * For choosing alpha, refer to the wikipedia article.
     *
     * @param initParams A float array containing the smoothing factor at index 0;
     * @see <a href="https://en.wikipedia.org/wiki/Low-pass_filter#Algorithmic_implementation">Wikipedia article</a>
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
     * @param data A new n-dimensional sensor reading. You may not change dimensionality after the first call.
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
        if (prevReading == null) {
            prevReading = data;
            return prevReading;
        }
        for (int i = 0; i < data.length; i++) {
            prevReading[i] = prevReading[i] + alpha * (data[i] - prevReading[i]);
        }
        return prevReading;
    }
}

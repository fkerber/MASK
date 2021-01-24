package de.frederickerber.maskcommons;

public abstract class Filter {

    public abstract void init(float... initParams);

    public abstract float[] apply(float... data);

}

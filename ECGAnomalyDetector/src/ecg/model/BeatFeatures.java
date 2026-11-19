package ecg.model;

/**
 * Measurements taken from ONE heartbeat (one PQRST complex).
 *
 *            R
 *            /\
 *   P       /  \        T
 *  /\  ____/    \  ____/ \____
 *     PR   Q     S  ST
 *
 * - baseline     : isoelectric level, measured on the flat PR segment
 * - rAmplitude   : height of the R wave above the baseline (mV)
 * - stDeviation  : level of the ST segment relative to the baseline (mV)
 *                  positive = ST elevation, negative = ST depression
 * - qDepth       : how far the Q wave dips below the baseline (mV)
 * - qDurationMs  : width of the Q wave in milliseconds
 */
public class BeatFeatures {

    private final int rPeakIndex;
    private final double baseline;
    private final double rAmplitude;
    private final double stDeviation;
    private final double qDepth;
    private final double qDurationMs;

    public BeatFeatures(int rPeakIndex, double baseline, double rAmplitude,
                        double stDeviation, double qDepth, double qDurationMs) {
        this.rPeakIndex = rPeakIndex;
        this.baseline = baseline;
        this.rAmplitude = rAmplitude;
        this.stDeviation = stDeviation;
        this.qDepth = qDepth;
        this.qDurationMs = qDurationMs;
    }

    public int getRPeakIndex()     { return rPeakIndex; }
    public double getBaseline()    { return baseline; }
    public double getRAmplitude()  { return rAmplitude; }
    public double getStDeviation() { return stDeviation; }
    public double getQDepth()      { return qDepth; }
    public double getQDurationMs() { return qDurationMs; }

    /** Q depth as a fraction of R height (e.g. 0.30 = Q is 30% of R). */
    public double getQToRRatio() {
        return rAmplitude > 0 ? qDepth / rAmplitude : 0;
    }
}


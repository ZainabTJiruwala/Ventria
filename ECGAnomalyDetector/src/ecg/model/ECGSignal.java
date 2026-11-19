package ecg.model;

/**
 * Represents a single-lead ECG recording.
 *
 * The signal is stored as an array of voltage samples in millivolts (mV),
 * recorded at a fixed sampling rate (samples per second, Hz).
 *
 * OOP concept: ENCAPSULATION - the raw array is private and can only be
 * read through getters, so no other class can accidentally modify it.
 */
public class ECGSignal {

    private final double[] samples;     // voltage values in mV
    private final int samplingRate;     // samples per second (Hz)
    private final String sourceName;    // where the signal came from (file name / scenario)

    public ECGSignal(double[] samples, int samplingRate, String sourceName) {
        if (samples == null || samples.length == 0) {
            throw new IllegalArgumentException("ECG signal must contain at least one sample");
        }
        if (samplingRate <= 0) {
            throw new IllegalArgumentException("Sampling rate must be positive");
        }
        this.samples = samples.clone();
        this.samplingRate = samplingRate;
        this.sourceName = sourceName;
    }

    /** Voltage (mV) of the sample at the given index. */
    public double getSample(int index) {
        return samples[index];
    }

    /** Number of samples in the recording. */
    public int length() {
        return samples.length;
    }

    public int getSamplingRate() {
        return samplingRate;
    }

    public String getSourceName() {
        return sourceName;
    }

    /** Total length of the recording in seconds. */
    public double getDurationSeconds() {
        return (double) samples.length / samplingRate;
    }

    /** Converts a time span in milliseconds into a number of samples. */
    public int msToSamples(double milliseconds) {
        return (int) Math.round(milliseconds * samplingRate / 1000.0);
    }

    /** Returns a defensive copy of the samples. */
    public double[] getSamples() {
        return samples.clone();
    }

    /** Average voltage between two sample indices (inclusive start, exclusive end). */
    public double meanBetween(int start, int end) {
        start = Math.max(0, start);
        end = Math.min(samples.length, end);
        if (end <= start) {
            return 0;
        }
        double sum = 0;
        for (int i = start; i < end; i++) {
            sum += samples[i];
        }
        return sum / (end - start);
    }
}


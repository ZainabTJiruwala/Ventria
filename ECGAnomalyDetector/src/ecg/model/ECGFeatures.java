package ecg.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * All measurements extracted from an ECG recording.
 * The detection rules read ONLY this object - they never touch raw samples.
 * This keeps the rules short and easy to understand.
 */
public class ECGFeatures {

    private final ECGSignal signal;
    private final List<Integer> rPeaks;          // sample index of every R peak
    private final List<Double> rrIntervals;      // seconds between consecutive R peaks
    private final List<BeatFeatures> beats;      // per-beat measurements

    public ECGFeatures(ECGSignal signal, List<Integer> rPeaks,
                       List<Double> rrIntervals, List<BeatFeatures> beats) {
        this.signal = signal;
        this.rPeaks = new ArrayList<>(rPeaks);
        this.rrIntervals = new ArrayList<>(rrIntervals);
        this.beats = new ArrayList<>(beats);
    }

    public ECGSignal getSignal()             { return signal; }
    public List<Integer> getRPeaks()         { return Collections.unmodifiableList(rPeaks); }
    public List<Double> getRrIntervals()     { return Collections.unmodifiableList(rrIntervals); }
    public List<BeatFeatures> getBeats()     { return Collections.unmodifiableList(beats); }
    public int getBeatCount()                { return rPeaks.size(); }

    /** Average time between beats, in seconds. */
    public double getMeanRR() {
        return average(rrIntervals);
    }

    /** Heart rate in beats per minute:  HR = 60 / meanRR. */
    public double getHeartRate() {
        double meanRR = getMeanRR();
        return meanRR > 0 ? 60.0 / meanRR : 0;
    }

    /** Slowest instantaneous heart rate (from the longest RR interval). */
    public double getMinHeartRate() {
        return rrIntervals.isEmpty() ? 0 : 60.0 / Collections.max(rrIntervals);
    }

    /** Fastest instantaneous heart rate (from the shortest RR interval). */
    public double getMaxHeartRate() {
        return rrIntervals.isEmpty() ? 0 : 60.0 / Collections.min(rrIntervals);
    }

    /** SDNN: standard deviation of RR intervals in ms (heart-rate variability). */
    public double getSdnnMs() {
        if (rrIntervals.size() < 2) {
            return 0;
        }
        double mean = getMeanRR();
        double sumSq = 0;
        for (double rr : rrIntervals) {
            sumSq += (rr - mean) * (rr - mean);
        }
        return Math.sqrt(sumSq / (rrIntervals.size() - 1)) * 1000.0;
    }

    /** Average ST deviation over all beats (mV). */
    public double getMeanStDeviation() {
        List<Double> values = new ArrayList<>();
        for (BeatFeatures b : beats) values.add(b.getStDeviation());
        return average(values);
    }

    /** Average Q/R ratio over all beats. */
    public double getMeanQToRRatio() {
        List<Double> values = new ArrayList<>();
        for (BeatFeatures b : beats) values.add(b.getQToRRatio());
        return average(values);
    }

    /** Average R-wave amplitude (mV). */
    public double getMeanRAmplitude() {
        List<Double> values = new ArrayList<>();
        for (BeatFeatures b : beats) values.add(b.getRAmplitude());
        return average(values);
    }

    private static double average(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (double v : values) sum += v;
        double mean = sum / values.size();
        return Math.abs(mean) < 0.005 ? 0.0 : mean;   // avoid showing "-0.00"
    }
}


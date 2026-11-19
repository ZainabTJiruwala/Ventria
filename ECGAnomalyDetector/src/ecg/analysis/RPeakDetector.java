package ecg.analysis;

import ecg.model.ECGSignal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds the R peaks (the tall spikes) in an ECG signal.
 *
 * Simple, rule-based algorithm:
 *   1. Estimate the baseline level using the median of all samples.
 *   2. Set a threshold at 60% of the tallest peak above the baseline.
 *   3. A sample is an R peak if
 *        a) it is above the threshold,
 *        b) it is the highest point within +/-120 ms, and
 *        c) it is at least 250 ms after the previous R peak
 *           (the heart cannot physically beat faster than ~240 bpm).
 */
public class RPeakDetector {

    private static final double THRESHOLD_FRACTION = 0.60;
    private static final double SEARCH_WINDOW_MS = 120;
    private static final double REFRACTORY_PERIOD_MS = 250;

    public List<Integer> detect(ECGSignal signal) {
        double baseline = median(signal.getSamples());
        double maxValue = robustMax(signal.getSamples());
        double threshold = baseline + THRESHOLD_FRACTION * (maxValue - baseline);

        int window = signal.msToSamples(SEARCH_WINDOW_MS);
        int refractory = signal.msToSamples(REFRACTORY_PERIOD_MS);

        List<Integer> peaks = new ArrayList<>();
        int lastPeak = -refractory;

        for (int i = 0; i < signal.length(); i++) {
            double value = signal.getSample(i);
            boolean aboveThreshold = value > threshold;
            boolean farFromLastPeak = (i - lastPeak) >= refractory;

            if (aboveThreshold && farFromLastPeak && isLocalMaximum(signal, i, window)) {
                peaks.add(i);
                lastPeak = i;
            }
        }
        return peaks;
    }

    /** True if no sample within +/- window is higher than sample i. */
    private boolean isLocalMaximum(ECGSignal signal, int i, int window) {
        double value = signal.getSample(i);
        int from = Math.max(0, i - window);
        int to = Math.min(signal.length() - 1, i + window);
        for (int j = from; j <= to; j++) {
            if (signal.getSample(j) > value) {
                return false;
            }
        }
        return true;
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    /** 99.5th percentile - ignores a few extreme noise spikes. */
    private static double robustMax(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[(int) (sorted.length * 0.995) - 1];
    }
}


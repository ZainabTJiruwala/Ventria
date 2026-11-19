package ecg.analysis;

import ecg.model.BeatFeatures;
import ecg.model.ECGFeatures;
import ecg.model.ECGSignal;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures clinically important values around every R peak.
 *
 * All positions are fixed time offsets from the R peak (in milliseconds):
 *
 *   PR segment (baseline)  : R-80 ms  ... R-60 ms
 *   Q-wave search window   : R-60 ms  ... R-10 ms
 *   J point                : R+40 ms  (end of the QRS complex)
 *   ST measurement point   : J+60 ms  = R+100 ms (averaged over 20 ms)
 */
public class FeatureExtractor {

    private static final double BASELINE_START_MS = -80;
    private static final double BASELINE_END_MS = -60;
    private static final double Q_SEARCH_START_MS = -60;
    private static final double Q_SEARCH_END_MS = -10;
    private static final double J_POINT_MS = 40;
    private static final double ST_AFTER_J_MS = 60;
    private static final double Q_WAVE_LEVEL_MV = 0.025;  // below baseline by this much = inside the Q wave

    private final RPeakDetector peakDetector = new RPeakDetector();

    public ECGFeatures extract(ECGSignal signal) {
        List<Integer> rPeaks = peakDetector.detect(signal);
        List<Double> rrIntervals = computeRRIntervals(rPeaks, signal.getSamplingRate());
        List<BeatFeatures> beats = new ArrayList<>();

        for (int r : rPeaks) {
            BeatFeatures beat = measureBeat(signal, r);
            if (beat != null) {
                beats.add(beat);
            }
        }
        return new ECGFeatures(signal, rPeaks, rrIntervals, beats);
    }

    /** RR interval = time between two consecutive R peaks (seconds). */
    private List<Double> computeRRIntervals(List<Integer> rPeaks, int samplingRate) {
        List<Double> rr = new ArrayList<>();
        for (int i = 1; i < rPeaks.size(); i++) {
            rr.add((rPeaks.get(i) - rPeaks.get(i - 1)) / (double) samplingRate);
        }
        return rr;
    }

    /** Returns measurements for one beat, or null if the beat is cut off at the edge. */
    private BeatFeatures measureBeat(ECGSignal s, int r) {
        int baselineStart = r + s.msToSamples(BASELINE_START_MS);
        int stPoint = r + s.msToSamples(J_POINT_MS + ST_AFTER_J_MS);
        int halfWindow = s.msToSamples(10);

        if (baselineStart < 0 || stPoint + halfWindow >= s.length()) {
            return null;    // beat incomplete
        }

        // 1. Baseline: average of the flat PR segment
        double baseline = s.meanBetween(baselineStart, r + s.msToSamples(BASELINE_END_MS));

        // 2. R amplitude: peak height above the baseline
        double rAmplitude = s.getSample(r) - baseline;

        // 3. ST deviation: ST level (J + 60 ms) minus baseline
        double stLevel = s.meanBetween(stPoint - halfWindow, stPoint + halfWindow);
        double stDeviation = stLevel - baseline;

        // 4. Q wave: lowest point just before the R peak
        int qStart = r + s.msToSamples(Q_SEARCH_START_MS);
        int qEnd = r + s.msToSamples(Q_SEARCH_END_MS);
        int qIndex = qStart;
        for (int i = qStart; i <= qEnd; i++) {
            if (s.getSample(i) < s.getSample(qIndex)) {
                qIndex = i;
            }
        }
        double qDepth = Math.max(0, baseline - s.getSample(qIndex));
        double qDurationMs = measureQDuration(s, qIndex, baseline);

        return new BeatFeatures(r, baseline, rAmplitude, stDeviation, qDepth, qDurationMs);
    }

    /** Width of the Q wave: how long the signal stays clearly below the baseline. */
    private double measureQDuration(ECGSignal s, int qIndex, double baseline) {
        double level = baseline - Q_WAVE_LEVEL_MV;
        if (s.getSample(qIndex) >= level) {
            return 0;   // no real Q wave
        }
        int left = qIndex;
        while (left > 0 && s.getSample(left - 1) < level) left--;
        int right = qIndex;
        while (right < s.length() - 1 && s.getSample(right + 1) < level) right++;
        return (right - left + 1) * 1000.0 / s.getSamplingRate();
    }
}


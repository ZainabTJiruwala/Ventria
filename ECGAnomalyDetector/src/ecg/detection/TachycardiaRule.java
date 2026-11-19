package ecg.detection;

import ecg.model.ECGFeatures;

import java.util.ArrayList;
import java.util.List;

/**
 * TACHYCARDIA = heart beats too fast.
 *
 *   Rule:  heart rate > 100 bpm  -> Tachycardia (WARNING)
 *          heart rate > 150 bpm  -> Severe tachycardia (CRITICAL)
 */
public class TachycardiaRule extends DetectionRule {

    public static final double TACHYCARDIA_LIMIT_BPM = 100;
    public static final double SEVERE_LIMIT_BPM = 150;

    public TachycardiaRule() {
        super("Tachycardia", "Heart rate above " + (int) TACHYCARDIA_LIMIT_BPM + " bpm");
    }

    @Override
    public Finding evaluate(ECGFeatures features) {
        double hr = features.getHeartRate();
        List<String> evidence = new ArrayList<>();
        evidence.add("Mean heart rate = " + fmt(hr, 0) + " bpm (normal range 60-100 bpm)");
        evidence.add("Mean RR interval = " + fmt(features.getMeanRR() * 1000, 0)
                + " ms (tachycardia if < 600 ms)");

        if (hr <= TACHYCARDIA_LIMIT_BPM) {
            return Finding.negative(getName(), evidence);
        }

        if (hr > SEVERE_LIMIT_BPM) {
            evidence.add("Heart rate exceeds the severe limit of " + (int) SEVERE_LIMIT_BPM + " bpm");
            return Finding.positive(getName(), "Severe Tachycardia", Severity.CRITICAL, evidence,
                    "Urgent evaluation: rule out SVT / VT. Check hemodynamic stability; consider synchronized cardioversion if unstable.");
        }

        return Finding.positive(getName(), "Sinus Tachycardia", Severity.WARNING, evidence,
                "Look for underlying cause: fever, pain, dehydration, anaemia, anxiety, hyperthyroidism or stimulants.");
    }
}


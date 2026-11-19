package ecg.detection;

import ecg.model.ECGFeatures;

import java.util.ArrayList;
import java.util.List;

/**
 * BRADYCARDIA = heart beats too slowly.
 *
 *   Rule:  heart rate < 60 bpm  -> Bradycardia (WARNING)
 *          heart rate < 40 bpm  -> Severe bradycardia (CRITICAL)
 */
public class BradycardiaRule extends DetectionRule {

    public static final double BRADYCARDIA_LIMIT_BPM = 60;
    public static final double SEVERE_LIMIT_BPM = 40;

    public BradycardiaRule() {
        super("Bradycardia", "Heart rate below " + (int) BRADYCARDIA_LIMIT_BPM + " bpm");
    }

    @Override
    public Finding evaluate(ECGFeatures features) {
        double hr = features.getHeartRate();
        List<String> evidence = new ArrayList<>();
        evidence.add("Mean heart rate = " + fmt(hr, 0) + " bpm (normal range 60-100 bpm)");
        evidence.add("Mean RR interval = " + fmt(features.getMeanRR() * 1000, 0)
                + " ms (bradycardia if > 1000 ms)");

        if (hr >= BRADYCARDIA_LIMIT_BPM) {
            return Finding.negative(getName(), evidence);
        }

        if (hr < SEVERE_LIMIT_BPM) {
            evidence.add("Heart rate is below the severe limit of " + (int) SEVERE_LIMIT_BPM + " bpm");
            return Finding.positive(getName(), "Severe Sinus Bradycardia", Severity.CRITICAL, evidence,
                    "Assess for hypotension, syncope or heart block. Consider atropine / pacing per ACLS protocol.");
        }

        return Finding.positive(getName(), "Sinus Bradycardia", Severity.WARNING, evidence,
                "Correlate clinically (athletes, sleep, beta-blockers). Check electrolytes and thyroid function if symptomatic.");
    }
}


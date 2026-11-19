package ecg.detection;

import ecg.model.BeatFeatures;
import ecg.model.ECGFeatures;

import java.util.ArrayList;
import java.util.List;

/**
 * MYOCARDIAL INFARCTION (heart attack) = part of the heart muscle is not
 * getting enough blood. On the ECG this shows up as:
 *
 *   - ST ELEVATION    : ST segment raised >= 0.1 mV (1 small box) above baseline
 *                       -> acute MI (STEMI)                          [CRITICAL]
 *   - PATHOLOGICAL Q  : Q wave deeper than 25% of the R wave, or wider than 40 ms
 *                       -> dead tissue, prior (old) MI                [WARNING]
 *   - ST DEPRESSION   : ST segment lowered >= 0.05 mV below baseline
 *                       -> ischaemia / possible NSTEMI                [WARNING]
 *
 * A pattern only counts if it is PERSISTENT - present in at least 50% of beats -
 * so a single noisy beat cannot cause a false alarm.
 */
public class MyocardialInfarctionRule extends DetectionRule {

    public static final double ST_ELEVATION_MV = 0.10;
    public static final double ST_DEPRESSION_MV = -0.05;
    public static final double Q_TO_R_RATIO_LIMIT = 0.25;
    public static final double Q_DURATION_LIMIT_MS = 40;
    public static final double PERSISTENCE_FRACTION = 0.50;

    public MyocardialInfarctionRule() {
        super("Myocardial Infarction", "ST elevation / pathological Q waves / ST depression");
    }

    @Override
    public Finding evaluate(ECGFeatures features) {
        List<BeatFeatures> beats = features.getBeats();
        int total = beats.size();

        // Count how many beats meet each criterion
        int stElevated = 0, stDepressed = 0, pathologicalQ = 0;
        for (BeatFeatures beat : beats) {
            if (beat.getStDeviation() >= ST_ELEVATION_MV)   stElevated++;
            if (beat.getStDeviation() <= ST_DEPRESSION_MV)  stDepressed++;
            if (isPathologicalQ(beat))                      pathologicalQ++;
        }

        boolean hasStElevation  = total > 0 && stElevated    >= PERSISTENCE_FRACTION * total;
        boolean hasStDepression = total > 0 && stDepressed   >= PERSISTENCE_FRACTION * total;
        boolean hasQWaves       = total > 0 && pathologicalQ >= PERSISTENCE_FRACTION * total;

        List<String> evidence = new ArrayList<>();
        evidence.add("Mean ST deviation = " + fmt(features.getMeanStDeviation(), 2)
                + " mV (elevation if >= +" + fmt(ST_ELEVATION_MV, 2) + " mV)");
        evidence.add("ST elevation in " + stElevated + " of " + total + " beats");
        evidence.add("Mean Q/R ratio = " + fmt(features.getMeanQToRRatio() * 100, 0)
                + "% (pathological if > " + (int) (Q_TO_R_RATIO_LIMIT * 100) + "%)");
        evidence.add("Pathological Q waves in " + pathologicalQ + " of " + total + " beats");

        // Decision rules, from most to least serious
        if (hasStElevation) {
            String diagnosis = hasQWaves
                    ? "Acute Myocardial Infarction (STEMI) with Q waves"
                    : "Acute Myocardial Infarction (STEMI pattern)";
            return Finding.positive(getName(), diagnosis, Severity.CRITICAL, evidence,
                    "MEDICAL EMERGENCY: activate cath-lab / STEMI pathway. Obtain 12-lead ECG, "
                  + "troponin, aspirin unless contraindicated. Target door-to-balloon < 90 min.");
        }
        if (hasQWaves) {
            return Finding.positive(getName(), "Pathological Q Waves - Possible Old MI", Severity.WARNING,
                    evidence, "Suggests previous infarction. Recommend echocardiogram and cardiology review.");
        }
        if (hasStDepression) {
            evidence.add("ST depression in " + stDepressed + " of " + total + " beats");
            return Finding.positive(getName(), "ST Depression - Ischaemia / Possible NSTEMI", Severity.WARNING,
                    evidence, "Serial ECGs and high-sensitivity troponin. Evaluate for acute coronary syndrome.");
        }
        return Finding.negative(getName(), evidence);
    }

    /** Q wave is pathological if it is deeper than 25% of R OR wider than 40 ms. */
    private boolean isPathologicalQ(BeatFeatures beat) {
        return beat.getQToRRatio() > Q_TO_R_RATIO_LIMIT
            || beat.getQDurationMs() >= Q_DURATION_LIMIT_MS;
    }
}


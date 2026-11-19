package ecg.detection;

import ecg.analysis.FeatureExtractor;
import ecg.model.ECGFeatures;
import ecg.model.ECGSignal;
import ecg.model.Patient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The "brain" of the program. It runs the full pipeline:
 *
 *   ECGSignal --> FeatureExtractor --> ECGFeatures --> [each DetectionRule] --> DiagnosticReport
 */
public class AnomalyDetector {

    private final FeatureExtractor featureExtractor = new FeatureExtractor();
    private final List<DetectionRule> rules = new ArrayList<>();

    public AnomalyDetector() {
        // Register all the rules. Adding a new anomaly = adding one line here.
        rules.add(new BradycardiaRule());
        rules.add(new TachycardiaRule());
        rules.add(new MyocardialInfarctionRule());
    }

    public List<DetectionRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public DiagnosticReport analyze(ECGSignal signal, Patient patient) {
        ECGFeatures features = featureExtractor.extract(signal);
        List<Finding> findings = new ArrayList<>();

        if (features.getBeatCount() < 3) {
            List<String> evidence = new ArrayList<>();
            evidence.add("Only " + features.getBeatCount() + " beats detected (minimum 3 required)");
            findings.add(Finding.positive("Poor Signal Quality", "Insufficient / Poor Quality Signal",
                    Severity.WARNING, evidence, "Check electrode contact and record again."));
            return new DiagnosticReport(patient, features, findings);
        }

        // POLYMORPHISM: each rule decides for itself how to evaluate the features.
        for (DetectionRule rule : rules) {
            findings.add(rule.evaluate(features));
        }
        return new DiagnosticReport(patient, features, findings);
    }
}


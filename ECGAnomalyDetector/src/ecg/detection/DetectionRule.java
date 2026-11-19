package ecg.detection;

import ecg.model.ECGFeatures;

/**
 * Base class for every detection rule.
 *
 * OOP concepts:
 *  - ABSTRACTION : every rule must implement evaluate(), but HOW is up to the rule.
 *  - INHERITANCE : BradycardiaRule, TachycardiaRule, MyocardialInfarctionRule extend this class.
 *  - POLYMORPHISM: the AnomalyDetector keeps a List<DetectionRule> and calls
 *                  evaluate() on each one without knowing which rule it is.
 *
 * To add a new anomaly (e.g. Atrial Fibrillation) you only create a new subclass
 * and register it in the AnomalyDetector - no other code needs to change.
 */
public abstract class DetectionRule {

    private final String name;
    private final String description;

    protected DetectionRule(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName()        { return name; }
    public String getDescription() { return description; }

    /** Applies the rule to the measured ECG features. */
    public abstract Finding evaluate(ECGFeatures features);

    /** Helper for nicely formatted numbers in the evidence text. */
    protected static String fmt(double value, int decimals) {
        return String.format("%." + decimals + "f", value);
    }
}


package ecg.detection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The result produced by one detection rule.
 * Contains the diagnosis, severity, the evidence (which criteria were met)
 * and a clinical recommendation.
 */
public class Finding {

    private final String ruleName;
    private final boolean detected;
    private final String diagnosis;
    private final Severity severity;
    private final List<String> evidence;
    private final String recommendation;

    private Finding(String ruleName, boolean detected, String diagnosis, Severity severity,
                    List<String> evidence, String recommendation) {
        this.ruleName = ruleName;
        this.detected = detected;
        this.diagnosis = diagnosis;
        this.severity = severity;
        this.evidence = new ArrayList<>(evidence);
        this.recommendation = recommendation;
    }

    /** Factory method: the rule found an abnormality. */
    public static Finding positive(String ruleName, String diagnosis, Severity severity,
                                   List<String> evidence, String recommendation) {
        return new Finding(ruleName, true, diagnosis, severity, evidence, recommendation);
    }

    /** Factory method: the rule found nothing abnormal. */
    public static Finding negative(String ruleName, List<String> evidence) {
        return new Finding(ruleName, false, "Not detected", Severity.NORMAL, evidence, "");
    }

    public String getRuleName()        { return ruleName; }
    public boolean isDetected()        { return detected; }
    public String getDiagnosis()       { return diagnosis; }
    public Severity getSeverity()      { return severity; }
    public List<String> getEvidence()  { return Collections.unmodifiableList(evidence); }
    public String getRecommendation()  { return recommendation; }
}


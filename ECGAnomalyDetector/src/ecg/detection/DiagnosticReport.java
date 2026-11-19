package ecg.detection;

import ecg.model.ECGFeatures;
import ecg.model.Patient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The complete result of analysing one ECG: patient, measurements and findings.
 */
public class DiagnosticReport {

    private final Patient patient;
    private final ECGFeatures features;
    private final List<Finding> findings;
    private final LocalDateTime timestamp;

    public DiagnosticReport(Patient patient, ECGFeatures features, List<Finding> findings) {
        this.patient = patient;
        this.features = features;
        this.findings = new ArrayList<>(findings);
        this.timestamp = LocalDateTime.now();
    }

    public Patient getPatient()          { return patient; }
    public ECGFeatures getFeatures()     { return features; }
    public List<Finding> getFindings()   { return Collections.unmodifiableList(findings); }
    public LocalDateTime getTimestamp()  { return timestamp; }

    /** Only the findings that are abnormal. */
    public List<Finding> getPositiveFindings() {
        List<Finding> positive = new ArrayList<>();
        for (Finding f : findings) {
            if (f.isDetected()) positive.add(f);
        }
        return positive;
    }

    /** The worst severity among all findings. */
    public Severity getOverallSeverity() {
        Severity worst = Severity.NORMAL;
        for (Finding f : getPositiveFindings()) {
            if (f.getSeverity().ordinal() > worst.ordinal()) {
                worst = f.getSeverity();
            }
        }
        return worst;
    }

    /** The most serious abnormal finding, or null when the rhythm is normal. */
    public Finding getPrimaryFinding() {
        Finding primary = null;
        for (Finding f : getPositiveFindings()) {
            if (primary == null || f.getSeverity().ordinal() > primary.getSeverity().ordinal()) {
                primary = f;
            }
        }
        return primary;
    }

    /** Short name of the detected condition, e.g. "Bradycardia" or "Normal Sinus Rhythm". */
    public String getDetectedCondition() {
        Finding primary = getPrimaryFinding();
        return primary == null ? "Normal Sinus Rhythm" : primary.getRuleName();
    }

    /** One-line interpretation shown in the status banner. */
    public String getSummary() {
        List<Finding> positive = getPositiveFindings();
        if (positive.isEmpty()) {
            return "Normal Sinus Rhythm - No anomalies detected";
        }
        StringBuilder sb = new StringBuilder();
        for (Finding f : positive) {
            if (sb.length() > 0) sb.append("  +  ");
            sb.append(f.getDiagnosis());
        }
        return sb.toString();
    }

    /** Plain-text report suitable for saving or printing. */
    public String toText() {
        String line = "=".repeat(64);
        StringBuilder sb = new StringBuilder();
        sb.append(line).append('\n');
        sb.append("             VENTRIA  -  ECG ANOMALY DETECTION REPORT\n");
        sb.append(line).append('\n');
        sb.append("Date/Time   : ").append(timestamp.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss"))).append('\n');
        sb.append("Patient     : ").append(patient.getName()).append('\n');
        sb.append("Patient ID  : ").append(patient.getPatientId()).append('\n');
        sb.append("Age / Sex   : ").append(patient.getAge()).append(" / ").append(patient.getSex()).append('\n');
        sb.append("Source      : ").append(features.getSignal().getSourceName()).append('\n');
        sb.append("Lead        : II   |  ").append(features.getSignal().getSamplingRate()).append(" Hz  |  ")
          .append(String.format("%.1f s", features.getSignal().getDurationSeconds())).append('\n');
        sb.append(line).append('\n');
        sb.append("MEASUREMENTS\n");
        sb.append(String.format("  Heart rate          : %.0f bpm (min %.0f, max %.0f)%n",
                features.getHeartRate(), features.getMinHeartRate(), features.getMaxHeartRate()));
        sb.append(String.format("  Mean RR interval    : %.0f ms%n", features.getMeanRR() * 1000));
        sb.append(String.format("  HRV (SDNN)          : %.0f ms%n", features.getSdnnMs()));
        sb.append(String.format("  Mean R amplitude    : %.2f mV%n", features.getMeanRAmplitude()));
        sb.append(String.format("  Mean ST deviation   : %+.2f mV%n", features.getMeanStDeviation()));
        sb.append(String.format("  Mean Q/R ratio      : %.0f %%%n", features.getMeanQToRRatio() * 100));
        sb.append(String.format("  Beats analysed      : %d%n", features.getBeatCount()));
        sb.append(line).append('\n');
        sb.append("DETECTED       : ").append(getDetectedCondition()).append('\n');
        sb.append("OVERALL STATUS : ").append(getOverallSeverity().getLabel()).append('\n');
        sb.append("INTERPRETATION : ").append(getSummary()).append('\n');
        sb.append(line).append('\n');
        for (Finding f : findings) {
            sb.append(String.format("[%s] %s : %s%n",
                    f.isDetected() ? f.getSeverity().getLabel() : "OK", f.getRuleName(), f.getDiagnosis()));
            for (String e : f.getEvidence()) {
                sb.append("     - ").append(e).append('\n');
            }
            if (f.isDetected()) {
                sb.append("     > Recommendation: ").append(f.getRecommendation()).append('\n');
            }
            sb.append('\n');
        }
        sb.append(line).append('\n');
        sb.append("Automated rule-based interpretation. Must be confirmed by a\n");
        sb.append("qualified physician. For educational purposes only.\n");
        return sb.toString();
    }
}


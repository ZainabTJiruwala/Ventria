package ecg.web;

import ecg.detection.DetectionRule;
import ecg.detection.DiagnosticReport;
import ecg.detection.Finding;
import ecg.detection.MyocardialInfarctionRule;
import ecg.model.BeatFeatures;
import ecg.model.ECGFeatures;
import ecg.model.ECGSignal;
import ecg.model.Patient;
import ecg.signal.RhythmScenario;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Converts our Java domain objects (Patient, ECGFeatures, DiagnosticReport)
 * into JSON text so the web browser can display them.
 *
 * Written without external libraries to keep the project dependency-free.
 */
public final class JsonWriter {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

    private JsonWriter() { }

    /** Serializes the available demo scenarios and active rules for the UI. */
    public static String configToJson(List<DetectionRule> rules) {
        StringBuilder sb = new StringBuilder("{");

        sb.append("\"scenarios\":[");
        RhythmScenario[] scenarios = RhythmScenario.values();
        for (int i = 0; i < scenarios.length; i++) {
            if (i > 0) sb.append(',');
            RhythmScenario s = scenarios[i];
            sb.append('{')
              .append("\"id\":").append(quote(s.name())).append(',')
              .append("\"label\":").append(quote(s.getLabel())).append(',')
              .append("\"heartRate\":").append(s.getHeartRate()).append(',')
              .append("\"defaultPatient\":").append(patientToJson(s.getDefaultPatient()))
              .append('}');
        }
        sb.append("],");

        sb.append("\"rules\":[");
        for (int i = 0; i < rules.size(); i++) {
            if (i > 0) sb.append(',');
            DetectionRule r = rules.get(i);
            sb.append('{')
              .append("\"name\":").append(quote(r.getName())).append(',')
              .append("\"description\":").append(quote(r.getDescription()))
              .append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Serializes a complete DiagnosticReport (signal + features + findings). */
    public static String reportToJson(DiagnosticReport report) {
        ECGFeatures features = report.getFeatures();
        ECGSignal signal = features.getSignal();

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"patient\":").append(patientToJson(report.getPatient())).append(',');

        sb.append("\"signal\":{");
        sb.append("\"sourceName\":").append(quote(signal.getSourceName())).append(',');
        sb.append("\"samplingRate\":").append(signal.getSamplingRate()).append(',');
        sb.append("\"durationSeconds\":").append(num(signal.getDurationSeconds(), 2)).append(',');
        sb.append("\"samples\":");
        appendDoubleArray(sb, signal.getSamples());
        sb.append("},");

        sb.append("\"features\":{");
        sb.append("\"beatCount\":").append(features.getBeatCount()).append(',');
        sb.append("\"heartRate\":").append(num(features.getHeartRate(), 1)).append(',');
        sb.append("\"minHeartRate\":").append(num(features.getMinHeartRate(), 1)).append(',');
        sb.append("\"maxHeartRate\":").append(num(features.getMaxHeartRate(), 1)).append(',');
        sb.append("\"meanRrMs\":").append(num(features.getMeanRR() * 1000.0, 1)).append(',');
        sb.append("\"sdnnMs\":").append(num(features.getSdnnMs(), 1)).append(',');
        sb.append("\"meanStDeviation\":").append(num(features.getMeanStDeviation(), 3)).append(',');
        sb.append("\"meanQToRRatio\":").append(num(features.getMeanQToRRatio(), 3)).append(',');
        sb.append("\"meanRAmplitude\":").append(num(features.getMeanRAmplitude(), 3)).append(',');
        sb.append("\"rPeaks\":");
        appendIntList(sb, features.getRPeaks());
        sb.append(",\"stElevatedPeaks\":[");
        boolean first = true;
        for (BeatFeatures beat : features.getBeats()) {
            if (beat.getStDeviation() >= MyocardialInfarctionRule.ST_ELEVATION_MV) {
                if (!first) sb.append(',');
                sb.append(beat.getRPeakIndex());
                first = false;
            }
        }
        sb.append("]},");

        sb.append("\"report\":{");
        sb.append("\"timestamp\":").append(quote(report.getTimestamp().format(DATE_FORMAT))).append(',');
        sb.append("\"overallSeverity\":").append(quote(report.getOverallSeverity().getLabel())).append(',');
        Finding primary = report.getPrimaryFinding();
        sb.append("\"detectedCondition\":").append(quote(report.getDetectedCondition())).append(',');
        sb.append("\"primaryDiagnosis\":")
          .append(quote(primary == null ? "Normal Sinus Rhythm" : primary.getDiagnosis())).append(',');
        sb.append("\"summary\":").append(quote(report.getSummary())).append(',');
        sb.append("\"plainText\":").append(quote(report.toText())).append(',');
        sb.append("\"findings\":[");
        List<Finding> findings = report.getFindings();
        for (int i = 0; i < findings.size(); i++) {
            if (i > 0) sb.append(',');
            appendFinding(sb, findings.get(i));
        }
        sb.append("]}");

        sb.append('}');
        return sb.toString();
    }

    private static String patientToJson(Patient p) {
        return "{"
                + "\"patientId\":" + quote(p.getPatientId()) + ','
                + "\"name\":" + quote(p.getName()) + ','
                + "\"age\":" + p.getAge() + ','
                + "\"sex\":" + quote(p.getSex())
                + "}";
    }

    private static void appendFinding(StringBuilder sb, Finding f) {
        sb.append('{');
        sb.append("\"ruleName\":").append(quote(f.getRuleName())).append(',');
        sb.append("\"detected\":").append(f.isDetected()).append(',');
        sb.append("\"diagnosis\":").append(quote(f.getDiagnosis())).append(',');
        sb.append("\"severity\":").append(quote(f.getSeverity().getLabel())).append(',');
        sb.append("\"recommendation\":").append(quote(f.getRecommendation())).append(',');
        sb.append("\"evidence\":[");
        List<String> evidence = f.getEvidence();
        for (int i = 0; i < evidence.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(quote(evidence.get(i)));
        }
        sb.append("]}");
    }

    private static void appendDoubleArray(StringBuilder sb, double[] arr) {
        sb.append('[');
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(num(arr[i], 4));
        }
        sb.append(']');
    }

    private static void appendIntList(StringBuilder sb, List<Integer> list) {
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(list.get(i));
        }
        sb.append(']');
    }

    private static String num(double value, int decimals) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        return String.format(Locale.US, "%." + decimals + "f", value);
    }

    public static String quote(String text) {
        if (text == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}


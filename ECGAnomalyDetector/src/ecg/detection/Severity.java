package ecg.detection;

/** How serious a finding is. The web UI picks the display colour from the label. */
public enum Severity {

    NORMAL  ("NORMAL"),
    WARNING ("WARNING"),
    CRITICAL("CRITICAL");

    private final String label;

    Severity(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}

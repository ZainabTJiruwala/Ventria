package ecg.signal;

import ecg.model.Patient;

/**
 * Demo scenarios that the simulator can produce.
 * Each scenario defines the heart rate, waveform shape, and demo patient.
 */
public enum RhythmScenario {

    NORMAL                ("Normal Sinus Rhythm",                    75,  1.20, -0.10, 0.30, 0.00,
                           new Patient("PT-24-1031", "Ayesha Khan", 34, "Female")),
    BRADYCARDIA           ("Sinus Bradycardia",                      45,  1.20, -0.10, 0.30, 0.00,
                           new Patient("PT-24-1047", "Rahul Mehta", 67, "Male")),
    TACHYCARDIA           ("Sinus Tachycardia",                     135,  1.15, -0.10, 0.25, 0.00,
                           new Patient("PT-24-1052", "Sara Ali", 28, "Female")),
    MYOCARDIAL_INFARCTION ("Acute Myocardial Infarction (STEMI)",   82,  1.10, -0.35, 0.30, 0.22,
                           new Patient("PT-24-1068", "Imran Sheikh", 59, "Male"));

    private final String label;
    private final int heartRate;          // beats per minute
    private final double rAmplitude;      // mV
    private final double qAmplitude;      // mV (negative = downward)
    private final double tAmplitude;      // mV
    private final double stElevation;     // mV (0 = normal flat ST segment)
    private final Patient defaultPatient;

    RhythmScenario(String label, int heartRate, double rAmplitude,
                   double qAmplitude, double tAmplitude, double stElevation,
                   Patient defaultPatient) {
        this.label = label;
        this.heartRate = heartRate;
        this.rAmplitude = rAmplitude;
        this.qAmplitude = qAmplitude;
        this.tAmplitude = tAmplitude;
        this.stElevation = stElevation;
        this.defaultPatient = defaultPatient;
    }

    public String getLabel()          { return label; }
    public int getHeartRate()         { return heartRate; }
    public double getRAmplitude()     { return rAmplitude; }
    public double getQAmplitude()     { return qAmplitude; }
    public double getTAmplitude()     { return tAmplitude; }
    public double getStElevation()    { return stElevation; }
    public Patient getDefaultPatient(){ return defaultPatient; }

    /** Shown in the drop-down list of the user interface. */
    @Override
    public String toString() {
        return label;
    }
}


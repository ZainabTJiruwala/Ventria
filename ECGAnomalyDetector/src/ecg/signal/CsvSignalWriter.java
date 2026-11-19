package ecg.signal;

import ecg.model.ECGSignal;
import ecg.model.Patient;

import java.util.Locale;

/**
 * Writes an ECG signal as CSV text that CsvSignalLoader can read back:
 * "# Key: Value" patient header lines, then one "time_s,lead_II_mV" row per sample.
 */
public final class CsvSignalWriter {

    private CsvSignalWriter() { }

    public static String toCsv(ECGSignal signal, Patient patient) {
        StringBuilder csv = new StringBuilder();
        csv.append("# Ventria ECG recording\n");
        csv.append("# Patient ID: ").append(patient.getPatientId()).append('\n');
        csv.append("# Name: ").append(patient.getName()).append('\n');
        csv.append("# Age: ").append(patient.getAge()).append('\n');
        csv.append("# Sex: ").append(patient.getSex()).append('\n');
        csv.append("# Lead: II\n");
        csv.append("# Sampling rate: ").append(signal.getSamplingRate()).append(" Hz\n");
        csv.append("# Units: mV\n");
        csv.append("time_s,lead_II_mV\n");

        double[] samples = signal.getSamples();
        int fs = signal.getSamplingRate();
        for (int i = 0; i < samples.length; i++) {
            csv.append(String.format(Locale.US, "%.4f,%.5f%n", (double) i / fs, samples[i]));
        }
        return csv.toString();
    }
}

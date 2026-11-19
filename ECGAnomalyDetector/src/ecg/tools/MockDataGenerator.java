package ecg.tools;

import ecg.model.ECGSignal;
import ecg.model.Patient;
import ecg.signal.CsvSignalWriter;
import ecg.signal.RhythmScenario;
import ecg.signal.SyntheticECGGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes a folder of mock ECG recordings (CSV) that the Ventria web app can load:
 * two each of normal sinus rhythm, bradycardia, tachycardia and myocardial infarction.
 *
 * Run with:  java -cp out ecg.tools.MockDataGenerator [output folder]
 * Default output folder: ../Ventria_Mock_ECG_Data (next to the project folder).
 */
public class MockDataGenerator {

    private static final Path DEFAULT_OUTPUT = Paths.get("..", "Ventria_Mock_ECG_Data");

    /** One mock recording: which rhythm, at what rate, for how long, and for whom. */
    private record MockRecording(String fileName, RhythmScenario scenario, int heartRate,
                                 double seconds, long seed, Patient patient) { }

    private static final MockRecording[] RECORDINGS = {
        new MockRecording("normal_sinus_rhythm_01.csv", RhythmScenario.NORMAL, 72, 10, 101,
                new Patient("PT-25-2101", "Meera Iyer", 41, "Female")),
        new MockRecording("normal_sinus_rhythm_02.csv", RhythmScenario.NORMAL, 88, 12, 102,
                new Patient("PT-25-2102", "Daniel Fernandes", 29, "Male")),
        new MockRecording("bradycardia_01.csv", RhythmScenario.BRADYCARDIA, 50, 12, 201,
                new Patient("PT-25-2201", "Harish Rao", 71, "Male")),
        new MockRecording("bradycardia_02_severe.csv", RhythmScenario.BRADYCARDIA, 37, 15, 202,
                new Patient("PT-25-2202", "Leela Nair", 78, "Female")),
        new MockRecording("tachycardia_01.csv", RhythmScenario.TACHYCARDIA, 118, 10, 301,
                new Patient("PT-25-2301", "Fatima Sayed", 24, "Female")),
        new MockRecording("tachycardia_02_severe.csv", RhythmScenario.TACHYCARDIA, 158, 10, 302,
                new Patient("PT-25-2302", "Kabir Malhotra", 33, "Male")),
        new MockRecording("myocardial_infarction_01.csv", RhythmScenario.MYOCARDIAL_INFARCTION, 82, 10, 401,
                new Patient("PT-25-2401", "Vikram Desai", 62, "Male")),
        new MockRecording("myocardial_infarction_02.csv", RhythmScenario.MYOCARDIAL_INFARCTION, 94, 12, 402,
                new Patient("PT-25-2402", "Rukhsana Begum", 57, "Female")),
    };

    public static void main(String[] args) throws IOException {
        Path outputDir = (args.length > 0 ? Paths.get(args[0]) : DEFAULT_OUTPUT).toAbsolutePath().normalize();
        Files.createDirectories(outputDir);

        for (MockRecording r : RECORDINGS) {
            ECGSignal signal = new SyntheticECGGenerator(r.scenario(), r.heartRate(),
                    SyntheticECGGenerator.DEFAULT_SAMPLING_RATE, r.seconds(), r.seed()).getSignal();
            Files.writeString(outputDir.resolve(r.fileName()),
                    CsvSignalWriter.toCsv(signal, r.patient()), StandardCharsets.UTF_8);
            System.out.printf("  %-30s %-38s %3d bpm%n", r.fileName(), r.scenario().getLabel(), r.heartRate());
        }
        System.out.println("Mock ECG data written to: " + outputDir);
    }
}

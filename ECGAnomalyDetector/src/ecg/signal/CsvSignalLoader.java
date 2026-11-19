package ecg.signal;

import ecg.model.ECGSignal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads an ECG from a CSV / text file or CSV text payload.
 *
 * Supported formats (values must be in millivolts):
 *   1) one value per line:            0.012
 *   2) time (s) and value per line:   0.004,0.012
 * Header lines that are not numbers are skipped automatically.
 * If a time column exists, the sampling rate is calculated from it.
 *
 * Optional metadata lines at the top of the file look like
 *   # Patient ID: PT-25-2101
 *   # Name: Meera Iyer
 * and are available after loading through getMetadata() (keys in lower case).
 */
public class CsvSignalLoader implements SignalSource {

    private final File file;
    private final String csvContent;
    private final String sourceLabel;
    private final int defaultSamplingRate;
    private final Map<String, String> metadata = new LinkedHashMap<>();

    public CsvSignalLoader(File file, int defaultSamplingRate) {
        this.file = file;
        this.csvContent = null;
        this.sourceLabel = "File: " + file.getName();
        this.defaultSamplingRate = defaultSamplingRate;
    }

    public CsvSignalLoader(String csvContent, int defaultSamplingRate, String fileName) {
        this.file = null;
        this.csvContent = csvContent;
        String safeName = (fileName == null || fileName.isBlank()) ? "uploaded.csv" : fileName;
        this.sourceLabel = "File: " + safeName;
        this.defaultSamplingRate = defaultSamplingRate;
    }

    @Override
    public ECGSignal getSignal() throws IOException {
        metadata.clear();
        try (Reader reader = (file != null) ? new FileReader(file) : new StringReader(csvContent)) {
            return parseCsv(reader, defaultSamplingRate, sourceLabel, metadata);
        }
    }

    /** "# Key: Value" header lines found by the last getSignal() call. */
    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    /** Parses CSV rows from any Reader into an ECGSignal; "# Key: Value" lines go into metadata. */
    public static ECGSignal parseCsv(Reader reader, int defaultSamplingRate, String sourceName,
                                     Map<String, String> metadata) throws IOException {
        List<Double> times = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    readMetadataLine(trimmed.substring(1), metadata);
                    continue;
                }
                String[] parts = trimmed.split("[,;\\t ]+");
                try {
                    if (parts.length >= 2) {
                        times.add(Double.parseDouble(parts[0]));
                        values.add(Double.parseDouble(parts[parts.length - 1]));
                    } else if (!parts[0].isEmpty()) {
                        values.add(Double.parseDouble(parts[0]));
                    }
                } catch (NumberFormatException headerLine) {
                    // not a number (e.g. "time,mV") -> skip this line
                }
            }
        }

        if (values.size() < 100) {
            throw new IOException("File contains too few numeric samples (" + values.size() + ")");
        }

        int samplingRate = defaultSamplingRate;
        if (times.size() == values.size() && times.size() > 1) {
            double dt = (times.get(times.size() - 1) - times.get(0)) / (times.size() - 1);
            if (dt > 0) {
                samplingRate = (int) Math.round(1.0 / dt);
            }
        }

        double[] samples = new double[values.size()];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = values.get(i);
        }
        return new ECGSignal(samples, samplingRate, sourceName);
    }

    private static void readMetadataLine(String text, Map<String, String> metadata) {
        int colon = text.indexOf(':');
        if (colon <= 0) {
            return;    // a plain comment, not "Key: Value"
        }
        String key = text.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String value = text.substring(colon + 1).trim();
        if (!key.isEmpty() && !value.isEmpty()) {
            metadata.put(key, value);
        }
    }

    @Override
    public String getDescription() {
        return sourceLabel;
    }
}

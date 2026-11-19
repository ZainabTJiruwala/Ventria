package ecg.signal;

import ecg.model.ECGSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a realistic-looking synthetic Lead II ECG.
 *
 * Each heartbeat is built by adding five bell-shaped (Gaussian) bumps:
 *     P wave, Q wave, R wave, S wave and T wave.
 * For myocardial infarction an extra "plateau" lifts the ST segment.
 * Small random noise and baseline wander make the trace look real.
 */
public class SyntheticECGGenerator implements SignalSource {

    public static final int DEFAULT_SAMPLING_RATE = 250;   // Hz
    public static final double DEFAULT_DURATION = 10.0;    // seconds

    private final RhythmScenario scenario;
    private final int heartRate;
    private final int samplingRate;
    private final double durationSeconds;
    private final Random random;

    public SyntheticECGGenerator(RhythmScenario scenario) {
        this(scenario, scenario.getHeartRate(), DEFAULT_SAMPLING_RATE, DEFAULT_DURATION, 42L);
    }

    public SyntheticECGGenerator(RhythmScenario scenario, int samplingRate,
                                 double durationSeconds, long seed) {
        this(scenario, scenario.getHeartRate(), samplingRate, durationSeconds, seed);
    }

    /** Same waveform shape as the scenario, but at a custom heart rate (bpm). */
    public SyntheticECGGenerator(RhythmScenario scenario, int heartRate, int samplingRate,
                                 double durationSeconds, long seed) {
        this.scenario = scenario;
        this.heartRate = heartRate;
        this.samplingRate = samplingRate;
        this.durationSeconds = durationSeconds;
        this.random = new Random(seed);
    }

    @Override
    public ECGSignal getSignal() {
        double rr = 60.0 / heartRate;                    // seconds per beat
        List<Double> beatTimes = createBeatTimes(rr);

        int n = (int) (durationSeconds * samplingRate);
        double[] samples = new double[n];

        for (int i = 0; i < n; i++) {
            double t = (double) i / samplingRate;

            // 1) slow breathing-related baseline wander + small electrical noise
            double value = 0.04 * Math.sin(2 * Math.PI * 0.25 * t)
                         + 0.012 * random.nextGaussian();

            // 2) add every heartbeat that is close enough to influence this sample
            for (double beatTime : beatTimes) {
                double dt = t - beatTime;
                if (Math.abs(dt) < 1.0) {
                    value += heartbeatWaveform(dt, rr);
                }
            }
            samples[i] = value;
        }
        return new ECGSignal(samples, samplingRate, "Simulator: " + scenario.getLabel());
    }

    @Override
    public String getDescription() {
        return "Simulated " + scenario.getLabel() + " (" + heartRate + " bpm)";
    }

    /** Times (s) of each R peak, with ~2% natural beat-to-beat variation. */
    private List<Double> createBeatTimes(double rr) {
        List<Double> times = new ArrayList<>();
        double t = 0.4;
        while (t < durationSeconds + 1.0) {
            times.add(t);
            t += rr * (1.0 + 0.02 * random.nextGaussian());
        }
        return times;
    }

    /**
     * Voltage of one PQRST complex at time dt (seconds) relative to its R peak.
     * Intervals shrink at fast heart rates (QT ~ sqrt(RR), Bazett's formula).
     */
    private double heartbeatWaveform(double dt, double rr) {
        double s = Math.sqrt(rr);
        double pScale = Math.max(0.75, s);
        double tCenter = 0.28 * s;

        double v = 0;
        v += gaussian(dt, 0.15,                      -0.16 * pScale, 0.020);  // P wave
        v += gaussian(dt, scenario.getQAmplitude(),  -0.030,         qWidth()); // Q wave
        v += gaussian(dt, scenario.getRAmplitude(),   0.000,         0.011);  // R wave
        v += gaussian(dt, -0.25,                      0.030,         0.009);  // S wave
        v += gaussian(dt, scenario.getTAmplitude(),   tCenter,       0.050 * s); // T wave

        if (scenario.getStElevation() > 0) {                                  // MI: ST elevation
            v += scenario.getStElevation() * plateau(dt, 0.04, tCenter, 0.08 * s);
        }
        return v;
    }

    /** Pathological (MI) Q waves are wider than normal ones. */
    private double qWidth() {
        return scenario.getQAmplitude() < -0.2 ? 0.011 : 0.007;
    }

    /** Bell-shaped curve: amplitude * exp(-(t - center)^2 / (2 * width^2)). */
    private static double gaussian(double t, double amplitude, double center, double width) {
        double x = (t - center) / width;
        return amplitude * Math.exp(-0.5 * x * x);
    }

    /** Smooth step that rises at 'start', stays at 1, then falls after 'end'. */
    private static double plateau(double t, double start, double end, double fallTime) {
        double rise = smoothStep((t - start) / 0.02);
        double fall = 1.0 - smoothStep((t - end) / fallTime);
        return rise * fall;
    }

    private static double smoothStep(double x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        return x * x * (3 - 2 * x);
    }
}


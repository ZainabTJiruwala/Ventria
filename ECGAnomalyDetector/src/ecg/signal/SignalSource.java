package ecg.signal;

import ecg.model.ECGSignal;

import java.io.IOException;

/**
 * Anything that can provide an ECG signal.
 *
 * OOP concept: INTERFACE / ABSTRACTION - the rest of the program does not
 * care whether the ECG comes from a simulator or from a file.
 */
public interface SignalSource {

    /** Produces (or loads) the ECG signal. */
    ECGSignal getSignal() throws IOException;

    /** Human-readable description of the source. */
    String getDescription();
}


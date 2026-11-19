# Ventria – ECG Anomaly Detection

A **rule-based** ECG anomaly detector written in **Java** using **object-oriented programming**,
served as a local **web app**. It detects:

| Anomaly | Rule | Severity |
|---|---|---|
| **Bradycardia** | Heart rate < 60 bpm (< 40 bpm = severe) | Warning / Critical |
| **Tachycardia** | Heart rate > 100 bpm (> 150 bpm = severe) | Warning / Critical |
| **Myocardial Infarction (STEMI)** | ST elevation ≥ 0.1 mV in ≥ 50 % of beats | Critical |
| Possible old MI | Pathological Q wave (Q > 25 % of R, or ≥ 40 ms) in ≥ 50 % of beats | Warning |
| Ischaemia / NSTEMI | ST depression ≤ −0.05 mV in ≥ 50 % of beats | Warning |

If no rule fires, the recording is reported as **Normal Sinus Rhythm**.

## How to run

```bash
chmod +x run.sh
./run.sh            # web app at http://localhost:8080
./run.sh 9090       # same, on another port
./run.sh mock       # (re)generate the mock ECG files in ../Ventria_Mock_ECG_Data
```

Or manually:

```bash
javac -d out $(find src -name "*.java")
java -cp out ecg.web.WebServer 8080          # web app on http://localhost:8080
java -cp out ecg.tools.MockDataGenerator     # mock ECG CSV files
```

## Using the web app

- **Choose folder** (or drag a folder onto the page): every `.csv` / `.txt` recording inside is
  analysed and listed in the *Batch analysis* table with its detected condition. Click a row to
  view that recording's ECG strip, vitals and findings.
- **Choose files**: same, for one or more individual files.
- **Reference recordings**: built-in demo signals (Normal, Bradycardia, Tachycardia, MI).
- **Export report** downloads the plain-text report for the recording on screen.

## Mock data

`../Ventria_Mock_ECG_Data/` (inside `Java_Atom_Training`) holds 8 synthetic Lead II recordings at 250 Hz:

| File | Expected result |
|---|---|
| `normal_sinus_rhythm_01.csv`, `_02.csv` | Normal Sinus Rhythm (72, 88 bpm) |
| `bradycardia_01.csv` | Sinus Bradycardia (50 bpm) |
| `bradycardia_02_severe.csv` | Severe Sinus Bradycardia (37 bpm) |
| `tachycardia_01.csv` | Sinus Tachycardia (118 bpm) |
| `tachycardia_02_severe.csv` | Severe Tachycardia (158 bpm) |
| `myocardial_infarction_01.csv`, `_02.csv` | Acute MI – STEMI (ST elevation + Q waves) |

## File format

```
# Patient ID: PT-25-2201      <- optional "# Key: Value" header lines (Patient ID, Name, Age, Sex)
# Name: Harish Rao
time_s,lead_II_mV             <- non-numeric lines are skipped
0.0000,-0.01131               <- time (s), value (mV)   ...or just one value per line
0.0040,-0.00600
```

With a time column the sampling rate is calculated automatically; otherwise the
"Rate if file has no time column" value (default 250 Hz) is used.

## How it works (pipeline)

```
ECGSignal ──► RPeakDetector ──► FeatureExtractor ──► ECGFeatures ──► DetectionRules ──► DiagnosticReport
 (samples)     (find beats)     (HR, ST, Q waves)    (measurements)   (if/else rules)    (shown in browser)
```

1. **R-peak detection** – a beat is a local maximum above 60 % of the tallest peak, at least 250 ms after the previous beat.
2. **Feature extraction** – for every beat (offsets from the R peak):
   - baseline = average of the PR segment (R−80 ms … R−60 ms)
   - ST level = J point + 60 ms (R+100 ms); ST deviation = ST level − baseline
   - Q wave = lowest point between R−60 ms and R−10 ms
   - Heart rate = 60 / average RR interval
3. **Rules** – each rule is a small class with simple `if` statements on the measurements.
4. **Detected condition** – the most severe positive finding (or Normal Sinus Rhythm).

## Project structure (OOP design)

```
src/ecg/
├── model/                        DATA classes (encapsulation)
│   ├── ECGSignal.java            samples + sampling rate
│   ├── Patient.java
│   ├── BeatFeatures.java         measurements of one heartbeat
│   └── ECGFeatures.java          measurements of the whole recording
├── signal/                       WHERE the ECG comes from
│   ├── SignalSource.java         «interface»
│   ├── SyntheticECGGenerator.java  implements SignalSource (simulator)
│   ├── CsvSignalLoader.java      implements SignalSource (files, incl. "# Key: Value" header)
│   ├── CsvSignalWriter.java      writes signals back out as CSV
│   └── RhythmScenario.java       enum of demo rhythms
├── analysis/                     SIGNAL PROCESSING
│   ├── RPeakDetector.java
│   └── FeatureExtractor.java
├── detection/                    RULE-BASED DIAGNOSIS
│   ├── DetectionRule.java        «abstract» base class
│   ├── BradycardiaRule.java      extends DetectionRule
│   ├── TachycardiaRule.java      extends DetectionRule
│   ├── MyocardialInfarctionRule.java extends DetectionRule
│   ├── AnomalyDetector.java      runs all rules (polymorphism)
│   ├── Finding.java, Severity.java, DiagnosticReport.java
├── tools/
│   └── MockDataGenerator.java    writes the mock ECG folder
└── web/                          HTTP SERVER (localhost web app)
    ├── WebServer.java            built-in JDK HttpServer + REST endpoints
    └── JsonWriter.java           pure-Java JSON serializer
```
Browser assets live under `web/` (`index.html`, `styles.css`, `app.js`, `favicon.svg`).

### OOP concepts used

- **Encapsulation** – all fields are `private`; data is accessed through getters (`ECGSignal`, `Patient` …).
- **Abstraction** – `DetectionRule` (abstract class) and `SignalSource` (interface).
- **Inheritance** – `BradycardiaRule`, `TachycardiaRule`, `MyocardialInfarctionRule` extend `DetectionRule`.
- **Polymorphism** – `AnomalyDetector` loops over a `List<DetectionRule>` and calls `evaluate()`;
  the web server analyses a `SignalSource` without caring whether it is the simulator or an uploaded file.
- **Enums** – `Severity`, `RhythmScenario`.
- **Factory methods** – `Finding.positive(...)` / `Finding.negative(...)`.

### Adding a new anomaly

1. Create `class MyNewRule extends DetectionRule` and implement `evaluate(ECGFeatures)`.
2. Add `rules.add(new MyNewRule());` in the `AnomalyDetector` constructor. Done – the web app shows it automatically.

> ⚠️ Educational project – not a certified medical device. A real diagnosis of MI requires a 12-lead ECG,
> cardiac biomarkers and a physician.

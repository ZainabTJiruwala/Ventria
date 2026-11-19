package ecg.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ecg.detection.AnomalyDetector;
import ecg.detection.DiagnosticReport;
import ecg.model.ECGSignal;
import ecg.model.Patient;
import ecg.signal.CsvSignalLoader;
import ecg.signal.CsvSignalWriter;
import ecg.signal.RhythmScenario;
import ecg.signal.SignalSource;
import ecg.signal.SyntheticECGGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Lightweight HTTP server using Java's built-in HttpServer (no external frameworks).
 * Exposes the exact same OOP AnomalyDetector engine to a browser UI on localhost.
 *
 * Run with:  java -cp out ecg.web.WebServer [port]
 */
public class WebServer {

    private static final int DEFAULT_PORT = 8080;

    private final AnomalyDetector detector = new AnomalyDetector();
    private final Path webRoot;

    public WebServer(Path webRoot) {
        this.webRoot = webRoot;
    }

    public static void main(String[] args) throws IOException {
        int port = parsePort(args);
        Path webRoot = resolveWebRoot();
        WebServer server = new WebServer(webRoot);
        server.start(port);
    }

    public void start(int port) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
        http.createContext("/api/config", this::handleConfig);
        http.createContext("/api/analyze", this::handleAnalyze);
        http.createContext("/api/sample-csv", this::handleSampleCsv);
        http.createContext("/", this::handleStatic);
        http.setExecutor(Executors.newFixedThreadPool(4));
        http.start();

        System.out.println("Ventria web app running at:");
        System.out.println("  http://localhost:" + port);
        System.out.println("Serving static assets from: " + webRoot.toAbsolutePath());
    }

    // ------------------------------------------------------------------ API handlers

    private void handleConfig(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        sendText(exchange, 200, JsonWriter.configToJson(detector.getRules()), "application/json");
    }

    private void handleAnalyze(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                params.putAll(parseForm(body));
            }

            RhythmScenario scenario = parseScenario(params.get("scenario"));
            SignalSource source = buildSignalSource(params, scenario);
            ECGSignal signal = source.getSignal();

            // Uploaded files carry their own patient header; demo recordings use the scenario's patient.
            Patient fallback = (source instanceof CsvSignalLoader csv)
                    ? patientFromMetadata(csv.getMetadata(), params.get("fileName"))
                    : scenario.getDefaultPatient();
            Patient patient = buildPatient(params, fallback);
            DiagnosticReport report = detector.analyze(signal, patient);
            sendText(exchange, 200, JsonWriter.reportToJson(report), "application/json");
        } catch (IllegalArgumentException | IOException ex) {
            String errJson = "{\"error\":" + JsonWriter.quote(ex.getMessage()) + "}";
            sendText(exchange, 400, errJson, "application/json");
        }
    }

    private void handleSampleCsv(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        RhythmScenario scenario = parseScenario(params.get("scenario"));
        ECGSignal signal = new SyntheticECGGenerator(scenario).getSignal();
        String csv = CsvSignalWriter.toCsv(signal, scenario.getDefaultPatient());

        exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"Ventria_" + scenario.name().toLowerCase(Locale.ROOT) + ".csv\"");
        sendText(exchange, 200, csv, "text/csv");
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        String rawPath = exchange.getRequestURI().getPath();
        String relPath = "/".equals(rawPath) ? "index.html" : rawPath.substring(1);

        Path target = webRoot.resolve(relPath).normalize();
        if (!target.startsWith(webRoot) || !Files.isRegularFile(target)) {
            sendText(exchange, 404, "Not Found", "text/plain");
            return;
        }

        byte[] bytes = Files.readAllBytes(target);
        exchange.getResponseHeaders().set("Content-Type", contentType(relPath) + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ------------------------------------------------------------------ helpers

    private SignalSource buildSignalSource(Map<String, String> params, RhythmScenario scenario) {
        String csvText = params.get("csvText");
        if (csvText != null && !csvText.isBlank()) {
            int fs = parseIntOrDefault(params.get("samplingRate"), SyntheticECGGenerator.DEFAULT_SAMPLING_RATE);
            return new CsvSignalLoader(csvText, fs, params.get("fileName"));
        }
        return new SyntheticECGGenerator(scenario);
    }

    /** Values typed in the browser win; anything left blank comes from the fallback patient. */
    private Patient buildPatient(Map<String, String> params, Patient fallback) {
        String id = valueOrDefault(params.get("patientId"), fallback.getPatientId());
        String name = valueOrDefault(params.get("name"), fallback.getName());
        int age = parseIntOrDefault(params.get("age"), fallback.getAge());
        String sex = valueOrDefault(params.get("sex"), fallback.getSex());
        return new Patient(id, name, age, sex);
    }

    /** Patient from the "# Patient ID / Name / Age / Sex" header of an uploaded file. */
    private static Patient patientFromMetadata(Map<String, String> meta, String fileName) {
        String baseName = (fileName == null || fileName.isBlank())
                ? "UPLOAD" : fileName.trim().replaceFirst("\\.[^.]+$", "");
        String id = valueOrDefault(meta.get("patient id"), baseName);
        String name = valueOrDefault(meta.get("name"), "Unidentified patient");
        int age = parseIntOrDefault(meta.get("age"), 0);
        String sex = valueOrDefault(meta.get("sex"), "Unknown");
        return new Patient(id, name, age, sex);
    }

    private static RhythmScenario parseScenario(String name) {
        if (name == null || name.isBlank()) {
            return RhythmScenario.NORMAL;
        }
        try {
            return RhythmScenario.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return RhythmScenario.NORMAL;
        }
    }

    private static String valueOrDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static int parseIntOrDefault(String text, int fallback) {
        if (text == null || text.isBlank()) return fallback;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        return parseForm(raw == null ? "" : raw);
    }

    private static Map<String, String> parseForm(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String text, String mime) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", mime + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css"))  return "text/css";
        if (path.endsWith(".js"))   return "application/javascript";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        return "application/octet-stream";
    }

    private static Path resolveWebRoot() {
        Path direct = Paths.get("web");
        if (Files.isDirectory(direct)) return direct;
        Path nested = Paths.get("ECGAnomalyDetector", "web");
        if (Files.isDirectory(nested)) return nested;
        throw new IllegalStateException("Cannot locate 'web' directory");
    }

    private static int parsePort(String[] args) {
        if (args.length > 0) {
            return parseIntOrDefault(args[0], DEFAULT_PORT);
        }
        String env = System.getenv("PORT");
        return parseIntOrDefault(env, DEFAULT_PORT);
    }
}


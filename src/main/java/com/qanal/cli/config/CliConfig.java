package com.qanal.cli.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists CLI settings in {@code ~/.qanal/config.json}.
 * Fields: apiKey, serverUrl.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CliConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public  static final Path CONFIG_FILE =
            Path.of(System.getProperty("user.home"), ".qanal", "config.json");

    private String apiKey    = "";
    private String serverUrl = "http://localhost:8080";

    // ── Constructors ─────────────────────────────────────────────────────

    public CliConfig() {}

    public CliConfig(String apiKey, String serverUrl) {
        this.apiKey    = apiKey;
        this.serverUrl = serverUrl;
    }

    // ── Persistence ──────────────────────────────────────────────────────

    public static CliConfig load() {
        if (!Files.exists(CONFIG_FILE)) {
            return new CliConfig();
        }
        try {
            return MAPPER.readValue(CONFIG_FILE.toFile(), CliConfig.class);
        } catch (IOException e) {
            System.err.println("Warning: could not read config — " + e.getMessage());
            return new CliConfig();
        }
    }

    public void save() throws IOException {
        Files.createDirectories(CONFIG_FILE.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(CONFIG_FILE.toFile(), this);
    }

    // ── Validation ───────────────────────────────────────────────────────

    public void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("No API key configured. Run: qanal config set-key <key>");
            System.exit(1);
        }
    }

    // ── Getters / Setters ────────────────────────────────────────────────

    public String getApiKey()    { return apiKey; }
    public String getServerUrl() { return serverUrl; }

    public void setApiKey(String apiKey)       { this.apiKey    = apiKey; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
}

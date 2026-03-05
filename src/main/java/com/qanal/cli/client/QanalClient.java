package com.qanal.cli.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qanal.cli.client.dto.OrgDto;
import com.qanal.cli.client.dto.TransferDto;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Thin wrapper over {@link HttpClient} for all Control Plane REST calls.
 * Uses {@code X-API-Key} header for authentication.
 */
public class QanalClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final HttpClient http;
    private final String     baseUrl;
    private final String     apiKey;

    public QanalClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey  = apiKey;
        this.http    = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ── Transfers ────────────────────────────────────────────────────────────

    public TransferDto initiateTransfer(String fileName, long fileSize, String fileChecksum,
                                        String sourceRegion, String targetRegion,
                                        Long bandwidthBps, Integer rttMs) throws IOException, InterruptedException {
        var body = MAPPER.writeValueAsString(new InitiateRequest(
                fileName, fileSize, fileChecksum, sourceRegion, targetRegion, bandwidthBps, rttMs));
        var req = post("/api/v1/transfers", body);
        return send(req, TransferDto.class);
    }

    @SuppressWarnings("unchecked")
    public List<TransferDto> listTransfers(int page, int size) throws IOException, InterruptedException {
        var req = get("/api/v1/transfers?page=" + page + "&size=" + size + "&sort=createdAt,desc");
        // Spring Page response — extract content array
        var node = MAPPER.readTree(sendRaw(req));
        return MAPPER.convertValue(node.get("content"),
                MAPPER.getTypeFactory().constructCollectionType(List.class, TransferDto.class));
    }

    public TransferDto getTransfer(String id) throws IOException, InterruptedException {
        return send(get("/api/v1/transfers/" + id), TransferDto.class);
    }

    public TransferDto cancelTransfer(String id) throws IOException, InterruptedException {
        return send(post("/api/v1/transfers/" + id + "/cancel", ""), TransferDto.class);
    }

    public TransferDto pauseTransfer(String id) throws IOException, InterruptedException {
        return send(post("/api/v1/transfers/" + id + "/pause", ""), TransferDto.class);
    }

    public TransferDto resumeTransfer(String id) throws IOException, InterruptedException {
        return send(post("/api/v1/transfers/" + id + "/resume", ""), TransferDto.class);
    }

    // ── Organizations ────────────────────────────────────────────────────────

    public OrgDto me() throws IOException, InterruptedException {
        return send(get("/api/v1/organizations/me"), OrgDto.class);
    }

    // ── SSE Progress ─────────────────────────────────────────────────────────

    /**
     * Subscribes to the SSE progress stream for a transfer.
     * Calls {@code onEvent} for each {@code data:} line received.
     * Blocks until the stream is closed or an error occurs.
     */
    public void streamProgress(String transferId, Consumer<String> onEvent) throws IOException, InterruptedException {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/transfers/" + transferId + "/progress"))
                .header("X-API-Key", apiKey)
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        http.send(req, HttpResponse.BodyHandlers.ofLines())
                .body()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).strip())
                .filter(json -> !json.isEmpty())
                .forEach(onEvent);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("X-API-Key", apiKey)
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private HttpRequest post(String path, String jsonBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    private <T> T send(HttpRequest req, Class<T> type) throws IOException, InterruptedException {
        String body = sendRaw(req);
        return MAPPER.readValue(body, type);
    }

    private String sendRaw(HttpRequest req) throws IOException, InterruptedException {
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            System.err.println("API error " + resp.statusCode() + ": " + resp.body());
            System.exit(1);
        }
        return resp.body();
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    private record InitiateRequest(
            String  fileName,
            long    fileSize,
            String  fileChecksum,
            String  sourceRegion,
            String  targetRegion,
            Long    estimatedBandwidthBps,
            Integer estimatedRttMs
    ) {}
}

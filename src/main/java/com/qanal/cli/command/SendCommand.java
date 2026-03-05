package com.qanal.cli.command;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qanal.cli.client.QanalClient;
import com.qanal.cli.client.dto.TransferDto;
import com.qanal.cli.config.CliConfig;
import com.qanal.cli.output.ProgressBar;
import com.qanal.cli.output.Printer;
import com.qanal.cli.transfer.FileHasher;
import com.qanal.cli.transfer.QuicSender;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Command(
        name        = "send",
        description = "Send a file to the Qanal network"
)
public class SendCommand implements Runnable {

    @Parameters(index = "0", description = "File to send")
    private Path file;

    @Option(names = {"--to",     "-t"}, description = "Target region (e.g. eu-west)")
    private String targetRegion;

    @Option(names = {"--from",   "-f"}, description = "Source region (e.g. us-east)")
    private String sourceRegion;

    @Option(names = {"--streams","-s"}, description = "Parallel QUIC streams (default: 8)",
            defaultValue = "8")
    private int parallelStreams;

    @Option(names = {"--api-key"},    description = "Override API key from config")
    private String apiKeyOverride;

    @Option(names = {"--url"},        description = "Override server URL from config")
    private String urlOverride;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public void run() {
        if (!Files.isRegularFile(file)) {
            Printer.error("File not found: " + file);
            System.exit(1);
        }

        CliConfig cfg = CliConfig.load();
        String apiKey = apiKeyOverride != null ? apiKeyOverride : cfg.getApiKey();
        String url    = urlOverride    != null ? urlOverride    : cfg.getServerUrl();
        cfg.requireApiKey();

        var client = new QanalClient(url, apiKey);

        try {
            long fileSize = Files.size(file);
            String fileName = file.getFileName().toString();

            // 1 — Compute file checksum (xxHash64)
            Printer.info("Computing checksum for " + Printer.humanBytes(fileSize) + " file...");
            String checksum = FileHasher.hexHash(file);

            // 2 — Register transfer with Control Plane
            Printer.info("Initiating transfer...");
            TransferDto transfer = client.initiateTransfer(
                    fileName, fileSize, checksum, sourceRegion, targetRegion, null, null);

            Printer.ok("Transfer ID: " + Printer.BOLD + transfer.id() + Printer.RESET);
            Printer.line("  Relay  : " + transfer.relayHost() + ":" + transfer.relayQuicPort());
            Printer.line("  Chunks : " + transfer.totalChunks());

            var    bar          = new ProgressBar(fileName, fileSize);
            var    stopped      = new AtomicBoolean(false);
            var    latestPct    = new AtomicInteger(0);
            var    latestBps    = new AtomicLong(0);
            var    latestBytes  = new AtomicLong(0);

            // 3 — Start SSE progress listener in background
            Thread sseThread = Thread.ofVirtual().name("sse").start(() -> {
                try {
                    client.streamProgress(transfer.id(), json -> {
                        try {
                            var node = MAPPER.readTree(json);
                            latestPct.set(node.path("progressPercent").asInt(0));
                            latestBps.set((long) node.path("currentThroughputBps").asDouble(0));
                            latestBytes.set(node.path("bytesTransferred").asLong(0));

                            bar.render(latestPct.get(), latestBytes.get(), latestBps.get());

                            String status = node.path("status").asText("");
                            if ("COMPLETED".equals(status) || "FAILED".equals(status)
                                    || "CANCELLED".equals(status)) {
                                stopped.set(true);
                            }
                        } catch (Exception ignored) {}
                    });
                } catch (Exception e) {
                    if (!stopped.get()) {
                        Printer.warn("Progress stream ended: " + e.getMessage());
                    }
                }
            });

            // 4 — Send file via QUIC
            try (var sender = new QuicSender(transfer.relayHost(), transfer.relayQuicPort())) {
                sender.sendAll(file, transfer.id(), transfer.chunks(), parallelStreams);
            }

            stopped.set(true);
            sseThread.join(5_000); // wait for SSE to catch up (up to 5s)

            bar.complete();

        } catch (Exception e) {
            Printer.error("Transfer failed: " + e.getMessage());
            System.exit(1);
        }
    }
}

package com.qanal.cli.command;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qanal.cli.client.QanalClient;
import com.qanal.cli.client.dto.TransferDto;
import com.qanal.cli.config.CliConfig;
import com.qanal.cli.output.ProgressBar;
import com.qanal.cli.output.Printer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.atomic.AtomicBoolean;

@Command(
        name        = "status",
        description = "Show transfer details. Use --watch to follow live progress."
)
public class StatusCommand implements Runnable {

    @Parameters(index = "0", description = "Transfer ID") String id;

    @Option(names = {"--watch", "-w"}, description = "Follow live SSE progress stream")
    private boolean watch;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public void run() {
        CliConfig cfg = CliConfig.load();
        cfg.requireApiKey();
        var client = new QanalClient(cfg.getServerUrl(), cfg.getApiKey());

        try {
            TransferDto t = client.getTransfer(id);
            printTransfer(t);

            if (watch && !isTerminal(t.status())) {
                System.out.println();
                Printer.info("Watching live progress (Ctrl+C to stop)...");
                var bar     = new ProgressBar(t.fileName(), t.fileSize());
                var stopped = new AtomicBoolean(false);

                client.streamProgress(id, json -> {
                    if (stopped.get()) return;
                    try {
                        var node   = MAPPER.readTree(json);
                        int pct    = node.path("progressPercent").asInt(0);
                        long bytes = node.path("bytesTransferred").asLong(0);
                        double bps = node.path("currentThroughputBps").asDouble(0);
                        bar.render(pct, bytes, bps);

                        String status = node.path("status").asText("");
                        if (isTerminal(status)) {
                            stopped.set(true);
                            if ("COMPLETED".equals(status)) bar.complete();
                            else bar.failed(status);
                        }
                    } catch (Exception ignored) {}
                });
            }

        } catch (Exception e) {
            Printer.error("Failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printTransfer(TransferDto t) {
        System.out.println();
        row("ID",         t.id());
        row("File",       t.fileName());
        row("Status",     Printer.status(t.status()));
        row("Size",       Printer.humanBytes(t.fileSize()));
        row("Progress",   t.progressPercent() + "% (" + t.completedChunks() + "/" + t.totalChunks() + " chunks)");
        row("Transferred",Printer.humanBytes(t.bytesTransferred()));
        if (t.avgThroughputBps() != null) {
            row("Avg speed", Printer.humanBps(t.avgThroughputBps()));
        }
        row("Relay",      t.relayHost() != null ? t.relayHost() + ":" + t.relayQuicPort() : "-");
        row("Created",    t.createdAt()  != null ? t.createdAt().toString()  : "-");
        row("Expires",    t.expiresAt()  != null ? t.expiresAt().toString()  : "-");
        row("Completed",  t.completedAt()!= null ? t.completedAt().toString(): "-");
        System.out.println();
    }

    private static void row(String label, String value) {
        System.out.printf("  %-14s %s%n", label + ":", value != null ? value : "-");
    }

    private static boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
    }
}

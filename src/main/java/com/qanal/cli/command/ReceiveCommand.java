package com.qanal.cli.command;

import com.qanal.cli.client.QanalClient;
import com.qanal.cli.client.dto.TransferDto;
import com.qanal.cli.config.CliConfig;
import com.qanal.cli.output.Printer;
import com.qanal.cli.transfer.QuicDownloader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * {@code qanal receive <transferId>}
 *
 * <p>Downloads the assembled file from the egress DataPlane download port.
 * The transfer must be in COMPLETED status before download is available.
 */
@Command(
        name        = "receive",
        description = "Download a completed transfer from the egress DataPlane."
)
public class ReceiveCommand implements Runnable {

    @Parameters(index = "0", description = "Transfer ID to download")
    private String transferId;

    @Option(names = {"-o", "--output"},
            description = "Output directory or file path (default: current directory)")
    private String outputPath;

    @Override
    public void run() {
        CliConfig cfg = CliConfig.load();
        cfg.requireApiKey();
        var client = new QanalClient(cfg.getServerUrl(), cfg.getApiKey());

        TransferDto transfer;
        try {
            transfer = client.getTransfer(transferId);
        } catch (Exception e) {
            Printer.error("Failed to fetch transfer info: " + e.getMessage());
            return;
        }

        if (!"COMPLETED".equalsIgnoreCase(transfer.status())) {
            Printer.warn("Transfer is not COMPLETED (current status: " + transfer.status() + ").");
            Printer.warn("Wait until the sender's upload finishes, then retry.");
            return;
        }

        String downloadHost = transfer.downloadHost();
        int    downloadPort = transfer.downloadPort();

        if (downloadHost == null || downloadHost.isEmpty()) {
            Printer.error("Transfer has no egress host. It may not have been routed to a download node.");
            return;
        }

        // Determine output file path
        Path destFile = resolveDestFile(transfer.fileName());

        Printer.info("Downloading  " + Printer.BOLD + transfer.fileName() + Printer.RESET
                + "  (" + Printer.humanBytes(transfer.fileSize()) + ")");
        Printer.info("From         " + downloadHost + ":" + downloadPort);
        Printer.info("To           " + destFile.toAbsolutePath());
        System.out.println();

        long start = System.currentTimeMillis();
        long[] lastUpdate = {0L};

        try {
            var downloader = new QuicDownloader(downloadHost, downloadPort);
            long received  = downloader.download(transferId, destFile, bytesReceived -> {
                long now = System.currentTimeMillis();
                if (now - lastUpdate[0] >= 500) {
                    int pct = transfer.fileSize() > 0
                            ? (int) Math.min(100, 100L * bytesReceived / transfer.fileSize())
                            : 0;
                    System.out.print("\r  " + Printer.humanBytes(bytesReceived)
                            + " / " + Printer.humanBytes(transfer.fileSize())
                            + "  [" + pct + "%]   ");
                    lastUpdate[0] = now;
                }
            });

            long elapsed = System.currentTimeMillis() - start;
            double bps   = elapsed > 0 ? (received * 8000.0) / elapsed : 0;

            System.out.println();
            Printer.ok("Download complete: " + destFile.toAbsolutePath());
            Printer.info("Received " + Printer.humanBytes(received)
                    + " in " + (elapsed / 1000.0) + "s  ("
                    + Printer.humanBps(bps) + ")");

        } catch (Exception e) {
            System.out.println();
            Printer.error("Download failed: " + e.getMessage());
        }
    }

    private Path resolveDestFile(String fileName) {
        if (outputPath == null || outputPath.isEmpty()) {
            return Paths.get(fileName != null ? fileName : transferId + ".bin");
        }
        Path p = Paths.get(outputPath);
        if (p.toFile().isDirectory()) {
            return p.resolve(fileName != null ? fileName : transferId + ".bin");
        }
        return p;
    }
}

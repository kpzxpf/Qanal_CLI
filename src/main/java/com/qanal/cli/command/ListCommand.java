package com.qanal.cli.command;

import com.qanal.cli.client.QanalClient;
import com.qanal.cli.client.dto.TransferDto;
import com.qanal.cli.config.CliConfig;
import com.qanal.cli.output.Printer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

@Command(
        name        = "list",
        description = "List transfers for your organization"
)
public class ListCommand implements Runnable {

    @Option(names = {"--page",  "-p"}, description = "Page number (0-based, default 0)",
            defaultValue = "0")
    private int page;

    @Option(names = {"--limit", "-n"}, description = "Results per page (default 20)",
            defaultValue = "20")
    private int limit;

    @Override
    public void run() {
        CliConfig cfg = CliConfig.load();
        cfg.requireApiKey();
        var client = new QanalClient(cfg.getServerUrl(), cfg.getApiKey());

        try {
            List<TransferDto> transfers = client.listTransfers(page, limit);
            if (transfers.isEmpty()) {
                Printer.line("No transfers found.");
                return;
            }

            // Header
            System.out.printf("%n%-12s  %-22s  %-12s  %8s  %5s  %-11s%n",
                    "ID", "FILE", "STATUS", "SIZE", "PROG", "CREATED");
            System.out.println("-".repeat(80));

            for (TransferDto t : transfers) {
                String shortId = t.id() == null ? "-" : t.id().substring(0, Math.min(12, t.id().length()));
                String name    = t.fileName() == null ? "-" : truncate(t.fileName(), 22);
                String status  = Printer.status(t.status() == null ? "-" : t.status());
                String size    = Printer.humanBytes(t.fileSize());
                String prog    = t.progressPercent() + "%";
                String created = Printer.relativeTime(t.createdAt());

                System.out.printf("%-12s  %-22s  %-12s  %8s  %5s  %-11s%n",
                        shortId, name, status, size, prog, created);
            }
            System.out.println();

        } catch (Exception e) {
            Printer.error("Failed to list transfers: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

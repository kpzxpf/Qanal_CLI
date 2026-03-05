package com.qanal.cli.command;

import com.qanal.cli.client.QanalClient;
import com.qanal.cli.client.dto.OrgDto;
import com.qanal.cli.config.CliConfig;
import com.qanal.cli.output.Printer;
import picocli.CommandLine.Command;

@Command(name = "me", description = "Show organization profile and usage")
public class MeCommand implements Runnable {

    @Override
    public void run() {
        CliConfig cfg = CliConfig.load();
        cfg.requireApiKey();
        try {
            OrgDto org = new QanalClient(cfg.getServerUrl(), cfg.getApiKey()).me();
            System.out.println();
            System.out.printf("  %-12s %s%n", "Name:",    org.name());
            System.out.printf("  %-12s %s%n", "Plan:",    Printer.BOLD + org.plan() + Printer.RESET);
            System.out.printf("  %-12s %s%n", "Usage:",   Printer.humanBytes(org.bytesUsedThisMonth()) + " this month");
            System.out.printf("  %-12s %s%n", "Member since:", Printer.relativeTime(org.createdAt()));
            System.out.println();
        } catch (Exception e) {
            Printer.error("Failed: " + e.getMessage());
            System.exit(1);
        }
    }
}

package com.qanal.cli.command;

import com.qanal.cli.client.QanalClient;
import com.qanal.cli.config.CliConfig;
import com.qanal.cli.output.Printer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "resume", description = "Resume a paused transfer")
public class ResumeCommand implements Runnable {

    @Parameters(index = "0", description = "Transfer ID") String id;

    @Override
    public void run() {
        CliConfig cfg = CliConfig.load();
        cfg.requireApiKey();
        try {
            var t = new QanalClient(cfg.getServerUrl(), cfg.getApiKey()).resumeTransfer(id);
            Printer.ok("Transfer " + t.id() + " → " + t.status());
        } catch (Exception e) {
            Printer.error("Failed: " + e.getMessage());
            System.exit(1);
        }
    }
}

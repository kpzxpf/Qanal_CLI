package com.qanal.cli.command;

import com.qanal.cli.config.CliConfig;
import com.qanal.cli.output.Printer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;

@Command(
        name        = "config",
        description = "Manage CLI configuration (~/.qanal/config.json)",
        subcommands = {
                ConfigCommand.SetKey.class,
                ConfigCommand.SetUrl.class,
                ConfigCommand.Show.class
        }
)
public class ConfigCommand implements Runnable {

    @Override
    public void run() {
        new picocli.CommandLine(this).usage(System.out);
    }

    // ── Subcommands ──────────────────────────────────────────────────────────

    @Command(name = "set-key", description = "Set the API key")
    static class SetKey implements Runnable {
        @Parameters(index = "0", description = "API key value") String key;

        @Override
        public void run() {
            CliConfig cfg = CliConfig.load();
            cfg.setApiKey(key);
            try {
                cfg.save();
                Printer.ok("API key saved to " + CliConfig.CONFIG_FILE);
            } catch (IOException e) {
                Printer.error("Failed to save config: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    @Command(name = "set-url", description = "Set the Control Plane URL")
    static class SetUrl implements Runnable {
        @Parameters(index = "0", description = "Base URL, e.g. http://localhost:8080") String url;

        @Override
        public void run() {
            CliConfig cfg = CliConfig.load();
            cfg.setServerUrl(url);
            try {
                cfg.save();
                Printer.ok("Server URL saved: " + url);
            } catch (IOException e) {
                Printer.error("Failed to save config: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    @Command(name = "show", description = "Show current configuration")
    static class Show implements Runnable {
        @Override
        public void run() {
            CliConfig cfg = CliConfig.load();
            Printer.line("Config file : " + CliConfig.CONFIG_FILE);
            Printer.line("Server URL  : " + cfg.getServerUrl());
            String key = cfg.getApiKey();
            String masked = key.length() > 8
                    ? key.substring(0, 4) + "****" + key.substring(key.length() - 4)
                    : "****";
            Printer.line("API Key     : " + (key.isBlank() ? "(not set)" : masked));
        }
    }
}

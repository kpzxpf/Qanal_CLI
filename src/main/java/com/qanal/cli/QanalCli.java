package com.qanal.cli;

import com.qanal.cli.command.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name            = "qanal",
        mixinStandardHelpOptions = true,
        version         = "qanal 0.1.0",
        description     = "Qanal — high-speed global file transfer CLI",
        subcommands     = {
                SendCommand.class,
                ReceiveCommand.class,
                ListCommand.class,
                StatusCommand.class,
                CancelCommand.class,
                PauseCommand.class,
                ResumeCommand.class,
                MeCommand.class,
                ConfigCommand.class
        }
)
public class QanalCli implements Runnable {

    public static void main(String[] args) {
        int exit = new CommandLine(new QanalCli())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exit);
    }

    @Override
    public void run() {
        // No subcommand → show help
        new CommandLine(this).usage(System.out);
    }
}

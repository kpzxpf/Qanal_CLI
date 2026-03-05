package com.qanal.cli.output;

/**
 * ANSI-coloured output helpers.
 * Colours are disabled automatically when stdout is not a TTY.
 */
public final class Printer {

    private static final boolean ANSI = isAnsiSupported();

    // ANSI codes
    public static final String RESET  = ansi("\033[0m");
    public static final String BOLD   = ansi("\033[1m");
    public static final String GREEN  = ansi("\033[32m");
    public static final String YELLOW = ansi("\033[33m");
    public static final String CYAN   = ansi("\033[36m");
    public static final String RED    = ansi("\033[31m");
    public static final String DIM    = ansi("\033[2m");

    private Printer() {}

    public static void ok(String msg)      { System.out.println(GREEN  + "✓ " + RESET + msg); }
    public static void warn(String msg)    { System.out.println(YELLOW + "! " + RESET + msg); }
    public static void error(String msg)   { System.err.println(RED    + "✗ " + RESET + msg); }
    public static void info(String msg)    { System.out.println(CYAN   + "→ " + RESET + msg); }
    public static void line(String msg)    { System.out.println(msg); }

    public static String status(String s) {
        return switch (s) {
            case "COMPLETED"  -> GREEN  + s + RESET;
            case "FAILED"     -> RED    + s + RESET;
            case "CANCELLED"  -> RED    + s + RESET;
            case "IN_PROGRESS"-> CYAN   + s + RESET;
            case "PAUSED"     -> YELLOW + s + RESET;
            default           -> s;
        };
    }

    /** Human-readable byte size: 1.4 GB, 320 MB, 512 KB, 42 B */
    public static String humanBytes(long bytes) {
        if (bytes >= 1_000_000_000L) return "%.1f GB".formatted(bytes / 1e9);
        if (bytes >= 1_000_000L)     return "%.1f MB".formatted(bytes / 1e6);
        if (bytes >= 1_000L)         return "%.1f KB".formatted(bytes / 1e3);
        return bytes + " B";
    }

    /** Human-readable throughput in bits: 1.2 Gbps, 450 Mbps */
    public static String humanBps(double bps) {
        if (bps >= 1_000_000_000) return "%.1f Gbps".formatted(bps / 1e9);
        if (bps >= 1_000_000)     return "%.1f Mbps".formatted(bps / 1e6);
        if (bps >= 1_000)         return "%.1f Kbps".formatted(bps / 1e3);
        return "%.0f bps".formatted(bps);
    }

    /** Relative time: "2h ago", "just now" */
    public static String relativeTime(java.time.OffsetDateTime dt) {
        if (dt == null) return "-";
        long secs = java.time.Duration.between(dt, java.time.OffsetDateTime.now()).toSeconds();
        if (secs < 60)  return "just now";
        if (secs < 3600) return (secs / 60) + "m ago";
        if (secs < 86400) return (secs / 3600) + "h ago";
        return (secs / 86400) + "d ago";
    }

    private static boolean isAnsiSupported() {
        // Disable ANSI when not writing to a terminal or on unsupported environments
        String term = System.getenv("TERM");
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        // Windows Terminal supports ANSI; legacy cmd.exe may not
        if (isWindows && System.getenv("WT_SESSION") == null &&
                System.getenv("ANSICON") == null) {
            return false;
        }
        return System.console() != null || "true".equals(System.getenv("FORCE_COLOR"));
    }

    private static String ansi(String code) {
        return ANSI ? code : "";
    }
}

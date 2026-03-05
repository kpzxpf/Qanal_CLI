package com.qanal.cli.output;

import static com.qanal.cli.output.Printer.*;

/**
 * Renders an in-place ANSI progress bar on stdout.
 *
 * <pre>
 * backup.tar.gz  [████████████░░░░░░░░]  60%  1.1 Gbps  ETA 8s  (8.4 GB / 14.0 GB)
 * </pre>
 *
 * Call {@link #render(int, long, double)} from any thread; uses {@code \r} to
 * overwrite the current line so no extra newlines are emitted.
 * Call {@link #complete()} when the transfer finishes.
 */
public final class ProgressBar {

    private static final int BAR_WIDTH = 20;

    private final String fileName;
    private final long   totalBytes;
    private long         startedAt;

    public ProgressBar(String fileName, long totalBytes) {
        this.fileName   = truncate(fileName, 20);
        this.totalBytes = totalBytes;
        this.startedAt  = System.currentTimeMillis();
    }

    /**
     * Re-renders the progress bar on the current line.
     *
     * @param percent      0–100
     * @param bytesRx      bytes transferred so far
     * @param throughputBps current throughput in bits/sec
     */
    public void render(int percent, long bytesRx, double throughputBps) {
        percent = Math.max(0, Math.min(100, percent));

        int    filled = percent * BAR_WIDTH / 100;
        String bar    = "█".repeat(filled) + "░".repeat(BAR_WIDTH - filled);

        long   etaSecs = throughputBps > 0
                ? (long) ((totalBytes - bytesRx) * 8.0 / throughputBps)
                : -1;
        String eta = etaSecs < 0 ? "?" : etaSecs + "s";

        String line = "\r" + BOLD + fileName + RESET + "  "
                + CYAN + "[" + bar + "]" + RESET
                + "  " + BOLD + "%3d%%".formatted(percent) + RESET
                + "  " + GREEN + humanBps(throughputBps) + RESET
                + "  ETA " + eta
                + DIM + "  (" + humanBytes(bytesRx) + " / " + humanBytes(totalBytes) + ")" + RESET
                + "   ";   // trailing spaces to overwrite previous longer line

        System.out.print(line);
        System.out.flush();
    }

    public void complete() {
        long   elapsed = (System.currentTimeMillis() - startedAt) / 1000;
        double avgBps  = elapsed > 0 ? (totalBytes * 8.0 / elapsed) : 0;

        System.out.println();   // end the progress line
        ok(fileName + "  " + humanBytes(totalBytes)
                + "  avg " + humanBps(avgBps)
                + "  " + elapsed + "s");
    }

    public void failed(String reason) {
        System.out.println();
        error(fileName + " — FAILED: " + reason);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

package com.radiance.v2.bridge;

import com.radiance.client.RadianceClient;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Java-side companion to engine::boot_trace. Writes one line per event to
 * {@code radiance/logs/radiance_boot.log}, flushed immediately so the file
 * survives a JVM crash, an EXCEPTION_ACCESS_VIOLATION inside core.dll, or a
 * System.exit(-1) called from anywhere. Truncated on first call per process.
 *
 * <p>This is intentionally minimal: no formatter chains, no log levels, no
 * background threads. Each event() call opens, writes, flushes, closes.
 *
 * <p>Tags follow the convention from {@code resilient-booting-lighthouse.md}
 * Phase 0: V2_INIT_CALLED, V2_INIT_OK, V2_INIT_THREW, V2_TICK_FIRST_OK,
 * V2_TICK_FAIL, etc.
 */
public final class BootTrace {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Object LOCK = new Object();
    private static volatile Path logFile = null;
    private static volatile boolean truncated = false;

    private BootTrace() {}

    /**
     * Emit a tagged event with no detail payload.
     */
    public static void event(String tag) {
        event(tag, null);
    }

    /**
     * Emit a tagged event with optional detail. Both written to the boot log.
     */
    public static void event(String tag, String detail) {
        String line = TS.format(LocalDateTime.now()) + " [BOOT] " + tag
                + (detail != null && !detail.isEmpty() ? " - " + detail : "");

        // Mirror to stderr so users without log file access still see boot progress.
        System.err.println(line);

        synchronized (LOCK) {
            try {
                Path file = resolveLogFile();
                if (file == null) return;
                if (!truncated) {
                    Files.writeString(file,
                            TS.format(LocalDateTime.now()) + " [BOOT] === Java boot trace start ===\n",
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                    truncated = true;
                }
                try (BufferedWriter w = Files.newBufferedWriter(file,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND)) {
                    w.write(line);
                    w.write('\n');
                    // BufferedWriter.close() flushes — but be explicit so a
                    // mid-write crash still commits the partial line.
                    w.flush();
                }
            } catch (IOException ignored) {
                // Boot trace failures must not break the boot itself.
            }
        }
    }

    /**
     * Convenience: log an exception with its message and stack-trace top frame.
     */
    public static void exception(String tag, Throwable t) {
        if (t == null) {
            event(tag);
            return;
        }
        StackTraceElement[] st = t.getStackTrace();
        String topFrame = st.length > 0 ? st[0].toString() : "<no stack>";
        event(tag, t.getClass().getSimpleName() + ": " + t.getMessage() + " @ " + topFrame);
    }

    private static Path resolveLogFile() {
        Path file = logFile;
        if (file != null) return file;
        if (RadianceClient.radianceDir == null) return null;
        synchronized (LOCK) {
            if (logFile != null) return logFile;
            Path logsDir = RadianceClient.radianceDir.resolve("logs");
            try {
                Files.createDirectories(logsDir);
            } catch (IOException e) {
                return null;
            }
            logFile = logsDir.resolve("radiance_boot.log");
            return logFile;
        }
    }
}

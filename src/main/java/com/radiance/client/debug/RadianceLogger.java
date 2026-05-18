package com.radiance.client.debug;

import com.radiance.client.RadianceClient;
import com.radiance.client.option.Options;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Java-side structured logger. Writes to radiance/logs/ when enabled.
 * Mirrors the C++ RadianceLogger format.
 */
public final class RadianceLogger {

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static PrintWriter writer;
    private static Path logFile;
    private static volatile boolean enabled = false;

    private RadianceLogger() {}

    public static boolean isEnabled() { return enabled; }

    public static void setEnabled(boolean enable) {
        if (enable && !enabled) {
            try {
                Path logsDir = RadianceClient.radianceDir.resolve("logs");
                Files.createDirectories(logsDir);
                String name = "radiance_java_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneId.systemDefault()).format(Instant.now()) + ".log";
                logFile = logsDir.resolve(name);
                writer = new PrintWriter(Files.newBufferedWriter(logFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND));
                enabled = true;
                log("Logger", "INFO", "Java-side logging started: " + logFile);
                RadianceClient.LOGGER.info("[RadianceLogger] Opened " + logFile);
            } catch (IOException e) {
                RadianceClient.LOGGER.error("[RadianceLogger] Failed to open log: " + e.getMessage());
            }
        } else if (!enable && enabled) {
            log("Logger", "INFO", "Java-side logging stopped");
            enabled = false;
            if (writer != null) { writer.flush(); writer.close(); writer = null; }
        }
    }

    public static void log(String component, String level, String message) {
        if (!enabled || writer == null) return;
        writer.printf("[%s] [%s] [%s] %s%n", FMT.format(Instant.now()), component, level, message);
        // Auto-flush on WARN/ERROR
        if ("WARN".equals(level) || "ERROR".equals(level)) writer.flush();
    }

    public static void logSettingsChange(String name, Object value) {
        if (!enabled) return;
        log("Settings", "INFO", name + "=" + value);
    }

    public static void logMaterialDirty(String context) {
        if (!enabled) return;
        log("Material", "INFO", "dirty: " + context);
    }

    public static void logTextureEvent(String event) {
        if (!enabled) return;
        log("Texture", "INFO", event);
    }

    public static void flush() {
        if (writer != null) writer.flush();
    }

    public static void shutdown() {
        if (writer != null) {
            log("Logger", "INFO", "Shutdown");
            writer.flush();
            writer.close();
            writer = null;
        }
        enabled = false;
    }
}

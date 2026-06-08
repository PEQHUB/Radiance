package com.radiance.client.debug;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import org.slf4j.Logger;

public final class RadianceBuildInfo {
    private static final Gson GSON = new Gson();
    private static final Properties BUILD = loadBuildProperties();
    private static volatile JarIdentity jarIdentity;

    private RadianceBuildInfo() {
    }

    public static void logStartup(Logger logger) {
        JsonObject info = buildInfo();
        logger.info("[Radiance] Build {} commit={} jarSha256={}",
            string(info, "version"), string(info, "commit"), string(info, "jarSha256"));
    }

    public static String buildInfoJson() {
        return GSON.toJson(buildInfo());
    }

    public static JsonObject buildInfo() {
        JarIdentity identity = jarIdentity();
        JsonObject json = new JsonObject();
        json.addProperty("ok", true);
        json.addProperty("modId", "radiance");
        json.addProperty("version", property("version"));
        json.addProperty("minecraftVersion", property("minecraftVersion"));
        json.addProperty("loaderVersion", property("loaderVersion"));
        json.addProperty("platform", property("platform"));
        json.addProperty("commit", property("commit"));
        json.addProperty("jarPath", identity.path());
        json.addProperty("jarSha256", identity.sha256());
        json.addProperty("jarSizeBytes", identity.sizeBytes());
        json.addProperty("jarLastModifiedMs", identity.lastModifiedMs());
        json.addProperty("jarHashError", identity.error());
        return json;
    }

    private static JarIdentity jarIdentity() {
        JarIdentity local = jarIdentity;
        if (local != null) {
            return local;
        }
        local = computeJarIdentity();
        jarIdentity = local;
        return local;
    }

    private static JarIdentity computeJarIdentity() {
        try {
            URI uri = RadianceBuildInfo.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(uri);
            if (!Files.isRegularFile(path)) {
                return new JarIdentity(path.toString(), "not-a-jar", Files.exists(path) ? 0L : -1L, 0L,
                    "code source is not a regular file");
            }
            return new JarIdentity(path.toString(), sha256(path), Files.size(path),
                Files.getLastModifiedTime(path).toMillis(), "");
        } catch (Exception e) {
            return new JarIdentity("", "unavailable", -1L, 0L, e.getClass().getSimpleName() + ": "
                + e.getMessage());
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest()).toUpperCase();
    }

    private static Properties loadBuildProperties() {
        Properties props = new Properties();
        try (InputStream input = RadianceBuildInfo.class.getResourceAsStream("/radiance-build.properties")) {
            if (input != null) {
                props.load(input);
            }
        } catch (IOException ignored) {
        }
        return props;
    }

    private static String property(String key) {
        return BUILD.getProperty(key, "unknown");
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsString() : "unknown";
    }

    private record JarIdentity(String path, String sha256, long sizeBytes, long lastModifiedMs, String error) {
    }
}

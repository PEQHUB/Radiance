package com.radiance.client.texture.packindex;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radiance.client.proxy.vulkan.TextureArrayBridgeV4;
import com.radiance.client.texture.v4.TextureLoadGeneration;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Java capture surface for the TexV5 native pack index.
 *
 * P1 keeps this observe-only: it describes the active resource stack and vanilla
 * ResourceManager winners, then publishes that generation snapshot to native
 * code for status and later native-side indexing.
 */
public final class PackStackSnapshot {
    private static final int SAMPLE_LIMIT = 96;
    private static final Object LOCK = new Object();

    private static String latestJson = unavailableJson("not_captured");
    private static String latestDiffJson = unavailableJson("not_captured");

    private PackStackSnapshot() {}

    public static JsonObject captureAndPublish(ResourceManager resourceManager, long generation) {
        long started = System.nanoTime();
        JsonObject root = capture(resourceManager, generation, started);
        long captureMillis = elapsedMillis(started);
        root.addProperty("captureMillis", captureMillis);

        JsonObject totals = root.getAsJsonObject("totals");
        int packCount = totals == null ? 0 : intProperty(totals, "packRecords");
        int resourceCount = totals == null ? 0 : intProperty(totals, "winnerResources");
        int ruleFileCount = totals == null ? 0 : intProperty(totals, "ruleFiles");
        int sidecarCount = totals == null ? 0 : intProperty(totals, "sidecarResources");
        String packStackHash = stringProperty(root, "packStackHash");

        boolean nativeAccepted = TextureArrayBridgeV4.nativeSubmitPackStackSnapshotV1(
            generation, root.toString(), packCount, resourceCount, ruleFileCount,
            sidecarCount, packStackHash, captureMillis);
        root.addProperty("nativeAccepted", nativeAccepted);

        JsonObject diff = diffStatus(root, nativeAccepted);
        synchronized (LOCK) {
            latestJson = root.toString();
            latestDiffJson = diff.toString();
        }
        return root;
    }

    public static String latestJson() {
        synchronized (LOCK) {
            return latestJson;
        }
    }

    public static String diffAgainstVanillaJson() {
        synchronized (LOCK) {
            return latestDiffJson;
        }
    }

    private static JsonObject capture(ResourceManager resourceManager, long generation, long startedNanos) {
        JsonObject root = new JsonObject();
        root.addProperty("ok", true);
        root.addProperty("schema", "radser_pack_stack_snapshot_v1");
        root.addProperty("generation", generation);
        root.addProperty("activeTextureGeneration", TextureLoadGeneration.active());
        root.addProperty("resourceManagerClass",
            resourceManager == null ? "missing" : resourceManager.getClass().getName());

        Path runDirectory = runDirectory();
        root.addProperty("runDirectory", runDirectory.toAbsolutePath().toString());
        ActivePackSelection activeSelection = readActivePackSelection(runDirectory);
        root.add("activeSelection", activeSelection.toJson());

        JsonArray namespaces = namespaces(resourceManager);
        root.add("namespaces", namespaces);

        JsonArray packs = scanPackRecords(runDirectory, activeSelection);
        root.add("packs", packs);

        WinnerSummary winners = collectWinnerSummary(resourceManager);
        root.add("winnerSummary", winners.toJson());
        root.add("packWinnerCounts", winnerCountsByPack(winners.winnerPackCounts));

        JsonObject totals = new JsonObject();
        totals.addProperty("packRecords", packs.size());
        totals.addProperty("namespaces", namespaces.size());
        totals.addProperty("winnerResources", winners.resourceCount);
        totals.addProperty("sidecarResources", winners.sidecarCount);
        totals.addProperty("ruleFiles", winners.ruleFileCount);
        totals.addProperty("uniqueWinnerPacks", winners.winnerPackCounts.size());
        root.add("totals", totals);

        root.addProperty("packStackHash", stableHash(hashInputs(root)));
        root.addProperty("captureStartedNanos", startedNanos);
        return root;
    }

    private static WinnerSummary collectWinnerSummary(ResourceManager resourceManager) {
        WinnerSummary summary = new WinnerSummary();
        if (resourceManager == null) {
            summary.error = "missing_resource_manager";
            return summary;
        }
        collectRoot(resourceManager, "textures", summary, PackStackSnapshot::isTextureInteresting);
        collectRoot(resourceManager, "optifine/ctm", summary, id -> id.getPath().endsWith(".properties"));
        collectRoot(resourceManager, "mcpatcher/ctm", summary, id -> id.getPath().endsWith(".properties"));
        collectRoot(resourceManager, "optifine", summary, id -> id.getPath().endsWith(".properties"));
        collectRoot(resourceManager, "mcpatcher", summary, id -> id.getPath().endsWith(".properties"));
        return summary;
    }

    private static void collectRoot(ResourceManager resourceManager, String root, WinnerSummary summary,
        java.util.function.Predicate<Identifier> predicate) {
        Map<Identifier, Resource> resources;
        try {
            resources = resourceManager.findResources(root, predicate);
        } catch (RuntimeException e) {
            summary.errors.add(root + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }
        resources.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> summary.add(root, entry.getKey(), entry.getValue()));
    }

    private static boolean isTextureInteresting(Identifier id) {
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return path.endsWith(".png") || path.endsWith(".png.mcmeta");
    }

    private static JsonArray namespaces(ResourceManager resourceManager) {
        JsonArray array = new JsonArray();
        if (resourceManager == null) {
            return array;
        }
        try {
            ArrayList<String> namespaces = new ArrayList<>(resourceManager.getAllNamespaces());
            namespaces.sort(String::compareTo);
            for (String namespace : namespaces) {
                array.add(namespace);
            }
        } catch (RuntimeException ignored) {
        }
        return array;
    }

    private static JsonArray scanPackRecords(Path runDirectory, ActivePackSelection activeSelection) {
        JsonArray packs = new JsonArray();
        Set<String> recordedFileEntries = new HashSet<>();
        Path resourcePacks = runDirectory.resolve("resourcepacks");
        if (Files.isDirectory(resourcePacks)) {
            try (var stream = Files.list(resourcePacks)) {
                List<Path> candidates = stream
                    .filter(path -> Files.isDirectory(path) || Files.isRegularFile(path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
                for (Path candidate : candidates) {
                    String fileName = candidate.getFileName() == null
                        ? candidate.toString()
                        : candidate.getFileName().toString();
                    JsonObject pack = packRecord(candidate, fileName, activeSelection);
                    packs.add(pack);
                    recordedFileEntries.add("file/" + fileName);
                }
            } catch (IOException e) {
                JsonObject error = new JsonObject();
                error.addProperty("sourceKind", "resourcepacks_directory");
                error.addProperty("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                packs.add(error);
            }
        }
        for (int i = 0; i < activeSelection.resourcePacks.size(); i++) {
            String entry = activeSelection.resourcePacks.get(i);
            if (recordedFileEntries.contains(entry)) {
                continue;
            }
            JsonObject virtualPack = new JsonObject();
            virtualPack.addProperty("id", entry);
            virtualPack.addProperty("optionEntry", entry);
            virtualPack.addProperty("sourceKind", "virtual_java_only");
            virtualPack.addProperty("active", true);
            virtualPack.addProperty("optionOrder", i);
            virtualPack.addProperty("nativePrecedence", i);
            packs.add(virtualPack);
        }
        return packs;
    }

    private static JsonObject packRecord(Path path, String fileName, ActivePackSelection activeSelection) {
        JsonObject pack = new JsonObject();
        pack.addProperty("id", "file/" + fileName);
        pack.addProperty("fileName", fileName);
        pack.addProperty("path", path.toAbsolutePath().toString());
        pack.addProperty("sourceKind", sourceKind(path));
        pack.addProperty("active", activeSelection.activeFilePackNames.contains(fileName));
        pack.addProperty("optionEntry", "file/" + fileName);
        pack.addProperty("optionOrder", activeSelection.activeOrder(fileName));
        pack.addProperty("nativePrecedence", activeSelection.activeOrder(fileName));
        pack.addProperty("incompatibleSelected", activeSelection.incompatibleFilePackNames.contains(fileName));
        try {
            pack.addProperty("sizeBytes", Files.isRegularFile(path) ? Files.size(path) : 0L);
            pack.addProperty("lastModifiedMillis", Files.getLastModifiedTime(path).toMillis());
        } catch (IOException e) {
            pack.addProperty("statError", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return pack;
    }

    private static String sourceKind(Path path) {
        if (Files.isDirectory(path)) {
            return "directory";
        }
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) {
            return "zip";
        }
        return "file";
    }

    private static JsonObject winnerCountsByPack(Map<String, Integer> counts) {
        JsonObject json = new JsonObject();
        counts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> json.addProperty(entry.getKey(), entry.getValue()));
        return json;
    }

    private static JsonObject diffStatus(JsonObject snapshot, boolean nativeAccepted) {
        JsonObject diff = new JsonObject();
        diff.addProperty("ok", false);
        diff.addProperty("schema", "radser_pack_index_diff_v1");
        diff.addProperty("generation", longProperty(snapshot, "generation"));
        diff.addProperty("nativeAcceptedSnapshot", nativeAccepted);
        diff.addProperty("diffReady", false);
        diff.addProperty("reason", "native_pack_index_tables_not_built_yet");
        diff.addProperty("vanillaTruthCaptured", true);
        diff.add("vanillaTotals", snapshot.getAsJsonObject("totals"));
        return diff;
    }

    private static Path runDirectory() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.runDirectory != null) {
                return client.runDirectory.toPath();
            }
        } catch (Throwable ignored) {
        }
        return Path.of(".");
    }

    private static ActivePackSelection readActivePackSelection(Path runDirectory) {
        Path options = runDirectory.resolve("options.txt");
        if (!Files.isRegularFile(options)) {
            return new ActivePackSelection(List.of(), List.of(), "missing_options");
        }

        List<String> resourcePacks = List.of();
        List<String> incompatible = List.of();
        try {
            for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
                if (line.startsWith("resourcePacks:")) {
                    resourcePacks = parseStringArrayOption(line.substring("resourcePacks:".length()));
                } else if (line.startsWith("incompatibleResourcePacks:")) {
                    incompatible = parseStringArrayOption(line.substring("incompatibleResourcePacks:".length()));
                }
            }
            return new ActivePackSelection(resourcePacks, incompatible, "options.txt");
        } catch (Exception e) {
            return new ActivePackSelection(resourcePacks, incompatible,
                e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static List<String> parseStringArrayOption(String raw) {
        ArrayList<String> values = new ArrayList<>();
        try {
            JsonElement parsed = JsonParser.parseString(raw.trim());
            if (!parsed.isJsonArray()) {
                return values;
            }
            for (JsonElement element : parsed.getAsJsonArray()) {
                if (element.isJsonPrimitive()) {
                    values.add(element.getAsString());
                }
            }
        } catch (RuntimeException ignored) {
        }
        return values;
    }

    private static String filePackName(String optionEntry) {
        if (optionEntry == null) return "";
        if (optionEntry.startsWith("file/")) return optionEntry.substring("file/".length());
        return "";
    }

    private static List<String> hashInputs(JsonObject snapshot) {
        ArrayList<String> inputs = new ArrayList<>();
        inputs.add("schema=" + stringProperty(snapshot, "schema"));
        inputs.add("resourceManager=" + stringProperty(snapshot, "resourceManagerClass"));
        JsonArray namespaces = snapshot.getAsJsonArray("namespaces");
        if (namespaces != null) {
            for (JsonElement element : namespaces) {
                inputs.add("namespace=" + element.getAsString());
            }
        }
        JsonObject activeSelection = snapshot.getAsJsonObject("activeSelection");
        if (activeSelection != null) {
            inputs.add("activeSelection=" + activeSelection);
        }
        JsonArray packs = snapshot.getAsJsonArray("packs");
        if (packs != null) {
            for (JsonElement element : packs) {
                inputs.add("pack=" + element);
            }
        }
        return inputs;
    }

    private static String stableHash(List<String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            byte[] bytes = digest.digest();
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private static String unavailableJson(String reason) {
        JsonObject json = new JsonObject();
        json.addProperty("ok", false);
        json.addProperty("reason", reason);
        return json.toString();
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private static int intProperty(JsonObject object, String name) {
        if (object == null || !object.has(name) || !object.get(name).isJsonPrimitive()) {
            return 0;
        }
        try {
            return object.get(name).getAsInt();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static long longProperty(JsonObject object, String name) {
        if (object == null || !object.has(name) || !object.get(name).isJsonPrimitive()) {
            return 0L;
        }
        try {
            return object.get(name).getAsLong();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static String stringProperty(JsonObject object, String name) {
        if (object == null || !object.has(name) || !object.get(name).isJsonPrimitive()) {
            return "";
        }
        try {
            return object.get(name).getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static final class ActivePackSelection {
        private final List<String> resourcePacks;
        private final List<String> incompatibleResourcePacks;
        private final Set<String> activeFilePackNames = new HashSet<>();
        private final Map<String, Integer> activeFilePackOrder = new HashMap<>();
        private final Set<String> incompatibleFilePackNames = new HashSet<>();
        private final String source;

        ActivePackSelection(List<String> resourcePacks, List<String> incompatibleResourcePacks, String source) {
            this.resourcePacks = resourcePacks;
            this.incompatibleResourcePacks = incompatibleResourcePacks;
            this.source = source;
            for (int i = 0; i < resourcePacks.size(); i++) {
                String fileName = filePackName(resourcePacks.get(i));
                if (!fileName.isEmpty()) {
                    activeFilePackNames.add(fileName);
                    activeFilePackOrder.putIfAbsent(fileName, i);
                }
            }
            for (String entry : incompatibleResourcePacks) {
                String fileName = filePackName(entry);
                if (!fileName.isEmpty()) {
                    incompatibleFilePackNames.add(fileName);
                }
            }
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("source", source);
            json.add("resourcePacks", stringArray(resourcePacks));
            json.add("incompatibleResourcePacks", stringArray(incompatibleResourcePacks));
            JsonArray ordered = new JsonArray();
            activeFilePackOrder.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> {
                    JsonObject item = new JsonObject();
                    item.addProperty("name", entry.getKey());
                    item.addProperty("optionOrder", entry.getValue());
                    item.addProperty("nativePrecedence", entry.getValue());
                    ordered.add(item);
                });
            json.add("activeFilePackOrder", ordered);
            return json;
        }

        int activeOrder(String fileName) {
            return activeFilePackOrder.getOrDefault(fileName, -1);
        }

        private static JsonArray stringArray(List<String> values) {
            JsonArray array = new JsonArray();
            for (String value : values) {
                array.add(value);
            }
            return array;
        }
    }

    private static final class WinnerSummary {
        private final Set<String> seen = new HashSet<>();
        private final Map<String, Integer> winnerPackCounts = new HashMap<>();
        private final JsonArray samples = new JsonArray();
        private final JsonArray errors = new JsonArray();
        private String error = "";
        private int resourceCount;
        private int sidecarCount;
        private int ruleFileCount;
        private int pngCount;
        private int mcmetaCount;

        void add(String root, Identifier id, Resource resource) {
            String key = id.toString();
            if (!seen.add(key)) {
                return;
            }
            String path = id.getPath().toLowerCase(Locale.ROOT);
            String packId = safePackId(resource);
            resourceCount++;
            winnerPackCounts.merge(packId, 1, Integer::sum);
            if (path.endsWith(".png")) {
                pngCount++;
                if (isSidecarPath(path)) {
                    sidecarCount++;
                }
            } else if (path.endsWith(".png.mcmeta")) {
                mcmetaCount++;
            } else if (path.endsWith(".properties")) {
                ruleFileCount++;
            }
            if (samples.size() < SAMPLE_LIMIT) {
                JsonObject sample = new JsonObject();
                sample.addProperty("root", root);
                sample.addProperty("resource", key);
                sample.addProperty("winnerPackId", packId);
                sample.addProperty("sidecar", isSidecarPath(path));
                samples.add(sample);
            }
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("resourceCount", resourceCount);
            json.addProperty("pngCount", pngCount);
            json.addProperty("mcmetaCount", mcmetaCount);
            json.addProperty("sidecarCount", sidecarCount);
            json.addProperty("ruleFileCount", ruleFileCount);
            if (!error.isBlank()) {
                json.addProperty("error", error);
            }
            json.add("samples", samples);
            json.add("errors", errors);
            return json;
        }

        private static boolean isSidecarPath(String path) {
            return path.endsWith("_n.png")
                || path.endsWith("_normal.png")
                || path.endsWith("_norm.png")
                || path.endsWith("_s.png")
                || path.endsWith("_spec.png")
                || path.endsWith("_specular.png")
                || path.endsWith("_e.png")
                || path.endsWith("_emissive.png")
                || path.endsWith("_f.png")
                || path.endsWith("_roughness.png")
                || path.endsWith("_rough.png")
                || path.endsWith("_metallic.png")
                || path.endsWith("_metalness.png")
                || path.endsWith("_height.png")
                || path.endsWith("_displacement.png")
                || path.endsWith("_disp.png")
                || path.endsWith("_ao.png")
                || path.endsWith("_ambientocclusion.png")
                || path.endsWith("_ambient_occlusion.png");
        }

        private static String safePackId(Resource resource) {
            if (resource == null) {
                return "unknown";
            }
            try {
                ResourcePack pack = resource.getPack();
                return pack == null ? "unknown" : pack.getId();
            } catch (RuntimeException e) {
                return "unknown";
            }
        }
    }
}

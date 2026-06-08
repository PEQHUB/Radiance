package com.radiance.client.texture.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.radiance.client.autopbr.AutoPbrTextureCatalog;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.texture.compat.ResourcePackCompatCtmTiles;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.util.Identifier;

/**
 * Java-side material-id registry.
 *
 * <p>The first implementation deliberately wraps the existing sprite-array
 * renderer: vanilla material ids equal sprite ids, while OptiFine CTM assets
 * are recorded as virtual materials with explicit fallback handles. That makes
 * the material universe observable and gives native code a stable ABI without
 * exposing array layers as the long-term content identity.</p>
 */
public final class ResourceMaterialRegistry {
    public static final int MATERIAL_ENTRY_SIZE = 80;
    public static final int MATERIAL_MAX_ENTRIES = 65536;
    public static final int MATERIAL_FLAG_VALID = 1 << 0;
    public static final int MATERIAL_FLAG_VANILLA_SPRITE = 1 << 1;
    public static final int MATERIAL_FLAG_COMPAT_VIRTUAL = 1 << 2;
    public static final int MATERIAL_FLAG_GPU_RESIDENT = 1 << 3;
    public static final int MATERIAL_FLAG_PENDING_RESIDENCY = 1 << 4;
    public static final int MATERIAL_FLAG_FALLBACK = 1 << 5;
    public static final int MATERIAL_FLAG_HAS_SPECULAR = 1 << 6;
    public static final int MATERIAL_FLAG_HAS_NORMAL = 1 << 7;
    public static final int MATERIAL_FLAG_DISPLACEMENT_ELIGIBLE = 1 << 8;
    public static final int MATERIAL_FLAG_CUTOUT_DISPLACEMENT_BLOCKED = 1 << 9;
    public static final int MATERIAL_TEXTURE_PAGE_MAX = 16;

    private static final AtomicReference<Snapshot> ACTIVE =
        new AtomicReference<>(Snapshot.empty());
    private static final AtomicReference<Map<Integer, ResidencyHandle>> ACTIVE_RESIDENCY =
        new AtomicReference<>(Map.of());
    private static volatile ResidencyMergeStats lastResidencyMergeStats =
        ResidencyMergeStats.empty("startup");

    private ResourceMaterialRegistry() {
    }

    public static Snapshot activeSnapshot() {
        return ACTIVE.get();
    }

    public static Snapshot publishVanillaSprites(List<Identifier> spriteIds, long generation) {
        resetResidentMaterialHandlesForGeneration(Map.of(), "vanilla_sprite_publish");
        Snapshot snapshot = buildVanillaSnapshot(spriteIds, generation, "");
        ACTIVE.set(snapshot);
        return snapshot;
    }

    public static Snapshot publishFromCompatReport(JsonObject root, long generation) {
        resetResidentMaterialHandlesForGeneration(Map.of(), "compat_report_publish");
        Snapshot snapshot = buildFromCompatReport(root, generation);
        ACTIVE.set(snapshot);
        return snapshot;
    }

    public static Snapshot previewFromCompatReport(JsonObject root, long generation) {
        return buildFromCompatReport(root, generation);
    }

    public static int materialIdForSpriteId(int spriteId) {
        Snapshot snapshot = ACTIVE.get();
        MaterialRecord record = snapshot.recordByMaterialId(spriteId);
        return record != null && record.baseSpriteId() == spriteId ? record.materialId() : spriteId;
    }

    public static int materialIdForCtmAssetPath(String assetPath, int fallbackSpriteId) {
        if (assetPath == null || assetPath.isBlank()) {
            return fallbackSpriteId >= 0 ? materialIdForSpriteId(fallbackSpriteId) : -1;
        }
        Snapshot snapshot = ACTIVE.get();
        Integer materialId = snapshot.keyLookup().get(MaterialKey.compatCtmTile(assetPath).stableKey());
        if (materialId != null) {
            return materialId;
        }

        String natural = ResourcePackCompatCtmTiles.naturalAtlasSpriteIdentifier(assetPath);
        Identifier naturalId = Identifier.tryParse(natural);
        int spriteId = TextureArrayBridge.resolveSpriteId(naturalId == null ? "" : naturalId.toString());
        if (spriteId >= 0) {
            return materialIdForSpriteId(spriteId);
        }
        return fallbackSpriteId >= 0 ? materialIdForSpriteId(fallbackSpriteId) : -1;
    }

    public static int materialIdForCompatCtmAssetPathExact(String assetPath) {
        if (assetPath == null || assetPath.isBlank()) {
            return -1;
        }
        Snapshot snapshot = ACTIVE.get();
        Integer materialId = snapshot.keyLookup().get(MaterialKey.compatCtmTile(assetPath).stableKey());
        return materialId == null ? -1 : materialId;
    }

    public static boolean isPendingCompatMaterialId(int materialId) {
        Snapshot snapshot = ACTIVE.get();
        MaterialRecord record = snapshot.recordByMaterialId(materialId);
        if (record == null) {
            return false;
        }
        int flags = effectiveFlags(record, ACTIVE_RESIDENCY.get().get(materialId));
        return (flags & MATERIAL_FLAG_COMPAT_VIRTUAL) != 0
            && (flags & MATERIAL_FLAG_GPU_RESIDENT) == 0;
    }

    public static boolean isCompatVirtualMaterialId(int materialId) {
        Snapshot snapshot = ACTIVE.get();
        MaterialRecord record = snapshot.recordByMaterialId(materialId);
        return record != null && (record.flags() & MATERIAL_FLAG_COMPAT_VIRTUAL) != 0;
    }

    public static ResidencyMergeStats resetResidentMaterialHandlesForGeneration(
        Map<Integer, ResidencyHandle> handles, String reason) {
        int before = ACTIVE_RESIDENCY.get().size();
        if (handles == null || handles.isEmpty()) {
            ACTIVE_RESIDENCY.set(Map.of());
            ResidencyMergeStats stats = new ResidencyMergeStats(reason, before, 0, 0, before, 0);
            lastResidencyMergeStats = stats;
            return stats;
        }
        ACTIVE_RESIDENCY.set(Map.copyOf(handles));
        ResidencyMergeStats stats = new ResidencyMergeStats(reason, before, handles.size(),
            handles.size(), before, handles.size());
        lastResidencyMergeStats = stats;
        return stats;
    }

    public static ResidencyMergeStats registerResidentMaterialHandles(Map<Integer, ResidencyHandle> handles) {
        return resetResidentMaterialHandlesForGeneration(handles, "legacy_register_resets_generation");
    }

    public static ResidencyMergeStats mergeResidentMaterialHandles(Map<Integer, ResidencyHandle> handles) {
        int before = ACTIVE_RESIDENCY.get().size();
        if (handles == null || handles.isEmpty()) {
            ResidencyMergeStats stats = new ResidencyMergeStats("merge", before, before, 0, 0, 0);
            lastResidencyMergeStats = stats;
            return stats;
        }
        Map<Integer, ResidencyHandle> next = new LinkedHashMap<>(ACTIVE_RESIDENCY.get());
        int replaced = 0;
        int added = 0;
        for (Integer materialId : handles.keySet()) {
            if (next.containsKey(materialId)) {
                replaced++;
            } else {
                added++;
            }
        }
        next.putAll(handles);
        ACTIVE_RESIDENCY.set(Map.copyOf(next));
        ResidencyMergeStats stats = new ResidencyMergeStats("merge", before, next.size(),
            handles.size(), replaced, added);
        lastResidencyMergeStats = stats;
        return stats;
    }

    public static JsonObject lastResidencyMergeStatsJson() {
        return lastResidencyMergeStats.toJson();
    }

    public static int shaderTextureIdForMaterialId(int materialId) {
        Snapshot snapshot = ACTIVE.get();
        MaterialRecord record = snapshot.recordByMaterialId(materialId);
        if (record == null) {
            return TextureArrayBridge.missingSpriteFallbackIdForTest();
        }
        if (record.baseSpriteId() >= 0) {
            return record.baseSpriteId();
        }
        MaterialRecord fallback = snapshot.recordByMaterialId(record.fallbackMaterialId());
        if (fallback != null && fallback.baseSpriteId() >= 0) {
            return fallback.baseSpriteId();
        }
        return TextureArrayBridge.missingSpriteFallbackIdForTest();
    }

    public static JsonObject activeSummaryJson() {
        return ACTIVE.get().toSummaryJson();
    }

    public static JsonArray activeRecordsJson(int limit) {
        return ACTIVE.get().recordsJson(0, limit);
    }

    public static JsonArray activeRecordsJson(int offset, int limit) {
        return ACTIVE.get().recordsJson(offset, limit);
    }

    public static JsonObject materialRecordJson(int materialId) {
        Snapshot snapshot = ACTIVE.get();
        MaterialRecord record = snapshot.recordByMaterialId(materialId);
        if (record == null) {
            JsonObject missing = new JsonObject();
            missing.addProperty("materialId", materialId);
            missing.addProperty("missing", true);
            return missing;
        }

        ResidencyHandle handle = ACTIVE_RESIDENCY.get().get(materialId);
        int flags = effectiveFlags(record, handle);
        JsonObject json = record.toJson(handle);
        json.addProperty("missing", false);
        json.addProperty("materialKey", record.key().stableKey());
        json.addProperty("compatVirtual", (flags & MATERIAL_FLAG_COMPAT_VIRTUAL) != 0);
        json.addProperty("gpuResident", (flags & MATERIAL_FLAG_GPU_RESIDENT) != 0);
        json.addProperty("currentlyUsingFallback", (flags & MATERIAL_FLAG_FALLBACK) != 0);
        json.addProperty("displacementEligible", (flags & MATERIAL_FLAG_DISPLACEMENT_ELIGIBLE) != 0);
        json.addProperty("effectiveFlagsHex", "0x" + Integer.toHexString(flags));
        json.addProperty("effectiveFlags", flags);
        json.addProperty("albedoPage", handle == null ? 0 : handle.albedoPage());
        json.addProperty("albedoLayer", handle == null ? record.baseSpriteId() : handle.albedoLayer());
        json.addProperty("normalPage", handle == null ? 0 : handle.normalPage());
        json.addProperty("normalLayer", handle == null
            ? ((flags & MATERIAL_FLAG_HAS_NORMAL) != 0 ? record.baseSpriteId() : -1)
            : handle.normalLayer());
        json.addProperty("specularPage", handle == null ? 0 : handle.specularPage());
        json.addProperty("specularLayer", handle == null
            ? ((flags & MATERIAL_FLAG_HAS_SPECULAR) != 0 ? record.baseSpriteId() : -1)
            : handle.specularLayer());
        json.addProperty("flagPage", handle == null ? 0 : handle.flagPage());
        json.addProperty("flagLayer", handle == null ? record.baseSpriteId() : handle.flagLayer());
        json.addProperty("heightRangePacked",
            handle != null && handle.heightRangePacked() >= 0
                ? handle.heightRangePacked()
                : record.heightRangePacked());
        return json;
    }

    public static boolean uploadActiveTableToNative() {
        Snapshot snapshot = ACTIVE.get();
        if (snapshot.records().isEmpty()) {
            return false;
        }
        int count = Math.min(snapshot.records().size(), MATERIAL_MAX_ENTRIES);
        ByteBuffer buffer = ByteBuffer.allocateDirect(count * MATERIAL_ENTRY_SIZE)
            .order(ByteOrder.nativeOrder());
        for (int i = 0; i < count; i++) {
            ResidencyHandle handle = ACTIVE_RESIDENCY.get().get(snapshot.records().get(i).materialId());
            writeMaterialEntry(buffer, i, snapshot.records().get(i), handle);
        }
        try {
            return TextureArrayBridge.nativeReceiveMaterialTable(
                org.lwjgl.system.MemoryUtil.memAddress(buffer), count, snapshot.generation());
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static boolean uploadMaterialTableEntriesToNative(Collection<Integer> materialIds) {
        Snapshot snapshot = ACTIVE.get();
        if (snapshot.records().isEmpty() || materialIds == null || materialIds.isEmpty()) {
            return false;
        }
        LinkedHashSet<Integer> uniqueIds = new LinkedHashSet<>();
        for (Integer materialId : materialIds) {
            if (materialId == null || materialId < 0 || materialId >= snapshot.records().size()) {
                continue;
            }
            uniqueIds.add(materialId);
        }
        if (uniqueIds.isEmpty()) {
            return false;
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(uniqueIds.size() * MATERIAL_ENTRY_SIZE)
            .order(ByteOrder.nativeOrder());
        int index = 0;
        Map<Integer, ResidencyHandle> residency = ACTIVE_RESIDENCY.get();
        for (Integer materialId : uniqueIds) {
            MaterialRecord record = snapshot.recordByMaterialId(materialId);
            writeMaterialEntry(buffer, index++, record, residency.get(materialId));
        }
        try {
            return TextureArrayBridge.nativeUpdateMaterialTableSparse(
                org.lwjgl.system.MemoryUtil.memAddress(buffer), uniqueIds.size(), snapshot.generation());
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    private static Snapshot buildVanillaSnapshot(List<Identifier> spriteIds, long generation,
        String packStackHash) {
        ArrayList<MaterialRecord> records = new ArrayList<>();
        Map<String, Integer> keyLookup = new LinkedHashMap<>();
        int count = spriteIds == null ? 0 : Math.min(spriteIds.size(), MATERIAL_MAX_ENTRIES);
        for (int i = 0; i < count; i++) {
            Identifier id = spriteIds.get(i);
            AutoPbrTextureCatalog.DisplacementEligibility displacement =
                AutoPbrTextureCatalog.displacementEligibility(i);
            int flags = MATERIAL_FLAG_VALID | MATERIAL_FLAG_VANILLA_SPRITE | MATERIAL_FLAG_GPU_RESIDENT;
            if (AutoPbrTextureCatalog.hasSpecularTexture(i)) flags |= MATERIAL_FLAG_HAS_SPECULAR;
            if (AutoPbrTextureCatalog.hasNormalTexture(i)) flags |= MATERIAL_FLAG_HAS_NORMAL;
            if (displacement.eligible()) flags |= MATERIAL_FLAG_DISPLACEMENT_ELIGIBLE;
            MaterialRecord record = new MaterialRecord(
                i,
                MaterialKey.vanillaSprite(id),
                id == null ? "" : id.toString(),
                i,
                i,
                flags,
                "vanilla_block_atlas",
                "sprite_array_layer",
                "none",
                "none",
                displacement.reason(),
                displacement.heightSource(),
                AutoPbrTextureCatalog.heightAlphaRangePacked(i),
                TextureTracker.currentSpriteLayerSize,
                true);
            records.add(record);
            keyLookup.put(record.key().stableKey(), record.materialId());
        }
        return new Snapshot(generation, packStackHash == null ? "" : packStackHash,
            count, 0, 0, 0, records, keyLookup);
    }

    private static Snapshot buildFromCompatReport(JsonObject root, long generation) {
        String packStackHash = stringProperty(root, "packStackHash");
        Snapshot vanilla = buildVanillaSnapshot(TextureArrayBridge.sortedSpriteIds, generation, packStackHash);
        ArrayList<MaterialRecord> records = new ArrayList<>(vanilla.records());
        Map<String, Integer> keyLookup = new LinkedHashMap<>(vanilla.keyLookup());
        int fallback = Math.max(0, TextureArrayBridge.missingSpriteFallbackIdForTest());
        JsonObject ctm = object(root, "activeCtmAtlasDependencies");
        JsonArray dependencies = array(ctm, "dependencies");
        int emittedCompat = 0;
        for (JsonElement element : dependencies) {
            if (records.size() >= MATERIAL_MAX_ENTRIES) {
                break;
            }
            JsonObject dependency = element.getAsJsonObject();
            String path = stringProperty(dependency, "path");
            if (path.isBlank()) {
                continue;
            }
            MaterialKey key = MaterialKey.compatCtmTile(path);
            if (keyLookup.containsKey(key.stableKey())) {
                continue;
            }
            int materialId = records.size();
            int baseSpriteId = resolveDependencyBaseSpriteId(dependency);
            boolean present = boolProperty(dependency, "present");
            boolean hasSpecular = boolProperty(dependency, "specularPresent")
                || boolProperty(dependency, "derivedSpecularPresent");
            boolean hasNormal = boolProperty(dependency, "normalPresent")
                || boolProperty(dependency, "derivedNormalPresent");
            int flags = MATERIAL_FLAG_VALID | MATERIAL_FLAG_COMPAT_VIRTUAL | MATERIAL_FLAG_FALLBACK;
            if (present) flags |= MATERIAL_FLAG_PENDING_RESIDENCY;
            if (hasSpecular) flags |= MATERIAL_FLAG_HAS_SPECULAR;
            if (hasNormal) flags |= MATERIAL_FLAG_HAS_NORMAL;
            MaterialRecord record = new MaterialRecord(
                materialId,
                key,
                path,
                baseSpriteId,
                fallback,
                flags,
                "optifine_ctm_tile",
                present ? "renderer_pool_pending" : "missing_asset",
                stringProperty(dependency, "specularPath"),
                stringProperty(dependency, "normalPath"),
                present ? "pending_renderer_pool_residency" : "missing_ctm_tile",
                AutoPbrTextureCatalog.DISPLACEMENT_HEIGHT_SOURCE,
                -1,
                0,
                false);
            records.add(record);
            keyLookup.put(key.stableKey(), materialId);
            emittedCompat++;
        }
        int declaredCompat = intProperty(object(root, "materialUniverse"), "virtualCompatMaterials");
        int presentCompat = intProperty(object(root, "materialUniverse"), "virtualCompatMaterialsPresent");
        return new Snapshot(generation, packStackHash, vanilla.vanillaMaterialCount(),
            Math.max(declaredCompat, emittedCompat), presentCompat, emittedCompat, records, keyLookup);
    }

    private static int resolveDependencyBaseSpriteId(JsonObject dependency) {
        String atlasSprite = stringProperty(dependency, "atlasSprite");
        Identifier sprite = Identifier.tryParse(atlasSprite);
        int spriteId = resolveExactSpriteId(sprite);
        if (spriteId >= 0) {
            return spriteId;
        }
        spriteId = TextureArrayBridge.resolveSpriteId(stringProperty(dependency, "fallbackAtlasSprite"));
        if (spriteId >= 0) {
            return spriteId;
        }
        Identifier resource = ResourcePackCompatCtmTiles.resourceIdentifierFromAssetPath(
            stringProperty(dependency, "path"));
        if (resource != null) {
            String natural = ResourcePackCompatCtmTiles.naturalAtlasSpriteIdentifier(
                ResourcePackCompatCtmTiles.assetPath(resource));
            Identifier naturalId = Identifier.tryParse(natural);
            spriteId = resolveExactSpriteId(naturalId);
            if (spriteId >= 0) {
                return spriteId;
            }
        }
        return TextureArrayBridge.missingSpriteFallbackIdForTest();
    }

    private static int resolveExactSpriteId(Identifier sprite) {
        return sprite == null ? -1 : TextureArrayBridge.resolveSpriteId(sprite.toString());
    }

    private static void writeMaterialEntry(ByteBuffer buffer, int index, MaterialRecord record,
        ResidencyHandle handle) {
        int off = index * MATERIAL_ENTRY_SIZE;
        int baseSprite = Math.max(0, record.baseSpriteId());
        int fallback = Math.max(0, record.fallbackMaterialId());
        int flags = effectiveFlags(record, handle);
        int tierAlbedoPage = vanillaTierPage(baseSprite, TextureTracker.spriteAlbedoPage);
        int tierAlbedoLayer = vanillaTierLayer(baseSprite, TextureTracker.spriteAlbedoLayer, baseSprite);
        int tierSpecPage = vanillaTierPage(baseSprite, TextureTracker.spriteSpecularPage);
        int tierSpecLayer = vanillaTierLayer(baseSprite, TextureTracker.spriteSpecularLayer, baseSprite);
        int tierNormalPage = vanillaTierPage(baseSprite, TextureTracker.spriteNormalPage);
        int tierNormalLayer = vanillaTierLayer(baseSprite, TextureTracker.spriteNormalLayer, baseSprite);
        int tierFlagPage = vanillaTierPage(baseSprite, TextureTracker.spriteFlagPage);
        int tierFlagLayer = vanillaTierLayer(baseSprite, TextureTracker.spriteFlagLayer, baseSprite);
        int albedoPage = handle == null ? tierAlbedoPage : handle.albedoPage();
        int albedoLayer = handle == null ? tierAlbedoLayer : handle.albedoLayer();
        int specPage = handle == null ? tierSpecPage : handle.specularPage();
        int specLayer = handle == null
            ? ((flags & MATERIAL_FLAG_HAS_SPECULAR) != 0 ? tierSpecLayer : -1)
            : handle.specularLayer();
        int normalPage = handle == null ? tierNormalPage : handle.normalPage();
        int normalLayer = handle == null
            ? ((flags & MATERIAL_FLAG_HAS_NORMAL) != 0 ? tierNormalLayer : -1)
            : handle.normalLayer();
        int flagPage = handle == null ? tierFlagPage : handle.flagPage();
        int flagLayer = handle == null ? tierFlagLayer : handle.flagLayer();
        int heightRangePacked = handle != null && handle.heightRangePacked() >= 0
            ? handle.heightRangePacked()
            : record.heightRangePacked();
        buffer.putInt(off, record.materialId());
        buffer.putInt(off + 4, baseSprite);
        buffer.putInt(off + 8, fallback);
        buffer.putInt(off + 12, flags);
        buffer.putInt(off + 16, albedoPage);
        buffer.putInt(off + 20, albedoLayer);
        buffer.putInt(off + 24, specPage);
        buffer.putInt(off + 28, specLayer);
        buffer.putInt(off + 32, normalPage);
        buffer.putInt(off + 36, normalLayer);
        buffer.putInt(off + 40, flagPage);
        buffer.putInt(off + 44, flagLayer);
        buffer.putInt(off + 48, -1);
        buffer.putInt(off + 52, displacementPolicy(flags));
        buffer.putFloat(off + 56, (flags & MATERIAL_FLAG_DISPLACEMENT_ELIGIBLE) != 0 ? 1.0f : 0.0f);
        buffer.putInt(off + 60, heightRangePacked);
        buffer.putFloat(off + 64, 1.0f);
        buffer.putFloat(off + 68, 1.0f);
        buffer.putFloat(off + 72, 0.0f);
        buffer.putFloat(off + 76, 0.0f);
    }

    private static int vanillaTierPage(int spriteId, int[] pages) {
        if (spriteId < 0 || spriteId >= pages.length) {
            return 0;
        }
        return Math.max(0, pages[spriteId]);
    }

    private static int vanillaTierLayer(int spriteId, int[] layers, int fallbackLayer) {
        if (spriteId < 0 || spriteId >= layers.length || layers[spriteId] < 0) {
            return fallbackLayer;
        }
        return layers[spriteId];
    }

    private static int effectiveFlags(MaterialRecord record, ResidencyHandle handle) {
        int flags = record.flags();
        if (handle != null) {
            flags |= MATERIAL_FLAG_GPU_RESIDENT | MATERIAL_FLAG_HAS_NORMAL;
            flags &= ~MATERIAL_FLAG_PENDING_RESIDENCY;
            flags &= ~MATERIAL_FLAG_FALLBACK;
            if (handle.hasSpecular()) {
                flags |= MATERIAL_FLAG_HAS_SPECULAR;
            }
            if (handle.displacementEligible() && handle.heightRangePacked() >= 0) {
                flags |= MATERIAL_FLAG_DISPLACEMENT_ELIGIBLE;
                flags &= ~MATERIAL_FLAG_CUTOUT_DISPLACEMENT_BLOCKED;
            } else if (handle.displacementBlocked()) {
                flags &= ~MATERIAL_FLAG_DISPLACEMENT_ELIGIBLE;
                flags |= MATERIAL_FLAG_CUTOUT_DISPLACEMENT_BLOCKED;
            }
        }
        return flags;
    }

    private static int displacementPolicy(int flags) {
        if ((flags & MATERIAL_FLAG_DISPLACEMENT_ELIGIBLE) != 0) {
            return 1;
        }
        if ((flags & MATERIAL_FLAG_CUTOUT_DISPLACEMENT_BLOCKED) != 0) {
            return 2;
        }
        return 0;
    }

    private static JsonObject object(JsonObject json, String key) {
        if (json != null && json.has(key) && json.get(key).isJsonObject()) {
            return json.getAsJsonObject(key);
        }
        return new JsonObject();
    }

    private static JsonArray array(JsonObject json, String key) {
        if (json != null && json.has(key) && json.get(key).isJsonArray()) {
            return json.getAsJsonArray(key);
        }
        return new JsonArray();
    }

    private static String stringProperty(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) return "";
        try {
            return json.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int intProperty(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) return 0;
        try {
            return json.get(key).getAsInt();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static boolean boolProperty(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) return false;
        try {
            return json.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    public record MaterialKey(String namespace, String kind, String path) {
        static MaterialKey vanillaSprite(Identifier id) {
            return new MaterialKey(id == null ? "minecraft" : id.getNamespace(),
                "vanilla_sprite", id == null ? "missing" : id.getPath());
        }

        static MaterialKey compatCtmTile(String assetPath) {
            Identifier id = ResourcePackCompatCtmTiles.resourceIdentifierFromAssetPath(assetPath);
            return new MaterialKey(id == null ? "minecraft" : id.getNamespace(),
                "optifine_ctm_tile", assetPath);
        }

        String stableKey() {
            return namespace + ":" + kind + ":" + path;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("namespace", namespace);
            json.addProperty("kind", kind);
            json.addProperty("path", path);
            json.addProperty("stableKey", stableKey());
            return json;
        }
    }

    public record MaterialRecord(int materialId,
                                 MaterialKey key,
                                 String label,
                                 int baseSpriteId,
                                 int fallbackMaterialId,
                                 int flags,
                                 String provenance,
                                 String residencyState,
                                 String specularSource,
                                 String normalSource,
                                 String displacementReason,
                                 String displacementHeightSource,
                                 int heightRangePacked,
                                 int authoredSize,
                                 boolean nativeWrappedSpriteArray) {
        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("materialId", materialId);
            json.add("key", key.toJson());
            json.addProperty("label", label);
            json.addProperty("baseSpriteId", baseSpriteId);
            json.addProperty("fallbackMaterialId", fallbackMaterialId);
            json.addProperty("flags", flags);
            json.addProperty("provenance", provenance);
            json.addProperty("residencyState", residencyState);
            json.addProperty("specularSource", specularSource);
            json.addProperty("normalSource", normalSource);
            json.addProperty("displacementReason", displacementReason);
            json.addProperty("displacementHeightSource", displacementHeightSource);
            json.addProperty("heightRangePacked", heightRangePacked);
            json.addProperty("authoredSize", authoredSize);
            json.addProperty("nativeWrappedSpriteArray", nativeWrappedSpriteArray);
            return json;
        }

        JsonObject toJson(ResidencyHandle handle) {
            JsonObject json = toJson();
            if (handle != null) {
                json.add("residencyHandle", handle.toJson());
                json.addProperty("effectiveFlags", effectiveFlags(this, handle));
                json.addProperty("effectiveResidencyState", "renderer_pool_resident");
            }
            return json;
        }
    }

    public record ResidencyHandle(int albedoPage,
                                  int albedoLayer,
                                  int specularPage,
                                  int specularLayer,
                                  int normalPage,
                                  int normalLayer,
                                  int flagPage,
                                  int flagLayer,
                                  int layerSize,
                                  boolean hasSpecular,
                                  boolean displacementEligible,
                                  boolean displacementBlocked,
                                  int heightRangePacked) {
        public static ResidencyHandle sameLayer(int page, int layer, int layerSize,
            boolean hasSpecular, boolean displacementEligible, boolean displacementBlocked,
            int heightRangePacked) {
            return new ResidencyHandle(page, layer, page, layer, page, layer, page, layer,
                layerSize, hasSpecular, displacementEligible, displacementBlocked,
                heightRangePacked);
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("albedoPage", albedoPage);
            json.addProperty("albedoLayer", albedoLayer);
            json.addProperty("specularPage", specularPage);
            json.addProperty("specularLayer", specularLayer);
            json.addProperty("normalPage", normalPage);
            json.addProperty("normalLayer", normalLayer);
            json.addProperty("flagPage", flagPage);
            json.addProperty("flagLayer", flagLayer);
            json.addProperty("layerSize", layerSize);
            json.addProperty("hasSpecular", hasSpecular);
            json.addProperty("displacementEligible", displacementEligible);
            json.addProperty("displacementBlocked", displacementBlocked);
            json.addProperty("heightRangePacked", heightRangePacked);
            return json;
        }
    }

    public record ResidencyMergeStats(String reason,
                                      int residentHandleCountBefore,
                                      int residentHandleCountAfter,
                                      int mergedHandleCount,
                                      int replacedHandleCount,
                                      int addedHandleCount) {
        static ResidencyMergeStats empty(String reason) {
            return new ResidencyMergeStats(reason, 0, 0, 0, 0, 0);
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("reason", reason);
            json.addProperty("residentHandleCountBefore", residentHandleCountBefore);
            json.addProperty("residentHandleCountAfter", residentHandleCountAfter);
            json.addProperty("mergedHandleCount", mergedHandleCount);
            json.addProperty("replacedHandleCount", replacedHandleCount);
            json.addProperty("addedHandleCount", addedHandleCount);
            return json;
        }
    }

    public record Snapshot(long generation,
                           String packStackHash,
                           int vanillaMaterialCount,
                           int declaredCompatMaterialCount,
                           int declaredPresentCompatMaterialCount,
                           int recordedCompatMaterialCount,
                           List<MaterialRecord> records,
                           Map<String, Integer> keyLookup) {
        static Snapshot empty() {
            return new Snapshot(0, "", 0, 0, 0, 0, List.of(), Map.of());
        }

        public Snapshot {
            records = List.copyOf(records);
            keyLookup = Map.copyOf(keyLookup);
        }

        MaterialRecord recordByMaterialId(int materialId) {
            if (materialId < 0 || materialId >= records.size()) {
                return null;
            }
            return records.get(materialId);
        }

        public JsonObject toSummaryJson() {
            JsonObject json = new JsonObject();
            Map<Integer, ResidencyHandle> residency = ACTIVE_RESIDENCY.get();
            int gpuResident = 0;
            int pendingResidency = 0;
            int fallback = 0;
            int fallbackPointer = 0;
            int compatVirtual = 0;
            int compatVirtualPending = 0;
            int compatVirtualResident = 0;
            int compatVirtualFallback = 0;
            int displacementEligible = 0;
            for (MaterialRecord record : records) {
                int flags = effectiveFlags(record, residency.get(record.materialId()));
                if ((flags & MATERIAL_FLAG_GPU_RESIDENT) != 0) gpuResident++;
                if ((flags & MATERIAL_FLAG_PENDING_RESIDENCY) != 0) pendingResidency++;
                if ((flags & MATERIAL_FLAG_FALLBACK) != 0) fallback++;
                if (record.fallbackMaterialId() >= 0 && record.fallbackMaterialId() != record.materialId()) {
                    fallbackPointer++;
                }
                if ((flags & MATERIAL_FLAG_DISPLACEMENT_ELIGIBLE) != 0) displacementEligible++;
                if ((flags & MATERIAL_FLAG_COMPAT_VIRTUAL) != 0) {
                    compatVirtual++;
                    if ((flags & MATERIAL_FLAG_PENDING_RESIDENCY) != 0) compatVirtualPending++;
                    if ((flags & MATERIAL_FLAG_GPU_RESIDENT) != 0) compatVirtualResident++;
                    if ((flags & MATERIAL_FLAG_FALLBACK) != 0) compatVirtualFallback++;
                }
            }
            json.addProperty("generation", generation);
            json.addProperty("packStackHash", packStackHash);
            json.addProperty("materialEntrySize", MATERIAL_ENTRY_SIZE);
            json.addProperty("maxMaterialEntries", MATERIAL_MAX_ENTRIES);
            json.addProperty("vanillaMaterialCount", vanillaMaterialCount);
            json.addProperty("declaredCompatMaterialCount", declaredCompatMaterialCount);
            json.addProperty("declaredPresentCompatMaterialCount", declaredPresentCompatMaterialCount);
            json.addProperty("recordedCompatMaterialCount", recordedCompatMaterialCount);
            json.addProperty("recordedMaterialCount", records.size());
            json.addProperty("gpuResidentMaterialCount", gpuResident);
            json.addProperty("pendingResidencyMaterialCount", pendingResidency);
            json.addProperty("fallbackMaterialCount", fallback);
            json.addProperty("materialsWithFallbackPointer", fallbackPointer);
            json.addProperty("materialsCurrentlyUsingFallback", fallback);
            json.addProperty("compatVirtualMaterialCount", compatVirtual);
            json.addProperty("compatVirtualPendingResidencyCount", compatVirtualPending);
            json.addProperty("compatVirtualGpuResidentCount", compatVirtualResident);
            json.addProperty("compatVirtualCurrentlyUsingFallbackCount", compatVirtualFallback);
            json.addProperty("residentMaterialHandleCount", residency.size());
            json.add("lastResidencyMergeStats", lastResidencyMergeStatsJson());
            json.addProperty("displacementEligibleMaterialCount", displacementEligible);
            json.add("visibleResidency", ResourceMaterialResidencyDemand.summaryJson(generation));
            json.addProperty("nativeBindingPolicy", AutoPbrTextureCatalog.MATERIAL_SET_BINDING_POLICY);
            json.addProperty("shaderLookupKey", AutoPbrTextureCatalog.MATERIAL_SET_SHADER_LOOKUP_KEY);
            json.addProperty("nativeMaterialTablePresent",
                AutoPbrTextureCatalog.MATERIAL_SET_NATIVE_TABLE_PRESENT);
            json.addProperty("vanillaIdsAliasSpriteIds", true);
            json.addProperty("compatIdsFallbackUntilRendererPoolResident", true);
            json.addProperty("compatTextureResidencyBackend", "renderer_owned_material_pages");
            json.addProperty("normalGameplayLoadingMode", "visible_first_progressive_residency");
            json.addProperty("fullPreloadAcceptanceMode", "diagnostic_required_zero_fallback_after_pool_backend");
            return json;
        }

        public JsonArray recordsJson(int limit) {
            return recordsJson(0, limit);
        }

        public JsonArray recordsJson(int offset, int limit) {
            JsonArray array = new JsonArray();
            Map<Integer, ResidencyHandle> residency = ACTIVE_RESIDENCY.get();
            int start = Math.min(Math.max(0, offset), records.size());
            int end = Math.min(records.size(), start + Math.max(0, limit));
            for (int i = start; i < end; i++) {
                MaterialRecord record = records.get(i);
                array.add(record.toJson(residency.get(record.materialId())));
            }
            return array;
        }
    }
}

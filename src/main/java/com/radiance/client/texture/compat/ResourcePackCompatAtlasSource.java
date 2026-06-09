package com.radiance.client.texture.compat;

import com.radiance.client.option.Options;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import net.minecraft.client.texture.atlas.AtlasSource;
import net.minecraft.client.texture.atlas.AtlasSourceManager;
import net.minecraft.client.texture.atlas.AtlasSourceType;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourcePackCompatAtlasSource implements AtlasSource {
    public static final ResourcePackCompatAtlasSource INSTANCE = new ResourcePackCompatAtlasSource();
    private static final Logger LOGGER = LoggerFactory.getLogger("RadSER Material Compat");
    private static final int TEST_ADMISSION_LIMIT = 4096;

    private ResourcePackCompatAtlasSource() {
    }

    public static boolean shouldInjectForAtlas(Identifier atlasId) {
        if (!Options.materialCompatEnabled || atlasId == null
            || (!Options.materialCompatCtmEnabled
                && !Options.materialCompatRandomEnabled
                && !Options.materialCompatOverlaysEnabled
                && !Options.materialCompatPhysicalEmissiveEnabled)) {
            return false;
        }
        String path = atlasId.getPath();
        return path.equals("blocks") || path.contains("blocks");
    }

    public static AdmissionSummary admitCtmSpritesForTest(ResourceManager resourceManager, SpriteRegions regions) {
        return admitCtmSprites(resourceManager, regions, TEST_ADMISSION_LIMIT);
    }

    @Override
    public void load(ResourceManager resourceManager, SpriteRegions regions) {
        ResourcePackEmissiveTextureResolver.clearRegisteredOverlaySprites();
        AdmissionSummary summary = admitCtmSprites(resourceManager, regions,
            Math.max(0, Options.materialCompatCtmAtlasAdmissionLimit));
        if (summary.added() > 0 || summary.missing() > 0 || summary.invalid() > 0) {
            LOGGER.info("[MaterialCompat] CTM atlas admission: {}", summary);
        }
    }

    @Override
    public AtlasSourceType getType() {
        return AtlasSourceManager.SINGLE;
    }

    private static AdmissionSummary admitCtmSprites(ResourceManager resourceManager, SpriteRegions regions,
        int limit) {
        ResourcePackCompatCtmTiles.clearRegisteredCtmSpriteAssetPaths();
        if (resourceManager == null || regions == null) {
            return AdmissionSummary.empty();
        }
        if (limit <= 0) {
            return AdmissionSummary.empty();
        }

        Counters counters = new Counters();
        Set<String> admittedSprites = new HashSet<>();
        String emissiveSuffix = ResourcePackEmissiveTextureResolver.suffix(
            resourceManager, Options.materialCompatLegacyMcPatcherEnabled);
        admitFromRoot(resourceManager, regions, "optifine/ctm", admittedSprites, counters, limit,
            emissiveSuffix);
        if (Options.materialCompatLegacyMcPatcherEnabled) {
            admitFromRoot(resourceManager, regions, "mcpatcher/ctm", admittedSprites, counters, limit,
                emissiveSuffix);
        }
        return counters.toSummary();
    }

    private static void admitFromRoot(ResourceManager resourceManager, SpriteRegions regions, String root,
        Set<String> admittedSprites, Counters counters, int limit, String emissiveSuffix) {
        Map<Identifier, Resource> properties = resourceManager.findResources(root,
            id -> id.getPath().endsWith(".properties"));
        properties.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> admitFromProperty(resourceManager, regions, entry.getKey(), entry.getValue(),
                admittedSprites, counters, limit, emissiveSuffix));
    }

    private static void admitFromProperty(ResourceManager resourceManager, SpriteRegions regions,
        Identifier propertyId, Resource propertyResource, Set<String> admittedSprites, Counters counters,
        int limit, String emissiveSuffix) {
        Properties props = new Properties();
        try (BufferedReader reader = propertyResource.getReader()) {
            props.load(reader);
        } catch (IOException e) {
            counters.invalid++;
            return;
        }

        String propertyAssetPath = ResourcePackCompatCtmTiles.assetPath(propertyId);
        List<String> materialFallbackAssetPaths =
            ResourcePackCompatCtmTiles.materialFallbackAssetPaths(propertyAssetPath, props);
        for (String assetPath : ResourcePackCompatCtmTiles.ctmTileDependencyAssetPaths(propertyAssetPath, props)) {
            admitAssetPath(resourceManager, regions, admittedSprites, counters, limit, assetPath, false,
                null, materialFallbackAssetPaths);
            if (Options.materialCompatPhysicalEmissiveEnabled) {
                String emissiveAssetPath =
                    ResourcePackEmissiveTextureResolver.emissiveAssetPath(assetPath, emissiveSuffix);
                if (emissiveAssetPath != null) {
                    admitAssetPath(resourceManager, regions, admittedSprites, counters, limit,
                        emissiveAssetPath, true, assetPath, List.of());
                }
            }
        }
    }

    private static void admitAssetPath(ResourceManager resourceManager, SpriteRegions regions,
        Set<String> admittedSprites, Counters counters, int limit, String assetPath, boolean emissive) {
        admitAssetPath(resourceManager, regions, admittedSprites, counters, limit, assetPath, emissive, null,
            List.of());
    }

    private static void admitAssetPath(ResourceManager resourceManager, SpriteRegions regions,
        Set<String> admittedSprites, Counters counters, int limit, String assetPath, boolean emissive,
        String baseAssetPath, List<String> materialFallbackAssetPaths) {
        if (counters.added >= limit) {
            counters.truncated = true;
            return;
        }
        counters.considered++;
        if (!ResourcePackCompatCtmTiles.requiresAtlasAdmission(assetPath)) {
            counters.natural++;
            return;
        }

        Identifier resourceId = ResourcePackCompatCtmTiles.resourceIdentifierFromAssetPath(assetPath);
        Identifier spriteId = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(assetPath));
        if (resourceId == null || spriteId == null
            || (!emissive && ResourcePackTextureNames.hasPbrAuxiliarySuffix(resourceId.getPath()))) {
            counters.invalid++;
            return;
        }
        Optional<Resource> resource = resourceManager.getResource(resourceId);
        if (resource.isEmpty()) {
            if (!emissive) {
                counters.missing++;
            }
            return;
        }
        if (!admittedSprites.add(spriteId.toString())) {
            counters.duplicates++;
            return;
        }
        if (emissive && baseAssetPath != null) {
            Identifier baseSpriteId =
                Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(baseAssetPath));
            ResourcePackEmissiveTextureResolver.registerOverlaySprite(spriteId, baseSpriteId);
        } else if (!emissive) {
            ResourcePackCompatCtmTiles.registerCtmSpriteAssetPath(spriteId, assetPath,
                materialFallbackAssetPaths);
        }
        regions.add(spriteId, resource.get());
        counters.added++;
    }

    public record AdmissionSummary(int considered,
                                   int natural,
                                   int added,
                                   int missing,
                                   int duplicates,
                                   int invalid,
                                   boolean truncated) {
        static AdmissionSummary empty() {
            return new AdmissionSummary(0, 0, 0, 0, 0, 0, false);
        }
    }

    private static final class Counters {
        int considered;
        int natural;
        int added;
        int missing;
        int duplicates;
        int invalid;
        boolean truncated;

        AdmissionSummary toSummary() {
            return new AdmissionSummary(considered, natural, added, missing, duplicates, invalid, truncated);
        }
    }
}

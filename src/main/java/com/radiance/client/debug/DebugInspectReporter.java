package com.radiance.client.debug;

import com.radiance.client.RadianceClient;
import com.radiance.client.material.BlockTypeIdRegistry;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.world.BlockModelBridge;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.util.MaterialBlock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * In-game debug capture for material and displacement issues.
 *
 * Pressing the bound key writes a stable report for the current crosshair target,
 * so visual bugs can be tied to exact block state, material settings, sprite IDs,
 * native sprite-entry data, and the shader's inferred height-source path.
 */
public final class DebugInspectReporter {
    private static final DateTimeFormatter FILE_TIME =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter REPORT_TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z").withZone(ZoneId.systemDefault());
    private static final Path FULL_TEXTURE_CSV = Path.of("C:/RadSER/texture_system_full.csv");
    private static final int SPRITE_FLAG_HAS_HEIGHT = 1 << 2;

    private DebugInspectReporter() {
    }

    public static void captureCurrentTarget(MinecraftClient client) {
        try {
            Path latest = captureCurrentTargetReport(client);
            if (client.inGameHud != null) {
                client.inGameHud.getChatHud().addMessage(Text.literal(
                    "Radiance debug inspect saved: radiance/logs/debug_inspect_latest.txt"));
            }
            RadianceClient.LOGGER.info("[DebugInspect] Captured target report at {}",
                latest);
        } catch (Exception e) {
            RadianceClient.LOGGER.error("[DebugInspect] Capture failed", e);
            if (client.inGameHud != null) {
                client.inGameHud.getChatHud().addMessage(Text.literal(
                    "Radiance debug inspect failed: " + e.getMessage()));
            }
        }
    }

    public static Path captureCurrentTargetReport(MinecraftClient client) throws IOException {
        CaptureResult result = buildReport(client);
        Path latest = writeReport(result.report());
        RadianceClient.LOGGER.info("[DebugInspect] Captured target report at {}", result.summary());
        return latest;
    }

    public static Path latestReportPath() {
        return getRadianceDir().resolve("logs").resolve("debug_inspect_latest.txt");
    }

    private static CaptureResult buildReport(MinecraftClient client) {
        StringBuilder sb = new StringBuilder(24_000);
        Instant now = Instant.now();
        sb.append("Radiance Debug Inspect\n");
        sb.append("======================\n");
        sb.append("capturedAt=").append(REPORT_TIME.format(now)).append('\n');
        sb.append("minecraftVersion=").append(client.getGameVersion()).append('\n');
        sb.append("radianceDir=").append(getRadianceDir().toAbsolutePath()).append('\n');
        sb.append("textureFullCsv=").append(FULL_TEXTURE_CSV.toAbsolutePath()).append('\n');
        sb.append('\n');

        appendPlayer(sb, client);
        appendGlobalDisplacement(sb);
        appendRendererDiagnostics(sb);

        String nativeDiag = requestNativeTextureDump();
        sb.append("Native Texture Diagnostic\n");
        sb.append("-------------------------\n");
        sb.append("nativeGetTextureReloadDiagnostics=").append(nativeDiag).append('\n');
        sb.append("fullCsvExists=").append(Files.exists(FULL_TEXTURE_CSV)).append('\n');
        sb.append('\n');

        Map<Integer, SpriteCsvRow> spriteRows = loadSpriteRows();

        if (client.world == null) {
            sb.append("Target\n------\nno world\n");
            return new CaptureResult(sb.toString(), "no world");
        }

        HitResult target = client.crosshairTarget;
        if (target instanceof BlockHitResult blockHit && target.getType() != HitResult.Type.MISS) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = client.world.getBlockState(pos);
            appendBlockTarget(sb, client, blockHit, state);
            appendMaterial(sb, state);
            appendModelQuads(sb, client, state, blockHit.getSide(), spriteRows);
            return new CaptureResult(sb.toString(),
                Registries.BLOCK.getId(state.getBlock()) + " at " + pos.toShortString());
        }

        if (target instanceof EntityHitResult entityHit) {
            sb.append("Target\n------\n");
            sb.append("type=entity\n");
            sb.append("entityType=").append(Registries.ENTITY_TYPE.getId(entityHit.getEntity().getType())).append('\n');
            sb.append("entityId=").append(entityHit.getEntity().getId()).append('\n');
            sb.append("entityUuid=").append(entityHit.getEntity().getUuid()).append('\n');
            sb.append("hitPos=").append(formatVec(entityHit.getPos())).append('\n');
            return new CaptureResult(sb.toString(), "entity " + entityHit.getEntity().getId());
        }

        sb.append("Target\n------\n");
        sb.append("type=").append(target == null ? "null" : target.getType()).append('\n');
        return new CaptureResult(sb.toString(), "no target");
    }

    private static void appendPlayer(StringBuilder sb, MinecraftClient client) {
        sb.append("Player And Camera\n");
        sb.append("-----------------\n");
        if (client.player == null) {
            sb.append("player=null\n\n");
            return;
        }
        Vec3d pos = client.player.getPos();
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        sb.append("playerBlockPos=").append(client.player.getBlockPos().toShortString()).append('\n');
        sb.append("playerPos=").append(formatVec(pos)).append('\n');
        sb.append("cameraPos=").append(formatVec(camera)).append('\n');
        sb.append("yaw=").append(fmt(client.player.getYaw())).append('\n');
        sb.append("pitch=").append(fmt(client.player.getPitch())).append('\n');
        sb.append("dimension=").append(client.world == null ? "null" : client.world.getRegistryKey().getValue()).append('\n');
        sb.append('\n');
    }

    private static void appendRendererDiagnostics(StringBuilder sb) {
        sb.append("Renderer Diagnostics\n");
        sb.append("--------------------\n");
        sb.append("gpuProfile=").append(safeNativeString(RendererProxy::nativeGetGpuProfile)).append('\n');
        sb.append("featureTruth=").append(safeNativeString(RendererProxy::nativeGetFeatureTruth)).append('\n');
        sb.append("vmaStats=").append(safeNativeString(RendererProxy::nativeGetVmaStats)).append('\n');
        sb.append("overlayDiag=").append(safeNativeString(RendererProxy::nativeGetOverlayDiag)).append('\n');
        sb.append('\n');
    }

    private static void appendGlobalDisplacement(StringBuilder sb) {
        sb.append("Global Material Displacement\n");
        sb.append("----------------------------\n");
        sb.append("displacementEnabled=").append(Options.displacementEnabled).append('\n');
        sb.append("depthCapBlocks=").append(fmt(Options.displacementDepthCapPercent / 100.0)).append('\n');
        sb.append("quality=").append(Options.displacementQuality)
            .append(" (").append(Options.displacementQualityName(Options.displacementQuality)).append(")\n");
        sb.append("primarySteps=").append(Options.displacementSteps).append('\n');
        sb.append("refinement=").append(Options.displacementRefinement).append('\n');
        sb.append("secondarySteps=").append(Math.max(8, Options.displacementSteps / 2)).append('\n');
        sb.append("fadeDistanceBlocks=").append(Options.displacementFadeDistance).append('\n');
        sb.append("autoPBREnabled=").append(Options.autoPBREnabled).append('\n');
        sb.append("materialOverridesEnabled=").append(Options.materialOverridesEnabled).append('\n');
        sb.append('\n');
    }

    private static void appendBlockTarget(StringBuilder sb, MinecraftClient client,
                                          BlockHitResult hit, BlockState state) {
        BlockPos pos = hit.getBlockPos();
        Identifier blockId = Registries.BLOCK.getId(state.getBlock());
        sb.append("Target Block\n");
        sb.append("------------\n");
        sb.append("block=").append(blockId).append('\n');
        sb.append("state=").append(state).append('\n');
        sb.append("pos=").append(pos.toShortString()).append('\n');
        sb.append("hitSide=").append(hit.getSide().asString()).append('\n');
        sb.append("hitPos=").append(formatVec(hit.getPos())).append('\n');
        sb.append("insideBlock=").append(hit.isInsideBlock()).append('\n');
        sb.append("renderType=").append(state.getRenderType()).append('\n');
        sb.append("renderLayer=").append(safeRenderLayer(state)).append('\n');
        sb.append("opaqueFullCube=").append(safeBool(() -> state.isOpaqueFullCube())).append('\n');
        sb.append("solidBlock=").append(safeBool(() -> state.isSolidBlock(client.world, pos))).append('\n');
        sb.append("fluidState=").append(state.getFluidState()).append('\n');
        sb.append("blockTypeId=").append(BlockTypeIdRegistry.getBlockTypeId(state.getBlock())).append('\n');
        try {
            sb.append("biome=").append(client.world.getBiome(pos).getKey()
                .map(key -> key.getValue().toString()).orElse("unknown")).append('\n');
        } catch (Exception e) {
            sb.append("biomeError=").append(e.getMessage()).append('\n');
        }
        sb.append('\n');
    }

    private static void appendMaterial(StringBuilder sb, BlockState state) {
        int ordinal = MaterialBlock.getOrdinalForBlock(state.getBlock());
        sb.append("Material\n");
        sb.append("--------\n");
        sb.append("blockMaterialOrdinal=").append(ordinal).append('\n');
        if (ordinal < 0 || ordinal >= Options.MAX_MATERIALS) {
            sb.append("materialStatus=unmapped\n\n");
            return;
        }
        sb.append("materialId=").append(MaterialBlock.getIdForOrdinal(ordinal)).append('\n');
        sb.append("materialDisplayName=").append(MaterialBlock.getDisplayNameForOrdinal(ordinal)).append('\n');
        sb.append("materialClass=").append(MaterialBlock.getMaterialClassForOrdinal(ordinal)).append('\n');
        sb.append("autoPBR=").append(Options.materialAutoPBR[ordinal]).append('\n');
        sb.append("normalInputType=").append(Options.materialNormalInputType[ordinal]).append('\n');
        sb.append("displacementMode=").append(modeName(Options.materialPomMode[ordinal]))
            .append(" (").append(Options.materialPomMode[ordinal]).append(")\n");
        sb.append("displacementDepthOverrideBlocks=")
            .append(fmt(Options.materialPomDepth[ordinal] / 100.0)).append('\n');
        sb.append("heightSource=").append(heightSourceName(Options.materialHeightSource[ordinal]))
            .append(" (").append(Options.materialHeightSource[ordinal]).append(")\n");
        sb.append("heightFilter=").append(filterName(Options.materialHeightFilter[ordinal]))
            .append(" (").append(Options.materialHeightFilter[ordinal]).append(")\n");
        sb.append("filterRadius=").append(Options.materialFilterRadius[ordinal]).append('\n');
        sb.append("mipBias=").append(Options.materialMipBias[ordinal]).append('\n');
        sb.append("heightRemapMin=").append(Options.materialHeightRemapMin[ordinal]).append('\n');
        sb.append("heightRemapMax=").append(Options.materialHeightRemapMax[ordinal]).append('\n');
        sb.append("heightContrast=").append(Options.materialHeightContrast[ordinal]).append('\n');
        sb.append("heightOffset=").append(Options.materialHeightOffset[ordinal]).append('\n');
        sb.append("selfShadow=").append(Options.materialDisplacementSelfShadow[ordinal]).append('\n');
        sb.append("autoPBRHeightGamma=").append(Options.materialAutoPBRHeightGamma[ordinal]).append('\n');
        sb.append("autoPBRFlags=").append(Options.materialAutoPBRFlags[ordinal])
            .append(" (invertRoughness=").append((Options.materialAutoPBRFlags[ordinal] & 1) != 0)
            .append(", invertNormal=").append((Options.materialAutoPBRFlags[ordinal] & 2) != 0)
            .append(", invertHeight=").append((Options.materialAutoPBRFlags[ordinal] & 4) != 0)
            .append(")\n");
        sb.append("effectiveAutoPBRFlagForShader=")
            .append(Options.autoPBREnabled || Options.materialAutoPBR[ordinal]).append('\n');
        sb.append('\n');
    }

    private static void appendModelQuads(StringBuilder sb, MinecraftClient client, BlockState state,
                                         Direction hitSide, Map<Integer, SpriteCsvRow> spriteRows) {
        sb.append("Model Quads\n");
        sb.append("-----------\n");
        if (state.getRenderType() != BlockRenderType.MODEL) {
            sb.append("modelStatus=not MODEL render type\n\n");
            return;
        }
        try {
            BakedModel model = client.getBakedModelManager().getBlockModels().getModel(state);
            if (model == null) {
                sb.append("modelStatus=null\n\n");
                return;
            }
            int total = 0;
            Direction[] faces = Direction.values();
            for (int i = 0; i < 7; i++) {
                Direction cullFace = i < 6 ? faces[i] : null;
                List<BakedQuad> quads = model.getQuads(state, cullFace, Random.create(42));
                sb.append("faceBucket=").append(cullFace == null ? "unculled" : cullFace.asString())
                    .append(" count=").append(quads.size())
                    .append(cullFace == hitSide ? " TARGET_SIDE" : "")
                    .append('\n');
                for (BakedQuad quad : quads) {
                    appendQuad(sb, state, cullFace, quad, spriteRows);
                    total++;
                }
            }
            sb.append("totalQuads=").append(total).append('\n');
            sb.append('\n');
        } catch (Exception e) {
            sb.append("modelError=").append(e).append("\n\n");
        }
    }

    private static void appendQuad(StringBuilder sb, BlockState state, Direction cullFace,
                                   BakedQuad quad, Map<Integer, SpriteCsvRow> spriteRows) {
        Sprite sprite = quad.getSprite();
        Identifier spriteId = sprite == null ? null : sprite.getContents().getId();
        int spriteIndex = spriteId == null ? -1 : BlockModelBridge.sortedSpriteIds.indexOf(spriteId);
        int blockMaterial = MaterialBlock.getOrdinalForBlock(state.getBlock());
        int textureMaterial = spriteId == null ? -1 : MaterialBlock.getOrdinalForTexture(spriteId.getPath());
        int mappedMaterial = spriteIndex >= 0
            ? BlockModelBridge.spriteId2MaterialOrdinal.getOrDefault(spriteIndex, -1)
            : -1;
        SpriteCsvRow row = spriteRows.get(spriteIndex);

        sb.append("  quad cullFace=").append(cullFace == null ? "unculled" : cullFace.asString());
        sb.append(" face=").append(quad.getFace().asString());
        sb.append(" tintIndex=").append(quad.getTintIndex());
        sb.append(" hasTint=").append(quad.hasTint());
        sb.append(" shade=").append(quad.hasShade()).append('\n');
        sb.append("    sprite=").append(spriteId == null ? "null" : spriteId).append('\n');
        sb.append("    spriteIndex=").append(spriteIndex)
            .append(" blockMaterial=").append(materialName(blockMaterial))
            .append(" textureMaterial=").append(materialName(textureMaterial))
            .append(" mappedMaterial=").append(materialName(mappedMaterial)).append('\n');
        appendQuadUvBounds(sb, quad);
        if (spriteIndex >= 0) {
            sb.append("    javaAux normalSource=").append(sourceName(TextureTracker.spriteNormalSource, spriteIndex))
                .append(" specularSource=").append(sourceName(TextureTracker.spriteSpecularSource, spriteIndex))
                .append('\n');
        }
        if (row == null) {
            sb.append("    nativeSpriteEntry=missing\n");
        } else {
            sb.append("    nativeSpriteEntry flags=").append(row.flags)
                .append(" hasHeight=").append((row.flags & SPRITE_FLAG_HAS_HEIGHT) != 0)
                .append(" specLayer=").append(row.specLayer)
                .append(" normalLayer=").append(row.normalLayer)
                .append(" maskLayer=").append(row.maskLayer)
                .append(" heightRange=").append(fmt(row.heightRange))
                .append(" atlas=").append(row.atlasX).append(',').append(row.atlasY)
                .append(" size=").append(row.width).append('x').append(row.height)
                .append(" frames=").append(row.frames)
                .append('\n');
            sb.append("    nativeHashes albedo=").append(row.albedoHash)
                .append(" spec=").append(row.specHash)
                .append(" normal=").append(row.normalHash)
                .append('\n');
        }
        sb.append("    inferredShaderHeightSource=")
            .append(inferShaderHeightSource(blockMaterial, spriteIndex, row))
            .append('\n');
    }

    private static void appendQuadUvBounds(StringBuilder sb, BakedQuad quad) {
        int[] data = quad.getVertexData();
        float minU = Float.POSITIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (int v = 0; v < 4; v++) {
            int base = v * 8;
            float u = Float.intBitsToFloat(data[base + 4]);
            float vv = Float.intBitsToFloat(data[base + 5]);
            minU = Math.min(minU, u);
            minV = Math.min(minV, vv);
            maxU = Math.max(maxU, u);
            maxV = Math.max(maxV, vv);
        }
        sb.append("    quadUv=[")
            .append(fmt(minU)).append(',').append(fmt(minV))
            .append(" -> ").append(fmt(maxU)).append(',').append(fmt(maxV))
            .append("]\n");
    }

    private static String inferShaderHeightSource(int materialOrdinal, int spriteIndex, SpriteCsvRow row) {
        if (!Options.displacementEnabled) return "flat:global_disabled";
        if (Options.displacementDepthCapPercent <= 0) return "flat:zero_global_depth";
        if (materialOrdinal < 0 || materialOrdinal >= Options.MAX_MATERIALS) return "flat:no_material";
        int mode = Options.materialPomMode[materialOrdinal];
        if (mode == 1) return "flat:material_off";
        if (mode == 2 && Options.materialPomDepth[materialOrdinal] <= 0) return "flat:custom_zero_depth";
        if (row == null) return "flat:no_native_sprite_entry";

        boolean hasNativeHeight = row.normalLayer >= 0
            && (row.flags & SPRITE_FLAG_HAS_HEIGHT) != 0
            && row.maskLayer >= 0;
        if (hasNativeHeight) {
            return "normal_alpha:labpbr_or_generated_height";
        }

        boolean shaderAutoPbr = Options.autoPBREnabled || Options.materialAutoPBR[materialOrdinal];
        if (shaderAutoPbr) {
            return "autopbr_albedo:normal_alpha_unavailable";
        }
        return "flat:no_normal_alpha_and_autopbr_disabled";
    }

    private static String requestNativeTextureDump() {
        try {
            return RendererProxy.nativeGetTextureReloadDiagnostics().replace('\n', ' ').trim();
        } catch (Throwable t) {
            return "error:" + t.getClass().getSimpleName() + ":" + t.getMessage();
        }
    }

    private static String safeNativeString(StringSupplier supplier) {
        try {
            String value = supplier.get();
            if (value == null || value.isBlank()) return "unavailable";
            return value.replace('\n', ' ').trim();
        } catch (Throwable t) {
            return "error:" + t.getClass().getSimpleName() + ":" + t.getMessage();
        }
    }

    private static Map<Integer, SpriteCsvRow> loadSpriteRows() {
        Map<Integer, SpriteCsvRow> rows = new HashMap<>();
        if (!Files.exists(FULL_TEXTURE_CSV)) return rows;
        try {
            List<String> lines = Files.readAllLines(FULL_TEXTURE_CSV);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) continue;
                String[] p = line.split(",", -1);
                if (p.length < 15) continue;
                int spriteId = parseInt(p[0], -1);
                if (spriteId < 0) continue;
                rows.put(spriteId, new SpriteCsvRow(
                    parseInt(p[1], -1),
                    parseInt(p[2], -1),
                    parseInt(p[3], -1),
                    parseInt(p[4], -1),
                    parseInt(p[5], -1),
                    parseInt(p[6], 0),
                    parseInt(p[7], -1),
                    parseInt(p[8], -1),
                    parseInt(p[9], -1),
                    parseDouble(p[10], -1.0),
                    p[11],
                    p[12],
                    p[13]));
            }
        } catch (IOException e) {
            RadianceClient.LOGGER.warn("[DebugInspect] Failed to read {}", FULL_TEXTURE_CSV, e);
        }
        return rows;
    }

    private static Path writeReport(String report) throws IOException {
        Path logsDir = getRadianceDir().resolve("logs");
        Files.createDirectories(logsDir);
        Path latest = logsDir.resolve("debug_inspect_latest.txt");
        Path history = logsDir.resolve("debug_inspect.log");
        Path stamped = logsDir.resolve("debug_inspect_" + FILE_TIME.format(Instant.now()) + ".txt");
        Files.writeString(latest, report, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(stamped, report, StandardOpenOption.CREATE_NEW);
        Files.writeString(history, report + "\n\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return latest;
    }

    private static Path getRadianceDir() {
        if (RadianceClient.radianceDir != null) return RadianceClient.radianceDir;
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("radiance");
    }

    private static String safeRenderLayer(BlockState state) {
        try {
            return String.valueOf(RenderLayers.getBlockLayer(state));
        } catch (Exception e) {
            return "error:" + e.getMessage();
        }
    }

    private static String safeBool(BoolSupplier supplier) {
        try {
            return String.valueOf(supplier.getAsBoolean());
        } catch (Exception e) {
            return "error:" + e.getMessage();
        }
    }

    private static String materialName(int ordinal) {
        if (ordinal < 0 || ordinal >= Options.MAX_MATERIALS) return "none(" + ordinal + ")";
        return MaterialBlock.getIdForOrdinal(ordinal) + "(" + ordinal + ")";
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case 0 -> "Inherit";
            case 1 -> "Off";
            case 2 -> "Custom";
            default -> "Unknown";
        };
    }

    private static String heightSourceName(int source) {
        return switch (source) {
            case 0 -> "Luminance";
            case 1 -> "Red";
            case 2 -> "Green";
            case 3 -> "Blue";
            case 4 -> "Alpha";
            case 5 -> "MaxRGB";
            case 6 -> "MinRGB";
            case 7 -> "Custom";
            default -> "Unknown";
        };
    }

    private static String filterName(int filter) {
        return switch (filter) {
            case 0 -> "Nearest";
            case 1 -> "Bilinear";
            case 2 -> "Smooth";
            default -> "Unknown";
        };
    }

    private static String sourceName(byte[] sources, int index) {
        if (index < 0 || index >= sources.length) return "out_of_range";
        return switch (sources[index]) {
            case TextureTracker.SOURCE_GENERATED -> "generated";
            case TextureTracker.SOURCE_PACK_AUTHORED -> "pack_authored";
            case TextureTracker.SOURCE_USER_CUSTOM -> "user_custom";
            case TextureTracker.SOURCE_FLAT -> "flat";
            default -> "unknown_" + sources[index];
        };
    }

    private static String formatVec(Vec3d vec) {
        return fmt(vec.x) + "," + fmt(vec.y) + "," + fmt(vec.z);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private record SpriteCsvRow(int atlasX, int atlasY, int width, int height, int frames,
                                int flags, int specLayer, int normalLayer, int maskLayer,
                                double heightRange, String albedoHash, String specHash,
                                String normalHash) {
    }

    private record CaptureResult(String report, String summary) {
    }

    @FunctionalInterface
    private interface BoolSupplier {
        boolean getAsBoolean();
    }

    @FunctionalInterface
    private interface StringSupplier {
        String get();
    }
}

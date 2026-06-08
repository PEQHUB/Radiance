package com.radiance.client.debug;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.texture.compat.ResourcePackNaturalTextureResolver;
import com.radiance.client.texture.compat.ResourcePackTextureVariantResolver;
import com.radiance.client.texture.compat.ResourcePackTextureVariantResolver.CompactCtmQuadrants;
import com.radiance.client.texture.compat.ResourcePackTextureVariantResolver.RepeatTextureBasis;
import com.radiance.client.texture.material.ResourceMaterialRegistry;
import com.radiance.client.vertex.PBRVertexConsumer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

public final class MaterialInspectReporter {
    private static final Gson GSON = new Gson();

    private MaterialInspectReporter() {
    }

    public static String materialInspectTargetJson(String requestJson) {
        return runOnClientThread(parseRequest(requestJson), MaterialInspectReporter::buildMaterialInspect);
    }

    public static String ctmFixtureCheckJson(String requestJson) {
        return runOnClientThread(parseRequest(requestJson), MaterialInspectReporter::buildFixtureCheck);
    }

    private static String runOnClientThread(JsonObject request, Inspector inspector) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return GSON.toJson(error("minecraft_client_unavailable"));
            }
            if (client.isOnThread()) {
                return GSON.toJson(inspector.inspect(client, request));
            }
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            client.execute(() -> {
                try {
                    future.complete(inspector.inspect(client, request));
                } catch (Throwable t) {
                    future.complete(error(t.getClass().getSimpleName() + ": " + t.getMessage()));
                }
            });
            return GSON.toJson(future.get(10, TimeUnit.SECONDS));
        } catch (Throwable t) {
            return GSON.toJson(error(t.getClass().getSimpleName() + ": " + t.getMessage()));
        }
    }

    private static JsonObject buildMaterialInspect(MinecraftClient client, JsonObject request) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", "radser_material_inspect_target_v1");
        root.addProperty("ok", false);
        root.add("request", request);
        if (client.world == null) {
            root.addProperty("error", "no_client_world");
            return root;
        }
        Target target = resolveTarget(client, request);
        if (target.error() != null) {
            root.addProperty("error", target.error());
            root.add("target", target.toJson());
            return root;
        }
        BlockState state = client.world.getBlockState(target.pos());
        root.add("target", target.toJson(state));
        if (state.getRenderType() != BlockRenderType.MODEL) {
            root.addProperty("error", "target_not_model_render_type");
            root.addProperty("renderType", state.getRenderType().toString());
            return root;
        }

        QuadSelection selection = selectQuad(client, state, target.face());
        root.add("selectedQuad", selection.toJson());
        if (selection.quad() == null) {
            root.addProperty("error", "no_model_quad_for_target_face");
            return root;
        }

        BakedQuad quad = selection.quad();
        Sprite sprite = quad.getSprite();
        Direction resolveFace = quad.getFace() == null ? target.face() : quad.getFace();
        RepeatTextureBasis repeatBasis = PBRVertexConsumer.repeatTextureBasisForTest(quad.getVertexData());
        JsonObject resolverTrace = ResourcePackTextureVariantResolver.traceBlockSpriteJson(
            sprite, client.world, state, target.pos(), resolveFace, repeatBasis);
        root.add("resolverTrace", resolverTrace);

        CompactCtmQuadrants compact = ResourcePackTextureVariantResolver.resolveCompactCtmQuadrants(
            sprite, client.world, state, target.pos(), resolveFace);
        if (compact != null) {
            root.add("compactCtm", compactJson(compact));
        }

        int materialId = intProperty(resolverTrace, "materialId", -1);
        root.add("materialRecord", ResourceMaterialRegistry.materialRecordJson(materialId));
        root.add("uvTrace", uvTrace(sprite, quad, materialId, target.pos()));
        root.add("displacement", displacementTrace(state, materialId));
        root.add("propagationTrace", propagationTrace(materialId));
        root.add("materialDebugModes", materialDebugModes());
        root.addProperty("ok", true);
        return root;
    }

    private static JsonObject buildFixtureCheck(MinecraftClient client, JsonObject request) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", "radser_ctm_fixture_check_v1");
        root.addProperty("ok", false);
        root.addProperty("liveWorldAvailable", client.world != null);
        root.addProperty("syntheticResolverWorld", false);
        root.addProperty("activatedInMinecraft", false);
        root.addProperty("reason", "fixture command surface installed; fixture pack/world activation is required for live pass");
        JsonArray fixtures = new JsonArray();
        for (String name : List.of("fixed", "random_unweighted", "random_weighted", "repeat_2x2",
            "horizontal", "vertical", "ctm_47", "ctm_compact", "solid_height_displacement",
            "cutout_no_displacement", "fluid_no_displacement")) {
            JsonObject fixture = new JsonObject();
            fixture.addProperty("fixture", name);
            fixture.addProperty("passed", false);
            fixture.addProperty("skipped", true);
            fixture.addProperty("reason", "not_activated_in_live_world");
            fixtures.add(fixture);
        }
        root.add("fixtures", fixtures);
        root.add("request", request);
        return root;
    }

    private static Target resolveTarget(MinecraftClient client, JsonObject request) {
        String activeDimension = client.world.getRegistryKey().getValue().toString();
        String requestedDimension = stringProperty(request, "dimension", "");
        if (!requestedDimension.isBlank() && !requestedDimension.equals(activeDimension)) {
            return new Target(null, null, activeDimension,
                "requested_dimension_not_active:" + requestedDimension);
        }
        BlockPos explicit = explicitPos(request);
        if (explicit != null) {
            Direction face = directionProperty(request, "face", Direction.NORTH);
            return new Target(explicit, face, activeDimension, null);
        }
        HitResult hit = client.crosshairTarget;
        if (hit instanceof BlockHitResult blockHit && hit.getType() != HitResult.Type.MISS) {
            return new Target(blockHit.getBlockPos(), blockHit.getSide(), activeDimension, null);
        }
        return new Target(null, null, activeDimension, "no_crosshair_block_target");
    }

    private static QuadSelection selectQuad(MinecraftClient client, BlockState state, Direction face) {
        try {
            BakedModel model = client.getBakedModelManager().getBlockModels().getModel(state);
            if (model == null) {
                return new QuadSelection(null, face, "model_null");
            }
            List<BakedQuad> faceQuads = model.getQuads(state, face, Random.create(42));
            if (!faceQuads.isEmpty()) {
                return new QuadSelection(faceQuads.get(0), face, "target_face");
            }
            List<BakedQuad> unculled = model.getQuads(state, null, Random.create(42));
            if (!unculled.isEmpty()) {
                return new QuadSelection(unculled.get(0), null, "unculled_fallback");
            }
            return new QuadSelection(null, face, "empty_model_quads");
        } catch (Throwable t) {
            return new QuadSelection(null, face, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static JsonObject compactJson(CompactCtmQuadrants compact) {
        JsonObject json = new JsonObject();
        json.addProperty("tintRgb", compact.tintRgb());
        json.addProperty("tintOverride", compact.tintOverride());
        json.addProperty("alphaMode", compact.alphaMode());
        JsonArray quadrants = new JsonArray();
        int[] ids = compact.spriteIds();
        for (int i = 0; i < ids.length; i++) {
            JsonObject item = new JsonObject();
            item.addProperty("quadrant", i);
            item.addProperty("materialId", ids[i]);
            item.add("materialRecord", ResourceMaterialRegistry.materialRecordJson(ids[i]));
            quadrants.add(item);
        }
        json.add("quadrants", quadrants);
        json.addProperty("subQuadMaterialIdsPreserved", true);
        return json;
    }

    private static JsonObject uvTrace(@Nullable Sprite sprite, BakedQuad quad, int materialId,
        @Nullable BlockPos pos) {
        JsonObject json = new JsonObject();
        int[] data = quad.getVertexData();
        int base = 0;
        float modelU = data.length > base + 4 ? Float.intBitsToFloat(data[base + 4]) : 0.0f;
        float modelV = data.length > base + 5 ? Float.intBitsToFloat(data[base + 5]) : 0.0f;
        float minU = sprite == null ? 0.0f : sprite.getMinU();
        float maxU = sprite == null ? 1.0f : sprite.getMaxU();
        float minV = sprite == null ? 0.0f : sprite.getMinV();
        float maxV = sprite == null ? 1.0f : sprite.getMaxV();
        float localU = PBRVertexConsumer.localSpriteUvForTest(modelU, minU, maxU);
        float localV = PBRVertexConsumer.localSpriteUvForTest(modelV, minV, maxV);
        int shaderSpriteId = ResourceMaterialRegistry.shaderTextureIdForMaterialId(materialId);
        ResourcePackNaturalTextureResolver.NaturalTransform transform =
            ResourcePackNaturalTextureResolver.resolveBlockTransform(sprite, shaderSpriteId, pos, quad.getFace());
        float finalU = transform.transformU(localU, localV);
        float finalV = transform.transformV(localU, localV);
        json.addProperty("modelU", modelU);
        json.addProperty("modelV", modelV);
        json.addProperty("sourceSpriteMinU", minU);
        json.addProperty("sourceSpriteMaxU", maxU);
        json.addProperty("sourceSpriteMinV", minV);
        json.addProperty("sourceSpriteMaxV", maxV);
        json.addProperty("afterLocalRemapU", localU);
        json.addProperty("afterLocalRemapV", localV);
        json.addProperty("naturalTransform", transform.isIdentity()
            ? "identity"
            : "quarterTurns=" + transform.quarterTurns() + ",flipU=" + transform.flipU());
        json.addProperty("finalTriangleU", finalU);
        json.addProperty("finalTriangleV", finalV);
        json.addProperty("compatVirtualMaterialsUseLocalUv", ResourceMaterialRegistry.isCompatVirtualMaterialId(materialId));
        return json;
    }

    private static JsonObject displacementTrace(BlockState state, int materialId) {
        JsonObject record = ResourceMaterialRegistry.materialRecordJson(materialId);
        boolean fluid = state.getFluidState() != null && !state.getFluidState().isEmpty();
        String renderLayer = RenderLayers.getBlockLayer(state).toString().toLowerCase(Locale.ROOT);
        boolean cutout = renderLayer.contains("cutout") || renderLayer.contains("translucent");
        int normalLayer = intProperty(record, "normalLayer", -1);
        int heightRange = intProperty(record, "heightRangePacked", -1);
        boolean materialEligible = boolProperty(record, "displacementEligible", false);
        JsonObject json = new JsonObject();
        json.addProperty("globalEnabled", Options.displacementEnabled);
        json.addProperty("depthCapBlocks", Options.displacementDepthCapPercent / 100.0f);
        json.addProperty("withinFade", true);
        json.addProperty("blockGeometry", true);
        json.addProperty("fluid", fluid);
        json.addProperty("thinCutoutPlant", false);
        json.addProperty("cutoutOrComplexAlpha", cutout);
        json.addProperty("coordinateMode", 0);
        json.addProperty("hasNormalLayer", normalLayer >= 0);
        json.addProperty("normalAlphaKind", heightRange >= 0 ? "non_uniform_or_reported" : "unknown_or_uniform");
        json.addProperty("heightRangePacked", heightRange);
        json.addProperty("eligible", Options.displacementEnabled && materialEligible && !fluid && !cutout);
        json.addProperty("rejectionReason", displacementRejectionReason(materialEligible, fluid, cutout, normalLayer,
            heightRange));
        return json;
    }

    private static JsonObject propagationTrace(int materialId) {
        JsonObject json = new JsonObject();
        json.addProperty("resolverMaterialId", materialId);
        json.addProperty("vertexConsumerPendingTextureOverride", materialId);
        json.addProperty("serializedTriangleTextureId", materialId);
        json.add("nativeTriangleTextureId", JsonNull.INSTANCE);
        json.add("shaderObservedMaterialId", JsonNull.INSTANCE);
        json.addProperty("shaderDebugProxy", "not_captured_bridge_inspector_only");
        json.addProperty("captureTier", "java_inferred_without_native_target_readback");
        return json;
    }

    private static JsonObject materialDebugModes() {
        JsonObject json = new JsonObject();
        JsonArray requested = new JsonArray();
        requested.add("materialId");
        requested.add("pageLayer");
        requested.add("displacementEligible");
        requested.add("displacementHeight");
        json.add("requestedModes", requested);
        json.addProperty("shaderModeSurface", "not_exposed_as_runtime_toggle_in_this_offline_pass");
        json.addProperty("inspectorProvidesEquivalentFields", true);
        return json;
    }

    private static String displacementRejectionReason(boolean materialEligible, boolean fluid, boolean cutout,
        int normalLayer, int heightRange) {
        if (!Options.displacementEnabled) return "global_disabled";
        if (Options.displacementDepthCapPercent <= 0) return "zero_depth";
        if (fluid) return "fluid";
        if (cutout) return "cutout_or_complex_alpha";
        if (normalLayer < 0) return "missing_normal_layer";
        if (heightRange < 0) return "uniform_normal_alpha";
        if (!materialEligible) return "material_policy_disabled";
        return "none";
    }

    private static JsonObject error(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("ok", false);
        json.addProperty("error", message == null ? "unknown" : message);
        return json;
    }

    private static JsonObject parseRequest(String requestJson) {
        try {
            JsonElement parsed = JsonParser.parseString(requestJson == null || requestJson.isBlank()
                ? "{}"
                : requestJson);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ignored) {
            return new JsonObject();
        }
    }

    @Nullable
    private static BlockPos explicitPos(JsonObject request) {
        if (request.has("pos") && request.get("pos").isJsonArray()) {
            JsonArray pos = request.getAsJsonArray("pos");
            if (pos.size() >= 3) {
                return new BlockPos(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt());
            }
        }
        if (request.has("x") && request.has("y") && request.has("z")) {
            return new BlockPos(request.get("x").getAsInt(), request.get("y").getAsInt(),
                request.get("z").getAsInt());
        }
        return null;
    }

    private static Direction directionProperty(JsonObject request, String key, Direction fallback) {
        String raw = stringProperty(request, key, "");
        for (Direction direction : Direction.values()) {
            if (direction.asString().equalsIgnoreCase(raw) || direction.name().equalsIgnoreCase(raw)) {
                return direction;
            }
        }
        return fallback;
    }

    private static String stringProperty(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int intProperty(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean boolProperty(JsonObject object, String key, boolean fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private interface Inspector {
        JsonObject inspect(MinecraftClient client, JsonObject request);
    }

    private record Target(@Nullable BlockPos pos, @Nullable Direction face, String activeDimension,
                          @Nullable String error) {
        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("dimension", activeDimension);
            json.add("pos", posJson(pos));
            json.addProperty("face", face == null ? "" : face.asString());
            if (error != null) {
                json.addProperty("error", error);
            }
            return json;
        }

        JsonObject toJson(BlockState state) {
            JsonObject json = toJson();
            json.addProperty("block", Registries.BLOCK.getId(state.getBlock()).toString());
            json.addProperty("state", state.toString());
            json.addProperty("renderType", state.getRenderType().toString());
            json.addProperty("renderLayer", RenderLayers.getBlockLayer(state).toString());
            json.addProperty("fluid", state.getFluidState() != null && !state.getFluidState().isEmpty());
            return json;
        }
    }

    private record QuadSelection(@Nullable BakedQuad quad, @Nullable Direction cullFace, String source) {
        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("selectionSource", source);
            json.addProperty("cullFace", cullFace == null ? "unculled" : cullFace.asString());
            if (quad == null) {
                json.addProperty("missing", true);
                return json;
            }
            Sprite sprite = quad.getSprite();
            Identifier spriteId = sprite == null || sprite.getContents() == null ? null : sprite.getContents().getId();
            json.addProperty("missing", false);
            json.addProperty("quadFace", quad.getFace().asString());
            json.addProperty("sprite", spriteId == null ? "" : spriteId.toString());
            json.addProperty("spriteNumericId", TextureArrayBridge.resolveRenderableSpriteId(spriteId));
            json.add("uvBounds", uvBounds(quad));
            return json;
        }
    }

    private static JsonArray posJson(@Nullable BlockPos pos) {
        JsonArray array = new JsonArray();
        if (pos != null) {
            array.add(pos.getX());
            array.add(pos.getY());
            array.add(pos.getZ());
        }
        return array;
    }

    private static JsonObject uvBounds(BakedQuad quad) {
        JsonObject json = new JsonObject();
        int[] data = quad.getVertexData();
        float minU = Float.POSITIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4 && data.length >= vertex * 8 + 6; vertex++) {
            int offset = vertex * 8;
            float u = Float.intBitsToFloat(data[offset + 4]);
            float v = Float.intBitsToFloat(data[offset + 5]);
            minU = Math.min(minU, u);
            minV = Math.min(minV, v);
            maxU = Math.max(maxU, u);
            maxV = Math.max(maxV, v);
        }
        json.addProperty("minU", minU);
        json.addProperty("minV", minV);
        json.addProperty("maxU", maxU);
        json.addProperty("maxV", maxV);
        return json;
    }
}

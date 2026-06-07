package com.radiance.client.texture.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import com.radiance.client.texture.IdentifierInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourcePackModelFallback {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcePackModelFallback.class);
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String FALLBACK_BLOCK_TEXTURE = "minecraft:block/dirt";

    private ResourcePackModelFallback() {
    }

    public static boolean isModelJson(Identifier id) {
        if (id == null) {
            return false;
        }
        String path = id.getPath();
        return path.startsWith("models/") && path.endsWith(".json");
    }

    public static Optional<Resource> fallbackForMalformedTopModel(Identifier id,
        List<Resource> resources) {
        if (!isModelJson(id) || resources == null || resources.size() < 2) {
            return Optional.empty();
        }

        Resource top = resources.get(resources.size() - 1);
        Validation topValidation = validateModel(top);
        if (topValidation.valid()) {
            return Optional.empty();
        }

        for (int i = resources.size() - 2; i >= 0; i--) {
            Resource candidate = resources.get(i);
            Validation candidateValidation = validateModel(candidate);
            if (candidateValidation.valid()) {
                LOGGER.warn("[ResourcePackCompat] Falling back from malformed model {} in pack {} "
                        + "to lower-priority pack {}: {}",
                    id, safePackId(top), safePackId(candidate), topValidation.reason());
                return Optional.of(wrapWithIdentifier(id, candidate));
            }
        }

        return Optional.empty();
    }

    public static Optional<Resource> fallbackForMalformedSelectedModel(Identifier id,
        Optional<Resource> selected,
        List<Resource> resources) {
        if (!isModelJson(id) || selected.isEmpty() || resources == null || resources.isEmpty()) {
            return Optional.empty();
        }

        Resource top = selected.get();
        Validation topValidation = validateModel(top);
        if (topValidation.valid()) {
            return Optional.empty();
        }

        for (Resource candidate : resources) {
            Validation candidateValidation = validateModel(candidate);
            if (candidateValidation.valid()) {
                LOGGER.warn("[ResourcePackCompat] Falling back from malformed model {} in pack {} "
                        + "to lower-priority pack {}: {}",
                    id, safePackId(top), safePackId(candidate), topValidation.reason());
                return Optional.of(wrapWithIdentifier(id, candidate));
            }
        }

        return Optional.empty();
    }

    public static Optional<Resource> repairSelectedModelTextureReferences(Identifier id,
        Optional<Resource> selected) {
        if (!isModelJson(id) || selected.isEmpty()) {
            return Optional.empty();
        }

        Resource resource = selected.get();
        String raw;
        try (BufferedReader reader = resource.getReader()) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            raw = builder.toString();
        } catch (IOException e) {
            return Optional.empty();
        }

        JsonElement rootElement;
        try {
            rootElement = JsonParser.parseString(raw);
        } catch (JsonParseException e) {
            return Optional.empty();
        }
        if (!rootElement.isJsonObject()) {
            return Optional.empty();
        }

        JsonObject root = rootElement.getAsJsonObject();
        JsonObject textures = root.has("textures") && root.get("textures").isJsonObject()
            ? root.getAsJsonObject("textures")
            : new JsonObject();
        boolean hadTextures = root.has("textures") && root.get("textures").isJsonObject();

        boolean repaired = repairTextureMap(textures);
        repaired |= repairMissingFaceReferences(root, textures);
        if (!repaired) {
            return Optional.empty();
        }

        if (!hadTextures) {
            root.add("textures", textures);
        }

        String repairedJson = GSON.toJson(root);
        LOGGER.warn("[ResourcePackCompat] Repaired missing texture references in model {} from pack {}",
            id, safePackId(resource));
        return Optional.of(new Resource(resource.getPack(),
            () -> new IdentifierInputStream(
                new ByteArrayInputStream(repairedJson.getBytes(StandardCharsets.UTF_8)), id),
            resource::getMetadata));
    }

    public static Optional<Resource> selectFallbackForTest(Identifier id, List<Resource> resources) {
        return fallbackForMalformedTopModel(id, resources);
    }

    public static Optional<Resource> selectFallbackForTest(Identifier id,
        Optional<Resource> selected,
        List<Resource> resources) {
        return fallbackForMalformedSelectedModel(id, selected, resources);
    }

    public static Optional<Resource> selectRepairedModelForTest(Identifier id,
        Optional<Resource> selected) {
        return repairSelectedModelTextureReferences(id, selected);
    }

    private static Resource wrapWithIdentifier(Identifier id, Resource resource) {
        return new Resource(resource.getPack(),
            () -> new IdentifierInputStream(resource.getInputStream(), id),
            resource::getMetadata);
    }

    private static boolean repairTextureMap(JsonObject textures) {
        boolean repaired = false;
        for (String key : List.copyOf(textures.keySet())) {
            JsonElement value = textures.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                continue;
            }
            String raw = value.getAsString();
            String fixed = repairedTextureToken(raw);
            if (!fixed.equals(raw)) {
                textures.addProperty(key, fixed);
                repaired = true;
            }
        }
        return repaired;
    }

    private static boolean repairMissingFaceReferences(JsonObject root, JsonObject textures) {
        JsonElement elementsElement = root.get("elements");
        if (elementsElement == null || !elementsElement.isJsonArray()) {
            return false;
        }
        boolean repaired = false;
        JsonArray elements = elementsElement.getAsJsonArray();
        for (JsonElement element : elements) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonElement facesElement = element.getAsJsonObject().get("faces");
            if (facesElement == null || !facesElement.isJsonObject()) {
                continue;
            }
            for (JsonElement face : facesElement.getAsJsonObject().asMap().values()) {
                if (!face.isJsonObject()) {
                    continue;
                }
                JsonElement textureElement = face.getAsJsonObject().get("texture");
                if (textureElement == null || !textureElement.isJsonPrimitive()
                    || !textureElement.getAsJsonPrimitive().isString()) {
                    continue;
                }
                String token = textureElement.getAsString();
                if (!token.startsWith("#") || token.length() <= 1) {
                    continue;
                }
                String key = token.substring(1);
                if (textures.has(key) || !isUnsafeMissingTextureReference(key)) {
                    continue;
                }
                textures.addProperty(key, FALLBACK_BLOCK_TEXTURE);
                repaired = true;
            }
        }
        return repaired;
    }

    private static boolean isUnsafeMissingTextureReference(String key) {
        return key.equals("missing") || key.equals("particle") || key.equals("color");
    }

    private static String repairedTextureToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String token = raw.trim();
        if (token.equals("#missing") || token.equals("missing") || token.equals("minecraft:color")) {
            return FALLBACK_BLOCK_TEXTURE;
        }
        if (token.equals("minecraft:block/farmland_dirt")) {
            return "minecraft:block/dirt";
        }
        if (token.equals("minecraft:block/light_blueterracotta")) {
            return "minecraft:block/light_blue_terracotta";
        }
        if (token.startsWith("minecraft:block") && !token.startsWith("minecraft:block/")) {
            return "minecraft:block/" + token.substring("minecraft:block".length());
        }
        return raw;
    }

    private static Validation validateModel(Resource resource) {
        if (resource == null) {
            return Validation.bad("resource is null");
        }

        try (BufferedReader reader = resource.getReader()) {
            JsonUnbakedModel.deserialize(reader);
            return Validation.ok();
        } catch (IOException e) {
            return Validation.bad(e.getMessage());
        } catch (RuntimeException e) {
            String message = e.getMessage();
            return Validation.bad(message == null ? e.getClass().getSimpleName() : message);
        }
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

    private record Validation(boolean valid, String reason) {
        static Validation ok() {
            return new Validation(true, "");
        }

        static Validation bad(String reason) {
            return new Validation(false, reason == null || reason.isBlank() ? "invalid model" : reason);
        }
    }
}

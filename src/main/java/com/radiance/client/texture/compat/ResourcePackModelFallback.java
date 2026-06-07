package com.radiance.client.texture.compat;

import com.radiance.client.texture.IdentifierInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourcePackModelFallback {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcePackModelFallback.class);

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

    public static Optional<Resource> selectFallbackForTest(Identifier id, List<Resource> resources) {
        return fallbackForMalformedTopModel(id, resources);
    }

    private static Resource wrapWithIdentifier(Identifier id, Resource resource) {
        return new Resource(resource.getPack(),
            () -> new IdentifierInputStream(resource.getInputStream(), id),
            resource::getMetadata);
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

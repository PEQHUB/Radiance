package com.radiance.client.texture.v4;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resource.ResourceManager;

/**
 * Dependency graph for a texture load generation.
 *
 * Captures the full set of work items (vanilla sprites, CTM materials,
 * animation frames) and their dependencies. The scheduler uses this
 * to determine upload order and priority.
 *
 * Phase 2/3: The graph is built from the v4 manifest and CTM dependency data.
 * Phase 5: CTM neighbor dependencies are included.
 */
public final class TextureLoadGraph {

    private final long generation;
    private final TextureManifestV4 manifest;
    private final List<WorkItem> workItems;
    private final List<String> warnings;

    private TextureLoadGraph(long generation, TextureManifestV4 manifest,
                              List<WorkItem> workItems, List<String> warnings) {
        this.generation = generation;
        this.manifest = manifest;
        this.workItems = List.copyOf(workItems);
        this.warnings = List.copyOf(warnings);
    }

    public long generation() { return generation; }
    public TextureManifestV4 manifest() { return manifest; }
    public List<WorkItem> workItems() { return workItems; }
    public List<String> warnings() { return warnings; }

    /**
     * Build the load graph from a v4 manifest and resource manager.
     * This is the entry point called from the block atlas bypass hook.
     */
    public static TextureLoadGraph build(ResourceManager resourceManager,
                                          TextureManifestV4 manifest,
                                          long generation) {
        TextureLoadGeneration.requireActive(generation, "TextureLoadGraph.build");

        List<WorkItem> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>(manifest.warnings());

        // Vanilla sprite work items — one per sprite, ordered by tier for efficient upload
        for (TextureManifestV4.SpriteRecord sprite : manifest.sprites()) {
            items.add(new WorkItem(
                WorkItem.Kind.VANILLA_SPRITE,
                sprite.spriteId(),
                sprite.tier(),
                sprite.frameCount(),
                sprite.hasImage(),
                true // visible — vanilla sprites are always first-frame
            ));
        }

        // CTM material work items will be added in Phase 5
        // when the CTM dependency graph is resolved

        return new TextureLoadGraph(generation, manifest, items, warnings);
    }

    /** A single unit of texture upload work. */
    public record WorkItem(
        Kind kind,
        int id,
        TextureTier tier,
        int frameCount,
        boolean hasPixels,
        boolean visible
    ) {
        public enum Kind {
            VANILLA_SPRITE,
            CTM_MATERIAL,
            ANIMATION_FRAME
        }
    }
}

package com.radiance.client.gui.unified;

import java.util.List;

/**
 * Interface for populating the content panel when a tree node is selected.
 * Each old settings sub-screen's addOptions() body becomes a populator implementation.
 */
public interface ContentPopulator {
    /**
     * Populate the content panel with sections and rows.
     *
     * @param panel the content panel to add sections to
     * @param screen the owning unified screen (for screen rebuilds, sub-screen launches, etc.)
     */
    void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen);

    /**
     * Return searchable entries for the unified search overlay.
     * Default returns empty list — populators override to expose their settings.
     *
     * @param nodeId the tree node ID this populator is associated with
     * @param category the display category path (e.g. "Lighting > Area Lights")
     */
    default List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return List.of();
    }
}

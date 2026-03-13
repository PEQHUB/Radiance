package com.radiance.client.gui.unified;

/**
 * Interface for populating the content panel when a tree node is selected.
 * Each old settings sub-screen's addOptions() body becomes a populator implementation.
 */
@FunctionalInterface
public interface ContentPopulator {
    /**
     * Populate the content panel with sections and rows.
     *
     * @param panel the content panel to add sections to
     * @param screen the owning unified screen (for screen rebuilds, sub-screen launches, etc.)
     */
    void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen);
}

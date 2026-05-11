package com.radiance.client.pipeline;

import com.radiance.client.RadianceClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ModuleEntryTest {

    private final Path originalRadianceDir = RadianceClient.radianceDir;

    @AfterEach
    void restoreRadianceDir() {
        RadianceClient.radianceDir = originalRadianceDir;
    }

    @Test
    void discoveredDiskModuleLoadsFromDiscoveredPathEvenWhenResourcePathCollides(
        @TempDir Path tempDir) throws Exception {
        Path radianceDir = tempDir.resolve("radiance");
        Path modulesDir = radianceDir.resolve("modules");
        Files.createDirectories(modulesDir);
        Files.writeString(modulesDir.resolve("tone_mapping.yaml"), """
            name: "disk.module"
            inputImageConfigs:
              - name: "disk_input"
                format: "R8G8B8A8_UNORM"
            outputImageConfigs:
              - name: "disk_output"
                format: "R8G8B8A8_UNORM"
            attributeConfigs: []
            """);
        RadianceClient.radianceDir = radianceDir;

        Map<String, ModuleEntry> entries = ModuleEntry.loadAllModuleEntries();
        Module module = entries.get("disk.module").loadModule();

        assertEquals("disk.module", module.name);
        assertEquals("disk_input", module.inputImageConfigs.get(0).name);
        assertSame(module, module.inputImageConfigs.get(0).owner);
        assertSame(module, module.outputImageConfigs.get(0).owner);
    }

    @Test
    void manuallyCreatedModuleEntryLoadsClasspathResource() {
        ModuleEntry entry = new ModuleEntry(
            "render_pipeline.module.tone_mapping.name",
            "modules/tone_mapping.yaml");

        Module module = entry.loadModule();

        assertEquals("render_pipeline.module.tone_mapping.name", module.name);
        assertEquals("denoised_radiance", module.inputImageConfigs.get(0).name);
        assertSame(module, module.inputImageConfigs.get(0).owner);
        assertSame(module, module.outputImageConfigs.get(0).owner);
    }
}

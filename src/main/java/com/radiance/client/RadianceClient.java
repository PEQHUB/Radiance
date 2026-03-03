package com.radiance.client;

import com.mojang.logging.LogUtils;
import com.radiance.client.option.Options;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.proxy.vulkan.RendererProxy;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import com.radiance.client.gui.DlssMissingScreen;
import com.radiance.client.input.KeyInputHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;

public class RadianceClient implements ClientModInitializer {

    public static final Logger LOGGER = LogUtils.getLogger();
    public static Path radianceDir;

    public static boolean dlssMissing = false;
    public static String dlssDownloadUrl = "";
    public static Path dlssInstallDir;

    @Override
    public void onInitializeClient() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Path mcBaseDir = mc.runDirectory.toPath();
        radianceDir = mcBaseDir.resolve("radiance");
        try {
            Files.createDirectories(radianceDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // core lib
        String osName = System.getProperty("os.name");
        if (osName.toLowerCase().contains("windows")) {
            Path libTargetPath = radianceDir.resolve("core.lib");
            Path libResourcePath = Path.of("core.lib");
            copyFileFromResource(libTargetPath, libResourcePath);

            Path dllTargetPath = radianceDir.resolve("core.dll");
            Path dllResourcePath = Path.of("core.dll");
            copyFileFromResource(dllTargetPath, dllResourcePath);

            // Extract Streamline SDK DLLs (Reflex, DLSS-G) next to core.dll.
            // These are optional — if missing, Reflex simply won't be available.
            for (String slDll : new String[]{
                    "sl.interposer.dll", "sl.common.dll", "sl.reflex.dll",
                    "sl.pcl.dll", "NvLowLatencyVk.dll"}) {
                try {
                    copyFileFromResource(radianceDir.resolve(slDll), Path.of(slDll));
                } catch (RuntimeException e) {
                    LOGGER.warn("Streamline DLL not found in JAR: {} (Reflex will be unavailable)", slDll);
                    break; // If one is missing, they're all missing
                }
            }

            System.load(dllTargetPath.toAbsolutePath().toString());

            dlssDownloadUrl = "https://github.com/NVIDIA/DLSS/tree/main/lib/Windows_x86_64/rel";
            dlssInstallDir = radianceDir;
            if (!recheckDlssFiles()) {
                logMissingDlss(
                    "nvngx_dlss.dll",
                    "nvngx_dlssd.dll",
                    dlssDownloadUrl,
                    radianceDir.toAbsolutePath().toString());
            }
        } else if (osName.toLowerCase().contains("linux")) {
            Path soTargetPath = radianceDir.resolve("libcore.so");
            Path soResourcePath = Path.of("libcore.so");
            copyFileFromResource(soTargetPath, soResourcePath);

            System.load(soTargetPath.toAbsolutePath().toString());

            dlssDownloadUrl = "https://github.com/NVIDIA/DLSS/tree/main/lib/Linux_x86_64/rel";
            dlssInstallDir = radianceDir;
            if (!recheckDlssFiles()) {
                logMissingDlss(
                    "libnvidia-ngx-dlss.so.310.5.3",
                    "libnvidia-ngx-dlssd.so.310.5.3",
                    dlssDownloadUrl,
                    radianceDir.toAbsolutePath().toString());
            }
        } else {
            throw new RuntimeException("The OS " + osName + " is not supported");
        }

        // shaders
        Path shaderTargetPath = radianceDir.resolve("shaders");
        Path shaderResourcePath = Path.of("shaders");
        copyFolderFromResource(shaderTargetPath, shaderResourcePath);

        // modules
        Path moduleTargetPath = radianceDir.resolve("modules");
        Path moduleResourcePath = Path.of("modules");
        copyFolderFromResource(moduleTargetPath, moduleResourcePath);

        RendererProxy.initFolderPath(radianceDir.toAbsolutePath().toString());
        Pipeline.initFolderPath(radianceDir);

        Options.readOptions();

        Pipeline.reloadAllModuleEntries();

        KeyInputHandler.register();

        if (dlssMissing) {
            ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
                private boolean shown = false;

                @Override
                public void onEndTick(MinecraftClient client) {
                    if (!shown && client.currentScreen != null) {
                        shown = true;
                        client.setScreen(new DlssMissingScreen(client.currentScreen));
                    }
                }
            });
        }

        // Send a welcome message when the player first enters a world, telling them about the O menu.
        // Disabled permanently once shown (or if the user turns it off via Welcome Message in the O menu).
        if (Options.showWelcomeMessage) {
            ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
                private boolean shown = false;

                @Override
                public void onEndTick(MinecraftClient client) {
                    if (!shown && client.player != null) {
                        shown = true;
                        Options.showWelcomeMessage = false;
                        Options.overwriteConfig();
                        client.inGameHud.getChatHud().addMessage(
                            Text.translatable("radiance.welcome_message.line1"));
                        client.inGameHud.getChatHud().addMessage(
                            Text.translatable("radiance.welcome_message.line2"));
                    }
                }
            });
        }
    }

    public void copyFileFromResource(Path targetPath, Path resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(toResourcePath(resourcePath))) {
            if (is == null) {
                throw new IOException("Cannot find target path: " + resourcePath);
            }

            Files.createDirectories(targetPath.getParent());
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String toResourcePath(Path path) {
        String joined = StreamSupport.stream(path.spliterator(), false).map(Object::toString)
            .collect(Collectors.joining("/"));
        return "/" + joined;
    }

    public void copyFolderFromResource(Path targetPath, Path resourcePath) {
        String resourcePathStr = toResourcePath(resourcePath);
        URL url = getClass().getResource(resourcePathStr);

        if (url == null) {
            throw new RuntimeException("Resource folder not found: " + resourcePathStr);
        }

        try {
            URI uri = url.toURI();

            if ("jar".equals(uri.getScheme())) {
                JarURLConnection conn = (JarURLConnection) url.openConnection();
                URI jarFileUri = conn.getJarFileURL().toURI();
                URI jarFsUri = URI.create("jar:" + jarFileUri);

                FileSystem fs = null;
                boolean created = false;
                try {
                    try {
                        fs = FileSystems.getFileSystem(jarFsUri);
                    } catch (FileSystemNotFoundException e) {
                        fs = FileSystems.newFileSystem(jarFsUri, Collections.emptyMap());
                        created = true;
                    }

                    Path root = fs.getPath(resourcePathStr);
                    walkAndCopy(root, targetPath, resourcePath);
                } finally {
                    if (created) {
                        try {
                            fs.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            } else {
                Path root = Paths.get(uri);
                walkAndCopy(root, targetPath, resourcePath);
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("Failed to copy resource folder", e);
        }
    }

    private void walkAndCopy(Path walkRoot, Path targetRoot, Path baseResourcePath)
        throws IOException {
        try (Stream<Path> stream = Files.walk(walkRoot)) {
            stream.filter(Files::isRegularFile).forEach(source -> {
                String relativePathStr = walkRoot.relativize(source).toString();
                Path targetFile = targetRoot.resolve(relativePathStr);
                Path childResourcePath = baseResourcePath.resolve(relativePathStr);
                copyFileFromResource(targetFile, childResourcePath);
            });
        }
    }

    /**
     * Re-checks whether DLSS DLL files are present in the radiance directory.
     * Updates {@code dlssMissing} accordingly. Returns true if files are present (DLSS available).
     */
    public static boolean recheckDlssFiles() {
        String osName = System.getProperty("os.name");
        if (osName.toLowerCase().contains("windows")) {
            Path dlssTargetPath = radianceDir.resolve("nvngx_dlss.dll");
            Path dlssDTargetPath = radianceDir.resolve("nvngx_dlssd.dll");
            if (Files.exists(dlssTargetPath) && Files.exists(dlssDTargetPath)) {
                dlssMissing = false;
                return true;
            }
        } else {
            Path dlssTargetPath = radianceDir.resolve("libnvidia-ngx-dlss.so.310.5.3");
            Path dlssDTargetPath = radianceDir.resolve("libnvidia-ngx-dlssd.so.310.5.3");
            if (Files.exists(dlssTargetPath) && Files.exists(dlssDTargetPath)) {
                dlssMissing = false;
                return true;
            }
        }
        dlssMissing = true;
        return false;
    }

    private void logMissingDlss(String file1, String file2, String url, String destFolder) {
        LOGGER.warn("DLSS runtime libraries not found: {} and/or {}", file1, file2);
        LOGGER.warn("DLSS will be unavailable. Download from: {}", url);
        LOGGER.warn("Place the files in: {}", destFolder);
    }
}

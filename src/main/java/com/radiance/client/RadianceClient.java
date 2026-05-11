package com.radiance.client;

import com.mojang.logging.LogUtils;
import com.radiance.client.constant.Constants;
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
    private static final int MC_VERSION_ID = 12001;
    public static Path radianceDir;

    public static boolean dlssMissing = false;
    public static String dlssDownloadUrl = "";
    public static Path dlssInstallDir;

    @Override
    public void onInitializeClient() {
        RadianceState.set(RadianceState.UNINITIALIZED);
        MinecraftClient mc = MinecraftClient.getInstance();
        Path mcBaseDir = mc.runDirectory.toPath();
        radianceDir = mcBaseDir.resolve("radiance");
        try {
            Files.createDirectories(radianceDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String osName = System.getProperty("os.name");
        boolean nativeReady = initializeNativeRenderer(osName);
        boolean resourcesReady = copyRuntimeResources();

        boolean optionsReady = false;
        if (nativeReady && resourcesReady) {
            optionsReady = initializeNativeBackedServices();
        } else if (nativeReady) {
            RadianceState.set(RadianceState.RENDERER_DISABLED);
        }

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

        if (optionsReady) {
            registerWelcomeMessage();
        }
    }

    private boolean initializeNativeRenderer(String osName) {
        if (osName.toLowerCase().contains("windows")) {
            dlssDownloadUrl = "https://github.com/NVIDIA/DLSS/tree/main/lib/Windows_x86_64/rel";
            dlssInstallDir = radianceDir;

            Path dllTargetPath = radianceDir.resolve("core.dll");
            try {
                copyOptionalFileFromResource(radianceDir.resolve("core.lib"), Path.of("core.lib"));
                copyFileFromResource(dllTargetPath, Path.of("core.dll"));

                // Extract Streamline SDK DLLs (Reflex, DLSS-G) next to core.dll.
                // These are optional — if missing, Reflex simply won't be available.
                for (String slDll : new String[]{
                    "sl.interposer.dll", "sl.common.dll", "sl.reflex.dll",
                    "sl.pcl.dll", "NvLowLatencyVk.dll"}) {
                    if (!copyOptionalFileFromResource(radianceDir.resolve(slDll), Path.of(slDll))) {
                        LOGGER.warn("Streamline DLL not found in JAR: {} (Reflex will be unavailable)", slDll);
                        break; // If one is missing, they're all missing
                    }
                }

                System.load(dllTargetPath.toAbsolutePath().toString());
                LOGGER.info("[radiance] System.load succeeded for {}", dllTargetPath.toAbsolutePath());
            } catch (RuntimeException | LinkageError e) {
                LOGGER.error("[radiance] Failed to load native renderer from {}", dllTargetPath.toAbsolutePath(), e);
                RadianceState.set(RadianceState.INIT_FAILED);
                return false;
            }

            if (!recheckDlssFiles()) {
                logMissingDlss("nvngx_dlss.dll", "nvngx_dlssd.dll", dlssDownloadUrl,
                    radianceDir.toAbsolutePath().toString());
            }

            return performHandshake();
        } else if (osName.toLowerCase().contains("linux")) {
            Path soTargetPath = radianceDir.resolve("libcore.so");
            dlssDownloadUrl = "https://github.com/NVIDIA/DLSS/tree/main/lib/Linux_x86_64/rel";
            dlssInstallDir = radianceDir;

            try {
                copyFileFromResource(soTargetPath, Path.of("libcore.so"));
                System.load(soTargetPath.toAbsolutePath().toString());
                LOGGER.info("[radiance] System.load succeeded for {}", soTargetPath.toAbsolutePath());
            } catch (RuntimeException | LinkageError e) {
                LOGGER.error("[radiance] Failed to load native renderer from {}", soTargetPath.toAbsolutePath(), e);
                RadianceState.set(RadianceState.INIT_FAILED);
                return false;
            }

            if (!recheckDlssFiles()) {
                logMissingDlss(
                    "libnvidia-ngx-dlss.so.310.5.3",
                    "libnvidia-ngx-dlssd.so.310.5.3",
                    dlssDownloadUrl,
                    radianceDir.toAbsolutePath().toString());
            }

            return performHandshake();
        } else {
            LOGGER.error("[radiance] The OS {} is not supported", osName);
            RadianceState.set(RadianceState.INIT_FAILED);
            return false;
        }
    }

    private boolean performHandshake() {
        long[] ordinals = Constants.dumpOrdinals();
        int result;
        try {
            result = RendererProxy.handshake(MC_VERSION_ID, ordinals);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.error("[radiance] RendererProxy.handshake could not be called. Renderer disabled.", e);
            RadianceState.set(RadianceState.INIT_FAILED);
            return false;
        }

        LOGGER.info("[radiance] RendererProxy.handshake({}, javaOrdinals.length={}) returned {}",
            MC_VERSION_ID, ordinals.length, result);
        if (result != 0) {
            LOGGER.error("Radiance: native renderer ABI mismatch (code={}). Renderer disabled.", result);
            RadianceState.set(RadianceState.INIT_FAILED);
            return false;
        }

        RadianceState.set(RadianceState.BOOT_OK);
        return true;
    }

    private boolean copyRuntimeResources() {
        Path shaderTargetPath = radianceDir.resolve("shaders");
        Path shaderResourcePath = Path.of("shaders");
        if (!copyRequiredFolderFromResource(shaderTargetPath, shaderResourcePath)) {
            LOGGER.error("[radiance] Required shader resources are missing. Renderer disabled.");
            return false;
        }

        Path moduleTargetPath = radianceDir.resolve("modules");
        Path moduleResourcePath = Path.of("modules");
        if (!copyRequiredFolderFromResource(moduleTargetPath, moduleResourcePath)) {
            LOGGER.error("[radiance] Required pipeline module resources are missing. Renderer disabled.");
            return false;
        }

        return true;
    }

    private boolean initializeNativeBackedServices() {
        try {
            RendererProxy.initFolderPath(radianceDir.toAbsolutePath().toString());
            Pipeline.initFolderPath(radianceDir);
            Options.readOptions();
            Pipeline.reloadAllModuleEntries();
            return true;
        } catch (RuntimeException | LinkageError e) {
            LOGGER.error("[radiance] Native-backed initialization failed. Renderer disabled.", e);
            RadianceState.set(RadianceState.RENDERER_DISABLED);
            return false;
        }
    }

    private void registerWelcomeMessage() {
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

    private boolean copyOptionalFileFromResource(Path targetPath, Path resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(toResourcePath(resourcePath))) {
            if (is == null) {
                return false;
            }

            Files.createDirectories(targetPath.getParent());
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
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

    private boolean copyRequiredFolderFromResource(Path targetPath, Path resourcePath) {
        try {
            copyFolderFromResource(targetPath, resourcePath);
            return true;
        } catch (RuntimeException e) {
            LOGGER.error("[radiance] Resource folder {} could not be copied to {}",
                toResourcePath(resourcePath), targetPath, e);
            return false;
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

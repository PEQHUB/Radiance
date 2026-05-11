package com.radiance.client.pipeline;

import com.radiance.client.RadianceClient;
import com.radiance.client.pipeline.config.ImageConfig;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class ModuleEntry {

    public String name;
    public String resourcePath;
    private final Path filePath;

    public ModuleEntry(String name, String resourcePath) {
        this(name, resourcePath, null);
    }

    ModuleEntry(String name, String resourcePath, Path filePath) {
        this.name = name;
        this.resourcePath = resourcePath;
        this.filePath = filePath;
    }

    public static Map<String, ModuleEntry> loadAllModuleEntries() throws Exception {
        Map<String, ModuleEntry> entries = new HashMap<>();
        String folderName = "modules";

        URL url = RadianceClient.radianceDir.resolve(folderName).toUri().toURL();

        String protocol = url.getProtocol();

        if ("file".equals(protocol)) {
            File dir = new File(url.toURI());
            File[] files = dir.listFiles(
                (d, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));

            if (files != null) {
                for (File file : files) {
                    String relativePath = folderName + "/" + file.getName();
                    try (InputStream is = new FileInputStream(file)) {
                        String yamlName = extractNameFromYaml(is);
                        if (yamlName != null) {
                            entries.put(yamlName,
                                new ModuleEntry(yamlName, relativePath, file.toPath()));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        } else if ("jar".equals(protocol)) {
            JarURLConnection jarConn = (JarURLConnection) url.openConnection();
            try (JarFile jarFile = jarConn.getJarFile()) {
                Enumeration<JarEntry> jarEntries = jarFile.entries();

                while (jarEntries.hasMoreElements()) {
                    JarEntry entry = jarEntries.nextElement();
                    String entryName = entry.getName();

                    if (entryName.startsWith(folderName + "/") && (entryName.endsWith(".yaml")
                        || entryName.endsWith(".yml")) && !entry.isDirectory()) {

                        String suffix = entryName.substring(folderName.length() + 1);
                        if (!suffix.contains("/")) {
                            try (InputStream is = jarFile.getInputStream(entry)) {
                                String yamlName = extractNameFromYaml(is);
                                if (yamlName != null) {
                                    entries.put(yamlName, new ModuleEntry(yamlName, entryName));
                                }
                            }
                        }
                    }
                }
            }
        }

        return entries;
    }

    private static String extractNameFromYaml(InputStream inputStream) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(inputStream);
        if (data != null && data.containsKey("name")) {
            return String.valueOf(data.get("name"));
        } else {
            throw new RuntimeException("Failed to extract module name");
        }
    }

    public Module loadModule() {
        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(Module.class, options));

        try (InputStream inputStream = openModuleStream()) {
            Module module = yaml.load(inputStream);
            for (ImageConfig imageConfig : module.inputImageConfigs) {
                imageConfig.owner = module;
            }
            for (ImageConfig imageConfig : module.outputImageConfigs) {
                imageConfig.owner = module;
            }
            return module;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load module from: " + sourceDescription(), e);
        }
    }

    private InputStream openModuleStream() throws IOException {
        if (this.filePath != null) {
            return Files.newInputStream(this.filePath);
        }

        InputStream inputStream = getClass().getClassLoader()
            .getResourceAsStream(this.resourcePath);
        if (inputStream == null) {
            throw new RuntimeException("Module not found in resource: " + this.resourcePath);
        }
        return inputStream;
    }

    private String sourceDescription() {
        if (this.filePath != null) {
            return this.filePath.toString();
        }
        return this.resourcePath;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.name);
        if (this.resourcePath != null) {
            sb.append(", resourcePath: ").append(this.resourcePath);
        } else {
            sb.append(", resourcePath: null");
        }
        if (this.filePath != null) {
            sb.append(", filePath: ").append(this.filePath);
        }
        return sb.toString();
    }
}

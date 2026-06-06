package com.radiance.client.pipeline;

import com.radiance.client.pipeline.config.AttributeConfig;
import com.radiance.client.pipeline.config.ImageConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.text.Text;

public class Module {

    public String name;
    public List<ImageConfig> inputImageConfigs;
    public List<ImageConfig> outputImageConfigs;
    public List<AttributeConfig> attributeConfigs;
    public List<AttributeConfig> staticAttributeConfigs;
    public Map<String, String> dynamicTranslations = new HashMap<>();
    public Set<String> shaderPackAttributeNames = new HashSet<>();
    public String dynamicAttributeStoragePath;

    // for GUI
    public double x, y;

    @Override
    public String toString() {
        return "name: " + name
            + ", inputImageConfigs: " + inputImageConfigs
            + ", outputImageConfigs: " + outputImageConfigs
            + ", attributeConfigs: " + attributeConfigs;
    }

    public static List<AttributeConfig> copyAttributeConfigs(List<AttributeConfig> source) {
        List<AttributeConfig> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (AttributeConfig attributeConfig : source) {
            if (attributeConfig == null) {
                continue;
            }
            AttributeConfig cloned = new AttributeConfig();
            cloned.type = attributeConfig.type;
            cloned.name = attributeConfig.name;
            cloned.value = attributeConfig.value;
            copy.add(cloned);
        }
        return copy;
    }

    public Text translateText(String key) {
        if (key == null || key.isEmpty()) {
            return Text.empty();
        }
        String translated = dynamicTranslations.get(key);
        return translated != null ? Text.literal(translated) : Text.translatable(key);
    }

    public ImageConfig getInputImageConfig(String name) {
        for (ImageConfig imageConfig : inputImageConfigs) {
            if (imageConfig.name.equals(name)) {
                return imageConfig;
            }
        }
        return null;
    }

    public ImageConfig getOutputImageConfig(String name) {
        for (ImageConfig imageConfig : outputImageConfigs) {
            if (imageConfig.name.equals(name)) {
                return imageConfig;
            }
        }
        return null;
    }
}

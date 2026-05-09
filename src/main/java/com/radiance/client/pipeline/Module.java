package com.radiance.client.pipeline;

import com.radiance.client.pipeline.config.AttributeConfig;
import com.radiance.client.pipeline.config.ImageConfig;
import java.util.List;

public class Module {

    public String name;
    public List<ImageConfig> inputImageConfigs;
    public List<ImageConfig> outputImageConfigs;
    public List<AttributeConfig> attributeConfigs;

    // for GUI
    public double x, y;

    @Override
    public String toString() {
        return "name: " + name
            + ", inputImageConfigs: " + inputImageConfigs
            + ", outputImageConfigs: " + outputImageConfigs
            + ", attributeConfigs: " + attributeConfigs;
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

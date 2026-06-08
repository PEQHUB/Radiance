package com.radiance.client.pipeline.config;

import com.radiance.client.pipeline.Module;

public class ImageConfig {

    public String name;
    public String format;
    public boolean finalOutput = false;

    public Module owner;

    @Override
    public String toString() {
        return "ImageConfig{" + "name='" + name + '\'' + ", format='" + format + '\''
            + ", finalOutput=" + finalOutput;
    }
}

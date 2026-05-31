package com.radiance.client.gui;

public enum AutoPBRMapTab {
    ROUGHNESS("Roughness"),
    NORMAL("Normal"),
    HEIGHT("Height"),
    AO("AO"),
    SPECULAR("Specular");

    public final String label;

    AutoPBRMapTab(String label) {
        this.label = label;
    }
}

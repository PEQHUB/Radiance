package com.radiance.client.materiallab;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MaterialLabProfile {
    public static final int VERSION = 1;

    public int version = VERSION;
    public String stackFingerprint = "";
    public Map<String, MaterialRecipe> recipes = new LinkedHashMap<>();
    public Map<String, String> remapHints = new LinkedHashMap<>();

    public MaterialRecipe recipe(String sprite) {
        MaterialRecipe recipe = recipes.get(sprite);
        return recipe == null ? MaterialRecipe.defaults() : recipe;
    }
}

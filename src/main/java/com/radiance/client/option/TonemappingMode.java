package com.radiance.client.option;

import static com.radiance.client.option.Options.TONEMAP_MODE_PBR_NEUTRAL;
import static com.radiance.client.option.Options.TONEMAP_MODE_REINHARD_EXTENDED;
import static com.radiance.client.option.Options.TONEMAP_MODE_ACES;
import static com.radiance.client.option.Options.TONEMAP_MODE_AGX;
import static com.radiance.client.option.Options.TONEMAP_MODE_LOTTES;
import static com.radiance.client.option.Options.TONEMAP_MODE_FROSTBITE;
import static com.radiance.client.option.Options.TONEMAP_MODE_UNCHARTED2;
import static com.radiance.client.option.Options.TONEMAP_MODE_GT;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.TranslatableOption;

public enum TonemappingMode implements TranslatableOption, StringIdentifiable {
    PBR_NEUTRAL(0, "pbr_neutral", TONEMAP_MODE_PBR_NEUTRAL),
    REINHARD_EXTENDED(1, "reinhard_extended", TONEMAP_MODE_REINHARD_EXTENDED),
    ACES(2, "aces", TONEMAP_MODE_ACES),
    AGX(3, "agx", TONEMAP_MODE_AGX),
    LOTTES(4, "lottes", TONEMAP_MODE_LOTTES),
    FROSTBITE(5, "frostbite", TONEMAP_MODE_FROSTBITE),
    UNCHARTED2(6, "uncharted2", TONEMAP_MODE_UNCHARTED2),
    GT(7, "gt", TONEMAP_MODE_GT);

    public static final Codec<TonemappingMode> Codec =
        StringIdentifiable.createCodec(TonemappingMode::values);
    private final int ordinal;
    private final String name;
    private final String translationKey;

    TonemappingMode(final int ordinal, final String name, final String translationKey) {
        this.ordinal = ordinal;
        this.name = name;
        this.translationKey = translationKey;
    }

    @Override
    public String asString() {
        return this.name;
    }

    @Override
    public int getId() {
        return this.ordinal;
    }

    @Override
    public String getTranslationKey() {
        return this.translationKey;
    }

    public static TonemappingMode byOrdinal(int ordinal) {
        TonemappingMode[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}

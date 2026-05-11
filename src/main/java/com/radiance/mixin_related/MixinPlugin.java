package com.radiance.mixin_related;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class MixinPlugin implements IMixinConfigPlugin {

    /**
     * Per-mixin allowlist (PRD §4.5 / §4.6). Mixins not listed here are skipped at runtime
     * even if `radiance.mixins.json` declares them. Each Implementation Checkpoint adds the
     * mixins it owns. The allowlist is canonical; `radiance.mixins.json` is structural.
     *
     * Current scope: alpha-0 — only the four resource-tracker mixins are applied. Vulkan
     * rendering mixins are added starting in Checkpoint B.
     *
     * NOTE for Checkpoint A: radiance.mixins.json declares ~28 mixins; this allowlist enables
     * only 4. The other ~24 mixins are loaded by Loom but rejected here at runtime. As each
     * is verified against 1.20.1 yarn (yarn-rename verified, target obfuscation mapping
     * confirmed), promote it to this set. Loom's mixin-target warnings (e.g.,
     * GlStateManager._clear, AbstractTexture.setClamp, Screen.applyBlur, GameOptionsScreen
     * @Shadow field) only fire when a mixin is ACTUALLY applied — so they currently appear at
     * compile time but won't trigger runtime errors until the corresponding mixin is added
     * to ENABLED_MIXINS. Address each before promoting.
     */
    public static final java.util.Set<String> ENABLED_MIXINS = java.util.Set.of(
        "com.radiance.mixins.vanilla_resource_tracker.NamespaceResourceManagerMixins",
        "com.radiance.mixins.vanilla_resource_tracker.TextureManagerMixins",
        "com.radiance.mixins.vanilla_resource_tracker.AbstractTextureMixins",
        "com.radiance.mixins.vanilla_resource_tracker.NativeImageMixins"
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return ENABLED_MIXINS.contains(mixinClassName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
        IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
        IMixinInfo mixinInfo) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }
}

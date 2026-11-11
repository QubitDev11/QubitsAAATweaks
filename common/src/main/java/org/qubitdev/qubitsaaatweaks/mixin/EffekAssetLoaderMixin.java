package org.qubitdev.qubitsaaatweaks.mixin;

import com.google.common.collect.BiMap;
import mod.chloeprime.aaaparticles.api.client.EffectHolder;
import mod.chloeprime.aaaparticles.client.loader.EffekAssetLoader;
import net.minecraft.resources.ResourceLocation;
import org.qubitdev.qubitsaaatweaks.QAATClientConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = EffekAssetLoader.class, remap = false)
public class EffekAssetLoaderMixin {

    @Shadow
    @Final
    private BiMap<ResourceLocation, EffectHolder> loadedEffects;

    /**
     * Preload the configured Effekseer effects on resource reload, so the first cast in a fight
     * doesn't hitch while textures/models load.
     */
    @Inject(method = "doPreloading", at = @At("TAIL"))
    private void preloadModEffects(CallbackInfo ci) {
        if (!QAATClientConfig.isPreloadEffectsEnabled()) {
            return;
        }
        for (Map.Entry<ResourceLocation, EffectHolder> entry : this.loadedEffects.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (QAATClientConfig.isNamespaceBloomEnabled(id) && !"test".equals(id.getPath())) {
                entry.getValue().load();
            }
        }
    }
}

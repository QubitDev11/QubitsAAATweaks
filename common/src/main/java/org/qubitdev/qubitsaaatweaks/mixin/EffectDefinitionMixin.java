package org.qubitdev.qubitsaaatweaks.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import mod.chloeprime.aaaparticles.api.client.EffectDefinition;
import mod.chloeprime.aaaparticles.api.client.EffectHolder;
import mod.chloeprime.aaaparticles.api.client.EffectRegistry;
import mod.chloeprime.aaaparticles.api.client.effekseer.EffekseerEffect;
import mod.chloeprime.aaaparticles.api.client.effekseer.EffekseerManager;
import mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter;
import mod.chloeprime.aaaparticles.api.client.effekseer.TextureType;
import net.minecraft.resources.ResourceLocation;
import org.qubitdev.qubitsaaatweaks.ParticleBloomRenderer;
import org.qubitdev.qubitsaaatweaks.QAATClientConfig;
import org.qubitdev.qubitsaaatweaks.helper.IMixinEffectDefinition;
import org.qubitdev.qubitsaaatweaks.helper.IMixinParticleEmitter;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Mixin(value = EffectDefinition.class, remap = false)
public abstract class EffectDefinitionMixin implements IMixinEffectDefinition {

    @Shadow
    public abstract @Nullable ResourceLocation getId();

    @Shadow
    @Final
    private EnumMap<ParticleEmitter.Type, Set<ParticleEmitter>> oneShotEmitters;

    @Shadow
    @Final
    private EnumMap<ParticleEmitter.Type, Map<ResourceLocation, ParticleEmitter>> namedEmitters;

    @Shadow
    @Final
    private static List<EffectDefinition> DEFINITION_BUFFER;

    @Shadow
    @Final
    private static List<ParticleEmitter> EMITTERS_BUFFER;

    @Shadow
    @Final
    private static java.util.function.Supplier<EnumMap<ParticleEmitter.Type, EffekseerManager>> THE_ONE_MANAGERS;

    @Unique
    private @Nullable Boolean qaat$isBloomEligible;

    @Unique
    private boolean qaat$hasDistortion;

    /**
     * Set whenever emitters were (re)created or the bloom blacklist changed, so the next draw re-applies the layer.
     */
    @Unique
    private boolean qaat$layersDirty = true;

    @Override
    public boolean qaat$isBloomEligible() {
        if (this.qaat$isBloomEligible == null) {
            ResourceLocation id = this.getId();
            if (id != null) {
                this.qaat$isBloomEligible = QAATClientConfig.isNamespaceBloomEnabled(id);
            } else {
                return false;
            }
        }
        return this.qaat$isBloomEligible;
    }

    @Override
    public void qaat$invalidateBloomEligibility() {
        this.qaat$isBloomEligible = null;
        this.qaat$assignEmitterLayers();
    }

    /**
     * Pushes the current bloom layer onto every live emitter of this definition and clears the dirty flag.
     */
    @Unique
    private void qaat$assignEmitterLayers() {
        this.qaat$layersDirty = false;
        int targetLayer = this.qaat$isBloomEligible() ? ParticleBloomRenderer.BLOOM_LAYER : 0;
        for (Set<ParticleEmitter> emitters : this.oneShotEmitters.values()) {
            if (emitters != null) {
                for (ParticleEmitter emitter : emitters) {
                    if (emitter instanceof IMixinParticleEmitter mixinEmitter) {
                        mixinEmitter.qaat$setLayer(targetLayer);
                    }
                }
            }
        }
        for (Map<ResourceLocation, ParticleEmitter> emitters : this.namedEmitters.values()) {
            if (emitters != null) {
                for (ParticleEmitter emitter : emitters.values()) {
                    if (emitter instanceof IMixinParticleEmitter mixinEmitter) {
                        mixinEmitter.qaat$setLayer(targetLayer);
                    }
                }
            }
        }
    }

    /**
     * Re-applies the layer of every emitter if they were recreated or the config changed. Called once per definition
     * from {@code draw} instead of from {@code play(Type, ResourceLocation)}, whose descriptor carries a vanilla type
     * that cannot be remapped inside a mixin targeting another mod.
     */
    @Unique
    public void qaat$ensureEmitterLayers() {
        if (this.qaat$layersDirty) {
            this.qaat$assignEmitterLayers();
        }
    }

    @Override
    public boolean qaat$hasDistortion() {
        return this.qaat$hasDistortion;
    }

    @Override
    public boolean qaat$hasActiveEmitters(ParticleEmitter.Type type) {
        Set<ParticleEmitter> oneshot = this.oneShotEmitters.get(type);
        if (oneshot != null && !oneshot.isEmpty()) {
            return true;
        }
        Map<ResourceLocation, ParticleEmitter> named = this.namedEmitters.get(type);
        return named != null && !named.isEmpty();
    }

    @Inject(method = "setEffect", at = @At("TAIL"))
    private void cacheEffectProperties(EffekseerEffect effect, CallbackInfoReturnable<EffectDefinition> cir) {
        this.qaat$isBloomEligible = null;
        this.qaat$hasDistortion = effect != null && effect.textureCount(TextureType.DISTORTION) > 0;
        // setEffect rebuilds the named emitter map, so their layers have to be re-applied on the next draw.
        this.qaat$layersDirty = true;
    }

    @Inject(method = "play(Lmod/chloeprime/aaaparticles/api/client/effekseer/ParticleEmitter$Type;)Lmod/chloeprime/aaaparticles/api/client/effekseer/ParticleEmitter;", at = @At("RETURN"))
    private void assignLayerAnonymous(ParticleEmitter.Type type, CallbackInfoReturnable<ParticleEmitter> cir) {
        if (this.qaat$isBloomEligible()) {
            ParticleEmitter emitter = cir.getReturnValue();
            if (emitter instanceof IMixinParticleEmitter mixinEmitter) {
                mixinEmitter.qaat$setLayer(ParticleBloomRenderer.BLOOM_LAYER);
            }
        }
    }

    /**
     * Kills AAAParticles' per-frame Stream/Optional allocations while it gathers active defs and emitters.
     */
    @Redirect(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lmod/chloeprime/aaaparticles/api/client/EffectRegistry;entries()Ljava/util/Collection;")
    )
    private static Collection<Map.Entry<ResourceLocation, EffectHolder>> skipStreamGathering() {
        return Collections.emptySet();
    }

    /**
     * Fills DEFINITION_BUFFER and EMITTERS_BUFFER with zero Stream/Optional allocations, and sorts out
     * the Layer Parameters for both the default layer and the bloom layer.
     */
    @Inject(
            method = "draw",
            at = @At(value = "INVOKE", target = "LEffekseer/swig/EffekseerManagerCore;SetLayerParameter(IFFFF)V")
    )
    private static void gatherEmittersAndSetupBloomLayer(
            ParticleEmitter.Type type,
            Vector3f front, Vector3f pos,
            int w, int h, float[] camera, float[] projection,
            float deltaFrames, float partialTicks,
            @Nullable RenderTarget background,
            CallbackInfo ci
    ) {
        for (Map.Entry<ResourceLocation, EffectHolder> entry : EffectRegistry.entries()) {
            Optional<EffectDefinition> opt = entry.getValue().lazyGet();
            if (opt.isPresent()) {
                EffectDefinition def = opt.get();
                EffectDefinitionMixin mixinDef = (EffectDefinitionMixin) (Object) def;
                mixinDef.qaat$ensureEmitterLayers();
                DEFINITION_BUFFER.add(def);

                Set<ParticleEmitter> oneshot = mixinDef.oneShotEmitters.get(type);
                if (oneshot != null && !oneshot.isEmpty()) {
                    EMITTERS_BUFFER.addAll(oneshot);
                }
                Map<ResourceLocation, ParticleEmitter> named = mixinDef.namedEmitters.get(type);
                if (named != null && !named.isEmpty()) {
                    EMITTERS_BUFFER.addAll(named.values());
                }
            }
        }

        EffekseerManager manager = THE_ONE_MANAGERS.get().get(type);
        if (manager != null) {
            manager.getImpl().SetLayerParameter(0, pos.x, pos.y, pos.z, 0);
            manager.getImpl().SetLayerParameter(ParticleBloomRenderer.BLOOM_LAYER, pos.x, pos.y, pos.z, 0);
        }
    }

    /**
     * Draws every live layer (default Layer 0 + Bloom Layer 2) onto the main target, then renders
     * only the bloom layer bitmask into the isolated bloom source buffer.
     */
    @Redirect(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lmod/chloeprime/aaaparticles/api/client/effekseer/EffekseerManager;draw()V")
    )
    private static void drawAllLayersAndBloom(
            EffekseerManager manager,
            ParticleEmitter.Type type,
            Vector3f front, Vector3f pos,
            int w, int h, float[] camera, float[] projection,
            float deltaFrames, float partialTicks,
            @Nullable RenderTarget background
    ) {
        boolean hasActiveBloomEmitter = false;
        if (type == ParticleEmitter.Type.WORLD && QAATClientConfig.isParticleBloomEnabled()) {
            for (int i = 0, size = DEFINITION_BUFFER.size(); i < size; i++) {
                EffectDefinition def = DEFINITION_BUFFER.get(i);
                IMixinEffectDefinition ext = (IMixinEffectDefinition) def;
                if (ext.qaat$isBloomEligible() && ext.qaat$hasActiveEmitters(type)) {
                    hasActiveBloomEmitter = true;
                    break;
                }
            }
            if (hasActiveBloomEmitter) {
                ParticleBloomRenderer.prepareBloomSourceTarget();
            }
        }

        manager.getImpl().DrawBack(ParticleBloomRenderer.ALL_LAYERS_MASK);
        manager.getImpl().DrawFront(ParticleBloomRenderer.ALL_LAYERS_MASK);

        if (hasActiveBloomEmitter) {
            ParticleBloomRenderer.renderEffekBloomLayer(manager);
        }
    }
}

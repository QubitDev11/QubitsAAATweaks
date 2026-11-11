package org.qubitdev.qubitsaaatweaks.mixin;

import mod.chloeprime.aaaparticles.api.client.effekseer.EffekseerManager;
import mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter;
import org.qubitdev.qubitsaaatweaks.helper.IMixinParticleEmitter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ParticleEmitter.class, remap = false)
public class ParticleEmitterMixin implements IMixinParticleEmitter {

    @Shadow
    @Final
    public int handle;

    @Shadow
    @Final
    private EffekseerManager manager;

    @Shadow
    private boolean isValid;

    @Override
    public void qaat$setLayer(int layer) {
        if (this.isValid && this.manager != null) {
            this.manager.getImpl().SetLayer(this.handle, layer);
        }
    }
}

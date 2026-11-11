package org.qubitdev.qubitsaaatweaks.helper;

import mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter;

public interface IMixinEffectDefinition {

    boolean qaat$isBloomEligible();

    void qaat$invalidateBloomEligibility();

    boolean qaat$hasDistortion();

    boolean qaat$hasActiveEmitters(ParticleEmitter.Type type);
}

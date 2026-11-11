package org.qubitdev.qubitsaaatweaks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter;
import mod.chloeprime.aaaparticles.client.render.EffekRenderer;
import mod.chloeprime.aaaparticles.client.render.RenderUtil;
import net.minecraft.client.Camera;
import net.minecraft.world.InteractionHand;
import org.qubitdev.qubitsaaatweaks.ParticleBloomRenderer;
import org.qubitdev.qubitsaaatweaks.QAATClientConfig;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EffekRenderer.class, remap = false)
public class EffekRendererMixin {

    /**
     * No hand emitters? Then skip the whole first-person hand Effekseer pass.
     */
    @Inject(method = "onRenderHand", at = @At("HEAD"), cancellable = true)
    private static void skipEmptyHandRender(float partialTick, InteractionHand hand, PoseStack pose, Matrix4f projection, Camera camera, CallbackInfo ci) {
        if (!QAATClientConfig.isSkipIdlePassesEnabled()) {
            return;
        }
        ParticleEmitter.Type type = hand == InteractionHand.MAIN_HAND
                ? ParticleEmitter.Type.FIRST_PERSON_MAINHAND
                : ParticleEmitter.Type.FIRST_PERSON_OFFHAND;
        if (!ParticleBloomRenderer.hasAnyActiveEmitters(type)) {
            ci.cancel();
        }
    }

    /**
     * Skip the world Effekseer pass when nothing is alive out there, and remember which pass we are in.
     * <p>
     * This hooks {@code draw} by name instead of {@code renderWorldEffeks(F...Camera)} so the injection never has to
     * spell out a descriptor containing vanilla types: those are obfuscated differently by every loader (SRG keeps
     * the official class names, Fabric's intermediary does not), and a mod-target mixin cannot be refmap remapped.
     * The depth blits the skipped pass would have done are already neutralised by {@code AAARenderUtilMixin}.
     */
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private static void captureDrawTypeAndSkipIdlePasses(ParticleEmitter.Type type, float partialTick, PoseStack pose, Matrix4f projection, Camera camera, CallbackInfo ci) {
        ParticleBloomRenderer.setCurrentDrawType(type);

        if (!QAATClientConfig.isSkipIdlePassesEnabled()) {
            return;
        }
        if (type != ParticleEmitter.Type.WORLD || ParticleBloomRenderer.hasAnyActiveEmitters(type)) {
            return;
        }
        // Bloom particles may have rendered earlier this frame (e.g. first person effects) - don't drop them.
        if (ParticleBloomRenderer.hasContentThisFrame()) {
            ParticleBloomRenderer.processAndComposite(partialTick);
        }
        ci.cancel();
    }

    /**
     * Ditch the 16x glGetInteger + 16x glPixelStorei calls while drawing - those only matter during texture upload.
     */
    @Redirect(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lmod/chloeprime/aaaparticles/client/render/RenderUtil;runPixelStoreCodeSafely(Ljava/lang/Runnable;)V")
    )
    private static void bypassPixelStorePollingOnDraw(Runnable code) {
        if (QAATClientConfig.isSkipPixelStorePollingOnDrawEnabled()) {
            code.run();
        } else {
            RenderUtil.runPixelStoreCodeSafely(code);
        }
    }

    /**
     * Once Effekseer is done drawing and samplers are back to normal, run the bloom PostChain
     * and composite it onto the active render target.
     */
    @Inject(method = "draw", at = @At("TAIL"))
    private static void compositeParticleBloom(ParticleEmitter.Type type, float partialTick, PoseStack pose, Matrix4f projection, Camera camera, CallbackInfo ci) {
        if (type == ParticleEmitter.Type.WORLD && ParticleBloomRenderer.hasContentThisFrame()) {
            ParticleBloomRenderer.processAndComposite(partialTick);
        }
    }
}

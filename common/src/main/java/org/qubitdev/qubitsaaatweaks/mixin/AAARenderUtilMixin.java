package org.qubitdev.qubitsaaatweaks.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter;
import mod.chloeprime.aaaparticles.client.internal.RenderStateCapture;
import mod.chloeprime.aaaparticles.client.render.RenderUtil;
import org.qubitdev.qubitsaaatweaks.ParticleBloomRenderer;
import org.qubitdev.qubitsaaatweaks.QAATClientConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = RenderUtil.class, remap = false)
public class AAARenderUtilMixin {

    /**
     * Only blit DISTORTION_BACKGROUND's color attachment when a live emitter actually wants distortion.
     * Otherwise just copy the depth over, so Effekseer soft particles keep working for free.
     */
    @Inject(method = "prepareBackgroundBuffer", at = @At("HEAD"), cancellable = true)
    private static void prepareBackgroundOnlyForDistortion(CallbackInfoReturnable<Optional<RenderTarget>> cir) {
        if (!QAATClientConfig.isLazyDistortionBackgroundCopyEnabled()) {
            return;
        }
        if (RenderUtil.isReloadingResourcePacks()) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        if (!ParticleBloomRenderer.hasAnyActiveDistortionEmitters(ParticleBloomRenderer.getCurrentDrawType())) {
            RenderTarget bg = RenderStateCapture.DISTORTION_BACKGROUND;
            RenderTarget main = RenderUtil.MC.getMainRenderTarget();
            RenderUtil.syncStencilState(main, bg);
            RenderUtil.copyDepthSafely(
                    main.frameBufferId,
                    bg.frameBufferId,
                    main.width,
                    main.height,
                    bg.width,
                    bg.height
            );
            cir.setReturnValue(Optional.of(bg));
        }
    }

    /**
     * Skip the fullscreen depth blits into CAPTURED_WORLD_DEPTH_BUFFER / CAPTURED_HAND_DEPTH_BUFFER
     * when nothing of that kind is actually alive.
     */
    @Inject(method = "copyCurrentDepthTo", at = @At("HEAD"), cancellable = true)
    private static void skipUnusedDepthCapture(RenderTarget target, CallbackInfo ci) {
        if (!QAATClientConfig.isSkipIdlePassesEnabled()) {
            return;
        }
        if (target == RenderStateCapture.CAPTURED_WORLD_DEPTH_BUFFER) {
            if (!ParticleBloomRenderer.hasAnyActiveEmitters(ParticleEmitter.Type.WORLD)) {
                ci.cancel();
            }
        } else if (target == RenderStateCapture.CAPTURED_HAND_DEPTH_BUFFER) {
            if (!ParticleBloomRenderer.hasAnyActiveEmitters(ParticleEmitter.Type.FIRST_PERSON_MAINHAND) && !ParticleBloomRenderer.hasAnyActiveEmitters(ParticleEmitter.Type.FIRST_PERSON_OFFHAND)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "pasteToCurrentDepthFrom", at = @At("HEAD"), cancellable = true)
    private static void skipUnusedDepthPaste(RenderTarget source, CallbackInfo ci) {
        if (!QAATClientConfig.isSkipIdlePassesEnabled()) {
            return;
        }
        if (source == RenderStateCapture.CAPTURED_WORLD_DEPTH_BUFFER) {
            if (!ParticleBloomRenderer.hasAnyActiveEmitters(ParticleEmitter.Type.WORLD)) {
                ci.cancel();
            }
        } else if (source == RenderStateCapture.CAPTURED_HAND_DEPTH_BUFFER) {
            if (!ParticleBloomRenderer.hasAnyActiveEmitters(ParticleEmitter.Type.FIRST_PERSON_MAINHAND) && !ParticleBloomRenderer.hasAnyActiveEmitters(ParticleEmitter.Type.FIRST_PERSON_OFFHAND)) {
                ci.cancel();
            }
        }
    }
}

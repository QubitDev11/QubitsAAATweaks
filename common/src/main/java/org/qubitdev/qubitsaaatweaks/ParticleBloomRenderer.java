package org.qubitdev.qubitsaaatweaks;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import mod.chloeprime.aaaparticles.api.client.EffectDefinition;
import mod.chloeprime.aaaparticles.api.client.EffectHolder;
import mod.chloeprime.aaaparticles.api.client.EffectRegistry;
import mod.chloeprime.aaaparticles.api.client.effekseer.EffekseerManager;
import mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter;
import mod.chloeprime.aaaparticles.client.internal.RenderContext;
import mod.chloeprime.aaaparticles.client.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.qubitdev.qubitsaaatweaks.helper.IMixinEffectDefinition;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

import java.util.Map;
import java.util.Optional;

public final class ParticleBloomRenderer {

    public static final int BLOOM_LAYER = 2;
    public static final int BLOOM_LAYER_MASK = 1 << BLOOM_LAYER;
    public static final int ALL_LAYERS_MASK = (1 << 0) | (1 << 1) | BLOOM_LAYER_MASK;

    public static final ResourceLocation PARTICLE_BLOOM_SHADER = QubitsAAATweaks.loc("shaders/post/particle_bloom.json");

    private static final String TARGET_SOURCE = "bloom_source";
    private static final String TARGET_PING = "bloom_ping";
    private static final String TARGET_TIGHT = "bloom_tight";

    @Nullable
    private static PostChain chain;
    @Nullable
    private static ResourceManager chainResourceManager;
    @Nullable
    private static VertexBuffer bloomScratchBuffer;
    private static int chainWidth;
    private static int chainHeight;
    private static boolean loadFailed;

    private static boolean preparedThisFrame;
    private static boolean hasContentThisFrame;
    private static ParticleEmitter.Type currentDrawType = ParticleEmitter.Type.WORLD;

    public static ParticleEmitter.Type getCurrentDrawType() {
        return currentDrawType;
    }

    public static void setCurrentDrawType(ParticleEmitter.Type type) {
        currentDrawType = type;
    }

    /**
     * Our own ParticleRenderType for vanilla particles: draws 'em into the normal particle target
     * AND the isolated bloom buffer, reusing one uploaded VertexBuffer. One upload, two draws.
     */
    public static final ParticleRenderType BLOOM_PARTICLE_SHEET = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textureManager) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            BufferBuilder.RenderedBuffer rendered = tesselator.getBuilder().end();
            if (rendered.isEmpty()) {
                rendered.release();
                return;
            }

            VertexBuffer vb = getBloomScratchBuffer();
            vb.bind();
            vb.upload(rendered);

            Matrix4f modelView = RenderSystem.getModelViewMatrix();
            Matrix4f projection = RenderSystem.getProjectionMatrix();
            var shader = RenderSystem.getShader();

            if (shader != null) {
                vb.drawWithShader(modelView, projection, shader);

                if (QAATClientConfig.isParticleBloomEnabled() && !RenderUtil.isReloadingResourcePacks()) {
                    getOrCreateChain();
                    RenderTarget bloomSource = chain != null ? chain.getTempTarget(TARGET_SOURCE) : null;
                    if (bloomSource != null) {
                        Minecraft mc = Minecraft.getInstance();
                        RenderTarget mainTarget = mc.getMainRenderTarget();
                        int prevReadFbo = GL11.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
                        int prevDrawFbo = GL11.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
                        try {
                            // Bind bloomSource via RenderTarget#bindWrite first so Iris/Oculus marks isMainBound = false
                            // and returns the vanilla particle shader instead of an ExtendedShader that rebinds G-buffers.
                            bloomSource.bindWrite(false);
                            ShaderInstance bloomShader = RenderContext.isIrisShaderEnabled()
                                    ? GameRenderer.getParticleShader()
                                    : shader;
                            if (bloomShader != null && (!RenderContext.isIrisShaderEnabled() || bloomShader != shader)) {
                                prepareBloomSourceTarget();
                                bloomSource.bindWrite(false);
                                GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, bloomSource.frameBufferId);
                                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, bloomSource.frameBufferId);
                                vb.bind();
                                vb.drawWithShader(modelView, projection, bloomShader);
                                hasContentThisFrame = true;
                            }
                        } finally {
                            restoreFramebuffers(mainTarget, prevReadFbo, prevDrawFbo);
                        }
                    }
                }
            }

            VertexBuffer.unbind();
        }

        @Override
        public String toString() {
            return "QUBITSAAATWEAKS_BLOOM_PARTICLE_SHEET";
        }
    };

    private ParticleBloomRenderer() {
    }

    /**
     * Scratch vertex buffer used for the second (bloom only) draw of vanilla particles. Vanilla has no
     * per-format "immediate draw" buffer - that accessor is a Forge patch - so we keep one of our own,
     * which behaves the same on every loader.
     */
    private static VertexBuffer getBloomScratchBuffer() {
        if (bloomScratchBuffer == null) {
            bloomScratchBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        return bloomScratchBuffer;
    }

    public static boolean hasContentThisFrame() {
        return hasContentThisFrame;
    }

    /**
     * Quick pass over loaded EffectDefinitions
     */
    public static boolean hasAnyActiveEmitters(ParticleEmitter.Type type) {
        if (RenderUtil.isReloadingResourcePacks()) {
            return false;
        }
        for (Map.Entry<ResourceLocation, EffectHolder> entry : EffectRegistry.entries()) {
            Optional<EffectDefinition> opt = entry.getValue().lazyGet();
            if (opt.isPresent() && ((IMixinEffectDefinition) opt.get()).qaat$hasActiveEmitters(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * do the live emitters of this type actually have a distortion texture ?
     */
    public static boolean hasAnyActiveDistortionEmitters(ParticleEmitter.Type type) {
        if (RenderUtil.isReloadingResourcePacks()) {
            return false;
        }
        for (Map.Entry<ResourceLocation, EffectHolder> entry : EffectRegistry.entries()) {
            Optional<EffectDefinition> opt = entry.getValue().lazyGet();
            if (opt.isPresent()) {
                IMixinEffectDefinition ext = (IMixinEffectDefinition) opt.get();
                if (ext.qaat$hasDistortion() && ext.qaat$hasActiveEmitters(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Pushes bloom Layer {@link #BLOOM_LAYER} (every bloom-eligible Effekseer effect) into the isolated
     * bloom source FBO, keeping world depth around so stuff behind walls stays hidden.
     */
    public static void renderEffekBloomLayer(EffekseerManager manager) {
        if (!QAATClientConfig.isParticleBloomEnabled() || RenderUtil.isReloadingResourcePacks()) {
            return;
        }

        RenderTarget bloomSource = prepareBloomSourceTarget();
        if (bloomSource == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.getMainRenderTarget();
        int prevReadFbo = GL11.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int prevDrawFbo = GL11.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, bloomSource.frameBufferId);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, bloomSource.frameBufferId);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, bloomSource.frameBufferId);
            manager.getImpl().DrawBack(BLOOM_LAYER_MASK);
            manager.getImpl().DrawFront(BLOOM_LAYER_MASK);
            hasContentThisFrame = true;
        } finally {
            restoreFramebuffers(mainTarget, prevReadFbo, prevDrawFbo);
        }
    }

    /**
     * Runs the multi-scale Gaussian bloom PostChain and slaps the result onto whatever framebuffer
     * is active right now - without nuking the scene's depth or stencil.
     */
    public static void processAndComposite(float partialTick) {
        if (!hasContentThisFrame) {
            preparedThisFrame = false;
            return;
        }

        preparedThisFrame = false;
        hasContentThisFrame = false;

        if (!QAATClientConfig.isParticleBloomEnabled() || RenderUtil.isReloadingResourcePacks()) {
            return;
        }

        getOrCreateChain();
        if (chain == null) {
            return;
        }

        RenderTarget finalBloom = chain.getTempTarget(TARGET_SOURCE);
        if (finalBloom == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.getMainRenderTarget();
        RenderTarget destTarget = Optional.ofNullable(mc.levelRenderer.getParticlesTarget()).orElse(mainTarget);

        int prevReadFbo = GL11.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int prevDrawFbo = GL11.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevActiveTex = GlStateManager._getActiveTexture();
        Matrix4f oldProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting oldSort = RenderSystem.getVertexSorting();

        try {
            BufferUploader.reset();
            RenderUtil.clearSamplerBindings(0);
            GlStateManager._glUseProgram(0);
            GL20C.glUseProgram(0);

            for (int i = 0; i < 8; i++) {
                GlStateManager._activeTexture(GL13C.GL_TEXTURE0 + i);
                GlStateManager._bindTexture(0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
            GlStateManager._activeTexture(GL13C.GL_TEXTURE0);

            // Sync hardware GL states that native Effekseer or Iris passes may have modified outside GlStateManager
            GlStateManager._disableBlend();
            GL11.glDisable(GL11.GL_BLEND);
            RenderSystem.blendEquation(GL30C.GL_FUNC_ADD);
            GL30C.glBlendEquation(GL30C.GL_FUNC_ADD);
            GlStateManager._disableCull();
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_BACK);
            GL11.glFrontFace(GL11.GL_CCW);
            GlStateManager._disableScissorTest();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GlStateManager._disableDepthTest();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GlStateManager._colorMask(true, true, true, true);
            GL11.glColorMask(true, true, true, true);
            GlStateManager._depthMask(true);
            GL11.glDepthMask(true);

            setLinearFiltering();
            chain.process(partialTick);

            // EffectInstance.clear() parked activeTexture on the last sampler unit (GL_TEXTURE1) - yank it back to GL_TEXTURE0
            GlStateManager._activeTexture(GL13C.GL_TEXTURE0);

            compositeBloomToTarget(finalBloom, destTarget);
        } finally {
            GlStateManager._activeTexture(prevActiveTex);
            GlStateManager._depthMask(true);
            GL11.glDepthMask(true);
            GlStateManager._colorMask(true, true, true, true);
            GL11.glColorMask(true, true, true, true);
            RenderSystem.defaultBlendFunc();
            GL30C.glBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE,
                    GL11.GL_ZERO
            );
            RenderSystem.disableBlend();
            GL11.glDisable(GL11.GL_BLEND);
            RenderSystem.enableDepthTest();
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GlStateManager._depthFunc(GL11.GL_LEQUAL);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            RenderSystem.setProjectionMatrix(oldProj, oldSort);

            restoreFramebuffers(mainTarget, prevReadFbo, prevDrawFbo);
        }
    }

    /**
     * Blends the bloom texture onto destTarget with the real screen blend (ONE, ONE_MINUS_SRC_COLOR).
     * Can't use RenderTarget#blitToScreen here: it sets blend state BEFORE ShaderInstance#apply(),
     * so blit_screen.json's SRC_ALPHA/ONE_MINUS_SRC_ALPHA BlendMode would stomp all over ours.
     */
    private static void compositeBloomToTarget(RenderTarget bloomTarget, RenderTarget destTarget) {
        RenderSystem.assertOnRenderThread();
        destTarget.bindWrite(true);

        GlStateManager._colorMask(true, true, true, false);
        GL11.glColorMask(true, true, true, false);
        GlStateManager._disableDepthTest();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager._depthMask(false);
        GL11.glDepthMask(false);
        GlStateManager._disableCull();
        GL11.glDisable(GL11.GL_CULL_FACE);

        Minecraft mc = Minecraft.getInstance();
        ShaderInstance shader = mc.gameRenderer.blitShader;
        if (shader == null) {
            return;
        }

        shader.setSampler("DiffuseSampler", bloomTarget.getColorTextureId());
        float width = (float) destTarget.width;
        float height = (float) destTarget.height;
        Matrix4f ortho = new Matrix4f().setOrtho(0.0F, width, height, 0.0F, 1000.0F, 3000.0F);
        RenderSystem.setProjectionMatrix(ortho, VertexSorting.ORTHOGRAPHIC_Z);
        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(new Matrix4f().translation(0.0F, 0.0F, -2000.0F));
        }
        if (shader.PROJECTION_MATRIX != null) {
            shader.PROJECTION_MATRIX.set(ortho);
        }
        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(1.0F, 1.0F, 1.0F, 1.0F);
        }

        shader.apply();

        // Gotta flip this on AFTER shader.apply() - apply() already ran blit_screen's BlendMode over us
        RenderSystem.enableBlend();
        GL11.glEnable(GL11.GL_BLEND);
        RenderSystem.blendEquation(GL30C.GL_FUNC_ADD);
        GL30C.glBlendEquation(GL30C.GL_FUNC_ADD);
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ZERO,
                GlStateManager.DestFactor.ONE
        );
        GL30C.glBlendFuncSeparate(
                GL11.GL_ONE,
                GL11.GL_ONE_MINUS_SRC_COLOR,
                GL11.GL_ZERO,
                GL11.GL_ONE
        );

        float u = (float) bloomTarget.viewWidth / (float) bloomTarget.width;
        float v = (float) bloomTarget.viewHeight / (float) bloomTarget.height;
        Tesselator tesselator = RenderSystem.renderThreadTesselator();
        BufferBuilder bufferbuilder = tesselator.getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bufferbuilder.vertex(0.0D, height, 0.0D).uv(0.0F, 0.0F).color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(width, height, 0.0D).uv(u, 0.0F).color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(width, 0.0D, 0.0D).uv(u, v).color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(0.0D, 0.0D, 0.0D).uv(0.0F, v).color(255, 255, 255, 255).endVertex();
        BufferUploader.draw(bufferbuilder.end());
        shader.clear();
    }

    @Nullable
    public static RenderTarget prepareBloomSourceTarget() {
        getOrCreateChain();
        if (chain == null) {
            return null;
        }

        RenderTarget bloomSource = chain.getTempTarget(TARGET_SOURCE);
        if (bloomSource == null) {
            return null;
        }

        if (!preparedThisFrame) {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget mainTarget = mc.getMainRenderTarget();

            int prevReadFbo = GL11.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
            int prevDrawFbo = GL11.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
            int prevActiveTex = GlStateManager._getActiveTexture();
            try {
                GlStateManager._colorMask(true, true, true, true);
                GL11.glColorMask(true, true, true, true);
                GlStateManager._depthMask(true);
                GL11.glDepthMask(true);
                GlStateManager._depthFunc(GL11.GL_LEQUAL);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                RenderUtil.syncStencilState(mainTarget, bloomSource);
                bloomSource.clear(Minecraft.ON_OSX);
                RenderUtil.copyDepthSafely(
                        mainTarget.frameBufferId,
                        bloomSource.frameBufferId,
                        mainTarget.width,
                        mainTarget.height,
                        bloomSource.width,
                        bloomSource.height
                );
                preparedThisFrame = true;
            } finally {
                GlStateManager._activeTexture(prevActiveTex);
                restoreFramebuffers(mainTarget, prevReadFbo, prevDrawFbo);
            }
        }

        return bloomSource;
    }

    private static void restoreFramebuffers(RenderTarget mainTarget, int prevReadFbo, int prevDrawFbo) {
        // Bind mainTarget first so Iris/Oculus MixinRenderTarget restores isMainBound = true
        mainTarget.bindWrite(false);
        if (prevDrawFbo > 0 && prevDrawFbo != mainTarget.frameBufferId) {
            GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
        } else if (prevDrawFbo > 0) {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, mainTarget.frameBufferId);
        }
        if (prevReadFbo > 0) {
            GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, prevReadFbo);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, prevReadFbo);
        }
    }

    private static void setLinearFiltering() {
        if (chain == null) {
            return;
        }
        RenderTarget source = chain.getTempTarget(TARGET_SOURCE);
        RenderTarget ping = chain.getTempTarget(TARGET_PING);
        RenderTarget tight = chain.getTempTarget(TARGET_TIGHT);
        if (source != null) {
            source.setFilterMode(GL11.GL_LINEAR);
        }
        if (ping != null) {
            ping.setFilterMode(GL11.GL_LINEAR);
        }
        if (tight != null) {
            tight.setFilterMode(GL11.GL_LINEAR);
        }
    }

    private static void getOrCreateChain() {
        Minecraft mc = Minecraft.getInstance();
        ResourceManager resourceManager = mc.getResourceManager();

        if (resourceManager != chainResourceManager) {
            unload();
            loadFailed = false;
            chainResourceManager = resourceManager;
        }

        if (loadFailed) {
            return;
        }

        RenderTarget mainTarget = mc.getMainRenderTarget();
        int prevReadFbo = GL11.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int prevDrawFbo = GL11.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);

        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), resourceManager, mainTarget, PARTICLE_BLOOM_SHADER);
                resizeChain(mainTarget.width, mainTarget.height);
                setLinearFiltering();
            } else if (mainTarget.width != chainWidth || mainTarget.height != chainHeight) {
                resizeChain(mainTarget.width, mainTarget.height);
                setLinearFiltering();
            }
        } catch (Exception e) {
            QubitsAAATweaks.LOGGER.error("Failed to load Qubit's AAA Tweaks particle bloom post chain '{}'", PARTICLE_BLOOM_SHADER, e);
            loadFailed = true;
            unload();
        } finally {
            restoreFramebuffers(mainTarget, prevReadFbo, prevDrawFbo);
        }
    }

    private static void resizeChain(int width, int height) {
        if (chain != null) {
            chain.resize(width, height);
        }
        chainWidth = width;
        chainHeight = height;
    }

    public static void unload() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        chainWidth = 0;
        chainHeight = 0;
        preparedThisFrame = false;
        hasContentThisFrame = false;
    }
}

package org.qubitdev.qubitsaaatweaks;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry;
import me.shedaniel.clothconfig2.gui.entries.StringListListEntry;
import mod.chloeprime.aaaparticles.api.client.EffectDefinition;
import mod.chloeprime.aaaparticles.api.client.EffectHolder;
import mod.chloeprime.aaaparticles.api.client.EffectRegistry;
import mod.chloeprime.aaaparticles.client.internal.RenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import org.qubitdev.qubitsaaatweaks.helper.IMixinEffectDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Config(name = QubitsAAATweaks.MODID)
public class QAATClientConfig implements ConfigData {

    public enum BloomMode implements SelectionListEntry.Translatable {
        ON("text.autoconfig.qubitsaaatweaks.option.bloomAll.on", "On"),
        ONLY_WHEN_IRIS_SHADER_LOADED("text.autoconfig.qubitsaaatweaks.option.bloomAll.only_when_iris_shader_loaded", "Iris Shaders Only"),
        OFF("text.autoconfig.qubitsaaatweaks.option.bloomAll.off", "Off");

        private final String translationKey;
        private final String fallbackLabel;

        BloomMode(String translationKey, String fallbackLabel) {
            this.translationKey = translationKey;
            this.fallbackLabel = fallbackLabel;
        }

        @Override
        public String getKey() {
            return this.translationKey;
        }

        public Component getDisplayName() {
            return Component.translatableWithFallback(this.translationKey, this.fallbackLabel);
        }
    }

    @Nullable
    private static ConfigHolder<QAATClientConfig> HOLDER;

    private static volatile Set<String> cachedBlacklistedNamespaces = Collections.emptySet();

    @ConfigEntry.Category("bloom")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public BloomMode bloomAll = BloomMode.ONLY_WHEN_IRIS_SHADER_LOADED;

    @ConfigEntry.Category("bloom")
    @ConfigEntry.Gui.Tooltip
    public List<String> bloomBlacklist = new ArrayList<>();

    @ConfigEntry.Category("optimization")
    @ConfigEntry.Gui.Tooltip
    public boolean preloadEffects = true;

    @ConfigEntry.Category("optimization")
    @ConfigEntry.Gui.Tooltip
    public boolean skipIdlePasses = true;

    @ConfigEntry.Category("optimization")
    @ConfigEntry.Gui.Tooltip
    public boolean lazyDistortionBackgroundCopy = true;

    @ConfigEntry.Category("optimization")
    @ConfigEntry.Gui.Tooltip
    public boolean skipPixelStorePollingOnDraw = true;

    public static boolean isValidNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return false;
        }
        for (int i = 0; i < namespace.length(); i++) {
            char c = namespace.charAt(i);
            if (c != '_' && c != '-' && (c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '.') {
                return false;
            }
        }
        return true;
    }

    @Override
    public void validatePostLoad() {
        if (this.bloomAll == null) {
            this.bloomAll = BloomMode.ONLY_WHEN_IRIS_SHADER_LOADED;
        }
        if (this.bloomBlacklist == null) {
            this.bloomBlacklist = new ArrayList<>();
        } else {
            List<String> sanitized = new ArrayList<>(this.bloomBlacklist.size());
            for (String entry : this.bloomBlacklist) {
                if (entry != null) {
                    String trimmed = entry.trim().toLowerCase(Locale.ROOT);
                    if (!trimmed.isEmpty() && isValidNamespace(trimmed)) {
                        sanitized.add(trimmed);
                    }
                }
            }
            this.bloomBlacklist = sanitized;
        }
        refreshCache(this);
    }

    public static synchronized void init() {
        if (HOLDER != null) {
            return;
        }
        HOLDER = AutoConfig.register(QAATClientConfig.class, GsonConfigSerializer::new);
        HOLDER.registerLoadListener((holder, config) -> {
            refreshCache(config);
            return InteractionResult.PASS;
        });
        HOLDER.registerSaveListener((holder, config) -> {
            refreshCache(config);
            return InteractionResult.PASS;
        });
        refreshCache(HOLDER.getConfig());
    }

    public static QAATClientConfig get() {
        if (HOLDER == null) {
            init();
        }
        return HOLDER.getConfig();
    }

    public static void save() {
        if (HOLDER != null) {
            HOLDER.getConfig().validatePostLoad();
            HOLDER.save();
        }
    }

    private static void refreshCache(QAATClientConfig config) {
        if (config.bloomBlacklist == null || config.bloomBlacklist.isEmpty()) {
            cachedBlacklistedNamespaces = Collections.emptySet();
        } else {
            Set<String> set = new HashSet<>(config.bloomBlacklist.size());
            for (String ns : config.bloomBlacklist) {
                if (ns != null) {
                    String trimmed = ns.trim().toLowerCase(Locale.ROOT);
                    if (!trimmed.isEmpty()) {
                        set.add(trimmed);
                    }
                }
            }
            cachedBlacklistedNamespaces = Collections.unmodifiableSet(set);
        }
        invalidateBloomEligibilityCache();
    }

    public static void invalidateBloomEligibilityCache() {
        try {
            for (Map.Entry<ResourceLocation, EffectHolder> entry : EffectRegistry.entries()) {
                Optional<EffectDefinition> opt = entry.getValue().lazyGet();
                if (opt.isPresent() && opt.get() instanceof IMixinEffectDefinition ext) {
                    ext.qaat$invalidateBloomEligibility();
                }
            }
        } catch (Throwable ignored) {
            // EffectRegistry may not be populated yet during early startup
        }
    }

    public static boolean isParticleBloomEnabled() {
        BloomMode mode = get().bloomAll;
        if (mode == null) {
            mode = BloomMode.ONLY_WHEN_IRIS_SHADER_LOADED;
        }
        return switch (mode) {
            case ON -> true;
            case ONLY_WHEN_IRIS_SHADER_LOADED -> RenderContext.isIrisShaderEnabled();
            case OFF -> false;
        };
    }

    /**
     * Checks whether the given effect ID's namespace is allowed to bloom (i.e. not in {@link #bloomBlacklist}).
     */
    public static boolean isNamespaceBloomEnabled(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        get();
        return !cachedBlacklistedNamespaces.contains(id.getNamespace().toLowerCase(Locale.ROOT));
    }

    public static boolean isPreloadEffectsEnabled() {
        return get().preloadEffects;
    }

    public static boolean isSkipIdlePassesEnabled() {
        return get().skipIdlePasses;
    }

    public static boolean isLazyDistortionBackgroundCopyEnabled() {
        return get().lazyDistortionBackgroundCopy;
    }

    public static boolean isSkipPixelStorePollingOnDrawEnabled() {
        return get().skipPixelStorePollingOnDraw;
    }

    public static Screen createConfigScreen(Screen parent) {
        QAATClientConfig config = get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatableWithFallback("text.autoconfig.qubitsaaatweaks.title", "Qubit's AAA Tweaks Config"))
                .setSavingRunnable(QAATClientConfig::save);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory bloomCategory = builder.getOrCreateCategory(
                Component.translatableWithFallback("text.autoconfig.qubitsaaatweaks.category.bloom", "Bloom")
        );

        bloomCategory.addEntry(
                entryBuilder.startEnumSelector(
                                Component.translatableWithFallback("text.autoconfig.qubitsaaatweaks.option.bloomAll", "Bloom All"),
                                BloomMode.class,
                                config.bloomAll != null ? config.bloomAll : BloomMode.ONLY_WHEN_IRIS_SHADER_LOADED
                        )
                        .setDefaultValue(BloomMode.ONLY_WHEN_IRIS_SHADER_LOADED)
                        .setEnumNameProvider(mode -> ((BloomMode) mode).getDisplayName())
                        .setTooltip(Component.translatableWithFallback(
                                "text.autoconfig.qubitsaaatweaks.option.bloomAll.@Tooltip",
                                "Controls when the post-processing bloom pass is active for AAAParticles effects."
                        ))
                        .setSaveConsumer(val -> config.bloomAll = val)
                        .build()
        );

        bloomCategory.addEntry(
                entryBuilder.startStrList(
                                Component.translatableWithFallback("text.autoconfig.qubitsaaatweaks.option.bloomBlacklist", "Bloom blacklist"),
                                new ArrayList<>(config.bloomBlacklist != null ? config.bloomBlacklist : Collections.emptyList())
                        )
                        .setDefaultValue(ArrayList::new)
                        .setExpanded(true)
                        .setCreateNewInstance(NamespaceListCell::new)
                        .setTooltip(Component.translatableWithFallback(
                                "text.autoconfig.qubitsaaatweaks.option.bloomBlacklist.@Tooltip",
                                "Mod namespaces excluded from particle bloom (e.g. 'modid')."
                        ))
                        .setCellErrorSupplier(val -> {
                            if (val == null || val.trim().isEmpty()) {
                                return Optional.empty();
                            }
                            String normalized = val.trim().toLowerCase(Locale.ROOT);
                            if (!isValidNamespace(normalized)) {
                                return Optional.of(Component.translatableWithFallback(
                                        "text.autoconfig.qubitsaaatweaks.error.invalidNamespace",
                                        "Invalid mod namespace (only a-z, 0-9, _, -, . allowed)"
                                ));
                            }
                            return Optional.empty();
                        })
                        .setSaveConsumer(val -> {
                            List<String> sanitized = new ArrayList<>();
                            if (val != null) {
                                for (String s : val) {
                                    if (s != null) {
                                        String trimmed = s.trim().toLowerCase(Locale.ROOT);
                                        if (!trimmed.isEmpty() && isValidNamespace(trimmed)) {
                                            sanitized.add(trimmed);
                                        }
                                    }
                                }
                            }
                            config.bloomBlacklist = sanitized;
                        })
                        .build()
        );

        ConfigCategory optimizationCategory = builder.getOrCreateCategory(
                Component.translatableWithFallback("text.autoconfig.qubitsaaatweaks.category.optimization", "Optimization")
        );

        optimizationCategory.addEntry(
                entryBuilder.startBooleanToggle(
                                Component.translatableWithFallback("text.autoconfig.qubitsaaatweaks.option.preloadEffects", "Preload Effects"),
                                config.preloadEffects
                        )
                        .setDefaultValue(true)
                        .setTooltip(Component.translatableWithFallback(
                                "text.autoconfig.qubitsaaatweaks.option.preloadEffects.@Tooltip",
                                "Pre-initialize Effekseer effects during resource reload to eliminate first-spawn frame stutters."
                        ))
                        .setSaveConsumer(val -> config.preloadEffects = val)
                        .build()
        );

        optimizationCategory.addEntry(
                entryBuilder.startBooleanToggle(
                                Component.translatableWithFallback("text.autoconfig.qubitsaaatweaks.option.skipIdlePasses", "Skip Idle Passes"),
                                config.skipIdlePasses
                        )
                        .setDefaultValue(true)
                        .setTooltip(Component.translatableWithFallback(
                                "text.autoconfig.qubitsaaatweaks.option.skipIdlePasses.@Tooltip",
                                "Skip Effekseer world/hand passes and fullscreen depth blits when no emitters of that type are active."
                        ))
                        .setSaveConsumer(val -> config.skipIdlePasses = val)
                        .build()
        );

        optimizationCategory.addEntry(
                entryBuilder.startBooleanToggle(
                                Component.translatableWithFallback("text.autoconfig.qubitsaaatweaks.option.lazyDistortionBackgroundCopy", "Lazy Distortion Background Copy"),
                                config.lazyDistortionBackgroundCopy
                        )
                        .setDefaultValue(true)
                        .setTooltip(Component.translatableWithFallback(
                                "text.autoconfig.qubitsaaatweaks.option.lazyDistortionBackgroundCopy.@Tooltip",
                                "Only copy the fullscreen color attachment into DISTORTION_BACKGROUND when an active emitter uses a distortion texture."
                        ))
                        .setSaveConsumer(val -> config.lazyDistortionBackgroundCopy = val)
                        .build()
        );

        optimizationCategory.addEntry(
                entryBuilder.startBooleanToggle(
                                Component.translatableWithFallback("text.autoconfig.qubitsaaatweaks.option.skipPixelStorePollingOnDraw", "Skip PixelStore Polling On Draw"),
                                config.skipPixelStorePollingOnDraw
                        )
                        .setDefaultValue(true)
                        .setTooltip(Component.translatableWithFallback(
                                "text.autoconfig.qubitsaaatweaks.option.skipPixelStorePollingOnDraw.@Tooltip",
                                "Bypass redundant glGetInteger and glPixelStorei polling during per-frame Effekseer draw calls."
                        ))
                        .setSaveConsumer(val -> config.skipPixelStorePollingOnDraw = val)
                        .build()
        );

        return builder.build();
    }

    private static class NamespaceListCell extends StringListListEntry.StringListCell {
        private boolean focusOnNextRender;

        public NamespaceListCell(StringListListEntry listListEntry) {
            this("", listListEntry);
        }

        public NamespaceListCell(String value, StringListListEntry listListEntry) {
            super(value, listListEntry);
            this.widget.setHint(
                    Component.translatableWithFallback(
                            "text.autoconfig.qubitsaaatweaks.option.bloomBlacklist.hint",
                            "Enter mod namespace..."
                    ).withStyle(ChatFormatting.DARK_GRAY)
            );
        }

        @Override
        public void onAdd() {
            super.onAdd();
            this.focusOnNextRender = true;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isSelected, float delta) {
            if (this.focusOnNextRender) {
                this.focusOnNextRender = false;
                if (this.listListEntry.getParent() != null) {
                    this.listListEntry.getParent().setFocused(this.listListEntry);
                }
                this.listListEntry.setFocused(this);
                this.setFocused(this.widget);
                this.widget.setFocused(true);
                isSelected = true;
            }
            super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isSelected, delta);
            if (!isSelected && this.listListEntry.isEditable()) {
                int color = this.getConfigError().isPresent() ? 0xFFFF5555 : 0xFF666666;
                graphics.fill(x, y + 12, x + entryWidth - 12, y + 13, color);
            }
        }
    }
}

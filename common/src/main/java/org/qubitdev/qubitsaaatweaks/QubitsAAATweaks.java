package org.qubitdev.qubitsaaatweaks;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * Loader independent core of Qubit's AAA Tweaks.
 * <p>
 * Only the {@code fabric} and {@code forge} projects may touch loader specific classes; everything that is shared
 * (the bloom post chain, the config and the AAAParticles mixins) lives in the common project.
 */
public final class QubitsAAATweaks {

    public static final String MODID = "qubitsaaatweaks";
    public static final String MODNAME = "Qubit's AAA Tweaks";
    public static final Logger LOGGER = LogUtils.getLogger();

    private QubitsAAATweaks() {
    }

    /**
     * Client bootstrap shared by both loaders. Called from the loader entrypoints once the client is ready.
     */
    public static void initClient() {
        QAATClientConfig.init();
    }

    public static ResourceLocation loc(String path) {
        return new ResourceLocation(MODID, path.toLowerCase(Locale.ROOT));
    }
}

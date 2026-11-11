package org.qubitdev.qubitsaaatweaks;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

@Mod(QubitsAAATweaks.MODID)
public class QubitsAAATweaksForge {

    public QubitsAAATweaksForge() {
        // Use Forge to bootstrap the common mod.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> QubitsAAATweaksForge::initClient);
    }

    private static void initClient() {
        QubitsAAATweaks.LOGGER.debug("Initialising {} on Forge", QubitsAAATweaks.MODNAME);

        QubitsAAATweaks.initClient();

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> QAATClientConfig.createConfigScreen(parent))
        );

        // QAATClientCommands is an @Mod.EventBusSubscriber, so Forge wires up the client commands by itself.
    }
}

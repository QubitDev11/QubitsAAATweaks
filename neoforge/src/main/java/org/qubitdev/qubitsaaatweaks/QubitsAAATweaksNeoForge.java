package org.qubitdev.qubitsaaatweaks;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = QubitsAAATweaks.MODID, dist = Dist.CLIENT)
public class QubitsAAATweaksNeoForge {

    public QubitsAAATweaksNeoForge(IEventBus modBus, ModContainer container) {
        QubitsAAATweaks.LOGGER.debug("Initialising {} on NeoForge", QubitsAAATweaks.MODNAME);

        // Use NeoForge to bootstrap the common mod.
        QubitsAAATweaks.initClient();

        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (modContainer, parent) -> QAATClientConfig.createConfigScreen(parent));

        // QAATClientCommands is an @EventBusSubscriber, so NeoForge wires up the client commands by itself.
    }
}

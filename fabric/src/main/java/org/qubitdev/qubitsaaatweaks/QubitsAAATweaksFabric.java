package org.qubitdev.qubitsaaatweaks;

import net.fabricmc.api.ClientModInitializer;

public class QubitsAAATweaksFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        QubitsAAATweaks.LOGGER.debug("Initialising {} on Fabric", QubitsAAATweaks.MODNAME);

        // Use Fabric to bootstrap the common mod.
        QubitsAAATweaks.initClient();
        QAATClientCommands.register();
    }
}

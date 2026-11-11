package org.qubitdev.qubitsaaatweaks;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

/**
 * Fabric bridge for the {@code /qubitsaaatweaks spawn} debug command.
 */
public final class QAATClientCommands {

    private QAATClientCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal(QubitsAAATweaks.MODID)
                        .then(ClientCommandManager.literal("spawn")
                                .executes(ctx -> QAATEffeks.spawnTestInFrontOfPlayer()))
                )
        );
    }
}

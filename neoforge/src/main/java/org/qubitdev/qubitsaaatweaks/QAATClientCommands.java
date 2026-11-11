package org.qubitdev.qubitsaaatweaks;

import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * NeoForge bridge for the {@code /qubitsaaatweaks spawn} debug command.
 */
@EventBusSubscriber(modid = QubitsAAATweaks.MODID, value = Dist.CLIENT)
public final class QAATClientCommands {

    private QAATClientCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(QubitsAAATweaks.MODID)
                .then(Commands.literal("spawn").executes(ctx -> QAATEffeks.spawnTestInFrontOfPlayer()))
        );
    }
}

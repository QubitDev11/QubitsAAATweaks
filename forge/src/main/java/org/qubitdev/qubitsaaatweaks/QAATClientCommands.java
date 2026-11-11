package org.qubitdev.qubitsaaatweaks;

import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge bridge for the {@code /qubitsaaatweaks spawn} debug command.
 */
@Mod.EventBusSubscriber(modid = QubitsAAATweaks.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
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

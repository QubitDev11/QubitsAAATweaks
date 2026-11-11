package org.qubitdev.qubitsaaatweaks;

import com.mojang.brigadier.Command;
import mod.chloeprime.aaaparticles.api.common.AAALevel;
import mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class QAATEffeks {

    public static final ParticleEmitterInfo TEST = create("test");

    private QAATEffeks() {
    }

    /**
     * Spawns the bundled test effect a few blocks in front of the local player. Shared by the loader specific
     * client command implementations.
     *
     * @return Brigadier's {@code SINGLE_SUCCESS} when the effect got spawned, {@code 0} when there is no player.
     */
    public static int spawnTestInFrontOfPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return 0;
        }

        Vec3 spawnPos = player.getEyePosition().add(player.getViewVector(1.0F).scale(5));
        ParticleEmitterInfo info = TEST.clone().position(spawnPos).scale(1.0F);
        AAALevel.addParticle(minecraft.level, info);

        return Command.SINGLE_SUCCESS;
    }

    private static ParticleEmitterInfo create(String id) {
        return new ParticleEmitterInfo(QubitsAAATweaks.loc(id));
    }
}

package org.qubitdev.qubitsaaatweaks;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Hooks the Cloth Config screen into Mod Menu. Optional at runtime: the {@code modmenu} entrypoint is only
 * resolved when Mod Menu itself is installed.
 */
public class QubitsAAATweaksModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> QAATClientConfig.createConfigScreen(parent);
    }
}

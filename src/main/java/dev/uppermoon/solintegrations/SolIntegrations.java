package dev.uppermoon.solintegrations;

import dev.uppermoon.solintegrations.config.SolIntegrationsConfig;
import dev.uppermoon.solintegrations.integration.tfc.TfcBridgeOptions;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(SolIntegrations.MOD_ID)
public final class SolIntegrations {
    public static final String MOD_ID = "solintegrations";
    public SolIntegrations() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SolIntegrationsConfig.SPEC);
        TfcBridgeOptions.install(SolIntegrationsConfig::tfcEnabled,
            SolIntegrationsConfig::positiveMinimumEnabled, SolIntegrationsConfig::minimumThreshold);
    }
}

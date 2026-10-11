package dev.uppermoon.solintegrations.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-owned settings, synchronized by Forge to connected clients. */
public final class SolIntegrationsConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue TFC;
    private static final ForgeConfigSpec.BooleanValue BACKPACK;
    private static final ForgeConfigSpec.BooleanValue POSITIVE_MINIMUM;
    private static final ForgeConfigSpec.DoubleValue MINIMUM_THRESHOLD;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("integrations");
        TFC = builder.comment("Restore native TFC food handling while applying Classic hunger depletion.").define("tfc", true);
        BACKPACK = builder.comment("Try eligible Sophisticated feeding candidates in least-depleted order.").define("sophisticatedFeeding", true);
        builder.pop().push("rounding");
        POSITIVE_MINIMUM = builder.comment("Optional TFC minimum of one hunger when a positive multiplier rounds to zero. Never changes Classic's own minimum setting.").define("positiveHungerMinimum", false);
        MINIMUM_THRESHOLD = builder.comment("Minimum positive multiplier eligible for the addon floor.").defineInRange("minimumMultiplier", 0.0D, 0.0D, 1.0D);
        SPEC = builder.pop().build();
    }
    private SolIntegrationsConfig() {}
    public static boolean tfcEnabled() { return TFC.get(); }
    public static boolean backpackSelectionEnabled() { return BACKPACK.get(); }
    public static boolean positiveMinimumEnabled() { return POSITIVE_MINIMUM.get(); }
    public static double minimumThreshold() { return MINIMUM_THRESHOLD.get(); }
}

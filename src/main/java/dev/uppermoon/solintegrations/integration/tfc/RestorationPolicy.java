package dev.uppermoon.solintegrations.integration.tfc;

/** Pure numeric policy; vanilla saturation gain is hunger * modifier * 2. */
public final class RestorationPolicy {
    private RestorationPolicy() {}

    /**
     * Preserve Classic's own rounded result, with an optional floor for small,
     * strictly positive multipliers. The addon floor never revives depleted food.
     */
    public static int hunger(int base, float multiplier, int classicResult,
                             boolean minimumEnabled, double minimumThreshold) {
        if (base <= 0) {
            return 0;
        }
        if (minimumEnabled && classicResult == 0 && Float.isFinite(multiplier)
                && multiplier > 0 && multiplier >= minimumThreshold) {
            return 1;
        }
        return classicResult;
    }

    /** Keep the modifier fixed: gain becomes native saturation * effective/base hunger. */
    public static float saturationModifier(int baseHunger, float nativeSaturation) {
        return baseHunger > 0 ? nativeSaturation / (2.0F * baseHunger) : 0.0F;
    }
}

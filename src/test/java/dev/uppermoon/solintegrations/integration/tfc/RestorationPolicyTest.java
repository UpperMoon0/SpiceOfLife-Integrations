package dev.uppermoon.solintegrations.integration.tfc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestorationPolicyTest {
    @Test
    void preservesClassicTruncationAtHighPercentage() {
        // 95% of 4 is 3.8, and Classic's int cast gives 3, not 4.
        assertEquals(3, RestorationPolicy.hunger(4, 0.95F, 3, false, 0));
    }

    @Test
    void lowPositiveMultiplierStaysZeroByDefault() {
        assertEquals(0, RestorationPolicy.hunger(4, 0.05F, 0, false, 0));
    }

    @Test
    void optInFloorRequiresStrictlyPositiveMultiplierAndThreshold() {
        assertEquals(1, RestorationPolicy.hunger(4, 0.05F, 0, true, 0.01));
        assertEquals(0, RestorationPolicy.hunger(4, 0.05F, 0, true, 0.10));
        assertEquals(0, RestorationPolicy.hunger(4, 0.0F, 0, true, 0.0));
        assertEquals(0, RestorationPolicy.hunger(4, Float.NaN, 0, true, 0.0));
    }

    @Test
    void doesNotInventHungerForZeroHungerFood() {
        assertEquals(0, RestorationPolicy.hunger(0, 0.5F, 1, true, 0));
    }

    @Test
    void preservesExplicitClassicMinimumSetting() {
        // A user-enabled Classic guarantee is separate from the addon's option.
        assertEquals(1, RestorationPolicy.hunger(4, 0, 1, false, 0));
    }

    @Test
    void saturationUsesRoundedHungerRatio() {
        float modifier = RestorationPolicy.saturationModifier(4, 2.4F);
        assertEquals(1.8F, 3 * modifier * 2.0F, 0.00001F);
        assertEquals(0.0F, 0 * modifier * 2.0F, 0.00001F);
        assertEquals(0.0F, RestorationPolicy.saturationModifier(0, 2.4F));
    }
}

package dev.uppermoon.solintegrations.integration.backpack;

import java.util.function.BooleanSupplier;

/** Applies the native feeding gates before another mod's early consumption. */
public final class NativeFeedingEligibility {
    private NativeFeedingEligibility() {
    }

    public static boolean allows(BooleanSupplier edible, BooleanSupplier filter,
                                 BooleanSupplier hungerThreshold, BooleanSupplier hurtException) {
        return edible.getAsBoolean() && filter.getAsBoolean()
                && (hungerThreshold.getAsBoolean() || hurtException.getAsBoolean());
    }
}

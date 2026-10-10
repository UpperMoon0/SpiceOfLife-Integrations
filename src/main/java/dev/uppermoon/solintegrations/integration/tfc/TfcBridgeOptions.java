package dev.uppermoon.solintegrations.integration.tfc;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/** Late-bound options keep optional TFC classes out of the common config's linkage. */
public final class TfcBridgeOptions {
    private static BooleanSupplier enabled = () -> true;
    private static BooleanSupplier positiveMinimum = () -> false;
    private static DoubleSupplier minimumThreshold = () -> 0.0D;

    private TfcBridgeOptions() {}

    public static void install(BooleanSupplier enabledOption, BooleanSupplier minimumOption,
                               DoubleSupplier thresholdOption) {
        enabled = Objects.requireNonNull(enabledOption);
        positiveMinimum = Objects.requireNonNull(minimumOption);
        minimumThreshold = Objects.requireNonNull(thresholdOption);
    }

    public static boolean enabled() {
        return enabled.getAsBoolean();
    }

    public static boolean positiveMinimum() {
        return positiveMinimum.getAsBoolean();
    }

    public static double minimumThreshold() {
        return minimumThreshold.getAsDouble();
    }
}

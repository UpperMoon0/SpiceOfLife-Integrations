package dev.uppermoon.solintegrations.mixin;

import java.util.function.Function;

/** Keeps optional-mod gating testable without loading Minecraft or integrations. */
public final class SupportedVersions {
    private SupportedVersions() {}
    public static boolean matches(String actual, String expected) {
        return actual != null && (actual.equals(expected) || actual.equals("1.20.1-" + expected));
    }
    public static boolean allows(String mixin, Function<String, String> versions) {
        if (!matches(versions.apply("solclassic"), "2.13")) return false;
        if (mixin.endsWith(".SophisticatedFoodEligibilityMixin") || mixin.endsWith(".ClassicFeedingNutritionMixin")) {
            return matches(versions.apply("tfc"), "3.2.25")
                    && matches(versions.apply("sophisticatedcore"), "1.3.84.2308");
        }
        if (mixin.contains(".tfc.")) return matches(versions.apply("tfc"), "3.2.25");
        if (mixin.contains(".backpack.")) return matches(versions.apply("sophisticatedcore"), "1.3.84.2308");
        return false;
    }
}

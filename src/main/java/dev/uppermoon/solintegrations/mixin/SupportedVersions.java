package dev.uppermoon.solintegrations.mixin;

import java.util.List;
import java.util.function.Function;

/** Presence gate only: optional compatibility is determined by IntegrationContract. */
public final class SupportedVersions {
    private SupportedVersions() {}
    public static boolean matches(String actual, String expected) {
        return actual != null && (actual.equals(expected) || actual.equals("1.20.1-" + expected));
    }
    public static List<String> optionalMods(String mixin) {
        if (mixin.equals("dev.uppermoon.solintegrations.mixin.integration.tfc.SophisticatedFoodEligibilityMixin") || mixin.equals("dev.uppermoon.solintegrations.mixin.integration.tfc.ClassicFeedingNutritionMixin"))
            return List.of("tfc", "sophisticatedcore");
        return switch (mixin) {
            case "dev.uppermoon.solintegrations.mixin.integration.tfc.PlayerConsumptionMixin",
                 "dev.uppermoon.solintegrations.mixin.integration.tfc.ClassicEffectiveFoodMixin",
                 "dev.uppermoon.solintegrations.mixin.integration.tfc.TfcFoodDataRestorationMixin",
                 "dev.uppermoon.solintegrations.mixin.integration.tfc.WickerBasketTfcMixin" -> List.of("tfc");
            case "dev.uppermoon.solintegrations.mixin.integration.backpack.FeedingUpgradeOrderMixin" -> List.of("sophisticatedcore");
            default -> List.of();
        };
    }
    public static boolean allows(String mixin, Function<String, String> versions) {
        if (!matches(versions.apply("solclassic"), "2.13")) return false;
        List<String> optional = optionalMods(mixin);
        return !optional.isEmpty() && optional.stream().allMatch(id -> {
            String version = versions.apply(id);
            return version != null && !version.isBlank();
        });
    }
}

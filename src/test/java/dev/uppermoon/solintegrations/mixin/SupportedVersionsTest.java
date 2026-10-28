package dev.uppermoon.solintegrations.mixin;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupportedVersionsTest {
    private static final String TFC = "dev.uppermoon.solintegrations.mixin.integration.tfc.PlayerConsumptionMixin";
    private static final String BACKPACK = "dev.uppermoon.solintegrations.mixin.integration.backpack.FeedingUpgradeOrderMixin";
    @Test void allOptionalModsAbsent() { assertFalse(SupportedVersions.allows(TFC, Map.of("solclassic", "2.13")::get)); }
    @Test void absentClassicNeverLinksIntegrations() { assertFalse(SupportedVersions.allows(TFC, Map.of("tfc", "3.2.25")::get)); }
    @Test void supportedTfc() { assertTrue(SupportedVersions.allows(TFC, Map.of("solclassic", "2.13", "tfc", "3.2.25")::get)); }
    @Test void futureTfcReachesApiInspection() { assertTrue(SupportedVersions.allows(TFC, Map.of("solclassic", "2.13", "tfc", "3.3.0")::get)); }
    @Test void modulesAreIndependent() { var versions = Map.of("solclassic", "2.13", "sophisticatedcore", "1.3.84.2308"); assertTrue(SupportedVersions.allows(BACKPACK, versions::get)); assertFalse(SupportedVersions.allows(TFC, versions::get)); }
    @Test void supportedPrefixOnly() { assertTrue(SupportedVersions.matches("1.20.1-3.2.25", "3.2.25")); assertFalse(SupportedVersions.matches("3.2.250", "3.2.25")); assertFalse(SupportedVersions.matches("3.2.25-beta", "3.2.25")); }
    @Test void unknownMixinDenied() { assertFalse(SupportedVersions.allows("unrecognized.Mixin", Map.of("solclassic", "2.13")::get)); }
    @Test void combinedFeedingRequiresBothOptionalMods() {
        String mixin = "dev.uppermoon.solintegrations.mixin.integration.tfc.SophisticatedFoodEligibilityMixin";
        assertFalse(SupportedVersions.allows(mixin, Map.of("solclassic", "2.13", "tfc", "3.2.25")::get));
        assertFalse(SupportedVersions.allows(mixin, Map.of("solclassic", "2.13", "sophisticatedcore", "1.3.84.2308")::get));
        assertTrue(SupportedVersions.allows(mixin, Map.of("solclassic", "2.13", "tfc", "3.2.25", "sophisticatedcore", "1.3.84.2308")::get));
    }
    @Test void futureCoreReachesApiInspection() {
        String mixin = "dev.uppermoon.solintegrations.mixin.integration.tfc.ClassicFeedingNutritionMixin";
        assertTrue(SupportedVersions.allows(mixin, Map.of("solclassic", "2.13", "tfc", "3.2.25", "sophisticatedcore", "1.5.16.2433")::get));
    }
}

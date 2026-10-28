package dev.uppermoon.solintegrations.gametest.backpack;

import dev.uppermoon.solintegrations.integration.backpack.*;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

/** Discovery stays loadable when both optional Sophisticated mods are absent. */
@GameTestHolder("solintegrations")
@PrefixGameTestTemplate(false)
public final class BackpackIntegrationGameTests {
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void optionalCoreActuallyAppliesFeedingMixin(GameTestHelper helper) throws ClassNotFoundException {
        if (ModList.get().isLoaded("sophisticatedcore")) {
            Class<?> wrapper = Class.forName("net.p3pp3rf1y.sophisticatedcore.upgrades.feeding.FeedingUpgradeWrapper");
            helper.assertTrue(Arrays.stream(wrapper.getDeclaredMethods())
                            .anyMatch(method -> method.getName().contains("solintegrations$preferLeastDepleted")),
                    "The actual loaded Core feeding wrapper must contain the transformed addon handler");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void feedingCyclesLeastDepletedFoodsWithoutMovingSlots(GameTestHelper helper) {
        if (hasVanillaFeedingItem()) BackpackGameTestCases.cycling(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void ordinaryFilterDenialFallsThroughToEligibleFood(GameTestHelper helper) {
        if (hasVanillaFeedingItem()) BackpackGameTestCases.ordinaryFilterFallback(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void ordinaryHungerDenialFallsThroughToSmallerFood(GameTestHelper helper) {
        if (hasVanillaFeedingItem()) BackpackGameTestCases.ordinaryHungerFallback(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void disabledSelectionRestoresNativePhysicalSlotOrder(GameTestHelper helper) {
        if (hasVanillaFeedingItem()) BackpackGameTestCases.disabledSelection(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void basketOnlyInsideBackpackConsumesAndRecordsExactlyOnce(GameTestHelper helper) {
        if (hasVanillaFeedingItem()) BackpackGameTestCases.backpackOnlyBasket(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void basketFilterDenialFallsThroughWithoutEatingItsContents(GameTestHelper helper) {
        if (hasVanillaFeedingItem()) BackpackGameTestCases.basketFallback(helper, false);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void basketMealHungerDenialFallsThroughWithoutEatingItsContents(GameTestHelper helper) {
        if (hasVanillaFeedingItem()) BackpackGameTestCases.basketFallback(helper, true);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nativeTfcFeedingCyclesPositiveDepletedMeals(GameTestHelper helper) {
        if (hasFeedingItem() && ModList.get().isLoaded("tfc")) TfcBackpackGameTestCases.nativeCycling(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void positiveDepletedTfcBasketOnlyInBackpackRestoresAndRecordsOnce(GameTestHelper helper) {
        if (hasFeedingItem() && ModList.get().isLoaded("tfc")) TfcBackpackGameTestCases.nativeBasket(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nativeTfcFilterDenialFallsThroughToPositiveDepletedFood(GameTestHelper helper) {
        if (hasFeedingItem() && ModList.get().isLoaded("tfc")) TfcBackpackGameTestCases.nativeFilterFallback(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nativePositiveMinimumFeedsOnceAndLeavesClassicMathUnchanged(GameTestHelper helper) {
        if (hasFeedingItem() && ModList.get().isLoaded("tfc")) TfcBackpackGameTestCases.positiveMinimum(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nativeZeroMultiplierNeverFeedsEvenWithPositiveMinimum(GameTestHelper helper) {
        if (hasFeedingItem() && ModList.get().isLoaded("tfc")) TfcBackpackGameTestCases.zeroMultiplier(helper);
        helper.succeed();
    }

    private static boolean hasVanillaFeedingItem() {
        return hasFeedingItem() && !ModList.get().isLoaded("tfc");
    }

    private static boolean hasFeedingItem() {
        return ModList.get().isLoaded("sophisticatedcore") && ModList.get().isLoaded("sophisticatedbackpacks");
    }
}

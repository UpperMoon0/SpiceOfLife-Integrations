package dev.uppermoon.solintegrations.gametest.tfc;

import dev.uppermoon.solintegrations.integration.tfc.*;

import com.github.leopoko.solclassic.network.FoodHistoryHolder;
import com.github.leopoko.solclassic.utils.FoodCalculator;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** This discovery class deliberately has no TFC field types or imports. */
@GameTestHolder("solintegrations")
@PrefixGameTestTemplate(false)
public final class FoodIntegrationGameTests {
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void playerConsumptionUsesAvailableIntegration(GameTestHelper helper) {
        if (ModList.get().isLoaded("tfc")) {
            TfcGameTestCases.playerConsumption(helper, false);
        } else {
            FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "sol_no_tfc"));
            FoodHistoryHolder.INSTANCE.resetFoodHistory(player);
            player.getFoodData().setFoodLevel(2);
            player.getFoodData().setSaturation(0);
            ItemStack apple = new ItemStack(Items.APPLE);
            int expected = FoodCalculator.CalculateNutrition(4, FoodCalculator.CalculateMultiplier(apple, player));
            player.eat(helper.getLevel(), apple);
            helper.assertTrue(player.getFoodData().getFoodLevel() == 2 + expected, "Classic hunger must work without optional TFC");
            helper.assertTrue(apple.isEmpty(), "The actual apple must be consumed once");
            helper.assertTrue(FoodHistoryHolder.INSTANCE.getFoodHistory(player).consumedItems.size() == 1, "Classic history must record once without TFC");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tfcRepeatedMealPreservesNativeWaterAndNutrients(GameTestHelper helper) {
        if (ModList.get().isLoaded("tfc")) TfcGameTestCases.playerConsumption(helper, true);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tfcDisabledBridgeRetainsUpstreamBehavior(GameTestHelper helper) {
        if (ModList.get().isLoaded("tfc")) TfcGameTestCases.disabledBridge(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tfcRottenFoodRecordsOnceWithoutRestoration(GameTestHelper helper) {
        if (ModList.get().isLoaded("tfc")) TfcGameTestCases.rottenConsumption(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tfcBasketDelegationConsumesAndRecordsOnce(GameTestHelper helper) {
        if (ModList.get().isLoaded("tfc")) TfcGameTestCases.basketConsumption(helper, false, false);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tfcDirectBasketConsumesAndRecordsOnce(GameTestHelper helper) {
        if (ModList.get().isLoaded("tfc")) TfcGameTestCases.basketConsumption(helper, true, false);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tfcBlacklistedDirectBasketCannotGiveFreeFood(GameTestHelper helper) {
        if (ModList.get().isLoaded("tfc")) TfcGameTestCases.basketConsumption(helper, true, true);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tfcBasketSkipsRottenFoodAndHonorsBlacklist(GameTestHelper helper) {
        if (ModList.get().isLoaded("tfc")) TfcGameTestCases.basketEligibility(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tfcBasketConsumesExactDynamicMeal(GameTestHelper helper) {
        if (ModList.get().isLoaded("tfc")) TfcGameTestCases.basketDynamicIdentity(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tfcMissingBasketMealCannotRestoreAndScopesClose(GameTestHelper helper) {
        if (ModList.get().isLoaded("tfc")) TfcGameTestCases.basketNoMatchAndScopeCleanup(helper);
        helper.succeed();
    }
}

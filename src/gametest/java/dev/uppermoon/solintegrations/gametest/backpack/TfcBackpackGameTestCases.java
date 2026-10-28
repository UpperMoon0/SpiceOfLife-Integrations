package dev.uppermoon.solintegrations.gametest.backpack;

import dev.uppermoon.solintegrations.integration.backpack.*;

import com.github.leopoko.solclassic.config.SolclassicConfigData;
import com.github.leopoko.solclassic.network.FoodHistoryHolder;
import com.github.leopoko.solclassic.utils.FoodCalculator;
import dev.uppermoon.solintegrations.config.SolIntegrationsConfig;
import dev.uppermoon.solintegrations.integration.tfc.RestorationPolicy;
import dev.uppermoon.solintegrations.integration.tfc.TfcBridgeOptions;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.FoodData;
import net.dries007.tfc.common.capabilities.food.FoodHandler;
import net.dries007.tfc.common.capabilities.food.IFood;
import net.dries007.tfc.common.capabilities.food.TFCFoodData;
import net.dries007.tfc.mixin.accessor.PlayerAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.feeding.FeedingUpgradeWrapper;

import java.util.List;

/** Loaded only with the real Classic + Core + Backpacks + TFC combination. */
public final class TfcBackpackGameTestCases {
    private static final FoodData VALUES = new FoodData(8, 7, 6, .1F, .2F, .3F, .4F, .5F, 1);

    private TfcBackpackGameTestCases() {}

    public static void nativeCycling(GameTestHelper helper) {
        try (DepletionProfile ignored = new DepletionProfile()) {
            nativeCyclingWithProfile(helper);
        }
    }

    private static void nativeCyclingWithProfile(GameTestHelper helper) {
        BackpackGameTestCases.Fixture fixture = BackpackGameTestCases.fixture(helper,
                meal(helper, "fruit_salad", 4), meal(helper, "grain_salad", 4), meal(helper, "protein_salad", 4));
        List<Integer> expectedOrder = List.of(0, 1, 2, 0, 1, 2);
        for (int round = 0; round < expectedOrder.size(); round++) {
            TFCFoodData nativeFood = resetNativeFood(fixture.player());
            int selected = expectedOrder.get(round);
            ItemStack expectedMeal = fixture.inventory().getStackInSlot(selected).copy();
            float multiplier = FoodCalculator.CalculateMultiplier(expectedMeal, fixture.player());
            int historyBefore = BackpackGameTestCases.historySize(fixture.player());
            int[] before = new int[fixture.inventory().getSlots()];
            for (int slot = 0; slot < before.length; slot++) before[slot] = fixture.inventory().getStackInSlot(slot).getCount();
            helper.assertTrue(multiplier > 0, "Selected fixture must retain positive native nutrition");
            if (round >= 3) helper.assertTrue(multiplier < 1, "Second rotation must exercise depleted native food");

            fixture.wrapper().tick(fixture.player(), helper.getLevel(), fixture.player().blockPosition());

            for (int slot = 0; slot < before.length; slot++) {
                helper.assertTrue(fixture.inventory().getStackInSlot(slot).getCount() == before[slot] - (slot == selected ? 1 : 0),
                        "Native feeding must select the least-depleted physical slot; round=" + round + ", slot=" + slot);
            }
            helper.assertTrue(BackpackGameTestCases.historySize(fixture.player()) == historyBefore + 1,
                    "Native auto-feeding must record one actual meal");
            helper.assertTrue(FoodHistoryHolder.INSTANCE.getFoodHistory(fixture.player()).consumedItems.getLast().is(expectedMeal.getItem()),
                    "Native selection must cycle across actual TFC item IDs");
            assertBenefits(helper, nativeFood, multiplier);
            BackpackGameTestCases.assertMainHand(helper, fixture.player());
            fixture.player().stopUsingItem();
        }
    }

    public static void nativeBasket(GameTestHelper helper) {
        try (DepletionProfile ignored = new DepletionProfile()) {
            nativeBasketWithProfile(helper);
        }
    }

    private static void nativeBasketWithProfile(GameTestHelper helper) {
        ItemStack food = meal(helper, "fruit_salad", 2);
        ItemStack basket = BackpackGameTestCases.basket(food);
        BackpackGameTestCases.Fixture fixture = BackpackGameTestCases.fixture(helper, basket);
        TFCFoodData nativeFood = resetNativeFood(fixture.player());
        FoodHistoryHolder.INSTANCE.addFoodHistory(fixture.player(), food, 100);
        float multiplier = FoodCalculator.CalculateMultiplier(food, fixture.player());
        helper.assertTrue(multiplier > 0 && multiplier < 1, "Native basket regression requires a positive depleted meal");
        BackpackGameTestCases.assertNoPlayerBasket(helper, fixture.player());
        int historyBefore = BackpackGameTestCases.historySize(fixture.player());

        fixture.wrapper().tick(fixture.player(), helper.getLevel(), fixture.player().blockPosition());

        helper.assertTrue(basket.getCount() == 1 && BackpackGameTestCases.basketFoodCount(basket) == 1,
                "A native meal stored only inside a backpack basket must be consumed exactly once");
        helper.assertTrue(BackpackGameTestCases.historySize(fixture.player()) == historyBefore + 1,
                "Native basket auto-feeding must record exactly one consumed meal");
        assertBenefits(helper, nativeFood, multiplier);
        BackpackGameTestCases.assertNoPlayerBasket(helper, fixture.player());
        BackpackGameTestCases.assertMainHand(helper, fixture.player());
    }

    public static void nativeFilterFallback(GameTestHelper helper) {
        try (DepletionProfile ignored = new DepletionProfile()) {
            BackpackGameTestCases.Fixture fixture = BackpackGameTestCases.fixture(helper,
                    meal(helper, "fruit_salad", 2), meal(helper, "grain_salad", 2));
            TFCFoodData nativeFood = resetNativeFood(fixture.player());
            ItemStack fallback = fixture.inventory().getStackInSlot(1);
            FoodHistoryHolder.INSTANCE.addFoodHistory(fixture.player(), fallback, 100);
            float multiplier = FoodCalculator.CalculateMultiplier(fallback, fixture.player());
            FeedingUpgradeWrapper wrapper = fixture.wrapper();
            wrapper.getFilterLogic().setAllowList(false);
            wrapper.getFilterLogic().getFilterHandler().setStackInSlot(0, fixture.inventory().getStackInSlot(0).copy());
            int beforeHistory = BackpackGameTestCases.historySize(fixture.player());

            wrapper.tick(fixture.player(), helper.getLevel(), fixture.player().blockPosition());

            helper.assertTrue(fixture.inventory().getStackInSlot(0).getCount() == 2
                            && fixture.inventory().getStackInSlot(1).getCount() == 1,
                    "Native filter must skip the highest-ranked food and permit the positive depleted fallback");
            helper.assertTrue(BackpackGameTestCases.historySize(fixture.player()) == beforeHistory + 1,
                    "Only the actual native fallback meal must enter history");
            assertBenefits(helper, nativeFood, multiplier);
            BackpackGameTestCases.assertMainHand(helper, fixture.player());
        }
    }

    public static void positiveMinimum(GameTestHelper helper) {
        try (DepletionProfile ignored = new DepletionProfile(); MinimumProfile minimum = new MinimumProfile()) {
            FoodData lowValues = new FoodData(2, 2, 6, .1F, .2F, .3F, .4F, .5F, 1);
            ItemStack food = meal(helper, "fruit_salad", 2, lowValues);
            BackpackGameTestCases.Fixture fixture = BackpackGameTestCases.fixture(helper, food);
            TFCFoodData nativeFood = resetNativeFood(fixture.player());
            FoodHistoryHolder.INSTANCE.addFoodHistory(fixture.player(), food, 100);
            float multiplier = FoodCalculator.CalculateMultiplier(food, fixture.player());
            helper.assertTrue(multiplier > 0 && FoodCalculator.CalculateNutrition(2, multiplier) == 0,
                    "The native H2 fixture must require the explicitly enabled addon floor");
            assertClassicMathUnchanged(helper);
            int historyBefore = BackpackGameTestCases.historySize(fixture.player());

            fixture.wrapper().tick(fixture.player(), helper.getLevel(), fixture.player().blockPosition());

            helper.assertTrue(food.getCount() == 1, "Positive minimum must allow one actual food consumption");
            helper.assertTrue(BackpackGameTestCases.historySize(fixture.player()) == historyBefore + 1,
                    "Positive-minimum feeding must record exactly once");
            assertBenefits(helper, nativeFood, lowValues, multiplier);
            helper.assertTrue(nativeFood.getFoodLevel() == 3, "Enabled positive minimum must restore exactly one hunger");
            assertClassicMathUnchanged(helper);
            BackpackGameTestCases.assertMainHand(helper, fixture.player());
        }
    }

    public static void zeroMultiplier(GameTestHelper helper) {
        try (DepletionProfile ignored = new DepletionProfile(); MinimumProfile minimum = new MinimumProfile()) {
            ItemStack food = meal(helper, "fruit_salad", 2);
            BackpackGameTestCases.Fixture fixture = BackpackGameTestCases.fixture(helper, food);
            TFCFoodData nativeFood = resetNativeFood(fixture.player());
            for (int i = 0; i < 4; i++) FoodHistoryHolder.INSTANCE.addFoodHistory(fixture.player(), food, 100);
            helper.assertTrue(FoodCalculator.CalculateMultiplier(food, fixture.player()) == 0,
                    "The zero-multiplier fixture must be completely depleted");
            assertClassicMathUnchanged(helper);
            int historyBefore = BackpackGameTestCases.historySize(fixture.player());

            fixture.wrapper().tick(fixture.player(), helper.getLevel(), fixture.player().blockPosition());

            helper.assertTrue(food.getCount() == 2, "Addon floor must never make zero-multiplier food eligible");
            helper.assertTrue(BackpackGameTestCases.historySize(fixture.player()) == historyBefore,
                    "Rejected zero-multiplier food must not alter history");
            helper.assertTrue(nativeFood.getFoodLevel() == 2 && nativeFood.getThirst() == 0
                            && nativeFood.getSaturationLevel() == 0,
                    "A rejected zero-multiplier meal must grant no native benefits");
            helper.assertTrue(nativeFood.getNutrition().writeToNbt().getList("records", Tag.TAG_COMPOUND).isEmpty(),
                    "A rejected zero-multiplier meal must add no nutrient record");
            assertClassicMathUnchanged(helper);
            BackpackGameTestCases.assertMainHand(helper, fixture.player());
        }
    }

    private static void assertClassicMathUnchanged(GameTestHelper helper) {
        helper.assertTrue(FoodCalculator.CalculateNutrition(4, 0.20F) == 0,
                "Outside the Core eligibility scope Classic CalculateNutrition must retain its original math");
    }

    private static ItemStack meal(GameTestHelper helper, String name, int count) {
        return meal(helper, name, count, VALUES);
    }

    private static ItemStack meal(GameTestHelper helper, String name, int count, FoodData values) {
        ItemStack meal = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("tfc", "food/" + name)), count);
        IFood capability = FoodCapability.get(meal);
        helper.assertTrue(capability instanceof FoodHandler.Dynamic, "TFC " + name + " must carry native dynamic FoodData");
        ((FoodHandler.Dynamic) capability).setFood(values);
        FoodCapability.setNeverExpires(meal);
        return meal;
    }

    private static TFCFoodData resetNativeFood(FakePlayer player) {
        TFCFoodData nativeFood = new TFCFoodData(player, new net.minecraft.world.food.FoodData());
        ((PlayerAccessor) player).accessor$setFoodData(nativeFood);
        nativeFood.setFoodLevel(2);
        nativeFood.setSaturation(0);
        nativeFood.setThirst(0);
        return nativeFood;
    }

    private static void assertBenefits(GameTestHelper helper, TFCFoodData nativeFood, float multiplier) {
        assertBenefits(helper, nativeFood, VALUES, multiplier);
    }

    private static void assertBenefits(GameTestHelper helper, TFCFoodData nativeFood, FoodData values, float multiplier) {
        int expectedHunger = RestorationPolicy.hunger(values.hunger(), multiplier,
                FoodCalculator.CalculateNutrition(values.hunger(), multiplier),
                TfcBridgeOptions.positiveMinimum(), TfcBridgeOptions.minimumThreshold());
        helper.assertTrue(nativeFood.getFoodLevel() == 2 + expectedHunger, "Auto-feeding must use native hunger with Classic depletion");
        close(helper, nativeFood.getSaturationLevel(), values.saturation() * expectedHunger / values.hunger(), "Native saturation");
        close(helper, nativeFood.getThirst(), values.water(), "Native water must be applied once without depletion");
        ListTag records = nativeFood.getNutrition().writeToNbt().getList("records", Tag.TAG_COMPOUND);
        helper.assertTrue(records.size() == 1 && records.getCompound(0).equals(values.write()),
                "All native nutrients must be applied exactly once with unchanged data");
    }

    private static void close(GameTestHelper helper, float actual, float expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) < 0.0001F, label + ": expected " + expected + ", got " + actual);
    }

    /**
     * At 20%, native H8 still restores one hunger while TFC's vanilla H4
     * placeholder rounds to zero. Restore all global settings on every exit.
     * GameTests execute these synchronous bodies on the server thread.
     */
    private static final class DepletionProfile implements AutoCloseable {
        private final List<Float> shortModifiers = SolclassicConfigData.shortFoodDecayModifiers;
        private final float longModifier = SolclassicConfigData.longFoodDecayModifiers;
        private final boolean minimum = SolclassicConfigData.guaranteeMinimumNutrition;

        DepletionProfile() {
            SolclassicConfigData.shortFoodDecayModifiers = List.of(1.0F, 0.2F, 0.1F, 0.05F, 0.0F);
            SolclassicConfigData.longFoodDecayModifiers = 0;
            SolclassicConfigData.guaranteeMinimumNutrition = false;
        }

        @Override
        public void close() {
            SolclassicConfigData.shortFoodDecayModifiers = shortModifiers;
            SolclassicConfigData.longFoodDecayModifiers = longModifier;
            SolclassicConfigData.guaranteeMinimumNutrition = minimum;
        }
    }

    private static final class MinimumProfile implements AutoCloseable {
        MinimumProfile() {
            TfcBridgeOptions.install(() -> true, () -> true, () -> 0.0D);
        }

        @Override
        public void close() {
            TfcBridgeOptions.install(SolIntegrationsConfig::tfcEnabled,
                    SolIntegrationsConfig::positiveMinimumEnabled, SolIntegrationsConfig::minimumThreshold);
        }
    }
}

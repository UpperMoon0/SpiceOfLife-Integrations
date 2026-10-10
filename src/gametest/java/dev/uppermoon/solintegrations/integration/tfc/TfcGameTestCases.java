package dev.uppermoon.solintegrations.integration.tfc;

import com.github.leopoko.solclassic.config.SolclassicConfigData;
import com.github.leopoko.solclassic.forge.config.SolClassicConfigForge;
import com.github.leopoko.solclassic.item.WickerBasketItem;
import com.github.leopoko.solclassic.network.FoodHistoryHolder;
import com.github.leopoko.solclassic.utils.FoodCalculator;
import com.mojang.authlib.GameProfile;
import dev.uppermoon.solintegrations.config.SolIntegrationsConfig;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.FoodData;
import net.dries007.tfc.common.capabilities.food.FoodHandler;
import net.dries007.tfc.common.capabilities.food.IFood;
import net.dries007.tfc.common.capabilities.food.TFCFoodData;
import net.dries007.tfc.mixin.accessor.PlayerAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Linked only by the TFC-present branch of the discovery class. */
public final class TfcGameTestCases {
    private static final String FOOD_ID = "tfc:food/fruit_salad";
    private static final FoodData VALUES = new FoodData(8, 7, 6, .1F, .2F, .3F, .4F, .5F, 1);

    private TfcGameTestCases() {}

    public static void playerConsumption(GameTestHelper helper, boolean repeated) {
        Fixture fixture = fixture(helper);
        if (repeated) {
            FoodHistoryHolder.INSTANCE.addFoodHistory(fixture.player, fixture.stack, 100);
        }
        int beforeHistory = historySize(fixture.player);
        float multiplier = FoodCalculator.CalculateMultiplier(fixture.stack, fixture.player);
        int expectedHunger = RestorationPolicy.hunger(VALUES.hunger(), multiplier,
                FoodCalculator.CalculateNutrition(VALUES.hunger(), multiplier),
                TfcBridgeOptions.positiveMinimum(), TfcBridgeOptions.minimumThreshold());
        helper.assertTrue(FoodHistoryHolder.INSTANCE.getEffectiveNutrition(fixture.stack, fixture.player) == VALUES.hunger(),
                "Effective API must expose native TFC hunger instead of the vanilla placeholder");
        close(helper, FoodHistoryHolder.INSTANCE.getEffectiveSaturationModifier(fixture.stack, fixture.player),
                VALUES.saturation() / (2 * VALUES.hunger()), "Effective saturation modifier");

        fixture.player.eat(helper.getLevel(), fixture.stack);

        helper.assertTrue(fixture.stack.isEmpty(), "Last item must be consumed once");
        helper.assertTrue(fixture.food.getData() == VALUES, "Capability must retain its original immutable FoodData object");
        helper.assertTrue(historySize(fixture.player) == beforeHistory + 1, "Classic must add exactly one history record");
        assertNativeBenefits(helper, fixture, expectedHunger);
        helper.assertTrue(ConsumptionScope.forFood(fixture.tfc, fixture.food) == null, "Consumption scope must be removed");
        helper.assertTrue(RestorationScope.current(fixture.tfc) == null, "Restoration scope must be removed");
    }

    public static void rottenConsumption(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        FoodCapability.setRotten(fixture.stack);
        helper.assertTrue(fixture.food.isRotten(), "Rotten fixture must be rotten before consumption");
        fixture.player.eat(helper.getLevel(), fixture.stack);
        helper.assertTrue(fixture.stack.isEmpty(), "Rotten food must still be consumed");
        helper.assertTrue(historySize(fixture.player) == 1, "Manually eaten rotten food must record once");
        helper.assertTrue(fixture.tfc.getFoodLevel() == 2, "Rotten food must not restore hunger");
        close(helper, fixture.tfc.getSaturationLevel(), 0, "Rotten saturation");
        close(helper, fixture.tfc.getThirst(), 0, "Rotten water");
        helper.assertTrue(fixture.tfc.getNutrition().writeToNbt().getList("records", Tag.TAG_COMPOUND).isEmpty(),
                "Rotten food must not add nutrition records");
    }

    public static void disabledBridge(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        try {
            TfcBridgeOptions.install(() -> false, () -> false, () -> 0.0D);
            helper.assertTrue(FoodHistoryHolder.INSTANCE.getEffectiveNutrition(fixture.stack, fixture.player)
                            == fixture.stack.getItem().getFoodProperties().getNutrition(),
                    "Disabled adapter must expose Classic's original vanilla food values");
            fixture.player.eat(helper.getLevel(), fixture.stack);
            helper.assertTrue(fixture.stack.isEmpty(), "Upstream disabled path must still consume its item");
            helper.assertTrue(historySize(fixture.player) == 1, "Upstream Classic history must still record once");
            helper.assertTrue(fixture.tfc.getFoodLevel() == 2, "Disabled bridge must retain the upstream TFC int/float no-op");
            close(helper, fixture.tfc.getSaturationLevel(), 0, "Disabled saturation");
            close(helper, fixture.tfc.getThirst(), 0, "Disabled water");
            helper.assertTrue(fixture.tfc.getNutrition().writeToNbt().getList("records", Tag.TAG_COMPOUND).isEmpty(),
                    "Disabled bridge must not add native nutrient records");
            helper.assertTrue(ConsumptionScope.forFood(fixture.tfc, fixture.food) == null
                            && RestorationScope.current(fixture.tfc) == null
                            && BasketConsumptionScope.current() == null
                            && FeedingNutritionScope.current() == null,
                    "Disabled consumption must leave no integration context");
        } finally {
            TfcBridgeOptions.install(SolIntegrationsConfig::tfcEnabled,
                    SolIntegrationsConfig::positiveMinimumEnabled, SolIntegrationsConfig::minimumThreshold);
        }
    }

    public static void basketConsumption(GameTestHelper helper, boolean direct, boolean blacklisted) {
        Fixture fixture = fixture(helper);
        ItemStack basket = basket(fixture.stack);
        List<String> originalCommonBlacklist = SolclassicConfigData.foodBlacklist;
        try (ConfigSnapshotScope ignored = ConfigSnapshotScope.open(SolClassicConfigForge.SERVER_CONFIG)) {
            if (blacklisted) {
                List<String> updated = new ArrayList<>(SolClassicConfigForge.CONFIG.foodBlacklist.get());
                updated.add(FOOD_ID);
                SolClassicConfigForge.CONFIG.foodBlacklist.set(updated);
                SolclassicConfigData.foodBlacklist = updated;
            }
            if (direct) {
                // Classic's Forge basket food-properties hook requires this
                // exact stack to be held in the player's inventory/offhand.
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND, basket);
                fixture.player.eat(helper.getLevel(), basket);
            } else {
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND, basket);
                fixture.player.startUsingItem(InteractionHand.MAIN_HAND);
                basket.finishUsingItem(helper.getLevel(), fixture.player);
                fixture.player.stopUsingItem();
            }
            helper.assertTrue(basket.getCount() == 1, "Basket itself must not be consumed");
            helper.assertTrue(basket.getOrCreateTag().getList("basket_inventory", Tag.TAG_COMPOUND).isEmpty(),
                    "Exactly one selected food must be removed from the basket");
            helper.assertTrue(historySize(fixture.player) == (blacklisted ? 0 : 1),
                    "Basket history must respect blacklist and record only the real food");
            assertNativeBenefits(helper, fixture, VALUES.hunger());
        } finally {
            SolclassicConfigData.foodBlacklist = originalCommonBlacklist;
        }
    }

    public static void basketEligibility(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        FoodCapability.setRotten(fixture.stack);
        ItemStack rottenBasket = basket(fixture.stack);
        helper.assertTrue(WickerBasketItem.getMostNutritiousFood(rottenBasket, fixture.player).isEmpty(),
                "Automatic basket selection must skip native rotten food");
        helper.assertTrue(rottenBasket.getOrCreateTag().getList("basket_inventory", Tag.TAG_COMPOUND).size() == 1,
                "Read-only rotten filtering must retain the stored item");

        Fixture fresh = fixture(helper);
        ItemStack freshBasket = basket(fresh.stack);
        List<String> original = SolclassicConfigData.basketBlacklist;
        try {
            SolclassicConfigData.basketBlacklist = List.of(FOOD_ID);
            helper.assertTrue(WickerBasketItem.getMostNutritiousFood(freshBasket, fresh.player).isEmpty(),
                    "Native rotten guard must preserve the existing basket blacklist");
            helper.assertTrue(freshBasket.getOrCreateTag().getList("basket_inventory", Tag.TAG_COMPOUND).size() == 1,
                    "Blacklist filtering must not destroy stored food");
        } finally {
            SolclassicConfigData.basketBlacklist = original;
        }
    }

    public static void basketDynamicIdentity(GameTestHelper helper) {
        for (boolean direct : new boolean[] {false, true}) {
            for (boolean expiring : new boolean[] {false, true}) {
                basketDynamicIdentity(helper, direct, expiring);
            }
        }
    }

    private static void basketDynamicIdentity(GameTestHelper helper, boolean direct, boolean expiring) {
        Fixture high = fixture(helper);
        Fixture low = fixture(helper);
        FoodData lowValues = new FoodData(2, 1, 0, 0, 0, 0, 0, .1F, 1);
        ((FoodHandler.Dynamic) low.food).setFood(lowValues);
        if (expiring) {
            long creation = FoodCapability.getRoundedCreationDate();
            FoodCapability.setCreationDate(high.stack, creation);
            FoodCapability.setCreationDate(low.stack, creation);
        }
        ItemStack basket = basket(low.stack);
        CompoundTag originalLow = basket.getOrCreateTag().getList("basket_inventory", Tag.TAG_COMPOUND).getCompound(0).copy();
        basket.getOrCreateTag().getList("basket_inventory", Tag.TAG_COMPOUND).add(high.stack.save(new CompoundTag()));
        if (direct) {
            high.player.setItemInHand(InteractionHand.MAIN_HAND, basket);
            high.player.eat(helper.getLevel(), basket);
        } else {
            high.player.setItemInHand(InteractionHand.MAIN_HAND, basket);
            high.player.startUsingItem(InteractionHand.MAIN_HAND);
            basket.finishUsingItem(helper.getLevel(), high.player);
            high.player.stopUsingItem();
        }
        ListTag remaining = basket.getOrCreateTag().getList("basket_inventory", Tag.TAG_COMPOUND);
        helper.assertTrue(remaining.size() == 1, "Only one dynamic meal should remain");
        helper.assertTrue(remaining.getCompound(0).equals(originalLow),
                "Unselected persisted food, including ordinary expiry timestamp, must remain byte-for-byte equivalent");
        ItemStack survivor = ItemStack.of(remaining.getCompound(0));
        helper.assertTrue(FoodCapability.get(survivor).getData().hunger() == lowValues.hunger(),
                "The selected high-value meal must be removed, not the first same-ID low-value meal");
        helper.assertTrue(historySize(high.player) == 1, "Dynamic meal selection must record once");
        assertNativeBenefits(helper, high, VALUES.hunger());
    }

    public static void basketNoMatchAndScopeCleanup(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ItemStack basket = basket(fixture.stack);
        TfcConsumptionBridge.Meal meal = TfcConsumptionBridge.capture(fixture.player, basket);
        helper.assertTrue(meal != null && meal.basketOrigin() != null, "Selection must carry original persisted data");
        // Simulate another authorized inventory handler changing the basket after
        // selection. The native restoration transaction must fail closed.
        basket.getOrCreateTag().put("basket_inventory", new ListTag());
        CompoundTag before = basket.getOrCreateTag().copy();
        try (BasketConsumptionScope scope = BasketConsumptionScope.open(basket, fixture.player)) {
            scope.defer(meal, false);
            WickerBasketItem.shrinkItemFromInventory(basket, meal.selectedFood());
            scope.complete();
        }
        helper.assertTrue(basket.getOrCreateTag().equals(before), "No-match must not mutate basket NBT");
        helper.assertTrue(fixture.tfc.getFoodLevel() == 2, "Unconsumed food must give no hunger");
        close(helper, fixture.tfc.getThirst(), 0, "Unconsumed food water");
        helper.assertTrue(fixture.tfc.getNutrition().writeToNbt().getList("records", Tag.TAG_COMPOUND).isEmpty(),
                "Unconsumed food must give no native nutrients");
        helper.assertTrue(BasketConsumptionScope.current() == null, "Completed basket must leave no scope");

        ItemStack otherBasket = basket(fixture.stack);
        try (BasketConsumptionScope outer = BasketConsumptionScope.open(basket, fixture.player)) {
            try {
                try (BasketConsumptionScope ignored = BasketConsumptionScope.open(otherBasket, fixture.player)) {
                    helper.assertTrue(BasketConsumptionScope.forBasket(basket) == null, "Inner player/basket must not claim outer state");
                    throw new IllegalStateException("simulated basket food failure");
                }
            } catch (IllegalStateException expected) {
                helper.assertTrue(BasketConsumptionScope.forBasket(basket) == outer, "Exception must restore outer basket scope");
            }
        }
        helper.assertTrue(BasketConsumptionScope.current() == null, "Exception cleanup must remove thread-local basket state");

        ItemStack validBasket = basket(fixture.stack);
        ItemStack selected = WickerBasketItem.getMostNutritiousFood(validBasket, fixture.player);
        Fixture otherPlayer = fixture(helper);
        ItemStack delegated = selected.copy();
        try (BasketConsumptionScope scope = BasketConsumptionScope.open(validBasket, fixture.player)) {
            scope.selected(selected, fixture.player);
            scope.beginDelegation(delegated, fixture.player);
            helper.assertTrue(scope.claim(delegated, otherPlayer.player) == null, "Another player cannot claim the basket meal");
            helper.assertTrue(scope.claim(selected.copy(), fixture.player) == null, "Unrelated same-ID stack cannot claim basket meal");
            helper.assertTrue(scope.claim(delegated, fixture.player) != null, "Exact delegated stack must claim basket meal");
            helper.assertTrue(scope.claim(delegated, fixture.player) == null, "Nested/repeated claim cannot consume pending meal again");
        }
    }

    private static Fixture fixture(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "sol_tfc_test"));
        TFCFoodData tfc = new TFCFoodData(player, new net.minecraft.world.food.FoodData());
        ((PlayerAccessor) player).accessor$setFoodData(tfc);
        tfc.setFoodLevel(2);
        tfc.setSaturation(0);
        tfc.setThirst(0);
        FoodHistoryHolder.INSTANCE.resetFoodHistory(player);
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(FOOD_ID)));
        IFood food = FoodCapability.get(stack);
        helper.assertTrue(food instanceof FoodHandler.Dynamic, "TFC fruit salad must have a dynamic food capability");
        ((FoodHandler.Dynamic) food).setFood(VALUES);
        FoodCapability.setNeverExpires(stack);
        return new Fixture(player, tfc, stack, food);
    }

    private static ItemStack basket(ItemStack food) {
        ItemStack basket = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("solclassic:wicker_basket")));
        ListTag inventory = new ListTag();
        inventory.add(food.save(new CompoundTag()));
        basket.getOrCreateTag().put("basket_inventory", inventory);
        return basket;
    }

    private static int historySize(FakePlayer player) {
        return FoodHistoryHolder.INSTANCE.getFoodHistory(player).consumedItems.size();
    }

    private static void assertNativeBenefits(GameTestHelper helper, Fixture fixture, int hunger) {
        helper.assertTrue(fixture.tfc.getFoodLevel() == 2 + hunger, "Native TFC hunger must use Classic rounding");
        close(helper, fixture.tfc.getSaturationLevel(), VALUES.saturation() * hunger / VALUES.hunger(), "Native TFC saturation");
        close(helper, fixture.tfc.getThirst(), VALUES.water(), "Native TFC water must apply once, without depletion");
        ListTag records = fixture.tfc.getNutrition().writeToNbt().getList("records", Tag.TAG_COMPOUND);
        helper.assertTrue(records.size() == 1, "Native TFC nutrients must apply exactly once");
        helper.assertTrue(records.getCompound(0).equals(VALUES.write()), "All native nutrient/data fields must remain unchanged");
    }

    private static void close(GameTestHelper helper, float actual, float expected, String field) {
        helper.assertTrue(Math.abs(actual - expected) < 0.0001F, field + ": expected " + expected + ", got " + actual);
    }

    private record Fixture(FakePlayer player, TFCFoodData tfc, ItemStack stack, IFood food) {}
}

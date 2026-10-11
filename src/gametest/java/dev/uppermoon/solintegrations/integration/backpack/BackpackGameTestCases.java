package dev.uppermoon.solintegrations.integration.backpack;

import com.github.leopoko.solclassic.network.FoodHistoryHolder;
import com.github.leopoko.solclassic.utils.FoodCalculator;
import com.mojang.authlib.GameProfile;
import dev.uppermoon.solintegrations.config.SolIntegrationsConfig;
import dev.uppermoon.solintegrations.integration.tfc.ConfigSnapshotScope;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.ITrackedContentsItemHandler;
import net.p3pp3rf1y.sophisticatedcore.inventory.ItemStackKey;
import net.p3pp3rf1y.sophisticatedcore.upgrades.feeding.FeedingUpgradeItem;
import net.p3pp3rf1y.sophisticatedcore.upgrades.feeding.FeedingUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.feeding.HungerLevel;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Real transformed Core wrappers with a small in-memory test storage backend. */
public final class BackpackGameTestCases {
    private BackpackGameTestCases() {}

    public static void cycling(GameTestHelper helper) {
        Fixture fixture = fixture(helper, new ItemStack(Items.APPLE, 4),
                new ItemStack(Items.CARROT, 4), new ItemStack(Items.BREAD, 4));
        List<Integer> consumedSlots = new ArrayList<>();
        for (int round = 0; round < 6; round++) {
            int[] before = counts(fixture.inventory);
            int historyBefore = historySize(fixture.player);
            fixture.player.getFoodData().setFoodLevel(2);
            // A fresh wrapper removes cooldown waiting while retaining the
            // same upgrade settings, physical inventory and player history.
            fixture.wrapper().tick(fixture.player, helper.getLevel(), fixture.player.blockPosition());
            int changed = -1;
            for (int slot = 0; slot < before.length; slot++) {
                int difference = before[slot] - fixture.inventory.getStackInSlot(slot).getCount();
                helper.assertTrue(difference == 0 || difference == 1, "A feeding attempt may remove only one item");
                if (difference == 1) {
                    helper.assertTrue(changed == -1, "Only one physical slot may be consumed per tick");
                    changed = slot;
                }
            }
            helper.assertTrue(changed >= 0, "An eligible meal must be fed on every attempt");
            consumedSlots.add(changed);
            helper.assertTrue(historySize(fixture.player) == historyBefore + 1, "Each successful feeding records once");
            fixture.player.stopUsingItem();
        }
        helper.assertTrue(consumedSlots.equals(List.of(0, 1, 2, 0, 1, 2)),
                "Least-depleted selection must re-evaluate history and preserve slot ties: " + consumedSlots);
        helper.assertTrue(fixture.inventory.getStackInSlot(0).is(Items.APPLE)
                        && fixture.inventory.getStackInSlot(1).is(Items.CARROT)
                        && fixture.inventory.getStackInSlot(2).is(Items.BREAD),
                "Selection must never rearrange inventory contents");
        assertMainHand(helper, fixture.player);
    }

    public static void ordinaryFilterFallback(GameTestHelper helper) {
        Fixture fixture = fixture(helper, new ItemStack(Items.APPLE, 2), new ItemStack(Items.CARROT, 2));
        depleteFallback(helper, fixture, 1);
        FeedingUpgradeWrapper wrapper = fixture.wrapper();
        wrapper.getFilterLogic().setAllowList(false);
        wrapper.getFilterLogic().getFilterHandler().setStackInSlot(0, new ItemStack(Items.APPLE));
        int beforeHistory = historySize(fixture.player);
        wrapper.tick(fixture.player, helper.getLevel(), fixture.player.blockPosition());
        assertFallback(helper, fixture, beforeHistory);
    }

    public static void ordinaryHungerFallback(GameTestHelper helper) {
        Fixture fixture = fixture(helper, new ItemStack(Items.COOKED_BEEF, 2), new ItemStack(Items.APPLE, 2));
        depleteFallback(helper, fixture, 1);
        fixture.player.getFoodData().setFoodLevel(16);
        FeedingUpgradeWrapper wrapper = fixture.wrapper();
        wrapper.setFeedAtHungerLevel(HungerLevel.FULL);
        int beforeHistory = historySize(fixture.player);
        wrapper.tick(fixture.player, helper.getLevel(), fixture.player.blockPosition());
        assertFallback(helper, fixture, beforeHistory);
    }

    public static void disabledSelection(GameTestHelper helper) {
        try (ConfigSnapshotScope ignored = ConfigSnapshotScope.open(SolIntegrationsConfig.SPEC)) {
            ForgeConfigSpec.BooleanValue selection = SolIntegrationsConfig.SPEC.getValues()
                    .get(List.of("integrations", "sophisticatedFeeding"));
            selection.set(false);
            helper.assertTrue(!SolIntegrationsConfig.backpackSelectionEnabled(), "Only the selection integration must be disabled");
            Fixture fixture = fixture(helper, new ItemStack(Items.APPLE, 2), new ItemStack(Items.CARROT, 2));
            FoodHistoryHolder.INSTANCE.addFoodHistory(fixture.player, fixture.inventory.getStackInSlot(0), 100);
            helper.assertTrue(FoodCalculator.CalculateMultiplier(fixture.inventory.getStackInSlot(0), fixture.player)
                            < FoodCalculator.CalculateMultiplier(fixture.inventory.getStackInSlot(1), fixture.player),
                    "First physical slot must be more depleted than the second slot");
            int beforeHistory = historySize(fixture.player);

            fixture.wrapper().tick(fixture.player, helper.getLevel(), fixture.player.blockPosition());

            helper.assertTrue(fixture.inventory.getStackInSlot(0).getCount() == 1
                            && fixture.inventory.getStackInSlot(1).getCount() == 2,
                    "Disabled selection must delegate to native physical-slot traversal");
            helper.assertTrue(historySize(fixture.player) == beforeHistory + 1
                            && FoodHistoryHolder.INSTANCE.getFoodHistory(fixture.player).consumedItems.getLast().is(Items.APPLE),
                    "Native disabled-selection path must consume and record the first eligible food once");
            assertMainHand(helper, fixture.player);
        }
    }

    public static void backpackOnlyBasket(GameTestHelper helper) {
        ItemStack basket = basket(new ItemStack(Items.APPLE, 2));
        Fixture fixture = fixture(helper, basket);
        assertNoPlayerBasket(helper, fixture.player);
        int beforeHistory = historySize(fixture.player);
        fixture.wrapper().tick(fixture.player, helper.getLevel(), fixture.player.blockPosition());
        helper.assertTrue(basket.getCount() == 1, "The basket itself must not be consumed");
        helper.assertTrue(basketFoodCount(basket) == 1, "A backpack-only basket must lose exactly one contained meal");
        helper.assertTrue(historySize(fixture.player) == beforeHistory + 1, "Basket auto-feeding records exactly one real meal");
        helper.assertTrue(FoodHistoryHolder.INSTANCE.getFoodHistory(fixture.player).consumedItems.getLast().is(Items.APPLE),
                "History must record the contained food, not the basket");
        helper.assertTrue(fixture.player.getFoodData().getFoodLevel() > 2, "The consumed basket meal must restore hunger");
        assertNoPlayerBasket(helper, fixture.player);
        assertMainHand(helper, fixture.player);
    }

    public static void basketFallback(GameTestHelper helper, boolean hungerDenial) {
        ItemStack basket = basket(new ItemStack(hungerDenial ? Items.COOKED_BEEF : Items.APPLE, 2));
        Fixture fixture = fixture(helper, basket, new ItemStack(Items.CARROT, 2));
        depleteFallback(helper, fixture, 1);
        assertNoPlayerBasket(helper, fixture.player);
        CompoundTag originalBasketTag = basket.getTag().copy();
        FeedingUpgradeWrapper wrapper = fixture.wrapper();
        if (hungerDenial) {
            fixture.player.getFoodData().setFoodLevel(16);
            wrapper.setFeedAtHungerLevel(HungerLevel.FULL);
        } else {
            wrapper.getFilterLogic().setAllowList(false);
            wrapper.getFilterLogic().getFilterHandler().setStackInSlot(0, basket.copy());
        }
        int beforeHistory = historySize(fixture.player);
        wrapper.tick(fixture.player, helper.getLevel(), fixture.player.blockPosition());
        helper.assertTrue(basket.getCount() == 1 && basket.getTag().equals(originalBasketTag),
                "A rejected higher-ranked basket must retain every stored item and its NBT");
        helper.assertTrue(fixture.inventory.getStackInSlot(1).getCount() == 1,
                "Rejected basket must fall through to the eligible ordinary food");
        helper.assertTrue(historySize(fixture.player) == beforeHistory + 1, "Only the fallback food records history");
        helper.assertTrue(FoodHistoryHolder.INSTANCE.getFoodHistory(fixture.player).consumedItems.getLast().is(Items.CARROT),
                "Rejected basket contents must not enter history");
        assertMainHand(helper, fixture.player);
    }

    private static void assertFallback(GameTestHelper helper, Fixture fixture, int beforeHistory) {
        helper.assertTrue(fixture.inventory.getStackInSlot(0).getCount() == 2, "Rejected highest-ranked food must remain intact");
        helper.assertTrue(fixture.inventory.getStackInSlot(1).getCount() == 1, "Eligible lower-ranked food must be consumed once");
        helper.assertTrue(historySize(fixture.player) == beforeHistory + 1, "Only one actual food may enter history");
        assertMainHand(helper, fixture.player);
    }

    private static void depleteFallback(GameTestHelper helper, Fixture fixture, int slot) {
        ItemStack fallback = fixture.inventory.getStackInSlot(slot);
        FoodHistoryHolder.INSTANCE.addFoodHistory(fixture.player, fallback, 100);
        helper.assertTrue(FoodCalculator.CalculateMultiplier(fallback, fixture.player) < 1,
                "Test requires the unchanged Classic default decay settings");
    }

    static Fixture fixture(GameTestHelper helper, ItemStack... stacks) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "sol_backpack"));
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 13));
        player.getFoodData().setFoodLevel(2);
        player.getFoodData().setSaturation(0);
        player.setHealth(player.getMaxHealth());
        player.getAbilities().instabuild = false;
        FoodHistoryHolder.INSTANCE.resetFoodHistory(player);

        TestInventory inventory = new TestInventory(stacks.length);
        for (int slot = 0; slot < stacks.length; slot++) inventory.setStackInSlot(slot, stacks[slot]);
        IStorageWrapper storage = (IStorageWrapper) Proxy.newProxyInstance(
                IStorageWrapper.class.getClassLoader(), new Class<?>[]{IStorageWrapper.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getInventoryForUpgradeProcessing", "getInventoryForInputOutput" -> inventory;
                    case "toString" -> "GameTest feeding storage";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new AssertionError("Unexpected storage API used by feeding: " + method);
                });
        Item upgradeItem = BuiltInRegistries.ITEM.get(new ResourceLocation("sophisticatedbackpacks", "feeding_upgrade"));
        helper.assertTrue(upgradeItem instanceof FeedingUpgradeItem, "Registered feeding upgrade must be available");
        return new Fixture(player, inventory, storage, new ItemStack(upgradeItem));
    }

    static ItemStack basket(ItemStack meal) {
        ItemStack basket = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("solclassic", "wicker_basket")));
        ListTag contents = new ListTag();
        contents.add(meal.save(new CompoundTag()));
        basket.getOrCreateTag().put("basket_inventory", contents);
        return basket;
    }

    static int basketFoodCount(ItemStack basket) {
        int count = 0;
        for (Tag tag : basket.getOrCreateTag().getList("basket_inventory", Tag.TAG_COMPOUND)) {
            count += ItemStack.of((CompoundTag) tag).getCount();
        }
        return count;
    }

    static void assertNoPlayerBasket(GameTestHelper helper, FakePlayer player) {
        ResourceLocation basketId = new ResourceLocation("solclassic", "wicker_basket");
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            helper.assertTrue(!BuiltInRegistries.ITEM.getKey(player.getInventory().getItem(slot).getItem()).equals(basketId),
                    "Regression fixture must keep the basket exclusively in backpack storage");
        }
    }

    static void assertMainHand(GameTestHelper helper, FakePlayer player) {
        helper.assertTrue(player.getMainHandItem().is(Items.STICK) && player.getMainHandItem().getCount() == 13,
                "Feeding must restore the original main-hand item without consuming it");
    }

    static int historySize(FakePlayer player) {
        return FoodHistoryHolder.INSTANCE.getFoodHistory(player).consumedItems.size();
    }

    private static int[] counts(TestInventory inventory) {
        int[] counts = new int[inventory.getSlots()];
        for (int slot = 0; slot < counts.length; slot++) counts[slot] = inventory.getStackInSlot(slot).getCount();
        return counts;
    }

    record Fixture(FakePlayer player, TestInventory inventory, IStorageWrapper storage, ItemStack upgrade) {
        FeedingUpgradeWrapper wrapper() {
            FeedingUpgradeWrapper wrapper = new FeedingUpgradeWrapper(storage, upgrade, ignored -> {});
            wrapper.setFeedAtHungerLevel(HungerLevel.ANY);
            wrapper.setFeedImmediatelyWhenHurt(false);
            wrapper.getFilterLogic().setAllowList(false);
            return wrapper;
        }
    }

    static final class TestInventory extends ItemStackHandler implements ITrackedContentsItemHandler {
        TestInventory(int slots) { super(slots); }

        @Override
        public ItemStack insertItem(ItemStack stack, boolean simulate) {
            return ItemHandlerHelper.insertItemStacked(this, stack, simulate);
        }

        @Override
        public ItemStack extractItem(ItemStack stack, boolean simulate) {
            for (int slot = 0; slot < getSlots(); slot++) {
                if (ItemStack.isSameItemSameTags(getStackInSlot(slot), stack)) {
                    return extractItem(slot, stack.getCount(), simulate);
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public Set<ItemStackKey> getTrackedStacks() {
            Set<ItemStackKey> result = new HashSet<>();
            for (int slot = 0; slot < getSlots(); slot++) {
                if (!getStackInSlot(slot).isEmpty()) result.add(ItemStackKey.of(getStackInSlot(slot)));
            }
            return result;
        }

        @Override
        public void registerTrackingListeners(Consumer<ItemStackKey> added, Consumer<ItemStackKey> removed,
                                              Runnable firstEmpty, Runnable lastEmpty) {}

        @Override
        public void unregisterStackKeyListeners() {}

        @Override
        public boolean hasEmptySlots() {
            for (int slot = 0; slot < getSlots(); slot++) if (getStackInSlot(slot).isEmpty()) return true;
            return false;
        }

        @Override
        public boolean isInsertBlocked() { return false; }
    }
}

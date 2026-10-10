package dev.uppermoon.solintegrations.mixin.integration.backpack;

import dev.uppermoon.solintegrations.integration.backpack.BackpackFoodScore;
import dev.uppermoon.solintegrations.integration.backpack.DepletionOrder;
import dev.uppermoon.solintegrations.integration.backpack.NativeFeedingEligibility;
import dev.uppermoon.solintegrations.integration.backpack.OrderedItemHandler;

import com.github.leopoko.solclassic.network.FoodHistoryHolder;
import com.github.leopoko.solclassic.item.WickerBasketItem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uppermoon.solintegrations.config.SolIntegrationsConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Changes only feeding traversal order. The existing stack callback remains
 * authoritative for filters, hunger thresholds, modded eligibility, item use,
 * containers, history, and the successful-attempt stop condition.
 *
 * Target verified against Sophisticated Core 1.3.84.2308. No consumption code
 * from Sophisticated Core is copied or replaced.
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.upgrades.feeding.FeedingUpgradeWrapper", remap = false)
public abstract class FeedingUpgradeOrderMixin {
    @Shadow(remap = false)
    public abstract FilterLogic getFilterLogic();

    @Shadow(remap = false)
    public abstract boolean shouldFeedImmediatelyWhenHurt();

    @Shadow(remap = false)
    private static boolean isEdible(ItemStack stack, LivingEntity player) {
        throw new AssertionError("Mixin shadow");
    }

    @Shadow(remap = false)
    private boolean isHungryEnoughForFood(int hungerLevel, ItemStack stack, Player player) {
        throw new AssertionError("Mixin shadow");
    }

    @WrapOperation(
            method = "tryFeedingFoodFromStorage(Lnet/minecraft/world/level/Level;ILnet/minecraft/world/entity/player/Player;)Z",
            at = @At(value = "INVOKE", target =
                    "Lnet/p3pp3rf1y/sophisticatedcore/util/InventoryHelper;iterate(Lnet/minecraftforge/items/IItemHandler;Ljava/util/function/BiFunction;Ljava/util/function/Supplier;Ljava/util/function/Predicate;)Ljava/lang/Object;"),
            remap = false,
            require = 1,
            expect = 1,
            allow = 1)
    private Object solintegrations$preferLeastDepleted(
            IItemHandler inventory,
            BiFunction<Integer, ItemStack, Object> attempt,
            Supplier<Object> defaultResult,
            Predicate<Object> shouldStop,
            Operation<Object> original,
            Level level,
            int hungerLevel,
            Player player) {
        if (!SolIntegrationsConfig.backpackSelectionEnabled()
                || level.isClientSide || FoodHistoryHolder.INSTANCE == null) {
            return original.call(inventory, attempt, defaultResult, shouldStop);
        }

        int[] order = DepletionOrder.slots(
                inventory.getSlots(),
                inventory::getStackInSlot,
                stack -> BackpackFoodScore.multiplier(stack, player));
        OrderedItemHandler ordered = new OrderedItemHandler(inventory, order);
        BiFunction<Integer, ItemStack, Object> physicalAttempt = (viewSlot, stack) -> {
            // SoL Classic's basket HEAD injection otherwise consumes before
            // Core can run any of these native, potentially transformed checks.
            if (stack.getItem() instanceof WickerBasketItem
                    && !solintegrations$passesNativeBasketChecks(stack, hungerLevel, player)) {
                return false;
            }
            return attempt.apply(ordered.physicalSlot(viewSlot), stack);
        };
        // Keep other wrappers and the original traversal/default/stop behavior.
        return original.call(ordered, physicalAttempt, defaultResult, shouldStop);
    }

    @Unique
    private boolean solintegrations$passesNativeBasketChecks(ItemStack stack, int hungerLevel, Player player) {
        // Classic hides basket FoodProperties unless the basket is in the
        // player's own inventory. For its supported backpack shortcut, evaluate
        // the selected actual meal, while user filters still see the basket.
        ItemStack meal = com.github.leopoko.solclassic.item.WickerBasketItem.getMostNutritiousFood(stack, player);
        if (meal.isEmpty()) return false;
        return NativeFeedingEligibility.allows(
                () -> isEdible(meal, player),
                () -> getFilterLogic().matchesFilter(stack),
                () -> isHungryEnoughForFood(hungerLevel, meal, player),
                () -> shouldFeedImmediatelyWhenHurt() && hungerLevel > 0
                        && player.getHealth() < player.getMaxHealth() - 0.1F);
    }
}

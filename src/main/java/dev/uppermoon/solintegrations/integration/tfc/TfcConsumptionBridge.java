package dev.uppermoon.solintegrations.integration.tfc;

import com.github.leopoko.solclassic.forge.config.SolClassicConfigForge;
import com.github.leopoko.solclassic.item.WickerBasketItem;
import com.github.leopoko.solclassic.network.FoodHistoryHolder;
import com.github.leopoko.solclassic.utils.FoodCalculator;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.IFood;
import net.dries007.tfc.common.capabilities.food.TFCFoodData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Classic owns history, synchronization, basket selection, and vanilla effects.
 * This bridge restores the native TFC call that Classic's redirect bypasses.
 */
public final class TfcConsumptionBridge {
    private TfcConsumptionBridge() {}

    @Nullable
    public static Meal capture(Player player, ItemStack stack) {
        if (!TfcBridgeOptions.enabled() || !(player.getFoodData() instanceof TFCFoodData tfc)) {
            return null;
        }

        // The normal basket delegates to the actual food's finishUsingItem.
        // Some auto-feeders call Player.eat with the basket itself instead.
        // Resolve that path before Classic changes history and therefore ranking.
        boolean directBasket = stack.getItem() instanceof WickerBasketItem;
        ItemStack meal = directBasket
                ? WickerBasketItem.getMostNutritiousFood(stack, player) : stack;
        if (meal.isEmpty()) {
            return null;
        }
        IFood food = FoodCapability.get(meal);
        if (food == null) {
            return null;
        }
        String id = BuiltInRegistries.ITEM.getKey(meal.getItem()).toString();
        boolean blacklisted = SolClassicConfigForge.CONFIG.foodBlacklist.get().contains(id);
        float multiplier = blacklisted ? 1.0F : FoodCalculator.CalculateMultiplier(meal, player);
        boolean classicSkipsBasketShrink = directBasket && (blacklisted
                || (FoodHistoryHolder.INSTANCE.getEffectiveNutrition(meal, player) == 0
                && FoodHistoryHolder.INSTANCE.getEffectiveSaturationModifier(meal, player) == 0.0F));
        ItemStack basket = directBasket ? stack : ItemStack.EMPTY;
        CompoundTag basketOrigin = directBasket ? BasketMealOrigins.get(meal) : null;
        BasketConsumptionScope currentBasket = BasketConsumptionScope.current();
        if (!directBasket && currentBasket != null) {
            basketOrigin = currentBasket.claim(meal, player);
            if (basketOrigin != null) basket = currentBasket.basket();
        }
        // Keep the live capability. Copying/rebuilding FoodData would lose TFG's
        // identity-keyed extended nutrients and other food-specific state.
        return new Meal(tfc, food, multiplier, blacklisted,
                classicSkipsBasketShrink && player instanceof ServerPlayer ? stack : ItemStack.EMPTY, meal,
                basket, basketOrigin, directBasket);
    }

    public record Meal(TFCFoodData target, IFood food, float multiplier, boolean blacklisted,
                       ItemStack unconsumedDirectBasket, ItemStack selectedFood,
                       ItemStack basket, @Nullable CompoundTag basketOrigin, boolean directBasket) {
        public ConsumptionScope openScope() {
            return ConsumptionScope.open(target, food, selectedFood, multiplier, blacklisted);
        }

        public void consume() {
            // Native IFood handling owns rotten-food effects and advancements.
            // Native FoodData handling owns water, nutrition, TFG hooks and caps.
            target.eat(food);
        }

        public void completeBasketConsumption() {
            // Classic's blacklist/zero-value return precedes its direct-basket
            // shrink. Repair that specific path so native benefits cannot be free.
            // The ordinary finishUsingItem basket path still owns its own shrink.
            if (!unconsumedDirectBasket.isEmpty()) {
                WickerBasketItem.shrinkItemFromInventory(unconsumedDirectBasket, selectedFood);
            }
        }
    }
}

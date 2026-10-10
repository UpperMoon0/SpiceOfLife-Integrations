package dev.uppermoon.solintegrations.integration.backpack;

import com.github.leopoko.solclassic.config.SolclassicConfigData;
import com.github.leopoko.solclassic.item.WickerBasketItem;
import com.github.leopoko.solclassic.utils.FoodCalculator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Read-only SoL Classic 2.13 score, independent of base food nutrition. */
public final class BackpackFoodScore {
    private BackpackFoodScore() {
    }

    public static double multiplier(ItemStack stack, Player player) {
        if (stack.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }

        // Rank the meal that SoL's existing basket compatibility will eat.
        // Its selector reads NBT; it does not save or shrink the basket.
        ItemStack meal = stack.getItem() instanceof WickerBasketItem
                ? WickerBasketItem.getMostNutritiousFood(stack, player)
                : stack;
        if (meal.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(meal.getItem()).toString();
        if (SolclassicConfigData.foodBlacklist.contains(itemId)) {
            // SoL excludes these foods from depletion and history updates.
            return 1.0D;
        }
        return FoodCalculator.CalculateMultiplier(meal, player);
    }
}

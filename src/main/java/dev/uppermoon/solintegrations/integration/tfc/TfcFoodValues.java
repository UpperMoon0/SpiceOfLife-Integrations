package dev.uppermoon.solintegrations.integration.tfc;

import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.FoodData;
import net.dries007.tfc.common.capabilities.food.IFood;
import net.dries007.tfc.common.capabilities.food.TFCFoodData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Read-only effective values shared with Classic; no FoodData reconstruction. */
public final class TfcFoodValues {
    private TfcFoodValues() {}

    @Nullable
    public static FoodData data(ItemStack stack, @Nullable Player player) {
        if (!TfcBridgeOptions.enabled()
                || (player != null && !(player.getFoodData() instanceof TFCFoodData))) {
            return null;
        }
        ConsumptionScope captured = ConsumptionScope.forStack(stack);
        IFood food = captured != null ? (IFood) captured.food() : FoodCapability.get(stack);
        // Deliberately expose BASE values even for rotten food. Returning zero
        // would make Classic skip that consumed item's history. Only native
        // TFCFoodData.eat(IFood) decides whether to award restoration or rot effects.
        return food != null ? food.getData() : null;
    }

}

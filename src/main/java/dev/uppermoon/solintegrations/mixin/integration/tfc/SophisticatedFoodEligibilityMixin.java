package dev.uppermoon.solintegrations.mixin.integration.tfc;

import dev.uppermoon.solintegrations.integration.tfc.FeedingNutritionScope;
import dev.uppermoon.solintegrations.integration.tfc.TfcFoodValues;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.dries007.tfc.common.capabilities.food.FoodData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Keeps the complete original eligibility result authoritative, including TFG
 * tags and blacklists. Only Classic's nested numeric check receives native H.
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.upgrades.feeding.FeedingUpgradeWrapper", remap = false)
public abstract class SophisticatedFoodEligibilityMixin {
    @WrapMethod(method = "isEdible(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z", remap = false)
    private static boolean solintegrations$scopeNativeEligibility(ItemStack stack, LivingEntity consumer,
                                                                  Operation<Boolean> original) {
        FoodData food = consumer instanceof Player player ? TfcFoodValues.data(stack, player) : null;
        try (FeedingNutritionScope ignored = FeedingNutritionScope.open(stack, consumer,
                food == null ? null : food.hunger())) {
            return original.call(stack, consumer);
        }
    }
}

package dev.uppermoon.solintegrations.mixin.integration.tfc;

import dev.uppermoon.solintegrations.integration.tfc.BasketConsumptionScope;
import dev.uppermoon.solintegrations.integration.tfc.ConsumptionScope;
import dev.uppermoon.solintegrations.integration.tfc.TfcConsumptionBridge;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
public abstract class PlayerConsumptionMixin {
    @WrapMethod(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;")
    private ItemStack solintegrations$restoreNativeTfcMeal(Level level, ItemStack stack,
                                                          Operation<ItemStack> original) {
        TfcConsumptionBridge.Meal meal = TfcConsumptionBridge.capture((Player) (Object) this, stack);
        if (meal == null) {
            return original.call(level, stack);
        }
        BasketConsumptionScope directBasket = meal.directBasket()
                ? BasketConsumptionScope.open(meal.basket(), (Player) (Object) this) : null;
        try {
            try (ConsumptionScope scope = meal.openScope()) {
                ItemStack result = original.call(level, stack);
                if (!meal.basket().isEmpty()) {
                    BasketConsumptionScope basketScope = BasketConsumptionScope.forBasket(meal.basket());
                    if (basketScope != null) basketScope.defer(meal, scope.nativeConsumed());
                    meal.completeBasketConsumption();
                    if (directBasket != null) directBasket.complete();
                } else if (!scope.nativeConsumed()) {
                    meal.consume();
                }
                return result;
            }
        } finally {
            if (directBasket != null) directBasket.close();
        }
    }
}

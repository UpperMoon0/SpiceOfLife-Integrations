package dev.uppermoon.solintegrations.mixin.integration.tfc;

import dev.uppermoon.solintegrations.integration.tfc.BasketConsumptionScope;
import dev.uppermoon.solintegrations.integration.tfc.BasketMealOrigins;
import dev.uppermoon.solintegrations.integration.tfc.TfcBridgeOptions;

import com.github.leopoko.solclassic.item.WickerBasketItem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rotten food remains manually edible, but is never selected automatically. */
@Mixin(value = WickerBasketItem.class, remap = false)
public abstract class WickerBasketTfcMixin {
    @WrapOperation(
            method = "getMostNutritiousFood(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lcom/github/leopoko/solclassic/utils/BasketBlacklist;isBlacklisted(Lnet/minecraft/world/item/ItemStack;)Z"),
            require = 1)
    private static boolean solintegrations$skipRottenAutomaticMeal(ItemStack food, Operation<Boolean> original) {
        return original.call(food) || (TfcBridgeOptions.enabled() && FoodCapability.isRotten(food));
    }

    @WrapOperation(method = "getMostNutritiousFood", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;of(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/item/ItemStack;", remap = true), require = 1)
    private static ItemStack solintegrations$rememberPersistedMeal(CompoundTag source, Operation<ItemStack> original) {
        CompoundTag persisted = source.copy();
        ItemStack candidate = original.call(source);
        if (TfcBridgeOptions.enabled() && FoodCapability.get(candidate) != null) {
            BasketMealOrigins.remember(candidate, persisted);
        }
        return candidate;
    }

    @Inject(method = "getMostNutritiousFood", at = @At("RETURN"), require = 1)
    private static void solintegrations$observeSelection(ItemStack basket, Player player, CallbackInfoReturnable<ItemStack> cir) {
        BasketConsumptionScope scope = BasketConsumptionScope.forBasket(basket);
        if (scope != null) scope.selected(cir.getReturnValue(), player);
    }

    @WrapMethod(method = "finishUsingItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;", remap = true)
    private ItemStack solintegrations$confirmBasketConsumption(ItemStack basket, Level level, LivingEntity entity,
                                                              Operation<ItemStack> original) {
        if (!TfcBridgeOptions.enabled()) return original.call(basket, level, entity);
        try (BasketConsumptionScope scope = BasketConsumptionScope.open(basket, entity)) {
            ItemStack result = original.call(basket, level, entity);
            scope.complete();
            return result;
        }
    }

    @WrapOperation(method = "finishUsingItem", remap = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;", remap = true), require = 1)
    private ItemStack solintegrations$bindExactDelegatedStack(ItemStack food, Level level, LivingEntity entity,
                                                            Operation<ItemStack> original) {
        BasketConsumptionScope scope = BasketConsumptionScope.current();
        if (scope == null) return original.call(food, level, entity);
        scope.beginDelegation(food, entity);
        try {
            return original.call(food, level, entity);
        } finally {
            scope.endDelegation(food);
        }
    }

    @Inject(method = "shrinkItemFromInventory", at = @At("HEAD"), cancellable = true, require = 1)
    private static void solintegrations$consumeExactPersistedMeal(ItemStack basket, ItemStack selected, CallbackInfo ci) {
        if (!TfcBridgeOptions.enabled() || FoodCapability.get(selected) == null) return;
        CompoundTag origin = BasketMealOrigins.get(selected);
        if (BasketMealOrigins.consume(basket, origin)) {
            BasketConsumptionScope scope = BasketConsumptionScope.forBasket(basket);
            if (scope != null) scope.removed(origin);
        }
        // A missing provenance/match is deliberately a safe failure. Never fall
        // back to removing a different same-ID food or award unconsumed benefits.
        ci.cancel();
    }
}

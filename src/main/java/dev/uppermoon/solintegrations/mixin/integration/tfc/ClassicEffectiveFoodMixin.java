package dev.uppermoon.solintegrations.mixin.integration.tfc;

import dev.uppermoon.solintegrations.integration.tfc.RestorationPolicy;
import dev.uppermoon.solintegrations.integration.tfc.TfcFoodValues;

import com.github.leopoko.solclassic.forge.network.FoodEventHandlerForge;
import net.dries007.tfc.common.capabilities.food.FoodData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adapt Classic's two public value getters, leaving its history owner unchanged. */
@Mixin(value = FoodEventHandlerForge.class, remap = false)
public abstract class ClassicEffectiveFoodMixin {
    @Inject(method = "getEffectiveNutrition(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)I",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void solintegrations$nativeHunger(ItemStack stack, Player player, CallbackInfoReturnable<Integer> cir) {
        FoodData data = TfcFoodValues.data(stack, player);
        if (data != null) {
            cir.setReturnValue(data.hunger());
        }
    }

    @Inject(method = "getEffectiveSaturationModifier(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)F",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void solintegrations$nativeSaturation(ItemStack stack, Player player, CallbackInfoReturnable<Float> cir) {
        FoodData data = TfcFoodValues.data(stack, player);
        if (data != null) {
            cir.setReturnValue(RestorationPolicy.saturationModifier(data.hunger(), data.saturation()));
        }
    }
}

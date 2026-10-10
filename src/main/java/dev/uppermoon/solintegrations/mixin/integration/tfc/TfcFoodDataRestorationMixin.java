package dev.uppermoon.solintegrations.mixin.integration.tfc;

import dev.uppermoon.solintegrations.integration.tfc.ConsumptionScope;
import dev.uppermoon.solintegrations.integration.tfc.RestorationPolicy;
import dev.uppermoon.solintegrations.integration.tfc.RestorationScope;
import dev.uppermoon.solintegrations.integration.tfc.TfcBridgeOptions;

import com.github.leopoko.solclassic.utils.FoodCalculator;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.dries007.tfc.common.capabilities.food.IFood;
import net.dries007.tfc.common.capabilities.food.TFCFoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = TFCFoodData.class, remap = false)
public abstract class TfcFoodDataRestorationMixin {
    @WrapMethod(method = "eat(Lnet/dries007/tfc/common/capabilities/food/IFood;)V", remap = false)
    private void solintegrations$scopeActualFood(IFood food, Operation<Void> original) {
        ConsumptionScope consumption = ConsumptionScope.forFood(this, food);
        if (consumption == null) {
            original.call(food);
            return;
        }
        consumption.markNativeConsumed();
        try (RestorationScope ignored = RestorationScope.open(this, consumption.multiplier(), consumption.blacklisted())) {
            original.call(food);
        }
    }

    @ModifyArg(
            method = "eat(Lnet/dries007/tfc/common/capabilities/food/FoodData;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(IF)V", remap = true),
            index = 0,
            require = 1)
    private int solintegrations$scaleRestorationOnly(int nativeHunger) {
        RestorationScope scope = RestorationScope.current(this);
        if (scope == null || scope.blacklisted()) {
            return nativeHunger;
        }
        // Leave argument 1 at TFC's native S/(2H). Vanilla therefore restores
        // S * H_effective/H. Never mutate the FoodData used by nutrition hooks.
        int classicHunger = FoodCalculator.CalculateNutrition(nativeHunger, scope.multiplier());
        return RestorationPolicy.hunger(nativeHunger, scope.multiplier(), classicHunger,
                TfcBridgeOptions.positiveMinimum(), TfcBridgeOptions.minimumThreshold());
    }
}

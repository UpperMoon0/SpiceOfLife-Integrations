package dev.uppermoon.solintegrations.mixin.integration.tfc;

import dev.uppermoon.solintegrations.integration.tfc.FeedingNutritionScope;
import dev.uppermoon.solintegrations.integration.tfc.RestorationPolicy;
import dev.uppermoon.solintegrations.integration.tfc.TfcBridgeOptions;

import com.github.leopoko.solclassic.utils.FoodCalculator;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/** Adapts Classic's SB eligibility operand without targeting a renamed callback. */
@Mixin(value = FoodCalculator.class, remap = false)
public abstract class ClassicFeedingNutritionMixin {
    @WrapMethod(method = "CalculateNutrition(IF)I", remap = false)
    private static int solintegrations$useNativeEligibilityHunger(int hunger, float multiplier,
                                                                 Operation<Integer> original) {
        FeedingNutritionScope scope = FeedingNutritionScope.current();
        if (scope == null || scope.nativeHunger() == null) return original.call(hunger, multiplier);
        int nativeHunger = scope.nativeHunger();
        int classicHunger = original.call(nativeHunger, multiplier);
        return RestorationPolicy.hunger(nativeHunger, multiplier, classicHunger,
                TfcBridgeOptions.positiveMinimum(), TfcBridgeOptions.minimumThreshold());
    }
}

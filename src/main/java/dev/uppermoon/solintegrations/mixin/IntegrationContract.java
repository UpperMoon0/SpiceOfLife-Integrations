package dev.uppermoon.solintegrations.mixin;

import java.util.List;
import java.util.function.Function;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Checks raw optional-mod bytecode without defining optional classes or asking
 * Mixin to transform a target recursively. Version numbers are not API checks.
 * This establishes structural compatibility; gameplay is tested separately.
 */
public final class IntegrationContract {
    private static final String FOOD = "net/dries007/tfc/common/capabilities/food/";
    private static final String CORE = "net/p3pp3rf1y/sophisticatedcore/";
    private static final String STACK = "Lnet/minecraft/world/item/ItemStack;";
    private static final String PLAYER = "Lnet/minecraft/world/entity/player/Player;";
    private static final String LIVING = "Lnet/minecraft/world/entity/LivingEntity;";
    private IntegrationContract() {}

    public static String tfc(Function<String, ClassNode> classes) {
        try {
            ClassNode data = required(classes, FOOD + "TFCFoodData");
            if (!"net/minecraft/world/food/FoodData".equals(data.superName))
                return "TFCFoodData no longer extends vanilla FoodData";
            MethodNode nativeEat = method(data, "eat", "(L" + FOOD + "IFood;)V", false);
            if (calls(nativeEat, FOOD + "TFCFoodData", List.of("eat"), "(L" + FOOD + "FoodData;)V") != 1)
                return "TFCFoodData.eat(IFood) must delegate to eat(FoodData) exactly once";
            MethodNode eat = method(data, "eat", "(L" + FOOD + "FoodData;)V", false);
            long calls = calls(eat, "net/minecraft/world/food/FoodData", List.of("eat", "m_38707_"), "(IF)V");
            if (calls != 1) return "TFCFoodData.eat(FoodData) must invoke vanilla eat(IF)V exactly once; found " + calls;
            method(required(classes, FOOD + "IFood"), "getData", "()L" + FOOD + "FoodData;", false);
            ClassNode values = required(classes, FOOD + "FoodData");
            method(values, "hunger", "()I", false);
            method(values, "saturation", "()F", false);
            ClassNode capability = required(classes, FOOD + "FoodCapability");
            method(capability, "get", "(" + STACK + ")L" + FOOD + "IFood;", true);
            method(capability, "isRotten", "(" + STACK + ")Z", true);
            return null;
        } catch (IllegalArgumentException ex) { return ex.getMessage(); }
    }

    public static String core(Function<String, ClassNode> classes) {
        try {
            ClassNode wrapper = required(classes, CORE + "upgrades/feeding/FeedingUpgradeWrapper");
            method(wrapper, "isEdible", "(" + STACK + LIVING + ")Z", true);
            method(wrapper, "isHungryEnoughForFood", "(I" + STACK + PLAYER + ")Z", false);
            method(wrapper, "getFilterLogic", "()L" + CORE + "upgrades/FilterLogic;", false);
            method(wrapper, "shouldFeedImmediatelyWhenHurt", "()Z", false);
            MethodNode feeding = method(wrapper, "tryFeedingFoodFromStorage",
                    "(Lnet/minecraft/world/level/Level;I" + PLAYER + ")Z", false);
            String iterate = "(Lnet/minecraftforge/items/IItemHandler;Ljava/util/function/BiFunction;Ljava/util/function/Supplier;Ljava/util/function/Predicate;)Ljava/lang/Object;";
            long calls = calls(feeding, CORE + "util/InventoryHelper", List.of("iterate"), iterate);
            if (calls != 1) return "FeedingUpgradeWrapper storage traversal must invoke InventoryHelper.iterate exactly once; found " + calls;
            method(required(classes, CORE + "upgrades/FilterLogic"), "matchesFilter", "(" + STACK + ")Z", false);
            method(required(classes, CORE + "util/InventoryHelper"), "iterate", iterate, true);
            return null;
        } catch (IllegalArgumentException ex) { return ex.getMessage(); }
    }

    private static ClassNode required(Function<String, ClassNode> classes, String name) {
        ClassNode node = classes.apply(name);
        if (node == null) throw new IllegalArgumentException("missing class " + name);
        return node;
    }
    private static MethodNode method(ClassNode node, String name, String desc, boolean isStatic) {
        MethodNode result = node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)
                && ((m.access & Opcodes.ACC_STATIC) != 0) == isStatic)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("missing " +
                        (isStatic ? "static " : "instance ") + node.name + "." + name + desc));
        if ((node.name.startsWith(FOOD) || name.equals("matchesFilter") || name.equals("iterate")
                || name.equals("getFilterLogic") || name.equals("shouldFeedImmediatelyWhenHurt"))
                && (result.access & Opcodes.ACC_PUBLIC) == 0)
            throw new IllegalArgumentException("API is no longer public: " + node.name + "." + name + desc);
        return result;
    }
    private static long calls(MethodNode method, String owner, List<String> names, String desc) {
        long count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner)
                    && names.contains(call.name) && call.desc.equals(desc)) count++;
        }
        return count;
    }
}

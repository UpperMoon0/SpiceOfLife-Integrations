package dev.uppermoon.solintegrations.mixin;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

class IntegrationContractTest {
    private static final String F = "net/dries007/tfc/common/capabilities/food/";
    private static final String C = "net/p3pp3rf1y/sophisticatedcore/";
    private static final String S = "Lnet/minecraft/world/item/ItemStack;";
    private static final String P = "Lnet/minecraft/world/entity/player/Player;";
    private static final String L = "Lnet/minecraft/world/entity/LivingEntity;";
    private static final String ITERATE = "(Lnet/minecraftforge/items/IItemHandler;Ljava/util/function/BiFunction;Ljava/util/function/Supplier;Ljava/util/function/Predicate;)Ljava/lang/Object;";

    private Map<String, ClassNode> tfc(String vanillaEat) {
        Map<String, ClassNode> classes = new HashMap<>();
        ClassNode data = node(classes, F + "TFCFoodData");
        data.superName = "net/minecraft/world/food/FoodData";
        MethodNode nativeEat = add(data, "eat", "(L" + F + "IFood;)V", false);
        nativeEat.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, F + "TFCFoodData", "eat", "(L" + F + "FoodData;)V", false));
        MethodNode eat = add(data, "eat", "(L" + F + "FoodData;)V", false);
        eat.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, data.superName, vanillaEat, "(IF)V", false));
        add(node(classes, F + "IFood"), "getData", "()L" + F + "FoodData;", false);
        ClassNode values = node(classes, F + "FoodData");
        add(values, "hunger", "()I", false);
        add(values, "saturation", "()F", false);
        ClassNode cap = node(classes, F + "FoodCapability");
        add(cap, "get", "(" + S + ")L" + F + "IFood;", true);
        add(cap, "isRotten", "(" + S + ")Z", true);
        return classes;
    }
    private Map<String, ClassNode> core() {
        Map<String, ClassNode> classes = new HashMap<>();
        ClassNode wrapper = node(classes, C + "upgrades/feeding/FeedingUpgradeWrapper");
        add(wrapper, "isEdible", "(" + S + L + ")Z", true);
        add(wrapper, "isHungryEnoughForFood", "(I" + S + P + ")Z", false);
        add(wrapper, "getFilterLogic", "()L" + C + "upgrades/FilterLogic;", false);
        add(wrapper, "shouldFeedImmediatelyWhenHurt", "()Z", false);
        MethodNode feeding = add(wrapper, "tryFeedingFoodFromStorage", "(Lnet/minecraft/world/level/Level;I" + P + ")Z", false);
        feeding.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, C + "util/InventoryHelper", "iterate", ITERATE, false));
        add(node(classes, C + "upgrades/FilterLogic"), "matchesFilter", "(" + S + ")Z", false);
        add(node(classes, C + "util/InventoryHelper"), "iterate", ITERATE, true);
        return classes;
    }
    private ClassNode node(Map<String, ClassNode> classes, String name) {
        ClassNode node = new ClassNode(); node.name = name; classes.put(name, node); return node;
    }
    private MethodNode add(ClassNode node, String name, String desc, boolean isStatic) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | (isStatic ? Opcodes.ACC_STATIC : 0), name, desc, null, null);
        node.methods.add(method); return method;
    }
    @Test void acceptsDevelopmentTfc() { assertNull(IntegrationContract.tfc(tfc("eat")::get)); }
    @Test void acceptsPackagedTfc() { assertNull(IntegrationContract.tfc(tfc("m_38707_")::get)); }
    @Test void missingOptionalClassExplainsFailure() { assertTrue(IntegrationContract.tfc(Map.<String, ClassNode>of()::get).contains("missing class")); }
    @Test void rejectsChangedTfcBase() {
        var classes = tfc("eat"); classes.get(F + "TFCFoodData").superName = "java/lang/Object";
        assertTrue(IntegrationContract.tfc(classes::get).contains("extends"));
    }
    @Test void rejectsMissingVanillaRestoration() { assertTrue(IntegrationContract.tfc(tfc("renamed")::get).contains("found 0")); }
    @Test void rejectsDuplicateRestoration() {
        var classes = tfc("eat"); var eat = classes.get(F + "TFCFoodData").methods.get(1);
        eat.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/food/FoodData", "eat", "(IF)V", false));
        assertTrue(IntegrationContract.tfc(classes::get).contains("found 2"));
    }
    @Test void rejectsChangedFoodApi() {
        var classes = tfc("eat"); classes.get(F + "FoodData").methods.get(0).desc = "()F";
        assertTrue(IntegrationContract.tfc(classes::get).contains("hunger()I"));
    }
    @Test void rejectsChangedStaticness() {
        var classes = tfc("eat"); classes.get(F + "FoodCapability").methods.get(0).access &= ~Opcodes.ACC_STATIC;
        assertTrue(IntegrationContract.tfc(classes::get).contains("static"));
    }
    @Test void rejectsRemovedNativeDelegation() {
        var classes = tfc("eat"); classes.get(F + "TFCFoodData").methods.get(0).instructions.clear();
        assertTrue(IntegrationContract.tfc(classes::get).contains("delegate"));
    }
    @Test void rejectsPrivateFoodApi() {
        var classes = tfc("eat"); classes.get(F + "FoodData").methods.get(0).access = Opcodes.ACC_PRIVATE;
        assertTrue(IntegrationContract.tfc(classes::get).contains("no longer public"));
    }
    @Test void acceptsCoreContract() { assertNull(IntegrationContract.core(core()::get)); }
    @Test void rejectsCoreEligibilityChange() {
        var classes = core(); classes.get(C + "upgrades/feeding/FeedingUpgradeWrapper").methods.remove(0);
        assertTrue(IntegrationContract.core(classes::get).contains("isEdible"));
    }
    @Test void rejectsCoreTraversalChange() {
        var classes = core(); classes.get(C + "upgrades/feeding/FeedingUpgradeWrapper").methods.get(4).instructions.clear();
        assertTrue(IntegrationContract.core(classes::get).contains("found 0"));
    }
    @Test void rejectsCoreFilterChange() {
        var classes = core(); classes.get(C + "upgrades/FilterLogic").methods.clear();
        assertTrue(IntegrationContract.core(classes::get).contains("matchesFilter"));
    }
}

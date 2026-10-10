package dev.uppermoon.solintegrations.integration.backpack;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GameTest-only lifecycle accommodation, excluded from the production artifact.
 *
 * Unmodified pinned TFC queries recipe-output capabilities during the initial
 * resource reload. A Backpacks wrapper then reads its SERVER config before
 * Forge's normal world-config load. Forge's development-only guard aborts that
 * upstream combination before any tests execute. Seed only the ordinary spec
 * defaults in memory when missing; normal world-config loading still follows.
 * Core also lazily appends missing enabled-item defaults while recipes load in
 * parallel. Repeated per-item autosaves race its file watcher in this profile.
 * Preseed missing registered Backpacks IDs with the same true default in one
 * normal config update, preserving every existing entry and explicit value.
 * This does not change feeding logic, skip assertions, suppress config errors,
 * disable config watchers, or mask the recorded unmodified startup failure.
 */
@Mod.EventBusSubscriber(modid = "solintegrations", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BackpackGameTestBootstrap {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BackpackGameTestBootstrap() {}

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("tfc") && ModList.get().isLoaded("sophisticatedbackpacks")) {
            event.enqueueWork(() -> {
                initializeMissingServerDefaults();
                preseedMissingEnabledItems();
            });
        }
    }

    private static void initializeMissingServerDefaults() {
        try {
            Class<?> config = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.Config");
            ForgeConfigSpec spec = (ForgeConfigSpec) config.getField("SERVER_SPEC").get(null);
            if (spec.isLoaded()) {
                LOGGER.info("[GameTest harness] Backpacks SERVER spec already loaded; no defaults seeded");
                return;
            }
            CommentedConfig defaults = CommentedConfig.inMemory();
            int corrected = spec.correct(defaults);
            spec.setConfig(defaults);
            LOGGER.warn("[GameTest harness only] Seeded {} Backpacks SERVER defaults in memory before TFC recipe capability scan; normal world config loading remains enabled",
                    corrected);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot initialize verified Backpacks SERVER spec for the combined GameTest harness", exception);
        }
    }

    private static void preseedMissingEnabledItems() {
        try {
            Class<?> config = Class.forName("net.p3pp3rf1y.sophisticatedcore.Config");
            ForgeConfigSpec spec = (ForgeConfigSpec) config.getField("COMMON_SPEC").get(null);
            if (!spec.isLoaded()) {
                throw new IllegalStateException("Expected Forge to load Core COMMON config before GameTest common setup");
            }
            ForgeConfigSpec.ConfigValue<List<String>> setting = spec.getValues().get(List.of("common", "enabledItems"));
            List<String> original = setting.get();
            List<String> complete = new ArrayList<>(original);
            Set<String> existingIds = new HashSet<>();
            for (String entry : original) {
                String[] fields = entry.split("\\|");
                if (fields.length == 2) existingIds.add(fields[0]);
            }
            BuiltInRegistries.ITEM.keySet().stream()
                    .filter(id -> id.getNamespace().equals("sophisticatedbackpacks"))
                    .map(Object::toString).sorted()
                    .filter(id -> !existingIds.contains(id))
                    .forEach(id -> complete.add(id + "|true"));
            int missing = complete.size() - original.size();
            if (missing > 0) {
                setting.set(complete);
                LOGGER.warn("[GameTest harness only] Added {} missing Backpacks enabled-item defaults in one Core COMMON update before parallel recipes; preserved all {} existing entries",
                        missing, original.size());
            } else {
                LOGGER.info("[GameTest harness] All Backpacks enabled-item entries already present; Core COMMON unchanged");
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot preseed verified Core enabled-item defaults for the combined GameTest harness", exception);
        }
    }
}

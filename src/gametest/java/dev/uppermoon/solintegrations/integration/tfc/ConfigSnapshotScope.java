package dev.uppermoon.solintegrations.integration.tfc;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.io.StringReader;
import java.io.StringWriter;

/** Test-only isolated config mutation; never changes the watched world file. */
public final class ConfigSnapshotScope implements AutoCloseable {
    private final ForgeConfigSpec spec;
    private final CommentedConfig original;

    private ConfigSnapshotScope(ForgeConfigSpec spec) {
        this.spec = spec;
        ModConfig config = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER).stream()
                .filter(candidate -> candidate.getSpec() == spec)
                .findFirst().orElseThrow(() -> new IllegalStateException("Server config is not registered"));
        original = config.getConfigData();
        if (original == null) throw new IllegalStateException("Server config is not loaded");
        // Round-trip only the test configuration, not food stacks. Unlike a
        // shallow map copy, this detaches nested configs/lists from FileConfig.
        StringWriter serialized = new StringWriter();
        new TomlWriter().write(original, serialized);
        CommentedConfig detached = new TomlParser().parse(new StringReader(serialized.toString()));
        spec.setConfig(detached);
    }

    public static ConfigSnapshotScope open(ForgeConfigSpec spec) { return new ConfigSnapshotScope(spec); }

    @Override public void close() {
        spec.setConfig(original);
    }
}

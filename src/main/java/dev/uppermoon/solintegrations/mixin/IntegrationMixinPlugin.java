package dev.uppermoon.solintegrations.mixin;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Optional types must not be linked before their mods are discovered. */
public final class IntegrationMixinPlugin implements IMixinConfigPlugin {
    private final Map<String, String> failures = new HashMap<>();

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!SupportedVersions.allows(mixin, this::version)) {
            LoggerFactory.getLogger("solintegrations").info("Integration inactive (required mod absent or unsupported Classic): {}", mixin);
            return false;
        }
        for (String id : SupportedVersions.optionalMods(mixin)) {
            if (!failures.containsKey(id)) {
                String failure;
                try {
                    failure = id.equals("tfc") ? IntegrationContract.tfc(name -> read(id, name))
                            : IntegrationContract.core(name -> read(id, name));
                } catch (RuntimeException ex) {
                    failure = "cannot inspect bytecode: " + ex.getMessage();
                }
                failures.put(id, failure);
                if (failure == null) LoggerFactory.getLogger("solintegrations").info(
                        "Integration API compatible: {} {}", id, version(id));
                else LoggerFactory.getLogger("solintegrations").error(
                        "Integration disabled: {} {}: {}", id, version(id), failure);
            }
            if (failures.get(id) != null) return false;
        }
        return true;
    }
    private String version(String id) {
        var info = LoadingModList.get().getModFileById(id);
        if (info == null) return null;
        return info.getMods().stream().filter(mod -> mod.getModId().equals(id))
                .map(mod -> mod.getVersion().toString()).findFirst().orElse(null);
    }
    private ClassNode read(String id, String name) {
        var info = LoadingModList.get().getModFileById(id);
        if (info == null) return null;
        var path = info.getFile().findResource(name + ".class");
        if (!Files.isRegularFile(path)) return null;
        try (var stream = Files.newInputStream(path)) {
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        } catch (IOException ex) { throw new IllegalStateException(name + ": " + ex.getMessage(), ex); }
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}

package dev.uppermoon.solintegrations.mixin;

import java.util.List;
import java.util.Set;
import org.slf4j.LoggerFactory;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Optional types must not be linked before their mods are discovered. */
public final class IntegrationMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        boolean enabled = SupportedVersions.allows(mixin, id -> {
            var info = LoadingModList.get().getModFileById(id);
            if (info == null) return null;
            return info.getMods().stream().filter(mod -> mod.getModId().equals(id))
                    .map(mod -> mod.getVersion().toString()).findFirst().orElse(null);
        });
        if (!enabled) LoggerFactory.getLogger("solintegrations").info("Integration inactive (optional mod absent or unsupported): {}", mixin);
        return enabled;
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}

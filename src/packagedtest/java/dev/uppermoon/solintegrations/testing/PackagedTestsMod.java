package dev.uppermoon.solintegrations.testing;

import dev.uppermoon.solintegrations.gametest.tfc.FoodIntegrationGameTests;
import dev.uppermoon.solintegrations.gametest.backpack.BackpackIntegrationGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.nio.file.Path;

/** Explicitly opt-in disposable-server harness; excluded from published addon. */
@Mod("solintegrationstests")
public final class PackagedTestsMod {
    private MultipleTestTracker tracker;
    private MinecraftServer server;
    private int ticks;
    private boolean finished;
    public PackagedTestsMod() {
        if (!Boolean.getBoolean("solintegrations.packagedTests"))
            throw new IllegalStateException("Test fixtures require -Dsolintegrations.packagedTests=true on a disposable server");
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }
    private void started(ServerStartedEvent event) {
        server = event.getServer();
        GameTestRegistry.register(FoodIntegrationGameTests.class);
        GameTestRegistry.register(BackpackIntegrationGameTests.class);
        var tests = GameTestRegistry.getAllTestFunctions();
        if (tests.size() != 23) throw new IllegalStateException("Expected 23 tests, got " + tests.size());
        System.out.println("SOL_PACKAGED_TEST_BEGIN count=" + tests.size());
        var infos = GameTestRunner.runTests(tests, new BlockPos(0, 200, 0), Rotation.NONE,
                server.overworld(), GameTestTicker.SINGLETON, 8);
        tracker = new MultipleTestTracker(infos);
        tracker.addFailureListener(info -> {
            System.err.println("SOL_PACKAGED_TEST_FAILURE " + info.getTestName());
            info.getError().printStackTrace();
        });
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || tracker == null || finished) return;
        GameTestTicker.SINGLETON.tick();
        if (!tracker.isDone() && ++ticks < 1200) return;
        finished = true;
        boolean passed = tracker.isDone() && !tracker.hasFailedRequired() && tracker.getTotalCount() == 23;
        String result = "{\"passed\":" + passed + ",\"total\":" + tracker.getTotalCount()
                + ",\"done\":" + tracker.getDoneCount() + ",\"failedRequired\":" + tracker.getFailedRequiredCount() + "}";
        try { Files.writeString(Path.of("packaged-test-result.json"), result + "\n"); }
        catch (Exception ex) { throw new RuntimeException(ex); }
        System.out.println("SOL_PACKAGED_TEST_END " + result);
        server.halt(false);
    }
}

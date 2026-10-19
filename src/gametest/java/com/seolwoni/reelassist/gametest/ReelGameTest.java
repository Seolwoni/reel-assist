package com.seolwoni.reelassist.gametest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.seolwoni.reelassist.config.ReelConfig;
import com.seolwoni.reelassist.fishing.FishingController;
import com.seolwoni.reelassist.fishing.FishingStats;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Five minutes of the fishing macro at an open-water pool: it must keep casting, reel in bites and catch things
 * without stopping by itself.
 */
public class ReelGameTest implements FabricClientGameTest {
	private final List<String> log = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealWorld.suite().equals("flat")) return;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			buildWorld(sp);
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender();

			context.runOnClient(mc -> {
				ReelConfig c = ReelConfig.get();
				c.modEnabled = true;
				c.storeEnabled = false;
				c.timerEnabled = false;
				mc.player.setYRot(-90f);
				mc.player.setXRot(35f);
				mc.player.getInventory().setSelectedSlot(0);
				c.macroEnabled = true;
				FishingController.onMacroToggled(true);
			});
			context.takeScreenshot("reel-00-start");

			int ticks = 0, shots = 1;
			final int limit = 20 * 60 * 5;
			boolean on = true;
			while (on && ticks < limit) {
				context.waitTicks(40);
				ticks += 40;
				final int t = ticks;
				log.add(context.computeOnClient(mc -> "t=" + t + " state=" + FishingController.stateLabel()
						+ " casts=" + FishingStats.session.casts + " catches=" + FishingStats.session.totalCatches()
						+ " hook=" + (mc.player.fishing != null)));
				on = context.computeOnClient(mc -> ReelConfig.get().macroEnabled);
				if (ticks % 1200 == 0 && shots < 10) context.takeScreenshot(String.format("reel-%02d-t%d", shots++, ticks));
			}
			context.takeScreenshot("reel-99-end");
			long catches = context.computeOnClient(mc -> FishingStats.session.totalCatches());
			long casts = context.computeOnClient(mc -> FishingStats.session.casts);
			log.add("after " + ticks + " ticks: casts=" + casts + " catches=" + catches + " macro on=" + on);
			if (!on) failures.add("macro turned itself off after " + ticks + " ticks");
			if (catches < 3) failures.add("only " + catches + " catches in 5 minutes");
			if (casts < catches) failures.add("fewer casts (" + casts + ") than catches (" + catches + ")");
			context.runOnClient(mc -> {
				ReelConfig.get().macroEnabled = false;
				FishingController.onMacroToggled(false);
			});
		} catch (Throwable t) {
			failures.add("exception: " + t);
			throw t;
		} finally {
			writeReport();
		}
		if (!failures.isEmpty()) throw new AssertionError("Reel Assist test failed: " + String.join("; ", failures));
	}

	private static void cmd(TestSingleplayerContext sp, String c) {
		sp.getServer().runCommand(c);
	}

	private void buildWorld(TestSingleplayerContext sp) {
		cmd(sp, "forceload add -8 -16 24 16");
		cmd(sp, "difficulty peaceful");
		cmd(sp, "time set noon");
		cmd(sp, "weather clear");
		cmd(sp, "fill -4 90 -10 16 99 10 minecraft:stone");
		cmd(sp, "fill -4 100 -10 16 115 10 minecraft:air");
		// Open water: 9 wide, 5 deep, nothing above it.
		cmd(sp, "fill 2 95 -5 12 99 5 minecraft:water");
		cmd(sp, "gamemode survival @a");
		cmd(sp, "clear @a");
		cmd(sp, "give @a minecraft:fishing_rod");
		cmd(sp, "give @a minecraft:bread 16");
		cmd(sp, "effect give @a minecraft:saturation infinite 0 true");
		cmd(sp, "tp @a 0.5 100 0.5 -90 35");
	}

	private void writeReport() {
		try {
			List<String> out = new ArrayList<>();
			out.add(failures.isEmpty() ? "RESULT: PASS" : "RESULT: FAIL (" + failures.size() + ")");
			for (String f : failures) out.add("  FAIL " + f);
			out.add("");
			out.addAll(log);
			Path p = FabricLoader.getInstance().getGameDir().resolve("reelassist-test-result.txt");
			Files.write(p, out, StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}
}

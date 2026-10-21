package com.seolwoni.reelassist.gametest;

import java.util.ArrayList;
import java.util.List;

import com.seolwoni.reelassist.config.ReelConfig;
import com.seolwoni.reelassist.fishing.FishingController;
import com.seolwoni.reelassist.fishing.FishingStats;
import com.seolwoni.reelassist.fishing.LootCategory;
import com.seolwoni.reelassist.fishing.OpenWater;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Real-map tests for Reel Assist: 15 minutes of fishing in a generated world (peaceful), as long as the other
 * mods' real-map tests run. One spot per world: real-ocean (seed 1), real-river (seed 2), real-swamp (seed 3).
 * The player stands on a small wooden pier facing the widest water. Checks that it keeps fishing, counts fish /
 * treasure / junk, and whether the bobber was in open water (needed for treasure).
 */
public class ReelRealTest implements FabricClientGameTest {
	private final List<String> log = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();
	private final RealWorld.Timing timing = new RealWorld.Timing();

	@Override
	public void runTest(ClientGameTestContext context) {
		String run = RealWorld.suite();
		// "-long": an hour on the same pier, storing in a chest beside it, rods fetched from it as they wear out;
		// at the end every catch is counted: in the chest or the inventory, or lost.
		boolean longRun = run.endsWith("-long");
		String suite = run.replace("-long", "");
		ResourceKey<Biome> biome;
		String seed;
		switch (suite) {
			case "real-ocean" -> {
				biome = Biomes.OCEAN;
				seed = RealWorld.SEED;
			}
			case "real-river" -> {
				biome = Biomes.RIVER;
				seed = RealWorld.SEED2;
			}
			case "real-swamp" -> {
				biome = Biomes.SWAMP;
				seed = RealWorld.SEED3;
			}
			default -> {
				return;
			}
		}
		try (TestSingleplayerContext sp = RealWorld.create(context, seed)) {
			sp.getConnection().waitForChunksRender();
			RealWorld.setup(sp);
			BlockPos b = RealWorld.findBiome(sp, biome, BlockPos.ZERO);
			if (b == null) {
				failures.add("biome not found: " + biome);
				throw new AssertionError("biome not found: " + biome);
			}
			RealWorld.goTo(context, sp, b.getX(), b.getZ(), 72);
			context.waitTicks(20);
			// A water column near the biome spot, and the direction with the most water in front of it.
			int[] spot = sp.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				int best = -1, bx = b.getX(), bz = b.getZ(), by = 0, bd = 0;
				for (int dx = -56; dx <= 56; dx += 2) {
					for (int dz = -56; dz <= 56; dz += 2) {
						int x = b.getX() + dx, z = b.getZ() + dz;
						// The water surface (under lily pads, vines or leaves, as in a swamp).
						int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
						while (top > level.getMinY() && !level.getBlockState(new BlockPos(x, top, z)).is(Blocks.WATER)
								&& level.getBlockState(new BlockPos(x, top, z)).getFluidState().isEmpty()
								&& !level.getBlockState(new BlockPos(x, top, z)).isSolidRender()) top--;
						if (!level.getBlockState(new BlockPos(x, top, z)).is(Blocks.WATER)) continue;
						// Shore next to it: a pier from the land is how a player would stand.
						for (int d = 0; d < 4; d++) {
							Direction dir = Direction.from2DDataValue(d);
							int water = 0;
							for (int k = 1; k <= 12; k++) {
								BlockPos q = new BlockPos(x, top, z).relative(dir, k);
								if (level.getBlockState(q).is(Blocks.WATER) && level.getBlockState(q.below()).is(Blocks.WATER)
										&& level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()) water++;
							}
							if (water > best) {
								best = water;
								bx = x;
								bz = z;
								by = top;
								bd = d;
							}
						}
					}
				}
				return new int[] {bx, by, bz, bd, best};
			});
			Direction dir = Direction.from2DDataValue(spot[3]);
			int px = spot[0], py = spot[1], pz = spot[2];
			// Pier: three planks at the water surface, the player on the middle one.
			BlockPos mid = new BlockPos(px, py, pz);
			for (int k = -1; k <= 1; k++) {
				BlockPos c = mid.relative(dir.getClockWise(), k);
				RealWorld.cmd(sp, "setblock " + c.getX() + " " + c.getY() + " " + c.getZ() + " minecraft:oak_planks");
				RealWorld.cmd(sp, "fill " + c.getX() + " " + (c.getY() + 1) + " " + c.getZ() + " " + c.getX() + " " + (c.getY() + 3) + " " + c.getZ() + " minecraft:air");
			}
			float yaw = dir.toYRot();
			RealWorld.cmd(sp, "tp @a " + (px + 0.5) + " " + (py + 1) + " " + (pz + 0.5) + " " + yaw + " 25");
			RealWorld.cmd(sp, "give @a minecraft:fishing_rod");
			RealWorld.cmd(sp, "give @a minecraft:bread 16");
			final BlockPos chestPos = mid.relative(dir.getClockWise(), 1).above();
			if (longRun) {
				RealWorld.cmd(sp, "setblock " + chestPos.getX() + " " + chestPos.getY() + " " + chestPos.getZ() + " minecraft:chest");
				for (int k = 0; k < 4; k++)
					RealWorld.cmd(sp, "item replace block " + chestPos.getX() + " " + chestPos.getY() + " " + chestPos.getZ() + " container." + k + " with minecraft:fishing_rod");
			}
			RealWorld.cmd(sp, "effect give @a minecraft:saturation infinite 0 true");
			log.add(biome + " (seed " + seed + "): pier at " + px + ", " + (py + 1) + ", " + pz + " facing " + dir + " (" + spot[4] + " water blocks ahead)");
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender();

			context.runOnClient(mc -> {
				ReelConfig c = ReelConfig.get();
				c.modEnabled = true;
				c.storeEnabled = longRun;
				c.timerEnabled = false;
				if (longRun) {
					c.chestSet = true;
					c.chestX = chestPos.getX();
					c.chestY = chestPos.getY();
					c.chestZ = chestPos.getZ();
					c.storeMode = 0;
					c.storeOther = true;
					c.discardEnabled = false; // every catch has to be found again at the end
					c.eatRawFish = false;
				}
				mc.player.getInventory().setSelectedSlot(0);
				c.macroEnabled = true;
				FishingController.onMacroToggled(true);
			});
			context.takeScreenshot(suite + "-00-start");

			int ticks = 0, shots = 1, openSamples = 0, samples = 0;
			final int limit = 20 * 60 * (longRun ? 60 : 15);
			boolean on = true;
			while (on && ticks < limit) {
				long t0 = System.nanoTime();
				context.waitTicks(40);
				timing.add(System.nanoTime() - t0);
				ticks += 40;
				final int t = ticks;
				Boolean open = context.computeOnClient(mc -> OpenWater.bobber());
				if (open != null) {
					samples++;
					if (open) openSamples++;
				}
				log.add(context.computeOnClient(mc -> "t=" + t + " state=" + FishingController.stateLabel()
						+ " casts=" + FishingStats.session.casts + " catches=" + FishingStats.session.totalCatches()
						+ " open=" + OpenWater.bobber()));
				on = context.computeOnClient(mc -> ReelConfig.get().macroEnabled);
				if (ticks % (longRun ? 6000 : 600) == 0) context.takeScreenshot(String.format("%s-%03d-s%d", suite, shots++, ticks / 20));
			}
			context.takeScreenshot(suite + "-99-end");
			final boolean wasOn = on;
			final int playedTicks = ticks;
			context.runOnClient(mc -> {
				FishingStats st = FishingStats.session;
				log.add("after " + playedTicks / 20 / 60 + " game minutes: casts=" + st.casts + " catches=" + st.totalCatches() + " macro on=" + wasOn);
				log.add("fish=" + st.categoryCount(LootCategory.FISH) + " treasure=" + st.categoryCount(LootCategory.TREASURE)
						+ " junk=" + st.categoryCount(LootCategory.JUNK) + " other=" + st.categoryCount(LootCategory.OTHER));
				for (FishingStats.Entry e : st.entries.values()) log.add("  " + e.category + " " + e.name + " x" + e.count);
				if (!wasOn) failures.add("macro turned itself off: " + FishingController.lastStop);
				if (st.totalCatches() < (longRun ? 60 : 15)) failures.add("only " + st.totalCatches() + " catches in " + (longRun ? 60 : 15) + " minutes");
				if (st.casts < st.totalCatches()) failures.add("fewer casts than catches");
				ReelConfig.get().macroEnabled = false;
				FishingController.onMacroToggled(false);
			});
			if (longRun) {
				// Every catch accounted for: items now in the chest and the inventory, minus what we started with
				// (bread, rods), against the catches counted (fishing rods caught as treasure are left out on both sides).
				long caughtRods = context.computeOnClient(mc -> FishingStats.session.entries.values().stream()
						.filter(e -> e.name.toLowerCase().contains("rod") || e.name.contains("낚싯대")).mapToLong(e -> e.count).sum());
				long catches = context.computeOnClient(mc -> FishingStats.session.totalCatches());
				int[] held = context.computeOnClient(mc -> {
					int n = 0, rods = 0;
					for (int i = 0; i < 36; i++) {
						var st = mc.player.getInventory().getItem(i);
						if (st.isEmpty() || st.is(net.minecraft.world.item.Items.BREAD)) continue;
						if (st.is(net.minecraft.world.item.Items.FISHING_ROD)) rods++;
						else n += st.getCount();
					}
					var off = mc.player.getOffhandItem();
					if (!off.isEmpty() && !off.is(net.minecraft.world.item.Items.FISHING_ROD)) n += off.getCount();
					return new int[] {n, rods};
				});
				int[] boxed = sp.getServer().computeOnServer(server -> {
					int n = 0, rods = 0, trips = 0;
					if (server.overworld().getBlockEntity(chestPos) instanceof net.minecraft.world.Container ct) {
						for (int i = 0; i < ct.getContainerSize(); i++) {
							var st = ct.getItem(i);
							if (st.isEmpty()) continue;
							if (st.is(net.minecraft.world.item.Items.FISHING_ROD)) rods++;
							else n += st.getCount();
						}
					}
					return new int[] {n, rods};
				});
				long found = held[0] + boxed[0];
				long expected = catches - caughtRods;
				long lost = Math.max(0, expected - found);
				log.add("LOSS: catches " + catches + " (rods among them " + caughtRods + "), found " + found + " (chest " + boxed[0] + ", inventory " + held[0] + "), missing " + lost
						+ " | rods: inventory " + held[1] + ", chest " + boxed[1]);
				log.add("items on the ground near the pier: " + sp.getServer().computeOnServer(server -> server.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
						new net.minecraft.world.phys.AABB(chestPos).inflate(12)).size()));
				if (lost > 0) failures.add(lost + " of " + expected + " catches lost");
				if (boxed[0] == 0) failures.add("nothing was stored in the chest in an hour");
			}
			log.add("bobber in open water in " + openSamples + " of " + samples + " checks"
					+ (samples > 0 ? String.format(" (%.0f%%)", openSamples * 100.0 / samples) : ""));
			log.add(timing.summary() + ", server tick " + String.format("%.1f", RealWorld.mspt(sp)) + " ms");
		} catch (Throwable t) {
			failures.add("exception: " + t);
			throw t;
		} finally {
			RealWorld.writeReport("reelassist-" + suite, failures, log);
		}
		if (!failures.isEmpty()) throw new AssertionError("Reel Assist " + suite + " failed: " + String.join("; ", failures));
	}
}

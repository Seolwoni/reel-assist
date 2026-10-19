package com.seolwoni.reelassist.fishing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Treasure (enchanted books, name tags, saddles, ...) only comes up when the bobber is in "open water":
 * the 5x5 area around it, from one block below to two above, is water in the lower layers and air (or lily
 * pads) above, with nothing else in it. This checks the same thing the game does, from what the client sees,
 * so the HUD can say whether this spot can give treasure.
 */
public final class OpenWater {
	private enum Layer { ABOVE, INSIDE, INVALID }

	private static long lastCheck;
	private static Boolean last;

	private OpenWater() {
	}

	/** Whether the bobber sits in open water right now (null when there is no bobber in the water). Cached for a second. */
	public static Boolean bobber() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return null;
		FishingHook hook = mc.player.fishing;
		if (hook == null || !hook.isInWater()) return last = null;
		long now = mc.level.getGameTime();
		if (last != null && now - lastCheck < 20) return last;
		lastCheck = now;
		return last = check(mc.level, hook.blockPosition());
	}

	public static boolean check(Level level, BlockPos pos) {
		Layer prev = Layer.INVALID;
		for (int dy = -1; dy <= 2; dy++) {
			Layer t = layer(level, pos.offset(-2, dy, -2), pos.offset(2, dy, 2));
			switch (t) {
				case ABOVE -> {
					if (prev == Layer.INVALID) return false;
				}
				case INSIDE -> {
					if (prev == Layer.ABOVE) return false;
				}
				case INVALID -> {
					return false;
				}
			}
			prev = t;
		}
		return true;
	}

	private static Layer layer(Level level, BlockPos a, BlockPos b) {
		Layer found = null;
		for (BlockPos p : BlockPos.betweenClosed(a, b)) {
			Layer t = block(level, p);
			if (found == null) found = t;
			else if (found != t) return Layer.INVALID;
		}
		return found == null ? Layer.INVALID : found;
	}

	private static Layer block(Level level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		if (s.isAir() || s.is(Blocks.LILY_PAD)) return Layer.ABOVE;
		var fluid = s.getFluidState();
		if (fluid.is(FluidTags.WATER) && fluid.isSource() && s.getCollisionShape(level, p).isEmpty()) return Layer.INSIDE;
		return Layer.INVALID;
	}
}

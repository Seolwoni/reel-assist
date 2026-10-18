package com.seolwoni.reelassist.fishing;

import com.seolwoni.reelassist.config.ReelConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;

/**
 * Bobber highlight (glowing outline whose colour shows the fishing state) and
 * fish-approach detection from the server's FISHING wake particles.
 */
public final class BobberFx {
	public static final int COLOR_IDLE = 0x55FFFF;
	public static final int COLOR_APPROACH = 0xFFFF55;
	public static final int COLOR_BITE = 0xFF5555;

	/** Wake particles further than this from our bobber belong to someone else. */
	private static final double APPROACH_RADIUS = 10.0;
	/** The approach state lasts this many ticks after the last wake particle. */
	private static final int APPROACH_HOLD = 12;

	private static int lastWakeTick = -1000;
	private static double wakeDistance = -1;
	private static boolean biting;
	private static int clientTicks;

	private BobberFx() {
	}

	/** Our own, live fishing hook, or null. */
	public static FishingHook ownHook() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return null;
		FishingHook h = p.fishing;
		return h == null || h.isRemoved() ? null : h;
	}

	public static boolean isOwnHook(Entity e) {
		return e instanceof FishingHook && e == ownHook();
	}

	public static boolean shouldGlow(Entity e) {
		return ReelConfig.get().bobberGlow && isOwnHook(e);
	}

	public static int color() {
		if (biting) return COLOR_BITE;
		if (isApproaching()) return COLOR_APPROACH;
		return COLOR_IDLE;
	}

	public static boolean isApproaching() {
		return !biting && clientTicks - lastWakeTick <= APPROACH_HOLD && ownHook() != null;
	}

	public static double approachDistance() {
		return wakeDistance;
	}

	/** Called from the controller every client tick. */
	public static void tick(boolean hookAlive, boolean bitingNow) {
		clientTicks++;
		biting = hookAlive && bitingNow;
		if (!hookAlive) {
			lastWakeTick = -1000;
			wakeDistance = -1;
		}
	}

	/** Called on the main thread for every particle packet. */
	public static void onParticle(ClientboundLevelParticlesPacket packet) {
		if (packet.getParticle().getType() != ParticleTypes.FISHING) return;
		FishingHook hook = ownHook();
		if (hook == null || biting) return;
		double dx = packet.getX() - hook.getX();
		double dz = packet.getZ() - hook.getZ();
		double dy = packet.getY() - hook.getY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal > APPROACH_RADIUS || Math.abs(dy) > 2.5) return;
		// Particles right on the bobber are the bite splash itself, not the approach.
		if (horizontal < 0.25) return;
		lastWakeTick = clientTicks;
		wakeDistance = horizontal;
	}
}

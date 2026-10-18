package com.seolwoni.reelassist.fishing;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.seolwoni.reelassist.ReelAssistClient;
import com.seolwoni.reelassist.config.DiscardEntry;
import com.seolwoni.reelassist.config.ReelConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The core of the mod: bite detection, alerts, catch counting, auto-fishing,
 * rod selection, look movement and auto-discard. Driven once per client tick.
 */
public final class FishingController {
	public static final String[] ROD_MODE_NAMES = {
			"손에 있는 낚싯대",
			"손에 있는 낚싯대 (파괴보호)",
			"핫바 낚싯대 전부",
			"핫바 낚싯대 전부 (파괴보호)"
	};

	/** The game never lets a bite last less than 20 ticks; keep 3 ticks spare for lag. */
	private static final int SAFE_REEL_TICKS = 17;
	private static final int CATCH_WATCH_TICKS = 40;

	private enum State { IDLE, CAST_WAIT, FISHING, REEL_WAIT }

	private static final Random RANDOM = new Random();
	private static Field bitingField;
	private static boolean bitingFieldLooked;

	private static State state = State.IDLE;
	private static int timer;
	private static int ticksSinceCast;

	// Hook / bite tracking (works for manual fishing too)
	private static boolean hadHook;
	private static boolean wasBiting;
	private static int ticksSinceBite = 1000;
	private static Map<String, Integer> biteSnapshot;
	private static Map<String, Integer> watchSnapshot;
	private static int watchTicks;

	// Look movement
	private static boolean anchored;
	private static float anchorYaw, anchorPitch;
	private static float offYaw, offPitch;
	private static float fromYaw, fromPitch, toYaw, toPitch;
	private static int moveTick, moveLen;
	private static int nextMoveIn;

	private static int discardCooldown;

	private FishingController() {
	}

	// ------------------------------------------------------------------ tick

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null) {
			resetRuntime();
			return;
		}
		if (mc.isPaused()) return;

		ReelConfig cfg = ReelConfig.get();
		FishingHook hook = player.fishing;
		boolean hookAlive = hook != null && !hook.isRemoved();
		boolean biting = hookAlive && isBiting(hook);

		trackHookAndBite(mc, player, hookAlive, biting, cfg);
		BobberFx.tick(hookAlive, biting);
		approachAlert(cfg);
		trackCatch(player);

		if (hookAlive || cfg.macroEnabled) FishingStats.tickFishing();

		if (cfg.macroEnabled && cfg.stopWhenRodRemoved && rodPutAway(player)) {
			stopMacro("매크로 꺼짐", "낚싯대를 내려놨어요", false);
		}
		if (cfg.macroEnabled) {
			if (ChestStorage.active()) {
				ChestStorage.tick(mc, player, hookAlive);
			} else {
				runMacro(mc, player, hookAlive, biting, cfg);
				lookMovement(player, cfg);
				autoDiscard(mc, player, cfg);
			}
		} else {
			state = State.IDLE;
			anchored = false;
			ChestStorage.cancel();
		}
		rememberHand(player);
	}

	// ------------------------------------------------------------------ rod put away

	private static int lastSlot = -1;
	private static boolean lastWasRod;
	private static int lastRemaining;

	private static void rememberHand(LocalPlayer player) {
		ItemStack hand = player.getMainHandItem();
		lastSlot = player.getInventory().getSelectedSlot();
		lastWasRod = isRod(hand);
		lastRemaining = lastWasRod ? remaining(hand) : 0;
	}

	/**
	 * True when the player switched away from the rod or took it out of their hand.
	 * Slot changes made by the macro itself happen after this check and are already
	 * remembered, and a rod that just broke from use does not count.
	 */
	private static boolean rodPutAway(LocalPlayer player) {
		if (lastSlot == -1) return false;
		if (isRod(player.getMainHandItem())) return false;
		int slot = player.getInventory().getSelectedSlot();
		if (slot != lastSlot) return true;
		boolean broke = lastWasRod && lastRemaining <= 2;
		return lastWasRod && !broke;
	}

	private static void resetRuntime() {
		state = State.IDLE;
		lastSlot = -1;
		hadHook = false;
		wasBiting = false;
		watchSnapshot = null;
		biteSnapshot = null;
		anchored = false;
	}

	// ------------------------------------------------------------------ bite / catch tracking

	private static boolean isBiting(FishingHook hook) {
		if (!bitingFieldLooked) {
			bitingFieldLooked = true;
			try {
				bitingField = FishingHook.class.getDeclaredField("biting");
				bitingField.setAccessible(true);
			} catch (Exception e) {
				ReelAssistClient.LOGGER.warn("FishingHook.biting not found, using movement fallback");
			}
		}
		if (bitingField != null) {
			try {
				return bitingField.getBoolean(hook);
			} catch (Exception ignored) {
			}
		}
		// Fallback: the bobber is yanked down sharply when a fish bites.
		return hook.isInWater() && hook.getDeltaMovement().y < -0.08;
	}

	private static void trackHookAndBite(Minecraft mc, LocalPlayer player, boolean hookAlive, boolean biting, ReelConfig cfg) {
		if (hookAlive && !hadHook) {
			FishingStats.recordCast();
			if (!cfg.macroEnabled && cfg.autoStartOnCast) {
				cfg.macroEnabled = true;
				ReelConfig.save();
				state = State.FISHING;
				anchored = false;
				Alerts.actionBar("매크로 자동 시작");
				Alerts.click();
			}
			ticksSinceCast = 0;
		}
		if (hookAlive) ticksSinceCast++;

		if (biting && !wasBiting) {
			ticksSinceBite = 0;
			if (cfg.biteTitle) Alerts.title("입질!", "", 0xFFFFFF55);
			if (cfg.biteSound) Alerts.ding();
		}
		if (biting) {
			ticksSinceBite = 0;
			biteSnapshot = snapshot(player.getInventory());
		} else {
			ticksSinceBite++;
		}

		// The line came out of the water right after (or during) a bite -> watch for the catch.
		if (hadHook && !hookAlive && ticksSinceBite <= 2 && biteSnapshot != null) {
			watchSnapshot = biteSnapshot;
			watchTicks = CATCH_WATCH_TICKS;
		}
		if (!hookAlive) biteSnapshot = null;

		hadHook = hookAlive;
		wasBiting = biting;
	}

	private static void trackCatch(LocalPlayer player) {
		if (watchSnapshot == null) return;
		Inventory inv = player.getInventory();
		Map<String, Integer> now = snapshot(inv);
		for (Map.Entry<String, Integer> e : now.entrySet()) {
			int before = watchSnapshot.getOrDefault(e.getKey(), 0);
			if (e.getValue() > before) {
				ItemStack sample = findStack(inv, e.getKey());
				if (sample != null) {
					LootCategory cat = LootCategory.of(sample);
					FishingStats.recordCatch(cat, DiscardEntry.itemId(sample),
							sample.getHoverName().getString(), e.getValue() - before);
				}
				watchSnapshot = null;
				return;
			}
		}
		if (--watchTicks <= 0) watchSnapshot = null;
	}

	private static String key(ItemStack s) {
		return DiscardEntry.itemId(s) + "|" + s.getHoverName().getString() + "|" + s.isEnchanted();
	}

	private static Map<String, Integer> snapshot(Inventory inv) {
		Map<String, Integer> map = new HashMap<>();
		for (int i = 0; i < 36; i++) add(map, inv.getItem(i));
		add(map, inv.getItem(40));
		return map;
	}

	private static void add(Map<String, Integer> map, ItemStack s) {
		if (s.isEmpty()) return;
		map.merge(key(s), s.getCount(), Integer::sum);
	}

	private static ItemStack findStack(Inventory inv, String key) {
		for (int i = 0; i < 41; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && key(s).equals(key)) return s;
		}
		return null;
	}

	private static boolean approachAnnounced;
	private static int approachMsgCooldown;

	private static void approachAlert(ReelConfig cfg) {
		if (!BobberFx.isApproaching()) {
			if (BobberFx.ownHook() == null || wasBiting) approachAnnounced = false;
			return;
		}
		if (!approachAnnounced) {
			approachAnnounced = true;
			approachMsgCooldown = 0;
			if (cfg.approachSound) Alerts.approach();
		}
		if (cfg.approachAlert && --approachMsgCooldown <= 0) {
			approachMsgCooldown = 4;
			Alerts.actionBar(String.format("물고기가 다가오는 중... %.1f칸", BobberFx.approachDistance()), 0xFFFFFF55);
		}
	}

	// ------------------------------------------------------------------ macro

	private static boolean screenBlocks(Minecraft mc, ReelConfig cfg) {
		Screen screen = mc.gui.screen();
		return screen != null && !cfg.workInScreens;
	}

	private static void runMacro(Minecraft mc, LocalPlayer player, boolean hookAlive, boolean biting, ReelConfig cfg) {
		if (screenBlocks(mc, cfg)) return;

		switch (state) {
			case IDLE -> {
				if (hookAlive) {
					state = State.FISHING;
				} else {
					timer = delay(cfg.castDelayMin, cfg.castDelayMax);
					state = State.CAST_WAIT;
				}
			}
			case CAST_WAIT -> {
				if (hookAlive) {
					state = State.FISHING;
					return;
				}
				if (--timer > 0) return;
				if (player.getInventory().getFreeSlot() == -1) {
					if (cfg.storeEnabled && ChestStorage.hasChest()) {
						ChestStorage.start(mc, player);
					} else {
						stopMacro("인벤토리 가득 참!", "매크로를 멈췄어요", cfg.fullInvAlert);
					}
					return;
				}
				if (!prepareRod(player, cfg)) return;
				use(mc, player);
				ticksSinceCast = 0;
				state = State.FISHING;
			}
			case FISHING -> {
				if (!hookAlive) {
					// Hook never appeared or vanished (broken line, hooked an entity etc.)
					if (ticksSinceCast > 20) state = State.IDLE;
					ticksSinceCast++;
					return;
				}
				if (biting) {
					int max = Math.min(cfg.reelDelayMax, SAFE_REEL_TICKS);
					int min = Math.min(cfg.reelDelayMin, max);
					timer = delay(min, max);
					state = State.REEL_WAIT;
					if (timer <= 0) reel(mc, player);
					return;
				}
				if (cfg.recastTimeoutSec > 0 && ticksSinceCast > cfg.recastTimeoutSec * 20) {
					use(mc, player);
					state = State.IDLE;
				}
			}
			case REEL_WAIT -> {
				if (!hookAlive) {
					state = State.IDLE;
					return;
				}
				if (--timer <= 0) reel(mc, player);
			}
		}
	}

	private static void reel(Minecraft mc, LocalPlayer player) {
		// Make sure the catch is watched even if the bite flag already dropped this tick.
		if (biteSnapshot == null) biteSnapshot = snapshot(player.getInventory());
		ticksSinceBite = 0;
		use(mc, player);
		state = State.IDLE;
	}

	private static void use(Minecraft mc, LocalPlayer player) {
		mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
		player.swing(InteractionHand.MAIN_HAND);
	}

	/**
	 * A random delay in [min, max]. In "human" mode most values cluster in the lower
	 * third of the range with an occasional slow reaction, instead of a flat spread.
	 */
	public static int delay(int min, int max) {
		if (max <= min) return min;
		if (!ReelConfig.get().humanDelay) return rand(min, max);
		double span = max - min;
		double v;
		if (RANDOM.nextDouble() < 0.08) {
			v = min + span * (0.6 + 0.4 * RANDOM.nextDouble());
		} else {
			v = min + span * 0.3 + RANDOM.nextGaussian() * span * 0.15;
		}
		return (int) Math.max(min, Math.min(max, Math.round(v)));
	}

	/** Re-anchor the look movement and restart the cast cycle (after a storage trip). */
	public static void resetLook() {
		anchored = false;
		state = State.IDLE;
	}

	private static int rand(int min, int max) {
		if (max <= min) return min;
		return min + RANDOM.nextInt(max - min + 1);
	}

	public static void stopMacro(String title, String subtitle, boolean alert) {
		ReelConfig cfg = ReelConfig.get();
		cfg.macroEnabled = false;
		ReelConfig.save();
		ChestStorage.cancel();
		state = State.IDLE;
		anchored = false;
		if (alert) {
			Alerts.title(title, subtitle, 0xFFFF5555);
			Alerts.warning();
		} else {
			Alerts.actionBar(title + " - " + subtitle);
		}
	}

	public static void onMacroToggled(boolean enabled) {
		state = State.IDLE;
		anchored = false;
	}

	// ------------------------------------------------------------------ rods

	public static int remaining(ItemStack rod) {
		if (!rod.isDamageableItem()) return Integer.MAX_VALUE;
		return rod.getMaxDamage() - rod.getDamageValue();
	}

	private static boolean isRod(ItemStack s) {
		return !s.isEmpty() && s.is(Items.FISHING_ROD);
	}

	/** Selects the rod to use according to the rod mode. Returns false (and stops) if none is usable. */
	private static boolean prepareRod(LocalPlayer player, ReelConfig cfg) {
		Inventory inv = player.getInventory();
		ItemStack hand = player.getMainHandItem();
		boolean protect = cfg.rodMode == 1 || cfg.rodMode == 3;
		int limit = protect ? cfg.protectDurability : 0;

		if (cfg.rodMode <= 1) {
			if (!isRod(hand)) {
				stopMacro("낚싯대가 없어요", "손에 낚싯대를 들어주세요", true);
				return false;
			}
			if (protect && remaining(hand) <= limit) {
				stopMacro("파괴보호", "낚싯대 내구도가 " + limit + " 이하예요", true);
				return false;
			}
			return true;
		}

		// Hotbar modes: always use the eligible rod with the LEAST durability left.
		int best = -1;
		int bestRemaining = Integer.MAX_VALUE;
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			if (!isRod(s)) continue;
			int r = remaining(s);
			if (protect && r <= limit) continue;
			if (best == -1 || r < bestRemaining) {
				best = i;
				bestRemaining = r;
			}
		}
		if (best == -1) {
			stopMacro(protect ? "파괴보호" : "낚싯대 소진",
					protect ? "쓸 수 있는 낚싯대가 없어요" : "핫바에 낚싯대가 없어요", true);
			return false;
		}
		if (inv.getSelectedSlot() != best) {
			inv.setSelectedSlot(best);
		}
		return true;
	}

	public static void cycleRodMode() {
		ReelConfig cfg = ReelConfig.get();
		cfg.rodMode = (cfg.rodMode + 1) % 4;
		ReelConfig.save();
		Alerts.actionBar("낚싯대 모드: " + ROD_MODE_NAMES[cfg.rodMode]);
		Alerts.click();
	}

	// ------------------------------------------------------------------ look movement

	private static void lookMovement(LocalPlayer player, ReelConfig cfg) {
		if (!cfg.lookJitter) {
			anchored = false;
			return;
		}
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		if (!anchored) {
			anchored = true;
			anchorYaw = yaw;
			anchorPitch = pitch;
			offYaw = offPitch = 0;
			moveLen = 0;
			nextMoveIn = rand(cfg.jitterIntervalMin * 20, cfg.jitterIntervalMax * 20);
			return;
		}
		// If the player moved the camera themselves, follow their new aim.
		float expectedYaw = anchorYaw + offYaw;
		float expectedPitch = anchorPitch + offPitch;
		if (Math.abs(yaw - expectedYaw) > 0.05f || Math.abs(pitch - expectedPitch) > 0.05f) {
			anchorYaw = yaw - offYaw;
			anchorPitch = pitch - offPitch;
		}

		if (moveLen > 0) {
			moveTick++;
			float t = Math.min(1f, moveTick / (float) moveLen);
			float ease = t * t * (3 - 2 * t);
			offYaw = fromYaw + (toYaw - fromYaw) * ease;
			offPitch = fromPitch + (toPitch - fromPitch) * ease;
			player.setYRot(anchorYaw + offYaw);
			player.setXRot(clampPitch(anchorPitch + offPitch));
			if (t >= 1f) moveLen = 0;
			return;
		}

		// Don't start a movement right before casting.
		if (state == State.CAST_WAIT && timer < 12) return;
		if (--nextMoveIn > 0) return;

		float d = cfg.jitterDegrees;
		fromYaw = offYaw;
		fromPitch = offPitch;
		toYaw = (RANDOM.nextFloat() * 2 - 1) * d;
		toPitch = (RANDOM.nextFloat() * 2 - 1) * d * 0.6f;
		moveTick = 0;
		moveLen = rand(6, 14);
		nextMoveIn = rand(cfg.jitterIntervalMin * 20, cfg.jitterIntervalMax * 20);
	}

	private static float clampPitch(float p) {
		return Math.max(-90f, Math.min(90f, p));
	}

	// ------------------------------------------------------------------ discard

	private static void autoDiscard(Minecraft mc, LocalPlayer player, ReelConfig cfg) {
		if (!cfg.discardEnabled || cfg.discardEntries.isEmpty()) return;
		if (discardCooldown > 0) {
			discardCooldown--;
			return;
		}
		Screen screen = mc.gui.screen();
		// Only throw from the player's own inventory menu.
		if (screen != null && !(screen instanceof InventoryScreen)) return;

		Inventory inv = player.getInventory();
		int selected = inv.getSelectedSlot();
		for (int i = 0; i < 36; i++) {
			if (i == selected) continue;
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || isRod(s)) continue;
			for (DiscardEntry e : cfg.discardEntries) {
				if (e.matches(s)) {
					int menuSlot = i < 9 ? 36 + i : i;
					mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot, 1,
							ContainerInput.THROW, player);
					discardCooldown = 3;
					return;
				}
			}
		}
	}

	// ------------------------------------------------------------------ HUD info

	public static String stateLabel() {
		if (!ReelConfig.get().macroEnabled) return "꺼짐";
		if (ChestStorage.active()) return ChestStorage.label();
		return switch (state) {
			case IDLE, CAST_WAIT -> "던지는 중";
			case FISHING -> "기다리는 중";
			case REEL_WAIT -> "당기는 중";
		};
	}
}

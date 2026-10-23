package com.seolwoni.reelassist.fishing;

import java.util.ArrayList;
import java.util.List;

import com.seolwoni.reelassist.config.ReelConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Empties the catch into a chest the player marked, without walking:
 * reel in -> turn to the chest -> open -> shift-click the loot -> close -> turn back.
 */
public final class ChestStorage {
	private enum Step { NONE, REEL_IN, TURN_TO, OPEN, WAIT_OPEN, MOVE, CLOSE, TURN_BACK }

	private static final double REACH = 4.4;

	private static Step step = Step.NONE;
	private static int timer;
	private static int waitTicks;
	private static float savedYaw, savedPitch;
	private static float fromYaw, fromPitch, toYaw, toPitch;
	private static int turnTick, turnLen;
	private static int moved;
	private static int taken;
	private static boolean chestFull;
	private static int phase;
	private static Sorter chestSorter;

	private ChestStorage() {
	}

	public static boolean active() {
		return step != Step.NONE;
	}

	public static void cancel() {
		step = Step.NONE;
	}

	public static boolean hasChest() {
		return ReelConfig.get().chestSet;
	}

	// ------------------------------------------------------------------ marking

	/** Called when the "mark chest" key is pressed in the world. */
	public static void markLookedAt(Minecraft mc) {
		ReelConfig cfg = ReelConfig.get();
		HitResult hit = mc.hitResult;
		if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK || mc.level == null) {
			Alerts.actionBar(com.seolwoni.assistcore.i18n.Tr.t("상자를 바라보고 눌러주세요"), 0xFFFF5555);
			return;
		}
		BlockPos pos = bhr.getBlockPos();
		BlockEntity be = mc.level.getBlockEntity(pos);
		if (!(be instanceof Container)) {
			Alerts.actionBar(com.seolwoni.assistcore.i18n.Tr.t("상자나 통 같은 보관함이 아니에요"), 0xFFFF5555);
			return;
		}
		if (cfg.chestSet && cfg.chestX == pos.getX() && cfg.chestY == pos.getY() && cfg.chestZ == pos.getZ()) {
			cfg.chestSet = false;
			ReelConfig.save();
			Alerts.actionBar(com.seolwoni.assistcore.i18n.Tr.t("보관 상자 지정 해제"));
			Alerts.click();
			return;
		}
		cfg.chestSet = true;
		cfg.chestX = pos.getX();
		cfg.chestY = pos.getY();
		cfg.chestZ = pos.getZ();
		ReelConfig.save();
		Alerts.actionBar(com.seolwoni.assistcore.i18n.Tr.t("보관 상자 지정: ") + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), 0xFF55FF55);
		Alerts.click();
	}

	private static BlockPos chestPos() {
		ReelConfig cfg = ReelConfig.get();
		return new BlockPos(cfg.chestX, cfg.chestY, cfg.chestZ);
	}

	// ------------------------------------------------------------------ run

	/** Starts a storage trip. Returns false (with a reason shown) if it cannot run. */
	public static boolean start(Minecraft mc, LocalPlayer player) {
		if (!hasChest()) return false;
		BlockPos pos = chestPos();
		if (mc.level == null || !(mc.level.getBlockEntity(pos) instanceof Container)) {
			FishingController.stopMacro(com.seolwoni.assistcore.i18n.Tr.t("보관 상자가 없어요"), com.seolwoni.assistcore.i18n.Tr.t("상자를 다시 지정해주세요"), true);
			return false;
		}
		if (player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > REACH) {
			FishingController.stopMacro(com.seolwoni.assistcore.i18n.Tr.t("보관 상자가 멀어요"), com.seolwoni.assistcore.i18n.Tr.t("손이 닿는 거리에 두세요"), true);
			return false;
		}
		savedYaw = player.getYRot();
		savedPitch = player.getXRot();
		moved = 0;
		taken = 0;
		chestFull = false;
		phase = 0;
		chestSorter = null;
		step = Step.REEL_IN;
		timer = FishingController.delay(2, 8);
		return true;
	}

	public static void tick(Minecraft mc, LocalPlayer player, boolean hookAlive) {
		switch (step) {
			case NONE -> {
			}
			case REEL_IN -> {
				if (hookAlive) {
					if (--timer > 0) return;
					mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
					player.swing(InteractionHand.MAIN_HAND);
					timer = FishingController.delay(3, 8);
					return;
				}
				if (--timer > 0) return;
				Vec3 target = Vec3.atCenterOf(chestPos());
				beginTurn(player, yawTo(player, target), pitchTo(player, target));
				step = Step.TURN_TO;
			}
			case TURN_TO -> {
				if (turn(player)) {
					timer = FishingController.delay(2, 6);
					step = Step.OPEN;
				}
			}
			case OPEN -> {
				if (--timer > 0) return;
				BlockPos pos = chestPos();
				Vec3 center = Vec3.atCenterOf(pos);
				Direction face = faceToward(player.getEyePosition(), center);
				Vec3 hitVec = center.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
				mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hitVec, face, pos, false));
				player.swing(InteractionHand.MAIN_HAND);
				waitTicks = 40;
				step = Step.WAIT_OPEN;
			}
			case WAIT_OPEN -> {
				if (openContainer(mc) != null) {
					timer = FishingController.delay(3, 8);
					step = Step.MOVE;
					return;
				}
				if (--waitTicks <= 0) fail(com.seolwoni.assistcore.i18n.Tr.t("상자를 열지 못했어요"), com.seolwoni.assistcore.i18n.Tr.t("막혀 있는지 확인해주세요"));
			}
			case MOVE -> {
				AbstractContainerScreen<?> screen = openContainer(mc);
				if (screen == null) {
					fail(com.seolwoni.assistcore.i18n.Tr.t("상자가 닫혔어요"), com.seolwoni.assistcore.i18n.Tr.t("보관을 멈췄어요"));
					return;
				}
				if (--timer > 0) return;
				AbstractContainerMenu menu = screen.getMenu();
				boolean acted = switch (phase) {
					case 0 -> moveOne(mc, player, menu);
					case 1 -> takeOne(mc, player, menu);
					case 2 -> sortChest(mc, player, menu);
					default -> false;
				};
				if (!acted) {
					phase++;
					if (phase > 2) {
						timer = FishingController.delay(3, 8);
						step = Step.CLOSE;
					}
					return;
				}
				timer = phase == 2 ? Math.max(1, FishingController.delay(2, 8) / 2) : FishingController.delay(1, 4);
			}
			case CLOSE -> {
				if (--timer > 0) return;
				player.closeContainer();
				beginTurn(player, savedYaw, savedPitch);
				step = Step.TURN_BACK;
			}
			case TURN_BACK -> {
				if (!turn(player)) return;
				player.setYRot(savedYaw);
				player.setXRot(savedPitch);
				step = Step.NONE;
				FishingController.resetLook();
				if (chestFull) {
					FishingController.stopMacro(com.seolwoni.assistcore.i18n.Tr.t("보관 상자가 가득 찼어요"), com.seolwoni.assistcore.i18n.Tr.t("상자를 비워주세요"), true);
				} else if (player.getInventory().getFreeSlot() == -1) {
					FishingController.stopMacro(com.seolwoni.assistcore.i18n.Tr.t("인벤토리 가득 참!"), com.seolwoni.assistcore.i18n.Tr.t("보관할 아이템이 없어요"), ReelConfig.get().fullInvAlert);
				} else {
					Alerts.actionBar(com.seolwoni.assistcore.i18n.Tr.t("보관 ") + moved + com.seolwoni.assistcore.i18n.Tr.t("칸") + (taken > 0 ? com.seolwoni.assistcore.i18n.Tr.t(", 꺼냄 ") + taken + com.seolwoni.assistcore.i18n.Tr.t("칸") : ""), 0xFF55FF55);
				}
			}
		}
	}

	private static void fail(String title, String sub) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.gui.screen() instanceof AbstractContainerScreen<?> s && !(s instanceof InventoryScreen)) {
			mc.player.closeContainer();
		}
		step = Step.NONE;
		if (mc.player != null) {
			mc.player.setYRot(savedYaw);
			mc.player.setXRot(savedPitch);
		}
		FishingController.resetLook();
		FishingController.stopMacro(title, sub, true);
	}

	private static AbstractContainerScreen<?> openContainer(Minecraft mc) {
		Screen s = mc.gui.screen();
		if (s instanceof AbstractContainerScreen<?> acs && !(s instanceof InventoryScreen)) return acs;
		return null;
	}

	/** Shift-clicks one storable stack. Returns false when nothing (more) can be moved. */
	private static boolean moveOne(Minecraft mc, LocalPlayer player, AbstractContainerMenu menu) {
		int selected = player.getInventory().getSelectedSlot();
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.slots.get(i);
			if (!(slot.container instanceof Inventory)) continue;
			if (slot.getContainerSlot() == selected) continue;
			ItemStack stack = slot.getItem();
			if (!shouldStore(stack)) continue;
			int before = stack.getCount();
			mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, player);
			ItemStack after = slot.getItem();
			if (!after.isEmpty() && after.getCount() == before && ItemStack.isSameItemSameComponents(after, stack)) {
				chestFull = true;
				return false;
			}
			moved++;
			return true;
		}
		return false;
	}

	private static List<Integer> chestSlots(AbstractContainerMenu menu) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < menu.slots.size(); i++) {
			if (!(menu.slots.get(i).container instanceof Inventory)) out.add(i);
		}
		return out;
	}

	/** Takes rods / food the player is out of. Returns false when nothing more is needed. */
	private static boolean takeOne(Minecraft mc, LocalPlayer player, AbstractContainerMenu menu) {
		ReelConfig cfg = ReelConfig.get();
		if (player.getInventory().getFreeSlot() == -1) return false;
		int rods = Housekeeping.rodsWanted(player, cfg);
		boolean food = Housekeeping.foodWanted(player, cfg);
		if (rods <= 0 && !food) return false;
		int bestSlot = -1;
		int bestRem = Integer.MAX_VALUE;
		for (int i : chestSlots(menu)) {
			ItemStack s = menu.slots.get(i).getItem();
			if (rods > 0 && Housekeeping.usable(s, cfg)) {
				int r = FishingController.remaining(s);
				if (r < bestRem) {
					bestRem = r;
					bestSlot = i;
				}
			}
		}
		if (bestSlot == -1 && food) {
			for (int i : chestSlots(menu)) {
				if (AutoEat.isEdible(menu.slots.get(i).getItem())) {
					bestSlot = i;
					break;
				}
			}
		}
		if (bestSlot == -1) return false;
		mc.gameMode.handleContainerInput(menu.containerId, bestSlot, 0, ContainerInput.QUICK_MOVE, player);
		taken++;
		return true;
	}

	private static boolean sortChest(Minecraft mc, LocalPlayer player, AbstractContainerMenu menu) {
		ReelConfig cfg = ReelConfig.get();
		if (chestSorter == null) {
			if (cfg.chestSortMode == 0) return false;
			List<Integer> slots = chestSlots(menu);
			if (cfg.chestSortMode == 1 && Sorter.fill(menu, slots) < 0.7) return false;
			chestSorter = new Sorter(menu, slots);
		}
		return chestSorter.step(mc, player);
	}

	public static boolean shouldStore(ItemStack stack) {
		if (stack.isEmpty()) return false;
		ReelConfig cfg = ReelConfig.get();
		// Shulker boxes and ender chests carried to store into stay with us.
		if (cfg.storeMode != com.seolwoni.assistcore.storage.PortableStorage.CHEST && com.seolwoni.assistcore.storage.PortableStorage.isPortable(stack)) return false;
		if (stack.is(Items.FISHING_ROD)) {
			// Worn rods: enchanted ones go to the chest; plain ones stay to be combined (unless combining is off).
			if (!cfg.wornRodsToChest || !Housekeeping.worn(stack, cfg)) return false;
			return stack.isEnchanted() || !cfg.combineRods;
		}
		return switch (LootCategory.of(stack)) {
			case FISH -> cfg.storeFish;
			case TREASURE -> cfg.storeTreasure;
			case JUNK -> cfg.storeJunk;
			case OTHER -> cfg.storeOther;
		};
	}

	// ------------------------------------------------------------------ turning

	private static void beginTurn(LocalPlayer player, float yaw, float pitch) {
		fromYaw = player.getYRot();
		fromPitch = player.getXRot();
		toYaw = fromYaw + wrap(yaw - fromYaw);
		toPitch = pitch;
		turnTick = 0;
		float dist = Math.abs(toYaw - fromYaw) + Math.abs(toPitch - fromPitch);
		turnLen = Math.max(4, Math.min(16, Math.round(dist / 12f))) + FishingController.delay(0, 3);
	}

	/** Advances the smooth turn. Returns true when finished. */
	private static boolean turn(LocalPlayer player) {
		turnTick++;
		float t = Math.min(1f, turnTick / (float) turnLen);
		float e = t * t * (3 - 2 * t);
		player.setYRot(fromYaw + (toYaw - fromYaw) * e);
		player.setXRot(fromPitch + (toPitch - fromPitch) * e);
		return t >= 1f;
	}

	private static float wrap(float deg) {
		deg %= 360f;
		if (deg >= 180f) deg -= 360f;
		if (deg < -180f) deg += 360f;
		return deg;
	}

	private static float yawTo(LocalPlayer p, Vec3 target) {
		Vec3 eye = p.getEyePosition();
		double dx = target.x - eye.x, dz = target.z - eye.z;
		return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
	}

	private static float pitchTo(LocalPlayer p, Vec3 target) {
		Vec3 eye = p.getEyePosition();
		double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
		return (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
	}

	private static Direction faceToward(Vec3 eye, Vec3 center) {
		double dx = eye.x - center.x, dy = eye.y - center.y, dz = eye.z - center.z;
		double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
		if (ay >= ax && ay >= az) return dy > 0 ? Direction.UP : Direction.DOWN;
		if (ax >= az) return dx > 0 ? Direction.EAST : Direction.WEST;
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	public static String label() {
		return com.seolwoni.assistcore.i18n.Tr.t("보관 중");
	}
}

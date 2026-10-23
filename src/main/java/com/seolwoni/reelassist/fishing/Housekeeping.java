package com.seolwoni.reelassist.fishing;

import java.util.ArrayList;
import java.util.List;

import com.seolwoni.reelassist.config.ReelConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Inventory chores done between casts, one click at a time at roughly twice human speed:
 * combining worn unenchanted rods, refilling rods / food into the hotbar, and sorting.
 */
public final class Housekeeping {
	private enum Job { NONE, COMBINE, SUPPLY, SORT }

	/** Player inventory menu slot ids. */
	private static final int RESULT = 0, CRAFT_1 = 1, CRAFT_2 = 2;
	private static final int RECIPE_BONUS = 3;

	private static Job job = Job.NONE;
	private static int timer;
	private static final List<Runnable> queue = new ArrayList<>();
	private static Sorter sorter;
	private static int catchesSinceSort;
	private static boolean fetchTried;

	private Housekeeping() {
	}

	public static boolean active() {
		return job != Job.NONE;
	}

	public static void cancel() {
		job = Job.NONE;
		queue.clear();
		sorter = null;
	}

	public static void onCatch() {
		catchesSinceSort++;
	}

	public static String label() {
		return switch (job) {
			case COMBINE -> com.seolwoni.assistcore.i18n.Tr.t("낚싯대 합치는 중");
			case SUPPLY -> com.seolwoni.assistcore.i18n.Tr.t("보급 중");
			case SORT -> com.seolwoni.assistcore.i18n.Tr.t("정리 중");
			default -> "";
		};
	}

	private static int fastDelay() {
		return Math.max(1, FishingController.delay(2, 8) / 2);
	}

	/** Can we click in the player's own inventory right now? */
	private static boolean canClick(Minecraft mc, LocalPlayer player) {
		Screen s = mc.gui.screen();
		if (s instanceof AbstractContainerScreen<?> && !(s instanceof InventoryScreen)) return false;
		return player.inventoryMenu.getCarried().isEmpty();
	}

	private static int menuSlot(int invIndex) {
		return invIndex < 9 ? InventoryMenu.USE_ROW_SLOT_START + invIndex : invIndex;
	}

	// ------------------------------------------------------------------ decide

	/**
	 * Called right before a cast. Starts one chore if any is due and returns true,
	 * or returns false to let the macro cast.
	 */
	public static boolean tryStart(Minecraft mc, LocalPlayer player) {
		ReelConfig cfg = ReelConfig.get();
		if (!canClick(mc, player)) return false;
		Inventory inv = player.getInventory();

		if (cfg.combineRods && planCombine(mc, player, inv, cfg)) {
			job = Job.COMBINE;
			timer = fastDelay();
			return true;
		}
		if (planSupply(mc, player, inv, cfg)) {
			job = Job.SUPPLY;
			timer = fastDelay();
			return true;
		}
		if (cfg.autoSort && sortDue(inv, cfg)) {
			List<Integer> slots = new ArrayList<>();
			for (int i = 9; i < 36; i++) slots.add(i);
			sorter = new Sorter(player.inventoryMenu, slots);
			catchesSinceSort = 0;
			job = Job.SORT;
			timer = fastDelay();
			return true;
		}
		// Nothing usable left anywhere -> one trip to the storage chest.
		if (cfg.fetchFromChest && cfg.storeEnabled && ChestStorage.hasChest() && needsFetch(player, cfg)) {
			if (!fetchTried) {
				fetchTried = true;
				return ChestStorage.start(mc, player);
			}
		} else {
			fetchTried = false;
		}
		return false;
	}

	public static void tick(Minecraft mc, LocalPlayer player) {
		if (--timer > 0) return;
		switch (job) {
			case NONE -> {
			}
			case COMBINE, SUPPLY -> {
				if (queue.isEmpty()) {
					job = Job.NONE;
					return;
				}
				queue.remove(0).run();
				timer = fastDelay();
			}
			case SORT -> {
				if (sorter == null || !sorter.step(mc, player)) {
					int merged = sorter == null ? 0 : sorter.merges;
					sorter = null;
					job = Job.NONE;
					Alerts.actionBar(com.seolwoni.assistcore.i18n.Tr.t("인벤토리 정리 완료") + (merged > 0 ? " (" + merged + com.seolwoni.assistcore.i18n.Tr.t("번 합침)") : ""), 0xFF55FF55);
					return;
				}
				timer = fastDelay();
			}
		}
	}

	// ------------------------------------------------------------------ sort

	private static boolean sortDue(Inventory inv, ReelConfig cfg) {
		int free = 0;
		for (int i = 9; i < 36; i++) if (inv.getItem(i).isEmpty()) free++;
		if (catchesSinceSort >= cfg.sortEveryCatches) return true;
		return free <= cfg.sortFreeSlots && catchesSinceSort >= 5;
	}

	// ------------------------------------------------------------------ rods

	private static boolean isRod(ItemStack s) {
		return !s.isEmpty() && s.is(Items.FISHING_ROD);
	}

	private static boolean protectMode(ReelConfig cfg) {
		return cfg.rodMode == 1 || cfg.rodMode == 3;
	}

	public static boolean worn(ItemStack rod, ReelConfig cfg) {
		return isRod(rod) && FishingController.remaining(rod) <= cfg.protectDurability;
	}

	/** A rod the current mode is allowed to fish with. */
	public static boolean usable(ItemStack s, ReelConfig cfg) {
		if (!isRod(s)) return false;
		return !protectMode(cfg) || FishingController.remaining(s) > cfg.protectDurability;
	}

	private static boolean planCombine(Minecraft mc, LocalPlayer player, Inventory inv, ReelConfig cfg) {
		int w = -1;
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (isRod(s) && !s.isEnchanted() && worn(s, cfg)) {
				w = i;
				break;
			}
		}
		if (w == -1) return false;
		int max = inv.getItem(w).getMaxDamage();
		int wRem = FishingController.remaining(inv.getItem(w));
		int partner = -1;
		int best = Integer.MAX_VALUE;
		for (int i = 0; i < 36; i++) {
			if (i == w) continue;
			ItemStack s = inv.getItem(i);
			if (!isRod(s) || s.isEnchanted() || !s.isDamaged()) continue;
			int r = FishingController.remaining(s);
			if (wRem + r + RECIPE_BONUS > max) continue;
			if (r < best) {
				best = r;
				partner = i;
			}
		}
		if (partner == -1) return false;
		if (!player.inventoryMenu.getSlot(CRAFT_1).getItem().isEmpty()
				|| !player.inventoryMenu.getSlot(CRAFT_2).getItem().isEmpty()) return false;

		final int a = menuSlot(w), b = menuSlot(partner);
		queue.clear();
		queue.add(() -> click(mc, player, a, ContainerInput.PICKUP));
		queue.add(() -> click(mc, player, CRAFT_1, ContainerInput.PICKUP));
		queue.add(() -> click(mc, player, b, ContainerInput.PICKUP));
		queue.add(() -> click(mc, player, CRAFT_2, ContainerInput.PICKUP));
		queue.add(() -> {
			// give the server a moment to fill in the crafting result
		});
		queue.add(() -> {
		});
		queue.add(() -> click(mc, player, RESULT, ContainerInput.QUICK_MOVE));
		queue.add(() -> {
		});
		queue.add(() -> {
			// If the recipe did not go through, take the rods back out of the grid.
			if (!player.inventoryMenu.getSlot(CRAFT_1).getItem().isEmpty()) click(mc, player, CRAFT_1, ContainerInput.QUICK_MOVE);
		});
		queue.add(() -> {
			if (!player.inventoryMenu.getSlot(CRAFT_2).getItem().isEmpty()) click(mc, player, CRAFT_2, ContainerInput.QUICK_MOVE);
			Alerts.actionBar(com.seolwoni.assistcore.i18n.Tr.t("낡은 낚싯대를 합쳤어요"), 0xFF55FF55);
		});
		return true;
	}

	// ------------------------------------------------------------------ supply

	private static boolean planSupply(Minecraft mc, LocalPlayer player, Inventory inv, ReelConfig cfg) {
		queue.clear();
		int selected = inv.getSelectedSlot();

		if (cfg.supplyRods) {
			if (cfg.rodMode <= 1) {
				// Hand modes: make sure the selected slot holds a usable rod.
				if (!usable(inv.getItem(selected), cfg)) {
					int from = bestRodIn(inv, 9, 36, cfg);
					if (from != -1) {
						final int src = from, dst = selected;
						queue.add(() -> swap(mc, player, src, dst));
						return true;
					}
				}
			} else {
				int count = 0;
				for (int i = 0; i < 9; i++) if (usable(inv.getItem(i), cfg)) count++;
				if (count < cfg.hotbarRods) {
					int from = bestRodIn(inv, 9, 36, cfg);
					int dst = freeHotbarFor(inv, cfg, true);
					if (from != -1 && dst != -1) {
						final int src = from, d = dst;
						queue.add(() -> swap(mc, player, src, d));
						return true;
					}
				}
			}
		}

		if (cfg.autoEat && cfg.supplyFood) {
			boolean hotbarFood = false;
			for (int i = 0; i < 9; i++) if (AutoEat.isEdible(inv.getItem(i))) hotbarFood = true;
			if (!hotbarFood) {
				int from = -1;
				for (int i = 9; i < 36; i++) {
					if (AutoEat.isEdible(inv.getItem(i))) {
						from = i;
						break;
					}
				}
				int dst = freeHotbarFor(inv, cfg, false);
				if (from != -1 && dst != -1) {
					final int src = from, d = dst;
					queue.add(() -> swap(mc, player, src, d));
					return true;
				}
			}
		}
		return false;
	}

	/** The usable rod with the least durability left in [from, to). */
	private static int bestRodIn(Inventory inv, int from, int to, ReelConfig cfg) {
		int best = -1, bestRem = Integer.MAX_VALUE;
		for (int i = from; i < to; i++) {
			ItemStack s = inv.getItem(i);
			if (!usable(s, cfg)) continue;
			int r = FishingController.remaining(s);
			if (r < bestRem) {
				best = i;
				bestRem = r;
			}
		}
		return best;
	}

	/** An empty hotbar slot (never the selected one), or for rods a slot holding a worn rod. */
	private static int freeHotbarFor(Inventory inv, ReelConfig cfg, boolean forRod) {
		int selected = inv.getSelectedSlot();
		for (int i = 0; i < 9; i++) {
			if (i != selected && inv.getItem(i).isEmpty()) return i;
		}
		if (forRod) {
			for (int i = 0; i < 9; i++) {
				if (i != selected && isRod(inv.getItem(i)) && !usable(inv.getItem(i), cfg)) return i;
			}
		}
		return -1;
	}

	/** Swap inventory index src (main inventory) with hotbar index dst using the number-key swap. */
	private static void swap(Minecraft mc, LocalPlayer player, int src, int dstHotbar) {
		if (!canClick(mc, player)) return;
		mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot(src), dstHotbar,
				ContainerInput.SWAP, player);
	}

	private static void click(Minecraft mc, LocalPlayer player, int menuSlot, ContainerInput input) {
		mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot, 0, input, player);
	}

	// ------------------------------------------------------------------ chest fetch

	private static boolean needsFetch(LocalPlayer player, ReelConfig cfg) {
		Inventory inv = player.getInventory();
		boolean anyRod = false;
		for (int i = 0; i < 36; i++) if (usable(inv.getItem(i), cfg)) anyRod = true;
		if (cfg.supplyRods && !anyRod) return true;
		if (cfg.autoEat && cfg.supplyFood && AutoEat.hungry(player)) {
			for (int i = 0; i < 36; i++) if (AutoEat.isEdible(inv.getItem(i))) return false;
			return true;
		}
		return false;
	}

	/** How many rods the chest trip should bring back. */
	public static int rodsWanted(LocalPlayer player, ReelConfig cfg) {
		if (!cfg.supplyRods || !cfg.fetchFromChest) return 0;
		Inventory inv = player.getInventory();
		int have = 0;
		for (int i = 0; i < 36; i++) if (usable(inv.getItem(i), cfg)) have++;
		int want = cfg.rodMode <= 1 ? 1 : cfg.hotbarRods;
		return Math.max(0, want - have);
	}

	public static boolean foodWanted(LocalPlayer player, ReelConfig cfg) {
		if (!cfg.autoEat || !cfg.supplyFood || !cfg.fetchFromChest) return false;
		Inventory inv = player.getInventory();
		for (int i = 0; i < 36; i++) if (AutoEat.isEdible(inv.getItem(i))) return false;
		return true;
	}
}

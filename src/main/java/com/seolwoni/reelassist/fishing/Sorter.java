package com.seolwoni.reelassist.fishing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.seolwoni.reelassist.config.DiscardEntry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Sorts a set of menu slots with plain pick-up clicks, one click per step, so it
 * behaves exactly like a player doing it by hand. First merges partial stacks,
 * then orders by category (fish, treasure, junk, other) and item.
 *
 * <p>The caller decides when to call {@link #step}; each call performs at most one click.
 */
public final class Sorter {
	private static final int MAX_CLICKS = 400;

	private final AbstractContainerMenu menu;
	private final List<Integer> slots;
	private final List<Integer> pending = new ArrayList<>();
	private int clicks;
	private boolean done;
	public int merges;

	public Sorter(AbstractContainerMenu menu, List<Integer> slots) {
		this.menu = menu;
		this.slots = slots;
	}

	public boolean isDone() {
		return done;
	}

	/** Performs a single click. Returns false once sorting is finished. */
	public boolean step(Minecraft mc, LocalPlayer player) {
		if (done) return false;
		if (clicks > MAX_CLICKS) {
			finishCarried(mc, player);
			done = true;
			return false;
		}
		if (pending.isEmpty() && !planNext()) {
			if (!finishCarried(mc, player)) done = true;
			return !done;
		}
		int slot = pending.remove(0);
		click(mc, player, slot);
		// Whatever is still on the cursor after an operation goes back where it came from.
		return true;
	}

	private void click(Minecraft mc, LocalPlayer player, int slot) {
		mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, player);
		clicks++;
	}

	/** If something is left on the cursor, drop it into the first empty sorted slot. */
	private boolean finishCarried(Minecraft mc, LocalPlayer player) {
		if (menu.getCarried().isEmpty()) return false;
		for (int s : slots) {
			if (menu.slots.get(s).getItem().isEmpty()) {
				click(mc, player, s);
				return true;
			}
		}
		return false;
	}

	/** Queues the clicks for the next merge or move. Returns false when everything is in order. */
	private boolean planNext() {
		if (!menu.getCarried().isEmpty()) return false;

		// 1) merge partial stacks of the same item
		for (int a = 0; a < slots.size(); a++) {
			ItemStack sa = item(slots.get(a));
			if (sa.isEmpty() || sa.getCount() >= sa.getMaxStackSize()) continue;
			for (int b = a + 1; b < slots.size(); b++) {
				ItemStack sb = item(slots.get(b));
				if (sb.isEmpty() || !ItemStack.isSameItemSameComponents(sa, sb)) continue;
				int from = slots.get(b), to = slots.get(a);
				pending.add(from);
				pending.add(to);
				// If the target overflows, the rest goes back to where it came from.
				if (sa.getCount() + sb.getCount() > sa.getMaxStackSize()) pending.add(from);
				merges++;
				return true;
			}
		}

		// 2) order: compute the wanted order of the current items, fix the first wrong slot
		List<ItemStack> current = new ArrayList<>();
		for (int s : slots) current.add(item(s));
		List<ItemStack> wanted = new ArrayList<>(current);
		wanted.sort(ORDER);
		for (int p = 0; p < slots.size(); p++) {
			String want = key(wanted.get(p));
			if (want.equals(key(current.get(p)))) continue;
			for (int q = p + 1; q < slots.size(); q++) {
				if (!want.equals(key(current.get(q)))) continue;
				int from = slots.get(q), to = slots.get(p);
				pending.add(from);
				pending.add(to);
				if (!current.get(p).isEmpty()) pending.add(from);
				return true;
			}
		}
		return false;
	}

	private ItemStack item(int menuSlot) {
		return menu.slots.get(menuSlot).getItem();
	}

	private static String key(ItemStack s) {
		if (s.isEmpty()) return "~";
		return rank(s) + "|" + DiscardEntry.itemId(s) + "|" + s.getHoverName().getString();
	}

	private static int rank(ItemStack s) {
		if (s.is(Items.FISHING_ROD)) return 5;
		return LootCategory.of(s).ordinal();
	}

	private static final Comparator<ItemStack> ORDER = (a, b) -> {
		if (a.isEmpty() && b.isEmpty()) return 0;
		if (a.isEmpty()) return 1;
		if (b.isEmpty()) return -1;
		int c = Integer.compare(rank(a), rank(b));
		if (c != 0) return c;
		c = DiscardEntry.itemId(a).compareTo(DiscardEntry.itemId(b));
		if (c != 0) return c;
		c = a.getHoverName().getString().compareTo(b.getHoverName().getString());
		if (c != 0) return c;
		return Integer.compare(b.getCount(), a.getCount());
	};

	/** How full a set of slots is, 0..1. */
	public static double fill(AbstractContainerMenu menu, List<Integer> slots) {
		if (slots.isEmpty()) return 0;
		int used = 0;
		for (int s : slots) if (!menu.slots.get(s).getItem().isEmpty()) used++;
		return used / (double) slots.size();
	}
}

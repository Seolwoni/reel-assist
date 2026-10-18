package com.seolwoni.reelassist.gui;

import java.lang.reflect.Field;

import com.seolwoni.reelassist.ReelAssistClient;
import com.seolwoni.reelassist.config.DiscardEntry;
import com.seolwoni.reelassist.config.GoalEntry;
import com.seolwoni.reelassist.config.ReelConfig;
import com.seolwoni.reelassist.fishing.Alerts;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Delete-key marking of discard items inside inventories, plus red slot borders. */
public final class InventoryMarker {
	private static Field hoveredSlotField;
	private static Field leftPosField;
	private static Field topPosField;
	private static boolean looked;

	private InventoryMarker() {
	}

	private static void lookup() {
		if (looked) return;
		looked = true;
		try {
			for (Field f : AbstractContainerScreen.class.getDeclaredFields()) {
				if (f.getType() == Slot.class && hoveredSlotField == null) {
					f.setAccessible(true);
					hoveredSlotField = f;
				}
			}
			leftPosField = AbstractContainerScreen.class.getDeclaredField("leftPos");
			leftPosField.setAccessible(true);
			topPosField = AbstractContainerScreen.class.getDeclaredField("topPos");
			topPosField.setAccessible(true);
		} catch (Exception e) {
			ReelAssistClient.LOGGER.warn("Could not access container screen fields", e);
		}
	}

	public static void onKey(Screen screen, KeyEvent event) {
		if (!ReelConfig.get().modEnabled) return;
		if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
		boolean discard = ReelAssistClient.markDiscardKey.matches(event);
		boolean goal = ReelAssistClient.markGoalKey.matches(event);
		if (!discard && !goal) return;
		lookup();
		if (hoveredSlotField == null) return;
		try {
			Slot slot = (Slot) hoveredSlotField.get(acs);
			if (slot == null) return;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) return;
			if (goal) toggleGoal(stack);
			else toggle(stack);
		} catch (Exception e) {
			ReelAssistClient.LOGGER.warn("Failed to read hovered slot", e);
		}
	}

	private static void toggleGoal(ItemStack stack) {
		ReelConfig cfg = ReelConfig.get();
		String name = stack.getHoverName().getString();
		for (GoalEntry g : cfg.goals) {
			if (g.kind == GoalEntry.EXACT && g.exact != null && g.exact.sameIdentity(stack)) {
				cfg.goals.remove(g);
				ReelConfig.save();
				Alerts.actionBar("목표에서 제거: " + name);
				Alerts.click();
				return;
			}
		}
		GoalEntry g = new GoalEntry();
		g.kind = GoalEntry.EXACT;
		g.exact = DiscardEntry.of(stack, DiscardEntry.EXACT);
		g.label = name;
		cfg.goals.add(g);
		ReelConfig.save();
		Alerts.actionBar("목표에 추가: " + name + " (설정 → 목표에서 개수 지정)", 0xFFFF55FF);
		Alerts.click();
	}

	private static void toggle(ItemStack stack) {
		ReelConfig cfg = ReelConfig.get();
		DiscardEntry existing = null;
		for (DiscardEntry e : cfg.discardEntries) {
			if (e.sameIdentity(stack)) {
				existing = e;
				break;
			}
		}
		String name = stack.getHoverName().getString();
		if (existing != null) {
			cfg.discardEntries.remove(existing);
			Alerts.actionBar("버리기 목록에서 제거: " + name);
		} else {
			cfg.discardEntries.add(DiscardEntry.of(stack, cfg.defaultMatchMode));
			Alerts.actionBar("버리기 목록에 추가: " + name);
		}
		Alerts.click();
		ReelConfig.save();
	}

	public static void drawMarks(Screen screen, GuiGraphicsExtractor g) {
		if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
		ReelConfig cfg = ReelConfig.get();
		if (!cfg.modEnabled) return;
		if (cfg.discardEntries.isEmpty()) return;
		lookup();
		if (leftPosField == null || topPosField == null) return;
		try {
			int left = leftPosField.getInt(acs);
			int top = topPosField.getInt(acs);
			for (Slot slot : acs.getMenu().slots) {
				// Only mark the player's own inventory slots.
				if (!(slot.container instanceof Inventory)) continue;
				ItemStack s = slot.getItem();
				if (s.isEmpty()) continue;
				for (DiscardEntry e : cfg.discardEntries) {
					if (e.matches(s)) {
						g.outline(left + slot.x - 1, top + slot.y - 1, 18, 18, 0xFFFF4040);
						break;
					}
				}
			}
		} catch (Exception ignored) {
		}
	}
}

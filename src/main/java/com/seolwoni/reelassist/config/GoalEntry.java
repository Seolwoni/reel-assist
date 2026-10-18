package com.seolwoni.reelassist.config;

import java.util.Locale;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** An item the player hopes to catch, with a target count and what to do when it shows up. */
public class GoalEntry {
	public static final int ITEM = 0;
	public static final int BOOK = 1;
	public static final int NAME = 2;
	public static final int EXACT = 3;

	public static final int ACTION_STOP = 0;
	public static final int ACTION_ALERT = 1;

	public int kind = ITEM;
	/** ITEM: item id. */
	public String itemId = "";
	/** BOOK: enchantment id ("" = any book) and minimum level (0 = any). */
	public String enchant = "";
	public int level = 0;
	/** NAME: text the item name must contain. */
	public String text = "";
	/** EXACT: a specific item picked from the inventory. */
	public DiscardEntry exact;

	public String label = "";
	public int target = 1;
	public long progress = 0;
	public int action = ACTION_STOP;
	public boolean reached = false;

	public boolean matches(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		return switch (kind) {
			case ITEM -> itemId.equals(DiscardEntry.itemId(stack));
			case BOOK -> matchesBook(stack);
			case NAME -> !text.isBlank() && stack.getHoverName().getString().toLowerCase(Locale.ROOT)
					.contains(text.toLowerCase(Locale.ROOT));
			case EXACT -> exact != null && exact.matches(stack);
			default -> false;
		};
	}

	private boolean matchesBook(ItemStack stack) {
		if (!stack.is(Items.ENCHANTED_BOOK)) return false;
		if (enchant.isEmpty()) return true;
		ItemEnchantments ench = stack.get(DataComponents.STORED_ENCHANTMENTS);
		if (ench == null) return false;
		for (Holder<Enchantment> h : ench.keySet()) {
			if (enchant.equals(h.getRegisteredName()) && (level <= 0 || ench.getLevel(h) >= level)) return true;
		}
		return false;
	}
}

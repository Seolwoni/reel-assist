package com.seolwoni.reelassist.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * An item the player wants thrown away. Stores enough information to recognise
 * custom server items (custom name, lore, model data) as well as plain vanilla ones.
 */
public class DiscardEntry {
	public static final int EXACT = 0;
	public static final int NAME = 1;
	public static final int TYPE = 2;

	public String itemId = "";
	public String name = "";
	public List<String> lore = new ArrayList<>();
	public String modelData = "";
	public int matchMode = EXACT;

	public static DiscardEntry of(ItemStack stack, int matchMode) {
		DiscardEntry e = new DiscardEntry();
		e.itemId = itemId(stack);
		e.name = stack.getHoverName().getString();
		e.lore = loreOf(stack);
		e.modelData = modelDataOf(stack);
		e.matchMode = matchMode;
		return e;
	}

	public boolean matches(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		return switch (matchMode) {
			case TYPE -> itemId.equals(itemId(stack));
			case NAME -> name.equals(stack.getHoverName().getString());
			default -> itemId.equals(itemId(stack))
					&& name.equals(stack.getHoverName().getString())
					&& Objects.equals(lore, loreOf(stack))
					&& modelData.equals(modelDataOf(stack));
		};
	}

	/** Same item ignoring the match mode, used to toggle an entry on/off. */
	public boolean sameIdentity(ItemStack stack) {
		return itemId.equals(itemId(stack))
				&& name.equals(stack.getHoverName().getString())
				&& Objects.equals(lore, loreOf(stack))
				&& modelData.equals(modelDataOf(stack));
	}

	public static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static List<String> loreOf(ItemStack stack) {
		List<String> out = new ArrayList<>();
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				out.add(line.getString());
			}
		}
		return out;
	}

	private static String modelDataOf(ItemStack stack) {
		Object cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		return cmd == null ? "" : cmd.toString();
	}
}

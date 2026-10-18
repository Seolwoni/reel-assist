package com.seolwoni.reelassist.fishing;

import java.util.Set;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vanilla fishing loot groups. Anything else (e.g. custom server items) is OTHER. */
public enum LootCategory {
	FISH("물고기", 0xFF55FFFF),
	TREASURE("보물", 0xFFFFAA00),
	JUNK("쓰레기", 0xFFAAAAAA),
	OTHER("기타", 0xFFFFFFFF);

	public final String label;
	public final int color;

	LootCategory(String label, int color) {
		this.label = label;
		this.color = color;
	}

	private static final Set<Item> FISH_ITEMS = Set.of(
			Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH);

	private static final Set<Item> TREASURE_ITEMS = Set.of(
			Items.BOW, Items.ENCHANTED_BOOK, Items.NAME_TAG, Items.NAUTILUS_SHELL, Items.SADDLE);

	private static final Set<Item> JUNK_ITEMS = Set.of(
			Items.LILY_PAD, Items.BOWL, Items.LEATHER, Items.LEATHER_BOOTS, Items.ROTTEN_FLESH,
			Items.STICK, Items.STRING, Items.POTION, Items.BONE, Items.INK_SAC,
			Items.TRIPWIRE_HOOK, Items.BAMBOO);

	/** Every vanilla fishing drop, in display order (fish, treasure, junk). */
	public static final java.util.List<Item> FISHING_ITEMS = java.util.List.of(
			Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH,
			Items.ENCHANTED_BOOK, Items.NAME_TAG, Items.SADDLE, Items.NAUTILUS_SHELL, Items.BOW, Items.FISHING_ROD,
			Items.LILY_PAD, Items.BOWL, Items.LEATHER, Items.LEATHER_BOOTS, Items.ROTTEN_FLESH, Items.STICK,
			Items.STRING, Items.POTION, Items.BONE, Items.INK_SAC, Items.TRIPWIRE_HOOK, Items.BAMBOO);

	public static LootCategory of(ItemStack stack) {
		Item item = stack.getItem();
		if (FISH_ITEMS.contains(item)) return FISH;
		if (TREASURE_ITEMS.contains(item)) return TREASURE;
		if (item == Items.FISHING_ROD) return stack.isEnchanted() ? TREASURE : JUNK;
		if (JUNK_ITEMS.contains(item)) return JUNK;
		return OTHER;
	}
}

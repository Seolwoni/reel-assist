package com.seolwoni.reelassist.fishing;

import java.util.Set;

import com.seolwoni.reelassist.config.ReelConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Eats food from the hotbar between casts when hunger drops, then goes back to the rod. */
public final class AutoEat {
	private enum Step { NONE, SWITCH, EAT, SWITCH_BACK }

	/** Foods that hurt, teleport, or are too valuable to eat automatically. */
	private static final Set<Item> NEVER = Set.of(
			Items.ROTTEN_FLESH, Items.PUFFERFISH, Items.SPIDER_EYE, Items.POISONOUS_POTATO,
			Items.SUSPICIOUS_STEW, Items.CHORUS_FRUIT, Items.CHICKEN,
			Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_CARROT);
	private static final Set<Item> RAW_FISH = Set.of(
			Items.COD, Items.SALMON, Items.TROPICAL_FISH);

	private static Step step = Step.NONE;
	private static int timer;
	private static int foodSlot = -1;
	private static int rodSlot = -1;
	private static int eatTicks;
	private static boolean startedUsing;
	private static boolean warnedNoFood;

	private AutoEat() {
	}

	public static boolean active() {
		return step != Step.NONE;
	}

	public static void cancel() {
		if (step == Step.EAT) Minecraft.getInstance().options.keyUse.setDown(false);
		step = Step.NONE;
	}

	public static boolean hungry(LocalPlayer player) {
		ReelConfig cfg = ReelConfig.get();
		return cfg.autoEat && player.getFoodData().getFoodLevel() <= cfg.eatThreshold;
	}

	/** Starts eating if hungry and food is in the hotbar. Returns true if it started. */
	public static boolean tryStart(LocalPlayer player) {
		if (!hungry(player)) {
			warnedNoFood = false;
			return false;
		}
		int slot = findFood(player.getInventory());
		if (slot == -1) {
			if (!warnedNoFood) {
				warnedNoFood = true;
				Alerts.title(com.seolwoni.assistcore.i18n.Tr.t("배고파요"), com.seolwoni.assistcore.i18n.Tr.t("핫바에 먹을 음식이 없어요"), 0xFFFFAA00);
				Alerts.warning();
			}
			return false;
		}
		warnedNoFood = false;
		foodSlot = slot;
		rodSlot = player.getInventory().getSelectedSlot();
		timer = FishingController.delay(2, 8);
		step = Step.SWITCH;
		return true;
	}

	public static void tick(Minecraft mc, LocalPlayer player) {
		Inventory inv = player.getInventory();
		switch (step) {
			case NONE -> {
			}
			case SWITCH -> {
				if (--timer > 0) return;
				if (!isEdible(inv.getItem(foodSlot))) {
					finish();
					return;
				}
				inv.setSelectedSlot(foodSlot);
				timer = FishingController.delay(2, 6);
				eatTicks = 0;
				startedUsing = false;
				step = Step.EAT;
			}
			case EAT -> {
				if (timer > 0) {
					timer--;
					return;
				}
				if (!startedUsing) {
					mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
					mc.options.keyUse.setDown(true);
					startedUsing = true;
				}
				eatTicks++;
				boolean done = (eatTicks > 5 && !player.isUsingItem()) || eatTicks > 80;
				if (!done) return;
				mc.options.keyUse.setDown(false);
				// Still hungry and food left -> take another bite.
				if (hungry(player) && player.getFoodData().getFoodLevel() < 20 && isEdible(inv.getItem(foodSlot))) {
					timer = FishingController.delay(3, 8);
					eatTicks = 0;
					startedUsing = false;
					return;
				}
				timer = FishingController.delay(2, 8);
				step = Step.SWITCH_BACK;
			}
			case SWITCH_BACK -> {
				if (--timer > 0) return;
				if (rodSlot >= 0 && rodSlot < 9) inv.setSelectedSlot(rodSlot);
				finish();
			}
		}
	}

	private static void finish() {
		step = Step.NONE;
		FishingController.resetLook();
	}

	private static int findFood(Inventory inv) {
		int best = -1;
		int bestNutrition = -1;
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			if (!isEdible(s)) continue;
			FoodProperties food = s.get(DataComponents.FOOD);
			int n = food == null ? 0 : food.nutrition();
			if (n > bestNutrition) {
				best = i;
				bestNutrition = n;
			}
		}
		return best;
	}

	public static boolean isEdible(ItemStack s) {
		if (s.isEmpty() || !s.has(DataComponents.FOOD)) return false;
		if (NEVER.contains(s.getItem())) return false;
		if (RAW_FISH.contains(s.getItem()) && !ReelConfig.get().eatRawFish) return false;
		return true;
	}

	public static String label() {
		return com.seolwoni.assistcore.i18n.Tr.t("먹는 중");
	}
}

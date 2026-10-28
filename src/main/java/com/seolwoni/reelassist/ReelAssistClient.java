package com.seolwoni.reelassist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;
import com.seolwoni.reelassist.config.ReelConfig;
import com.seolwoni.reelassist.fishing.ChestStorage;
import com.seolwoni.reelassist.fishing.FishingController;
import com.seolwoni.reelassist.fishing.FishingStats;
import com.seolwoni.reelassist.gui.InventoryMarker;
import com.seolwoni.reelassist.hud.HudRenderer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

public class ReelAssistClient implements ClientModInitializer {
	public static final String MOD_ID = "reelassist";
	public static final Logger LOGGER = LoggerFactory.getLogger("Reel Assist");

	public static KeyMapping markDiscardKey;
	public static KeyMapping markChestKey;
	public static KeyMapping markGoalKey;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		ReelConfig.load();
		FishingStats.loadTotal();

		KeyMapping.Category category = KeyMapping.Category.register(id("main"));
		markDiscardKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.reelassist.mark_discard", InputConstants.Type.KEYBOARD, com.mojang.blaze3d.platform.InputConstants.KEY_DELETE, category));

		markChestKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.reelassist.mark_chest", InputConstants.Type.KEYBOARD, com.mojang.blaze3d.platform.InputConstants.KEY_K, category));

		markGoalKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.reelassist.mark_goal", InputConstants.Type.KEYBOARD, com.mojang.blaze3d.platform.InputConstants.KEY_UNKNOWN, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (markChestKey.consumeClick()) {
				if (ReelConfig.get().modEnabled) ChestStorage.markLookedAt(client);
			}
			FishingController.tick(client);
		});

		HudElementRegistry.addLast(id("hud"), (graphics, deltaTracker) -> HudRenderer.renderHud(graphics));

		// Shift + left click with a rod cycles the rod mode instead of attacking.
		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			if (ReelConfig.get().modEnabled && player.isShiftKeyDown() && player.getMainHandItem().is(Items.FISHING_ROD)) {
				if (clickCount > 0) FishingController.cycleRodMode();
				return true;
			}
			return false;
		});

		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof AbstractContainerScreen<?>) {
				ScreenKeyboardEvents.afterKeyPress(screen).register(InventoryMarker::onKey);
				ScreenEvents.afterExtract(screen).register((s, graphics, mx, my, delta) ->
						InventoryMarker.drawMarks(s, graphics));
			}
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ReelConfig.save();
			FishingStats.saveTotal();
		});

		LOGGER.info("Reel Assist loaded - made by seolwoni");
	}
}

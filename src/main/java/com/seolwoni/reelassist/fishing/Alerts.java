package com.seolwoni.reelassist.fishing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Titles, action-bar messages and UI sounds. */
public final class Alerts {
	private Alerts() {
	}

	public static void title(String title, String subtitle, int color) {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.hud.setTimes(2, 20, 6);
		mc.gui.hud.setSubtitle(Component.literal(subtitle == null ? "" : subtitle));
		mc.gui.hud.setTitle(Component.literal(title).withColor(color));
	}

	public static void actionBar(String text) {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.hud.setOverlayMessage(Component.literal(text), false);
	}

	/** The "띠링" bell. */
	public static void ding() {
		Minecraft mc = Minecraft.getInstance();
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.6f));
	}

	public static void warning() {
		Minecraft mc = Minecraft.getInstance();
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 0.6f));
	}

	public static void click() {
		Minecraft mc = Minecraft.getInstance();
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT, 1.2f));
	}
}

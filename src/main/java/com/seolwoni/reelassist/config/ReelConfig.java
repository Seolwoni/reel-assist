package com.seolwoni.reelassist.config;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.seolwoni.reelassist.ReelAssistClient;

import net.fabricmc.loader.api.FabricLoader;

/** All user settings. Saved to config/reelassist.json. */
public class ReelConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("reelassist.json");

	private static ReelConfig instance = new ReelConfig();

	// ---- Macro ----
	public boolean macroEnabled = false;
	/** Ticks to wait after a bite before reeling in. Capped so the fish is never lost. */
	public int reelDelayMin = 1;
	public int reelDelayMax = 30;
	/** Ticks to wait after reeling in before casting again. */
	public int castDelayMin = 1;
	public int castDelayMax = 30;
	/** Keep fishing while chat / inventory is open. */
	public boolean workInScreens = true;
	/** Reel in and recast when nothing bites for this many seconds (0 = off). */
	public int recastTimeoutSec = 60;
	/** Turn the macro on automatically when the player casts a rod by hand. */
	public boolean autoStartOnCast = false;

	// ---- Rod ----
	/** 0 = hand, 1 = hand (protect), 2 = whole hotbar, 3 = whole hotbar (protect). */
	public int rodMode = 0;
	public int protectDurability = 5;

	// ---- Alerts ----
	public boolean biteTitle = true;
	public boolean biteSound = true;
	public boolean fullInvAlert = true;
	/** Glowing outline on the bobber; colour shows idle / fish approaching / bite. */
	public boolean bobberGlow = true;
	/** Action-bar message while a fish swims toward the bobber. */
	public boolean approachAlert = true;
	public boolean approachSound = false;

	// ---- Look movement ----
	public boolean lookJitter = true;
	public float jitterDegrees = 1.0f;
	public int jitterIntervalMin = 12;
	public int jitterIntervalMax = 18;

	// ---- Discard ----
	public boolean discardEnabled = true;
	/** 0 = exact, 1 = name only, 2 = item type only. Used for newly added entries. */
	public int defaultMatchMode = 0;
	public List<DiscardEntry> discardEntries = new ArrayList<>();

	// ---- HUD ----
	public boolean trackerEnabled = true;
	public float trackerX = 0.01f;
	public float trackerY = 0.25f;
	public float trackerScale = 1.0f;
	public boolean statusEnabled = true;
	public float statusX = 0.01f;
	public float statusY = 0.18f;
	public float statusScale = 1.0f;
	/** Background opacity, 0..255. */
	public int hudBgAlpha = 110;
	/** false = this session, true = all-time totals. */
	public boolean showTotals = false;

	public static ReelConfig get() {
		return instance;
	}

	public static void load() {
		try {
			if (Files.exists(PATH)) {
				try (Reader r = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
					ReelConfig loaded = GSON.fromJson(r, ReelConfig.class);
					if (loaded != null) {
						if (loaded.discardEntries == null) loaded.discardEntries = new ArrayList<>();
						instance = loaded;
					}
				}
			}
		} catch (Exception e) {
			ReelAssistClient.LOGGER.warn("Failed to load config, using defaults", e);
		}
		instance.sanitize();
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer w = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, w);
			}
		} catch (Exception e) {
			ReelAssistClient.LOGGER.warn("Failed to save config", e);
		}
	}

	private void sanitize() {
		reelDelayMin = clamp(reelDelayMin, 1, 30);
		reelDelayMax = clamp(reelDelayMax, reelDelayMin, 30);
		castDelayMin = clamp(castDelayMin, 1, 60);
		castDelayMax = clamp(castDelayMax, castDelayMin, 60);
		rodMode = clamp(rodMode, 0, 3);
		protectDurability = clamp(protectDurability, 1, 64);
		jitterIntervalMin = clamp(jitterIntervalMin, 3, 120);
		jitterIntervalMax = clamp(jitterIntervalMax, jitterIntervalMin, 120);
		hudBgAlpha = clamp(hudBgAlpha, 0, 255);
		defaultMatchMode = clamp(defaultMatchMode, 0, 2);
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}

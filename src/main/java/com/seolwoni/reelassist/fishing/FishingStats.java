package com.seolwoni.reelassist.fishing;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.seolwoni.reelassist.ReelAssistClient;

import net.fabricmc.loader.api.FabricLoader;

/** Catch counters. One instance for this session, one persisted all-time. */
public class FishingStats {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("reelassist-stats.json");

	public static FishingStats session = new FishingStats();
	public static FishingStats total = new FishingStats();

	public static class Entry {
		public String category = LootCategory.OTHER.name();
		public String name = "";
		public long count = 0;
		public long lastCatchMs = 0;
	}

	/** key = category|itemId|name */
	public Map<String, Entry> entries = new LinkedHashMap<>();
	public long casts = 0;
	/** Ticks spent with a line in the water or the macro running. */
	public long fishingTicks = 0;

	public static FishingStats shown() {
		return com.seolwoni.reelassist.config.ReelConfig.get().showTotals ? total : session;
	}

	public void addCatch(LootCategory cat, String itemId, String name, int amount) {
		String key = cat.name() + "|" + itemId + "|" + name;
		Entry e = entries.computeIfAbsent(key, k -> new Entry());
		e.category = cat.name();
		e.name = name;
		e.count += amount;
		e.lastCatchMs = System.currentTimeMillis();
	}

	public long totalCatches() {
		long n = 0;
		for (Entry e : entries.values()) n += e.count;
		return n;
	}

	public long categoryCount(LootCategory cat) {
		long n = 0;
		for (Entry e : entries.values()) if (e.category.equals(cat.name())) n += e.count;
		return n;
	}

	public void reset() {
		entries.clear();
		casts = 0;
		fishingTicks = 0;
	}

	// ---- static helpers that update both ----

	public static void recordCatch(LootCategory cat, String itemId, String name, int amount) {
		session.addCatch(cat, itemId, name, amount);
		total.addCatch(cat, itemId, name, amount);
		saveTotal();
	}

	public static void recordCast() {
		session.casts++;
		total.casts++;
	}

	public static void tickFishing() {
		session.fishingTicks++;
		total.fishingTicks++;
	}

	public static void loadTotal() {
		try {
			if (Files.exists(PATH)) {
				try (Reader r = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
					FishingStats s = GSON.fromJson(r, FishingStats.class);
					if (s != null) {
						if (s.entries == null) s.entries = new LinkedHashMap<>();
						total = s;
					}
				}
			}
		} catch (Exception e) {
			ReelAssistClient.LOGGER.warn("Failed to load stats", e);
		}
	}

	public static void saveTotal() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer w = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(total, w);
			}
		} catch (Exception e) {
			ReelAssistClient.LOGGER.warn("Failed to save stats", e);
		}
	}
}

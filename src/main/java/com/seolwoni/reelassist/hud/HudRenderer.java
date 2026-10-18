package com.seolwoni.reelassist.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.seolwoni.reelassist.config.ReelConfig;
import com.seolwoni.reelassist.fishing.BobberFx;
import com.seolwoni.reelassist.fishing.FishingController;
import com.seolwoni.reelassist.fishing.FishingStats;
import com.seolwoni.reelassist.fishing.LootCategory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Draws the loot tracker and the status line. Also used by the HUD editor. */
public final class HudRenderer {
	public enum Element { TRACKER, STATUS }

	private static final int PAD = 3;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GOLD = 0xFFFFAA00;
	private static final int GREEN = 0xFF55FF55;
	private static final int RED = 0xFFFF5555;

	/** Unscaled size from the last draw, used for hit-testing in the editor. */
	public static int trackerW = 120, trackerH = 60, statusW = 120, statusH = 12;

	private record Seg(String text, int color) {
	}

	private HudRenderer() {
	}

	public static void renderHud(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (mc.gui.screen() instanceof HudEditScreen) return;
		ReelConfig cfg = ReelConfig.get();
		if (cfg.trackerEnabled) draw(g, Element.TRACKER, false);
		if (cfg.statusEnabled) draw(g, Element.STATUS, false);
	}

	public static void draw(GuiGraphicsExtractor g, Element el, boolean editing) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		ReelConfig cfg = ReelConfig.get();
		List<List<Seg>> lines = el == Element.TRACKER ? trackerLines() : statusLines();

		int w = 0;
		for (List<Seg> line : lines) {
			int lw = 0;
			for (Seg s : line) lw += font.width(s.text());
			w = Math.max(w, lw);
		}
		int lineH = font.lineHeight + 1;
		int h = lines.size() * lineH;
		int boxW = w + PAD * 2;
		int boxH = h + PAD * 2 - 1;
		if (el == Element.TRACKER) {
			trackerW = boxW;
			trackerH = boxH;
		} else {
			statusW = boxW;
			statusH = boxH;
		}

		float scale = el == Element.TRACKER ? cfg.trackerScale : cfg.statusScale;
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		float fx = el == Element.TRACKER ? cfg.trackerX : cfg.statusX;
		float fy = el == Element.TRACKER ? cfg.trackerY : cfg.statusY;
		float x = clampPos(fx * sw, boxW * scale, sw);
		float y = clampPos(fy * sh, boxH * scale, sh);

		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);

		int bg = (cfg.hudBgAlpha & 0xFF) << 24;
		if (cfg.hudBgAlpha > 0) g.fill(0, 0, boxW, boxH, bg);
		if (editing) g.outline(0, 0, boxW, boxH, 0xFFFFFF55);

		int ty = PAD;
		for (List<Seg> line : lines) {
			int tx = PAD;
			for (Seg s : line) {
				g.text(font, Component.literal(s.text()).getVisualOrderText(), tx, ty, s.color(), true);
				tx += font.width(s.text());
			}
			ty += lineH;
		}
		g.pose().popMatrix();
	}

	private static float clampPos(float v, float size, int screen) {
		return Math.max(0, Math.min(v, screen - size));
	}

	/** Returns the element's on-screen rectangle {x, y, w, h} in scaled GUI pixels. */
	public static float[] bounds(Element el) {
		Minecraft mc = Minecraft.getInstance();
		ReelConfig cfg = ReelConfig.get();
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		boolean t = el == Element.TRACKER;
		float scale = t ? cfg.trackerScale : cfg.statusScale;
		float w = (t ? trackerW : statusW) * scale;
		float h = (t ? trackerH : statusH) * scale;
		float x = clampPos((t ? cfg.trackerX : cfg.statusX) * sw, w, sw);
		float y = clampPos((t ? cfg.trackerY : cfg.statusY) * sh, h, sh);
		return new float[] {x, y, w, h};
	}

	// ------------------------------------------------------------------ content

	private static List<List<Seg>> trackerLines() {
		ReelConfig cfg = ReelConfig.get();
		FishingStats st = FishingStats.shown();
		List<List<Seg>> out = new ArrayList<>();
		long totalCatches = st.totalCatches();

		out.add(List.of(new Seg("Reel Assist ", GOLD),
				new Seg(cfg.showTotals ? "(누적)" : "(이번 세션)", GRAY)));

		long now = System.currentTimeMillis();
		for (LootCategory cat : LootCategory.values()) {
			long catCount = st.categoryCount(cat);
			if (catCount == 0 && cat == LootCategory.OTHER) continue;
			out.add(List.of(
					new Seg(cat.label + " ", cat.color),
					new Seg(catCount + "", WHITE),
					new Seg(" (" + pct(catCount, totalCatches) + ")", GRAY)));

			List<FishingStats.Entry> items = new ArrayList<>();
			for (FishingStats.Entry e : st.entries.values()) {
				if (e.category.equals(cat.name())) items.add(e);
			}
			items.sort(Comparator.comparingLong((FishingStats.Entry e) -> e.count).reversed());
			for (FishingStats.Entry e : items) {
				out.add(List.of(
						new Seg("  " + ago(now - e.lastCatchMs) + " | ", GRAY),
						new Seg(e.name, cat.color),
						new Seg(": " + e.count, WHITE),
						new Seg(" (" + pct(e.count, totalCatches) + ")", GRAY)));
			}
		}

		double hours = st.fishingTicks / 20.0 / 3600.0;
		long perHour = hours > 0.01 ? Math.round(totalCatches / hours) : 0;
		out.add(List.of(new Seg("총 캐스팅: ", GOLD), new Seg(st.casts + "", WHITE)));
		out.add(List.of(new Seg("총 획득: ", GOLD), new Seg(totalCatches + "", WHITE),
				new Seg(" [" + perHour + "/시간]", GRAY)));
		out.add(List.of(new Seg("낚시 시간: ", GOLD), new Seg(duration(st.fishingTicks / 20), WHITE)));
		return out;
	}

	private static List<List<Seg>> statusLines() {
		ReelConfig cfg = ReelConfig.get();
		Minecraft mc = Minecraft.getInstance();
		List<List<Seg>> out = new ArrayList<>();
		boolean on = cfg.macroEnabled;
		out.add(List.of(new Seg("매크로: ", GOLD),
				new Seg(FishingController.stateLabel(), on ? GREEN : RED)));
		List<Seg> rod = new ArrayList<>();
		rod.add(new Seg("모드: ", GOLD));
		rod.add(new Seg(FishingController.ROD_MODE_NAMES[cfg.rodMode], WHITE));
		if (mc.player != null) {
			ItemStack hand = mc.player.getMainHandItem();
			if (hand.is(Items.FISHING_ROD) && hand.isDamageableItem()) {
				int r = FishingController.remaining(hand);
				rod.add(new Seg("  내구도 " + r + "/" + hand.getMaxDamage(), r <= cfg.protectDurability ? RED : GRAY));
			}
		}
		out.add(rod);
		if (BobberFx.isApproaching()) {
			out.add(List.of(new Seg("물고기 접근 ", 0xFFFFFF55),
					new Seg(String.format("%.1f칸", BobberFx.approachDistance()), WHITE)));
		}
		return out;
	}

	private static String pct(long part, long whole) {
		if (whole <= 0) return "0%";
		return String.format("%.1f%%", part * 100.0 / whole);
	}

	private static String ago(long ms) {
		long s = ms / 1000;
		if (s < 60) return s + "s";
		if (s < 3600) return String.format("%.1fm", s / 60.0);
		if (s < 86400) return String.format("%.1fh", s / 3600.0);
		return String.format("%.1fd", s / 86400.0);
	}

	private static String duration(long sec) {
		long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
		if (h > 0) return h + "h " + m + "m";
		if (m > 0) return m + "m " + s + "s";
		return s + "s";
	}
}

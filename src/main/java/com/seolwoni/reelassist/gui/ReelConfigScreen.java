package com.seolwoni.reelassist.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.seolwoni.reelassist.config.DiscardEntry;
import com.seolwoni.reelassist.config.ReelConfig;
import com.seolwoni.reelassist.fishing.Alerts;
import com.seolwoni.reelassist.fishing.FishingController;
import com.seolwoni.reelassist.fishing.FishingStats;
import com.seolwoni.reelassist.hud.HudEditScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The settings screen opened from Mod Menu. Macro on/off sits at the very top. */
public class ReelConfigScreen extends Screen {
	private static final String[] TABS = {"매크로", "낚싯대", "알림", "시선", "버리기", "HUD"};
	private static final String[] MATCH_NAMES = {"완전 일치", "이름만", "종류만"};
	private static final int ROW = 24;

	private static int tab = 0;
	private static int discardPage = 0;

	private final Screen parent;
	private final List<Label> labels = new ArrayList<>();
	private boolean confirmResetTotal;

	private record Label(String text, int x, int y, int color, boolean centered) {
	}

	public ReelConfigScreen(Screen parent) {
		super(Component.literal("Reel Assist 설정"));
		this.parent = parent;
	}

	private ReelConfig cfg() {
		return ReelConfig.get();
	}

	@Override
	protected void init() {
		labels.clear();
		int cx = this.width / 2;
		int left = cx - 155;
		int right = cx + 5;

		// ---- Macro toggle, always on top ----
		addRenderableWidget(Button.builder(macroText(), b -> {
			cfg().macroEnabled = !cfg().macroEnabled;
			FishingController.onMacroToggled(cfg().macroEnabled);
			ReelConfig.save();
			b.setMessage(macroText());
		}).bounds(left, 22, 310, 20).build());

		// ---- Tabs ----
		int tabW = 50;
		int tabX = cx - (tabW * TABS.length + 2 * (TABS.length - 1)) / 2;
		for (int i = 0; i < TABS.length; i++) {
			final int idx = i;
			Button tb = Button.builder(Component.literal(i == tab ? "[" + TABS[i] + "]" : TABS[i]), b -> {
				tab = idx;
				rebuildWidgets();
			}).bounds(tabX + i * (tabW + 2), 46, tabW, 20).build();
			tb.active = i != tab;
			addRenderableWidget(tb);
		}

		int y = 74;
		switch (tab) {
			case 0 -> buildMacro(left, right, y);
			case 1 -> buildRod(left, right, y);
			case 2 -> buildAlerts(left, right, y);
			case 3 -> buildLook(left, right, y);
			case 4 -> buildDiscard(left, right, y);
			case 5 -> buildHud(left, right, y);
			default -> {
			}
		}

		addRenderableWidget(Button.builder(Component.literal("완료"), b -> onClose())
				.bounds(cx - 75, this.height - 26, 150, 20).build());
	}

	private Component macroText() {
		return toggleText("매크로", cfg().macroEnabled);
	}

	// ------------------------------------------------------------------ tabs

	private void buildMacro(int left, int right, int y) {
		ReelConfig c = cfg();
		add(new IntSlider(left, y, 150, "당기기 최소", "틱", 1, 30, c.reelDelayMin, v -> {
			c.reelDelayMin = v;
			ReelConfig.save();
		}));
		add(new IntSlider(right, y, 150, "당기기 최대", "틱", 1, 30, c.reelDelayMax, v -> {
			c.reelDelayMax = v;
			ReelConfig.save();
		}));
		y += ROW;
		add(new IntSlider(left, y, 150, "던지기 최소", "틱", 1, 30, c.castDelayMin, v -> {
			c.castDelayMin = v;
			ReelConfig.save();
		}));
		add(new IntSlider(right, y, 150, "던지기 최대", "틱", 1, 30, c.castDelayMax, v -> {
			c.castDelayMax = v;
			ReelConfig.save();
		}));
		y += ROW;
		toggle(left, y, 150, "화면 열려도 동작", () -> c.workInScreens, v -> c.workInScreens = v);
		add(new IntSlider(right, y, 150, "안 물면 재투척", "초", 0, 180, c.recastTimeoutSec, v -> {
			c.recastTimeoutSec = v;
			ReelConfig.save();
		}));
		y += ROW;
		toggle(left, y, 310, "낚싯대를 던지면 매크로 자동 켜기", () -> c.autoStartOnCast, v -> c.autoStartOnCast = v);
		y += ROW;
		toggle(left, y, 310, "낚싯대를 손에서 빼면 매크로 끄기", () -> c.stopWhenRodRemoved, v -> c.stopWhenRodRemoved = v);
		y += ROW + 4;
		note("당기기는 물고기를 놓치지 않도록 최대 17틱으로 자동 제한돼요.", y);
		note("재투척 0초 = 끔. 1틱 = 0.05초", y + 12);
	}

	private void buildRod(int left, int right, int y) {
		ReelConfig c = cfg();
		addRenderableWidget(Button.builder(rodText(), b -> {
			c.rodMode = (c.rodMode + 1) % 4;
			ReelConfig.save();
			b.setMessage(rodText());
		}).bounds(left, y, 310, 20).build());
		y += ROW;
		add(new IntSlider(left, y, 310, "파괴보호 기준 내구도", "", 1, 64, c.protectDurability, v -> {
			c.protectDurability = v;
			ReelConfig.save();
		}));
		y += ROW + 4;
		note("게임 안에서 낚싯대를 들고 Shift + 좌클릭으로도 모드를 바꿀 수 있어요.", y);
		note("핫바 모드는 내구도가 적은 낚싯대부터 사용해요.", y + 12);
	}

	private Component rodText() {
		return Component.literal("낚싯대 모드: " + FishingController.ROD_MODE_NAMES[cfg().rodMode]);
	}

	private void buildAlerts(int left, int right, int y) {
		ReelConfig c = cfg();
		toggle(left, y, 150, "입질 타이틀", () -> c.biteTitle, v -> c.biteTitle = v);
		toggle(right, y, 150, "입질 소리", () -> c.biteSound, v -> c.biteSound = v);
		y += ROW;
		toggle(left, y, 150, "인벤 가득 알림", () -> c.fullInvAlert, v -> c.fullInvAlert = v);
		addRenderableWidget(Button.builder(Component.literal("소리 미리듣기"), b -> Alerts.ding())
				.bounds(right, y, 150, 20).build());
		y += ROW;
		toggle(left, y, 150, "찌 강조 효과", () -> c.bobberGlow, v -> c.bobberGlow = v);
		toggle(right, y, 150, "물고기 접근 표시", () -> c.approachAlert, v -> c.approachAlert = v);
		y += ROW;
		toggle(left, y, 150, "접근 소리", () -> c.approachSound, v -> c.approachSound = v);
		y += ROW + 4;
		note("찌 색: 하늘색 = 기다리는 중, 노랑 = 물고기 접근, 빨강 = 입질", y);
		y += 12;
		note("인벤토리가 가득 차면 알림과 상관없이 매크로는 멈춰요.", y);
	}

	private void buildLook(int left, int right, int y) {
		ReelConfig c = cfg();
		toggle(left, y, 150, "시선 미세 움직임", () -> c.lookJitter, v -> c.lookJitter = v);
		add(new IntSlider(right, y, 150, "움직임 크기(x0.1도)", "", 1, 30, Math.round(c.jitterDegrees * 10), v -> {
			c.jitterDegrees = v / 10f;
			ReelConfig.save();
		}));
		y += ROW;
		add(new IntSlider(left, y, 150, "간격 최소", "초", 3, 60, c.jitterIntervalMin, v -> {
			c.jitterIntervalMin = v;
			ReelConfig.save();
		}));
		add(new IntSlider(right, y, 150, "간격 최대", "초", 3, 60, c.jitterIntervalMax, v -> {
			c.jitterIntervalMax = v;
			ReelConfig.save();
		}));
		y += ROW + 4;
		note("매크로가 켜져 있을 때만 움직여요. 직접 시점을 돌리면 그 방향을 따라가요.", y);
	}

	private void buildDiscard(int left, int right, int y) {
		ReelConfig c = cfg();
		toggle(left, y, 150, "자동 버리기", () -> c.discardEnabled, v -> c.discardEnabled = v);
		addRenderableWidget(Button.builder(matchDefaultText(), b -> {
			c.defaultMatchMode = (c.defaultMatchMode + 1) % 3;
			ReelConfig.save();
			b.setMessage(matchDefaultText());
		}).bounds(right, y, 150, 20).build());
		y += ROW;
		note("인벤토리에서 아이템에 마우스를 올리고 Delete 키 → 추가/제거", y);
		y += 14;

		List<DiscardEntry> list = c.discardEntries;
		int perPage = Math.max(1, (this.height - 30 - y - 26) / ROW);
		int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
		discardPage = Math.max(0, Math.min(discardPage, pages - 1));

		if (list.isEmpty()) {
			note("아직 지정한 아이템이 없어요.", y + 4);
		}
		int start = discardPage * perPage;
		for (int i = start; i < Math.min(list.size(), start + perPage); i++) {
			DiscardEntry e = list.get(i);
			labels.add(new Label(trim(e.name, 150), left, y + 6, 0xFFFFFFFF, false));
			addRenderableWidget(Button.builder(Component.literal(MATCH_NAMES[e.matchMode]), b -> {
				e.matchMode = (e.matchMode + 1) % 3;
				ReelConfig.save();
				b.setMessage(Component.literal(MATCH_NAMES[e.matchMode]));
			}).bounds(right, y, 90, 20).build());
			addRenderableWidget(Button.builder(Component.literal("삭제"), b -> {
				list.remove(e);
				ReelConfig.save();
				rebuildWidgets();
			}).bounds(right + 94, y, 56, 20).build());
			y += ROW;
		}
		if (pages > 1) {
			int py = this.height - 50;
			Button prev = Button.builder(Component.literal("◀"), b -> {
				discardPage--;
				rebuildWidgets();
			}).bounds(this.width / 2 - 60, py, 20, 20).build();
			prev.active = discardPage > 0;
			Button next = Button.builder(Component.literal("▶"), b -> {
				discardPage++;
				rebuildWidgets();
			}).bounds(this.width / 2 + 40, py, 20, 20).build();
			next.active = discardPage < pages - 1;
			addRenderableWidget(prev);
			addRenderableWidget(next);
			labels.add(new Label((discardPage + 1) + " / " + pages, this.width / 2, py + 6, 0xFFFFFFFF, true));
		}
	}

	private Component matchDefaultText() {
		return Component.literal("새 항목: " + MATCH_NAMES[cfg().defaultMatchMode]);
	}

	private void buildHud(int left, int right, int y) {
		ReelConfig c = cfg();
		addRenderableWidget(Button.builder(Component.literal("HUD 위치 편집"),
				b -> this.minecraft.gui.setScreen(new HudEditScreen(this))).bounds(left, y, 310, 20).build());
		y += ROW;
		toggle(left, y, 150, "통계 트래커", () -> c.trackerEnabled, v -> c.trackerEnabled = v);
		toggle(right, y, 150, "매크로 상태", () -> c.statusEnabled, v -> c.statusEnabled = v);
		y += ROW;
		add(new IntSlider(left, y, 150, "트래커 크기", "%", 50, 300, Math.round(c.trackerScale * 100), v -> {
			c.trackerScale = v / 100f;
			ReelConfig.save();
		}));
		add(new IntSlider(right, y, 150, "상태 크기", "%", 50, 300, Math.round(c.statusScale * 100), v -> {
			c.statusScale = v / 100f;
			ReelConfig.save();
		}));
		y += ROW;
		add(new IntSlider(left, y, 150, "배경 투명도", "", 0, 255, c.hudBgAlpha, v -> {
			c.hudBgAlpha = v;
			ReelConfig.save();
		}));
		addRenderableWidget(Button.builder(statsModeText(), b -> {
			c.showTotals = !c.showTotals;
			ReelConfig.save();
			b.setMessage(statsModeText());
		}).bounds(right, y, 150, 20).build());
		y += ROW;
		addRenderableWidget(Button.builder(Component.literal("세션 통계 초기화"), b -> {
			FishingStats.session.reset();
			Alerts.click();
		}).bounds(left, y, 150, 20).build());
		addRenderableWidget(Button.builder(Component.literal("누적 통계 초기화"), b -> {
			if (!confirmResetTotal) {
				confirmResetTotal = true;
				b.setMessage(Component.literal("한 번 더 누르면 초기화").withColor(0xFFFF5555));
				return;
			}
			confirmResetTotal = false;
			FishingStats.total.reset();
			FishingStats.saveTotal();
			b.setMessage(Component.literal("누적 통계 초기화"));
			Alerts.click();
		}).bounds(right, y, 150, 20).build());
	}

	private Component statsModeText() {
		return Component.literal("표시: " + (cfg().showTotals ? "누적 통계" : "이번 세션"));
	}

	// ------------------------------------------------------------------ helpers

	private void add(IntSlider slider) {
		addRenderableWidget(slider);
	}

	private void toggle(int x, int y, int w, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
		addRenderableWidget(Button.builder(toggleText(label, get.get()), b -> {
			boolean v = !get.get();
			set.accept(v);
			ReelConfig.save();
			b.setMessage(toggleText(label, v));
		}).bounds(x, y, w, 20).build());
	}

	private static Component toggleText(String label, boolean on) {
		return Component.literal(label + ": ").append(on
				? Component.literal("켜짐").withColor(0xFF55FF55)
				: Component.literal("꺼짐").withColor(0xFFFF5555));
	}

	private void note(String text, int y) {
		labels.add(new Label(text, this.width / 2, y, 0xFFAAAAAA, true));
	}

	private String trim(String s, int maxW) {
		if (this.font.width(s) <= maxW) return s;
		while (s.length() > 1 && this.font.width(s + "...") > maxW) s = s.substring(0, s.length() - 1);
		return s + "...";
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
		for (Label l : labels) {
			if (l.centered()) {
				g.centeredText(this.font, Component.literal(l.text()), l.x(), l.y(), l.color());
			} else {
				g.text(this.font, Component.literal(l.text()).getVisualOrderText(), l.x(), l.y(), l.color(), true);
			}
		}
	}

	@Override
	public void onClose() {
		ReelConfig.save();
		this.minecraft.gui.setScreen(parent);
	}
}

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
import com.seolwoni.reelassist.fishing.Goals;
import com.seolwoni.reelassist.hud.HudEditScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The settings screen opened from Mod Menu. Macro on/off sits at the very top. */
public class ReelConfigScreen extends Screen {
	private static final String[] TABS = {"매크로", "낚싯대", "알림", "시선", "버리기", "보관", "정리·보급", "목표", "HUD"};
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

		// ---- Master switch + macro toggle, always on top ----
		addRenderableWidget(Button.builder(toggleText("모드 전체", cfg().modEnabled), b -> {
			FishingController.setModEnabled(!cfg().modEnabled);
			rebuildWidgets();
		}).bounds(left, 22, 100, 20).build());
		Button macro = Button.builder(macroText(), b -> {
			cfg().macroEnabled = !cfg().macroEnabled;
			FishingController.onMacroToggled(cfg().macroEnabled);
			ReelConfig.save();
			b.setMessage(macroText());
		}).bounds(left + 105, 22, 100, 20).build();
		macro.active = cfg().modEnabled;
		addRenderableWidget(macro);

		// ---- Tabs ----
		// Two rows of tabs: 5 on top, 4 below.
		for (int i = 0; i < TABS.length; i++) {
			final int idx = i;
			int rowCount = i < 5 ? 5 : 4;
			int col = i < 5 ? i : i - 5;
			int tabW = (310 - 2 * (rowCount - 1)) / rowCount;
			int tx = left + col * (tabW + 2);
			int ty = i < 5 ? 46 : 68;
			Button tb = Button.builder(Component.literal(TABS[i]), b -> {
				tab = idx;
				rebuildWidgets();
			}).bounds(tx, ty, tabW, 20).build();
			tb.active = i != tab;
			addRenderableWidget(tb);
		}

		int y = 96;
		switch (tab) {
			case 0 -> buildMacro(left, right, y);
			case 1 -> buildRod(left, right, y);
			case 2 -> buildAlerts(left, right, y);
			case 3 -> buildLook(left, right, y);
			case 4 -> buildDiscard(left, right, y);
			case 5 -> buildStorage(left, right, y);
			case 6 -> buildChores(left, right, y);
			case 7 -> buildGoals(left, right, y);
			case 8 -> buildHud(left, right, y);
			default -> {
			}
		}

		// The worker's journal (Assist Core): this mod's jobs, newest first.
		addRenderableWidget(Button.builder(Component.literal("일지"), b -> this.minecraft.gui.setScreen(new com.seolwoni.assistcore.gui.JournalScreen(this, "reel")))
				.bounds(left + 210, 22, 48, 20).build());
		addRenderableWidget(Button.builder(Component.literal("완료"), b -> onClose())
				.bounds(left + 262, 22, 48, 20).build()); // up top: at the bottom it covered the last rows on small screens
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
		toggle(left, y, 150, "사람 같은 딜레이", () -> c.humanDelay, v -> c.humanDelay = v);
		toggle(right, y, 150, "땅에 떨어지면 재투척", () -> c.groundRecast, v -> c.groundRecast = v);
		y += ROW;
		toggle(left, y, 150, "화면 열려도 동작", () -> c.workInScreens, v -> c.workInScreens = v);
		add(new IntSlider(right, y, 150, "안 물면 재투척", "초", 0, 180, c.recastTimeoutSec, v -> {
			c.recastTimeoutSec = v;
			ReelConfig.save();
		}));
		y += ROW;
		toggle(left, y, 150, "던지면 자동 켜기", () -> c.autoStartOnCast, v -> c.autoStartOnCast = v);
		toggle(right, y, 150, "낚싯대 빼면 끄기", () -> c.stopWhenRodRemoved, v -> c.stopWhenRodRemoved = v);
		y += ROW + 4;
		note("당기기는 놓치지 않게 최대 17틱으로 제한. 재투척 0초 = 끔. 1틱 = 0.05초", y);
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
		y += ROW;
		toggle(left, y, 150, "자동 밥", () -> c.autoEat, v -> c.autoEat = v);
		add(new IntSlider(right, y, 150, "배고픔 기준", "/20", 1, 19, c.eatThreshold, v -> {
			c.eatThreshold = v;
			ReelConfig.save();
		}));
		y += ROW;
		toggle(left, y, 150, "날생선도 먹기", () -> c.eatRawFish, v -> c.eatRawFish = v);
		y += ROW + 4;
		note("낚싯대를 들고 Shift + 좌클릭으로도 모드를 바꿀 수 있어요. 핫바 모드는 내구도 적은 것부터.", y);
		note("자동 밥: 핫바의 음식 중 배부른 것부터 먹어요. 썩은 살점·복어·황금 사과 등은 제외.", y + 12);
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

	private void buildStorage(int left, int right, int y) {
		ReelConfig c = cfg();
		toggle(left, y, 150, "자동 보관", () -> c.storeEnabled, v -> c.storeEnabled = v);
		Button clear = Button.builder(Component.literal("상자 지정 해제"), b -> {
			c.chestSet = false;
			ReelConfig.save();
			rebuildWidgets();
		}).bounds(right, y, 150, 20).build();
		clear.active = c.chestSet;
		addRenderableWidget(clear);
		y += ROW;
		note(c.chestSet ? "보관 상자: " + c.chestX + ", " + c.chestY + ", " + c.chestZ : "보관 상자: 지정 안 됨", y + 4);
		y += 18;
		addRenderableWidget(Button.builder(Component.literal("보관 방식: " + com.seolwoni.assistcore.storage.PortableStorage.LABELS[Math.max(0, Math.min(3, c.storeMode))]), b -> {
			c.storeMode = (c.storeMode + 1) % 4;
			ReelConfig.save();
			rebuildWidgets();
		}).bounds(left, y, 150, 20).build());
		addRenderableWidget(Button.builder(Component.literal(switch (c.storeMode) {
			case 1 -> "옆에 엔더 상자 놓고 담기";
			case 2 -> "옆에 셜커 놓고 담고 줍기";
			case 3 -> "엔더 → 셜커 → 상자";
			default -> "손 닿는 상자에 담기";
		}), b -> {
		}).bounds(right, y, 150, 20).build()).active = false;
		y += ROW;
		toggle(left, y, 150, "물고기 보관", () -> c.storeFish, v -> c.storeFish = v);
		toggle(right, y, 150, "보물 보관", () -> c.storeTreasure, v -> c.storeTreasure = v);
		y += ROW;
		toggle(left, y, 150, "쓰레기 보관", () -> c.storeJunk, v -> c.storeJunk = v);
		toggle(right, y, 150, "기타 아이템 보관", () -> c.storeOther, v -> c.storeOther = v);
		y += ROW + 4;
		note("상자를 바라보고 K 키로 지정 (한 번 더 누르면 해제, 조작 설정에서 키 변경)", y);
		note("인벤이 차면 손 닿는 거리의 상자에 넣고 다시 낚시. 쓰는 낚싯대는 안 넣어요.", y + 12);
	}

	private void buildChores(int left, int right, int y) {
		ReelConfig c = cfg();
		toggle(left, y, 150, "인벤 자동 정리", () -> c.autoSort, v -> c.autoSort = v);
		add(new IntSlider(right, y, 150, "정리 주기", "번 낚을 때", 5, 200, c.sortEveryCatches, v -> {
			c.sortEveryCatches = v;
			ReelConfig.save();
		}));
		y += ROW;
		add(new IntSlider(left, y, 150, "빈 칸 이하면 정리", "칸", 0, 27, c.sortFreeSlots, v -> {
			c.sortFreeSlots = v;
			ReelConfig.save();
		}));
		addRenderableWidget(Button.builder(chestSortText(), b -> {
			c.chestSortMode = (c.chestSortMode + 1) % 3;
			ReelConfig.save();
			b.setMessage(chestSortText());
		}).bounds(right, y, 150, 20).build());
		y += ROW;
		toggle(left, y, 150, "낚싯대 보급", () -> c.supplyRods, v -> c.supplyRods = v);
		add(new IntSlider(right, y, 150, "핫바 낚싯대", "개", 1, 8, c.hotbarRods, v -> {
			c.hotbarRods = v;
			ReelConfig.save();
		}));
		y += ROW;
		toggle(left, y, 150, "음식 보급", () -> c.supplyFood, v -> c.supplyFood = v);
		toggle(right, y, 150, "상자에서 꺼내오기", () -> c.fetchFromChest, v -> c.fetchFromChest = v);
		y += ROW;
		toggle(left, y, 150, "낡은 낚싯대 합치기", () -> c.combineRods, v -> c.combineRods = v);
		toggle(right, y, 150, "마법 낚싯대 상자로", () -> c.wornRodsToChest, v -> c.wornRodsToChest = v);
		y += ROW + 4;
		note("합치기는 마법 없는 낚싯대만 (제작칸 수리라 마법이 사라져요). 마법 붙은 건 상자로.", y);
	}

	private Component chestSortText() {
		String[] names = {"상자 정리: 안 함", "상자 정리: 70% 이상", "상자 정리: 매번"};
		return Component.literal(names[cfg().chestSortMode]);
	}

	private void buildGoals(int left, int right, int y) {
		ReelConfig c = cfg();
		addRenderableWidget(Button.builder(Component.literal("목표 아이템 관리 (" + c.goals.size() + "개)"),
				b -> this.minecraft.gui.setScreen(new GoalScreen(this))).bounds(left, y, 310, 20).build());
		y += ROW;
		toggle(left, y, 150, "타이머", () -> c.timerEnabled, v -> {
			c.timerEnabled = v;
			Goals.resetTimer();
		});
		add(new IntSlider(right, y, 150, "시간", "분", 1, 600, c.timerMinutes, v -> {
			c.timerMinutes = v;
			Goals.resetTimer();
			ReelConfig.save();
		}));
		y += ROW;
		addRenderableWidget(Button.builder(Component.literal("타이머 초기화"), b -> {
			Goals.resetTimer();
			Alerts.click();
		}).bounds(left, y, 150, 20).build());
		y += ROW + 4;
		note("타이머는 매크로가 켜져 있을 때만 줄어들고, 0이 되면 매크로를 멈춰요.", y);
		note("인벤토리에서 바로 지정하려면 조작 설정에서 '목표 아이템 지정' 키를 정해주세요.", y + 12);
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

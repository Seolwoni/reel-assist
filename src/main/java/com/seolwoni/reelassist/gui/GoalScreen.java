package com.seolwoni.reelassist.gui;

import java.util.ArrayList;
import java.util.List;

import com.seolwoni.reelassist.config.GoalEntry;
import com.seolwoni.reelassist.config.ReelConfig;
import com.seolwoni.reelassist.fishing.Goals;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Lists goal items: target count, stop/alert, reset, delete. */
public class GoalScreen extends Screen {
	private static final int ROW = 24;
	private static int page = 0;

	private final Screen parent;
	private final List<Object[]> labels = new ArrayList<>();

	public GoalScreen(Screen parent) {
		super(Component.literal(com.seolwoni.assistcore.i18n.Tr.t("목표 아이템")));
		this.parent = parent;
	}

	@Override
	protected void init() {
		labels.clear();
		ReelConfig cfg = ReelConfig.get();
		List<GoalEntry> goals = cfg.goals;
		int cx = this.width / 2;
		int left = cx - 155;
		int y = 28;

		int perPage = Math.max(1, (this.height - 28 - 56) / ROW);
		int pages = Math.max(1, (goals.size() + perPage - 1) / perPage);
		page = Math.max(0, Math.min(page, pages - 1));

		if (goals.isEmpty()) {
			labels.add(new Object[] {com.seolwoni.assistcore.i18n.Tr.t("아직 목표가 없어요. 아래 '추가'를 눌러주세요."), cx, y + 6, 0xFFAAAAAA, true});
		}
		int start = page * perPage;
		for (int i = start; i < Math.min(goals.size(), start + perPage); i++) {
			GoalEntry g = goals.get(i);
			int color = g.reached ? 0xFF55FF55 : 0xFFFFFFFF;
			labels.add(new Object[] {trim(g.label, 112), left, y + 6, color, false});
			addRenderableWidget(Button.builder(Component.literal("-"), b -> {
				g.target = Math.max(1, g.target - (hasShift() ? 10 : 1));
				ReelConfig.save();
				rebuildWidgets();
			}).bounds(cx - 40, y, 16, 20).build());
			labels.add(new Object[] {g.progress + "/" + g.target, cx - 6, y + 6, 0xFFFFFF55, true});
			addRenderableWidget(Button.builder(Component.literal("+"), b -> {
				g.target = Math.min(9999, g.target + (hasShift() ? 10 : 1));
				ReelConfig.save();
				rebuildWidgets();
			}).bounds(cx + 12, y, 16, 20).build());
			addRenderableWidget(Button.builder(actionText(g), b -> {
				g.action = g.action == GoalEntry.ACTION_STOP ? GoalEntry.ACTION_ALERT : GoalEntry.ACTION_STOP;
				ReelConfig.save();
				b.setMessage(actionText(g));
			}).bounds(cx + 31, y, 52, 20).build());
			addRenderableWidget(Button.builder(Component.literal("↺"), b -> {
				Goals.resetProgress(g);
				rebuildWidgets();
			}).bounds(cx + 86, y, 20, 20).build());
			addRenderableWidget(Button.builder(Component.literal(com.seolwoni.assistcore.i18n.Tr.t("삭제")), b -> {
				goals.remove(g);
				ReelConfig.save();
				rebuildWidgets();
			}).bounds(cx + 109, y, 46, 20).build());
			y += ROW;
		}

		int by = this.height - 50;
		if (pages > 1) {
			Button prev = Button.builder(Component.literal("◀"), b -> {
				page--;
				rebuildWidgets();
			}).bounds(cx - 155, by, 20, 20).build();
			prev.active = page > 0;
			Button next = Button.builder(Component.literal("▶"), b -> {
				page++;
				rebuildWidgets();
			}).bounds(cx - 110, by, 20, 20).build();
			next.active = page < pages - 1;
			addRenderableWidget(prev);
			addRenderableWidget(next);
			labels.add(new Object[] {(page + 1) + "/" + pages, cx - 122, by + 6, 0xFFFFFFFF, true});
		}
		addRenderableWidget(Button.builder(Component.literal(com.seolwoni.assistcore.i18n.Tr.t("목표 추가")), b ->
				this.minecraft.gui.setScreen(new GoalPickerScreen(this))).bounds(cx - 80, by, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal(com.seolwoni.assistcore.i18n.Tr.t("전체 진행 초기화")), b -> {
			for (GoalEntry g : goals) Goals.resetProgress(g);
			rebuildWidgets();
		}).bounds(cx + 25, by, 130, 20).build());
		addRenderableWidget(Button.builder(Component.literal(com.seolwoni.assistcore.i18n.Tr.t("완료")), b -> onClose())
				.bounds(cx - 75, this.height - 26, 150, 20).build());
	}

	public Screen parentScreen() {
		return parent;
	}

	private static boolean hasShift() {
		return net.minecraft.client.Minecraft.getInstance().hasShiftDown();
	}

	private static Component actionText(GoalEntry g) {
		return g.action == GoalEntry.ACTION_STOP
				? Component.literal(com.seolwoni.assistcore.i18n.Tr.t("정지")).withColor(0xFFFF7777)
				: Component.literal(com.seolwoni.assistcore.i18n.Tr.t("알림만")).withColor(0xFF77DDFF);
	}

	private String trim(String s, int maxW) {
		if (this.font.width(s) <= maxW) return s;
		while (s.length() > 1 && this.font.width(s + "...") > maxW) s = s.substring(0, s.length() - 1);
		return s + "...";
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
		for (Object[] l : labels) {
			String text = (String) l[0];
			int x = (int) l[1], y = (int) l[2], color = (int) l[3];
			if ((boolean) l[4]) {
				g.centeredText(this.font, Component.literal(text), x, y, color);
			} else {
				g.text(this.font, Component.literal(text).getVisualOrderText(), x, y, color, true);
			}
		}
	}

	@Override
	public void onClose() {
		ReelConfig.save();
		this.minecraft.gui.setScreen(parent);
	}
}

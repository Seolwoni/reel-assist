package com.seolwoni.reelassist.hud;

import com.seolwoni.reelassist.config.ReelConfig;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag HUD boxes to move them, scroll over a box to resize it. */
public class HudEditScreen extends Screen {
	private final Screen parent;
	private HudRenderer.Element dragging;
	private double grabDx, grabDy;

	public HudEditScreen(Screen parent) {
		super(Component.literal("HUD 위치 편집"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.literal("완료"), b -> onClose())
				.bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		ReelConfig cfg = ReelConfig.get();
		if (cfg.trackerEnabled) HudRenderer.draw(g, HudRenderer.Element.TRACKER, true);
		if (cfg.statusEnabled) HudRenderer.draw(g, HudRenderer.Element.STATUS, true);
		g.centeredText(this.font, Component.literal("드래그로 이동 · 휠로 크기 조절"), this.width / 2, 8, 0xFFFFFFFF);
	}

	private HudRenderer.Element hit(double mx, double my) {
		ReelConfig cfg = ReelConfig.get();
		if (cfg.statusEnabled && inside(HudRenderer.bounds(HudRenderer.Element.STATUS), mx, my)) {
			return HudRenderer.Element.STATUS;
		}
		if (cfg.trackerEnabled && inside(HudRenderer.bounds(HudRenderer.Element.TRACKER), mx, my)) {
			return HudRenderer.Element.TRACKER;
		}
		return null;
	}

	private static boolean inside(float[] b, double mx, double my) {
		return mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		HudRenderer.Element el = hit(event.x(), event.y());
		if (el != null) {
			float[] b = HudRenderer.bounds(el);
			dragging = el;
			grabDx = event.x() - b[0];
			grabDy = event.y() - b[1];
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null) {
			ReelConfig cfg = ReelConfig.get();
			float nx = (float) ((event.x() - grabDx) / this.width);
			float ny = (float) ((event.y() - grabDy) / this.height);
			nx = Math.max(0, Math.min(1, nx));
			ny = Math.max(0, Math.min(1, ny));
			if (dragging == HudRenderer.Element.TRACKER) {
				cfg.trackerX = nx;
				cfg.trackerY = ny;
			} else {
				cfg.statusX = nx;
				cfg.statusY = ny;
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			ReelConfig.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		HudRenderer.Element el = hit(mx, my);
		if (el != null) {
			ReelConfig cfg = ReelConfig.get();
			float step = scrollY > 0 ? 0.1f : -0.1f;
			if (el == HudRenderer.Element.TRACKER) {
				cfg.trackerScale = clampScale(cfg.trackerScale + step);
			} else {
				cfg.statusScale = clampScale(cfg.statusScale + step);
			}
			ReelConfig.save();
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	private static float clampScale(float s) {
		return Math.round(Math.max(0.5f, Math.min(3f, s)) * 10f) / 10f;
	}

	@Override
	public void onClose() {
		ReelConfig.save();
		this.minecraft.gui.setScreen(parent);
	}
}

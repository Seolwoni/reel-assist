package com.seolwoni.reelassist.fishing;

import com.seolwoni.reelassist.config.GoalEntry;
import com.seolwoni.reelassist.config.ReelConfig;

import net.minecraft.world.item.ItemStack;

/** Goal items and the countdown timer. */
public final class Goals {
	/** Remaining timer ticks; -1 = not started (starts from the full time). */
	private static long timerLeft = -1;

	private Goals() {
	}

	public static void onCatch(ItemStack stack, int amount) {
		ReelConfig cfg = ReelConfig.get();
		boolean changed = false;
		for (GoalEntry g : cfg.goals) {
			if (!g.matches(stack)) continue;
			g.progress += amount;
			changed = true;
			String progress = g.label + " " + g.progress + "/" + g.target;
			if (g.action == GoalEntry.ACTION_ALERT) {
				Alerts.title(com.seolwoni.assistcore.i18n.Tr.t("원하는 아이템!"), progress, 0xFFFFAA00);
				Alerts.ding();
			} else if (!g.reached && g.progress >= g.target) {
				g.reached = true;
				ReelConfig.save();
				FishingController.stopMacro(com.seolwoni.assistcore.i18n.Tr.t("목표 달성!"), progress, true);
				Alerts.ding();
				return;
			}
		}
		if (changed) ReelConfig.save();
	}

	public static void resetProgress(GoalEntry g) {
		g.progress = 0;
		g.reached = false;
		ReelConfig.save();
	}

	// ------------------------------------------------------------------ timer

	public static void tickTimer() {
		ReelConfig cfg = ReelConfig.get();
		if (!cfg.timerEnabled) return;
		if (timerLeft < 0) timerLeft = (long) cfg.timerMinutes * 1200L;
		if (--timerLeft <= 0) {
			timerLeft = -1;
			FishingController.stopMacro(com.seolwoni.assistcore.i18n.Tr.t("타이머 종료"), cfg.timerMinutes + com.seolwoni.assistcore.i18n.Tr.t("분이 지났어요"), true);
		}
	}

	public static void resetTimer() {
		timerLeft = -1;
	}

	/** Remaining seconds, or -1 when the timer is off. */
	public static long timerSecondsLeft() {
		ReelConfig cfg = ReelConfig.get();
		if (!cfg.timerEnabled) return -1;
		long ticks = timerLeft < 0 ? (long) cfg.timerMinutes * 1200L : timerLeft;
		return ticks / 20;
	}

	public static String formatTime(long sec) {
		long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
	}
}

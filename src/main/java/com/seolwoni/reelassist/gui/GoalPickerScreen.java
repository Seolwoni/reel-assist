package com.seolwoni.reelassist.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.seolwoni.reelassist.config.DiscardEntry;
import com.seolwoni.reelassist.config.GoalEntry;
import com.seolwoni.reelassist.config.ReelConfig;
import com.seolwoni.reelassist.fishing.Alerts;
import com.seolwoni.reelassist.fishing.LootCategory;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/** Pick a goal: fishing loot grid, enchanted book by enchantment, item search, or name text. */
public class GoalPickerScreen extends Screen {
	private static final String[] MODES = {"낚시 아이템", "마법 책", "아이템 검색", "이름으로"};
	private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
	private static int mode = 0;
	private static int page = 0;
	private static String query = "";

	private final Screen parent;
	private boolean dirty;
	private final List<Icon> icons = new ArrayList<>();
	private final List<Object[]> labels = new ArrayList<>();

	private record Icon(ItemStack stack, int x, int y) {
	}

	public GoalPickerScreen(Screen parent) {
		super(Component.literal("목표 추가"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		icons.clear();
		labels.clear();
		int cx = this.width / 2;
		int left = cx - 155;

		int tw = 76;
		for (int i = 0; i < MODES.length; i++) {
			final int m = i;
			Button b = Button.builder(Component.literal(MODES[i]), x -> {
				mode = m;
				page = 0;
				rebuildWidgets();
			}).bounds(left + i * (tw + 2), 24, tw, 20).build();
			b.active = i != mode;
			addRenderableWidget(b);
		}

		int y = 52;
		switch (mode) {
			case 0 -> buildLoot(left, y);
			case 1 -> buildBooks(left, y);
			case 2 -> buildSearch(left, y);
			case 3 -> buildName(left, y);
			default -> {
			}
		}

		addRenderableWidget(Button.builder(Component.literal("뒤로"), b -> onClose())
				.bounds(cx - 75, this.height - 26, 150, 20).build());
	}

	// ------------------------------------------------------------------ modes

	private void buildLoot(int left, int y) {
		int perRow = 11;
		for (int i = 0; i < LootCategory.FISHING_ITEMS.size(); i++) {
			Item item = LootCategory.FISHING_ITEMS.get(i);
			ItemStack stack = new ItemStack(item);
			int x = left + (i % perRow) * 28;
			int yy = y + (i / perRow) * 28;
			Button b = Button.builder(Component.empty(), btn -> addItemGoal(item)).bounds(x, yy, 24, 24).build();
			b.setTooltip(Tooltip.create(stack.getHoverName()));
			addRenderableWidget(b);
			icons.add(new Icon(stack, x + 4, yy + 4));
		}
		note("아이콘을 눌러 목표로 추가해요. 마법 책의 마법은 '마법 책' 탭에서 골라요.", y + 64);
	}

	private void buildBooks(int left, int y) {
		addRenderableWidget(Button.builder(Component.literal("아무 마법 책"), b -> addBookGoal("", 0, "마법 책 (아무거나)"))
				.bounds(left, y, 310, 20).build());
		y += 24;
		if (this.minecraft.level == null) {
			note("월드에 들어가 있어야 마법 목록을 불러올 수 있어요.", y + 6);
			return;
		}
		List<Holder<Enchantment>> list = new ArrayList<>();
		this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(list::add);
		list.sort((a, b) -> a.value().description().getString().compareTo(b.value().description().getString()));

		int perPage = Math.max(1, (this.height - y - 60) / 22);
		int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
		page = Math.max(0, Math.min(page, pages - 1));
		for (int i = page * perPage; i < Math.min(list.size(), (page + 1) * perPage); i++) {
			Holder<Enchantment> h = list.get(i);
			String name = h.value().description().getString();
			String id = h.getRegisteredName();
			int max = Math.min(10, h.value().getMaxLevel());
			labels.add(new Object[] {name, left, y + 6, 0xFFFFFFFF, false});
			int bx = left + 120;
			addRenderableWidget(Button.builder(Component.literal("아무"), b -> addBookGoal(id, 0, name))
					.bounds(bx, y, 32, 20).build());
			bx += 34;
			for (int lv = 1; lv <= max; lv++) {
				final int level = lv;
				addRenderableWidget(Button.builder(Component.literal(ROMAN[lv]), b ->
						addBookGoal(id, level, name + " " + ROMAN[level] + "+")).bounds(bx, y, 24, 20).build());
				bx += 26;
			}
			y += 22;
		}
		pager(pages);
	}

	private void buildSearch(int left, int y) {
		EditBox box = new EditBox(this.font, left, y, 310, 20, Component.literal("검색"));
		box.setMaxLength(50);
		box.setValue(query);
		box.setResponder(s -> {
			if (!s.equals(query)) {
				query = s;
				page = 0;
				dirty = true;
			}
		});
		addRenderableWidget(box);
		setInitialFocus(box);
		y += 26;

		String q = query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			note("아이템 이름을 입력하세요 (예: 이름표, 안장)", y + 6);
			return;
		}
		List<Item> found = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) continue;
			ItemStack s = new ItemStack(item);
			String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
			String id = DiscardEntry.itemId(s);
			if (name.contains(q) || id.contains(q)) found.add(item);
		}
		if (found.isEmpty()) {
			note("찾는 아이템이 없어요.", y + 6);
			return;
		}
		int perPage = Math.max(1, (this.height - y - 60) / 22) * 2;
		int pages = Math.max(1, (found.size() + perPage - 1) / perPage);
		page = Math.max(0, Math.min(page, pages - 1));
		int idx = 0;
		for (int i = page * perPage; i < Math.min(found.size(), (page + 1) * perPage); i++, idx++) {
			Item item = found.get(i);
			ItemStack s = new ItemStack(item);
			int x = left + (idx % 2) * 158;
			int yy = y + (idx / 2) * 22;
			addRenderableWidget(Button.builder(Component.literal("    " + s.getHoverName().getString()), b -> addItemGoal(item))
					.bounds(x, yy, 152, 20).build());
			icons.add(new Icon(s, x + 3, yy + 2));
		}
		pager(pages);
	}

	private void buildName(int left, int y) {
		EditBox box = new EditBox(this.font, left, y, 230, 20, Component.literal("이름"));
		box.setMaxLength(60);
		addRenderableWidget(box);
		setInitialFocus(box);
		addRenderableWidget(Button.builder(Component.literal("추가"), b -> {
			String t = box.getValue().trim();
			if (t.isEmpty()) return;
			GoalEntry g = new GoalEntry();
			g.kind = GoalEntry.NAME;
			g.text = t;
			g.label = "이름에 '" + t + "'";
			add(g);
		}).bounds(left + 236, y, 74, 20).build());
		note("서버 커스텀 아이템처럼 아직 가져본 적 없는 아이템용이에요.", y + 32);
		note("입력한 글자가 이름에 들어간 아이템이 낚이면 목표로 쳐요.", y + 44);
	}

	// ------------------------------------------------------------------ helpers

	private void pager(int pages) {
		if (pages <= 1) return;
		int py = this.height - 50;
		int cx = this.width / 2;
		Button prev = Button.builder(Component.literal("◀"), b -> {
			page--;
			rebuildWidgets();
		}).bounds(cx - 60, py, 20, 20).build();
		prev.active = page > 0;
		Button next = Button.builder(Component.literal("▶"), b -> {
			page++;
			rebuildWidgets();
		}).bounds(cx + 40, py, 20, 20).build();
		next.active = page < pages - 1;
		addRenderableWidget(prev);
		addRenderableWidget(next);
		labels.add(new Object[] {(page + 1) + " / " + pages, cx, py + 6, 0xFFFFFFFF, true});
	}

	private void note(String text, int y) {
		labels.add(new Object[] {text, this.width / 2, y, 0xFFAAAAAA, true});
	}

	private void addItemGoal(Item item) {
		GoalEntry g = new GoalEntry();
		g.kind = GoalEntry.ITEM;
		ItemStack s = new ItemStack(item);
		g.itemId = DiscardEntry.itemId(s);
		g.label = s.getHoverName().getString();
		add(g);
	}

	private void addBookGoal(String enchant, int level, String label) {
		GoalEntry g = new GoalEntry();
		g.kind = GoalEntry.BOOK;
		g.enchant = enchant;
		g.level = level;
		g.label = label;
		add(g);
	}

	private void add(GoalEntry g) {
		ReelConfig.get().goals.add(g);
		ReelConfig.save();
		Alerts.click();
		this.minecraft.gui.setScreen(parent instanceof GoalScreen ? new GoalScreen(((GoalScreen) parent).parentScreen()) : parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
		for (Icon icon : icons) g.item(icon.stack(), icon.x(), icon.y());
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
	public void tick() {
		super.tick();
		if (dirty) {
			// Rebuild outside the key event so the search box keeps working smoothly.
			dirty = false;
			rebuildWidgets();
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}

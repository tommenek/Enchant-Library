package dev.enchantlibrary;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The drawn library screen. Everything is painted with filled rectangles and text, so there is
 * no GUI texture to ship and nothing to line up with a background image.
 *
 * Layout (panel 240 x 239, 8px margins):
 *   header     y 0..19    title left, stored count right
 *   list       y 20..104  collection rows + scrollbar  | right column: Books, Item, level
 *   details    y 109..140 hovered row info, full width
 *   inventory  y 146..231 label, 3 rows, hotbar
 */
public class EnchantLibraryScreen extends AbstractContainerScreen<EnchantLibraryMenu> {

    // panel
    private static final int PANEL = 0xFF2B2B33;
    private static final int PANEL_LIGHT = 0xFF3A3A45;
    private static final int INSET = 0xFF1F1F27;
    private static final int BORDER = 0xFF15151A;
    private static final int EDGE_HI = 0xFF55556A;
    private static final int SLOT_BG = 0xFF1B1B22;
    private static final int SLOT_EDGE = 0xFF0E0E12;
    private static final int BUTTON_HOVER = 0xFF4A4A5A;

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 239;
    private static final int MARGIN = 8;

    // list
    private static final int LIST_X = MARGIN;
    private static final int LIST_Y = 20;
    private static final int LIST_W = 164;
    private static final int ROW_H = 14;
    private static final int VISIBLE_ROWS = 6;
    private static final int LIST_H = VISIBLE_ROWS * ROW_H;
    private static final int SCROLL_X = LIST_X + LIST_W + 2;
    private static final int SCROLL_W = 4;
    private static final int BAR_W = 40;

    private static final int ROW_BG = 0xFF23232B;
    private static final int ROW_BG_ALT = 0xFF272730;
    private static final int ROW_HOVER = 0xFF3D4A66;
    private static final int BAR_BG = 0xFF14141A;
    private static final int BAR_FILL = 0xFF7A5FD0;
    private static final int BAR_FULL = 0xFF4FBF6A;

    private static final int TEXT = 0xFFE6E6F0;
    private static final int TEXT_DIM = 0xFF9A9AAE;
    private static final int TEXT_GOLD = 0xFFE6C34A;
    private static final int TEXT_GREEN = 0xFF7FE08A;

    // right column, same height as the list so the two line up
    private static final int COL_X = 184;
    private static final int COL_W = PANEL_W - MARGIN - COL_X;
    private static final int COL_CENTER = COL_X + COL_W / 2;

    // level selector, bottom of the right column
    private static final int LEVEL_Y = 86;
    private static final int LEVEL_BTN = 12;
    private static final int LEVEL_H = 14;
    private static final int LEVEL_MINUS_X = COL_X + 2;
    private static final int LEVEL_PLUS_X = COL_X + COL_W - 2 - LEVEL_BTN;

    // details strip
    private static final int DETAIL_Y = 109;
    private static final int DETAIL_H = 32;

    // inventory
    private static final int INVENTORY_LABEL_Y = EnchantLibraryMenu.INVENTORY_Y - 11;
    private static final int DIVIDER_Y = INVENTORY_LABEL_Y - 3;

    private int scroll = 0;
    /** 0 means "the highest level the library has". */
    private int chosenLevel = 0;
    private int hoveredRow = -1;

    public EnchantLibraryScreen(EnchantLibraryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_W, PANEL_H);
        this.titleLabelX = MARGIN;
        this.titleLabelY = 7;
        this.inventoryLabelX = EnchantLibraryMenu.INVENTORY_X - 1;
        this.inventoryLabelY = INVENTORY_LABEL_Y;
    }

    private java.util.List<Payloads.Entry> entries() {
        return this.menu.clientEntries();
    }

    private int maxScroll() {
        return Math.max(0, entries().size() - VISIBLE_ROWS);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------- drawing ----------

    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, EDGE_HI);
        g.fill(x, y, x + 1, y + h, EDGE_HI);
        g.fill(x, y + h - 1, x + w, y + h, BORDER);
        g.fill(x + w - 1, y, x + w, y + h, BORDER);
    }

    /** A sunken area: dark top/left edge, light bottom/right edge. */
    private static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, EDGE_HI);
        g.fill(x - 1, y - 1, x + w, y + h, BORDER);
        g.fill(x, y, x + w, y + h, fill);
    }

    private static void slotBox(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
        g.fill(x, y, x + 16, y + 16, SLOT_BG);
    }

    /** Cuts text to fit a width, adding "..." when it had to cut. */
    private String fit(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        String dots = "...";
        int end = text.length();
        while (end > 0 && this.font.width(text.substring(0, end) + dots) > width) {
            end--;
        }
        return text.substring(0, end) + dots;
    }

    private void centered(GuiGraphicsExtractor g, String text, int centerX, int y, int colour) {
        g.text(this.font, Component.literal(text), centerX - this.font.width(text) / 2, y, colour, false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        scroll = Math.min(scroll, maxScroll());
        int left = this.leftPos;
        int top = this.topPos;

        panel(g, left, top, PANEL_W, PANEL_H, PANEL);

        // collection list
        inset(g, left + LIST_X, top + LIST_Y, LIST_W, LIST_H, ROW_BG);
        java.util.List<Payloads.Entry> entries = entries();
        hoveredRow = -1;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scroll + i;
            int rowX = left + LIST_X;
            int rowY = top + LIST_Y + i * ROW_H;
            boolean hovering = index < entries.size()
                    && inside(mouseX, mouseY, rowX, rowY, LIST_W, ROW_H);
            if (hovering) {
                hoveredRow = index;
            }
            g.fill(rowX, rowY, rowX + LIST_W, rowY + ROW_H,
                    hovering ? ROW_HOVER : (i % 2 == 0 ? ROW_BG : ROW_BG_ALT));
            if (index >= entries.size()) {
                continue;
            }

            // progress toward the next level, on the right of the row
            Payloads.Entry entry = entries.get(index);
            int barX = rowX + LIST_W - BAR_W - 4;
            int barY = rowY + 4;
            g.fill(barX, barY, barX + BAR_W, barY + 6, BAR_BG);
            if (entry.nextLevelPoints() > 0) {
                int have = EnchantLibraryBlockEntity.pointsForLevel(entry.level());
                int span = Math.max(1, entry.nextLevelPoints() - have);
                float progress = Math.min(1.0F, Math.max(0.0F, (entry.points() - have) / (float) span));
                g.fill(barX, barY, barX + (int) (BAR_W * progress), barY + 6, BAR_FILL);
            } else {
                g.fill(barX, barY, barX + BAR_W, barY + 6, BAR_FULL);
            }
        }

        // scrollbar track is always there so the list never shifts
        inset(g, left + SCROLL_X, top + LIST_Y, SCROLL_W, LIST_H, BAR_BG);
        if (maxScroll() > 0) {
            int knobH = Math.max(12, LIST_H * VISIBLE_ROWS / entries.size());
            int knobY = top + LIST_Y + (LIST_H - knobH) * scroll / maxScroll();
            g.fill(left + SCROLL_X, knobY, left + SCROLL_X + SCROLL_W, knobY + knobH, EDGE_HI);
        }

        // right column: store and anvil slots, level selector
        panel(g, left + COL_X, top + LIST_Y - 1, COL_W, LIST_H + 2, PANEL_LIGHT);
        slotBox(g, left + EnchantLibraryMenu.STORE_SLOT_X, top + EnchantLibraryMenu.STORE_SLOT_Y);
        slotBox(g, left + EnchantLibraryMenu.TARGET_SLOT_X, top + EnchantLibraryMenu.TARGET_SLOT_Y);

        int ly = top + LEVEL_Y;
        inset(g, left + LEVEL_MINUS_X + LEVEL_BTN + 1, ly, LEVEL_PLUS_X - LEVEL_MINUS_X - LEVEL_BTN - 2,
                LEVEL_H, INSET);
        for (int bx : new int[]{LEVEL_MINUS_X, LEVEL_PLUS_X}) {
            boolean hover = inside(mouseX, mouseY, left + bx, ly, LEVEL_BTN, LEVEL_H);
            panel(g, left + bx, ly, LEVEL_BTN, LEVEL_H, hover ? BUTTON_HOVER : PANEL);
        }

        // details strip
        inset(g, left + MARGIN, top + DETAIL_Y, PANEL_W - 2 * MARGIN, DETAIL_H, INSET);

        // divider above the inventory
        g.fill(left + MARGIN, top + DIVIDER_Y, left + PANEL_W - MARGIN, top + DIVIDER_Y + 1, BORDER);
        g.fill(left + MARGIN, top + DIVIDER_Y + 1, left + PANEL_W - MARGIN, top + DIVIDER_Y + 2, EDGE_HI);

        // player inventory slots
        for (Slot slot : this.menu.slots) {
            if (slot.container instanceof Inventory) {
                slotBox(g, left + slot.x, top + slot.y);
            }
        }
    }

    /** Draws text at 3/4 size, so the details fit on one line each. */
    private void smallText(GuiGraphicsExtractor g, String text, int x, int y, int colour) {
        float scale = 0.75F;
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        g.text(this.font, Component.literal(text), Math.round(x / scale), Math.round(y / scale), colour, false);
        g.pose().popMatrix();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        java.util.List<Payloads.Entry> entries = entries();

        // header
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT_GOLD, false);
        String count = entries.size() + " stored";
        g.text(this.font, Component.literal(count),
                SCROLL_X + SCROLL_W - this.font.width(count), this.titleLabelY, TEXT_DIM, false);

        // list rows
        int nameW = LIST_W - BAR_W - 14;
        for (int i = 0; i < VISIBLE_ROWS && scroll + i < entries.size(); i++) {
            Payloads.Entry entry = entries.get(scroll + i);
            g.text(this.font, Component.literal(fit(entry.name(), nameW)),
                    LIST_X + 4, LIST_Y + i * ROW_H + 3, TEXT, false);
        }
        if (entries.isEmpty()) {
            centered(g, "Empty library", LIST_X + LIST_W / 2, LIST_Y + LIST_H / 2 - 9, TEXT_DIM);
            centered(g, "Drop enchanted books in", LIST_X + LIST_W / 2, LIST_Y + LIST_H / 2 + 2, TEXT_DIM);
        }

        // right column
        centered(g, "Books", COL_CENTER, EnchantLibraryMenu.STORE_SLOT_Y - 10, TEXT_DIM);
        centered(g, "Item", COL_CENTER, EnchantLibraryMenu.TARGET_SLOT_Y - 10, TEXT_DIM);
        int textY = LEVEL_Y + (LEVEL_H - 8) / 2 + 1;
        centered(g, "-", LEVEL_MINUS_X + LEVEL_BTN / 2 + 1, textY, TEXT);
        centered(g, "+", LEVEL_PLUS_X + LEVEL_BTN / 2 + 1, textY, TEXT);
        centered(g, chosenLevel <= 0 ? "max" : String.valueOf(chosenLevel), COL_CENTER, textY,
                chosenLevel <= 0 ? TEXT_GOLD : TEXT);

        // details for the hovered row, instead of a tooltip
        int dx = MARGIN + 4;
        int dy = DETAIL_Y + 4;
        if (hoveredRow >= 0 && hoveredRow < entries.size()) {
            Payloads.Entry entry = entries.get(hoveredRow);
            smallText(g, entry.name() + "  -  level " + entry.level() + " of max " + entry.cap()
                    + " (vanilla " + entry.vanillaMax() + ")", dx, dy, TEXT);

            int apply = chosenLevel <= 0 ? entry.level() : Math.min(chosenLevel, entry.level());
            int oldLevel = 0; // the real check happens server-side; this is the common case
            if (apply > oldLevel) {
                int xp = EnchantLibraryBlockEntity.xpCost(apply) - EnchantLibraryBlockEntity.xpCost(oldLevel);
                int used = EnchantLibraryBlockEntity.enchantPoints(apply)
                        - EnchantLibraryBlockEntity.enchantPoints(oldLevel);
                smallText(g, "Apply lv " + apply + ": " + xp + " XP, " + used + " pts   |   "
                        + entry.points() + " pts stored", dx, dy + 9, TEXT_GREEN);
            } else {
                smallText(g, entry.points() + " pts stored", dx, dy + 9, TEXT_DIM);
            }
            smallText(g, "Left-click: apply   |   Right-click: take a book", dx, dy + 18, TEXT_DIM);
        } else {
            smallText(g, "Hover an enchantment for details.", dx, dy, TEXT_DIM);
            smallText(g, "Books slot: store books.   Item slot: item to enchant.", dx, dy + 9, TEXT_DIM);
            smallText(g, "Scroll the list with the mouse wheel.", dx, dy + 18, TEXT_DIM);
        }

        g.text(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, TEXT_DIM, false);
    }

    // ---------- input ----------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        int left = this.leftPos;
        int top = this.topPos;

        // level selector
        if (inside(mouseX, mouseY, left + LEVEL_MINUS_X, top + LEVEL_Y, LEVEL_BTN, LEVEL_H)) {
            chosenLevel = Math.max(0, chosenLevel - 1);
            return true;
        }
        if (inside(mouseX, mouseY, left + LEVEL_PLUS_X, top + LEVEL_Y, LEVEL_BTN, LEVEL_H)) {
            chosenLevel = Math.min(EnchantLibraryBlockEntity.ABSOLUTE_LEVEL_CAP, chosenLevel + 1);
            return true;
        }

        // collection rows
        if (inside(mouseX, mouseY, left + LIST_X, top + LIST_Y, LIST_W, LIST_H)) {
            int row = (int) ((mouseY - (top + LIST_Y)) / ROW_H) + scroll;
            if (row >= 0 && row < entries().size()) {
                Payloads.Entry entry = entries().get(row);
                int action = button == 1 ? Payloads.LibraryAction.EXTRACT : Payloads.LibraryAction.APPLY;
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new Payloads.LibraryAction(entry.id(), action, chosenLevel));
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

}

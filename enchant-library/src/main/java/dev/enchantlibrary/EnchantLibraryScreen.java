package dev.enchantlibrary;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The drawn library screen. Everything is painted with filled rectangles and text, so there is
 * no GUI texture to ship and nothing to line up with a background image.
 */
public class EnchantLibraryScreen extends AbstractContainerScreen<EnchantLibraryMenu> {

    // panel
    private static final int PANEL = 0xFF2B2B33;
    private static final int PANEL_LIGHT = 0xFF3A3A45;
    private static final int BORDER = 0xFF15151A;
    private static final int EDGE_HI = 0xFF55556A;
    private static final int SLOT_BG = 0xFF1B1B22;
    private static final int SLOT_EDGE = 0xFF0E0E12;

    // list
    private static final int LIST_X = 8;
    private static final int LIST_Y = 30;
    private static final int LIST_W = 172;
    private static final int ROW_H = 14;
    private static final int VISIBLE_ROWS = 7;
    private static final int LIST_H = VISIBLE_ROWS * ROW_H;

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
    private static final int TEXT_RED = 0xFFE07F7F;

    // level selector
    private static final int LEVEL_X = 184;
    private static final int LEVEL_Y = 118;
    private static final int LEVEL_BTN = 14;

    private int scroll = 0;
    /** 0 means "the highest level the library has". */
    private int chosenLevel = 0;
    private int hoveredRow = -1;

    public EnchantLibraryScreen(EnchantLibraryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 230;
        this.imageHeight = 232;
        this.titleLabelX = 8;
        this.titleLabelY = 8;
        this.inventoryLabelX = EnchantLibraryMenu.INVENTORY_X;
        this.inventoryLabelY = EnchantLibraryMenu.INVENTORY_Y - 11;
    }

    private java.util.List<Payloads.Entry> entries() {
        return this.menu.clientEntries();
    }

    private int maxScroll() {
        return Math.max(0, entries().size() - VISIBLE_ROWS);
    }

    // ---------- drawing ----------

    private static void panel(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, EDGE_HI);
        g.fill(x, y, x + 1, y + h, EDGE_HI);
        g.fill(x, y + h - 1, x + w, y + h, BORDER);
        g.fill(x + w - 1, y, x + w, y + h, BORDER);
    }

    private static void slotBox(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
        g.fill(x, y, x + 16, y + 16, SLOT_BG);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        panel(g, left, top, this.imageWidth, this.imageHeight, PANEL);

        // collection area
        g.fill(left + LIST_X - 1, top + LIST_Y - 1, left + LIST_X + LIST_W + 1, top + LIST_Y + LIST_H + 1, BORDER);

        java.util.List<Payloads.Entry> entries = entries();
        hoveredRow = -1;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scroll + i;
            int rowX = left + LIST_X;
            int rowY = top + LIST_Y + i * ROW_H;
            boolean hovering = mouseX >= rowX && mouseX < rowX + LIST_W
                    && mouseY >= rowY && mouseY < rowY + ROW_H;

            if (index >= entries.size()) {
                g.fill(rowX, rowY, rowX + LIST_W, rowY + ROW_H, i % 2 == 0 ? ROW_BG : ROW_BG_ALT);
                continue;
            }
            if (hovering) {
                hoveredRow = index;
            }
            g.fill(rowX, rowY, rowX + LIST_W, rowY + ROW_H,
                    hovering ? ROW_HOVER : (i % 2 == 0 ? ROW_BG : ROW_BG_ALT));

            Payloads.Entry entry = entries.get(index);
            g.drawString(this.font, entry.name(), rowX + 4, rowY + 3, TEXT, false);

            // progress toward the next level, on the right of the row
            int barW = 44;
            int barX = rowX + LIST_W - barW - 4;
            int barY = rowY + 4;
            g.fill(barX, barY, barX + barW, barY + 6, BAR_BG);
            if (entry.nextLevelPoints() > 0) {
                int have = EnchantLibraryBlockEntity.pointsForLevel(entry.level());
                int span = Math.max(1, entry.nextLevelPoints() - have);
                float progress = Math.min(1.0F, Math.max(0.0F, (entry.points() - have) / (float) span));
                g.fill(barX, barY, barX + (int) (barW * progress), barY + 6, BAR_FILL);
            } else {
                g.fill(barX, barY, barX + barW, barY + 6, BAR_FULL);
            }
        }

        // scrollbar
        if (maxScroll() > 0) {
            int trackX = left + LIST_X + LIST_W + 2;
            g.fill(trackX, top + LIST_Y, trackX + 4, top + LIST_Y + LIST_H, BAR_BG);
            int knobH = Math.max(12, LIST_H * VISIBLE_ROWS / Math.max(1, entries.size()));
            int knobY = top + LIST_Y + (LIST_H - knobH) * scroll / maxScroll();
            g.fill(trackX, knobY, trackX + 4, knobY + knobH, EDGE_HI);
        }

        // right column: store and anvil slots
        panel(g, left + 182, top + 22, 40, 88, PANEL_LIGHT);
        slotBox(g, left + EnchantLibraryMenu.STORE_SLOT_X, top + EnchantLibraryMenu.STORE_SLOT_Y);
        slotBox(g, left + EnchantLibraryMenu.TARGET_SLOT_X, top + EnchantLibraryMenu.TARGET_SLOT_Y);

        // level selector buttons
        int lx = left + LEVEL_X;
        int ly = top + LEVEL_Y;
        panel(g, lx, ly, 40, LEVEL_BTN, PANEL_LIGHT);
        panel(g, lx, ly + LEVEL_BTN + 1, LEVEL_BTN, LEVEL_BTN, PANEL_LIGHT);
        panel(g, lx + 26, ly + LEVEL_BTN + 1, LEVEL_BTN, LEVEL_BTN, PANEL_LIGHT);

        // player inventory slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotBox(g, left + EnchantLibraryMenu.INVENTORY_X + col * 18,
                        top + EnchantLibraryMenu.INVENTORY_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotBox(g, left + EnchantLibraryMenu.INVENTORY_X + col * 18, top + EnchantLibraryMenu.HOTBAR_Y);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT_GOLD, false);
        g.drawString(this.font, Component.literal(entries().size() + " stored"),
                LIST_X, LIST_Y - 11, TEXT_DIM, false);
        g.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, TEXT_DIM, false);

        // column labels
        g.drawString(this.font, Component.literal("Books"), 186, 24, TEXT_DIM, false);
        g.drawString(this.font, Component.literal("Item"), 186, 74, TEXT_DIM, false);

        // level selector text
        String levelText = chosenLevel <= 0 ? "Lv: max" : "Lv: " + chosenLevel;
        g.drawString(this.font, Component.literal(levelText), LEVEL_X + 4, LEVEL_Y + 3, TEXT, false);
        g.drawString(this.font, Component.literal("-"), LEVEL_X + 5, LEVEL_Y + LEVEL_BTN + 5, TEXT, false);
        g.drawString(this.font, Component.literal("+"), LEVEL_X + 30, LEVEL_Y + LEVEL_BTN + 5, TEXT, false);

        // detail strip for the hovered row, instead of a tooltip
        int detailY = LIST_Y + LIST_H + 4;
        if (hoveredRow >= 0 && hoveredRow < entries().size()) {
            Payloads.Entry entry = entries().get(hoveredRow);
            g.drawString(this.font, Component.literal(
                            entry.name() + "  -  level " + entry.level() + " of max " + entry.cap()
                                    + " (vanilla " + entry.vanillaMax() + ")"),
                    LIST_X, detailY, TEXT, false);

            String second;
            int colour;
            int apply = chosenLevel <= 0 ? entry.level() : Math.min(chosenLevel, entry.level());
            int oldLevel = 0; // the real check happens server-side; this is the common case
            if (apply > oldLevel) {
                int xp = EnchantLibraryBlockEntity.xpCost(apply) - EnchantLibraryBlockEntity.xpCost(oldLevel);
                int used = EnchantLibraryBlockEntity.enchantPoints(apply)
                        - EnchantLibraryBlockEntity.enchantPoints(oldLevel);
                second = "Apply lv " + apply + ": " + xp + " XP, " + used + " pts  |  "
                        + entry.points() + " pts stored";
                colour = TEXT_GREEN;
            } else {
                second = entry.points() + " pts stored";
                colour = TEXT_DIM;
            }
            g.drawString(this.font, Component.literal(second), LIST_X, detailY + 10, colour, false);
            g.drawString(this.font, Component.literal("Left-click apply  |  Right-click take book"),
                    LIST_X, detailY + 20, TEXT_DIM, false);
        } else {
            g.drawString(this.font, Component.literal("Hover an enchantment for details."),
                    LIST_X, detailY, TEXT_DIM, false);
            g.drawString(this.font, Component.literal("Books -> store slot, item -> anvil slot."),
                    LIST_X, detailY + 10, TEXT_DIM, false);
        }
    }

    // ---------- input ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = this.leftPos;
        int top = this.topPos;

        // level selector
        int lx = left + LEVEL_X;
        int ly = top + LEVEL_Y + LEVEL_BTN + 1;
        if (mouseY >= ly && mouseY < ly + LEVEL_BTN) {
            if (mouseX >= lx && mouseX < lx + LEVEL_BTN) {
                chosenLevel = Math.max(0, chosenLevel - 1);
                return true;
            }
            if (mouseX >= lx + 26 && mouseX < lx + 26 + LEVEL_BTN) {
                chosenLevel = Math.min(EnchantLibraryBlockEntity.ABSOLUTE_LEVEL_CAP, chosenLevel + 1);
                return true;
            }
        }

        // collection rows
        int rowX = left + LIST_X;
        if (mouseX >= rowX && mouseX < rowX + LIST_W
                && mouseY >= top + LIST_Y && mouseY < top + LIST_Y + LIST_H) {
            int row = (int) ((mouseY - (top + LIST_Y)) / ROW_H) + scroll;
            if (row >= 0 && row < entries().size()) {
                Payloads.Entry entry = entries().get(row);
                int action = button == 1 ? Payloads.LibraryAction.EXTRACT : Payloads.LibraryAction.APPLY;
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new Payloads.LibraryAction(entry.id(), action, chosenLevel));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
        scroll = Math.min(scroll, maxScroll());
    }
}

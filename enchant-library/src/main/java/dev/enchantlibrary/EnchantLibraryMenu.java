package dev.enchantlibrary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

/**
 * The library screen. It reuses the vanilla 9x6 chest screen, so it needs no client code:
 * the server fills the slots and interprets every click itself.
 *
 * Layout (top 6 rows):
 *  - slots 0-44   one enchanted book per stored enchantment (shown at the library's level)
 *  - slot 45      help
 *  - slot 48/50   previous / next page
 *  - slot 49      the item to enchant (a real slot: put an item in, take it out again)
 *
 * Clicks:
 *  - left-click an enchantment            -> enchant the item in slot 49
 *  - right-click / shift-click one        -> take out a book
 *  - click with an enchanted book on the list, or shift-click a book in your inventory -> store it
 */
public class EnchantLibraryMenu extends AbstractContainerMenu {

    private static final int ROWS = 6;
    private static final int MENU_SLOTS = ROWS * 9;     // 54
    private static final int PAGE_SIZE = 45;
    private static final int INFO_SLOT = 45;
    private static final int PREV_SLOT = 48;
    private static final int TARGET_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int PLAYER_START = MENU_SLOTS; // 54
    private static final int PLAYER_END = PLAYER_START + 36;

    private final EnchantLibraryBlockEntity library;
    private final Level level;
    private final SimpleContainer container = new SimpleContainer(MENU_SLOTS);
    private int page = 0;

    public EnchantLibraryMenu(int containerId, Inventory playerInventory,
                              EnchantLibraryBlockEntity library, Level level) {
        super(MenuType.GENERIC_9x6, containerId);
        this.library = library;
        this.level = level;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9;
                int x = 8 + col * 18;
                int y = 18 + row * 18;
                addSlot(index == TARGET_SLOT
                        ? new TargetSlot(container, index, x, y)
                        : new DisplaySlot(container, index, x, y));
            }
        }
        int offset = (ROWS - 4) * 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + offset));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 161 + offset));
        }
        refresh();
    }

    // ---------- slots ----------

    /** A slot that only shows something: nothing can be put in or taken out. */
    private static class DisplaySlot extends Slot {
        DisplaySlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    /** The slot for the item that gets enchanted: one enchantable item at a time. */
    private static class TargetSlot extends Slot {
        TargetSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return EnchantLibraryBlock.isRelevant(stack) && !stack.is(Items.ENCHANTED_BOOK);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    // ---------- display ----------

    private static Component line(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(style -> style.withItalic(false).withColor(color));
    }

    private static ItemStack named(Item item, String name, ChatFormatting color, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, line(name, color));
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return stack;
    }

    private static ItemStack filler() {
        return named(Items.GRAY_STAINED_GLASS_PANE, " ", ChatFormatting.GRAY, List.of());
    }

    private ItemStack entryStack(String id, int pts) {
        Optional<Holder<Enchantment>> found = EnchantLibraryBlockEntity.lookup(level, id);
        if (found.isEmpty()) {
            return named(Items.BARRIER, id, ChatFormatting.RED,
                    List.of(line("Unknown enchantment (mod removed?)", ChatFormatting.GRAY)));
        }
        Holder<Enchantment> holder = found.get();
        int lvl = EnchantLibraryBlockEntity.effectiveLevel(holder, pts);
        int cap = EnchantLibraryBlockEntity.levelCap(holder);

        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(holder, lvl);
        book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());

        List<Component> lore = new ArrayList<>();
        lore.add(line("Stored points: " + pts, ChatFormatting.GRAY));
        lore.add(line("Library level: " + lvl + " / " + cap, ChatFormatting.GRAY));
        if (lvl < cap) {
            lore.add(line("Next level at " + EnchantLibraryBlockEntity.pointsForLevel(lvl + 1) + " points",
                    ChatFormatting.DARK_GRAY));
        }
        lore.add(line(" ", ChatFormatting.GRAY));
        lore.add(line("Left-click: enchant the item in the middle slot", ChatFormatting.YELLOW));
        lore.add(line("Right-click: take out a book", ChatFormatting.YELLOW));
        book.set(DataComponents.LORE, new ItemLore(lore));
        return book;
    }

    private ItemStack infoStack() {
        return named(Items.BOOK, "Enchant Library", ChatFormatting.GOLD, List.of(
                line("Click with an enchanted book to store it", ChatFormatting.GRAY),
                line("(or shift-click one in your inventory).", ChatFormatting.GRAY),
                line("Put an item in the middle bottom slot,", ChatFormatting.GRAY),
                line("then left-click an enchantment.", ChatFormatting.GRAY),
                line(" ", ChatFormatting.GRAY),
                line("Two level-N books make one level N+1.", ChatFormatting.DARK_GRAY),
                line("Taking a book out uses up its points.", ChatFormatting.DARK_GRAY)));
    }

    private void refresh() {
        List<Map.Entry<String, Integer>> entries = library.entries();
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(Math.max(page, 0), pages - 1);

        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            container.setItem(i, index < entries.size()
                    ? entryStack(entries.get(index).getKey(), entries.get(index).getValue())
                    : ItemStack.EMPTY);
        }
        container.setItem(INFO_SLOT, infoStack());
        container.setItem(46, filler());
        container.setItem(47, filler());
        container.setItem(51, filler());
        container.setItem(52, filler());
        container.setItem(53, filler());
        container.setItem(PREV_SLOT, page > 0
                ? named(Items.ARROW, "Previous page", ChatFormatting.WHITE, List.of())
                : filler());
        container.setItem(NEXT_SLOT, page < pages - 1
                ? named(Items.ARROW, "Next page (" + (page + 2) + "/" + pages + ")", ChatFormatting.WHITE, List.of())
                : filler());
    }

    // ---------- clicks ----------

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (slotId >= 0 && slotId < MENU_SLOTS && slotId != TARGET_SLOT) {
            // every click on the list / buttons is ours; vanilla never gets to move these items
            if (input != ContainerInput.QUICK_CRAFT && player instanceof ServerPlayer serverPlayer) {
                handleMenuClick(slotId, button, input, serverPlayer);
            }
            return;
        }
        super.clicked(slotId, button, input, player);
    }

    private void handleMenuClick(int slotId, int button, ContainerInput input, ServerPlayer player) {
        ItemStack carried = getCarried();

        if (input == ContainerInput.PICKUP && carried.is(Items.ENCHANTED_BOOK)) {
            // holding a book: store it
            EnchantLibraryBlockEntity.Result result = library.storeBook(
                    carried.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
            if (result.success()) {
                carried.shrink(1);
                setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
            player.sendOverlayMessage(result.message());
        } else if (slotId == PREV_SLOT && input == ContainerInput.PICKUP) {
            page--;
        } else if (slotId == NEXT_SLOT && input == ContainerInput.PICKUP) {
            page++;
        } else if (slotId < PAGE_SIZE
                && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
            List<Map.Entry<String, Integer>> entries = library.entries();
            int index = page * PAGE_SIZE + slotId;
            if (index < entries.size()) {
                String id = entries.get(index).getKey();
                boolean takeOut = input == ContainerInput.QUICK_MOVE || button == 1;
                if (takeOut) {
                    ItemStack book = library.extractBook(level, id);
                    if (!book.isEmpty()) {
                        player.getInventory().placeItemBackInInventory(book);
                    }
                } else {
                    ItemStack target = container.getItem(TARGET_SLOT);
                    if (target.isEmpty()) {
                        player.sendOverlayMessage(Component.literal(
                                "Put an item in the middle bottom slot first."));
                    } else {
                        player.sendOverlayMessage(library.applyTo(level, id, target).message());
                        container.setChanged();
                    }
                }
            }
        }

        refresh();
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (index < MENU_SLOTS && index != TARGET_SLOT) {
            return ItemStack.EMPTY; // list / buttons: handled in clicked()
        }
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == TARGET_SLOT) {
            // item back to the player's inventory
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(Items.ENCHANTED_BOOK)) {
            // shift-click a book in the inventory: store it
            EnchantLibraryBlockEntity.Result result = library.storeBook(
                    stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
            if (result.success()) {
                stack.shrink(1);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(result.message());
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            refresh();
            return ItemStack.EMPTY;
        } else if (EnchantLibraryBlock.isRelevant(stack)) {
            // shift-click an enchantable item: put it in the target slot
            if (!container.getItem(TARGET_SLOT).isEmpty()
                    || !moveItemStackTo(stack, TARGET_SLOT, TARGET_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    // ---------- lifecycle ----------

    @Override
    public boolean stillValid(Player player) {
        BlockPos pos = library.getBlockPos();
        return !library.isRemoved()
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void removed(Player player) {
        // give the item in the target slot back
        ItemStack target = container.getItem(TARGET_SLOT);
        if (!target.isEmpty()) {
            container.setItem(TARGET_SLOT, ItemStack.EMPTY);
            player.getInventory().placeItemBackInInventory(target);
        }
        super.removed(player);
    }
}

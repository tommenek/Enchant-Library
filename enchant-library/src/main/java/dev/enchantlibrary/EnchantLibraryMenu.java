package dev.enchantlibrary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

/**
 * The library menu. Only two real slots live here (store and anvil); the collection itself is
 * drawn by EnchantLibraryScreen from the data this menu syncs, and clicks on it come back as
 * Payloads.LibraryAction packets.
 */
public class EnchantLibraryMenu extends AbstractContainerMenu {

    public static final int STORE_SLOT_X = 200;
    public static final int STORE_SLOT_Y = 33;
    public static final int TARGET_SLOT_X = 200;
    public static final int TARGET_SLOT_Y = 64;
    /** Centred in the 240 wide panel the screen draws. */
    public static final int INVENTORY_X = 40;
    public static final int INVENTORY_Y = 157;
    public static final int HOTBAR_Y = 215;

    private static final int STORE_SLOT = 0;
    private static final int TARGET_SLOT = 1;
    private static final int PLAYER_START = 2;
    private static final int PLAYER_END = PLAYER_START + 36;

    private final EnchantLibraryBlockEntity library;
    private final Level level;
    private final SimpleContainer container = new SimpleContainer(2);
    /** Client side only: the collection as last sent by the server. */
    private List<Payloads.Entry> clientEntries = List.of();

    /** Client constructor, used by the registered MenuType. */
    public EnchantLibraryMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, null, playerInventory.player.level());
    }

    public EnchantLibraryMenu(int containerId, Inventory playerInventory,
                              EnchantLibraryBlockEntity library, Level level) {
        super(ModRegistry.ENCHANT_LIBRARY_MENU, containerId);
        this.library = library;
        this.level = level;

        addSlot(new StoreSlot(container, STORE_SLOT, STORE_SLOT_X, STORE_SLOT_Y));
        addSlot(new TargetSlot(container, TARGET_SLOT, TARGET_SLOT_X, TARGET_SLOT_Y));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }

    }

    // ---------- slots ----------

    /** Drop enchanted books here; they are absorbed into the collection immediately. */
    private static class StoreSlot extends Slot {
        StoreSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(Items.ENCHANTED_BOOK);
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

    // ---------- client side ----------

    public List<Payloads.Entry> clientEntries() {
        return clientEntries;
    }

    public void setClientEntries(List<Payloads.Entry> entries) {
        this.clientEntries = entries;
    }

    /** The item currently in the anvil slot, so the screen can show what a level would cost. */
    public ItemStack targetItem() {
        return container.getItem(TARGET_SLOT);
    }

    // ---------- server side ----------

    /**
     * Builds the collection snapshot and sends it to the player. Must not be called from the
     * constructor: the client has no screen yet at that point and would drop the packet.
     */
    public void syncToClient(ServerPlayer player) {
        List<Payloads.Entry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> stored : library.entries()) {
            Optional<Holder<Enchantment>> found = EnchantLibraryBlockEntity.lookup(level, stored.getKey());
            int points = stored.getValue();
            if (found.isEmpty()) {
                entries.add(new Payloads.Entry(stored.getKey(), stored.getKey(), 0, 0, 0, points, 0));
                continue;
            }
            Holder<Enchantment> holder = found.get();
            int lvl = EnchantLibraryBlockEntity.effectiveLevel(holder, points);
            int cap = EnchantLibraryBlockEntity.levelCap(holder);
            entries.add(new Payloads.Entry(
                    stored.getKey(),
                    Enchantment.getFullname(holder, lvl).getString(),
                    lvl,
                    cap,
                    holder.value().getMaxLevel(),
                    points,
                    lvl < cap ? EnchantLibraryBlockEntity.pointsForLevel(lvl + 1) : 0));
        }
        entries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        ServerPlayNetworking.send(player, new Payloads.LibraryData(entries));
    }

    /** Handles a click the client made on the drawn collection. */
    public void handleAction(ServerPlayer player, Payloads.LibraryAction action) {
        if (library == null) {
            return;
        }
        if (action.action() == Payloads.LibraryAction.EXTRACT) {
            ItemStack book = library.extractBook(level, action.id(), action.level());
            if (!book.isEmpty()) {
                giveOrDrop(player, book);
                player.level().playSound(null, library.getBlockPos(), SoundEvents.BOOK_PAGE_TURN,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        } else if (action.action() == Payloads.LibraryAction.APPLY) {
            ItemStack target = container.getItem(TARGET_SLOT);
            if (target.isEmpty()) {
                player.sendSystemMessage(Component.literal("Put an item in the anvil slot first.")
                        .withStyle(ChatFormatting.RED));
            } else {
                EnchantLibraryBlockEntity.Result result =
                        library.applyTo(level, action.id(), target, player, action.level());
                player.sendSystemMessage(result.message());
                if (result.success()) {
                    player.level().playSound(null, library.getBlockPos(), SoundEvents.ENCHANTMENT_TABLE_USE,
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                container.setChanged();
            }
        }
        syncToClient(player);
        broadcastChanges();
    }

    /** Pulls any books sitting in the store slot into the collection. */
    private void absorbStoreSlot(Player player) {
        if (library == null) {
            return;
        }
        ItemStack stack = container.getItem(STORE_SLOT);
        boolean changed = false;
        while (!stack.isEmpty() && stack.is(Items.ENCHANTED_BOOK)) {
            EnchantLibraryBlockEntity.Result result = library.storeBook(
                    stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(result.message());
            }
            if (!result.success()) {
                break;
            }
            stack.shrink(1);
            changed = true;
        }
        container.setItem(STORE_SLOT, stack.isEmpty() ? ItemStack.EMPTY : stack);
        if (changed && player instanceof ServerPlayer serverPlayer) {
            player.level().playSound(null, library.getBlockPos(), SoundEvents.BOOK_PUT,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            syncToClient(serverPlayer);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == STORE_SLOT || index == TARGET_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(Items.ENCHANTED_BOOK)) {
            if (!moveItemStackTo(stack, STORE_SLOT, STORE_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (EnchantLibraryBlock.isRelevant(stack)) {
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
        absorbStoreSlot(player);
        return original;
    }

    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ContainerInput input, Player player) {
        super.clicked(slotId, button, input, player);
        absorbStoreSlot(player);
        broadcastChanges();
    }

    /** Puts a stack in the player's inventory, dropping what does not fit (same on every 26.x version). */
    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    // ---------- lifecycle ----------

    @Override
    public boolean stillValid(Player player) {
        if (library == null) {
            return true; // client side copy
        }
        BlockPos pos = library.getBlockPos();
        return !library.isRemoved()
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void removed(Player player) {
        for (int slotId : new int[]{TARGET_SLOT, STORE_SLOT}) {
            ItemStack stack = container.getItem(slotId);
            if (!stack.isEmpty()) {
                container.setItem(slotId, ItemStack.EMPTY);
                giveOrDrop(player, stack);
            }
        }
        super.removed(player);
    }
}

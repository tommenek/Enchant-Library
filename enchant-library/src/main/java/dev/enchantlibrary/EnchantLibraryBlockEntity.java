package dev.enchantlibrary;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Stores "points" per enchantment. A stored book of level L adds 2^(L-1) points
 * (two level-N books = one level N+1, like an anvil). The library's level for an
 * enchantment is the highest L with 2^(L-1) <= points, capped at
 * vanillaMax * LEVEL_CAP_MULTIPLIER. Taking a book out removes the points it is worth,
 * so nothing is created or lost.
 */
public class EnchantLibraryBlockEntity extends BlockEntity {

    /** Max points per enchantment (also bounds how many books can drop when the block is broken). */
    public static final int MAX_POINTS = 4096;
    /** Highest level the library reaches = vanilla max level * this. Enchantments with max level 1 stay at 1. */
    public static final int LEVEL_CAP_MULTIPLIER = 2;

    private static final Codec<Map<String, Integer>> POINTS_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT);

    public record Result(boolean success, Component message) {
    }

    private final Map<String, Integer> points = new TreeMap<>();
    private String selected = "";

    public EnchantLibraryBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.ENCHANT_LIBRARY_BE, pos, state);
    }

    // ---------- saving ----------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("Points", POINTS_CODEC, points);
        output.putString("Selected", selected);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        points.clear();
        input.read("Points", POINTS_CODEC).ifPresent(points::putAll);
        selected = input.getStringOr("Selected", "");
    }

    // ---------- helpers ----------

    private static Optional<Holder<Enchantment>> lookup(Level level, String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return Optional.empty();
        }
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .get(ResourceKey.create(Registries.ENCHANTMENT, identifier))
                .map(ref -> (Holder<Enchantment>) ref);
    }

    private static int levelCap(Holder<Enchantment> holder) {
        int max = holder.value().getMaxLevel();
        return max <= 1 ? 1 : Math.min(max * LEVEL_CAP_MULTIPLIER, 30);
    }

    private static int pointsForLevel(int level) {
        return 1 << Math.min(Math.max(level, 1) - 1, 12);
    }

    private static int effectiveLevel(Holder<Enchantment> holder, int pts) {
        if (pts <= 0) {
            return 0;
        }
        int level = 32 - Integer.numberOfLeadingZeros(pts); // floor(log2(pts)) + 1
        return Math.min(level, levelCap(holder));
    }

    private static ItemStack makeBook(Holder<Enchantment> holder, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(holder, level);
        book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return book;
    }

    private String resolveSelected() {
        if (!points.containsKey(selected)) {
            selected = points.isEmpty() ? "" : points.keySet().iterator().next();
        }
        return selected;
    }

    // ---------- actions ----------

    /** Adds every enchantment on the book to the library. */
    public Result storeBook(ItemEnchantments enchants) {
        if (enchants.isEmpty()) {
            return new Result(false, Component.literal("That book has no enchantments."));
        }
        for (Holder<Enchantment> holder : enchants.keySet()) {
            int add = pointsForLevel(enchants.getLevel(holder));
            if (points.getOrDefault(holder.getRegisteredName(), 0) + add > MAX_POINTS) {
                return new Result(false, Component.literal("The library is full for ")
                        .append(Enchantment.getFullname(holder, enchants.getLevel(holder))));
            }
        }

        MutableComponent message = Component.literal("Stored ");
        boolean first = true;
        for (Holder<Enchantment> holder : enchants.keySet()) {
            int level = enchants.getLevel(holder);
            points.merge(holder.getRegisteredName(), pointsForLevel(level), Integer::sum);
            if (!first) {
                message.append(Component.literal(", "));
            }
            message.append(Enchantment.getFullname(holder, level));
            first = false;
        }
        if (selected.isEmpty()) {
            resolveSelected();
        }
        setChanged();
        return new Result(true, message);
    }

    /** Applies the selected enchantment, at the library's current level, to the given item. */
    public Result applySelected(Level level, ItemStack stack) {
        String id = resolveSelected();
        if (id.isEmpty()) {
            return new Result(false, Component.literal("The library is empty. Right-click it with enchanted books."));
        }
        Optional<Holder<Enchantment>> found = lookup(level, id);
        if (found.isEmpty()) {
            return new Result(false, Component.literal("Unknown enchantment: " + id));
        }
        Holder<Enchantment> holder = found.get();
        int lvl = effectiveLevel(holder, points.getOrDefault(id, 0));

        if (!holder.value().canEnchant(stack)) {
            return new Result(false, Component.literal("That enchantment can't go on this item: ")
                    .append(Enchantment.getFullname(holder, lvl)));
        }
        ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (current.getLevel(holder) >= lvl) {
            return new Result(false, Component.literal("Already has ")
                    .append(Enchantment.getFullname(holder, current.getLevel(holder))).append(" or better."));
        }
        for (Holder<Enchantment> other : current.keySet()) {
            if (!other.getRegisteredName().equals(id) && !Enchantment.areCompatible(holder, other)) {
                return new Result(false, Component.literal("Conflicts with ")
                        .append(Enchantment.getFullname(other, current.getLevel(other))));
            }
        }

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        mutable.set(holder, lvl);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        return new Result(true, Component.literal("Applied ").append(Enchantment.getFullname(holder, lvl)));
    }

    /** Removes the points for the highest possible book of the selected enchantment and returns that book. */
    public ItemStack extractSelectedBook(Level level) {
        String id = resolveSelected();
        if (id.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Optional<Holder<Enchantment>> found = lookup(level, id);
        if (found.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Holder<Enchantment> holder = found.get();
        int pts = points.getOrDefault(id, 0);
        int lvl = effectiveLevel(holder, pts);
        if (lvl <= 0) {
            return ItemStack.EMPTY;
        }

        int remaining = pts - pointsForLevel(lvl);
        if (remaining <= 0) {
            points.remove(id);
            resolveSelected();
        } else {
            points.put(id, remaining);
        }
        setChanged();
        return makeBook(holder, lvl);
    }

    public Component cycleSelection(Level level) {
        if (points.isEmpty()) {
            return Component.literal("The library is empty.");
        }
        List<String> keys = new ArrayList<>(points.keySet());
        int index = keys.indexOf(selected);
        selected = keys.get((index + 1) % keys.size());
        setChanged();
        return describeSelection(level);
    }

    public Component describeSelection(Level level) {
        String id = resolveSelected();
        if (id.isEmpty()) {
            return Component.literal("The library is empty.");
        }
        int pts = points.getOrDefault(id, 0);
        Optional<Holder<Enchantment>> found = lookup(level, id);
        if (found.isEmpty()) {
            return Component.literal("Selected: " + id + " (unknown enchantment, " + pts + " pts)");
        }
        int lvl = effectiveLevel(found.get(), pts);
        return Component.literal("Selected: ")
                .append(Enchantment.getFullname(found.get(), lvl))
                .append(Component.literal("  (" + pts + " pts, " + points.size() + " enchantments stored)"));
    }

    /** Drops everything as books when the block is broken. */
    public void dropContents(Level level, BlockPos pos) {
        for (Map.Entry<String, Integer> entry : points.entrySet()) {
            Optional<Holder<Enchantment>> found = lookup(level, entry.getKey());
            if (found.isEmpty()) {
                continue;
            }
            Holder<Enchantment> holder = found.get();
            int cap = levelCap(holder);
            int remaining = entry.getValue();
            while (remaining > 0) {
                int lvl = Math.min(32 - Integer.numberOfLeadingZeros(remaining), cap);
                remaining -= pointsForLevel(lvl);
                Block.popResource(level, pos, makeBook(holder, lvl));
            }
        }
        points.clear();
    }
}

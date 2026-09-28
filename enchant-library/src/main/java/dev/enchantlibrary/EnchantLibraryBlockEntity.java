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
import net.minecraft.world.entity.player.Player;
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
 * Stores "points" per enchantment.
 *
 * Tiers: a book of level L is worth TIER_MULTIPLIER^(L-1) points, so TIER_MULTIPLIER books of
 * level N are worth one book of level N+1. The library's level for an enchantment is the highest
 * L whose price is covered by the stored points, capped at vanillaMax * LEVEL_CAP_MULTIPLIER.
 * Taking a book out removes the points it is worth, so books are never created or lost.
 *
 * XP: putting an enchantment on an item costs XP_COST_FACTOR * level^2 experience POINTS
 * (not levels). Upgrading an existing enchantment only costs the difference.
 */
public class EnchantLibraryBlockEntity extends BlockEntity {

    // ---- balance settings: tweak these ----
    /** How many books of one tier make the next tier (2 = anvil-like, 3 = default, 4 = harsh). */
    public static final int TIER_MULTIPLIER = 3;
    /** Highest level the library reaches = vanilla max level * this. Enchantments with max level 1 stay at 1. */
    public static final int LEVEL_CAP_MULTIPLIER = 2;
    /** Hard upper limit for any level (keeps the point math inside an int). */
    public static final int ABSOLUTE_LEVEL_CAP = 15;
    /** XP points needed to put level L on an item = XP_COST_FACTOR * L * L (1 XP level is roughly 7-20 points). */
    public static final int XP_COST_FACTOR = 10;
    /** How many top-level books' worth of points one enchantment can hold. */
    public static final int STORAGE_TOP_BOOKS = 4;

    private static final Codec<Map<String, Integer>> POINTS_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT);

    public record Result(boolean success, Component message) {
    }

    private final Map<String, Integer> points = new TreeMap<>();

    public EnchantLibraryBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.ENCHANT_LIBRARY_BE, pos, state);
    }

    // ---------- saving ----------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("Points", POINTS_CODEC, points);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        points.clear();
        input.read("Points", POINTS_CODEC).ifPresent(points::putAll);
    }

    // ---------- helpers (also used by the menu) ----------

    public static Optional<Holder<Enchantment>> lookup(Level level, String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return Optional.empty();
        }
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .get(ResourceKey.create(Registries.ENCHANTMENT, identifier))
                .map(ref -> (Holder<Enchantment>) ref);
    }

    public static int levelCap(Holder<Enchantment> holder) {
        int max = holder.value().getMaxLevel();
        return max <= 1 ? 1 : Math.min(max * LEVEL_CAP_MULTIPLIER, ABSOLUTE_LEVEL_CAP);
    }

    /** Points a book of this level is worth (and the points the library needs to reach that level). */
    public static int pointsForLevel(int level) {
        long value = 1;
        for (int i = 1; i < Math.min(Math.max(level, 1), ABSOLUTE_LEVEL_CAP); i++) {
            value *= TIER_MULTIPLIER;
        }
        return (int) Math.min(value, Integer.MAX_VALUE / (STORAGE_TOP_BOOKS + 1));
    }

    /** Points a stored book adds; books above the library's level cap count as cap-level books. */
    public static int pointsForBook(Holder<Enchantment> holder, int level) {
        return pointsForLevel(Math.min(level, levelCap(holder)));
    }

    /** Most points one enchantment can hold. */
    public static int maxPoints(Holder<Enchantment> holder) {
        long top = (long) pointsForLevel(levelCap(holder)) * STORAGE_TOP_BOOKS;
        return (int) Math.max(top, 64);
    }

    public static int effectiveLevel(Holder<Enchantment> holder, int pts) {
        if (pts <= 0) {
            return 0;
        }
        int cap = levelCap(holder);
        int level = 1;
        while (level < cap && pointsForLevel(level + 1) <= pts) {
            level++;
        }
        return level;
    }

    /** XP points needed to have this level of an enchantment applied. */
    public static int xpCost(int level) {
        return XP_COST_FACTOR * level * level;
    }

    private static int xpPointsAtLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        } else if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        }
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    /** The player's total experience in points (levels and the progress bar together). */
    public static int totalXpPoints(Player player) {
        return xpPointsAtLevel(player.experienceLevel)
                + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    private static ItemStack makeBook(Holder<Enchantment> holder, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(holder, level);
        book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return book;
    }

    private static Result fail(String text) {
        return new Result(false, Component.literal(text));
    }

    /** A snapshot of (enchantment id, points), sorted by id. */
    public List<Map.Entry<String, Integer>> entries() {
        List<Map.Entry<String, Integer>> list = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : points.entrySet()) {
            list.add(Map.entry(entry.getKey(), entry.getValue()));
        }
        return list;
    }

    public int pointsOf(String id) {
        return points.getOrDefault(id, 0);
    }

    // ---------- actions ----------

    /** Adds every enchantment on the book to the library. */
    public Result storeBook(ItemEnchantments enchants) {
        if (enchants.isEmpty()) {
            return fail("That book has no enchantments.");
        }
        for (Holder<Enchantment> holder : enchants.keySet()) {
            int add = pointsForBook(holder, enchants.getLevel(holder));
            if (points.getOrDefault(holder.getRegisteredName(), 0) + add > maxPoints(holder)) {
                return new Result(false, Component.literal("The library is full for ")
                        .append(Enchantment.getFullname(holder, enchants.getLevel(holder))));
            }
        }

        MutableComponent message = Component.literal("Stored ");
        boolean first = true;
        for (Holder<Enchantment> holder : enchants.keySet()) {
            int level = enchants.getLevel(holder);
            points.merge(holder.getRegisteredName(), pointsForBook(holder, level), Integer::sum);
            if (!first) {
                message.append(Component.literal(", "));
            }
            message.append(Enchantment.getFullname(holder, level));
            first = false;
        }
        setChanged();
        return new Result(true, message);
    }

    /**
     * Applies the enchantment, at the library's current level, to the given item and charges the
     * player XP points (free in creative). Upgrading an enchantment only costs the difference.
     */
    public Result applyTo(Level level, String id, ItemStack stack, Player player) {
        int pts = pointsOf(id);
        if (pts <= 0) {
            return fail("That enchantment is no longer stored.");
        }
        Optional<Holder<Enchantment>> found = lookup(level, id);
        if (found.isEmpty()) {
            return fail("Unknown enchantment: " + id);
        }
        Holder<Enchantment> holder = found.get();
        int lvl = effectiveLevel(holder, pts);

        if (!holder.value().canEnchant(stack)) {
            return new Result(false, Component.literal("That can't go on this item: ")
                    .append(Enchantment.getFullname(holder, lvl)));
        }
        ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int oldLevel = current.getLevel(holder);
        if (oldLevel >= lvl) {
            return new Result(false, Component.literal("Already has ")
                    .append(Enchantment.getFullname(holder, oldLevel)).append(" or better."));
        }
        for (Holder<Enchantment> other : current.keySet()) {
            if (!other.getRegisteredName().equals(id) && !Enchantment.areCompatible(holder, other)) {
                return new Result(false, Component.literal("Conflicts with ")
                        .append(Enchantment.getFullname(other, current.getLevel(other))));
            }
        }

        int cost = xpCost(lvl) - xpCost(oldLevel);
        if (!player.isCreative()) {
            int have = totalXpPoints(player);
            if (have < cost) {
                return fail("Needs " + cost + " XP points (you have " + have + ").");
            }
            player.giveExperiencePoints(-cost);
        }

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        mutable.set(holder, lvl);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        return new Result(true, Component.literal("Applied ")
                .append(Enchantment.getFullname(holder, lvl))
                .append(Component.literal(" (-" + cost + " XP)")));
    }

    /** Removes the points for the highest possible book of this enchantment and returns that book. */
    public ItemStack extractBook(Level level, String id) {
        Optional<Holder<Enchantment>> found = lookup(level, id);
        if (found.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Holder<Enchantment> holder = found.get();
        int pts = pointsOf(id);
        int lvl = effectiveLevel(holder, pts);
        if (lvl <= 0) {
            return ItemStack.EMPTY;
        }

        int remaining = pts - pointsForLevel(lvl);
        if (remaining <= 0) {
            points.remove(id);
        } else {
            points.put(id, remaining);
        }
        setChanged();
        return makeBook(holder, lvl);
    }

    /** Drops everything as books when the block is broken. */
    public void dropContents(Level level, BlockPos pos) {
        for (Map.Entry<String, Integer> entry : points.entrySet()) {
            Optional<Holder<Enchantment>> found = lookup(level, entry.getKey());
            if (found.isEmpty()) {
                continue;
            }
            Holder<Enchantment> holder = found.get();
            int remaining = entry.getValue();
            while (remaining > 0) {
                int lvl = effectiveLevel(holder, remaining);
                remaining -= pointsForLevel(lvl);
                Block.popResource(level, pos, makeBook(holder, lvl));
            }
        }
        points.clear();
    }
}

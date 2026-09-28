package dev.enchantlibrary;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Controls:
 *  - Right-click with an enchanted book      -> store it in the library
 *  - Right-click with an enchantable item    -> apply the selected enchantment (at the library's level)
 *  - Sneak + right-click with an empty hand  -> cycle the selected enchantment
 *  - Right-click with an empty hand          -> take out a book of the selected enchantment
 */
public class EnchantLibraryBlock extends Block implements EntityBlock {

    public EnchantLibraryBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnchantLibraryBlockEntity(pos, state);
    }

    private static boolean isRelevant(ItemStack stack) {
        if (stack.is(Items.ENCHANTED_BOOK)) {
            return true;
        }
        return stack.isEnchantable()
                || !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !isRelevant(stack)) {
            // let the normal flow continue (empty-hand use, block placing, etc.)
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof EnchantLibraryBlockEntity library)) {
            return InteractionResult.PASS;
        }

        EnchantLibraryBlockEntity.Result result;
        if (stack.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            result = library.storeBook(stored);
            if (result.success()) {
                stack.shrink(1);
            }
        } else {
            result = library.applySelected(level, stack);
        }

        player.displayClientMessage(result.message(), true);
        if (result.success()) {
            level.playSound((Player) null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        // only react to a truly empty hand, so placing blocks against the library still works
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof EnchantLibraryBlockEntity library)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            player.displayClientMessage(library.cycleSelection(level), true);
            return InteractionResult.SUCCESS;
        }

        ItemStack book = library.extractSelectedBook(level);
        if (book.isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "Nothing to take. Sneak + right-click with an empty hand to select an enchantment."), true);
        } else {
            player.getInventory().placeItemBackInInventory(book);
            level.playSound((Player) null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.displayClientMessage(library.describeSelection(level), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EnchantLibraryBlockEntity library) {
            library.dropContents(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}

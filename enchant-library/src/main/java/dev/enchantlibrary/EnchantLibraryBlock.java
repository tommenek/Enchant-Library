package dev.enchantlibrary;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
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

/** Right-click the block to open the library screen (see EnchantLibraryMenu). */
public class EnchantLibraryBlock extends Block implements EntityBlock {

    public EnchantLibraryBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnchantLibraryBlockEntity(pos, state);
    }

    /** True for enchanted books and for anything that can be (or already is) enchanted. */
    static boolean isRelevant(ItemStack stack) {
        if (stack.is(Items.ENCHANTED_BOOK)) {
            return true;
        }
        return stack.isEnchantable()
                || !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof EnchantLibraryBlockEntity library) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new EnchantLibraryMenu(containerId, inventory, library, level),
                    this.getName()));
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

package dev.enchantlibrary;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModRegistry {
    public static final Block ENCHANT_LIBRARY;
    public static final BlockItem ENCHANT_LIBRARY_ITEM;
    public static final BlockEntityType<EnchantLibraryBlockEntity> ENCHANT_LIBRARY_BE;
    public static final MenuType<EnchantLibraryMenu> ENCHANT_LIBRARY_MENU;

    static {
        Identifier id = EnchantLibraryMod.id("enchant_library");
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);

        ENCHANT_LIBRARY = Registry.register(BuiltInRegistries.BLOCK, blockKey,
                new EnchantLibraryBlock(BlockBehaviour.Properties.of()
                        .strength(3.0F)
                        .sound(SoundType.WOOD)
                        .setId(blockKey)));

        ENCHANT_LIBRARY_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(ENCHANT_LIBRARY, new Item.Properties()
                        .setId(itemKey)
                        .useBlockDescriptionPrefix()));

        ENCHANT_LIBRARY_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id,
                FabricBlockEntityTypeBuilder.create(EnchantLibraryBlockEntity::new, ENCHANT_LIBRARY).build());

        ENCHANT_LIBRARY_MENU = Registry.register(BuiltInRegistries.MENU, id,
                new MenuType<>(EnchantLibraryMenu::new, FeatureFlags.VANILLA_SET));
    }

    /** Forces the static initializer above to run. */
    public static void init() {
    }

    private ModRegistry() {
    }
}

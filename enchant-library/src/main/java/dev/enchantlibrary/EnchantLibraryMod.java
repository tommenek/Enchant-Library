package dev.enchantlibrary;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;

public class EnchantLibraryMod implements ModInitializer {
    public static final String MOD_ID = "enchantlibrary";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModRegistry.init();
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(output -> output.accept(ModRegistry.ENCHANT_LIBRARY_ITEM));

        PayloadTypeRegistry.clientboundPlay().register(Payloads.LibraryData.TYPE, Payloads.LibraryData.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.LibraryAction.TYPE, Payloads.LibraryAction.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Payloads.LibraryAction.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof EnchantLibraryMenu menu) {
                    menu.handleAction(context.player(), payload);
                }
            });
        });
    }
}

package dev.enchantlibrary;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

public class EnchantLibraryClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModRegistry.ENCHANT_LIBRARY_MENU, EnchantLibraryScreen::new);

        ClientPlayNetworking.registerGlobalReceiver(Payloads.LibraryData.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null
                        && context.client().player.containerMenu instanceof EnchantLibraryMenu menu) {
                    menu.setClientEntries(payload.entries());
                }
            });
        });
    }
}

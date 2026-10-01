package dev.enchantlibrary;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;

public class EnchantLibraryClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModRegistry.ENCHANT_LIBRARY_MENU, EnchantLibraryScreen::new);

        // creative search matches tooltip text, so this makes "@enchantlibrary" find the block
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.is(ModRegistry.ENCHANT_LIBRARY_ITEM)) {
                lines.add(Component.literal("@enchantlibrary").withStyle(ChatFormatting.DARK_GRAY));
            }
        });

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

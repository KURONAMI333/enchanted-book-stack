package com.kuronami.enchantedbookstack.client;

import com.kuronami.enchantedbookstack.client.screen.BookStackScreen;
import com.kuronami.enchantedbookstack.registry.EBSMenuTypes;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Enchanted Book Stack — Fabric client entry ({@link ClientModInitializer})。
 *
 * <p>MenuType ↔ Screen の紐付け。 fabric.mod.json の "entrypoints.client" で参照される。
 */
public class EnchantedBookStackFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(EBSMenuTypes.POUCH_MENU, BookStackScreen::new);
    }
}

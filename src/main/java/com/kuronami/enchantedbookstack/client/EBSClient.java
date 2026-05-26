package com.kuronami.enchantedbookstack.client;

import com.kuronami.enchantedbookstack.EnchantedBookStack;
import com.kuronami.enchantedbookstack.client.screen.BookStackScreen;
import com.kuronami.enchantedbookstack.menu.BookStackMenu;
import com.kuronami.enchantedbookstack.registry.EBSMenuTypes;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * EBS の client-side セットアップ ({@code Dist.CLIENT} 限定)。
 *
 * <p>{@link BookStackMenu} → {@link BookStackScreen} の紐付けを {@link RegisterMenuScreensEvent} で登録。
 */
@EventBusSubscriber(modid = EnchantedBookStack.MODID, value = Dist.CLIENT)
public final class EBSClient {

    private EBSClient() {}

    /** BookStackMenu (server) ↔ BookStackScreen (client) のひも付け。 */
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(EBSMenuTypes.POUCH_MENU.get(), BookStackScreen::new);
    }
}

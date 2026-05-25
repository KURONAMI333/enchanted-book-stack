package com.kuronami.portableenchantedbookshelf.client;

import com.kuronami.portableenchantedbookshelf.PortableEnchantedBookshelf;
import com.kuronami.portableenchantedbookshelf.client.screen.PouchScreen;
import com.kuronami.portableenchantedbookshelf.registry.PEBMenuTypes;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * PEB の client-side セットアップ ({@code Dist.CLIENT} 限定)。
 *
 * <p>Phase 2 (AE2 viewport): {@link PouchScreen} を {@link PEBMenuTypes#POUCH_MENU} に紐付け。
 * Hover preview ({@code TooltipComponent} factory) は Phase 2 後半で復活させる予定。
 */
@EventBusSubscriber(modid = PortableEnchantedBookshelf.MODID, value = Dist.CLIENT)
public final class PEBClient {

    private PEBClient() {}

    /** PouchMenu (server) ↔ PouchScreen (client) のひも付け。 */
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(PEBMenuTypes.POUCH_MENU.get(), PouchScreen::new);
    }
}

package com.kuronami.portableenchantedbookshelf.client;

import com.kuronami.portableenchantedbookshelf.PortableEnchantedBookshelf;
import com.kuronami.portableenchantedbookshelf.client.screen.PouchScreen;
import com.kuronami.portableenchantedbookshelf.client.tooltip.ClientPouchTooltip;
import com.kuronami.portableenchantedbookshelf.client.tooltip.PouchTooltip;
import com.kuronami.portableenchantedbookshelf.registry.PEBMenuTypes;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * PEB の client-side セットアップ。
 *
 * <p>{@code @EventBusSubscriber(value = Dist.CLIENT)} で **dedicated server では一切ロードされない**
 * → client 専用クラス ({@link net.minecraft.client.gui.Font} 等) を含んでも安全。
 */
@EventBusSubscriber(modid = PortableEnchantedBookshelf.MODID, value = Dist.CLIENT)
public final class PEBClient {

    private PEBClient() {}

    /**
     * {@link PouchTooltip} (server data) → {@link ClientPouchTooltip} (client renderer) の
     * factory を登録。vanilla の tooltip システムが {@code Item#getTooltipImage()} で
     * {@code PouchTooltip} を受け取ると、この factory で client renderer を生成して描画する。
     */
    @SubscribeEvent
    public static void onRegisterClientTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(PouchTooltip.class, ClientPouchTooltip::new);
    }

    /** PouchMenu (server) ↔ PouchScreen (client) のひも付け。 */
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(PEBMenuTypes.POUCH_MENU.get(), PouchScreen::new);
    }
}

package com.kuronami.enchantedbookstack.client;

import com.kuronami.enchantedbookstack.EnchantedBookStack;
import com.kuronami.enchantedbookstack.client.screen.BookStackScreen;
import com.kuronami.enchantedbookstack.menu.BookStackMenu;
import com.kuronami.enchantedbookstack.registry.EBSMenuTypes;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * EBS の client-side セットアップ ({@code Dist.CLIENT} 限定)。
 *
 * <p>{@link BookStackMenu} → {@link BookStackScreen} の紐付けを Forge 1.21.1 の
 * {@link FMLClientSetupEvent} 内で {@link MenuScreens#register} 経由で登録。
 */
@Mod.EventBusSubscriber(modid = EnchantedBookStack.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EBSClient {

    private EBSClient() {}

    /** BookStackMenu (server) ↔ BookStackScreen (client) のひも付け。 */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MenuScreens.register(EBSMenuTypes.POUCH_MENU.get(), BookStackScreen::new)
        );
    }
}

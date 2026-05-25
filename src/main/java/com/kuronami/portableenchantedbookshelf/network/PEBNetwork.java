package com.kuronami.portableenchantedbookshelf.network;

import com.kuronami.portableenchantedbookshelf.PortableEnchantedBookshelf;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * PEB の network payload 登録ハブ。
 *
 * <p>{@code @EventBusSubscriber} (mod bus) で {@link RegisterPayloadHandlersEvent} を listen し、
 * {@link ExtractBookPayload} 等の client→server payload を登録する。
 */
@EventBusSubscriber(modid = PortableEnchantedBookshelf.MODID)
public final class PEBNetwork {

    private PEBNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                ExtractBookPayload.TYPE,
                ExtractBookPayload.STREAM_CODEC,
                ExtractBookPayload::handle
        );
    }
}

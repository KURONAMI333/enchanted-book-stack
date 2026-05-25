package com.kuronami.portableenchantedbookshelf.network;

import com.kuronami.portableenchantedbookshelf.PortableEnchantedBookshelf;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * PEB の network payload 登録ハブ。
 *
 * <p>Phase 2 (AE2 viewport pattern) で使う client → server packet を登録:
 * <ul>
 *   <li>{@link ExtractByIdxPayload}: viewport slot click → handler slot から N 個取り出し</li>
 *   <li>{@link InsertCarriedPayload}: 空 viewport slot click → carried book を最初の空 slot へ</li>
 * </ul>
 *
 * <p>内容物 sync は別経路 — PEB stack の {@code DataComponents.CONTAINER} は
 * vanilla の slot sync で client にも自動届く ({@code PouchMenu.onHandlerChanged} 経由)。
 */
@EventBusSubscriber(modid = PortableEnchantedBookshelf.MODID)
public final class PEBNetwork {

    private PEBNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                ExtractByIdxPayload.TYPE,
                ExtractByIdxPayload.STREAM_CODEC,
                ExtractByIdxPayload::handle
        );
        registrar.playToServer(
                InsertCarriedPayload.TYPE,
                InsertCarriedPayload.STREAM_CODEC,
                InsertCarriedPayload::handle
        );
    }
}

package com.kuronami.portableenchantedbookshelf.network;

import com.kuronami.portableenchantedbookshelf.PortableEnchantedBookshelf;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * PEB の network payload 登録ハブ。
 *
 * <p>Phase 2 (AE2 viewport): client/server 内容物 sync + insert / extract packet を登録予定。
 * 現在は skeleton (payload 未追加)。
 */
@EventBusSubscriber(modid = PortableEnchantedBookshelf.MODID)
public final class PEBNetwork {

    private PEBNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // TODO Phase 2: SyncContentsPayload (S→C), InsertCarriedPayload (C→S),
        //               ExtractByIdxPayload (C→S), TakeAllByKindPayload (C→S)
        // 現在は payload 無し
        // registrar 未使用警告抑制
        if (registrar == null) throw new IllegalStateException("registrar must not be null");
    }
}

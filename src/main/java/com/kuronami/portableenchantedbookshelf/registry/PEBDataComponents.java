package com.kuronami.portableenchantedbookshelf.registry;

import com.kuronami.portableenchantedbookshelf.PortableEnchantedBookshelf;
import com.kuronami.portableenchantedbookshelf.data.PouchContents;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * PEB が ItemStack に持たせる {@link DataComponentType} の登録ハブ。
 *
 * <p>1.21 標準の DataComponent API を使う (NBT 直書きしない)。Codec + StreamCodec で
 * persistent / network-synced の両方を満たす。
 */
public final class PEBDataComponents {

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PortableEnchantedBookshelf.MODID);

    /**
     * PEB の中身 — 保管されたエンチャント本の集約リスト。
     * Stack を pick up したら client にも reflective に届くよう {@code networkSynchronized}。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PouchContents>> POUCH_CONTENTS =
            COMPONENTS.registerComponentType(
                    "pouch_contents",
                    builder -> builder
                            .persistent(PouchContents.CODEC)
                            .networkSynchronized(PouchContents.STREAM_CODEC)
            );

    private PEBDataComponents() {}
}

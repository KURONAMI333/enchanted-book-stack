package com.kuronami.portableenchantedbookshelf.registry;

import com.kuronami.portableenchantedbookshelf.PortableEnchantedBookshelf;
import com.kuronami.portableenchantedbookshelf.item.PortableEnchantedBookshelfItem;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * PEB の Item 登録ハブ。
 *
 * <p>v0.1.0 では単一 item ({@code portable_enchanted_bookshelf}) のみ。tier 廃止 (kura 判断:
 * アイテム 1 種限定なのでバランス崩壊しない)、容量無制限。
 */
public final class PEBItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(PortableEnchantedBookshelf.MODID);

    /** 唯一の PEB アイテム。stack 不可 (NBT に内容物を持つので)。 */
    public static final DeferredItem<PortableEnchantedBookshelfItem> PORTABLE_ENCHANTED_BOOKSHELF =
            ITEMS.register(
                    "portable_enchanted_bookshelf",
                    () -> new PortableEnchantedBookshelfItem(new Item.Properties().stacksTo(1))
            );

    private PEBItems() {}
}

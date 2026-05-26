package com.kuronami.enchantedbookstack.registry;

import com.kuronami.enchantedbookstack.EnchantedBookStack;
import com.kuronami.enchantedbookstack.item.EnchantedBookStackItem;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * EBS の Item 登録ハブ。
 *
 * <p>v0.1.0 では単一 item ({@code enchanted_book_stack}) のみ。tier 廃止 (kura 判断:
 * アイテム 1 種限定なのでバランス崩壊しない)、容量無制限。
 */
public final class EBSItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(EnchantedBookStack.MODID);

    /** 唯一の EBS アイテム。stack 不可 (NBT に内容物を持つので)。
     *  エンチャント glint を常時表示 ({@link DataComponents#ENCHANTMENT_GLINT_OVERRIDE})。 */
    public static final DeferredItem<EnchantedBookStackItem> PORTABLE_ENCHANTED_BOOKSHELF =
            ITEMS.register(
                    "enchanted_book_stack",
                    () -> new EnchantedBookStackItem(
                            new Item.Properties()
                                    .stacksTo(1)
                                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                    )
            );

    private EBSItems() {}
}

package com.kuronami.enchantedbookstack.registry;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.CreativeModeTabs;

/**
 * EBS をクリエイティブタブに追加 (Fabric 流: ItemGroupEvents)。
 */
public final class EBSTabs {

    private EBSTabs() {}

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(EBSItems.ENCHANTED_BOOK_STACK));
    }
}

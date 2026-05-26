package com.kuronami.enchantedbookstack.registry;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;

/**
 * EBS のクリエイティブタブ統合。
 *
 * <p>独自タブは作らない (item 1 個しかない、過剰)。vanilla の {@code TOOLS_AND_UTILITIES}
 * タブに追加。「持ち歩く道具」カテゴリが文脈的に最も自然。
 *
 * <p>将来 EBS Mini / Wall-mount EBS 等を追加するなら独自タブ復活を検討。
 */
public final class EBSTabs {

    private EBSTabs() {}

    /** vanilla TOOLS_AND_UTILITIES タブに EBS を追加。 */
    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(EBSItems.PORTABLE_ENCHANTED_BOOKSHELF);
        }
    }
}

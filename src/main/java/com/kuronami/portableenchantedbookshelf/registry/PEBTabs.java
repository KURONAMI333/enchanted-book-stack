package com.kuronami.portableenchantedbookshelf.registry;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * PEB のクリエイティブタブ統合。
 *
 * <p>独自タブは作らない (item 1 個しかない、過剰)。vanilla の {@code TOOLS_AND_UTILITIES}
 * タブに追加。「持ち歩く道具」カテゴリが文脈的に最も自然。
 *
 * <p>将来 PEB Mini / Wall-mount PEB 等を追加するなら独自タブ復活を検討。
 */
public final class PEBTabs {

    private PEBTabs() {}

    /** vanilla TOOLS_AND_UTILITIES タブに PEB を追加。 */
    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(PEBItems.PORTABLE_ENCHANTED_BOOKSHELF);
        }
    }
}

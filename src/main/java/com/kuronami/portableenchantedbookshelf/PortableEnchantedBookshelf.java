package com.kuronami.portableenchantedbookshelf;

import com.kuronami.portableenchantedbookshelf.registry.PEBItems;
import com.kuronami.portableenchantedbookshelf.registry.PEBMenuTypes;
import com.kuronami.portableenchantedbookshelf.registry.PEBTabs;
import com.kuronami.portableenchantedbookshelf.registry.PEBTrades;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

import org.slf4j.Logger;

/**
 * Portable Enchanted Bookshelf — entry point.
 *
 * <p>持ち歩けるエンチャント本箱 (v4)。 vanilla shulker box と完全同等の 27 slot UI、 ただし
 * enchanted_book のみ受け入れ + 他の shulker/backpack に入れられる (nest 可能) = スペース削減。
 *
 * <p>内容物は vanilla {@code DataComponents.CONTAINER} ({@code ItemContainerContents})。
 * Menu は vanilla {@code SlotItemHandler} で標準 slot interaction (shift-click / drag /
 * hover tooltip / 数字キー swap) を全て vanilla 任せ。
 */
@Mod(PortableEnchantedBookshelf.MODID)
public class PortableEnchantedBookshelf {
    public static final String MODID = "portableenchantedbookshelf";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PortableEnchantedBookshelf(IEventBus modBus, ModContainer container) {
        // Item 登録
        PEBItems.ITEMS.register(modBus);

        // MenuType 登録 (右クリックで開く GUI)
        PEBMenuTypes.MENU_TYPES.register(modBus);

        // Creative tab 統合 (vanilla TOOLS_AND_UTILITIES に追加)
        modBus.addListener(PEBTabs::addCreative);

        // 司書 (Librarian) Level 4 trade に PEB 追加 (game bus イベント)
        NeoForge.EVENT_BUS.addListener(PEBTrades::onRegisterTrades);

        LOGGER.info("Portable Enchanted Bookshelf v4 — shulker-like, enchanted_book only, nestable.");
    }
}

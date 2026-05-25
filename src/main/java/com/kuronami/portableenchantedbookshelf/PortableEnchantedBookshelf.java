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
 * <p>持ち歩けるエンチャント本箱。 vanilla shulker box / chest を超える「**無限容量 + 検索 + scroll**」
 * 体験を AE2 ME terminal の viewport pattern で実装。
 *
 * <p>内容物は vanilla {@code DataComponents.CONTAINER} ({@code ItemContainerContents}) に保持、
 * server は最大 256 slot の ItemStackHandler、 client は AE2 流儀の virtual slot viewport
 * (9×6=54 visible slots + scroll) で全 entry を navigate。
 *
 * <p>詳細仕様: {@code _docs/SPEC.md} (Phase 2 = B 案 AE2 viewport pattern)
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

        LOGGER.info("Portable Enchanted Bookshelf — viewport pattern (AE2-style) loaded.");
    }
}

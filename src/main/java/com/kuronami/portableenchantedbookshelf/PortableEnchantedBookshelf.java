package com.kuronami.portableenchantedbookshelf;

import com.kuronami.portableenchantedbookshelf.registry.PEBDataComponents;
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
 * <p>持ち歩ける本棚アイテム。エンチャント本だけを 1 スロットに無制限保管。
 * tree 展開 GUI で「エンチャント種類 → level 別 breakdown」検索、modded enchantment
 * / level 上限拡張 mod にも動的対応 (ハードコード一切無し)。
 *
 * <p>司書交易 (Level 4 Expert) で入手可能、craft も可能。NeoForge 1.21.1 first release、
 * 後で Forge/Fabric × 1.21.1/1.20.1 へ水平展開。
 *
 * <p>詳細仕様: {@code _docs/SPEC.md}
 */
@Mod(PortableEnchantedBookshelf.MODID)
public class PortableEnchantedBookshelf {
    public static final String MODID = "portableenchantedbookshelf";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PortableEnchantedBookshelf(IEventBus modBus, ModContainer container) {
        // DataComponent 登録 (NBT 設計)
        PEBDataComponents.COMPONENTS.register(modBus);

        // Item 登録
        PEBItems.ITEMS.register(modBus);

        // MenuType 登録 (右クリックで開く GUI)
        PEBMenuTypes.MENU_TYPES.register(modBus);

        // Creative tab 統合 (vanilla TOOLS_AND_UTILITIES に追加)
        modBus.addListener(PEBTabs::addCreative);

        // 司書 (Librarian) Level 4 trade に PEB 追加 (game bus イベント)
        NeoForge.EVENT_BUS.addListener(PEBTrades::onRegisterTrades);

        LOGGER.info("Portable Enchanted Bookshelf — ready to swallow your librarian farm output.");
    }
}

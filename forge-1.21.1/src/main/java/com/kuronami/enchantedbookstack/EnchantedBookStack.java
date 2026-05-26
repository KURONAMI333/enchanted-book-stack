package com.kuronami.enchantedbookstack;

import com.kuronami.enchantedbookstack.registry.EBSItems;
import com.kuronami.enchantedbookstack.registry.EBSMenuTypes;
import com.kuronami.enchantedbookstack.registry.EBSTabs;
import com.kuronami.enchantedbookstack.registry.EBSTrades;
import com.mojang.logging.LogUtils;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.slf4j.Logger;

/**
 * Enchanted Book Stack — entry point.
 *
 * <p>エンチャント本束ねて持ち歩く 1 アイテム。 54 slot (9×6) の vanilla 大型チェスト UI、
 * enchanted_book のみ受け入れ + 他の shulker/backpack に入れられる (nest 可能) ＝ スペース削減。
 *
 * <p>内容物は vanilla {@code DataComponents.CONTAINER} ({@code ItemContainerContents})。
 * Menu は vanilla {@code SlotItemHandler} で標準 slot interaction (shift-click / drag /
 * hover tooltip / 数字キー swap) を全て vanilla 任せ。
 */
@Mod(EnchantedBookStack.MODID)
public class EnchantedBookStack {
    public static final String MODID = "enchantedbookstack";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EnchantedBookStack(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        // Item 登録
        EBSItems.ITEMS.register(modBus);

        // MenuType 登録 (右クリックで開く GUI)
        EBSMenuTypes.MENU_TYPES.register(modBus);

        // Creative tab 統合 (vanilla TOOLS_AND_UTILITIES に追加)
        modBus.addListener(EBSTabs::addCreative);

        // 司書 (Librarian) Level 4 trade に EBS 追加 (game bus イベント)
        MinecraftForge.EVENT_BUS.addListener(EBSTrades::onRegisterTrades);

        LOGGER.info("Enchanted Book Stack — 54 slot, enchanted_book only, nestable.");
    }
}

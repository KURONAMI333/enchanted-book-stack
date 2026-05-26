package com.kuronami.enchantedbookstack;

import com.kuronami.enchantedbookstack.registry.EBSItems;
import com.kuronami.enchantedbookstack.registry.EBSMenuTypes;
import com.kuronami.enchantedbookstack.registry.EBSTabs;
import com.kuronami.enchantedbookstack.registry.EBSTrades;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enchanted Book Stack — Fabric 1.21.1 entry point ({@link ModInitializer}).
 *
 * <p>54 slot のエンチャント本専用ストレージアイテム。 vanilla {@code DataComponents.CONTAINER}
 * に内容物を保存、他の shulker/backpack に nest 可能。
 */
public class EnchantedBookStackFabric implements ModInitializer {

    public static final String MODID = "enchantedbookstack";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        EBSItems.register();
        EBSMenuTypes.register();
        EBSTabs.register();
        EBSTrades.register();
        LOGGER.info("Enchanted Book Stack — 54 slot, enchanted_book only, nestable.");
    }
}

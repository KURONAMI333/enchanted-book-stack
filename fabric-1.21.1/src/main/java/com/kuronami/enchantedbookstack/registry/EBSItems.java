package com.kuronami.enchantedbookstack.registry;

import com.kuronami.enchantedbookstack.EnchantedBookStackFabric;
import com.kuronami.enchantedbookstack.item.EnchantedBookStackItem;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Fabric 流: vanilla {@link Registry#register} で直接登録 (NeoForge の DeferredRegister 相当)。
 */
public final class EBSItems {

    public static final EnchantedBookStackItem ENCHANTED_BOOK_STACK = new EnchantedBookStackItem(
            new Item.Properties()
                    .stacksTo(1)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
    );

    private EBSItems() {}

    public static void register() {
        Registry.register(
                BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(EnchantedBookStackFabric.MODID, "enchanted_book_stack"),
                ENCHANTED_BOOK_STACK
        );
    }
}

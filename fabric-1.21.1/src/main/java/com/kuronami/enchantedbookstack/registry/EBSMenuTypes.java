package com.kuronami.enchantedbookstack.registry;

import com.kuronami.enchantedbookstack.EnchantedBookStackFabric;
import com.kuronami.enchantedbookstack.menu.BookStackMenu;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/**
 * MenuType を vanilla registry に直接登録 (Fabric 流)。
 */
public final class EBSMenuTypes {

    public static MenuType<BookStackMenu> POUCH_MENU;

    private EBSMenuTypes() {}

    public static void register() {
        POUCH_MENU = Registry.register(
                BuiltInRegistries.MENU,
                ResourceLocation.fromNamespaceAndPath(EnchantedBookStackFabric.MODID, "book_stack_menu"),
                new MenuType<>(BookStackMenu::new, FeatureFlags.VANILLA_SET)
        );
    }

    /** Forge/NeoForge の DeferredHolder.get() と同じ感覚で使う既存コード互換用。 */
    public static MenuType<BookStackMenu> get() {
        return POUCH_MENU;
    }
}

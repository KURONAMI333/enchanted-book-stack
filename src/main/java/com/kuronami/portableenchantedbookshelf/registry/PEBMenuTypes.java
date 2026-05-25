package com.kuronami.portableenchantedbookshelf.registry;

import com.kuronami.portableenchantedbookshelf.PortableEnchantedBookshelf;
import com.kuronami.portableenchantedbookshelf.menu.PouchMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * PEB の {@link MenuType} 登録ハブ。
 *
 * <p>v0.1.0 では PEB を右クリックすると {@link PouchMenu} が開く。
 */
public final class PEBMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, PortableEnchantedBookshelf.MODID);

    /** PEB 中身閲覧 / 操作 menu。 */
    public static final DeferredHolder<MenuType<?>, MenuType<PouchMenu>> POUCH_MENU =
            MENU_TYPES.register(
                    "pouch_menu",
                    () -> new MenuType<>(PouchMenu::new, FeatureFlags.VANILLA_SET)
            );

    private PEBMenuTypes() {}
}

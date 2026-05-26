package com.kuronami.enchantedbookstack.registry;

import com.kuronami.enchantedbookstack.EnchantedBookStack;
import com.kuronami.enchantedbookstack.menu.BookStackMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * EBS の {@link MenuType} 登録ハブ。
 *
 * <p>v0.1.0 では EBS を右クリックすると {@link BookStackMenu} が開く。
 */
public final class EBSMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, EnchantedBookStack.MODID);

    /** EBS 中身閲覧 / 操作 menu。 */
    public static final DeferredHolder<MenuType<?>, MenuType<BookStackMenu>> POUCH_MENU =
            MENU_TYPES.register(
                    "book_stack_menu",
                    () -> new MenuType<>(BookStackMenu::new, FeatureFlags.VANILLA_SET)
            );

    private EBSMenuTypes() {}
}

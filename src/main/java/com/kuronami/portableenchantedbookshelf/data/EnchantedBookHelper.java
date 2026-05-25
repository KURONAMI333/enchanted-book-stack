package com.kuronami.portableenchantedbookshelf.data;

import java.util.Map;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.tags.EnchantmentTags;

/**
 * エンチャント本 ({@link ItemStack}) と {@link EnchantEntry} の相互変換 helper。
 *
 * <p>ハードコード一切無し — vanilla / Apotheosis / Iron's Spellbooks / Quark / 任意の
 * modded enchantment が全部同じパスで扱える。level 上限なし (Limitless Enchantments で
 * 作った Fortune XV も保管可)。
 *
 * <p>v0.1.0 制約: 単一エンチャント本のみサポート (multi-enchant 本は {@link #tryRead} で
 * {@link Optional#empty()} を返して reject)。multi-enchant 対応は Phase 2 で検討。
 */
public final class EnchantedBookHelper {

    private EnchantedBookHelper() {}

    /**
     * {@link ItemStack} がサポートされる単一エンチャント本なら {@link EnchantEntry} に変換。
     *
     * <p>サポート条件:
     * <ul>
     *   <li>{@code stack.is(Items.ENCHANTED_BOOK)}</li>
     *   <li>{@code STORED_ENCHANTMENTS} に exactly 1 エンチャント</li>
     * </ul>
     *
     * @param stack 判定対象
     * @return 変換成功時は {@link EnchantEntry} (count=1)、それ以外は {@link Optional#empty()}
     */
    public static Optional<EnchantEntry> tryRead(ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK)) return Optional.empty();

        ItemEnchantments enchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) return Optional.empty();
        if (enchantments.size() != 1) return Optional.empty(); // multi-enchant は reject

        Map.Entry<Holder<Enchantment>, Integer> only =
                enchantments.entrySet().iterator().next();
        Holder<Enchantment> holder = only.getKey();
        int level = only.getValue();

        ResourceLocation id = holder.unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);
        if (id == null) return Optional.empty(); // 名前無し enchant は reject

        boolean isCurse = holder.is(EnchantmentTags.CURSE);

        return Optional.of(new EnchantEntry(id, level, isCurse, 1));
    }

    /**
     * {@link EnchantEntry} (1 種類のエンチャント) から新規エンチャント本 {@link ItemStack} を生成。
     *
     * <p>registries 経由で {@code Holder<Enchantment>} を解決し、ENCHANTED_BOOK item に
     * {@code STORED_ENCHANTMENTS} component を書き込む。
     *
     * @param registries  enchant ID → Holder 解決用 (通常 {@code level.registryAccess()})
     * @param entry       生成するエンチャントの種類・レベル
     * @return 新規エンチャント本 stack (count=1)。enchant ID が registry に無ければ
     *         空の {@link ItemStack}
     */
    public static ItemStack createBook(HolderLookup.Provider registries, EnchantEntry entry) {
        var enchantRegistry = registries.lookup(Registries.ENCHANTMENT).orElse(null);
        if (enchantRegistry == null) return ItemStack.EMPTY;

        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, entry.enchantId());
        Holder<Enchantment> holder = enchantRegistry.get(key).orElse(null);
        if (holder == null) return ItemStack.EMPTY;

        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.updateEnchantments(book, mutable -> mutable.set(holder, entry.level()));
        return book;
    }

    /**
     * {@link ItemStack} がエンチャント本かどうかの軽量チェック (NBT 解析せず item ID のみ)。
     */
    public static boolean isEnchantedBook(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK);
    }
}

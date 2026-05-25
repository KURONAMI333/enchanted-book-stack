package com.kuronami.portableenchantedbookshelf.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.kuronami.portableenchantedbookshelf.item.PortableEnchantedBookshelfItem;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Client-side cache of the PEB's contents — AE2 流儀の {@code Repo}。
 *
 * <p>役割:
 * <ul>
 *   <li>PEB ItemStack の {@link ItemContainerContents} から books を吸い上げ</li>
 *   <li>検索クエリ + sort mode を保持、 適用後の filtered+sorted list を提供</li>
 *   <li>scroll offset を保持し、 {@link RepoSlot#getItem()} の lookup index を支援</li>
 * </ul>
 *
 * <p>{@code setSearch} / {@code setSort} / {@code setScrollOffset} で view 更新、
 * Screen 側は {@link #getViewportSlot(int)} で virtual slot の中身を取れる。
 */
public class PouchRepo {

    /** sort モード (Phase 2 では NAME_ASC のみ実装、 後で LEVEL_DESC / RECENT 追加予定)。 */
    public enum SortMode {
        NAME_ASC,
        LEVEL_DESC,
        RECENT
    }

    /** 元の books (sync 反映用、 immutable copy)。 */
    private List<ItemStack> rawBooks;
    /** filter + sort 適用後の view。 */
    private List<ItemStack> view;

    private String searchQuery = "";
    private SortMode sortMode = SortMode.NAME_ASC;
    private int scrollOffset = 0;

    public PouchRepo(List<ItemStack> books) {
        this.rawBooks = List.copyOf(books);
        rebuildView();
    }

    /** PEB ItemStack の DataComponents.CONTAINER から books を吸い上げて Repo を生成。 */
    public static PouchRepo fromStack(ItemStack pebStack) {
        ItemContainerContents contents = PortableEnchantedBookshelfItem.getContents(pebStack);
        List<ItemStack> books = new ArrayList<>();
        contents.stream().forEach(s -> books.add(s.copy()));
        return new PouchRepo(books);
    }

    /** PEB ItemStack が更新された時に呼ぶ (sync packet 受信時 or DataComponent 直読み時)。 */
    public void updateFromStack(ItemStack pebStack) {
        ItemContainerContents contents = PortableEnchantedBookshelfItem.getContents(pebStack);
        List<ItemStack> books = new ArrayList<>();
        contents.stream().forEach(s -> books.add(s.copy()));
        this.rawBooks = List.copyOf(books);
        rebuildView();
    }

    public int totalRawCount() {
        return rawBooks.size();
    }

    public int viewSize() {
        return view.size();
    }

    /** filter + sort 適用後の i 番目の book (範囲外なら EMPTY)。 */
    public ItemStack get(int idx) {
        if (idx < 0 || idx >= view.size()) return ItemStack.EMPTY;
        return view.get(idx);
    }

    /** viewport slot の中身: scrollOffset + viewportIdx でずらして lookup。 */
    public ItemStack getViewportSlot(int viewportIdx) {
        return get(scrollOffset + viewportIdx);
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    /** scrollOffset を [0, max(0, viewSize - rowsVisible*colsVisible)] にクランプして set。 */
    public void setScrollOffset(int offset, int visibleSlots) {
        int maxOffset = Math.max(0, view.size() - visibleSlots);
        this.scrollOffset = Math.max(0, Math.min(offset, maxOffset));
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearch(String query) {
        this.searchQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);
        this.scrollOffset = 0;
        rebuildView();
    }

    public SortMode getSortMode() {
        return sortMode;
    }

    public void setSort(SortMode mode) {
        this.sortMode = mode;
        rebuildView();
    }

    // ─────────────────────────────────────────────────────────────
    // 内部: view 再構築 (filter + sort 適用)
    // ─────────────────────────────────────────────────────────────

    private void rebuildView() {
        HolderLookup.Provider registries = currentRegistries();
        boolean noFilter = searchQuery.isEmpty();

        List<ItemStack> filtered = rawBooks.stream()
                .filter(book -> noFilter || matchesQuery(book, searchQuery, registries))
                .sorted(comparator(sortMode, registries))
                .toList();
        this.view = filtered;
    }

    private static HolderLookup.Provider currentRegistries() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.registryAccess() : null;
    }

    private static boolean matchesQuery(ItemStack book, String lowerQuery, HolderLookup.Provider registries) {
        // 1st enchantment ベースで判定 (single-enchant book 想定、 multi も 1 つ目で match)
        ItemEnchantments encs = book.get(DataComponents.STORED_ENCHANTMENTS);
        if (encs == null || encs.isEmpty()) return false;
        for (Map.Entry<Holder<Enchantment>, Integer> entry : encs.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int level = entry.getValue();

            // localized name
            String name = holder.value().description().getString().toLowerCase(Locale.ROOT);
            if (name.contains(lowerQuery)) return true;

            // enchant ID (例 "minecraft:fortune")
            String id = holder.unwrapKey().map(k -> k.location().toString()).orElse("");
            if (id.contains(lowerQuery)) return true;

            // ローマ数字 level 完全一致 (例 "III")
            if (romanOrPlain(level).toLowerCase(Locale.ROOT).equals(lowerQuery)) return true;
        }
        return false;
    }

    private static Comparator<ItemStack> comparator(SortMode mode, HolderLookup.Provider registries) {
        return switch (mode) {
            case NAME_ASC -> Comparator.comparing((ItemStack b) -> primaryName(b, registries));
            case LEVEL_DESC -> Comparator.comparingInt((ItemStack b) -> -primaryLevel(b));
            case RECENT -> Comparator.comparingInt(b -> 0); // raw 順 (= insertion order)
        };
    }

    private static String primaryName(ItemStack book, HolderLookup.Provider registries) {
        ItemEnchantments encs = book.get(DataComponents.STORED_ENCHANTMENTS);
        if (encs == null || encs.isEmpty()) return "";
        Holder<Enchantment> first = encs.keySet().iterator().next();
        return first.value().description().getString().toLowerCase(Locale.ROOT);
    }

    private static int primaryLevel(ItemStack book) {
        ItemEnchantments encs = book.get(DataComponents.STORED_ENCHANTMENTS);
        if (encs == null || encs.isEmpty()) return 0;
        return encs.entrySet().iterator().next().getValue();
    }

    /** 公開: I-X はローマ数字、 11+ は "Lvl N"。 */
    public static String romanOrPlain(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> level <= 0 ? Integer.toString(level) : "Lvl " + level;
        };
    }

    /** 公開: enchant の localized display name を解決 (registry lookup, fallback あり)。 */
    public static Component resolveEnchantmentName(Holder<Enchantment> holder, HolderLookup.Provider registries) {
        if (holder == null) return Component.literal("?");
        return holder.value().description();
    }

    /** 公開: enchantment ID から Holder を解決 (registries 経由)。 */
    public static Holder<Enchantment> resolveHolder(net.minecraft.resources.ResourceLocation id, HolderLookup.Provider registries) {
        if (registries == null) return null;
        var registry = registries.lookup(Registries.ENCHANTMENT).orElse(null);
        if (registry == null) return null;
        return registry.get(ResourceKey.create(Registries.ENCHANTMENT, id)).orElse(null);
    }
}

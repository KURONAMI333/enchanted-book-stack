package com.kuronami.portableenchantedbookshelf.client.screen;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.kuronami.portableenchantedbookshelf.item.PortableEnchantedBookshelfItem;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Client-side cache of PEB の内容物 — AE2 {@code Repo} + IPN sort + JEI 検索の統合。
 *
 * <p>役割:
 * <ul>
 *   <li>PEB stack の {@code DataComponents.CONTAINER} (rawBooks) を吸い上げ</li>
 *   <li>{@code searchPhrase} + {@code sortMode} を適用した view を提供</li>
 *   <li>{@code scrollOffset} で viewport をずらす</li>
 *   <li>{@code paused} で shift 連打中の view 揺れを抑止 (AE2 流儀)</li>
 *   <li>view の i 番目 → handler の元 index 逆マッピング (click → server packet 用)</li>
 * </ul>
 *
 * <p>NB: client が PEB stack を直接読む設計のため、 server からの差分 packet は不要 (vanilla の
 * slot sync で stack DataComponent が反映される)。 {@code updateFromStack} を tick で呼ぶ。
 */
public class PouchRepo {

    public enum SortMode {
        /** localized name 昇順 (Collator で日本語 / 中文等のロケール対応)。 */
        NAME_ASC,
        /** 主 enchant level 降順 (高 level が上)。 */
        LEVEL_DESC,
        /** kind 集約 count 降順 (同じ Fortune III が 3 冊なら 3 で sort)。 */
        COUNT_DESC,
        /** 挿入順 (= raw order)、 v0.1 では DataComponent 無いので raw 順そのまま。 */
        RECENT
    }

    /** PEB stack から吸い上げた snapshot (immutable copy)。 */
    private List<ItemStack> rawBooks;
    /** filter + sort 適用後の表示用 view。 */
    private List<ItemStack> view;
    /** view 内 index → rawBooks 内 index 逆マッピング (extract packet で handler idx 復元用)。 */
    private List<Integer> viewToRaw;

    private String searchPhrase = "";
    private SortMode sortMode = SortMode.NAME_ASC;
    private int scrollOffset = 0;
    /** shift 連打中 true で rebuildView 抑止 (AE2 流儀)、 click 連打で sort 動かないように。 */
    private boolean paused = false;

    public PouchRepo(List<ItemStack> books) {
        this.rawBooks = List.copyOf(books);
        rebuildView();
    }

    /** PEB stack の DataComponents.CONTAINER から books 吸い上げて Repo 作成。 */
    public static PouchRepo fromStack(ItemStack pebStack) {
        return new PouchRepo(extractBooks(pebStack));
    }

    /** PEB stack 内容物 更新時 (tick) に呼ぶ。 paused 中は rawBooks も更新しない (view 固定)。 */
    public void updateFromStack(ItemStack pebStack) {
        if (paused) return;
        List<ItemStack> next = extractBooks(pebStack);
        // 浅い equality check で no-op 高速化
        if (sameBooks(this.rawBooks, next)) return;
        this.rawBooks = List.copyOf(next);
        rebuildView();
    }

    private static List<ItemStack> extractBooks(ItemStack pebStack) {
        ItemContainerContents contents = PortableEnchantedBookshelfItem.getContents(pebStack);
        List<ItemStack> books = new ArrayList<>();
        contents.stream().forEach(s -> books.add(s.copy()));
        return books;
    }

    private static boolean sameBooks(List<ItemStack> a, List<ItemStack> b) {
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────
    // Accessors
    // ─────────────────────────────────────────────────────────────

    public int rawSize() { return rawBooks.size(); }
    public int viewSize() { return view.size(); }
    public String getSearchPhrase() { return searchPhrase; }
    public SortMode getSortMode() { return sortMode; }
    public int getScrollOffset() { return scrollOffset; }

    /** view の i 番目 (範囲外なら EMPTY)。 */
    public ItemStack get(int viewIdx) {
        if (viewIdx < 0 || viewIdx >= view.size()) return ItemStack.EMPTY;
        return view.get(viewIdx);
    }

    /** viewport slot の中身 = view の (scrollOffset + viewportIdx) 番目。 */
    public ItemStack getViewportSlot(int viewportIdx) {
        return get(scrollOffset + viewportIdx);
    }

    /**
     * view 内 idx → rawBooks 内 idx に逆マッピング (server に送る extract packet 用)。
     * view idx 範囲外なら -1。
     */
    public int viewToRawIndex(int viewIdx) {
        if (viewIdx < 0 || viewIdx >= viewToRaw.size()) return -1;
        return viewToRaw.get(viewIdx);
    }

    // ─────────────────────────────────────────────────────────────
    // Mutators (UI 操作)
    // ─────────────────────────────────────────────────────────────

    public void setSearchPhrase(String phrase) {
        String next = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT);
        if (next.equals(this.searchPhrase)) return;
        this.searchPhrase = next;
        this.scrollOffset = 0; // 検索変化で先頭に戻す
        rebuildView();
    }

    public void setSortMode(SortMode mode) {
        if (mode == this.sortMode) return;
        this.sortMode = mode;
        rebuildView();
    }

    /** [0, max(0, viewSize - visibleSlots)] に clamp して set。 */
    public void setScrollOffset(int offset, int visibleSlots) {
        int maxOffset = Math.max(0, view.size() - visibleSlots);
        this.scrollOffset = Math.max(0, Math.min(offset, maxOffset));
    }

    /** shift 連打中 view を固定。 AE2 流儀 ({@code MEStorageScreen.java:381})。 */
    public void setPaused(boolean p) {
        if (this.paused == p) return;
        this.paused = p;
        if (!p) rebuildView(); // resume 時に最新を反映
    }

    // ─────────────────────────────────────────────────────────────
    // 内部: view rebuild (filter + sort)
    // ─────────────────────────────────────────────────────────────

    private void rebuildView() {
        HolderLookup.Provider registries = currentRegistries();
        boolean noFilter = searchPhrase.isEmpty();
        Comparator<IndexedBook> cmp = comparator(sortMode, registries, rawBooks);

        List<IndexedBook> indexed = new ArrayList<>(rawBooks.size());
        for (int i = 0; i < rawBooks.size(); i++) {
            ItemStack b = rawBooks.get(i);
            if (b.isEmpty()) continue;
            if (!noFilter && !matchesQuery(b, searchPhrase, registries)) continue;
            indexed.add(new IndexedBook(i, b));
        }
        indexed.sort(cmp);

        List<ItemStack> newView = new ArrayList<>(indexed.size());
        List<Integer> newViewToRaw = new ArrayList<>(indexed.size());
        for (IndexedBook ib : indexed) {
            newView.add(ib.stack);
            newViewToRaw.add(ib.rawIdx);
        }
        this.view = newView;
        this.viewToRaw = newViewToRaw;
    }

    private record IndexedBook(int rawIdx, ItemStack stack) {}

    private static HolderLookup.Provider currentRegistries() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.registryAccess() : null;
    }

    /**
     * 検索マッチ判定: localized name / enchant ID / ローマ数字 level (完全一致) のいずれか。
     * JEI {@code Translator.toLowercaseWithLocale} 流儀で正規化 (トルコ語 I→ı 罠は本実装では
     * {@code Locale.ROOT} で回避)。
     */
    private static boolean matchesQuery(ItemStack book, String lowerQuery, HolderLookup.Provider registries) {
        ItemEnchantments encs = book.get(DataComponents.STORED_ENCHANTMENTS);
        if (encs == null || encs.isEmpty()) return false;

        for (Map.Entry<Holder<Enchantment>, Integer> e : encs.entrySet()) {
            Holder<Enchantment> holder = e.getKey();
            int level = e.getValue();

            String name = holder.value().description().getString().toLowerCase(Locale.ROOT);
            if (name.contains(lowerQuery)) return true;

            String id = holder.unwrapKey().map(k -> k.location().toString()).orElse("");
            if (id.toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;

            if (romanOrPlain(level).toLowerCase(Locale.ROOT).equals(lowerQuery)) return true;
        }
        return false;
    }

    /** Sort comparator chain. */
    private static Comparator<IndexedBook> comparator(
            SortMode mode, HolderLookup.Provider registries, List<ItemStack> raw
    ) {
        Map<String, Integer> countByKind = mode == SortMode.COUNT_DESC ? bucketCounts(raw) : null;

        return switch (mode) {
            case NAME_ASC -> {
                Collator collator = Collator.getInstance(Minecraft.getInstance().getLanguageManager().getJavaLocale());
                yield Comparator.comparing(
                        (IndexedBook ib) -> primaryName(ib.stack, registries),
                        collator
                ).thenComparingInt(ib -> -primaryLevel(ib.stack));
            }
            case LEVEL_DESC -> Comparator.comparingInt((IndexedBook ib) -> -primaryLevel(ib.stack))
                    .thenComparing(ib -> primaryName(ib.stack, registries));
            case COUNT_DESC -> Comparator.comparingInt((IndexedBook ib) -> -countByKind.get(kindKey(ib.stack)))
                    .thenComparing(ib -> primaryName(ib.stack, registries));
            case RECENT -> Comparator.comparingInt((IndexedBook ib) -> -ib.rawIdx); // 新しい挿入が後ろ = -rawIdx で desc
        };
    }

    private static Map<String, Integer> bucketCounts(List<ItemStack> raw) {
        Map<String, Integer> m = new HashMap<>();
        for (ItemStack b : raw) {
            if (b.isEmpty()) continue;
            m.merge(kindKey(b), 1, Integer::sum);
        }
        return m;
    }

    /** kind key = enchantId + level + isCurse 区別 (Fortune III と Mending I を別 kind 扱い)。 */
    private static String kindKey(ItemStack book) {
        ItemEnchantments encs = book.get(DataComponents.STORED_ENCHANTMENTS);
        if (encs == null || encs.isEmpty()) return "?";
        Map.Entry<Holder<Enchantment>, Integer> first = encs.entrySet().iterator().next();
        String id = first.getKey().unwrapKey().map(k -> k.location().toString()).orElse("?");
        return id + ":" + first.getValue();
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

    // ─────────────────────────────────────────────────────────────
    // Public utility
    // ─────────────────────────────────────────────────────────────

    /** I-X はローマ数字、 11+ は "Lvl N" (Screen の tooltip 等で再利用)。 */
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

    /** enchant ID から Holder を解決 (registries 経由、 fallback null)。 */
    public static Holder<Enchantment> resolveHolder(net.minecraft.resources.ResourceLocation id, HolderLookup.Provider registries) {
        if (registries == null) return null;
        var registry = registries.lookup(Registries.ENCHANTMENT).orElse(null);
        if (registry == null) return null;
        return registry.get(ResourceKey.create(Registries.ENCHANTMENT, id)).orElse(null);
    }
}

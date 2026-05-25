package com.kuronami.portableenchantedbookshelf.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * PEB の中身 — 保管されたエンチャント本の集約リスト。
 *
 * <p>同一の (enchantId, level, isCurse) を持つ本は 1 つの {@link EnchantEntry}
 * に集約される (Fortune III × 3 のような形)。
 *
 * <p>容量は<b>無制限</b> (kura 設計判断: アイテム 1 種限定なのでバランス崩壊しない)。
 *
 * <p>本クラスは immutable。{@link #insert} / {@link #extract} は新しい
 * {@code PouchContents} を返す。
 *
 * @param entries 集約されたエンチャントエントリのリスト
 */
public record PouchContents(List<EnchantEntry> entries) {

    /** 空の PEB。 */
    public static final PouchContents EMPTY = new PouchContents(List.of());

    /** 永続化用 Codec。 */
    public static final Codec<PouchContents> CODEC =
            EnchantEntry.CODEC.listOf().xmap(PouchContents::new, PouchContents::entries);

    /** ネットワーク同期用 StreamCodec。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, PouchContents> STREAM_CODEC =
            EnchantEntry.STREAM_CODEC
                    .apply(ByteBufCodecs.list())
                    .map(PouchContents::new, PouchContents::entries);

    /** コンパクト constructor: リストは immutable コピーで保持。 */
    public PouchContents {
        entries = List.copyOf(entries);
    }

    /** 中に入っている本の総数 (全エントリの count 合計)。 */
    public int totalBookCount() {
        int total = 0;
        for (EnchantEntry e : entries) {
            total += e.count();
        }
        return total;
    }

    /** ユニークなエンチャント種類数 (= entries.size())。 */
    public int uniqueKindCount() {
        return entries.size();
    }

    /** 空か。 */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 指定の本 1 冊を挿入した新しい {@code PouchContents} を返す。
     *
     * <p>同じ kind (enchantId + level + isCurse) のエントリが既にあれば
     * count を +1、無ければ新エントリを追加。
     */
    public PouchContents insertOne(EnchantEntry newEntry) {
        List<EnchantEntry> updated = new ArrayList<>(entries);
        for (int i = 0; i < updated.size(); i++) {
            EnchantEntry existing = updated.get(i);
            if (existing.isSameKind(newEntry)) {
                updated.set(i, existing.withCount(existing.count() + newEntry.count()));
                return new PouchContents(updated);
            }
        }
        // 新規 kind
        updated.add(newEntry);
        return new PouchContents(updated);
    }

    /**
     * 指定の kind から count 冊取り出した新しい {@code PouchContents} を返す。
     *
     * <p>対象 kind が無い / count 不足の場合は変化なし (同じ instance を返す)。
     * count に 0 以下を渡しても変化なし。
     */
    public PouchContents extract(EnchantEntry kind, int countToExtract) {
        if (countToExtract <= 0) return this;

        List<EnchantEntry> updated = new ArrayList<>(entries);
        for (int i = 0; i < updated.size(); i++) {
            EnchantEntry existing = updated.get(i);
            if (existing.isSameKind(kind)) {
                int newCount = existing.count() - countToExtract;
                if (newCount <= 0) {
                    updated.remove(i);
                } else {
                    updated.set(i, existing.withCount(newCount));
                }
                return new PouchContents(updated);
            }
        }
        return this; // 対象 kind 無し
    }

    /** デバッグ・テスト用: entries の不変ビュー。 */
    public List<EnchantEntry> view() {
        return Collections.unmodifiableList(entries);
    }
}

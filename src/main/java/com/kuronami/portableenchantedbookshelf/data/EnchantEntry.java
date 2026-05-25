package com.kuronami.portableenchantedbookshelf.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * PEB に保管された 1 種類のエンチャント本のエントリ。
 *
 * <p>「同じ enchantId + 同じ level + 同じ isCurse」の本は等価として {@code count}
 * に集約される。例: Fortune III が 3 冊 → {@code (minecraft:fortune, 3, false, 3)}。
 *
 * <p>level は int 上限なし — modded で {@code Fortune XV} のような level 拡張本も保管可能。
 *
 * @param enchantId エンチャントの ResourceLocation (vanilla / modded 問わず動的に扱う)
 * @param level     エンチャントのレベル (1+, int 上限なし)
 * @param isCurse   curse 系エンチャント (Curse of Vanishing 等) かどうか
 * @param count     このエントリに集約された本の枚数
 */
public record EnchantEntry(
        ResourceLocation enchantId,
        int level,
        boolean isCurse,
        int count
) {
    /** 永続化用 Codec (JSON / SNBT)。 */
    public static final Codec<EnchantEntry> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("id").forGetter(EnchantEntry::enchantId),
                    Codec.INT.fieldOf("level").forGetter(EnchantEntry::level),
                    Codec.BOOL.optionalFieldOf("curse", false).forGetter(EnchantEntry::isCurse),
                    Codec.INT.fieldOf("count").forGetter(EnchantEntry::count)
            ).apply(instance, EnchantEntry::new)
    );

    /** ネットワーク同期用 StreamCodec (server→client packet)。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantEntry> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, EnchantEntry::enchantId,
                    ByteBufCodecs.VAR_INT,         EnchantEntry::level,
                    ByteBufCodecs.BOOL,            EnchantEntry::isCurse,
                    ByteBufCodecs.VAR_INT,         EnchantEntry::count,
                    EnchantEntry::new
            );

    /**
     * 同一の (enchantId, level, isCurse) を持つ別エントリと等価か。
     * count は無視 — 「同じエンチャント種・同じレベル・同じ curse 区分」の判定。
     */
    public boolean isSameKind(EnchantEntry other) {
        return this.enchantId.equals(other.enchantId)
                && this.level == other.level
                && this.isCurse == other.isCurse;
    }

    /** count だけ変えた新しいエントリを返す (record は immutable)。 */
    public EnchantEntry withCount(int newCount) {
        return new EnchantEntry(enchantId, level, isCurse, newCount);
    }
}

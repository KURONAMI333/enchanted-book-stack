package com.kuronami.portableenchantedbookshelf.network;

import com.kuronami.portableenchantedbookshelf.PortableEnchantedBookshelf;
import com.kuronami.portableenchantedbookshelf.data.EnchantEntry;
import com.kuronami.portableenchantedbookshelf.data.EnchantedBookHelper;
import com.kuronami.portableenchantedbookshelf.data.PouchContents;
import com.kuronami.portableenchantedbookshelf.item.PortableEnchantedBookshelfItem;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Screen から PEB の中身を取り出すリクエスト (client → server)。
 *
 * <p>EnchantEntry の kind (enchantId + level + isCurse) で対象を指定 + 取り出し冊数指定。
 * index ベースでないのは、filtered list でクリックされた時の filtered→original 変換が
 * 不要になる + 並行更新で entries の順序が変わってもズレない。
 *
 * <p>count = 1 で通常クリック、count = {@link Integer#MAX_VALUE} で shift-click「全部取り出し」。
 * server 側で entry の実在 count に clamp する。
 */
public record ExtractBookPayload(
        ResourceLocation enchantId,
        int level,
        boolean isCurse,
        int count
) implements CustomPacketPayload {

    /** 「全部取り出し」フラグ用 sentinel (server で actual count に clamp)。 */
    public static final int EXTRACT_ALL = Integer.MAX_VALUE;

    public static final Type<ExtractBookPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PortableEnchantedBookshelf.MODID, "extract_book")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ExtractBookPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, ExtractBookPayload::enchantId,
                    ByteBufCodecs.VAR_INT,         ExtractBookPayload::level,
                    ByteBufCodecs.BOOL,            ExtractBookPayload::isCurse,
                    ByteBufCodecs.VAR_INT,         ExtractBookPayload::count,
                    ExtractBookPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server 側 handler。手持ち PEB から該当 kind を count 冊 (上限あり) 取り出してインベントリへ。 */
    public static void handle(ExtractBookPayload payload, IPayloadContext context) {
        Player player = context.player();
        ItemStack pebStack = findPouchInHand(player);
        if (pebStack.isEmpty()) return;

        PouchContents contents = PortableEnchantedBookshelfItem.getContents(pebStack);
        EnchantEntry target = null;
        for (EnchantEntry e : contents.entries()) {
            if (e.enchantId().equals(payload.enchantId())
                    && e.level() == payload.level()
                    && e.isCurse() == payload.isCurse()) {
                target = e;
                break;
            }
        }
        if (target == null) return;

        // 取り出し数を clamp: payload.count を 1..target.count() に
        int requested = Math.max(1, payload.count());
        int actual = Math.min(requested, target.count());

        // book を actual 冊生成、player インベントリへ (満杯なら drop)
        for (int i = 0; i < actual; i++) {
            ItemStack book = EnchantedBookHelper.createBook(player.level().registryAccess(), target);
            if (book.isEmpty()) return; // enchant 解決失敗、以後の生成も無理
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
        }
        PouchContents updated = contents.extract(target.withCount(1), actual);
        PortableEnchantedBookshelfItem.setContents(pebStack, updated);
    }

    private static ItemStack findPouchInHand(Player player) {
        ItemStack main = player.getMainHandItem();
        if (PortableEnchantedBookshelfItem.is(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (PortableEnchantedBookshelfItem.is(off)) return off;
        return ItemStack.EMPTY;
    }
}

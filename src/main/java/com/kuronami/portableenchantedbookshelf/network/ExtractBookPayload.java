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
 * Screen から PEB の中身を 1 冊取り出すリクエスト (client → server)。
 *
 * <p>EnchantEntry の kind (enchantId + level + isCurse) で対象を指定する。
 * index ベースでないのは、filtered list でクリックされた時の filtered→original 変換が
 * 不要になる + 並行更新で entries の順序が変わってもズレない。
 */
public record ExtractBookPayload(
        ResourceLocation enchantId,
        int level,
        boolean isCurse
) implements CustomPacketPayload {

    public static final Type<ExtractBookPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PortableEnchantedBookshelf.MODID, "extract_book")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ExtractBookPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, ExtractBookPayload::enchantId,
                    ByteBufCodecs.VAR_INT,         ExtractBookPayload::level,
                    ByteBufCodecs.BOOL,            ExtractBookPayload::isCurse,
                    ExtractBookPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server 側 handler。プレイヤー手持ちの PEB から該当 kind を 1 冊取り出してインベントリへ。 */
    public static void handle(ExtractBookPayload payload, IPayloadContext context) {
        Player player = context.player();
        ItemStack pebStack = findPouchInHand(player);
        if (pebStack.isEmpty()) return;

        PouchContents contents = PortableEnchantedBookshelfItem.getContents(pebStack);
        // 指定 kind を探す
        EnchantEntry target = null;
        for (EnchantEntry e : contents.entries()) {
            if (e.enchantId().equals(payload.enchantId())
                    && e.level() == payload.level()
                    && e.isCurse() == payload.isCurse()) {
                target = e;
                break;
            }
        }
        if (target == null) return; // race condition (Screen 表示と現状が乖離)、無視

        // book 生成
        ItemStack book = EnchantedBookHelper.createBook(player.level().registryAccess(), target);
        if (book.isEmpty()) return; // enchant が registry から消えた等

        // PouchContents 更新 + book を player インベントリへ
        PouchContents updated = contents.extract(target.withCount(1), 1);
        PortableEnchantedBookshelfItem.setContents(pebStack, updated);
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }

    private static ItemStack findPouchInHand(Player player) {
        ItemStack main = player.getMainHandItem();
        if (PortableEnchantedBookshelfItem.is(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (PortableEnchantedBookshelfItem.is(off)) return off;
        return ItemStack.EMPTY;
    }
}

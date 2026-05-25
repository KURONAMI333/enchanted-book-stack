package com.kuronami.portableenchantedbookshelf.network;

import com.kuronami.portableenchantedbookshelf.PortableEnchantedBookshelf;
import com.kuronami.portableenchantedbookshelf.item.PortableEnchantedBookshelfItem;
import com.kuronami.portableenchantedbookshelf.menu.PouchMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: cursor が握ってる carried ItemStack (= enchanted_book) を
 * PEB の handler の最初の空 slot に挿入する。 typically Screen 内の空 viewport slot を
 * クリックした時に送信。
 *
 * <p>cursor は server で {@code shrink(1)} → client にも自動 sync。
 */
public record InsertCarriedPayload() implements CustomPacketPayload {

    public static final InsertCarriedPayload INSTANCE = new InsertCarriedPayload();

    public static final Type<InsertCarriedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PortableEnchantedBookshelf.MODID, "insert_carried")
    );

    /** payload に field 無し、 unit packet。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, InsertCarriedPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server 側 handler。 */
    public static void handle(InsertCarriedPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player.containerMenu instanceof PouchMenu menu)) return;
        if (menu.isClientSide()) return;

        ItemStack carried = menu.getCarried();
        if (!PortableEnchantedBookshelfItem.isAcceptableBook(carried)) return;

        ItemStackHandler handler = menu.getHandler();
        int emptySlot = -1;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).isEmpty()) {
                emptySlot = i;
                break;
            }
        }
        if (emptySlot < 0) return; // 256 上限到達

        ItemStack one = carried.copy();
        one.setCount(1);
        handler.setStackInSlot(emptySlot, one);
        carried.shrink(1);
        menu.setCarried(carried);
        menu.onHandlerChanged();
    }
}

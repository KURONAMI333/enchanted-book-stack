package com.kuronami.portableenchantedbookshelf.network;

import com.kuronami.portableenchantedbookshelf.PortableEnchantedBookshelf;
import com.kuronami.portableenchantedbookshelf.menu.PouchMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: PEB の {@link ItemStackHandler} の slot index から count 個取り出す。
 *
 * <p>{@code count = 1} で通常クリック、 {@code count = Integer.MAX_VALUE} で shift-click「全部」。
 * server が clamp。 取り出した book は player インベントリへ (満杯なら drop)。
 */
public record ExtractByIdxPayload(int handlerSlotIdx, int count) implements CustomPacketPayload {

    public static final int EXTRACT_ALL = Integer.MAX_VALUE;

    public static final Type<ExtractByIdxPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PortableEnchantedBookshelf.MODID, "extract_by_idx")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ExtractByIdxPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ExtractByIdxPayload::handlerSlotIdx,
                    ByteBufCodecs.VAR_INT, ExtractByIdxPayload::count,
                    ExtractByIdxPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server 側 handler。 */
    public static void handle(ExtractByIdxPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player.containerMenu instanceof PouchMenu menu)) return;
        if (menu.isClientSide()) return;

        ItemStackHandler handler = menu.getHandler();
        int idx = payload.handlerSlotIdx();
        if (idx < 0 || idx >= handler.getSlots()) return;

        ItemStack inSlot = handler.getStackInSlot(idx);
        if (inSlot.isEmpty()) return;

        int requested = Math.max(1, payload.count());
        int actual = Math.min(requested, inSlot.getCount());
        ItemStack extracted = handler.extractItem(idx, actual, false);
        if (extracted.isEmpty()) return;

        if (!player.getInventory().add(extracted)) {
            player.drop(extracted, false);
        }

        menu.onHandlerChanged();
    }
}

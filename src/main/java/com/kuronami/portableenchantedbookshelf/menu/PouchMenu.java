package com.kuronami.portableenchantedbookshelf.menu;

import java.util.ArrayList;
import java.util.List;

import com.kuronami.portableenchantedbookshelf.item.PortableEnchantedBookshelfItem;
import com.kuronami.portableenchantedbookshelf.registry.PEBMenuTypes;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * PouchMenu — AE2 viewport pattern の **server-side** Menu。
 *
 * <p>{@link ItemStackHandler} (256 slot 上限) を server-side で保持し、 PEB の
 * {@code DataComponents.CONTAINER} と同期する。 vanilla の Menu slot としては
 * <b>player inventory のみ</b>を持ち、 PEB の内容物 slot は menu に load せず、
 * client が独自 {@code Repo} + {@code ClientReadOnlySlot} で virtual 表示する。
 *
 * <p>これは AE2 ME terminal と同型 — 内容物 sync は専用 packet (Phase 2 で実装) で
 * client に flush、 client は viewport で navigate。
 *
 * <p>Menu close 時に handler を {@code ItemContainerContents} に書き戻し、
 * PEB の {@code ItemStack} に永続化する。
 */
public class PouchMenu extends AbstractContainerMenu {

    /** player inventory slot layout 用の標準オフセット (generic_54.png 想定)。 */
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y_HOTBAR = 198;
    private static final int PLAYER_INV_Y_MAIN = 140;

    private final Player player;
    private final boolean clientSide;
    private final ItemStack pebStack;
    /** server-side で内容物を保持する 256-slot handler。 client では空 (placeholder)。 */
    private final ItemStackHandler handler;

    /** MenuType の MenuSupplier 用 (client / server 両方で呼ばれる)。 */
    public PouchMenu(int containerId, Inventory playerInventory) {
        super(PEBMenuTypes.POUCH_MENU.get(), containerId);
        this.player = playerInventory.player;
        this.clientSide = playerInventory.player.level().isClientSide();
        this.pebStack = findPebInHand(playerInventory.player);

        this.handler = new ItemStackHandler(PortableEnchantedBookshelfItem.MAX_BOOKS);
        if (!clientSide && !pebStack.isEmpty()) {
            // server: PEB の中身を handler にロード
            List<ItemStack> books = PortableEnchantedBookshelfItem.getMutableBooks(pebStack);
            int slotIdx = 0;
            for (ItemStack book : books) {
                if (slotIdx >= handler.getSlots()) break;
                handler.setStackInSlot(slotIdx++, book);
            }
        }

        addPlayerInventorySlots(playerInventory);
    }

    /** player inventory 36 slots を vanilla 標準位置で追加。 */
    private void addPlayerInventorySlots(Inventory inv) {
        // メインインベントリ (3 行 × 9 列)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIdx = col + row * 9 + 9;
                addSlot(new Slot(inv, slotIdx, PLAYER_INV_X + col * 18, PLAYER_INV_Y_MAIN + row * 18));
            }
        }
        // ホットバー (9 列)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y_HOTBAR));
        }
    }

    /** プレイヤー手持ちから PEB を探す (main → off の順)。 */
    private static ItemStack findPebInHand(Player player) {
        ItemStack main = player.getMainHandItem();
        if (PortableEnchantedBookshelfItem.is(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (PortableEnchantedBookshelfItem.is(off)) return off;
        return ItemStack.EMPTY;
    }

    // ─────────────────────────────────────────────────────────────
    // Accessors (server / client 共有)
    // ─────────────────────────────────────────────────────────────

    public ItemStack getPebStack() { return pebStack; }
    public Player getPlayer() { return player; }
    public boolean isClientSide() { return clientSide; }
    /** server-side 専用 handler。 client では空 placeholder を返す。 */
    public ItemStackHandler getHandler() { return handler; }

    /** server-side で handler から非空 ItemStack を順に取り出す。 */
    public List<ItemStack> snapshotBooks() {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty()) result.add(s.copy());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // AbstractContainerMenu overrides
    // ─────────────────────────────────────────────────────────────

    @Override
    public boolean stillValid(Player player) {
        return !findPebInHand(player).isEmpty();
    }

    /** shift-click: PEB に slot を持たせてないので player inventory 内移動のみ。 v0.1 では no-op。 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // TODO Phase 2 後半: player inventory → PEB の shift-click 移動を実装
        return ItemStack.EMPTY;
    }

    /** Menu close 時に server-side handler を PEB の DataComponent に書き戻す。 */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!clientSide && !pebStack.isEmpty()) {
            PortableEnchantedBookshelfItem.writeBooks(pebStack, snapshotBooks());
        }
    }

    /**
     * Client-only viewport slot (AE2 流儀の {@code ClientReadOnlySlot}) を menu に追加する。
     *
     * <p>{@link AbstractContainerMenu#addSlot} は protected なので、 PouchScreen 等の
     * 外部から呼べるよう public wrapper を提供。 server / client で slot 数が異なる
     * (server = 36 player inv のみ、 client = 36 + 54 viewport) のが正常 — viewport slot は
     * vanilla の slot sync を bypass し、 client が独自 {@code PouchRepo} から動的取得する。
     */
    public void addClientViewportSlot(Slot slot) {
        addSlot(slot);
    }
}

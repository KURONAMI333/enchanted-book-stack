package com.kuronami.portableenchantedbookshelf.menu;

import java.util.ArrayList;
import java.util.List;

import com.kuronami.portableenchantedbookshelf.inventory.PouchInventory;
import com.kuronami.portableenchantedbookshelf.item.PortableEnchantedBookshelfItem;
import com.kuronami.portableenchantedbookshelf.registry.PEBMenuTypes;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * PouchMenu — v3 (AE2 virtual slot + Sophisticated PEB lock + DataComponent 経由 sync)。
 *
 * <p>Server-side で {@link PouchInventory} (256 slot) を持つ。 vanilla の Menu slot としては
 * <b>player inventory 36 のみ</b>を追加 (PEB の内容物 slot は menu に load せず、 client が
 * {@code PouchVirtualSlot} で virtual に表示)。
 *
 * <p>主要 pattern:
 * <ul>
 *   <li>{@link #onHandlerChanged()}: handler 変化時に PEB stack の {@code DataComponents.CONTAINER}
 *       を即書き戻し → vanilla の player slot sync で client にも届く (Tom's clickMenuButton
 *       流儀の vanilla sync 流用、 独自 packet 削減)</li>
 *   <li>{@link #pebSlotIndex} で player inv の PEB 自身 slot を識別、 {@link #canDragTo} で
 *       drag 拒否 + {@link Slot#mayPickup} override で取り出し拒否 (Sophisticated Core 罠回避)</li>
 *   <li>{@link #addClientViewportSlot}: server に無い slot を client menu に add するための
 *       public wrapper (AE2 流儀)</li>
 * </ul>
 *
 * <p>v3 で v2 から変わった点:
 * <ul>
 *   <li>{@code ItemStackHandler} を直 use → {@link PouchInventory} (isItemValid 強化版) に</li>
 *   <li>player inv PEB 自身を lock (新規)</li>
 *   <li>player inventory slot を generic_54.png レイアウトに合わせて再配置 (y 座標)</li>
 * </ul>
 */
public class PouchMenu extends AbstractContainerMenu {

    /** vanilla large chest (generic_54.png 222px) 上の player inventory 配置。 */
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_MAIN_Y = 140;
    private static final int PLAYER_INV_HOTBAR_Y = 198;

    private final Player player;
    private final boolean clientSide;
    /** PEB 自身。 server で内容物 load/save の対象、 client では参照用 snapshot。 */
    private final ItemStack pebStack;
    /** player inventory 内で PEB がある slot index (-1 = PEB が main/off hand 以外 / 見つからず)。 */
    private final int pebSlotIndex;
    /** server-side 256 slot handler。 client では空 placeholder。 */
    private final PouchInventory handler;

    /** vanilla MenuType supplier 用 (client/server 両方で呼ばれる)。 */
    public PouchMenu(int containerId, Inventory playerInventory) {
        super(PEBMenuTypes.POUCH_MENU.get(), containerId);
        this.player = playerInventory.player;
        this.clientSide = playerInventory.player.level().isClientSide();
        this.pebStack = findPebInHand(playerInventory.player);
        this.pebSlotIndex = findPebSlotIndex(playerInventory, this.pebStack);

        this.handler = new PouchInventory();
        if (!clientSide && !pebStack.isEmpty()) {
            loadHandlerFromStack();
        }

        addPlayerInventorySlots(playerInventory);
    }

    /** 内容物 ItemContainerContents → handler に展開。 */
    private void loadHandlerFromStack() {
        ItemContainerContents contents = pebStack.getOrDefault(
                DataComponents.CONTAINER, ItemContainerContents.EMPTY
        );
        int i = 0;
        for (var iter = contents.stream().iterator(); iter.hasNext() && i < handler.getSlots(); ) {
            ItemStack book = iter.next();
            handler.setStackInSlot(i++, book.copy());
        }
    }

    /** generic_54.png レイアウトに合わせた player inventory 36 slot 追加。 */
    private void addPlayerInventorySlots(Inventory inv) {
        // メイン (3 行 × 9 列、 slot index 9..35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIdx = col + row * 9 + 9;
                addSlot(new LockableSlot(inv, slotIdx, PLAYER_INV_X + col * 18,
                        PLAYER_INV_MAIN_Y + row * 18, slotIdx == pebSlotIndex));
            }
        }
        // ホットバー (slot index 0..8)
        for (int col = 0; col < 9; col++) {
            addSlot(new LockableSlot(inv, col, PLAYER_INV_X + col * 18,
                    PLAYER_INV_HOTBAR_Y, col == pebSlotIndex));
        }
    }

    /** PEB 自身の slot は mayPickup=false で取り出し禁止 (Menu open 中)。 */
    private static class LockableSlot extends Slot {
        private final boolean locked;
        public LockableSlot(net.minecraft.world.Container c, int idx, int x, int y, boolean locked) {
            super(c, idx, x, y);
            this.locked = locked;
        }
        @Override public boolean mayPickup(Player player) { return !locked && super.mayPickup(player); }
        @Override public boolean mayPlace(ItemStack stack) { return !locked && super.mayPlace(stack); }
    }

    private static ItemStack findPebInHand(Player player) {
        ItemStack main = player.getMainHandItem();
        if (PortableEnchantedBookshelfItem.is(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (PortableEnchantedBookshelfItem.is(off)) return off;
        return ItemStack.EMPTY;
    }

    /** player inventory の中で PEB が格納されてる slot index を探す (-1 = 見つからず)。 */
    private static int findPebSlotIndex(Inventory inv, ItemStack peb) {
        if (peb.isEmpty()) return -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == peb) return i;
        }
        return -1;
    }

    // ─────────────────────────────────────────────────────────────
    // Accessors
    // ─────────────────────────────────────────────────────────────

    public ItemStack getPebStack() { return pebStack; }
    public Player getPlayer() { return player; }
    public boolean isClientSide() { return clientSide; }
    public PouchInventory getHandler() { return handler; }
    public int getPebSlotIndex() { return pebSlotIndex; }

    /** server-side で handler から非空 ItemStack を順に取り出す (DataComponent 書き戻し用)。 */
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

    /** shift-click: v0.1 では no-op (PEB に slot 無いので vanilla quickMove 不可)。 v0.2 で client 独自実装。 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** Menu close 時に server-side handler を PEB の DataComponent に flush。 */
    @Override
    public void removed(Player player) {
        super.removed(player);
        onHandlerChanged();
    }

    /**
     * Client-only viewport slot (AE2 流儀の virtual slot) を menu に追加する public wrapper。
     *
     * <p>{@code AbstractContainerMenu.addSlot} は protected。 server / client で slot 数が
     * 異なる (server = 36 player inv のみ、 client = 36 + 54 virtual) 設計を実現するための公開メソッド。
     */
    public void addClientViewportSlot(Slot slot) {
        addSlot(slot);
    }

    /**
     * Server-side: handler が変更された時、 PEB stack の {@code DataComponents.CONTAINER} を即書き戻し。
     *
     * <p>PEB stack は player の hand slot (or pebSlotIndex の hotbar slot) にあるので、 stack の
     * DataComponent を更新すると vanilla の slot sync で client にも反映される。 → client は次 tick で
     * {@code PouchRepo.updateFromStack} で内容物 refresh できる (Tom's の clickMenuButton 流儀の
     * vanilla sync 流用、 独自 sync packet 削減)。
     */
    public void onHandlerChanged() {
        if (clientSide || pebStack.isEmpty()) return;
        pebStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(snapshotBooks()));
    }
}

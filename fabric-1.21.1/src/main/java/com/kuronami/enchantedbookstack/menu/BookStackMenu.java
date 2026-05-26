package com.kuronami.enchantedbookstack.menu;

import com.kuronami.enchantedbookstack.inventory.BookStackInventory;
import com.kuronami.enchantedbookstack.item.EnchantedBookStackItem;
import com.kuronami.enchantedbookstack.registry.EBSMenuTypes;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * BookStackMenu — vanilla large chest like (54 slot、 9×6 grid)。
 *
 * <p>各 slot は {@link SlotItemHandler} で {@link BookStackInventory} にバインド、 vanilla の
 * shift-click / drag / hover tooltip すべて vanilla 任せ。
 *
 * <p>shift-click 対応 ({@link #quickMoveStack}):
 * <ul>
 *   <li>player inv → EBS slot: enchanted_book を最初の空 slot へ</li>
 *   <li>EBS slot → player inv: book を player インベントリへ</li>
 * </ul>
 *
 * <p>{@link #onHandlerChanged} で handler 変化を EBS stack の DataComponent に即書き戻し
 * (vanilla の slot sync で client にも反映される)。
 */
public class BookStackMenu extends AbstractContainerMenu {

    /** vanilla large chest GUI レイアウト (generic_54.png 176×222、 9×6 slot grid)。 */
    private static final int POUCH_SLOT_X = 8;
    private static final int POUCH_SLOT_Y = 18;
    private static final int POUCH_ROWS = 6;
    private static final int POUCH_COLS = 9;
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_MAIN_Y = 140;
    private static final int PLAYER_INV_HOTBAR_Y = 198;

    private final Player player;
    private final boolean clientSide;
    private final ItemStack pebStack;
    private final BookStackInventory handler;

    /** vanilla MenuType supplier 用 (client/server 両方で呼ばれる)。 */
    public BookStackMenu(int containerId, Inventory playerInventory) {
        super(EBSMenuTypes.POUCH_MENU, containerId);
        this.player = playerInventory.player;
        this.clientSide = playerInventory.player.level().isClientSide();
        this.pebStack = findPebInHand(playerInventory.player);

        this.handler = new BookStackInventory();
        if (!clientSide && !pebStack.isEmpty()) {
            loadHandlerFromStack();
        }

        addBookStackSlots();
        addPlayerInventorySlots(playerInventory);
    }

    /** EBS stack の {@code DataComponents.CONTAINER} → handler に展開。 */
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

    /** EBS の 54 slot (9×6) を vanilla large chest 配置で追加。 */
    private void addBookStackSlots() {
        for (int row = 0; row < POUCH_ROWS; row++) {
            for (int col = 0; col < POUCH_COLS; col++) {
                final int slotIdx = col + row * POUCH_COLS;
                addSlot(new Slot(handler, slotIdx,
                        POUCH_SLOT_X + col * 18,
                        POUCH_SLOT_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return handler.canPlaceItem(slotIdx, stack);
                    }
                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                    @Override
                    public void onTake(Player p, ItemStack stack) {
                        super.onTake(p, stack);
                        onHandlerChanged();
                    }
                    @Override
                    public void setChanged() {
                        super.setChanged();
                        onHandlerChanged();
                    }
                });
            }
        }
    }

    /** player inventory 36 slot (shulker box GUI と同じ y=84/142)。 */
    private void addPlayerInventorySlots(Inventory inv) {
        // メイン (3 行 × 9 列、 slot 9..35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIdx = col + row * 9 + 9;
                addSlot(new Slot(inv, slotIdx,
                        PLAYER_INV_X + col * 18,
                        PLAYER_INV_MAIN_Y + row * 18) {
                    @Override
                    public boolean mayPickup(Player p) {
                        // EBS 自身は取り出し禁止 (Menu 開いてる間)
                        return inv.getItem(slotIdx) != pebStack && super.mayPickup(p);
                    }
                });
            }
        }
        // ホットバー (slot 0..8)
        for (int col = 0; col < 9; col++) {
            final int slotIdx = col;
            addSlot(new Slot(inv, slotIdx, PLAYER_INV_X + col * 18, PLAYER_INV_HOTBAR_Y) {
                @Override
                public boolean mayPickup(Player p) {
                    return inv.getItem(slotIdx) != pebStack && super.mayPickup(p);
                }
            });
        }
    }

    private static ItemStack findPebInHand(Player player) {
        ItemStack main = player.getMainHandItem();
        if (EnchantedBookStackItem.is(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (EnchantedBookStackItem.is(off)) return off;
        return ItemStack.EMPTY;
    }

    public ItemStack getPebStack() { return pebStack; }
    public boolean isClientSide() { return clientSide; }
    public BookStackInventory getHandler() { return handler; }

    // ─────────────────────────────────────────────────────────────
    // AbstractContainerMenu overrides
    // ─────────────────────────────────────────────────────────────

    @Override
    public boolean stillValid(Player player) {
        return !findPebInHand(player).isEmpty();
    }

    /**
     * shift-click 処理 — vanilla large chest と同じ pattern。
     *
     * <p>slot 0..53 = EBS slot (9×6=54)、 54..89 = player inventory (main 27 + hotbar 9)。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack returned = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return returned;

        ItemStack inSlot = slot.getItem();
        returned = inSlot.copy();

        final int book_stackEnd = BookStackInventory.SIZE; // 54
        final int playerEnd = book_stackEnd + 36;

        if (index < book_stackEnd) {
            // EBS → player inv
            if (!moveItemStackTo(inSlot, book_stackEnd, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // player inv → EBS (enchanted_book のみ、 mayPlace で自動弾く)
            if (!moveItemStackTo(inSlot, 0, book_stackEnd, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (inSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return returned;
    }

    /** Menu close 時に handler を EBS stack の DataComponent に flush。 */
    @Override
    public void removed(Player player) {
        super.removed(player);
        onHandlerChanged();
    }

    /**
     * Server-side: handler 変更時に EBS stack の {@code DataComponents.CONTAINER} 書き戻し。
     * EBS は player hand slot にあるので、 vanilla slot sync で client にも反映。
     */
    public void onHandlerChanged() {
        if (clientSide || pebStack.isEmpty()) return;
        java.util.List<ItemStack> nonEmpty = new java.util.ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty()) nonEmpty.add(s.copy());
        }
        pebStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(nonEmpty));
    }
}

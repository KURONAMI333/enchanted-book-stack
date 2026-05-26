package com.kuronami.portableenchantedbookshelf.menu;

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
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * PouchMenu — v4 (shulker box like、 27 slot)。
 *
 * <p>v3 までの AE2 viewport / 検索 / scroll 流儀を捨てて、 vanilla shulker box と完全同等の
 * 9×3=27 slot menu に。 各 slot は {@link SlotItemHandler} で {@link PouchInventory} に
 * バインド、 vanilla の shift-click / drag / hover tooltip すべて vanilla 任せ。
 *
 * <p>shift-click 対応 ({@link #quickMoveStack}):
 * <ul>
 *   <li>player inv → PEB slot: enchanted_book を最初の空 slot へ</li>
 *   <li>PEB slot → player inv: book を player インベントリへ</li>
 * </ul>
 *
 * <p>{@link #onHandlerChanged} で handler 変化を PEB stack の DataComponent に即書き戻し
 * (vanilla の slot sync で client にも反映される)。
 */
public class PouchMenu extends AbstractContainerMenu {

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
    private final PouchInventory handler;

    /** vanilla MenuType supplier 用 (client/server 両方で呼ばれる)。 */
    public PouchMenu(int containerId, Inventory playerInventory) {
        super(PEBMenuTypes.POUCH_MENU.get(), containerId);
        this.player = playerInventory.player;
        this.clientSide = playerInventory.player.level().isClientSide();
        this.pebStack = findPebInHand(playerInventory.player);

        this.handler = new PouchInventory();
        if (!clientSide && !pebStack.isEmpty()) {
            loadHandlerFromStack();
        }

        addPouchSlots();
        addPlayerInventorySlots(playerInventory);
    }

    /** PEB stack の {@code DataComponents.CONTAINER} → handler に展開。 */
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

    /** PEB の 54 slot (9×6) を vanilla large chest 配置で追加。 */
    private void addPouchSlots() {
        for (int row = 0; row < POUCH_ROWS; row++) {
            for (int col = 0; col < POUCH_COLS; col++) {
                final int slotIdx = col + row * POUCH_COLS;
                addSlot(new SlotItemHandler(handler, slotIdx,
                        POUCH_SLOT_X + col * 18,
                        POUCH_SLOT_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        // enchanted_book のみ受け入れ (PouchInventory.isItemValid に委譲)
                        return handler.isItemValid(slotIdx, stack);
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
                        // PEB 自身は取り出し禁止 (Menu 開いてる間)
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
        if (PortableEnchantedBookshelfItem.is(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (PortableEnchantedBookshelfItem.is(off)) return off;
        return ItemStack.EMPTY;
    }

    public ItemStack getPebStack() { return pebStack; }
    public boolean isClientSide() { return clientSide; }
    public PouchInventory getHandler() { return handler; }

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
     * <p>slot 0..53 = PEB slot (9×6=54)、 54..89 = player inventory (main 27 + hotbar 9)。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack returned = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return returned;

        ItemStack inSlot = slot.getItem();
        returned = inSlot.copy();

        final int pouchEnd = PouchInventory.SIZE; // 54
        final int playerEnd = pouchEnd + 36;

        if (index < pouchEnd) {
            // PEB → player inv
            if (!moveItemStackTo(inSlot, pouchEnd, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // player inv → PEB (enchanted_book のみ、 mayPlace で自動弾く)
            if (!moveItemStackTo(inSlot, 0, pouchEnd, false)) {
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

    /** Menu close 時に handler を PEB stack の DataComponent に flush。 */
    @Override
    public void removed(Player player) {
        super.removed(player);
        onHandlerChanged();
    }

    /**
     * Server-side: handler 変更時に PEB stack の {@code DataComponents.CONTAINER} 書き戻し。
     * PEB は player hand slot にあるので、 vanilla slot sync で client にも反映。
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

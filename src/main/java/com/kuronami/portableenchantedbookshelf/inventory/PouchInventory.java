package com.kuronami.portableenchantedbookshelf.inventory;

import com.kuronami.portableenchantedbookshelf.item.PortableEnchantedBookshelfItem;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * PEB の内容物保持 server-side handler (256 slot 固定)。
 *
 * <p>vanilla {@code ItemContainerContents} (256 hard cap) と容量を合わせる。 各 slot は
 * vanilla enchanted_book ({@code max_stack=1}) のみ受け入れ、 bag-in-bag 禁止 + PEB 内 PEB 禁止。
 *
 * <p>出典 pattern:
 * <ul>
 *   <li>Sophisticated Core {@code StatefulComponentItemHandler} (canonical pattern, 256 slot 上限)</li>
 *   <li>Functional Storage {@code ArmoryCabinetInventoryHandler} (isItemValid で item type 制限)</li>
 *   <li>Functional Storage {@code Capabilities.ItemHandler.ITEM != null} check で bag-in-bag 防止</li>
 * </ul>
 */
public class PouchInventory extends ItemStackHandler {

    /** 上限 = vanilla {@code ItemContainerContents.MAX_SIZE} (256) と一致。 */
    public static final int SIZE = 256;

    public PouchInventory() {
        super(SIZE);
    }

    /**
     * 受け入れ判定:
     * <ol>
     *   <li>vanilla enchanted_book であること (modded enchant でも enchanted_book item は OK)</li>
     *   <li>{@code Capabilities.ItemHandler.ITEM != null} な item (= 別の container item) は禁止 (bag-in-bag 防止)</li>
     *   <li>PEB 自身は禁止 (PEB-in-PEB 完全防止)</li>
     * </ol>
     */
    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) return true; // empty は常に OK (vanilla 慣習)
        if (!stack.is(Items.ENCHANTED_BOOK)) return false;
        if (stack.getCapability(Capabilities.ItemHandler.ITEM) != null) return false;
        if (stack.getItem() instanceof PortableEnchantedBookshelfItem) return false;
        return true;
    }

    /** enchanted_book は vanilla で max_stack=1。 PEB は各 slot 1 個固定。 */
    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    /** non-empty slot 数 (PEB UI で「N 冊保管中」表示等に使う)。 */
    public int countNonEmpty() {
        int n = 0;
        for (int i = 0; i < getSlots(); i++) {
            if (!getStackInSlot(i).isEmpty()) n++;
        }
        return n;
    }
}

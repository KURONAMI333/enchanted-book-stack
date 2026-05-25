package com.kuronami.portableenchantedbookshelf.inventory;

import com.kuronami.portableenchantedbookshelf.item.PortableEnchantedBookshelfItem;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * PEB の内容物保持 server-side handler (vanilla shulker box と同じ 27 slot 固定)。
 *
 * <p>v4 設計: 「shulker box like + enchanted_book only + nest 可能」シンプル仕様。
 * 256 slot + 検索 + scroll の AE2 viewport 流儀は捨て、 vanilla shulker と完全同等の
 * 9×3=27 slot UI で扱う。
 *
 * <p>各 slot は vanilla enchanted_book ({@code max_stack=1}) のみ受け入れ、 bag-in-bag 禁止 +
 * PEB 内 PEB 禁止 (ただし PEB を shulker/backpack に入れることは可能 = nest 可能の核)。
 *
 * <p>出典 pattern:
 * <ul>
 *   <li>Functional Storage {@code ArmoryCabinetInventoryHandler} (isItemValid で item type 制限)</li>
 *   <li>Functional Storage {@code Capabilities.ItemHandler.ITEM != null} check で bag-in-bag 防止</li>
 * </ul>
 */
public class PouchInventory extends ItemStackHandler {

    /** vanilla shulker box と同じ 27 slot (9×3 grid)。 */
    public static final int SIZE = 27;

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

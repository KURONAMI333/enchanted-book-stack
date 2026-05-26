package com.kuronami.enchantedbookstack.inventory;

import com.kuronami.enchantedbookstack.item.EnchantedBookStackItem;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * EBS の内容物保持 server-side handler (vanilla large chest と同じ 54 slot 固定)。
 *
 * <p>各 slot は vanilla enchanted_book ({@code max_stack=1}) のみ受け入れ、 bag-in-bag 禁止 +
 * EBS 内 EBS 禁止 (ただし EBS を shulker/backpack に入れることは可能 = nest 可能の核)。
 *
 * <p>出典 pattern:
 * <ul>
 *   <li>Functional Storage {@code ArmoryCabinetInventoryHandler} (isItemValid で item type 制限)</li>
 *   <li>Functional Storage {@code Capabilities.ItemHandler.ITEM != null} check で bag-in-bag 防止</li>
 * </ul>
 */
public class BookStackInventory extends ItemStackHandler {

    /** vanilla large chest 同等の 54 slot (9×6 grid)。 Sophisticated Backpacks の鉄バックパック級。 */
    public static final int SIZE = 54;

    public BookStackInventory() {
        super(SIZE);
    }

    /**
     * 受け入れ判定:
     * <ol>
     *   <li>vanilla enchanted_book であること (modded enchant でも enchanted_book item は OK)</li>
     *   <li>{@code Capabilities.ItemHandler.ITEM != null} な item (= 別の container item) は禁止 (bag-in-bag 防止)</li>
     *   <li>EBS 自身は禁止 (EBS-in-EBS 完全防止)</li>
     * </ol>
     */
    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) return true; // empty は常に OK (vanilla 慣習)
        if (!stack.is(Items.ENCHANTED_BOOK)) return false;
        if (stack.getCapability(Capabilities.ItemHandler.ITEM) != null) return false;
        if (stack.getItem() instanceof EnchantedBookStackItem) return false;
        return true;
    }

    /** enchanted_book は vanilla で max_stack=1。 EBS は各 slot 1 個固定。 */
    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    /** non-empty slot 数 (EBS UI で「N 冊保管中」表示等に使う)。 */
    public int countNonEmpty() {
        int n = 0;
        for (int i = 0; i < getSlots(); i++) {
            if (!getStackInSlot(i).isEmpty()) n++;
        }
        return n;
    }
}

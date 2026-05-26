package com.kuronami.enchantedbookstack.inventory;

import com.kuronami.enchantedbookstack.item.EnchantedBookStackItem;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * EBS の内容物保持 server-side container (54 slot 固定)。
 *
 * <p>各 slot は vanilla enchanted_book ({@code max_stack=1}) のみ受け入れ、 EBS 自身は禁止
 * (EBS-in-EBS 防止)。 EBS を shulker/backpack に入れることは可能 = nest 可能の核。
 *
 * <p>Fabric では NeoForge の {@code ItemStackHandler} 相当が無いため vanilla
 * {@link SimpleContainer} を継承して同等の挙動を持たせる。
 */
public class BookStackInventory extends SimpleContainer {

    /** vanilla large chest 同等の 54 slot (9×6 grid)。 */
    public static final int SIZE = 54;

    public BookStackInventory() {
        super(SIZE);
    }

    /** enchanted_book のみ受け入れ、 EBS 自身は禁止。 */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (!stack.is(Items.ENCHANTED_BOOK)) return false;
        if (stack.getItem() instanceof EnchantedBookStackItem) return false;
        return true;
    }

    /** enchanted_book は vanilla で max_stack=1。 EBS は各 slot 1 個固定。 */
    @Override
    public int getMaxStackSize() {
        return 1;
    }

    /** non-empty slot 数。 */
    public int countNonEmpty() {
        int n = 0;
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) n++;
        }
        return n;
    }

    /** Forge/NeoForge ItemStackHandler 互換 API (移植用)。 */
    public int getSlots() {
        return getContainerSize();
    }

    public ItemStack getStackInSlot(int slot) {
        return getItem(slot);
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        setItem(slot, stack);
    }
}

package com.kuronami.portableenchantedbookshelf.client.screen;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Client-only virtual slot — AE2 {@code ClientReadOnlySlot} 流儀の自前再実装。
 *
 * <p>vanilla {@link Slot} を継承するが、 server に対応 slot を持たない。 {@link #getItem()} のみ
 * 動的に {@link PouchRepo} から取得 (scrollOffset + viewportIdx)、 vanilla の
 * slot interaction (mayPlace / mayPickup / set / remove / getMaxStackSize) は全 final で封じる。
 *
 * <p>Click 処理は {@link PouchScreen#mouseClicked} で独自 packet 経路を取る。
 *
 * <p>出典:
 * <ul>
 *   <li>AE2 {@code ClientReadOnlySlot} (LGPL 直 copy 不可、 構造のみ自前再実装)</li>
 *   <li>filter 非表示時は {@code slot.x = -2000} で画面外配置 (Sophisticated Core 流儀)</li>
 * </ul>
 *
 * <p>Slot constructor の x/y は **relative coord** (leftPos/topPos 加算なし) — vanilla
 * {@link net.minecraft.client.gui.screens.inventory.AbstractContainerScreen} が描画時に
 * leftPos/topPos を加算する仕様に従う。 v2 で僕が absolute を渡してズレた bug の対策。
 */
public class PouchVirtualSlot extends Slot {

    /** vanilla slot system が触っても害が出ない dummy。 */
    private static final Container EMPTY_CONTAINER = new SimpleContainer(0);

    private final PouchRepo repo;
    /** viewport 内 0-based index (固定)、 内容物は repo の scrollOffset で動的に変わる。 */
    private final int viewportIdx;

    public PouchVirtualSlot(PouchRepo repo, int viewportIdx, int xRelative, int yRelative) {
        super(EMPTY_CONTAINER, 0, xRelative, yRelative);
        this.repo = repo;
        this.viewportIdx = viewportIdx;
    }

    /** repo から動的に取得 (scrollOffset 反映)。 */
    @Override
    public ItemStack getItem() {
        return repo.getViewportSlot(viewportIdx);
    }

    @Override
    public boolean hasItem() {
        return !getItem().isEmpty();
    }

    /** repo の view 内 absolute index (scrollOffset + viewportIdx)。 click 時に server へ送る。 */
    public int currentViewIndex() {
        return repo.getScrollOffset() + viewportIdx;
    }

    // ─────────────────────────────────────────────────────────────
    // vanilla slot interaction を全 final で封じる (AE2 ClientReadOnlySlot 流儀)
    // ─────────────────────────────────────────────────────────────
    @Override public final boolean mayPlace(ItemStack stack) { return false; }
    @Override public final void set(ItemStack stack) { /* no-op */ }
    @Override public final int getMaxStackSize() { return 0; }
    @Override public final ItemStack remove(int amount) { return ItemStack.EMPTY; }
    @Override public final boolean mayPickup(Player player) { return false; }
}

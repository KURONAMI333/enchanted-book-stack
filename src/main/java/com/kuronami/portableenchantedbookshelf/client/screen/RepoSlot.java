package com.kuronami.portableenchantedbookshelf.client.screen;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Virtual client-only slot — AE2 {@code ClientReadOnlySlot} 流儀。
 *
 * <p>vanilla Slot を継承するが、 server に対応 slot を持たない。 {@link #getItem()} のみ
 * 動的に {@link PouchRepo} から取得し、 vanilla の slot interaction
 * ({@code mayPlace} / {@code set} / {@code remove}) は全て封じる ({@code false} or no-op)。
 *
 * <p>Screen 側で {@code mouseClicked} を override して独自 packet 送信で server に
 * insert/extract 依頼する設計。
 */
public class RepoSlot extends Slot {

    /** vanilla slot system がアクセスしようとした時の dummy container。 */
    private static final Container EMPTY_CONTAINER = new SimpleContainer(0);

    private final PouchRepo repo;
    /** viewport 内での 0-based index (0..MAX_VIEWPORT_SLOTS-1)。 */
    private final int viewportIdx;

    public RepoSlot(PouchRepo repo, int viewportIdx, int xPosition, int yPosition) {
        super(EMPTY_CONTAINER, 0, xPosition, yPosition);
        this.repo = repo;
        this.viewportIdx = viewportIdx;
    }

    /** repo の scrollOffset + viewportIdx から動的に lookup。 */
    @Override
    public ItemStack getItem() {
        return repo.getViewportSlot(viewportIdx);
    }

    @Override
    public boolean hasItem() {
        return !getItem().isEmpty();
    }

    /** 現在 viewport 内で何番目の book を表示してるか (Screen.mouseClicked で使う)。 */
    public int currentRepoIndex() {
        return repo.getScrollOffset() + viewportIdx;
    }

    // ─────────────────────────────────────────────────────────────
    // vanilla slot interaction を全て封じる (AE2 ClientReadOnlySlot と同じ)
    // ─────────────────────────────────────────────────────────────

    @Override public final boolean mayPlace(ItemStack stack) { return false; }
    @Override public final void set(ItemStack stack) { /* no-op */ }
    @Override public final int getMaxStackSize() { return 0; }
    @Override public final ItemStack remove(int amount) { return ItemStack.EMPTY; }
    @Override public final boolean mayPickup(Player player) { return false; }
}

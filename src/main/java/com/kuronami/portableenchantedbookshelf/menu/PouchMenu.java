package com.kuronami.portableenchantedbookshelf.menu;

import com.kuronami.portableenchantedbookshelf.data.PouchContents;
import com.kuronami.portableenchantedbookshelf.item.PortableEnchantedBookshelfItem;
import com.kuronami.portableenchantedbookshelf.registry.PEBMenuTypes;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * PEB を右クリックして開く menu。
 *
 * <p>vanilla {@link AbstractContainerMenu} を**最小限**で継承 — slot 持たず、内容物は
 * PEB の {@code ItemStack} の DataComponent から都度読む設計。client/server 間の同期は
 * vanilla の DataComponent 自動同期に乗る。
 *
 * <p>{@link PouchMenu#quickMoveStack} は no-op を返す (slot 無いので shift+click は無効)。
 *
 * <p>v0.1.0 では read-only ビュー。本クラスは Screen への state 提供のみで、
 * 実際の insert / extract は Bundle 流儀の {@link PortableEnchantedBookshelfItem}
 * 側 (overrideStackedOnOther / overrideOtherStackedOnMe) が担う。
 */
public class PouchMenu extends AbstractContainerMenu {

    private final Player player;

    /** vanilla MenuType の MenuSupplier 用 constructor (client / server 両方で呼ばれる)。 */
    public PouchMenu(int containerId, Inventory playerInventory) {
        super(PEBMenuTypes.POUCH_MENU.get(), containerId);
        this.player = playerInventory.player;
    }

    /** プレイヤーが手に持ってる PEB stack を取得。main hand → off hand fallback。 */
    public ItemStack getPouchStack() {
        ItemStack main = player.getMainHandItem();
        if (PortableEnchantedBookshelfItem.is(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (PortableEnchantedBookshelfItem.is(off)) return off;
        return ItemStack.EMPTY;
    }

    /** PEB の中身を取得 (毎フレーム呼ばれる想定、Screen 側で render に使う)。 */
    public PouchContents getContents() {
        ItemStack stack = getPouchStack();
        if (stack.isEmpty()) return PouchContents.EMPTY;
        return PortableEnchantedBookshelfItem.getContents(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return !getPouchStack().isEmpty();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // slot 無し → no-op
    }
}

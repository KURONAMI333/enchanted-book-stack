package com.kuronami.portableenchantedbookshelf.client.screen;

import com.kuronami.portableenchantedbookshelf.menu.PouchMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * PouchScreen — v4 (vanilla shulker box GUI と完全同等)。
 *
 * <p>背景: vanilla {@code shulker_box.png} (176×166) を流用。 9×3=27 slot grid + player
 * inventory のレイアウトを vanilla がそのまま提供。 自前の検索 / scroll / sort は無し。
 *
 * <p>shift-click / drag / hover tooltip / 数字キー hotbar swap など、 vanilla の全 slot
 * interaction が自動で動く ({@link PouchMenu} の slot が vanilla SlotItemHandler ベース)。
 *
 * <p>v4 で v3 から削除されたもの:
 * <ul>
 *   <li>PouchVirtualSlot (vanilla slot で十分)</li>
 *   <li>PouchRepo (検索/sort なし)</li>
 *   <li>PouchSearchBox (検索なし)</li>
 *   <li>SortButton (sort なし)</li>
 *   <li>ExtractByIdxPayload / InsertCarriedPayload (vanilla slot click で OK)</li>
 *   <li>独自 mouseClicked / mouseScrolled / containerTick (全 vanilla 任せ)</li>
 * </ul>
 */
public class PouchScreen extends AbstractContainerScreen<PouchMenu> {

    /** vanilla large chest GUI 背景 (9×6 slot 領域あり)。 */
    private static final ResourceLocation BG_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    /** large chest GUI のサイズ (vanilla 標準: 176×222、 9×6 + player inventory)。 */
    private static final int BG_WIDTH = 176;
    private static final int BG_HEIGHT = 222;

    public PouchScreen(PouchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = BG_HEIGHT - 96 + 2; // vanilla shulker default
    }

    /** 背景画像 (vanilla shulker_box.png) を描画。 vanilla がその上に slot icons / labels を自動描画。 */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BG_TEXTURE, leftPos, topPos, 0, 0, BG_WIDTH, BG_HEIGHT);
    }
}

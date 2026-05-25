package com.kuronami.portableenchantedbookshelf.client.screen;

import com.kuronami.portableenchantedbookshelf.menu.PouchMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * PouchScreen — Phase 2 (AE2 viewport pattern) の **skeleton**。
 *
 * <p>vanilla large chest 背景 ({@code generic_54.png}, 176×222) を流用。 9×6=54 virtual slot
 * の viewport を実装する予定 (本コミットは背景のみ、 viewport/scroll/検索/sort は後続 chunk)。
 */
public class PouchScreen extends AbstractContainerScreen<PouchMenu> {

    /** vanilla large chest GUI 背景 (9×6 slot 領域あり)。 */
    private static final ResourceLocation BG_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    /** large chest GUI のサイズ (vanilla 標準: 176×222、 9×6 = 54 slot + player inventory)。 */
    private static final int BG_WIDTH = 176;
    private static final int BG_HEIGHT = 222;

    public PouchScreen(PouchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        // player inventory label position (BG 内の player inv 位置に合わせる)
        this.inventoryLabelX = 8;
        this.inventoryLabelY = BG_HEIGHT - 96 + 2;
    }

    /** 背景画像 (vanilla large chest) を描画。 */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BG_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }
}

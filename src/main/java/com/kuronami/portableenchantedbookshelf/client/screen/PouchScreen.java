package com.kuronami.portableenchantedbookshelf.client.screen;

import com.kuronami.portableenchantedbookshelf.item.PortableEnchantedBookshelfItem;
import com.kuronami.portableenchantedbookshelf.menu.PouchMenu;
import com.kuronami.portableenchantedbookshelf.network.ExtractByIdxPayload;
import com.kuronami.portableenchantedbookshelf.network.InsertCarriedPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * PouchScreen v3 — AE2 viewport + Sophisticated key handling + Tom's vanilla sync 流儀の統合。
 *
 * <p>背景: vanilla {@code generic_54.png} (176×222、 9×6 slot 領域あり)。 9×6=54 個の
 * {@link PouchVirtualSlot} を **relative 座標**で配置 (vanilla {@code AbstractContainerScreen} が
 * 描画時に leftPos/topPos を加算する仕様、 v2 で僕が absolute を渡してズレた bug の対策)。
 *
 * <p>主要 pattern 出典:
 * <ul>
 *   <li>slot relative 座標 = vanilla {@code AbstractContainerScreen.renderSlot} 仕様</li>
 *   <li>{@link PouchSearchBox} = 3 mod 共通 pattern (Sophisticated/Tom's/AE2) の E キー swallow</li>
 *   <li>{@link SortButton} = IPN scroll-toggle 流儀</li>
 *   <li>{@code repo.setPaused(hasShiftDown())} = AE2 流儀 ({@code MEStorageScreen.java:381})</li>
 *   <li>{@code containerTick} で {@code repo.updateFromStack} = Tom's の vanilla slot sync 流用</li>
 * </ul>
 */
public class PouchScreen extends AbstractContainerScreen<PouchMenu> {

    /** vanilla large chest GUI 背景。 */
    private static final ResourceLocation BG_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    /** large chest GUI size: 176×222、 上半分 = 9×6 slot grid + 下半分 = player inventory。 */
    private static final int BG_WIDTH = 176;
    private static final int BG_HEIGHT = 222;

    /** viewport slot grid の relative 開始座標 (vanilla 標準と一致)。 */
    private static final int VIEWPORT_X = 8;
    private static final int VIEWPORT_Y = 18;
    private static final int VIEWPORT_COLS = 9;
    private static final int VIEWPORT_ROWS = 6;
    private static final int SLOT_SIZE = 18;
    private static final int VISIBLE_SLOTS = VIEWPORT_COLS * VIEWPORT_ROWS;

    /** 検索バー位置 (タイトル右隣、 kura 指摘の「インベントリ整理系流儀」)。 */
    private static final int SEARCH_X = 80;
    private static final int SEARCH_Y = 4;
    private static final int SEARCH_W = 70;
    private static final int SEARCH_H = 12;

    /** Sort button 位置 (検索バー右隣)。 */
    private static final int SORT_BTN_X = 154;
    private static final int SORT_BTN_Y = 4;
    private static final int SORT_BTN_SIZE = 12;

    private PouchRepo repo;
    private PouchSearchBox searchBox;
    private SortButton sortButton;

    public PouchScreen(PouchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = BG_HEIGHT - 96 + 2; // vanilla default for large chest
    }

    @Override
    protected void init() {
        super.init();

        // Repo: PEB stack 直読みで初期化
        this.repo = PouchRepo.fromStack(menu.getPebStack());

        // 検索バー (v3 = PouchSearchBox、 E キー swallow 実装)
        this.searchBox = new PouchSearchBox(
                this.font,
                this.leftPos + SEARCH_X,
                this.topPos + SEARCH_Y,
                SEARCH_W,
                SEARCH_H,
                Component.translatable("item.portableenchantedbookshelf.portable_enchanted_bookshelf.search")
        );
        this.searchBox.setMaxLength(50);
        this.searchBox.setBordered(true);
        this.searchBox.setResponder(text -> this.repo.setSearchPhrase(text));
        addRenderableWidget(this.searchBox); // addRenderableWidget で event chain 自動、 vanilla 標準ルート
        setInitialFocus(this.searchBox);

        // Sort button (scroll-toggle、 IPN 流儀)
        this.sortButton = new SortButton(
                this.leftPos + SORT_BTN_X,
                this.topPos + SORT_BTN_Y,
                SORT_BTN_SIZE,
                SORT_BTN_SIZE,
                this.repo
        );
        addRenderableWidget(this.sortButton);

        // 9×6 = 54 virtual slot を menu に追加 (relative 座標 — v2 bug 修正)
        for (int row = 0; row < VIEWPORT_ROWS; row++) {
            for (int col = 0; col < VIEWPORT_COLS; col++) {
                int viewportIdx = col + row * VIEWPORT_COLS;
                int xRel = VIEWPORT_X + col * SLOT_SIZE; // leftPos 加算しない
                int yRel = VIEWPORT_Y + row * SLOT_SIZE; // topPos 加算しない
                this.menu.addClientViewportSlot(new PouchVirtualSlot(repo, viewportIdx, xRel, yRel));
            }
        }
    }

    /** 背景画像 (vanilla generic_54.png) を描画。 */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BG_TEXTURE, leftPos, topPos, 0, 0, BG_WIDTH, BG_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // shift 中は repo を pause (view 揺れ抑止、 AE2 流儀)
        if (repo != null) repo.setPaused(hasShiftDown());
    }

    /**
     * Tick ごと repo を PEB stack から refresh (server 側変更を vanilla slot sync 経由で取り込む)。
     */
    @Override
    public void containerTick() {
        super.containerTick();
        if (repo != null) repo.updateFromStack(menu.getPebStack());
    }

    /** マウスホイール: 行単位 (= VIEWPORT_COLS 個) scroll。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseInViewportArea(mouseX, mouseY)) {
            int delta = (scrollY > 0 ? -1 : 1) * VIEWPORT_COLS;
            this.repo.setScrollOffset(this.repo.getScrollOffset() + delta, VISIBLE_SLOTS);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isMouseInViewportArea(double mouseX, double mouseY) {
        return mouseX >= leftPos + VIEWPORT_X
                && mouseX < leftPos + VIEWPORT_X + VIEWPORT_COLS * SLOT_SIZE
                && mouseY >= topPos + VIEWPORT_Y
                && mouseY < topPos + VIEWPORT_Y + VIEWPORT_ROWS * SLOT_SIZE;
    }

    /**
     * Viewport slot click → server packet。 vanilla AbstractContainerScreen.mouseClicked の
     * 標準 slot 処理 (RepoSlot は ReadOnly で動かないので) を bypass して、 専用ハンドラへ。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseInViewportArea(mouseX, mouseY)) {
            PouchVirtualSlot clicked = findClickedVirtualSlot(mouseX, mouseY);
            if (clicked != null) {
                handleViewportClick(clicked);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * mouseX/Y は absolute、 slot.x/y は relative なので比較時に leftPos/topPos を加算。
     * (v2 で僕が slot 側に absolute 渡してた bug の正解)
     */
    private PouchVirtualSlot findClickedVirtualSlot(double mouseX, double mouseY) {
        for (var s : menu.slots) {
            if (s instanceof PouchVirtualSlot p) {
                int ax = leftPos + s.x;
                int ay = topPos + s.y;
                if (mouseX >= ax && mouseX < ax + 16 && mouseY >= ay && mouseY < ay + 16) {
                    return p;
                }
            }
        }
        return null;
    }

    private void handleViewportClick(PouchVirtualSlot slot) {
        ItemStack carried = menu.getCarried();

        // cursor に enchanted_book → insert
        if (PortableEnchantedBookshelfItem.isAcceptableBook(carried)) {
            PacketDistributor.sendToServer(InsertCarriedPayload.INSTANCE);
            playClickSound();
            return;
        }

        // cursor 空 + slot に book → extract (shift で全部)
        ItemStack slotItem = slot.getItem();
        if (slotItem.isEmpty()) return;

        int viewIdx = slot.currentViewIndex();
        int handlerIdx = this.repo.viewToRawIndex(viewIdx);
        if (handlerIdx < 0) return; // race: view 更新中

        int count = hasShiftDown() ? ExtractByIdxPayload.EXTRACT_ALL : 1;
        PacketDistributor.sendToServer(new ExtractByIdxPayload(handlerIdx, count));
        playClickSound();
    }

    private void playClickSound() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
        }
    }
}

package com.kuronami.portableenchantedbookshelf.client.screen;

import com.kuronami.portableenchantedbookshelf.item.PortableEnchantedBookshelfItem;
import com.kuronami.portableenchantedbookshelf.menu.PouchMenu;
import com.kuronami.portableenchantedbookshelf.network.ExtractByIdxPayload;
import com.kuronami.portableenchantedbookshelf.network.InsertCarriedPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * PouchScreen — Phase 2 (AE2 viewport pattern)。
 *
 * <p>vanilla large chest 背景 ({@code generic_54.png}, 176×222) を流用、 9×6=54 個の
 * {@link RepoSlot} を viewport として配置。 {@link PouchRepo} の scrollOffset を
 * マウスホイールで上下、 同じ slot 位置に違う book を表示する。
 *
 * <p>UI:
 * <ul>
 *   <li>タイトル (y=6)</li>
 *   <li>検索バー (y=15、 vanilla {@code EditBox}、 自前 texture 不要)</li>
 *   <li>9×6 = 54 RepoSlot (y=33..123 を 6 行)</li>
 *   <li>player inventory (y=140..)</li>
 * </ul>
 *
 * <p>v0.2: hover preview + click extract (network packet) は次 chunk。
 */
public class PouchScreen extends AbstractContainerScreen<PouchMenu> {

    /** vanilla large chest GUI 背景 (9×6 slot 領域あり)。 */
    private static final ResourceLocation BG_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    /** large chest GUI のサイズ (vanilla 標準: 176×222、 9×6 + player inventory)。 */
    private static final int BG_WIDTH = 176;
    private static final int BG_HEIGHT = 222;

    /** viewport の行・列・slot 数 (= 54)。 */
    private static final int VIEWPORT_COLS = 9;
    private static final int VIEWPORT_ROWS = 6;
    private static final int VIEWPORT_SLOTS = VIEWPORT_COLS * VIEWPORT_ROWS;

    /** vanilla 標準 slot サイズ (18×18)。 */
    private static final int SLOT_SIZE = 18;

    /** viewport slot grid の左上座標 (BG 内相対)。 */
    private static final int VIEWPORT_X = 8;
    private static final int VIEWPORT_Y = 33;

    /** 検索バー位置 (タイトル直下、 slot grid 上)。 */
    private static final int SEARCH_X = 8;
    private static final int SEARCH_Y = 15;
    private static final int SEARCH_W = 160;
    private static final int SEARCH_H = 12;

    private PouchRepo repo;
    private EditBox searchBar;

    public PouchScreen(PouchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = BG_HEIGHT - 96 + 2;
    }

    @Override
    protected void init() {
        super.init();

        // Repo を menu の PEB stack から初期化
        this.repo = PouchRepo.fromStack(menu.getPebStack());

        // 検索バー (vanilla EditBox widget)
        this.searchBar = new EditBox(
                this.font,
                this.leftPos + SEARCH_X,
                this.topPos + SEARCH_Y,
                SEARCH_W,
                SEARCH_H,
                Component.translatable("item.portableenchantedbookshelf.portable_enchanted_bookshelf.search")
        );
        this.searchBar.setMaxLength(50);
        this.searchBar.setBordered(true);
        this.searchBar.setResponder(text -> this.repo.setSearch(text));
        this.addRenderableWidget(this.searchBar);
        this.setInitialFocus(this.searchBar);

        // 9×6 viewport slot を menu に追加 (RepoSlot は client-only、 server 同期しない)
        // 注: 既存の player inventory slot より後に add するので、 slot index は 36 以降
        for (int row = 0; row < VIEWPORT_ROWS; row++) {
            for (int col = 0; col < VIEWPORT_COLS; col++) {
                int viewportIdx = col + row * VIEWPORT_COLS;
                int x = this.leftPos + VIEWPORT_X + col * SLOT_SIZE;
                int y = this.topPos + VIEWPORT_Y + row * SLOT_SIZE;
                RepoSlot slot = new RepoSlot(repo, viewportIdx, x, y);
                this.menu.addClientViewportSlot(slot);
            }
        }
    }

    /** 背景画像 (vanilla large chest) を描画。 */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BG_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    /** マウスホイール: viewport scroll (1 行ずつ = COLS 個ずつ)。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseInViewportArea(mouseX, mouseY)) {
            int direction = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
            int newOffset = this.repo.getScrollOffset() + direction * VIEWPORT_COLS;
            this.repo.setScrollOffset(newOffset, VIEWPORT_SLOTS);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isMouseInViewportArea(double mouseX, double mouseY) {
        return mouseX >= leftPos + VIEWPORT_X
                && mouseX <= leftPos + VIEWPORT_X + VIEWPORT_COLS * SLOT_SIZE
                && mouseY >= topPos + VIEWPORT_Y
                && mouseY <= topPos + VIEWPORT_Y + VIEWPORT_ROWS * SLOT_SIZE;
    }

    /**
     * Viewport slot click → server に packet 送信。
     *
     * <p>挙動:
     * <ul>
     *   <li>cursor 空 + slot に book あり: {@link ExtractByIdxPayload} で取り出し
     *       (shift+click で全部、 通常クリックで 1 個)</li>
     *   <li>cursor が enchanted_book + slot 空 or 何か: {@link InsertCarriedPayload} で投入</li>
     * </ul>
     *
     * <p>RepoSlot は vanilla の slot system を bypass してるので、 vanilla の mouseClicked は
     * 何もしない。 ここで完全自前 click handling。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseInViewportArea(mouseX, mouseY)) {
            RepoSlot clicked = findClickedRepoSlot(mouseX, mouseY);
            if (clicked != null) {
                handleViewportClick(clicked);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private RepoSlot findClickedRepoSlot(double mouseX, double mouseY) {
        for (var slot : menu.slots) {
            if (slot instanceof RepoSlot rs) {
                int sx = rs.x;
                int sy = rs.y;
                if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                    return rs;
                }
            }
        }
        return null;
    }

    private void handleViewportClick(RepoSlot slot) {
        ItemStack carried = menu.getCarried();

        if (PortableEnchantedBookshelfItem.isAcceptableBook(carried)) {
            // carried = book → insert
            PacketDistributor.sendToServer(InsertCarriedPayload.INSTANCE);
            playInsertSound();
            return;
        }

        // carried 空 → click extract
        ItemStack slotItem = slot.getItem();
        if (slotItem.isEmpty()) return;

        int handlerIdx = slot.currentRepoIndex();
        int count = hasShiftDown() ? ExtractByIdxPayload.EXTRACT_ALL : 1;
        PacketDistributor.sendToServer(new ExtractByIdxPayload(handlerIdx, count));
        playInsertSound();
    }

    private void playInsertSound() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
        }
    }

    /**
     * Tick ごと repo を PEB stack から refresh。 server 側 handler の変更は
     * {@link PouchMenu#onHandlerChanged} 経由で stack の DataComponent に書き込まれ、
     * vanilla の player slot sync で client にも届く。 ここで repo に反映。
     */
    @Override
    public void containerTick() {
        super.containerTick();
        if (repo != null) {
            repo.updateFromStack(menu.getPebStack());
        }
    }
}

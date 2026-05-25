package com.kuronami.portableenchantedbookshelf.client.screen;

import com.kuronami.portableenchantedbookshelf.client.screen.PouchRepo.SortMode;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Sort モード切替ボタン — IPN ({@code SortingButtonCollectionWidget.kt:157-180}) の
 * scroll-toggle pattern。
 *
 * <p>左クリック: 次の sort モードへ循環、 ホイール: 上下で前後の sort モードへ。
 * UI 省スペース化のため複数ボタン並べず 1 つに集約。
 *
 * <p>表示はモード名の頭 1-3 文字をテキストで (例 NAME / LVL / CNT / REC)。 vanilla の
 * Button より小型 (12×12)。
 */
public class SortButton extends AbstractWidget {

    private final PouchRepo repo;

    public SortButton(int x, int y, int width, int height, PouchRepo repo) {
        super(x, y, width, height, Component.empty());
        this.repo = repo;
        updateMessage();
    }

    private void updateMessage() {
        String label = switch (repo.getSortMode()) {
            case NAME_ASC -> "A↓";
            case LEVEL_DESC -> "Lv";
            case COUNT_DESC -> "##";
            case RECENT -> "★";
        };
        setMessage(Component.literal(label));
    }

    /** 左クリック: 次のモードへ循環。 */
    @Override
    public void onClick(double mouseX, double mouseY) {
        cycle(1);
    }

    /** ホイール: scroll で前後 (IPN scroll-toggle 流儀)。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseOver(mouseX, mouseY)) {
            cycle(scrollY > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    private void cycle(int direction) {
        SortMode[] all = SortMode.values();
        int idx = (repo.getSortMode().ordinal() + direction + all.length) % all.length;
        repo.setSortMode(all[idx]);
        updateMessage();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // シンプル: 背景を hover 時に半透明白で highlight、 中央にテキスト
        int bgColor = isHovered() ? 0x60FFFFFF : 0x60000000;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
        graphics.drawCenteredString(
                net.minecraft.client.Minecraft.getInstance().font,
                getMessage(),
                getX() + getWidth() / 2,
                getY() + (getHeight() - 8) / 2,
                0xFFFFFFFF
        );
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, getMessage());
    }
}

package com.kuronami.portableenchantedbookshelf.client.screen;

import java.util.List;

import com.kuronami.portableenchantedbookshelf.data.EnchantEntry;
import com.kuronami.portableenchantedbookshelf.data.PouchContents;
import com.kuronami.portableenchantedbookshelf.menu.PouchMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * PEB の中身を開いた Screen。
 *
 * <p>背景画像は vanilla の {@code shulker_box.png} をそのまま流用 (新規 texture 0 個)。
 * 上に PEB の内容物を tree list で描画。
 *
 * <p>v0.1.0 は read-only ビュー: 内容物の一覧表示 + ヘッダ。検索バー / sort / 個別取り出しは
 * Phase 1 後半で追加予定。
 */
public class PouchScreen extends AbstractContainerScreen<PouchMenu> {

    /** vanilla shulker box GUI 背景。 */
    private static final ResourceLocation BG_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/shulker_box.png");

    /** shulker box GUI のサイズ (vanilla 標準)。 */
    private static final int BG_WIDTH = 176;
    private static final int BG_HEIGHT = 166;

    /** リスト描画開始 y (背景の slot 領域内)。 */
    private static final int LIST_X_OFFSET = 8;
    private static final int LIST_Y_OFFSET = 18;
    private static final int LINE_HEIGHT = 10;

    /** リスト表示行数上限 (shulker box の slot 領域 ~54px に収まる目安)。 */
    private static final int MAX_VISIBLE_LINES = 5;

    public PouchScreen(PouchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = BG_HEIGHT - 96 + 2;
    }

    /** 背景画像 (vanilla shulker box) を描画。 */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BG_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    /** vanilla labels (タイトル + プレイヤーインベントリ "Inventory") の上に内容物リスト描画。 */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);

        PouchContents contents = menu.getContents();
        List<EnchantEntry> entries = contents.view();

        if (entries.isEmpty()) {
            graphics.drawString(
                    font,
                    Component.literal("(empty)").withStyle(ChatFormatting.GRAY),
                    LIST_X_OFFSET,
                    LIST_Y_OFFSET,
                    0xFFFFFF,
                    false
            );
            return;
        }

        // 内容物 line を描画 (上限まで)
        var registries = (this.minecraft != null && this.minecraft.level != null)
                ? this.minecraft.level.registryAccess()
                : null;

        int shown = Math.min(entries.size(), MAX_VISIBLE_LINES);
        for (int i = 0; i < shown; i++) {
            EnchantEntry e = entries.get(i);
            Component line = formatEntryLine(e, registries);
            graphics.drawString(
                    font,
                    line,
                    LIST_X_OFFSET,
                    LIST_Y_OFFSET + i * LINE_HEIGHT,
                    0xFFFFFF,
                    false
            );
        }

        if (entries.size() > MAX_VISIBLE_LINES) {
            Component more = Component.literal("...and " + (entries.size() - MAX_VISIBLE_LINES) + " more")
                    .withStyle(ChatFormatting.DARK_GRAY);
            graphics.drawString(
                    font,
                    more,
                    LIST_X_OFFSET,
                    LIST_Y_OFFSET + shown * LINE_HEIGHT,
                    0xFFFFFF,
                    false
            );
        }
    }

    private Component formatEntryLine(EnchantEntry entry, HolderLookup.Provider registries) {
        Component name = resolveEnchantmentName(entry, registries);
        ChatFormatting color = entry.isCurse() ? ChatFormatting.RED : ChatFormatting.WHITE;
        return Component.empty()
                .append(name.copy().withStyle(color))
                .append(Component.literal(" " + romanOrPlain(entry.level())).withStyle(color))
                .append(Component.literal(" × " + entry.count()).withStyle(ChatFormatting.GRAY));
    }

    private static Component resolveEnchantmentName(EnchantEntry entry, HolderLookup.Provider registries) {
        if (registries == null) {
            return Component.literal(entry.enchantId().toString());
        }
        var enchantRegistry = registries.lookup(Registries.ENCHANTMENT).orElse(null);
        if (enchantRegistry == null) {
            return Component.literal(entry.enchantId().toString());
        }
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, entry.enchantId());
        Holder<Enchantment> holder = enchantRegistry.get(key).orElse(null);
        if (holder == null) {
            return Component.literal(entry.enchantId().toString());
        }
        return Component.translatable(holder.value().description().getString());
    }

    private static String romanOrPlain(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> level <= 0 ? Integer.toString(level) : "Lvl " + level;
        };
    }
}

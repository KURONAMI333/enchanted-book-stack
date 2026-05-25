package com.kuronami.portableenchantedbookshelf.client.screen;

import java.util.List;

import com.kuronami.portableenchantedbookshelf.data.EnchantEntry;
import com.kuronami.portableenchantedbookshelf.data.PouchContents;
import com.kuronami.portableenchantedbookshelf.menu.PouchMenu;
import com.kuronami.portableenchantedbookshelf.network.ExtractBookPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.neoforge.network.PacketDistributor;
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
 * 上に PEB の内容物を tree list で描画、上部に vanilla {@link EditBox} で検索バー。
 *
 * <p>v0.1.0: read-only ビュー + 検索フィルタ。
 * Phase 1.5: entry クリックで個別取り出し (network packet)、sort 機能。
 */
public class PouchScreen extends AbstractContainerScreen<PouchMenu> {

    /** vanilla shulker box GUI 背景。 */
    private static final ResourceLocation BG_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/shulker_box.png");

    /** shulker box GUI のサイズ (vanilla 標準)。 */
    private static final int BG_WIDTH = 176;
    private static final int BG_HEIGHT = 166;

    /** 検索バー位置 (タイトル直下、slot 領域上端)。 */
    private static final int SEARCH_X = 8;
    private static final int SEARCH_Y = 14;
    private static final int SEARCH_W = 100;
    private static final int SEARCH_H = 12;

    /** リスト描画位置 (検索バー直下から)。 */
    private static final int LIST_X = 8;
    private static final int LIST_Y = 30;
    private static final int LINE_HEIGHT = 10;

    /** リスト表示行数上限 (検索バー下 ~40px に収まる目安)。 */
    private static final int MAX_VISIBLE_LINES = 4;

    private EditBox searchBar;
    /** 現在の検索クエリ (lowercase, 空文字 = フィルタ無し)。 */
    private String searchQuery = "";
    /** スクロール位置 (0 = リスト先頭表示)。 */
    private int scrollOffset = 0;

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

        // 検索バー (vanilla EditBox widget、自前 texture 不要)
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
        this.searchBar.setResponder(text -> {
            this.searchQuery = text.toLowerCase();
            this.scrollOffset = 0; // 検索クエリ変化で先頭にリセット
        });
        this.addRenderableWidget(this.searchBar);
        this.setInitialFocus(this.searchBar);
    }

    /** マウスホイールでリストをスクロール (背景上のホバー時のみ反応)。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseInListArea(mouseX, mouseY)) {
            // 上スクロール (scrollY > 0) で offset 減、下スクロール (scrollY < 0) で offset 増
            int delta = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
            updateScrollOffset(this.scrollOffset + delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isMouseInListArea(double mouseX, double mouseY) {
        return mouseX >= leftPos + LIST_X
                && mouseX <= leftPos + LIST_X + 160
                && mouseY >= topPos + LIST_Y
                && mouseY <= topPos + LIST_Y + MAX_VISIBLE_LINES * LINE_HEIGHT;
    }

    private void updateScrollOffset(int newOffset) {
        // total はクエリ変化で変わるので render 時 clamp、ここでは負値だけ止める
        this.scrollOffset = Math.max(0, newOffset);
    }

    /**
     * リスト entry を左クリック → 該当 enchant 本を 1 冊取り出す ({@link ExtractBookPayload})。
     * vanilla の slot 系には無い独自 click 処理なので {@code mouseClicked} で捕まえる。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseInListArea(mouseX, mouseY)) {
            int lineIdx = (int) ((mouseY - topPos - LIST_Y) / LINE_HEIGHT);
            if (lineIdx < 0 || lineIdx >= MAX_VISIBLE_LINES) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            // 現在の filtered list を再構築 (race 安全のため毎回計算)
            var registries = (this.minecraft != null && this.minecraft.level != null)
                    ? this.minecraft.level.registryAccess()
                    : null;
            List<EnchantEntry> filtered = filterEntries(
                    menu.getContents().view(), this.searchQuery, registries
            );
            int absIdx = this.scrollOffset + lineIdx;
            if (absIdx < 0 || absIdx >= filtered.size()) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            EnchantEntry clicked = filtered.get(absIdx);
            // server 側で extract 実行 (book を player インベントリへ)
            PacketDistributor.sendToServer(new ExtractBookPayload(
                    clicked.enchantId(), clicked.level(), clicked.isCurse()
            ));
            // 効果音 (vanilla book page turn)
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.playSound(
                        net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F
                );
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BG_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    /** vanilla labels の上に内容物リスト描画 (検索フィルタ適用)。 */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);

        PouchContents contents = menu.getContents();
        var registries = (this.minecraft != null && this.minecraft.level != null)
                ? this.minecraft.level.registryAccess()
                : null;

        List<EnchantEntry> filteredEntries = filterEntries(contents.view(), this.searchQuery, registries);

        if (filteredEntries.isEmpty()) {
            String label = contents.isEmpty() ? "(empty)" : "(no match)";
            graphics.drawString(
                    font,
                    Component.literal(label).withStyle(ChatFormatting.GRAY),
                    LIST_X,
                    LIST_Y,
                    0xFFFFFF,
                    false
            );
            return;
        }

        // スクロール位置の clamp (filtered 件数に応じて)
        int maxScroll = Math.max(0, filteredEntries.size() - MAX_VISIBLE_LINES);
        if (this.scrollOffset > maxScroll) this.scrollOffset = maxScroll;

        int shown = Math.min(filteredEntries.size() - this.scrollOffset, MAX_VISIBLE_LINES);
        for (int i = 0; i < shown; i++) {
            EnchantEntry e = filteredEntries.get(this.scrollOffset + i);
            Component line = formatEntryLine(e, registries);
            graphics.drawString(
                    font,
                    line,
                    LIST_X,
                    LIST_Y + i * LINE_HEIGHT,
                    0xFFFFFF,
                    false
            );
        }

        // スクロール indicator (上 / 下にまだあるよ表示)
        if (this.scrollOffset > 0) {
            graphics.drawString(
                    font,
                    Component.literal("▲").withStyle(ChatFormatting.GRAY),
                    LIST_X + 145,
                    LIST_Y,
                    0xFFFFFF,
                    false
            );
        }
        if (this.scrollOffset + MAX_VISIBLE_LINES < filteredEntries.size()) {
            graphics.drawString(
                    font,
                    Component.literal("▼").withStyle(ChatFormatting.GRAY),
                    LIST_X + 145,
                    LIST_Y + (MAX_VISIBLE_LINES - 1) * LINE_HEIGHT,
                    0xFFFFFF,
                    false
            );
        }
    }

    /**
     * クエリで entries を filter。マッチ条件:
     * <ul>
     *   <li>localized name (例: "Fortune", "幸運") を partial match</li>
     *   <li>enchant ID (例: "minecraft:fortune") を partial match</li>
     *   <li>ローマ数字レベル (例: "III") を完全一致</li>
     * </ul>
     * クエリ空文字なら全件返す。
     */
    private static List<EnchantEntry> filterEntries(
            List<EnchantEntry> all, String lowerQuery, HolderLookup.Provider registries
    ) {
        if (lowerQuery == null || lowerQuery.isBlank()) return all;
        return all.stream()
                .filter(e -> matchesQuery(e, lowerQuery, registries))
                .toList();
    }

    private static boolean matchesQuery(EnchantEntry e, String lowerQuery, HolderLookup.Provider registries) {
        // localized name
        String name = resolveEnchantmentName(e, registries).getString().toLowerCase();
        if (name.contains(lowerQuery)) return true;

        // enchant ID
        String idStr = e.enchantId().toString().toLowerCase();
        if (idStr.contains(lowerQuery)) return true;

        // ローマ数字レベル (例: "III") を完全一致
        String levelRoman = romanOrPlain(e.level()).toLowerCase();
        return levelRoman.equals(lowerQuery);
    }

    private static Component formatEntryLine(EnchantEntry entry, HolderLookup.Provider registries) {
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

package com.kuronami.portableenchantedbookshelf.client.tooltip;

import java.util.List;

import com.kuronami.portableenchantedbookshelf.data.EnchantEntry;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * {@link PouchTooltip} の client-side レンダラ。
 *
 * <p>vanilla Bundle / Shulker preview と同じ流儀: マウスホバー時に小さな popup を出し、
 * PEB の中身を一覧表示する。tooltip 本体 (背景・枠) は vanilla が描画、
 * 本クラスは内側のテキストレンダリングを担当 → **新規 texture 0 個**。
 *
 * <p>v0.1.0 表示仕様:
 * <ul>
 *   <li>1 行ごとに「{enchant 名} {ローマ数字レベル} × {count}」</li>
 *   <li>Curse 系は赤色、通常は白色</li>
 *   <li>最大表示行数 = {@value #MAX_LINES}、超過分は「...他 N 種」</li>
 * </ul>
 */
public class ClientPouchTooltip implements ClientTooltipComponent {

    private static final int MAX_LINES = 12;
    private static final int LINE_HEIGHT = 10;

    private final List<EnchantEntry> entries;
    private final int totalBooks;
    /** Cached registries access (HolderLookup.Provider) — non-null after first use; null until resolved. */
    private final HolderLookup.Provider registries;

    public ClientPouchTooltip(PouchTooltip data) {
        this.totalBooks = data.contents().totalBookCount();
        this.registries = net.minecraft.client.Minecraft.getInstance().level != null
                ? net.minecraft.client.Minecraft.getInstance().level.registryAccess()
                : null;
        // PouchScreen と同じ sort (Name asc → Level asc) を適用、UX 一貫性確保。
        this.entries = data.contents().view().stream()
                .sorted(
                        java.util.Comparator
                                .<EnchantEntry, String>comparing(
                                        e -> resolveEnchantmentNameStatic(e, registries).getString().toLowerCase()
                                )
                                .thenComparingInt(EnchantEntry::level)
                )
                .toList();
    }

    /** ctor 内で使うため static 化したヘルパー。後段の resolveEnchantmentName と同実装。 */
    private static Component resolveEnchantmentNameStatic(EnchantEntry entry, HolderLookup.Provider registries) {
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

    @Override
    public int getHeight() {
        int shown = Math.min(entries.size(), MAX_LINES);
        int lines = 1 + shown; // header + entries
        if (entries.size() > MAX_LINES) lines++;       // "...他 N 種"
        if (entries.isEmpty()) lines = 1;              // "Empty"
        return lines * LINE_HEIGHT + 2;
    }

    @Override
    public int getWidth(Font font) {
        int max = font.width(headerLine());
        int shown = Math.min(entries.size(), MAX_LINES);
        for (int i = 0; i < shown; i++) {
            int w = font.width(formatEntryLine(entries.get(i)));
            if (w > max) max = w;
        }
        if (entries.size() > MAX_LINES) {
            max = Math.max(max, font.width(moreLine(entries.size() - MAX_LINES)));
        }
        return max;
    }

    @Override
    public void renderText(Font font, int x, int y, org.joml.Matrix4f matrix,
                           net.minecraft.client.renderer.MultiBufferSource.BufferSource buffer) {
        int line = 0;

        // Header
        Component header = Component.literal(headerLine())
                .withStyle(entries.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.AQUA);
        font.drawInBatch(header, x, y + line * LINE_HEIGHT, 0xFFFFFFFF, false, matrix, buffer,
                Font.DisplayMode.NORMAL, 0, 0xF000F0);
        line++;

        if (entries.isEmpty()) return;

        int shown = Math.min(entries.size(), MAX_LINES);
        for (int i = 0; i < shown; i++) {
            EnchantEntry e = entries.get(i);
            Component lineText = formatEntryLineComponent(e);
            font.drawInBatch(lineText, x, y + line * LINE_HEIGHT, 0xFFFFFFFF, false, matrix, buffer,
                    Font.DisplayMode.NORMAL, 0, 0xF000F0);
            line++;
        }

        if (entries.size() > MAX_LINES) {
            Component moreText = Component.literal(moreLine(entries.size() - MAX_LINES))
                    .withStyle(ChatFormatting.DARK_GRAY);
            font.drawInBatch(moreText, x, y + line * LINE_HEIGHT, 0xFFFFFFFF, false, matrix, buffer,
                    Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }
    }

    /** {@link ClientTooltipComponent#renderImage} はオプショナル、本クラスでは未使用 (テキストのみ)。 */
    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        // no-op: 背景 / 枠は vanilla tooltip が描画、内部 image は無し
    }

    // ─────────────────────────────────────────────────────────────
    // 表示文字列の組み立て
    // ─────────────────────────────────────────────────────────────

    private String headerLine() {
        if (entries.isEmpty()) {
            return "(empty)";
        }
        return totalBooks + " enchanted books";
    }

    private String formatEntryLine(EnchantEntry entry) {
        return resolveEnchantmentName(entry).getString() + " " + romanOrPlain(entry.level()) + " × " + entry.count();
    }

    private Component formatEntryLineComponent(EnchantEntry entry) {
        Component name = resolveEnchantmentName(entry);
        ChatFormatting color = entry.isCurse() ? ChatFormatting.RED : ChatFormatting.WHITE;
        return Component.empty()
                .append(name.copy().withStyle(color))
                .append(Component.literal(" " + romanOrPlain(entry.level())).withStyle(color))
                .append(Component.literal(" × " + entry.count()).withStyle(ChatFormatting.GRAY));
    }

    private String moreLine(int rest) {
        return "...and " + rest + " more";
    }

    private Component resolveEnchantmentName(EnchantEntry entry) {
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

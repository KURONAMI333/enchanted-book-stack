package com.kuronami.portableenchantedbookshelf.client.tooltip;

import com.kuronami.portableenchantedbookshelf.data.PouchContents;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * PEB の hover preview 用 {@link TooltipComponent}。
 *
 * <p>vanilla Bundle と同じ pattern (server-side data + client-side renderer separation)。
 * Common 側で {@code PouchContents} を保持するだけのマーカー record。
 * 実際の描画は {@link ClientPouchTooltip} (client 専用)。
 *
 * <p>{@link net.minecraft.world.item.Item#getTooltipImage} で返すと、vanilla の
 * tooltip システムが {@code RegisterClientTooltipComponentFactoriesEvent} で登録された
 * factory 経由で client renderer を生成する。
 */
public record PouchTooltip(PouchContents contents) implements TooltipComponent {
}

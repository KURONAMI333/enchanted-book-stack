package com.kuronami.enchantedbookstack.registry;

import java.util.Optional;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;

/**
 * EBS の司書交易追加。
 *
 * <p>vanilla {@code librarian} (司書) villager の Level 4 (Expert) trade list に EBS を追加。
 *
 * <p>取引内容: <b>24 emerald + 4 leather</b> ↔ <b>Enchanted Book Stack ×1</b>
 *
 * <p>価格設計の根拠:
 * <ul>
 *   <li>24 emerald = vanilla Mending book trade (30 emerald) に近い中位帯</li>
 *   <li>4 leather = レシピと同じ素材 (本 + 革)、craft 派と並列入手路を象徴</li>
 *   <li>paper は SPEC で「+4 paper」と書いていたが、vanilla {@link MerchantOffer} は
 *       primary + secondary の 2 素材しか持てない API 制約のため、leather のみに簡素化</li>
 * </ul>
 *
 * <p>Trade 補助パラメータ:
 * <ul>
 *   <li>{@code maxUses=12}: 1 司書から 12 回まで補充可</li>
 *   <li>{@code xp=15}: villager XP 増加 (Expert→Master 昇格に寄与)</li>
 *   <li>{@code priceMultiplier=0.05F}: 評判ボーナス標準値</li>
 * </ul>
 */
public final class EBSTrades {

    private EBSTrades() {}

    /** {@code MinecraftForge.EVENT_BUS.addListener} で登録される。 */
    public static void onRegisterTrades(VillagerTradesEvent event) {
        if (event.getType() == VillagerProfession.LIBRARIAN) {
            // Level 4 (Expert) trades
            event.getTrades().get(4).add(new EBSTradeListing());
        }
    }

    /**
     * Trade offer 本体 — vanilla {@link VillagerTrades.ItemListing} を実装。
     */
    private static final class EBSTradeListing implements VillagerTrades.ItemListing {
        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            ItemStack peb = new ItemStack(EBSItems.PORTABLE_ENCHANTED_BOOKSHELF.get(), 1);
            return new MerchantOffer(
                    new ItemCost(Items.EMERALD, 24),
                    Optional.of(new ItemCost(Items.LEATHER, 4)),
                    peb,
                    12,    // maxUses
                    15,    // xp
                    0.05F  // priceMultiplier
            );
        }
    }
}

package com.kuronami.enchantedbookstack.registry;

import java.util.Optional;

import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 司書 (Librarian) Level 4 trade に EBS を追加 (Fabric 流: TradeOfferHelper)。
 *
 * <p>取引: 24 emerald + 4 leather ↔ Enchanted Book Stack ×1。
 */
public final class EBSTrades {

    private EBSTrades() {}

    public static void register() {
        TradeOfferHelper.registerVillagerOffers(
                VillagerProfession.LIBRARIAN, 4,
                factories -> factories.add(new EBSTradeListing())
        );
    }

    private static final class EBSTradeListing implements VillagerTrades.ItemListing {
        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            ItemStack stack = new ItemStack(EBSItems.ENCHANTED_BOOK_STACK, 1);
            return new MerchantOffer(
                    new ItemCost(Items.EMERALD, 24),
                    Optional.of(new ItemCost(Items.LEATHER, 4)),
                    stack,
                    12,    // maxUses
                    15,    // xp
                    0.05F  // priceMultiplier
            );
        }
    }
}

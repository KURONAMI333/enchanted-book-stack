package com.kuronami.portableenchantedbookshelf.item;

import java.util.List;
import java.util.Optional;

import com.kuronami.portableenchantedbookshelf.client.tooltip.PouchTooltip;
import com.kuronami.portableenchantedbookshelf.data.EnchantEntry;
import com.kuronami.portableenchantedbookshelf.data.EnchantedBookHelper;
import com.kuronami.portableenchantedbookshelf.data.PouchContents;
import com.kuronami.portableenchantedbookshelf.menu.PouchMenu;
import com.kuronami.portableenchantedbookshelf.registry.PEBDataComponents;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 持ち歩けるエンチャント本棚。
 *
 * <p>Bundle 流儀の右クリック / shift-click 操作で GUI 無しでも insert / extract 可能:
 * <ul>
 *   <li>インベントリ内で PEB を保持してエンチャント本に右クリック → 本を PEB に挿入</li>
 *   <li>エンチャント本を保持して PEB に右クリック → 本を PEB に挿入 (逆方向)</li>
 *   <li>PEB に空手で右クリック → 最後に入れた本を 1 冊取り出す</li>
 * </ul>
 *
 * <p>GUI は Phase 1 後半で実装。
 *
 * <p>Stack 不可 ({@code stacksTo(1)}) — ItemStack の DataComponent に内容物を持つ。
 */
public class PortableEnchantedBookshelfItem extends Item {

    public PortableEnchantedBookshelfItem(Properties properties) {
        super(properties);
    }

    // ─────────────────────────────────────────────────────────────
    // Component アクセス
    // ─────────────────────────────────────────────────────────────

    /**
     * 指定の {@link ItemStack} の中身 ({@link PouchContents}) を取得。
     * Component が未設定なら {@link PouchContents#EMPTY} を返す。
     */
    public static PouchContents getContents(ItemStack stack) {
        PouchContents contents = stack.get(PEBDataComponents.POUCH_CONTENTS.get());
        return contents != null ? contents : PouchContents.EMPTY;
    }

    /** 指定の {@link ItemStack} に中身を書き込む。 */
    public static void setContents(ItemStack stack, PouchContents contents) {
        stack.set(PEBDataComponents.POUCH_CONTENTS.get(), contents);
    }

    /** 指定 stack が PEB か判定。 */
    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof PortableEnchantedBookshelfItem;
    }

    // ─────────────────────────────────────────────────────────────
    // Insert / Extract (Bundle 流儀)
    // ─────────────────────────────────────────────────────────────

    /**
     * 本を 1 冊 PEB に挿入。bookStack は 1 だけ消費される。
     *
     * @return 挿入成功時 true (bookStack は shrink される)、それ以外 false
     */
    public static boolean tryInsertOne(ItemStack pebStack, ItemStack bookStack) {
        if (bookStack.isEmpty()) return false;

        Optional<EnchantEntry> entry = EnchantedBookHelper.tryRead(bookStack);
        if (entry.isEmpty()) return false; // not a supported enchanted book

        PouchContents contents = getContents(pebStack);
        PouchContents updated = contents.insertOne(entry.get());
        setContents(pebStack, updated);
        bookStack.shrink(1);
        return true;
    }

    /**
     * PEB から本を 1 冊取り出す。最後に挿入された種類 (entries の末尾) から取る。
     *
     * @return 取り出した本の {@link ItemStack}、PEB が空なら {@link ItemStack#EMPTY}
     */
    public static ItemStack tryExtractLast(ItemStack pebStack, HolderLookup.Provider registries) {
        PouchContents contents = getContents(pebStack);
        if (contents.isEmpty()) return ItemStack.EMPTY;

        List<EnchantEntry> entries = contents.view();
        EnchantEntry last = entries.get(entries.size() - 1);

        ItemStack book = EnchantedBookHelper.createBook(registries, last);
        if (book.isEmpty()) return ItemStack.EMPTY; // enchant が registry から消えた等

        PouchContents updated = contents.extract(last, 1);
        setContents(pebStack, updated);
        return book;
    }

    // ─────────────────────────────────────────────────────────────
    // Bundle 流儀のインベントリ操作 (NeoForge 1.21.1 Item override)
    // ─────────────────────────────────────────────────────────────

    /**
     * カーソル (PEB) で別 slot をクリックした時 (PEB を持って本に右クリック)。
     */
    @Override
    public boolean overrideStackedOnOther(ItemStack pebStack, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;

        ItemStack target = slot.getItem();
        if (target.isEmpty()) {
            // 空 slot に右クリック → 最後の本を 1 冊取り出してそこに置く
            ItemStack extracted = tryExtractLast(pebStack, player.level().registryAccess());
            if (extracted.isEmpty()) {
                playFailSound(player);
                return false;
            }
            slot.safeInsert(extracted);
            playInsertSound(player);
            return true;
        }

        // 何か入ってる slot に右クリック → PEB に挿入を試す
        if (tryInsertOne(pebStack, target)) {
            playInsertSound(player);
            return true;
        }
        return false;
    }

    /**
     * カーソル (本) で PEB slot をクリックした時 (本を持って PEB に右クリック)。
     */
    @Override
    public boolean overrideOtherStackedOnMe(
            ItemStack pebStack, ItemStack other, Slot slot, ClickAction action,
            Player player, SlotAccess access
    ) {
        if (action != ClickAction.SECONDARY) return false;
        if (other.isEmpty()) return false;

        if (tryInsertOne(pebStack, other)) {
            playInsertSound(player);
            return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────
    // Tooltip — getTooltipImage (Bundle 流儀 popup) で完結。
    // appendHoverText は削除済 (二重表示防止)。
    // ─────────────────────────────────────────────────────────────
    // 効果音 (UX feedback)
    // ─────────────────────────────────────────────────────────────

    private static void playInsertSound(Player player) {
        player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
    }

    private static void playFailSound(Player player) {
        player.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 0.5F);
    }

    // ─────────────────────────────────────────────────────────────
    // Hover preview (Bundle 流儀)
    // ─────────────────────────────────────────────────────────────

    /**
     * マウスホバー時の中身プレビュー UI を返す。vanilla Bundle と同じ pattern。
     * 描画は client 側の {@link com.kuronami.portableenchantedbookshelf.client.tooltip.ClientPouchTooltip}
     * が担当 (factory は {@code RegisterClientTooltipComponentFactoriesEvent} で登録)。
     */
    @Override
    public java.util.Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return java.util.Optional.of(new PouchTooltip(getContents(stack)));
    }

    // ─────────────────────────────────────────────────────────────
    // 右クリック (空中) で PouchMenu を開く
    // ─────────────────────────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            MenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new PouchMenu(id, inv),
                    stack.getHoverName()
            );
            serverPlayer.openMenu(provider);
        }
        return InteractionResultHolder.success(stack);
    }
}

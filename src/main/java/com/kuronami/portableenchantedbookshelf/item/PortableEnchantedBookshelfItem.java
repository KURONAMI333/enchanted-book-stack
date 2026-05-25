package com.kuronami.portableenchantedbookshelf.item;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.kuronami.portableenchantedbookshelf.menu.PouchMenu;

import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * 持ち歩けるエンチャント本箱 — Phase 2 (AE2 viewport pattern)。
 *
 * <p>内容物は vanilla {@code DataComponents.CONTAINER} ({@link ItemContainerContents}) に保持、
 * 最大 256 個の enchanted_book (各 stack=1)。
 *
 * <p>UX:
 * <ul>
 *   <li>右クリック (空中) → {@link PouchMenu} 開く → AE2 流儀 viewport scroll GUI</li>
 *   <li>Bundle 流儀 (carry book + 右クリ peb / carry peb + 右クリ book) で 1 冊ずつ insert</li>
 *   <li>carry peb + 空 slot 右クリ → 最後の book extract</li>
 * </ul>
 *
 * <p>Stack 不可 ({@code stacksTo(1)})。
 */
public class PortableEnchantedBookshelfItem extends Item {

    /** vanilla ItemContainerContents の上限と整合させる soft cap。 */
    public static final int MAX_BOOKS = 256;

    public PortableEnchantedBookshelfItem(Properties properties) {
        super(properties);
    }

    // ─────────────────────────────────────────────────────────────
    // Component アクセス
    // ─────────────────────────────────────────────────────────────

    /** PEB の中身を取得 (default = EMPTY)。 */
    public static ItemContainerContents getContents(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
    }

    /** PEB の中身を上書き。 */
    public static void setContents(ItemStack stack, ItemContainerContents contents) {
        stack.set(DataComponents.CONTAINER, contents);
    }

    /** {@link ItemContainerContents} から書きやすい mutable list を取得。 */
    public static List<ItemStack> getMutableBooks(ItemStack pebStack) {
        return getContents(pebStack).stream()
                .map(ItemStack::copy)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** mutable list を {@link ItemContainerContents} に焼き直して書き戻す。 */
    public static void writeBooks(ItemStack pebStack, List<ItemStack> books) {
        pebStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(books));
    }

    /** PEB か判定。 */
    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof PortableEnchantedBookshelfItem;
    }

    /** ItemStack が vanilla enchanted_book で、 stack=1 取り出せる状態か。 */
    public static boolean isAcceptableBook(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.ENCHANTED_BOOK);
    }

    // ─────────────────────────────────────────────────────────────
    // Bundle 流儀 Insert / Extract
    // ─────────────────────────────────────────────────────────────

    /** book 1 冊を PEB に追加。 bookStack は 1 shrink される。 */
    public static boolean tryInsertOne(ItemStack pebStack, ItemStack bookStack) {
        if (!isAcceptableBook(bookStack)) return false;

        List<ItemStack> books = getMutableBooks(pebStack);
        if (books.size() >= MAX_BOOKS) return false;

        ItemStack one = bookStack.copy();
        one.setCount(1);
        books.add(one);
        writeBooks(pebStack, books);
        bookStack.shrink(1);
        return true;
    }

    /** PEB の末尾 (= 最後に入れた) book を 1 冊取り出す。 空なら EMPTY。 */
    public static ItemStack tryExtractLast(ItemStack pebStack) {
        List<ItemStack> books = getMutableBooks(pebStack);
        if (books.isEmpty()) return ItemStack.EMPTY;
        ItemStack last = books.remove(books.size() - 1);
        writeBooks(pebStack, books);
        return last;
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

    // ─────────────────────────────────────────────────────────────
    // Bundle 流儀のインベントリ操作 (vanilla Item override)
    // ─────────────────────────────────────────────────────────────

    @Override
    public boolean overrideStackedOnOther(ItemStack pebStack, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        ItemStack target = slot.getItem();
        if (target.isEmpty()) {
            ItemStack extracted = tryExtractLast(pebStack);
            if (extracted.isEmpty()) return false;
            slot.safeInsert(extracted);
            playInsertSound(player);
            return true;
        }
        if (tryInsertOne(pebStack, target)) {
            playInsertSound(player);
            return true;
        }
        return false;
    }

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

    private static void playInsertSound(Player player) {
        player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
    }
}

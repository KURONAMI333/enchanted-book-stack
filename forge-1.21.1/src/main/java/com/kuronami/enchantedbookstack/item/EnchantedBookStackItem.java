package com.kuronami.enchantedbookstack.item;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.kuronami.enchantedbookstack.menu.BookStackMenu;

import net.minecraft.client.Minecraft;
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
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * エンチャント本束 — 54 slot のエンチャント本専用ストレージアイテム。
 *
 * <p>内容物は vanilla {@code DataComponents.CONTAINER} ({@link ItemContainerContents}) に保持、
 * 最大 54 個の enchanted_book (各 stack=1)。 他の shulker / backpack に nest 可能。
 *
 * <p>主な機能:
 * <ul>
 *   <li>右クリックで {@link BookStackMenu} を開く (vanilla large chest UI)</li>
 *   <li>Bundle 流儀の右クリック stash (carry stack + click book / carry book + click stack)、
 *       creative menu open 中は抑止 (誤動作防止)</li>
 *   <li>インベントリで hover すると中身の主要エンチャントを text 表示</li>
 * </ul>
 */
public class EnchantedBookStackItem extends Item {

    /** vanilla large chest 同等の 54 slot (Sophisticated 鉄バックパック級)。 */
    public static final int MAX_BOOKS = 54;

    public EnchantedBookStackItem(Properties properties) {
        super(properties);
    }

    // ─────────────────────────────────────────────────────────────
    // Component アクセス
    // ─────────────────────────────────────────────────────────────

    /** EBS の中身 (default = EMPTY)。 */
    public static ItemContainerContents getContents(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
    }

    /** EBS の中身を上書き。 */
    public static void setContents(ItemStack stack, ItemContainerContents contents) {
        stack.set(DataComponents.CONTAINER, contents);
    }

    /** mutable list として books を取得 (ItemStack は copy 済)。 */
    public static List<ItemStack> getMutableBooks(ItemStack pebStack) {
        return getContents(pebStack).stream()
                .map(ItemStack::copy)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** mutable list を {@link ItemContainerContents} に焼き直して書き戻し。 */
    public static void writeBooks(ItemStack pebStack, List<ItemStack> books) {
        pebStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(books));
    }

    /** EBS か判定。 */
    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof EnchantedBookStackItem;
    }

    /** 単一 enchanted_book (stack=1 取り出せる前提) として受け入れ可能か。 */
    public static boolean isAcceptableBook(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.ENCHANTED_BOOK);
    }

    // ─────────────────────────────────────────────────────────────
    // Bundle 流儀 Insert / Extract
    // ─────────────────────────────────────────────────────────────

    /** book 1 冊を EBS に追加。 bookStack は 1 shrink される。 */
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

    /**
     * EBS から最後の book を 1 冊取り出す (LIFO)。
     * Akashic 罠回避: index で削除 (list.remove(int))、 equals ベース remove は使わない。
     */
    public static ItemStack tryExtractLast(ItemStack pebStack) {
        List<ItemStack> books = getMutableBooks(pebStack);
        if (books.isEmpty()) return ItemStack.EMPTY;
        ItemStack last = books.remove(books.size() - 1); // index ベース、 equals 比較なし
        writeBooks(pebStack, books);
        return last;
    }

    // ─────────────────────────────────────────────────────────────
    // 右クリック (空中) で BookStackMenu を開く
    // ─────────────────────────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            MenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new BookStackMenu(id, inv),
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
        if (isCreativeMenuOpen()) return false; // Sophisticated Backpacks 罠回避

        ItemStack target = slot.getItem();
        if (target.isEmpty()) {
            // 空 slot 右クリ → 最後の本 extract → そこに置く
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
        if (isCreativeMenuOpen()) return false; // Sophisticated Backpacks 罠回避
        if (other.isEmpty()) return false;
        if (tryInsertOne(pebStack, other)) {
            playInsertSound(player);
            return true;
        }
        return false;
    }

    /**
     * Creative menu (CreativeModeInventoryScreen) 開いてる時に Bundle 流儀の stash を抑止。
     *
     * <p>Sophisticated Backpacks {@code hasCreativeScreenContainerOpen} 流儀。 creative inventory
     * は通常の Menu でなく専用 Screen を使うため、 carry 中の操作で意図しない stash が起きる罠を防ぐ。
     *
     * <p>{@code FMLEnvironment.dist} で client side 判定して Minecraft.getInstance() 参照。
     * dedicated server では常に false を返す。
     */
    private static boolean isCreativeMenuOpen() {
        if (FMLEnvironment.dist != Dist.CLIENT) return false;
        try {
            var screen = Minecraft.getInstance().screen;
            return screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void playInsertSound(Player player) {
        player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
    }

    // ─────────────────────────────────────────────────────────────
    // Hover preview (vanilla shulker box BlockItem 流儀のテキスト中身表示)
    // ─────────────────────────────────────────────────────────────

    /** hover preview に表示する本の最大行数。超過分は「...他 N 冊」表示。 */
    private static final int PREVIEW_LIMIT = 5;

    /**
     * インベントリで EBS に hover した時、 中身をテキスト list で表示する。
     * vanilla shulker box ({@code BlockItem.appendHoverText}) と同じ pattern。
     *
     * <p>各 enchanted_book の主 enchant 名 + ローマ数字 level を表示
     * (例: 「幸運 III」「修繕」)、 上限 5 行、 超過分は「...他 N 冊」。
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        List<ItemStack> books = getMutableBooks(stack);
        if (books.isEmpty()) return;

        int shown = Math.min(books.size(), PREVIEW_LIMIT);
        for (int i = 0; i < shown; i++) {
            tooltip.add(formatBookEnchant(books.get(i)).withStyle(ChatFormatting.GRAY));
        }
        if (books.size() > PREVIEW_LIMIT) {
            tooltip.add(Component.translatable(
                    "container.shulkerBox.more", books.size() - PREVIEW_LIMIT
            ).withStyle(ChatFormatting.ITALIC));
        }
    }

    /** 「幸運 III」「修繕 I」のような短い表示 (vanilla shulker と同感覚)。 */
    private static net.minecraft.network.chat.MutableComponent formatBookEnchant(ItemStack book) {
        ItemEnchantments encs = book.get(DataComponents.STORED_ENCHANTMENTS);
        if (encs == null || encs.isEmpty()) {
            return book.getHoverName().copy();
        }
        var first = encs.entrySet().iterator().next();
        Holder<Enchantment> holder = first.getKey();
        int level = first.getValue();
        Component name = holder.value().description();
        return Component.empty()
                .append(name)
                .append(" ")
                .append(romanLevel(level));
    }

    private static Component romanLevel(int level) {
        return Component.literal(switch (level) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> level <= 0 ? Integer.toString(level) : "Lvl " + level;
        });
    }
}

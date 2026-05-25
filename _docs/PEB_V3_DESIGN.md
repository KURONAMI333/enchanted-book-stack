---
title: Portable Enchanted Bookshelf v3 Design
status: DESIGN — pending kura review
created: 2026-05-26
based_on: 10-mod research in _docs/research/
abandons: phase-1-bundle-style (git tag), phase-2-viewport (main, current)
---

# Portable Enchanted Bookshelf v3 — 統合設計

10 mod (Sophisticated Core/Backpacks/Storage, Tom's Storage, AE2, Functional Storage, JEI, Akashic Tome, IPN, InvTweaks) の reading から抽出した **canonical patterns** を統合した v3 設計。

v1 (Bundle 流儀集約) と v2 (AE2 viewport 簡略実装) は **両方とも捨てる**。 v3 は **各層で各 mod の最適 pattern を採用**して再構築。

---

## 1. アーキテクチャ全体図

```
┌─────────────────────────────────────────────────────────┐
│ PEB ItemStack                                            │
│ ├── DataComponents.CONTAINER : ItemContainerContents     │
│ │   (vanilla 256 slot hard cap、 cacheEncoding() 必須)   │
│ └── (optional 自前) DataComponents.peb_insert_tick :     │
│     Map<UUID, Long> for RECENT sort                      │
└─────────────────────────────────────────────────────────┘
        │ open
        ↓
┌─────────────────────────────────────────────────────────┐
│ Server                                                   │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ PouchInventory extends ItemStackHandler(256)         │ │
│ │   isItemValid = enchanted_book only                  │ │
│ │   isItemValid 補完で bag-in-bag 防止                  │ │
│ └─────────────────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ PouchMenu extends AbstractContainerMenu              │ │
│ │   - Player inv 36 slots (vanilla Slot)               │ │
│ │   - PEB 自身を持つ slot lock (slot index 保存)        │ │
│ │   - 内部 PouchInventory 256                          │ │
│ │   - handler 変化時に ItemStack の Component 書き戻し │ │
│ │   - close 時 final flush                             │ │
│ └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
        │ packet (vanilla slot sync + custom)
        ↓
┌─────────────────────────────────────────────────────────┐
│ Client                                                   │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ PouchRepo (cache)                                    │ │
│ │   - rawBooks : List<ItemStack> (PEB stack 直読み)    │ │
│ │   - view : filter + sort 適用後                       │ │
│ │   - scrollOffset (行単位)                            │ │
│ │   - searchPhrase / sortMode                          │ │
│ │   - paused = hasShiftDown() で shift 連打中 stable   │ │
│ └─────────────────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ PouchScreen extends AbstractContainerScreen          │ │
│ │   - 背景: vanilla generic_54.png 流用                 │ │
│ │   - 9×6 = 54 PouchVirtualSlot (client-only)          │ │
│ │     - slot 位置: relative (leftPos 加算なし)         │ │
│ │     - 動的 lookup: repo.get(scrollOffset + idx)      │ │
│ │     - filter 非表示: slot.x = -2000                  │ │
│ │   - PouchSearchBox 自前 TextBox 派生 (右上)          │ │
│ │     - keyPressed: focused 時 ESC/TAB 以外 swallow    │ │
│ │   - mouseScrolled: 行単位 scroll                     │ │
│ │   - mouseClicked: PouchVirtualSlot 判定 → packet     │ │
│ │   - containerTick: repo.updateFromStack(menu.peb)    │ │
│ │   - sort button (scroll-toggle, IPN 流儀)            │ │
│ │   - scrollbar drag (vanilla creative scroller スプ)  │ │
│ └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

---

## 2. データ層

### 2.1 内容物保持

vanilla `DataComponents.CONTAINER` = `ItemContainerContents` 使用。

**根拠 + 罠**:
- Sophisticated Core `StatefulComponentItemHandler:17` が canonical pattern、 256 slot hard cap が PEB 仕様と一致 [`research/01-sophisticated-core.md`]
- Sophisticated Storage 警告「256 slot を data component に直接持つと毎 item move で全 byte 同期が走る」→ **`cacheEncoding()` 必須** [`research/03-sophisticated-storage.md` + `research/08-akashic-tome.md`]
- SavedData + UUID 流儀 (Sophisticated Backpacks/Storage) は world-level なので player inventory item の PEB には不適 → inline 採用

### 2.2 (optional) RECENT sort 用 DataComponent

`DataComponents` に自前で:
```java
peb_insert_tick : Map<UUID, Long>
```
- 各 enchanted_book に挿入時の tick 番号付与
- RECENT sort で desc 並び
- v0.1 では省略可、 v0.2 で追加

### 2.3 構造化アクセス

immutable 更新パターン (Akashic Tome 流儀):
```java
ItemContainerContents current = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
List<ItemStack> mutable = new ArrayList<>();
current.stream().forEach(s -> mutable.add(s.copy()));
// ... 操作 ...
stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(mutable));
```

**罠**: list.remove(ItemStack) は equals 比較 → 同じ enchant 重複時に「最初の 1 件」しか消えない (Akashic Tome 罠)。 **index ベース API** 必須。

---

## 3. Server-side Inventory

### 3.1 PouchInventory (新規)

```java
public class PouchInventory extends ItemStackHandler {
    private static final int SIZE = 256;

    public PouchInventory() { super(SIZE); }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        // 1) enchanted_book only
        if (!stack.is(Items.ENCHANTED_BOOK)) return false;
        // 2) bag-in-bag 防止 (FS ArmoryCabinet 流儀)
        if (stack.getCapability(Capabilities.ItemHandler.ITEM) != null) return false;
        // 3) PEB 自身を入れさせない (念のため)
        if (stack.getItem() instanceof PortableEnchantedBookshelfItem) return false;
        return true;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1; // enchanted_book は max_stack=1
    }
}
```

**罠**: `isSameItemSameComponents` で重複判定 (vanilla の `isSameItem` だと Fortune III と Mending I が同一視される、 Sophisticated Core 罠)。

### 3.2 PouchInventory ↔ ItemContainerContents 変換

Menu 開閉時に load/save:
```java
// load (Menu open)
ItemContainerContents contents = pebStack.getOrDefault(DataComponents.CONTAINER, EMPTY);
NonNullList<ItemStack> stacks = NonNullList.withSize(256, ItemStack.EMPTY);
contents.copyInto(stacks);
PouchInventory handler = new PouchInventory();
for (int i = 0; i < stacks.size(); i++) handler.setStackInSlot(i, stacks.get(i));

// save (handler change or menu close)
List<ItemStack> nonEmpty = new ArrayList<>();
for (int i = 0; i < handler.getSlots(); i++) {
    ItemStack s = handler.getStackInSlot(i);
    if (!s.isEmpty()) nonEmpty.add(s);
}
pebStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(nonEmpty));
```

---

## 4. Server-side Menu

### 4.1 PouchMenu

```java
public class PouchMenu extends AbstractContainerMenu {
    private final Player player;
    private final boolean clientSide;
    private final ItemStack pebStack;          // PEB 自身
    private final int pebSlotIndex;            // player inv 内の PEB の slot index
    private final PouchInventory handler;       // server-side 256 slot
    private final ContainerData scrollSyncData; // sort/scroll/filter sync (Sophisticated SyncContainerClientDataPayload 流儀)

    public PouchMenu(int containerId, Inventory playerInventory) {
        super(PEBMenuTypes.POUCH_MENU.get(), containerId);
        // ...
        // 1. player inv 36 slot (vanilla)
        // 2. PEB 自身を持つ slot は lock (mayPickup=false override)
        // 3. handler = 256 slot but no slot added to menu (client が virtual で扱う)
    }

    /** PEB 自身を持つ slot を lock — Sophisticated Core 罠回避 */
    @Override
    public boolean canDragTo(Slot slot) {
        return slot.index != pebSlotIndex && super.canDragTo(slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // player inv → 内容物 へ Insert (1 個ずつ、 enchanted_book のみ)
        // handler → player inv は client 側 click handler で処理 (extract packet)
        return ItemStack.EMPTY;  // shift-click は client 独自処理に任せる
    }

    /** handler 変化時に PEB の DataComponent 書き戻し → vanilla slot sync で client に伝播 */
    public void onHandlerChanged() {
        if (clientSide || pebStack.isEmpty()) return;
        List<ItemStack> nonEmpty = new ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty()) nonEmpty.add(s);
        }
        pebStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(nonEmpty));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        onHandlerChanged(); // final flush
    }

    /** server に slot を持たない virtual viewport 用 */
    public void addClientVirtualSlot(Slot slot) {
        addSlot(slot);
    }
}
```

**重要 pattern 出典**:
- `addClientVirtualSlot` で server/client slot 数差 = AE2 流儀 [`research/06-ae2-mestorage.md`]
- `pebSlotIndex` lock = Sophisticated Core 罠回避
- `onHandlerChanged` で DataComponent 経由 sync = vanilla の slot sync メカニズム流用 (独自 packet 削減)

---

## 5. Client-side Repo

### 5.1 PouchRepo

```java
public class PouchRepo {
    public enum SortMode { NAME_ASC, LEVEL_DESC, COUNT_DESC, RECENT }

    private List<ItemStack> rawBooks;   // PEB stack 直読みの snapshot
    private List<ItemStack> view;       // filter + sort 適用後
    private String searchPhrase = "";
    private SortMode sortMode = SortMode.NAME_ASC;
    private int scrollOffset = 0;
    private boolean paused = false;     // shift 連打中の sort 停止 (AE2 流儀)

    public static PouchRepo fromStack(ItemStack pebStack) { ... }
    public void updateFromStack(ItemStack pebStack) { ... } // tick で呼ぶ
    public void setSearchPhrase(String s) { ... rebuild ... }
    public void setSortMode(SortMode m) { ... rebuild ... }
    public void setPaused(boolean p) { this.paused = p; }
    public void setScrollOffset(int offset, int visibleSlots) { ... clamp ... }
    public ItemStack getViewportSlot(int viewportIdx) { ... }

    private void rebuildView() {
        if (paused) return; // shift 連打中は view 固定 (AE2 流儀)
        // 1. filter (search)
        // 2. sort (comparator chain)
    }
}
```

### 5.2 検索 algorithm

partial match (MVP v0.1):
- localized enchant name (`holder.value().description().getString()`)
- enchant ID (`minecraft:fortune`)
- ローマ数字レベル (`III` の完全一致)
- すべて `Translator.toLowercaseWithLocale` 正規化 (JEI 流儀、 トルコ語 I→ı 罠回避)

v0.2 で JEI 流儀の構文拡張 (`|` OR、 space AND、 `-` neg、 `"..."` 引用句)。

### 5.3 Sort 4 軸 comparator chain (IPN 流儀)

```java
case NAME_ASC -> Comparator.comparing(book -> nameLocalized(book));
case LEVEL_DESC -> Comparator.comparingInt(book -> -primaryLevel(book));
case COUNT_DESC -> Comparator.comparingInt(book -> -bucketCount(book));  // 同 kind の集約 count
case RECENT -> Comparator.comparingLong(book -> -insertTick(book));      // peb_insert_tick component
```

---

## 6. Client-side Screen

### 6.1 PouchScreen

```java
public class PouchScreen extends AbstractContainerScreen<PouchMenu> {
    private static final ResourceLocation BG_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int BG_WIDTH = 176;
    private static final int BG_HEIGHT = 222;
    
    /** vanilla large chest の slot grid 開始位置 (relative)。 */
    private static final int VIEWPORT_X = 8;
    private static final int VIEWPORT_Y = 18;   // ★ v2 bug 修正点
    private static final int VIEWPORT_COLS = 9;
    private static final int VIEWPORT_ROWS = 6;
    
    private PouchRepo repo;
    private PouchSearchBox searchBox;
    private SortButton sortButton;

    @Override
    protected void init() {
        super.init();
        repo = PouchRepo.fromStack(menu.getPebStack());

        // 検索バー: 右上 (タイトル横、 インベントリ整理系 MOD 流儀、 kura 指摘)
        searchBox = new PouchSearchBox(font, VIEWPORT_X + 88, 4, 70, 12);
        searchBox.setResponder(repo::setSearchPhrase);
        addWidget(searchBox); // ★ Sophisticated 流儀: addWidget + 手動 render
        setInitialFocus(searchBox);

        // Sort button: 検索バー右隣 (IPN 流儀の scroll-toggle)
        sortButton = new SortButton(VIEWPORT_X + 160, 4, repo);
        addRenderableWidget(sortButton);

        // 9×6=54 virtual slot を **relative 座標** で追加 (v2 bug 修正)
        for (int row = 0; row < VIEWPORT_ROWS; row++) {
            for (int col = 0; col < VIEWPORT_COLS; col++) {
                int viewportIdx = col + row * VIEWPORT_COLS;
                int x = VIEWPORT_X + col * 18;  // ★ leftPos 加算なし
                int y = VIEWPORT_Y + row * 18;  // ★ topPos 加算なし
                menu.addClientVirtualSlot(new PouchVirtualSlot(repo, viewportIdx, x, y));
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(BG_TEXTURE, leftPos, topPos, 0, 0, BG_WIDTH, BG_HEIGHT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        searchBox.render(g, mouseX, mouseY, partialTick); // ★ addWidget なので手動 render
        // shift 連打中の sort 停止 (AE2 流儀)
        repo.setPaused(hasShiftDown());
    }

    @Override
    public void containerTick() {
        super.containerTick();
        repo.updateFromStack(menu.getPebStack());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInViewport(mouseX, mouseY)) {
            int delta = (scrollY > 0 ? -1 : 1) * VIEWPORT_COLS; // 行単位
            repo.setScrollOffset(repo.getScrollOffset() + delta, VIEWPORT_COLS * VIEWPORT_ROWS);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PouchVirtualSlot clicked = findClickedVirtualSlot(mouseX, mouseY);
        if (clicked != null && button == 0) {
            handleVirtualSlotClick(clicked);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** mouseX/Y を slot 絶対座標と比較 (leftPos/topPos 加算が必要) */
    private PouchVirtualSlot findClickedVirtualSlot(double mouseX, double mouseY) {
        for (var s : menu.slots) {
            if (s instanceof PouchVirtualSlot p) {
                int ax = leftPos + s.x;  // ★ leftPos 加算
                int ay = topPos + s.y;   // ★ topPos 加算
                if (mouseX >= ax && mouseX < ax + 16 && mouseY >= ay && mouseY < ay + 16) return p;
            }
        }
        return null;
    }
}
```

### 6.2 PouchVirtualSlot (PEB 名前で衝突回避、 旧 RepoSlot 改名)

AE2 ClientReadOnlySlot 流儀の自前再実装 (LGPL 汚染回避、 12-80 行):

```java
public class PouchVirtualSlot extends Slot {
    private static final Container EMPTY = new SimpleContainer(0);
    private final PouchRepo repo;
    private final int viewportIdx;

    public PouchVirtualSlot(PouchRepo repo, int viewportIdx, int x, int y) {
        super(EMPTY, 0, x, y);  // ★ relative 座標
        this.repo = repo;
        this.viewportIdx = viewportIdx;
    }

    @Override public ItemStack getItem() { return repo.getViewportSlot(viewportIdx); }
    @Override public boolean hasItem() { return !getItem().isEmpty(); }
    public int currentHandlerIndex() {
        // repo の view 内 index → 元の handler index に逆マッピング
        return repo.viewToHandlerIndex(repo.getScrollOffset() + viewportIdx);
    }

    // vanilla slot interaction を全 final で封じる (AE2 流儀)
    @Override public final boolean mayPlace(ItemStack s) { return false; }
    @Override public final void set(ItemStack s) { /* no-op */ }
    @Override public final int getMaxStackSize() { return 0; }
    @Override public final ItemStack remove(int amount) { return ItemStack.EMPTY; }
    @Override public final boolean mayPickup(Player p) { return false; }
}
```

### 6.3 PouchSearchBox (E キー回避の核)

Sophisticated `TextBox` 流儀 (LGPL/MIT 安全範囲で自前):

```java
public class PouchSearchBox extends EditBox {
    public PouchSearchBox(Font font, int x, int y, int w, int h) {
        super(font, x, y, w, h, Component.translatable("...search"));
        setBordered(true);
        setMaxLength(50);
    }

    /** ★ 核: focused 時 ESC/TAB 以外を consume → vanilla の E キー → close を bypass */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) return false;
        super.keyPressed(keyCode, scanCode, modifiers);
        return keyCode != GLFW.GLFW_KEY_ESCAPE && keyCode != GLFW.GLFW_KEY_TAB;
    }
}
```

**3 mod で同一 pattern 確定**: Sophisticated `TextBox.java:54-63`, Tom's `AbstractStorageTerminalScreen.java:651-668`, AE2 `AETextField.java:106-115`。

### 6.4 SortButton (IPN scroll-toggle 流儀)

```java
public class SortButton extends Button {
    private final PouchRepo repo;

    public SortButton(int x, int y, PouchRepo repo) {
        super(Button.builder(...).bounds(x, y, 12, 12).onPress(b -> {
            // 左 click: 実行 (今は表示更新だけ、 sort は view 自動)
        }).build());
        this.repo = repo;
    }

    /** ホイールで sort method 切替 (IPN 流儀) */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseOver(mouseX, mouseY)) {
            int dir = scrollY > 0 ? -1 : 1;
            SortMode[] modes = SortMode.values();
            int next = (repo.getSortMode().ordinal() + dir + modes.length) % modes.length;
            repo.setSortMode(modes[next]);
            return true;
        }
        return false;
    }
}
```

---

## 7. Network packets

### 7.1 ExtractByIdxPayload (C→S)

```java
public record ExtractByIdxPayload(int handlerIdx, int count) {
    public static final int EXTRACT_ALL = Integer.MAX_VALUE;
}
```

server で `handler.extractItem(idx, count, false)` → `player.getInventory().add(...)` → `menu.onHandlerChanged()`。

### 7.2 InsertCarriedPayload (C→S)

cursor の carried (enchanted_book) を最初の空 slot へ。

### 7.3 (option) SyncContainerClientDataPayload (C→S) — Sophisticated 流儀

```java
public record SyncContainerClientDataPayload(CompoundTag data) { }
```

汎用 CompoundTag で sort/search/設定を一括 sync。 v0.1 では search/sort は client-only (server に伝えない) で十分、 v0.2 で追加。

---

## 8. Key handling (E キー回避の最終答え)

PouchSearchBox の `keyPressed` override で **focused 時 ESC/TAB 以外 consume** → これだけで完結。

`addWidget` (Sophisticated 流儀) 採用なら、 widget の key event chain に検索バーが入る。 ただし手動 `render()` 必要。

`addRenderableWidget` (vanilla 流儀) なら render 自動、 ただし event chain も自動。 PEB は `addWidget` 採用 (Sophisticated 流儀踏襲、 検索バー以外の render order を制御できる利点)。

---

## 9. Item (Bundle 流儀維持)

```java
public class PortableEnchantedBookshelfItem extends Item {
    public static final int MAX_BOOKS = 256;

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new PouchMenu(id, inv),
                stack.getHoverName()
            ));
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack pebStack, Slot slot, ClickAction action, Player player) {
        // Bundle 流儀: carry peb + click on book → insert
        if (action != ClickAction.SECONDARY) return false;
        if (creativeMenuOpen(player)) return false; // ★ Sophisticated Backpacks 罠回避
        // ... 既存 v2 ロジック ...
    }
}
```

**罠**: creative menu open ガード。 Sophisticated Backpacks の `hasCreativeScreenContainerOpen` が前例。

---

## 10. Hover preview

`getTooltipImage` で `PouchTooltip` (record、 PouchContents 持つ) → client renderer `ClientPouchTooltip` で list 描画。 vanilla `bundle.png` スプライト流用。

v2 で実装済の pattern を v3 にも維持 (broken file は復活、 data source は新設計に合わせて書き換え)。

---

## 11. lang

```
item.portableenchantedbookshelf.portable_enchanted_bookshelf  = エンチャント本箱 (ja_jp) / Portable Enchanted Bookshelf (en_us)
item.peb.search                                                = エンチャント検索... / Search enchantments...
item.peb.sort.NAME_ASC                                         = 名前順 / Name (A→Z)
item.peb.sort.LEVEL_DESC                                       = レベル順 / Level (high→low)
item.peb.sort.COUNT_DESC                                       = 冊数順 / Count (many→few)
item.peb.sort.RECENT                                           = 新着順 / Recent
item.peb.tooltip.click_extract                                 = クリックで 1 冊取り出し / Click to extract 1
item.peb.tooltip.shift_extract                                 = Shift+クリックで全部 (%d) / Shift+Click for all (%d)
item.peb.empty                                                 = (空) / (empty)
item.peb.no_match                                              = (該当なし) / (no match)
item.peb.books_count                                           = エンチャント本 %d 冊 / %d enchanted books
```

---

## 12. 罠回避リスト (実装中チェック)

| # | 罠 | 出典 | 対策 |
|---|---|---|---|
| 1 | ItemContainerContents 256 hard cap | Sophisticated Core | MAX_BOOKS=256 で抑える |
| 2 | `isSameItem` 重複判定 | Sophisticated Core | `isSameItemSameComponents` 使う |
| 3 | PEB を player inv から取られる | Sophisticated Core | pebSlotIndex lock |
| 4 | list.remove(equals) で間違って消す | Akashic Tome | index ベース API のみ |
| 5 | bag-in-bag (PEB 内 PEB) | Functional Storage | `Capabilities.ItemHandler.ITEM != null` check |
| 6 | AGPL コード body 直接 copy | IPN | api enum 程度のみ、 body は自前 |
| 7 | data component sync コスト | Sophisticated Storage | `cacheEncoding()` 必須 |
| 8 | `addWidget` で手動 render 必要 | Sophisticated Core | `searchBox.render(...)` 明示呼び出し |
| 9 | EditBox E キー auto-close | 3 mod 共通 | `keyPressed` で ESC/TAB 以外 consume |
| 10 | slot 位置 = relative coord | vanilla 仕様 | leftPos/topPos 加算なしで slot ctor |
| 11 | creative menu 中の Bundle 流儀 | Sophisticated Backpacks | `creativeMenuOpen()` ガード |
| 12 | shift 連打中 view 揺れ | AE2 | `repo.setPaused(hasShiftDown())` |
| 13 | DataPacket 受信 unlimited | Tom's | 容量制限明示 (256 で clamp) |

---

## 13. ファイル構成

```
src/main/java/com/kuronami/portableenchantedbookshelf/
├── PortableEnchantedBookshelf.java       (entry @Mod)
├── client/
│   ├── PEBClient.java                     (factory 登録)
│   ├── screen/
│   │   ├── PouchScreen.java
│   │   ├── PouchVirtualSlot.java
│   │   ├── PouchSearchBox.java
│   │   ├── PouchRepo.java
│   │   └── SortButton.java
│   └── tooltip/
│       ├── PouchTooltip.java              (TooltipComponent record)
│       └── ClientPouchTooltip.java
├── inventory/
│   └── PouchInventory.java                (ItemStackHandler 256 slot)
├── item/
│   └── PortableEnchantedBookshelfItem.java
├── menu/
│   └── PouchMenu.java
├── network/
│   ├── PEBNetwork.java
│   ├── ExtractByIdxPayload.java
│   ├── InsertCarriedPayload.java
│   └── (v0.2) SyncContainerClientDataPayload.java
└── registry/
    ├── PEBItems.java
    ├── PEBMenuTypes.java
    ├── PEBTabs.java
    ├── PEBTrades.java
    └── (v0.2) PEBDataComponents.java       (peb_insert_tick 用)
```

---

## 14. 実装順序 (chunk 分け)

### Chunk 1: 旧コード捨て + 新規 skeleton (compile 通すまで)
- 既存 main は git checkout v2 から branch して reset
- v2 のうち以下は流用:
  - `PortableEnchantedBookshelf.java` (entry、 修正小)
  - `PEBItems / PEBMenuTypes / PEBTabs / PEBTrades` (registry、 そのまま)
  - `PEBClient` (修正小)
- v2 のうち以下は **完全書き直し**:
  - `PortableEnchantedBookshelfItem` (DataComponent 直接保持 + 罠回避)
  - `PouchMenu` (PouchInventory ベース)
  - `PouchScreen` (relative slot + addWidget 流儀)
  - `PouchVirtualSlot` (旧 RepoSlot 名前変更)
  - `PouchRepo` (sort 4 軸 + paused)
- 新規:
  - `PouchInventory`
  - `PouchSearchBox` (E キー swallow)
  - `SortButton`

### Chunk 2: 内容物 sync 完成 + 動作確認
- 実機テスト 1: PEB 開く → slot grid 表示 + 背景一致
- 検索: E キーで close しない ← **修正の主目的 1**
- slot 位置: 背景と完全一致 ← **修正の主目的 2**

### Chunk 3: sort + RECENT
- comparator chain
- (option) `peb_insert_tick` DataComponent

### Chunk 4: hover preview 復活
- `PouchTooltip` + `ClientPouchTooltip` を新 data 構造で再実装

### Chunk 5: lang + polish
- ja_jp + en_us 完成
- error path テスト

### Chunk 6: マルチローダー展開 (Phase 3 = v0.1.0 release 準備)
- Forge 1.21.1 / 1.20.1 / Fabric 1.21.1 / 1.20.1

---

## 15. 設計レビュー観点

kura に確認したい点:

1. **データ層 inline 採用 OK?** (Sophisticated SavedData 流儀ではなく ItemContainerContents)
2. **256 slot hard cap OK?** (それ以上は NBT 直書きで複雑化)
3. **検索バー位置: 右上 (タイトル横、 70px width)** で OK?
4. **Sort 4 軸 (NAME / LEVEL / COUNT / RECENT)** で OK? RECENT は v0.2 でも OK?
5. **Sort UI = scroll-toggle button** (IPN 流儀) で OK? それとも複数ボタン?
6. **Bundle 流儀 (carry insert/extract) は維持で OK?**
7. **PEB を player inv から取られない lock OK?** (Menu 開いてる間)
8. **MIT ライセンス維持** (AGPL/LGPL コード直 copy 禁止、 spec 参照のみ) で OK?

---

## 16. v3 vs v2 の差分 (実装インパクト)

| 項目 | v2 | v3 |
|---|---|---|
| slot 位置 | absolute (leftPos 加算済) | **relative** (leftPos 加算なし) ← bug fix |
| E キー | vanilla EditBox (=close する) | **PouchSearchBox.keyPressed で swallow** ← bug fix |
| inventory | (server-side) ItemStackHandler(256) ✓ | 同じ + isItemValid 強化 |
| menu | viewport 自前 | 同じ + lock + onHandlerChanged ✓ |
| repo | client cache ✓ | 同じ + paused + sort 4 軸 |
| sort | NAME_ASC のみ | NAME/LEVEL/COUNT/RECENT (scroll-toggle button) |
| filter | partial | 同じ + 日本語正規化 (JEI 流儀) |
| Bundle 流儀 | 維持 ✓ | 同じ + creative ガード |
| hover preview | 削除 | 復活 (新 data 構造で再実装) |
| widget 追加 | addRenderableWidget | **addWidget + 手動 render** (Sophisticated 流儀) |
| cacheEncoding | 未対応 | **必須対応** |

→ v3 は v2 の延長線、 ただし root cause 2 つ (slot 位置 + E キー) を含む 13 箇所の罠回避を統合実装。

---

**この設計で進める? kura レビュー後に実装着手。**

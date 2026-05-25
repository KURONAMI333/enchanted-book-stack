# Sophisticated Core 構造解析

ソース: `/tmp/mod-sources/SophisticatedCore/src/main/java/net/p3pp3rf1y/sophisticatedcore/`
対象バージョン: NeoForge 1.21.1 系（コードは `MutableDataComponentHolder`, `ItemContainerContents`, `IPayloadContext` を使用＝NeoForge 1.21+ API）
解析対象: PEB (mod-027) で使いたい「ItemStack の DataComponent に container を持ち、検索＋scroll で 256 slot 単一カテゴリ (enchanted_book) を扱う UI」

---

## 1. パッケージ構造

`sophisticatedcore/` 直下:
```
api/        client/        common/        compat/        controller/
crafting/   data/          init/          inventory/     mixin/
network/    renderdata/    settings/      upgrades/      util/
Config.java SophisticatedCore.java
```

PEB に直接効くのは以下のみ:

| パッケージ | 役割 (実コードから確認) |
|---|---|
| `common/gui/` | `StorageContainerMenuBase` (Menu 基底, 1899 行), `StorageInventorySlot`, `SlotSuppliedHandler`, `FilterSlotItemHandler` |
| `client/gui/` | `StorageScreenBase` (Screen 基底, 1395 行), `SearchBox`, `SettingsScreen` |
| `client/gui/controls/` | `TextBox` (EditBox ラッパ), `InventoryScrollPanel` (Neoforge ScrollPanel 拡張) |
| `inventory/` | `InventoryHandler` (抽象、ItemStackHandler ベース), `StatefulComponentItemHandler` (**DataComponent 直結の handler**) |
| `network/` | `SyncContainerClientDataPayload`, `SyncSlotStackPayload` 他 12 packet |

PEB で参照不要: `controller/`, `crafting/`, `renderdata/`, `settings/` (上位機能), `upgrades/` (機能拡張用)。

---

## 2. Menu (Container) Base: StorageContainerMenuBase.java

- パス: `common/gui/StorageContainerMenuBase.java`
- 行数: **1899 行**

### 宣言

```java
// StorageContainerMenuBase.java:54
public abstract class StorageContainerMenuBase<S extends IStorageWrapper>
    extends AbstractContainerMenu
    implements IAdditionalSlotInfoMenu {
```

### 主要定数

```java
// StorageContainerMenuBase.java:55
public static final int NUMBER_OF_PLAYER_SLOTS = 36;
```

### Constructor 引数 (StorageContainerMenuBase.java:101, :104)

```java
protected StorageContainerMenuBase(
    MenuType<?> menuType,
    int containerId,
    Player player,
    S storageWrapper,
    IStorageWrapper parentStorageWrapper,
    int storageItemSlotIndex,
    boolean shouldLockStorageItemSlot,
    List<Slot> extraSlots)
```

`storageItemSlotIndex` = container を内包している ItemStack が player inventory のどの slot にあるか (PEB の "持ち歩く本棚" でも同じ仕組みで使える)。`shouldLockStorageItemSlot=true` にすると **そのスロットを取れなくなる** (StorageContainerMenuBase.java:319-326)。

### slot 配置の流れ

```java
// StorageContainerMenuBase.java:124-130
protected void initSlotsAndContainers(Player player, int storageItemSlotIndex, boolean shouldLockStorageItemSlot, List<Slot> extraSlots) {
    addStorageInventorySlots();
    addPlayerInventorySlots(player.getInventory(), storageItemSlotIndex, shouldLockStorageItemSlot);
    addExtraSlots(extraSlots);
    addUpgradeSlots();
    addUpgradeSettingsContainers(player);
}
```

順序が固定: storage → player → extra → upgrade。`quickMoveStack` でこの index range を判定に使う (`StorageContainerMenuBase.java:723-744` の `mergeSlotStack`)。

### `inventoryHandler.getSlots()` で何 slot 作るか

```java
// StorageContainerMenuBase.java:249-296
protected void addStorageInventorySlots() {
    InventoryHandler inventoryHandler = storageWrapper.getInventoryHandler();
    int slotIndex = 0;
    Set<Integer> noSortSlotIndexes = getNoSortSlotIndexes();
    while (slotIndex < inventoryHandler.getSlots()) {
        StorageInventorySlot slot = new StorageInventorySlot(...) { ... };
        ...
        addSlot(slot);
        slotIndex++;
    }
}
```

→ **`InventoryHandler.getSlots()` の値そのまま分の `StorageInventorySlot` を作る**。位置は (0,0) で生成して後で Screen 側が `slot.x` / `slot.y` を書き換える (重要設計: Menu は位置を持たず Screen が動的に決める)。

### Player Inventory slot 追加

```java
// StorageContainerMenuBase.java:303-318
protected void addPlayerInventorySlots(Inventory playerInventory, int storageItemSlotIndex, boolean shouldLockStorageItemSlot) {
    for (int i = 0; i < 3; ++i) {
        for (int j = 0; j < 9; ++j) {
            int slotIndex = j + i * 9 + 9;
            Slot slot = addStorageItemSafeSlot(playerInventory, slotIndex, storageItemSlotIndex, shouldLockStorageItemSlot);
            addSlotAndUpdateStorageItemSlotNumber(...);
        }
    }
    for (int slotIndex = 0; slotIndex < 9; ++slotIndex) {
        Slot slot = addStorageItemSafeSlot(...);
        ...
    }
}
```

→ vanilla 標準の player inventory レイアウト (3×9 + hotbar)。

### Key methods

| method | 行 | 重要点 |
|---|---|---|
| `quickMoveStack(player, index)` | :692-720 | slot type を index 範囲で判定し `mergeSlotStack` に分岐。これが shift-click 動作 |
| `clicked(slotId, dragType, clickType, player)` | :384-444 | filter slot / upgrade slot / overflow handling を割り込んでから `super.clicked()` |
| `removed(player)` | :1328-1342 | upgrade tab の inventory slot に残っているもの drop 処理 + `super.removed()` |
| `broadcastChanges()` | :1557-1579 | `hasSomethingMessedWithStorage()` で再 open 判定 → `broadcastChangesIn(upgrade)` → `broadcastChangesIn(real)` |
| `stillValid(player)` | **基底では未定義、abstract で subclass 実装** (BackpackContainer.java:103-106 では `backpackContext.canInteractWith(player)`) |

### Network sync mechanism

```java
// StorageContainerMenuBase.java:573-577
protected void sendToServer(Consumer<CompoundTag> addData) {
    CompoundTag data = new CompoundTag();
    addData.accept(data);
    PacketDistributor.sendToServer(new SyncContainerClientDataPayload(data));
}
```

CompoundTag に好きな key を詰めて 1 packet で送る方式。Server 側は:

```java
// StorageContainerMenuBase.java:654 付近
public void handlePacket(CompoundTag data) { ... }
```

PEB 用には search phrase だけ送れば良いので **この pattern をそのまま使える**。

```java
// StorageContainerMenuBase.java:773-784 (setSearchPhrase)
if (isClientSide()) {
    sendToServer(data -> data.putString(SEARCH_PHRASE_TAG, searchPhrase));
}
```

### キーになる pattern (抜粋)

1. **Slot 位置を Menu で決めず Screen 側で動的更新** — `addSlot` 時は `(0, 0)`、Screen.init で `slot.x = ...; slot.y = ...` (StorageContainerMenuBase.java:256, StorageScreenBase.java:208-211)
2. **storage slot は filter (`isItemValid`) を InventoryHandler に委譲** (StorageContainerMenuBase.java:271-273 → InventoryHandler.java:392-395)
3. **shouldLockStorageItemSlot で「自分自身が入っている player slot」を取れなくする** (StorageContainerMenuBase.java:319-326)
4. **`broadcastChanges` で `realInventorySlots.size() != handler.getSlots() + 36 + extraSlotsSize` のとき再 open** = inventory size 不整合 detection (StorageContainerMenuBase.java:334-336)
5. **search/sort/openTab 等の UI state を CompoundTag で単一 packet 送信** (StorageContainerMenuBase.java:573-577)

---

## 3. Screen Base: StorageScreenBase.java

- パス: `client/gui/StorageScreenBase.java`
- 行数: **1395 行**

### 宣言

```java
// StorageScreenBase.java:57-58
public abstract class StorageScreenBase<S extends StorageContainerMenuBase<?>>
    extends AbstractContainerScreen<S>
    implements InventoryScrollPanel.IInventoryScreen {
```

### 重要定数

```java
// StorageScreenBase.java:66, :67, :70, :72
public static final int UPGRADE_INVENTORY_OFFSET = 21;
public static final int DISABLED_SLOT_X_POS = -2000;  // ← フィルタで非表示の slot を画面外に飛ばす
public static final int ERROR_SLOT_COLOR = ...;
public static final int HEIGHT_WITHOUT_STORAGE_SLOTS = 114;
```

### stackFilter 初期化

```java
// StorageScreenBase.java:100-101
private Predicate<ItemStack> stackFilter = stack -> searchBox == null || searchBox.getValue().isEmpty()
    || (!stack.isEmpty() && stack.getHoverName().getString().toLowerCase().contains(searchBox.getValue().toLowerCase()));
```

### init() で何を add しているか

```java
// StorageScreenBase.java:254-283
protected void init() {
    super.init();
    updateInventoryScrollPanel();
    craftingUIPart.setStorageScreen(this);
    initUpgradeSettingsControl();
    initUpgradeInventoryParts();
    addUpgradeSwitches();
    getMenu().setUpgradeChangeListener(c -> { ... });
    if (shouldShowSortButtons()) addSortButtons();
    addTransferButtons();
    addSearchBox();
    initializing = false;
}
```

### Search box 追加 (StorageScreenBase.java:285-304)

```java
protected void addSearchBox() {
    int x = 7;
    int xEnd = getTitleLineEndBeforeSortButtons();
    int width = xEnd - x;
    searchBox = new SearchBox(new Position(leftPos + x, topPos + 5), new Dimension(width, 10), this);
    searchBox.setResponder(this::onSearchPhraseChange);
    if (getMenu().shouldKeepSearchPhrase()) {
        searchBox.setValue(getMenu().getSearchPhrase());
    }
    addWidget(searchBox);  // ← addRenderableWidget でなく addWidget (描画は手動)
    ...
}
```

注: `addWidget` であって `addRenderableWidget` ではない → render は手動で `searchBox.render(...)` 呼び出し (StorageScreenBase.java:626-629)。

### slot 位置と background image の合わせ方

```java
// StorageScreenBase.java:200-221 (updateStorageSlotsPositions)
protected void updateStorageSlotsPositions() {
    int yPosition = 18;
    visibleSlotsCount = 0;
    int slotIndex = 0;
    while (slotIndex < getMenu().getNumberOfStorageInventorySlots()) {
        Slot slot = getMenu().getSlot(slotIndex);
        int lineIndex = visibleSlotsCount % getSlotsOnLine();
        slotIndex++;
        if (stackFilter.test(slot.getItem())) {
            slot.x = 8 + lineIndex * 18;
            slot.y = yPosition;
            visibleSlotsCount++;
            if (visibleSlotsCount % getSlotsOnLine() == 0) yPosition += 18;
        } else {
            slot.x = DISABLED_SLOT_X_POS;  // ← 画面外
        }
    }
}
```

**slot 位置 = `8 + lineIndex * 18, yPosition`** で 18px grid。`DISABLED_SLOT_X_POS = -2000` で非表示にする。background image 側は `GuiHelper.renderSlotsBackground(...)` (StorageScreenBase.java:866) で動的描画。

### 検索バーの key 干渉回避 pattern (重要)

**vanilla の E (inventory close) を奪う仕組みは TextBox.keyPressed の中にある:**

```java
// controls/TextBox.java:54-63
@Override
public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (!editBox.isFocused()) {
        return false;          // (A) focus 無いときは pass-through (E で inventory 閉じる)
    }
    editBox.keyPressed(keyCode, scanCode, modifiers);
    if (keyCode == GLFW.GLFW_KEY_ENTER) {
        onEnterPressed();
    }
    return keyCode != GLFW.GLFW_KEY_ESCAPE;  // (B) focus ありなら ESC 以外 true (全 vanilla key を block)
}
```

→ **二段構え**:
- focus 無し: false を返す → Screen.keyPressed の super が動き E で閉じる
- focus あり: ESC 以外で true → vanilla の E (inventory close) と矢印キー (slot 切替) を完全 block
- ESC: false → 親に伝搬し vanilla が ESC で inventory close 処理

Screen 側 keyPressed は modal overlay 制御のみで search の干渉ロジックは無い:

```java
// StorageScreenBase.java:1173-1184
public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (modalOverlay != null) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            setModalOverlay(null);
            return true;
        }
        modalOverlay.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }
    return super.keyPressed(keyCode, scanCode, modifiers);
}
```

→ Screen.keyPressed は SearchBox 検知すらしない。**`addWidget(searchBox)` + 親の `AbstractContainerScreen.keyPressed` がフォーカス済み widget に dispatch するという vanilla 機構に乗っているだけ**。TextBox の return 値で制御している。

### mouseClicked / mouseScrolled の処理

```java
// StorageScreenBase.java:1111-1133 (mouseClicked)
public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (modalOverlay != null) { ... }
    Slot slot = findSlot(mouseX, mouseY);
    if (hasShiftDown() && hasControlDown() && slot instanceof StorageInventorySlot && button == 0) {
        PacketDistributor.sendToServer(new TransferFullSlotPayload(slot.index));  // ctrl+shift で全部送る
        return true;
    }
    GuiEventListener focused = getFocused();
    if (focused != null && !focused.isMouseOver(mouseX, mouseY) && (focused instanceof WidgetBase widgetBase)) {
        widgetBase.setFocused(false);  // ← クリックで focus 外れる pattern
    }
    return super.mouseClicked(mouseX, mouseY, button);
}
```

```java
// StorageScreenBase.java:1165-1171 (mouseScrolled)
public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    if (modalOverlay != null) { modalOverlay.mouseScrolled(...); return true; }
    return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
}
```

→ scroll の本処理は **`InventoryScrollPanel.mouseScrolled` (NeoForge ScrollPanel 経由)** が `getFocused`/`getChildAt` で受ける。Screen は単に super に委譲。

### render flow (StorageScreenBase.java:544-573)

```java
public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
    ...
    super.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
    settingsTabControl.render(...);
    renderSuper(...);  // ← AbstractContainerScreen.render の copy で storage slot rendering を抜いた版
    ...
    sortButton.render(...);  sortByButton.render(...);
    upgradeSwitches.forEach(us -> us.render(...));
    renderErrorOverlay(...);
    settingsTabControl.renderTooltip(...);
    renderTooltip(...);
    renderModalOverlay(...);
}
```

`renderBg` 内 (StorageScreenBase.java:852-861):
```java
protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
    int x = (width - imageWidth) / 2;
    int y = (height - imageHeight) / 2;
    drawInventoryBg(guiGraphics, x, y, storageBackgroundProperties.getTextureName());
    if (inventoryScrollPanel == null) {
        drawSlotBg(guiGraphics, x, y, visibleSlotsCount);  // 動的描画
        drawSlotOverlays(guiGraphics);
    }
    drawUpgradeBackground(guiGraphics);
}
```

scrollPanel がある場合は `InventoryScrollPanel.drawBackground` 側で `screen.drawSlotBg(...)` が呼ばれる (`controls/InventoryScrollPanel.java:47-49`)。

### 重要 pattern まとめ

1. **EditBox の focus 状態で vanilla key を block する pattern** (controls/TextBox.java:54-63) — PEB で再利用必須
2. **filter で hidden の slot は `DISABLED_SLOT_X_POS=-2000` に飛ばす** (StorageScreenBase.java:218, :67) — slot を remove せず位置で隠す
3. **`addWidget` (not `addRenderableWidget`) + 手動 render** で描画順制御 (StorageScreenBase.java:295, :626-629)
4. **focus widget の外を click したら自動で `setFocused(false)`** (StorageScreenBase.java:1128-1131)
5. **search responder で slot 位置を即時再計算** (`onSearchPhraseChange` → `updateStorageSlotsPositions`/`inventoryScrollPanel.updateSlotsPosition`, StorageScreenBase.java:308-321)

---

## 4. SearchBox.java + TextBox.java (検索 widget)

### TextBox (controls/TextBox.java, 150 行)

- extends `WidgetBase`
- 内部に vanilla `EditBox` を持つ **委譲 wrapper** (composition over inheritance)
- `editBox = new EditBox(minecraft.font, x, y, w, h, Component.empty())` (TextBox.java:23)

### SearchBox (client/gui/SearchBox.java, 100 行)

- `class SearchBox extends TextBox` (SearchBox.java:17, package private)
- Constructor: `SearchBox(Position position, Dimension dimension, StorageScreenBase<?> screen)` (SearchBox.java:30-40)
  - `setBordered(false); setMaxLength(50); setUnfocusedEmptyHint(MAGNIFYING_GLASS);`
  - `MAGNIFYING_GLASS = "🔍"` (絵文字、SearchBox.java:22)

### Focus 状態管理 (SearchBox.java:60-71)

```java
@Override
public void setFocused(boolean focused) {
    if (isFocused() != focused) {
        lastFocusChangeTime = System.currentTimeMillis();  // expand animation のため
    }
    super.setFocused(focused);
    if (focused) setTextColor(-1);
    else setTextColor(UNFOCUSED_COLOR);
}
```

### mouseClicked (SearchBox.java:42-58)

```java
public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (!isMouseOver(mouseX, mouseY)) return false;
    if (isEditable()) {
        if (button == 0) {
            setFocused(true);
            screen.setFocused(this);  // ← Screen にも focus を通知 (key dispatch 用)
        } else if (button == 1) {
            setValue("");  // 右クリックでクリア
        }
        return true;
    }
    return super.mouseClicked(mouseX, mouseY, button);
}
```

### 矢印/Enter/Esc/E の処理

**SearchBox 自体は keyPressed を override していない**。すべて親 TextBox の実装に乗る:

```java
// controls/TextBox.java:54-63
public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (!editBox.isFocused()) return false;
    editBox.keyPressed(keyCode, scanCode, modifiers);
    if (keyCode == GLFW.GLFW_KEY_ENTER) onEnterPressed();
    return keyCode != GLFW.GLFW_KEY_ESCAPE;
}
```

| キー | focus 無し | focus あり |
|---|---|---|
| E (inventory key) | false → vanilla で閉じる | true → block (block して文字入力扱い) |
| 矢印 | false → vanilla | true → block (cursor 移動として editBox 内で消費) |
| Enter | false | `onEnterPressed()` + true |
| Esc | false → vanilla | false → vanilla で閉じる (focus 維持) |

→ **「focus あり時は ESC 以外すべての key を奪う」が pattern の核**

---

## 5. Slot 系

### SlotSuppliedHandler (common/gui/SlotSuppliedHandler.java, 43 行)

```java
// SlotSuppliedHandler.java:105-138
public class SlotSuppliedHandler extends SlotItemHandler {
    private final Supplier<IItemHandler> itemHandlerSupplier;
    private final int slot;

    public SlotSuppliedHandler(Supplier<IItemHandler> itemHandlerSupplier, int slot, int xPosition, int yPosition) {
        super(itemHandlerSupplier.get(), slot, xPosition, yPosition);
        this.itemHandlerSupplier = itemHandlerSupplier;
        this.slot = slot;
    }

    @Override
    public IItemHandler getItemHandler() { return itemHandlerSupplier.get(); }  // ← Supplier 経由で常に最新を取る

    @Override
    public boolean mayPlace(ItemStack stack) { return itemHandlerSupplier.get().isItemValid(slot, stack); }

    @Override
    public int getMaxStackSize() { return itemHandlerSupplier.get().getSlotLimit(slot); }

    @Override
    public void setChanged() {
        super.setChanged();
        if (itemHandlerSupplier.get() instanceof ISlotChangeListener contentsChangeListener) {
            contentsChangeListener.onSlotChanged(slot);
        }
    }
}
```

→ **`Supplier<IItemHandler>` を持つ**ので、handler が差し替わっても slot が壊れない。PEB の DataComponent handler は ItemStack ごとに instance が違うので、これは必須 pattern。

### FilterSlotItemHandler (common/gui/FilterSlotItemHandler.java, 24 行)

```java
// FilterSlotItemHandler.java:80-95
public class FilterSlotItemHandler extends SlotSuppliedHandler implements IFilterSlot {
    @Override public boolean mayPickup(Player playerIn) { return false; }
    @Override public int getMaxStackSize(ItemStack stack) { return 1; }
}
```

→ filter 専用 slot: 取り出せない、1 個固定。

### StorageInventorySlot (common/gui/StorageInventorySlot.java, 71 行)

```java
// StorageInventorySlot.java:15-21
public StorageInventorySlot(boolean isClientSide, IStorageWrapper storageWrapper, int slotIndex, Player player) {
    super(storageWrapper::getInventoryHandler, slotIndex, 0, 0);  // ← x=0, y=0 で生成
    ...
}

// :23-26
@Override
public boolean mayPlace(ItemStack stack) {
    return storageWrapper.getInventoryHandler().isItemValid(slotIndex, stack, player);
}

// :43-46
@Override
public int getMaxStackSize(ItemStack stack) {
    return storageWrapper.getInventoryHandler().getStackLimit(slotIndex, stack);
}

// :48-66 (safeInsert)
@Override
public ItemStack safeInsert(ItemStack stack, int maxCount) {
    if (!stack.isEmpty() && mayPlace(stack)) {
        ItemStack itemstack = getItem();
        int i = Math.min(Math.min(maxCount, stack.getCount()), getMaxStackSize(stack) - itemstack.getCount());
        if (itemstack.isEmpty()) {
            set(stack.split(i));
        } else if (ItemStack.isSameItemSameComponents(itemstack, stack)) {
            stack.shrink(i);
            ItemStack copy = itemstack.copy();
            copy.grow(i);
            set(copy);
        }
        return stack;
    } else { return stack; }
}
```

→ **`isSameItemSameComponents` 重要**: enchanted_book は enchantment ごとに DataComponent が異なるので `isSameItem` だと混ざる、`isSameItemSameComponents` でないと駄目 (1.21 で `isSameItemSameTags` から名前変わった)。

### slot.x / slot.y の決定方法

| 生成時 | (0, 0) で `addSlot` (StorageInventorySlot.java:16, StorageContainerMenuBase.java:256) |
|---|---|
| init後 | Screen の `updateStorageSlotsPositions` で `slot.x = 8 + lineIndex * 18; slot.y = yPosition;` (StorageScreenBase.java:208-211) |
| filter で非表示 | `slot.x = DISABLED_SLOT_X_POS` (=-2000, StorageScreenBase.java:218) |
| scroll | `InventoryScrollPanel.updateSlotsPosition` で `newX/newY` 計算、見えない行は `newY = -100` (controls/InventoryScrollPanel.java:140-162) |

background image とは: **そもそも static image を使わず `GuiHelper.renderSlotsBackground(...)` で動的描画** (StorageScreenBase.java:866) ので slot 数 / 列数を変えても自動で合う。

---

## 6. Inventory: InventoryHandler.java

- パス: `inventory/InventoryHandler.java`
- 行数: **577 行**

### 宣言

```java
// InventoryHandler.java:33
public abstract class InventoryHandler extends ItemStackHandler implements ITrackedContentsItemHandler, IInsertBlockOverride {
```

→ **vanilla `ItemStackHandler` 拡張** (NeoForge の標準 item handler)。abstract。

### Constructor (InventoryHandler.java:57-71)

```java
protected InventoryHandler(int numberOfInventorySlots, IStorageWrapper storageWrapper, CompoundTag contentsNbt, Runnable saveHandler, int baseSlotLimit, StackUpgradeConfig stackUpgradeConfig) {
    super(numberOfInventorySlots);
    ...
    RegistryHelper.getRegistryAccess().ifPresent(registryAccess -> deserializeNBT(registryAccess, contentsNbt.getCompound(INVENTORY_TAG)));
    ...
}
```

NBT-backed (CompoundTag を直接持つ)。**注意**: `StatefulComponentItemHandler` の方が DataComponent backed。**PEB は `StatefulComponentItemHandler` パターンを使うべき**。

### getSlots() の動的拡張

```java
// InventoryHandler.java:460-470 (changeSlots)
public void changeSlots(int diff) {
    NonNullList<ItemStack> previousStacks = stacks;
    stacks = NonNullList.withSize(previousStacks.size() + diff, ItemStack.EMPTY);
    for (int slot = 0; slot < previousStacks.size() && slot < stacks.size(); slot++) {
        stacks.set(slot, previousStacks.get(slot));
    }
    initStackNbts();
    saveInventory();
    getSlotTracker().refreshSlotIndexesFrom(this);
}
```

→ **`changeSlots(diff)` で動的にサイズ変更可能**。Screen は `broadcastChanges` の不整合検出 (StorageContainerMenuBase.java:334-336) で再 open を要求する。

### isItemValid (slot 制約)

```java
// InventoryHandler.java:392-400
public boolean isItemValid(int slot, ItemStack stack, @Nullable Player player) {
    return inventoryPartitioner.getPartBySlot(slot).isItemValid(slot, stack, player, super::isItemValid)
        && isAllowed(stack)                                                                      // ← 抽象 method
        && storageWrapper.getSettingsHandler().getTypeCategory(MemorySettingsCategory.class).matchesFilter(slot, stack);
}

@Override
public boolean isItemValid(int slot, ItemStack stack) {
    return isItemValid(slot, stack, null);
}

// :408
protected abstract boolean isAllowed(ItemStack stack);
```

→ **`isAllowed(ItemStack)` を subclass で override** すれば PEB の "enchanted_book 限定" が実装できる (e.g. `stack.is(Items.ENCHANTED_BOOK)`)。

### NBT serialize/deserialize

```java
// InventoryHandler.java:437-444
@Override
public CompoundTag serializeNBT(HolderLookup.Provider registries) {
    ListTag nbtTagList = new ListTag();
    nbtTagList.addAll(stackNbts.values());
    CompoundTag nbt = new CompoundTag();
    nbt.put("Items", nbtTagList);
    nbt.putInt("Size", getSlots());
    return nbt;
}
```

→ NBT 直書き。**PEB は DataComponent (`ItemContainerContents`) を使うので、この pattern より `StatefulComponentItemHandler` をベースにすべき**。

### onContentsChanged

```java
// InventoryHandler.java:94-101
public void onContentsChanged(int slot) {
    super.onContentsChanged(slot);
    inventoryPartitioner.getPartBySlot(slot).onContentsChanged(slot, super::setStackInSlot);
    if (persistent && updateSlotNbt(slot)) {
        saveInventory();
        triggerOnChangeListeners(slot);
    }
}
```

→ `persistent` フラグでクライアント時は save を抑止。

### 重要: DataComponent backed handler (StatefulComponentItemHandler.java)

```java
// inventory/StatefulComponentItemHandler.java:17
public class StatefulComponentItemHandler implements IItemHandlerModifiable, ISlotChangeListener {
    protected NonNullList<ItemStack> stacks;
    protected final MutableDataComponentHolder parent;
    protected final DataComponentType<ItemContainerContents> component;
    protected final int size;

    public StatefulComponentItemHandler(MutableDataComponentHolder parent, DataComponentType<ItemContainerContents> component, int size) {
        ...
        Preconditions.checkArgument(size <= 256, "The max size of ItemContainerContents is 256 slots.");  // ← 256 制限
        fillStacks();
    }
```

→ **PEB は 256 slot 上限**で `ItemContainerContents` (vanilla の `DataComponents.CONTAINER` 型) を使うので、これがそのまま base になる:

```java
// StatefulComponentItemHandler.java:131-137 (updateContents)
protected void updateContents(ItemStack stack, int slot) {
    validateSlotIndex(slot);
    ItemStack oldStack = stacks.get(slot);
    stacks.set(slot, stack);
    parent.set(component, ItemContainerContents.fromItems(stacks));  // ← DataComponent 書き戻し
    onContentsChanged(slot, oldStack, stack);
}
```

`parent` は ItemStack 自身 (ItemStack は `MutableDataComponentHolder` を実装)。**書き換えるたびに DataComponent 全体を再書き込み**する pattern。

---

## 7. Network

`network/` 配下に **12 payload + 1 package-info**:

| ファイル | record/class | 方向 | 役割 |
|---|---|---|---|
| `SyncAdditionalSlotInfoPayload.java` | payload | S→C | inaccessible slot / limit / filter / icon の追加情報を送る |
| `SyncBlockHighlightsPayload.java` | payload | S→C | block highlight (ranged 系 upgrade) |
| `SyncContainerClientDataPayload.java` | record (network/SyncContainerClientDataPayload.java:15) | C→S | **client→server の汎用 CompoundTag transport (検索 phrase 等)** |
| `SyncContainerStacksPayload.java` | payload | S→C | container 全 slot stack の bulk sync |
| `SyncDatapackSettingsTemplatePayload.java` | payload | S→C | datapack の template settings |
| `SyncEmptySlotIconsPayload.java` | payload | S→C | empty slot icon list |
| `SyncPlayerSettingsPayload.java` | payload | S→C | player config (search phrase keep 等) |
| `SyncRecentCraftedResultsPayload.java` | payload | S→C | recent craft 結果 |
| `SyncSlotChangeErrorPayload.java` | payload | S→C | slot insert error 通知 |
| `SyncSlotStackPayload.java` | record (network/SyncSlotStackPayload.java:14) | S→C | **特定 slot の stack を 1 個だけ更新** |
| `SyncTemplateSettingsPayload.java` | payload | S→C | template settings |
| `TransferFullSlotPayload.java` | payload | C→S | ctrl+shift click でその slot 全部移動 |
| `TransferItemsPayload.java` | payload | C→S | transfer button (storage⇄player) |

**PEB に必要なのは 2 種だけ**:
- `SyncContainerClientDataPayload` pattern (client→server で検索 phrase) — `StorageContainerMenuBase.java:573-577` で送信、`:654` で受信
- (slot 同期は vanilla の `AbstractContainerMenu.broadcastChanges` 経由で済むので追加 packet 不要)

---

## 8. PEB に取り込む価値の高い 5 パターン

### Pattern 1: DataComponent backed ItemHandler

**目的**: ItemStack 内部に container slot を持つ (PEB の本棚 = item)。

```java
// inventory/StatefulComponentItemHandler.java:17-29
public class StatefulComponentItemHandler implements IItemHandlerModifiable, ISlotChangeListener {
    public StatefulComponentItemHandler(MutableDataComponentHolder parent, DataComponentType<ItemContainerContents> component, int size) {
        ...
        Preconditions.checkArgument(size <= 256, "The max size of ItemContainerContents is 256 slots.");
        fillStacks();
    }
```

```java
// :131-137
protected void updateContents(ItemStack stack, int slot) {
    ...
    parent.set(component, ItemContainerContents.fromItems(stacks));
    onContentsChanged(slot, oldStack, stack);
}
```

**PEB への適用**: PEB の本棚アイテム ItemStack を `parent` に、`DataComponents.CONTAINER` (vanilla の `ItemContainerContents` 型) を `component` に、`size=256` で渡す。`isItemValid` を override して `stack.is(Items.ENCHANTED_BOOK)` を返す:

```java
@Override
public boolean isItemValid(int slot, ItemStack stack) {
    return stack.is(Items.ENCHANTED_BOOK) || stack.isEmpty();
}
```

### Pattern 2: Slot を Supplier 経由で持つ

**目的**: ItemStack handler は instance 差し替えが起こりうる (player が同じ slot 内で別 ItemStack に切り替える 等) ので、Slot に handler 実体を握らせない。

```java
// common/gui/SlotSuppliedHandler.java:105-119
public class SlotSuppliedHandler extends SlotItemHandler {
    private final Supplier<IItemHandler> itemHandlerSupplier;
    public SlotSuppliedHandler(Supplier<IItemHandler> itemHandlerSupplier, int slot, int xPosition, int yPosition) {
        super(itemHandlerSupplier.get(), slot, xPosition, yPosition);
        this.itemHandlerSupplier = itemHandlerSupplier;
        ...
    }
    @Override public IItemHandler getItemHandler() { return itemHandlerSupplier.get(); }
```

**PEB への適用**: Menu 側で `() -> myStorageItem.getOrDefault(MY_COMPONENT, ItemContainerContents.EMPTY)` のような Supplier を作るのではなく、handler instance を Supplier で返す (Menu が wrapper class を持って中で生成):

```java
new SlotSuppliedHandler(() -> bookshelfHandler, slotIndex, 0, 0)
```

### Pattern 3: SearchBox + EditBox focus 状態で vanilla key を block

**目的**: 検索バー focus 中に E (close inventory) や矢印が干渉しない。

```java
// client/gui/controls/TextBox.java:54-63
public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (!editBox.isFocused()) {
        return false;
    }
    editBox.keyPressed(keyCode, scanCode, modifiers);
    if (keyCode == GLFW.GLFW_KEY_ENTER) {
        onEnterPressed();
    }
    return keyCode != GLFW.GLFW_KEY_ESCAPE;
}
```

```java
// client/gui/SearchBox.java:42-58 (mouseClicked で screen.setFocused まで通知)
if (button == 0) {
    setFocused(true);
    screen.setFocused(this);
}
```

```java
// client/gui/StorageScreenBase.java:1128-1131 (外をクリックで自動 unfocus)
GuiEventListener focused = getFocused();
if (focused != null && !focused.isMouseOver(mouseX, mouseY) && (focused instanceof WidgetBase widgetBase)) {
    widgetBase.setFocused(false);
}
```

**PEB への適用**: TextBox を `WidgetBase extends AbstractWidget` で実装 (sophisticated は独自の WidgetBase を持つが、AbstractWidget で代用可)。focus 管理は同じ二段構え。`addWidget` で追加、render は手動。

### Pattern 4: 検索フィルタで slot を非表示にする (delete でなく x 位置)

**目的**: filter に当たらない slot は描画/click 対象から外したいが、Menu からは消したくない (sync 不整合になる)。

```java
// client/gui/StorageScreenBase.java:67
public static final int DISABLED_SLOT_X_POS = -2000;

// :200-221 (updateStorageSlotsPositions)
if (stackFilter.test(slot.getItem())) {
    slot.x = 8 + lineIndex * 18;
    slot.y = yPosition;
    visibleSlotsCount++;
    ...
} else {
    slot.x = DISABLED_SLOT_X_POS;
}
```

**PEB への適用**: enchant 名で検索する場合 `stack.getEnchantments()` から map を取って toString match。filter unmatched は x=-2000 へ:

```java
private Predicate<ItemStack> stackFilter = stack -> {
    String q = searchBox.getValue().toLowerCase();
    if (q.isEmpty()) return true;
    if (stack.isEmpty()) return false;
    // book の hover name は "Enchanted Book", enchant 名を見たい
    return stack.getEnchantments().keySet().stream()
        .anyMatch(holder -> holder.value().description().getString().toLowerCase().contains(q));
};
```

### Pattern 5: NeoForge ScrollPanel + 行単位 snap で大量 slot を描画

**目的**: 256 slot を全部表示は無理。N 行だけ見せて scroll で動かす。

```java
// client/gui/controls/InventoryScrollPanel.java:15-44
public class InventoryScrollPanel extends ScrollPanel {
    @Override protected int getScrollAmount() { return 18; }   // ← 1 行 = 18px
    @Override protected int getContentHeight() {
        int rows = numberOfSlots / slotsInARow + (numberOfSlots % slotsInARow > 0 ? 1 : 0);
        return rows * 18;
    }
```

```java
// :140-162 (updateSlotsPosition: scrollDistance から各 slot の x,y を計算)
public void updateSlotsPosition() {
    screen.setVisibleSlotsCount(0);
    int filteredSlotsCount = 0;
    for (int i = firstSlotIndex; i < firstSlotIndex + numberOfSlots; i++) {
        int rowOffset = (int) scrollDistance / 18;
        int row = filteredSlotsCount / slotsInARow - rowOffset;
        boolean matchesFilter = screen.getStackFilter().test(screen.getSlot(i).getItem());
        if (matchesFilter) filteredSlotsCount++;
        int column = screen.getVisibleSlotsCount() % slotsInARow;
        int newY = top - screen.getTopY() + row * 18 + TOP_Y_OFFSET;
        int newX = left - screen.getLeftX() + column * 18 + 1;
        if (newY < 1 || newY > height || !matchesFilter) {
            newY = -100;
        } else {
            screen.setVisibleSlotsCount(screen.getVisibleSlotsCount() + 1);
        }
        screen.getSlot(i).y = newY;
        screen.getSlot(i).x = newX;
    }
}
```

**PEB への適用**: NeoForge の `net.neoforged.neoforge.client.gui.widget.ScrollPanel` を `extends`。PEB は 16×16=256 slot で例えば 6 行 (96 slot) 表示 + scroll で 11 行スクロール:

```java
new InventoryScrollPanel(minecraft, this, 0, 256, 16, 6 * 18, getGuiTop() + 17, getGuiLeft() + 7);
```

`IInventoryScreen` interface (controls/InventoryScrollPanel.java:76-97) を Screen が実装すれば連動完了。

---

## 9. 罠 / 注意点

1. **`ItemContainerContents` の 256 slot 制限はハードリミット** (StatefulComponentItemHandler.java:27 で `Preconditions.checkArgument(size <= 256, ...)`). PEB の 256 slot 仕様はこの限界ちょうど。**257 以上にしたくなったら NBT 直で持つしかない** (= StatefulComponentItemHandler を使わず InventoryHandler 系へ)。

2. **ItemStack 内部 container は player の inventory slot index と本体 ItemStack の整合性を保つ必要がある**: `shouldLockStorageItemSlot=true` + `storageItemSlotIndex` を指定して、開いている間に本体を player slot から取り出せないようにする (StorageContainerMenuBase.java:319-326). 取り出せると `hasSomethingMessedWithStorage` で次 tick に閉じる (StorageContainerMenuBase.java:1558-1561, :334-336)。

3. **`isSameItemSameComponents` を使うこと** (StorageInventorySlot.java:55). enchanted_book は enchantment が DataComponent (`DataComponents.STORED_ENCHANTMENTS`) なので、`isSameItem` だと別 enchant の本が同じ slot に積まれてしまう。

4. **Server で stack を変更したら client へ送るのは vanilla `AbstractContainerMenu.broadcastChanges` 経由 (broadcastChangesIn) で十分**だが、`MutableDataComponentHolder.set` した時点で **player inventory slot 内の ItemStack 自体が変わる** のではなく ItemStack 内 component が変わるだけ。`PlayerInventory` には変更通知が来ない事がある → server 側で `player.containerMenu.broadcastChanges()` を明示呼ぶか、`Slot.setChanged()` 呼ぶ。StatefulComponentItemHandler は `ISlotChangeListener.onSlotChanged` (StatefulComponentItemHandler.java:152-154) で `updateContents` を再実行するだけで notify は外部任せ。

5. **TextBox.keyPressed が ESC で false を返すと Screen が閉じる** (controls/TextBox.java:62). search 中の ESC で「検索だけクリアして screen は閉じない」を実現するには、SearchBox 側で keyPressed を override し、ESC のときは `setValue("")` してから false ではなく true を返すように。sophisticated はそれをやっていないので **ESC で常に閉じる**動作 (これは意図的に見える)。

6. **`addWidget(searchBox)` (StorageScreenBase.java:295) は `addRenderableWidget` ではない**ので、自動で render されない。手動で `searchBox.render(...)` を呼ぶ (StorageScreenBase.java:626-629)。これを忘れると search box が消える。

7. **Slot.x / Slot.y は public field で直接書き換える** (StorageScreenBase.java:208-211)。これは vanilla の `Slot.x` `Slot.y` がそうなっているため。 NeoForge 1.21 でも変わっていない。

8. **`addStorageInventorySlots` は再 init で呼び直される** (StorageContainerMenuBase.java:1635-1636 `refreshInventorySlotsIfNeeded`)。slot list を `slots.clear(); lastSlots.clear(); ...` してから再構築。**PEB は size 動的変更しないなら無視可**。

9. **Screen は `InventoryScrollPanel.IInventoryScreen` を実装する必要がある** (StorageScreenBase.java:58, controls/InventoryScrollPanel.java:76)。`getSlot(int)`, `getStackFilter()`, `setVisibleSlotsCount(int)`, `drawSlotBg(...)`, `renderInventorySlots(...)`, `isMouseOverSlot(...)` を実装する。

10. **`Predicate<ItemStack> stackFilter` の default は「searchBox が null か空なら通す」** (StorageScreenBase.java:100-101)。`updateSearchFilter` で `@`, `#` プレフィクスを mod/tooltip 検索に使うミニ DSL (StorageScreenBase.java:323-345)。PEB で enchant 名検索を実装するならここを真似て pattern を拡張する。

---

## 結論 (PEB 設計のたたき台)

PEB は以下の構成で実装可能 (Sophisticated を直接依存せず、pattern だけ参照):

| 層 | 採用 pattern | 元 source |
|---|---|---|
| Item | 本棚 Item を Vanilla の `DataComponents.CONTAINER` (or 独自 `DataComponentType<ItemContainerContents>`) を持つように登録 | (新規) |
| ItemHandler | `StatefulComponentItemHandler` をそのまま拝借し subclass で `isItemValid` を `stack.is(ENCHANTED_BOOK)` に | StatefulComponentItemHandler.java:17 |
| Menu | `AbstractContainerMenu` 直 extend、constructor で player inventory + 256 storage slot + storageItemSlot lock | StorageContainerMenuBase.java:101 構造を簡略化 |
| Slot | `SlotItemHandler` 直 extend (Supplier 経由)、`safeInsert` で `isSameItemSameComponents` 使用 | SlotSuppliedHandler.java:105 + StorageInventorySlot.java:49 |
| Screen | `AbstractContainerScreen` 直 extend、search box を `addWidget` (not Renderable)、`InventoryScrollPanel` 利用 | StorageScreenBase.java:58, :285, :358 |
| SearchBox | `EditBox` 内包 wrapper、`keyPressed` で focus 時 ESC 以外 true | TextBox.java:54 + SearchBox.java:42 |
| Scroll | NeoForge `ScrollPanel` extend、`getScrollAmount=18`, slot 位置を行単位 snap | InventoryScrollPanel.java:36 |
| Sync | search phrase を `SyncContainerClientDataPayload` 相当の自前 record で C→S | StorageContainerMenuBase.java:573 |

**Sophisticated 本体に依存する必要なし**。pattern を参照するだけで PEB は独立 mod として成立する。

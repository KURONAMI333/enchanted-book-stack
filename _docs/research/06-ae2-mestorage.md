# AE2 MEStorage 構造解析 (viewport pattern 本家)

ソース: AE2 (NeoForge 系) 2026-05 時点
- `MEStorageMenu.java` 749 行
- `MEStorageScreen.java` 871 行
- `Repo.java` 475 行
- `RepoSlot.java` 81 行
- `ClientReadOnlySlot.java` 69 行

全行数・ファイルパスは grep 検証済み。 推測なし。

---

## 1. MEStorageMenu (server side)

### Constructor / 主要 fields (`MEStorageMenu.java:113-202`)

```java
public class MEStorageMenu extends AEBaseMenu
        implements IConfigurableObject, IMEInteractionHandler, LinkStatusAwareMenu,
        KeyTypeSelectionMenu {
```

主要 field:

| field | 行 | 役割 |
|---|---|---|
| `MEStorage storage` | 130 | 本体ストレージ (server) |
| `IEnergySource energySource` | 132 | 電力減算 (PEB では不要) |
| `IncrementalUpdateHelper updateHelper` | 134 | AEKey→serial mapping & 変更追跡 |
| `IClientRepo clientRepo` | 142 | client 側 repo 参照 (nullable, client only) |
| `Set<AEKey> previousCraftables` | 147 | 前 tick の craftable 一覧 (差分検出用) |
| `KeyCounter previousAvailableStacks` | 148 | 前 tick の在庫 (差分検出用) |

### Server 側で何 slot を持つか

**ME 仮想 slot は server に存在しない**。 server slot は次のみ:

- `viewCellSlots` (`MEStorageMenu.java:177-187`) — `RestrictedInputSlot` (view cell があるホストのみ)
- upgrade slots (`setupUpgrades(host.getUpgrades())` `MEStorageMenu.java:191`)
- player inventory slots (`bindInventory` 経由 `MEStorageMenu.java:199-201`)

256 slot grid は **完全に client 側の virtual slot (`RepoSlot`)** で、 server には 1 つも存在しない。 これが viewport pattern の本質。

### 内容物 source と sync の流れ

```
storage (MEStorage)  ← server 唯一の source
  ↓ broadcastChanges() ごとに getAvailableStacks() を呼ぶ
KeyCounter availableStacks (each tick)
  ↓ 前 tick (previousAvailableStacks) との差分
IncrementalUpdateHelper.changes (AEKey set)
  ↓ MEInventoryUpdatePacket.Builder
client へ送信
```

### broadcast / sync packet 送信タイミング (`MEStorageMenu.java:229-289`)

`broadcastChanges()` (vanilla menu のフレーム関数) で毎 tick:

```java
// 252-253
var craftables = getCraftablesFromGrid();
var availableStacks = storage.getAvailableStacks();
```

差分検出は `Sets.difference` + `previousAvailableStacks.removeAll(availableStacks)` (`MEStorageMenu.java:262-270`):

```java
// Craftables
Sets.difference(previousCraftables, craftables).forEach(updateHelper::addChange);
Sets.difference(craftables, previousCraftables).forEach(updateHelper::addChange);

// Available changes
previousAvailableStacks.removeAll(availableStacks);
previousAvailableStacks.removeZeros();
previousAvailableStacks.keySet().forEach(updateHelper::addChange);
```

その後 `updateHelper.hasChanges()` なら packet 構築 (`MEStorageMenu.java:272-278`):

```java
if (updateHelper.hasChanges()) {
    var builder = MEInventoryUpdatePacket
            .builder(containerId, updateHelper.isFullUpdate(), getPlayer().registryAccess());
    builder.setFilter(this::isKeyVisible);
    builder.addChanges(updateHelper, availableStacks, craftables, requestables);
    builder.buildAndSend(this::sendPacketToClient);
    updateHelper.commitChanges();
}
```

**初回 (`fullUpdate=true`)** は `addFull` で全 stack 送信、 以後は差分。

### Player click → server 処理の flow

1. client が `MEInteractionPacket(containerId, serial, action)` を送信 (`MEStorageScreen.java:529`)
2. `MEInteractionPacket.handleOnServer` (`MEInteractionPacket.java:43-51`):
   ```java
   if (player.containerMenu instanceof IMEInteractionHandler handler) {
       if (player.containerMenu.containerId != containerId) return;
       handler.handleInteraction(serial, action);
   }
   ```
3. `MEStorageMenu.handleInteraction(serial, action)` (`MEStorageMenu.java:373-403`):
   - `serial == -1` なら空 virtual slot → `handleNetworkInteraction(player, null, action)` (持ち手アイテムを格納のみ)
   - 通常は `updateHelper.getBySerial(serial)` で AEKey 取得
   - **serial がもう知らないキー (race) なら return 安全に無視**:
     ```java
     // 397-401
     AEKey stack = getStackBySerial(serial);
     if (stack == null) {
         // This can happen if the client sent the request after we removed the item
         return;
     }
     ```

### shift-click 等の処理 (`MEStorageMenu.java:451-548`)

`handleNetworkInteraction` で `InventoryAction` を switch:

| Action | 行 | 動作 |
|---|---|---|
| `SHIFT_CLICK` | 452 | `moveOneStackToPlayer(clickedItem)` — 1 スタック extract → player inv |
| `ROLL_DOWN` (shift+wheel up) | 453-463 | 持ち手から 1 個 insert |
| `ROLL_UP` / `PICKUP_SINGLE` | 464-487 | 1 個 extract → 持ち手 |
| `PICKUP_OR_SET_DOWN` (左クリック) | 488-504 | 持ち手あり→insert、 なし→maxStack extract |
| `SPLIT_OR_PLACE_SINGLE` (右クリック) | 505-528 | 持ち手あり→1 個 insert、 なし→半分 extract |
| `CREATIVE_DUPLICATE` (mid click in creative) | 529-535 | maxStack で持ち手にセット |
| `MOVE_REGION` (Space + click) | 536-543 | player inv 全 slot を移動先に試行 |

`moveOneStackToPlayer` (`MEStorageMenu.java:607-635`):

```java
var destinationSlots = getQuickMoveDestinationSlots(what.toStack(), false);
for (var destinationSlot : destinationSlots) {
    var amount = getPlaceableAmount(destinationSlot, what);
    if (amount <= 0) continue;
    var extracted = StorageHelper.poweredExtraction(...);
    destinationSlot.setByPlayer(...);
    return true;
}
```

---

## 2. MEStorageScreen (client side)

### 主要 fields (`MEStorageScreen.java:106-125`)

```java
private static final int MIN_ROWS = 2;                       // 108
private static String rememberedSearch = "";                 // 110 (static = screen 再 open でも保持)
protected final Repo repo;                                   // 113
private final AETextField searchField;                       // 117
private int rows = 0;                                        // 118
private SettingToggleButton<ViewItems> viewModeToggle;       // 119
private SettingToggleButton<SortOrder> sortByToggle;         // 120
private final SettingToggleButton<SortDir> sortDirToggle;    // 121
private final Scrollbar scrollbar;                           // 124
```

### init() / constructor で何を add しているか

constructor (`MEStorageScreen.java:127-217`) の add 順序:

| 行 | widget | 名前 |
|---|---|---|
| 140 | `widgets.addTextField("search")` → `AETextField` | 検索バー |
| 143 | `widgets.addScrollBar("scrollbar", Scrollbar.BIG)` | スクロール |
| 144 | `new Repo(scrollbar, this)` | client cache |
| 145 | `menu.setClientRepo(this.repo)` | menu に逆参照を仕込む |
| 146 | `repo.setUpdateViewListener(this::updateScrollbar)` | view 変更時に scrollbar 高さ再計算 |
| 149 | `searchField.setResponder(this::setSearchText)` | 入力ごとに `repo.setSearchString` |
| 151 | `imageWidth = style.getScreenWidth()` | 横幅 |
| 152 | `imageHeight = style.getScreenHeight(0)` | 高さ (行数で再計算) |
| 154 | `menu.setGui(this::onMenuReceivedClientUpdate)` | server からの設定変更通知 |
| 156-161 | viewCells panel (条件) | |
| 163-167 | crafting status button (条件) | |
| 169-185 | sortBy / viewMode / typeFilter / sortDir toggle | |
| 187-189 | terminal settings, terminal style toggle | |
| 191-193 | upgrades panel | |

### `init()` (`MEStorageScreen.java:357-385`) — slot 再生成

`init()` は画面サイズが変わるたびに呼ばれ、 既存 RepoSlot を **全削除して作り直す** ところがコア:

```java
// 359-360
var availableHeight = height - 2 * AEConfig.instance().getTerminalMargin();
this.rows = Math.max(MIN_ROWS, config.getTerminalStyle().getRows(style.getPossibleRows(availableHeight)));

// 366
this.imageHeight = style.getScreenHeight(rows);

// 368-378 ← ここが本命
List<Slot> slots = this.menu.slots;
slots.removeIf(slot -> slot instanceof RepoSlot);

int repoIndex = 0;
for (int row = 0; row < this.rows; row++) {
    for (int col = 0; col < style.getSlotsPerRow(); col++) {
        Point pos = style.getSlotPos(row, col);
        slots.add(new RepoSlot(this.repo, repoIndex++, pos.getX(), pos.getY()));
    }
}

super.init();
```

**重要**: `menu.slots` に直接 add している (これは vanilla の `AbstractContainerMenu.slots: NonNullList<Slot>`)。 client only。 server 側の `menu.slots` には RepoSlot は存在せず、 `containerId` が同じ client-side menu の `slots` フィールドに対し client 側スクリーンが追加している。

### 検索バー — vanilla key 干渉回避 (本命)

**`AETextField.keyPressed` (`AETextField.java:106-115`)**:

```java
@Override
public boolean keyPressed(KeyEvent event) {
    if (super.keyPressed(event)) {
        return true;
    }

    // Swallow all key presses except for focus escape when we're focused to prevent "e" from
    // closing the window instead of typing into the text field
    return isFocused() && canConsumeInput() && event.key() != GLFW.GLFW_KEY_TAB
            && event.key() != GLFW.GLFW_KEY_ESCAPE;
}
```

仕組み:

1. `super.keyPressed(event)` は EditBox の通常処理 (テキスト編集系を消費して true)
2. それで消費されなかったキー (E, W, A, S, D, Q, 数字, etc.) について
3. **focus 中で `canConsumeInput()` なら true を返して全部 swallow**
4. ただし `TAB` と `ESCAPE` は除外 → focus 移動とウィンドウ閉じは許可

これにより vanilla `AbstractContainerScreen.keyPressed` の inventory key 判定 (`if (this.minecraft.options.keyInventory.matches(...))`) には到達しない (子 widget が true を返したので)。

加えて `MEStorageScreen.keyPressed` (`MEStorageScreen.java:752-765`):

```java
@Override
public boolean keyPressed(KeyEvent event) {
    if (this.searchField.isFocused() && event.key() == GLFW.GLFW_KEY_ENTER) {
        this.searchField.setFocused(false);
        this.setFocused(null);
        return true;
    }

    if (!this.searchField.isFocused() && isCloseHotkey(event)) {
        this.getPlayer().closeContainer();
        return true;
    }

    return super.keyPressed(event);
}
```

→ Enter で focus 解除、 search 未 focus 時のホットキー処理 (portable terminal 用)。

`MEStorageScreen.charTyped` (`MEStorageScreen.java:736-743`):

```java
@Override
public boolean charTyped(CharacterEvent event) {
    if (event.codepoint() == ' ' && this.searchField.getValue().isEmpty()) {
        return true;
    }
    return super.charTyped(event);
}
```

→ 空の search field に半角スペースを最初に打たせない (`MOVE_REGION` の Space ホットキーとの衝突回避)。

### Viewport — 画面 slot grid サイズ / scroll 機構

行数決定: `MIN_ROWS = 2` (`MEStorageScreen.java:108`), 上限は `style.getPossibleRows(availableHeight)` (`MEStorageScreen.java:360`)。 ユーザー設定で 6/12/Tall 等から選ぶ。

`getSlotsPerRow()` (`MEStorageScreen.java:354`):
```java
private int getSlotsPerRow() {
    return style.getSlotsPerRow();
}
```

`TerminalStyle.getSlotPos(row, col)` (`TerminalStyle.java:141`) が **絶対座標** を返す。

スクロール:
- `Scrollbar` widget (`MEStorageScreen.java:124`)
- `updateScrollbar()` (`MEStorageScreen.java:325-331`):
  ```java
  scrollbar.setHeight(this.rows * style.getRow().getSrcHeight() - 2);
  int totalRows = (this.repo.size() + getSlotsPerRow() - 1) / getSlotsPerRow();
  if (repo.hasPinnedRow()) totalRows++;
  scrollbar.setRange(0, totalRows - this.rows, Math.max(1, this.rows / 6));
  ```
- `Repo.get(idx)` 内で `idx += this.src.getCurrentScroll() * this.rowSize` (`Repo.java:336`) — view の offset 計算は **repo 側に集約**。 RepoSlot は素朴に `repoIndex` だけ持っていれば良い。

`Scrollbar.wantsAllMouseWheelEvents` (`Scrollbar.java:267-270`) が **画面内どこでも wheel を奪う** → grid 上で wheel しても scrollbar が動く。

### mouseClicked (`MEStorageScreen.java:506-525`)

```java
@Override
public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    // Right-clicking on the search field should clear it
    if (this.searchField.isMouseOver(event.x(), event.y()) && event.button() == 1) {
        this.searchField.setValue("");
        setSearchText("");
        // Don't return immediately to also grab focus.
    }

    // handler for middle mouse button crafting in survival mode
    if (Minecraft.getInstance().options.keyPickItem.matchesMouse(event)) {
        Slot slot = this.getHoveredSlot(event.x(), event.y());
        if (slot instanceof RepoSlot repoSlot && repoSlot.isCraftable()) {
            handleGridInventoryEntryMouseClick(repoSlot.getEntry(), event.button(), ContainerInput.CLONE);
            return true;
        }
    }

    return super.mouseClicked(event, doubleClick);
}
```

通常 click は親 `AEBaseScreen.mouseClicked` → vanilla 経由で `slotClicked` に降りる。

### slotClicked (`MEStorageScreen.java:548-555`)

```java
@Override
protected void slotClicked(Slot slot, int slotIdx, int mouseButton, ContainerInput clickType) {
    if (slot instanceof RepoSlot repoSlot) {
        handleGridInventoryEntryMouseClick(repoSlot.getEntry(), mouseButton, clickType);
        return;
    }
    super.slotClicked(slot, slotIdx, mouseButton, clickType);
}
```

**ここで vanilla の通常 slot 処理を遮断**して `handleGridInventoryEntryMouseClick` に流す。 RepoSlot は server 側に存在しないので vanilla の `MenuType.clickMenuButton` 経由の処理を呼ばせてはいけない。

### handleGridInventoryEntryMouseClick (`MEStorageScreen.java:230-313`)

mouseButton + clickType + Shift / Space を見て `InventoryAction` を組み立て、 `menu.handleInteraction(serial, action)` を呼ぶ。 ここがすべての click → packet 変換のハブ。

主要パス:

| 入力 | InventoryAction |
|---|---|
| 左クリック (持ち手なし) | `PICKUP_OR_SET_DOWN` |
| 左クリック (持ち手あり) | `PICKUP_OR_SET_DOWN` (insert される) |
| 右クリック | `SPLIT_OR_PLACE_SINGLE` |
| Shift + 左クリック | `SHIFT_CLICK` |
| Shift + 右クリック | `PICKUP_SINGLE` |
| Space + クリック | `MOVE_REGION` |
| middle click + creative | `CREATIVE_DUPLICATE` |
| middle click + craftable | `AUTO_CRAFT` |

Space ホットキーは `InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE)` (`MEStorageScreen.java:302`) で **その瞬間の物理キー状態** を直接見る。

### mouseScrolled (`MEStorageScreen.java:528-546`)

```java
@Override
public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
    if (deltaY != 0 && getMinecraft().hasShiftDown()) {
        if (this.getHoveredSlot(x, y) instanceof RepoSlot repoSlot) {
            GridInventoryEntry entry = repoSlot.getEntry();
            long serial = entry != null ? entry.getSerial() : -1;
            final InventoryAction direction = deltaY > 0 ? InventoryAction.ROLL_DOWN
                    : InventoryAction.ROLL_UP;
            int times = (int) Math.abs(deltaY);
            for (int h = 0; h < times; h++) {
                final MEInteractionPacket p = new MEInteractionPacket(this.menu.containerId, serial, direction);
                ClientPacketDistributor.sendToServer(p);
            }
            return true;
        }
    }
    return super.mouseScrolled(x, y, deltaX, deltaY);
}
```

**Shift+wheel** 時のみ slot 上で「1 個 insert / extract」を発火。 通常 wheel は `super` → `AEBaseScreen.mouseScrolled` → `widgets.onMouseWheel` で Scrollbar が消費。

### BG / slot rendering

`drawBG` (`MEStorageScreen.java:582-614`): header → 行ごとの `style.getRow()` blitter → bottom。 行数によって構造が伸縮する。

`extractSlot` (`MEStorageScreen.java:617-655`): RepoSlot だけ自前 render:
- `AEKeyRendering.drawInGui(...)` で アイテム描画
- `StackSizeRenderer.renderSizeLabel(...)` で数量 (`+` か K/M フォーマット)
- craftable は `+` を overlay

`drawFG` (`MEStorageScreen.java:443-464`): pinned 行装飾 + crafting jobs カウント + disconnect overlay。

`renderLinkStatus` (`MEStorageScreen.java:466-486`): 切断時 grid 全体を `0x3f000000` で覆い、 中央にエラーテキスト。

### sort button (`MEStorageScreen.java:169-186`)

`addToLeftToolbar` で `SettingToggleButton<SortOrder>` / `SortDir` / `ViewItems` を縦に並べる。 callback は `toggleServerSetting`:

```java
// 833-837
private <SE extends Enum<SE>> void toggleServerSetting(SettingToggleButton<SE> btn, boolean backwards) {
    SE next = btn.getNextValue(backwards);
    ServerboundPacket message = new ConfigValuePacket(btn.getSetting(), next);
    ClientPacketDistributor.sendToServer(message);
    btn.set(next);
}
```

→ 設定変更は server に packet 送って同期。 server 側で `clientCM` ⇆ `serverCM` の diff を tick ごとに送り返す (`MEStorageMenu.java:235-242`)。

---

## 3. Repo (client cache)

### データ source / 構造

```java
// Repo.java:79-85
private final BiMap<Long, GridInventoryEntry> entries = HashBiMap.create();  // serial → entry
private final ArrayList<GridInventoryEntry> view = new ArrayList<>();         // filter+sort 後
private final ArrayList<GridInventoryEntry> pinnedRow = new ArrayList<>();    // 先頭行
```

- `entries` = master cache (server から受け取った全件)
- `view` = filter + sort 適用後の表示順
- `pinnedRow` = ピン留め (任意)

### update from server packet (`Repo.java:107-145`)

```java
@Override
public final void handleUpdate(boolean fullUpdate, List<GridInventoryEntry> entries) {
    if (fullUpdate) {
        clear();
    }
    for (var entry : entries) {
        handleUpdate(entry);
    }
    updateView();
}
```

個別 entry 処理 (`Repo.java:118-145`):

- 初見 serial → `entries.put(serial, serverEntry)` (`what != null` 必須)
- 既知 serial + `!isMeaningful()` → `entries.remove(serial)`
- 既知 serial + `what == null` (差分パケット) → 既存の `what` を保持して数量だけ差し替え
- 既知 serial + 完全な entry → 上書き

### filter / sort / view 構築 (`Repo.java:147-225`)

`updateView()` (`Repo.java:147`):

1. **paused 中** (`Repo.java:151-176`): 既存 view の位置を保ったまま、 削除された slot のみ後乗せ。 player の mis-click 防止。
2. **通常** (`Repo.java:177-184`): `view.clear()` → `entries.values()` を全件 `addEntriesToView` で再構築。
3. ソート (`Repo.java:188-195`):
   ```java
   var sortOrder = this.sortSrc.getSortBy();
   var sortDir = this.sortSrc.getSortDir();
   this.view.sort(getComparator(sortOrder, sortDir));
   ```
4. `updateViewListener.run()` (`Repo.java:200-202`) → screen の `updateScrollbar` をキック。

`addEntriesToView` (`Repo.java:206-245`) のフィルタ順:

```java
for (var entry : entries) {
    // 1. ピン留め判定 (先取り)
    if (hasPinnedRow && pinnedRow.size() < rowSize && PinnedKeys.isPinned(entry.getWhat())) {
        pinnedRow.add(entry); continue;
    }
    // 2. partitionList (view cell フィルタ)
    if (this.partitionList != null && !this.partitionList.isListed(entry.getWhat())) continue;
    // 3. ViewItems (CRAFTABLE / STORED / ALL)
    if (viewMode == ViewItems.CRAFTABLE && !entry.isCraftable()) continue;
    if (viewMode == ViewItems.STORED && entry.getStoredAmount() == 0) continue;
    // 4. type filter
    if (!typeFilter.contains(entry.getWhat().getType())) continue;
    // 5. search 文字列マッチ
    if (search.matches(entry)) {
        this.view.add(entry);
    }
}
```

### scrollOffset との連携 (`Repo.java:323-345`)

```java
public final GridInventoryEntry get(int idx) {
    if (!this.pinnedRow.isEmpty()) {
        if (idx < this.rowSize) {
            if (idx < this.pinnedRow.size()) return this.pinnedRow.get(idx);
            return null;
        }
        idx -= this.rowSize;
    }
    idx += this.src.getCurrentScroll() * this.rowSize;  // ← ここで scrollbar 値を反映
    if (idx >= this.view.size()) return null;
    return this.view.get(idx);
}
```

`src` = `IScrollSource` = `Scrollbar` インスタンス。 RepoSlot は単に `repo.get(this.offset)` を呼ぶだけで、 スクロール後の lookup を repo が完結。

### isPaused / setPaused (`Repo.java:425-435`)

```java
public void setPaused(boolean paused) {
    if (this.paused != paused) {
        this.paused = paused;
        if (!paused) updateView();
    }
}
```

`MEStorageScreen.updateBeforeRender` (`MEStorageScreen.java:381`) で:
```java
repo.setPaused(getMinecraft().hasShiftDown());
```

→ **Shift 押下中は view の並び替えを止める** = shift-click 連打中にスタックが動かない UX。

---

## 4. RepoSlot (virtual slot)

完全引用 (`RepoSlot.java:1-81`):

```java
package appeng.client.gui.me.common;

import net.minecraft.world.item.ItemStack;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.slot.ClientReadOnlySlot;

public class RepoSlot extends ClientReadOnlySlot {

    private final Repo repo;
    private final int offset;

    public RepoSlot(Repo repo, int offset, int displayX, int displayY) {
        super(displayX, displayY);
        this.repo = repo;
        this.offset = offset;
    }

    public int getRepoViewIndex() { return this.offset; }

    public GridInventoryEntry getEntry() {
        if (this.repo.isEnabled()) {
            return this.repo.get(this.offset);
        }
        return null;
    }

    public long getStoredAmount() {
        GridInventoryEntry entry = getEntry();
        return entry != null ? entry.getStoredAmount() : 0;
    }

    public long getRequestableAmount() {
        GridInventoryEntry entry = getEntry();
        return entry != null ? entry.getRequestableAmount() : 0;
    }

    public boolean isCraftable() {
        GridInventoryEntry entry = getEntry();
        return entry != null && entry.isCraftable();
    }

    @Override
    public ItemStack getItem() {
        GridInventoryEntry entry = getEntry();
        if (entry != null) {
            return entry.getWhat().wrapForDisplayOrFilter();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean hasItem() {
        return getEntry() != null;
    }
}
```

ポイント:

- **`offset` は固定 (RepoSlot 作成時の row*cols+col)**。 内容物は `repo.get(offset)` が scrollOffset を加味した dynamic lookup。
- `getItem()` は毎フレーム呼ばれる → 常に最新の entry を返す。
- click は `RepoSlot` 自体が何もせず、 `MEStorageScreen.slotClicked` で `instanceof RepoSlot` 判定 → 専用 handler に分岐。

---

## 5. ClientReadOnlySlot

完全引用 (`ClientReadOnlySlot.java:30-69`):

```java
public class ClientReadOnlySlot extends Slot {
    /**
     * We use this fake/empty inventory to prevent other mods from attempting to interact with anything based on this
     * slot's inventory/slot index.
     */
    private static final Container EMPTY_INVENTORY = new SimpleContainer(0);

    public ClientReadOnlySlot(int xPosition, int yPosition) {
        super(EMPTY_INVENTORY, 0, xPosition, yPosition);
    }

    public ClientReadOnlySlot() {
        this(0, 0);
    }

    @Override public final boolean mayPlace(ItemStack stack) { return false; }
    @Override public final void set(ItemStack stack) {}
    @Override public final int getMaxStackSize() { return 0; }
    @Override public final ItemStack remove(int amount) { return ItemStack.EMPTY; }
    @Override public final boolean mayPickup(Player player) { return false; }
}
```

vanilla `Slot` との差分:

| 項目 | vanilla | ClientReadOnlySlot |
|---|---|---|
| container | 実 Container | `SimpleContainer(0)` (空) |
| index | 任意 | 常に 0 |
| `mayPlace` | true | **final false** |
| `set` | container に書き込む | **no-op** |
| `getMaxStackSize` | 64 | **0** |
| `remove` | container から取る | **EMPTY** |
| `mayPickup` | true | **final false** |

全 override が `final` で、 vanilla / 他 mod / JEI 等が誤って書き込み・取り出しを試みても無害化。

---

## 6. Network protocol

### Packet 一覧

**Server → Client**:

| packet | 役割 |
|---|---|
| `MEInventoryUpdatePacket` | grid 内容の full/delta sync |
| `SetLinkStatusPacket` | 接続/切断状態 |
| `ConfigValuePacket` | sort 等の設定同期 (双方向) |
| guisync field (`@GuiSync(100)` 等) | `activeCraftingJobs`, `searchKeyTypes` |

**Client → Server**:

| packet | 役割 |
|---|---|
| `MEInteractionPacket(containerId, serial, action)` | slot click → 動作 |
| `ConfigValuePacket` | 設定変更 |
| `SwitchGuisPacket` | crafting status へ |

### Full sync vs delta sync (`MEInventoryUpdatePacket.java:103-167`)

- `Builder.addFull(updateHelper, networkStorage, craftables, requestables)` — **全 key を**`getOrAssignSerial` で serialize、 `what` を必ず同梱
- `Builder.addChanges(updateHelper, ...)` — 変更分のみ:
  - 新規 key → `sendKey = key` (full info)
  - 既知 key → `sendKey = null` (serial だけで更新)
  - 在庫 0 になった key → `(serial, null, 0, 0, false)` 送って `removeSerial`

### Packet 分割 (`MEInventoryUpdatePacket.java:49-50, 174-189`)

```java
private static final int UNCOMPRESSED_PACKET_BYTE_LIMIT = 512 * 1024;
// ...
if (data.writerIndex() >= UNCOMPRESSED_PACKET_BYTE_LIMIT || entryCount >= Short.MAX_VALUE) {
    flushData();
}
```

大量在庫時は自動分割。 2 つめ以降は `fullUpdate=false`。

### Client 受信 (`AEClientboundPacketHandler.java:205-219`)

```java
public void handleMEInventoryUpdatePacket(MEInventoryUpdatePacket packet, Minecraft minecraft, Player player) {
    if (player.containerMenu.containerId == packet.containerId()
            && player.containerMenu instanceof MEStorageMenu meMenu) {
        var clientRepo = meMenu.getClientRepo();
        if (clientRepo == null) {
            AELog.info("Ignoring ME inventory update packet because no client repo is available.");
            return;
        }
        var actualEntries = packet.getActualEntries();
        if (actualEntries != null) {
            clientRepo.handleUpdate(packet.fullUpdate(), actualEntries);
        }
    }
}
```

containerId 一致と menu 型チェック → repo.handleUpdate。

---

## 7. PEB に直接適用すべき 10 パターン

### P1. virtual slot は server に置かない (`MEStorageMenu.java:177-202`)

server menu には player inv + 必要なら upgrade/view slot だけ持たせる。 grid slot は **client init() で `menu.slots.add(new RepoSlot(...))`**。 これで「server に存在しない動的 256 slot」が実現する。

PEB 適用: ItemStack 内 container の中身を **server で `MEStorageMenu` 相当が読み取る**だけで、 grid slot は client only。

### P2. ClientReadOnlySlot の `final` 防御 (`ClientReadOnlySlot.java:46-65`)

`mayPlace = final false`, `set = final no-op`, `mayPickup = final false`, `getMaxStackSize = final 0`。 これがないと他 mod (JEI, REI, Inventory Tweaks 等) が virtual slot に書き込もうとして崩壊する。

**PEB そのままコピー可**。 ライセンス LGPL に注意。

### P3. RepoSlot.offset 固定 + repo.get() で dynamic lookup (`RepoSlot.java:36-39, 71-76`, `Repo.java:323-345`)

slot の表示位置 (`offset`) は固定。 内容物は `repo.get(offset)` が scrollOffset を加味して動的解決。 スクロール時に slot を作り直さない設計。

PEB: bookshelf 内エンチャ本リストを `List<ItemStack> view` に格納し、 `Slot.getItem()` で `view.get(offset + scrollOffset * cols)` を返す。

### P4. serial による key 識別 (`IncrementalUpdateHelper.java:64-72`)

```java
public long getOrAssignSerial(AEKey key) {
    return mapping.computeIfAbsent(key, k -> ++this.serial);
}
```

ItemStack を packet で毎回送らず、 server が割り振った long serial で参照。 click packet も `(containerId, serial, action)` だけ。

PEB: ItemStack は重複可なので serial = book の固有 ID (book 1 冊 = 1 ItemStack 想定なら) でいい。 軽量化と「server がもう知らない serial を client が送ってきた race」処理 (`MEStorageMenu.java:397-401`) もセットで実装。

### P5. 検索バーの key swallow (`AETextField.java:106-115`)

```java
return isFocused() && canConsumeInput() && event.key() != GLFW.GLFW_KEY_TAB
        && event.key() != GLFW.GLFW_KEY_ESCAPE;
```

E (inventory key) で画面が閉じる vanilla 挙動を回避する **唯一の正攻法**。 `EditBox` を継承して `keyPressed` をこの形で override。

PEB 必須。 これがないと検索中に "e" 打つたびに画面が閉じる。

### P6. broadcastChanges() で差分検出 (`MEStorageMenu.java:252-285`)

```java
previousAvailableStacks.removeAll(availableStacks);
previousAvailableStacks.removeZeros();
previousAvailableStacks.keySet().forEach(updateHelper::addChange);
```

毎 tick 全件比較。 storage が小さければこれで十分。 初回 only `fullUpdate=true`、 以後は差分。

PEB: bookshelf 内 ItemStack の数も普通 256 以下なので **同じ pattern で十分**。 KeyCounter は不要、 `List<ItemStack>` の equals 比較で OK。

### P7. slotClicked で RepoSlot 専用分岐 (`MEStorageScreen.java:548-555`)

```java
@Override
protected void slotClicked(Slot slot, int slotIdx, int mouseButton, ContainerInput clickType) {
    if (slot instanceof RepoSlot repoSlot) {
        handleGridInventoryEntryMouseClick(repoSlot.getEntry(), mouseButton, clickType);
        return;
    }
    super.slotClicked(slot, slotIdx, mouseButton, clickType);
}
```

**必須**。 これがないと vanilla の `quickMoveStack` 等が呼ばれて NPE / 異常動作。

### P8. Shift 中は view 並び替えを止める (`MEStorageScreen.java:381`, `Repo.java:425-435`)

```java
repo.setPaused(getMinecraft().hasShiftDown());
```

shift-click 連打中にスタックが動かない UX。 これがないと毎 click ごとにソートで slot 位置が変わって誤クリック頻発。

PEB 必須。 1 行で UX 激変。

### P9. Scrollbar.wantsAllMouseWheelEvents (`Scrollbar.java:267-270`)

grid 上で wheel しても scrollbar が動く。 vanilla は scrollbar 上でしか反応しない。

PEB: 256 アイテム + 6 行 grid なら必須機能。

### P10. updateScrollbar の trigger 経路 (`MEStorageScreen.java:146`, `Repo.java:200-202`)

```java
this.repo.setUpdateViewListener(this::updateScrollbar);  // screen
// repo 側
if (this.updateViewListener != null) this.updateViewListener.run();
```

repo の view 変更 (filter/sort/server 更新) → screen に通知 → scrollbar.setRange 再計算。 検索で件数が変わったら scrollbar 即更新。

---

## 8. AE2 のクセ (PEB に流用する時の注意)

### C1. AEKey → ItemStack 置換

AE2 は `AEKey` (`AEItemKey`, `AEFluidKey`) で「型」と「数量」を分離。 amount は `long`。 1 つの key で何百万個も持てる。

PEB は ItemStack 直接で OK だが、 重複チェックは **`ItemStack.equals` ではなく `ItemStack.isSameItemSameComponents` を使う** (Components API 時代の正しい比較)。 vanilla `equals` は reference 比較。

### C2. KeyCounter は捨てて良い

`KeyCounter` は AE2 の集約コンテナ。 PEB は単純 `List<ItemStack>` で十分。 差分検出は `Map<UUID, ItemStack>` (book ごとに ID 付与) or 単純 `equals` list diff。

### C3. GridInventoryEntry の `craftable` / `requestableAmount` は不要

PEB に crafting 機能はない。 `GridInventoryEntry` 相当の DTO は `(long serial, ItemStack stack)` 2 フィールドで足りる。

### C4. wrapForDisplayOrFilter() の罠

`RepoSlot.getItem()` (`RepoSlot.java:73`) は `entry.getWhat().wrapForDisplayOrFilter()` を呼ぶが、 これは AEKey → ItemStack 変換で **表示専用 ItemStack** を作る。 PEB ではそのまま元の ItemStack を返せばいい。

### C5. storage backend の interface 設計

AE2 の `MEStorage` は `getAvailableStacks()` (`KeyCounter`) + `insert/extract`。 PEB の bookshelf は ItemStackHandler (NeoForge の `IItemHandler`) を使えば良い。 menu 側は `IItemHandler` を直接持って tick ごとに slot を読み出し。

### C6. partitionList / view cell / pinnedRow は丸ごと不要

PEB に「フィルタカード」「ピン留め」はないので `Repo.addEntriesToView` の半分は削れる。 残すのは `viewMode` ではなく **search 文字列マッチだけ**で十分。

### C7. updateHelper の "client が古い serial を送ってきた" 対策は必須

```java
// MEStorageMenu.java:397-401
AEKey stack = getStackBySerial(serial);
if (stack == null) {
    // This can happen if the client sent the request after we removed the item, but before
    // the client knows about it (-> network delay).
    return;
}
```

**これを忘れると、 別 player が同時操作した時に NPE で server crash する**。 PEB でも必ず実装する。

### C8. Toggle 設定の double sync (`MEStorageMenu.java:235-242`)

sort 設定は `serverCM` と `clientCM` の 2 つ持って、 client が `ConfigValuePacket` で「変えたい値」を送り、 server が `serverCM` を更新して **逆方向に `ConfigValuePacket` を送り返す**。 これで複数 client 間でも sync。

PEB が single player or 1 client only ならこの双方向は省略可。 LAN 想定するなら丸コピー推奨。

### C9. AEBaseScreen + widgets system

`widgets.addTextField(...)` `widgets.addScrollBar(...)` は AE2 独自の widget manager (`ScreenStyle` JSON で位置決め)。 NeoForge 標準で書くなら:
- `EditBox` を `addRenderableWidget` で追加
- スクロールバーは自前実装 or `AbstractWidget` 継承

ScreenStyle JSON 機構ごと持ってくると大事になるので、 **座標ハードコード or 自前簡易レイアウト** を推奨。

### C10. License (LGPL v3)

AE2 は LGPL v3。 **`ClientReadOnlySlot` をそのまま copy するとライセンス汚染**。 PEB が MIT/Apache を維持したいなら、 同じ仕様を **自前再実装** (12 行程度なので問題なし)。 RepoSlot / Repo も同様、 **構造を学んで自前実装** が正解。

# Tom's Simple Storage 構造解析 (network storage + search + viewport)

ソース: `/tmp/mod-sources/Toms-Storage/NeoForge/src/` (NeoForge 1.21.x)
基本: コードの大半は `platform-shared/java/com/tom/storagemod/`、NeoForge 固有 (NetworkHandler 等) は `main/java/com/tom/storagemod/`

> 注意: `platform-shared/` は `src/main/` と並列に置かれた multi-loader 共有ソースルート (Architectury 風)。本ドキュメント内の絶対パスは Linux 形式 (`/tmp/mod-sources/Toms-Storage/...`) で記載。

---

## 1. パッケージ構造

```
com.tom.storagemod/
  Content.java                       # 全レジストリ集約 (block/item/menu/BE/component)
  block/                             # Block 定義
    entity/                          # BlockEntity (StorageTerminal, FilingCabinet 等)
  components/                        # DataComponent (Filter NBT 永続化)
  inventory/                         # StoredItemStack, NetworkInventory, IInventoryAccess
    filter/                          # SimpleItemFilter
  menu/                              # AbstractContainerMenu サブクラス
    slot/                            # PhantomSlot / FilterSlot / ItemFilterSlot
  network/                           # DataPacket / OpenTerminalPacket
  screen/                            # Screen 群
    widget/                          # EnumCycleButton / ToggleButton / ListWidget / TerminalSearchModeButton / IconButton
  util/                              # TerminalSyncManager, PopupMenuManager, DataSlots, LimitedContainer, FilingCabinetContainer
  client/  jei/ rei/ emi/ jade/      # クライアント補助・統合
  platform/                          # Platform 抽象 (NeoForge では Content と並んで具象)
```

主要な「terminal + scroll + search + viewport」コア:
- Menu: `menu/StorageTerminalMenu.java` (437 行)
- Screen: `screen/AbstractStorageTerminalScreen.java` (766 行)
- Sync: `util/TerminalSyncManager.java` (250 行)
- StoredItem: `inventory/StoredItemStack.java` (238 行)

別系統「ItemStack 永続 + 大容量 + viewport scroll」(PEB に最も近い形):
- Menu: `menu/FilingCabinetMenu.java` (149 行)
- Screen: `screen/FilingCabinetScreen.java` (101 行)
- Container: `util/FilingCabinetContainer.java` (103 行) + `util/LimitedContainer.java` (109 行)

---

## 2. Menu

### 2.1 AbstractFilteredMenu

ファイル: `menu/AbstractFilteredMenu.java` (101 行)

PhantomSlot / FilterSlot の「クリックでアイテムを実消費せずコピーを設定する」挙動を `clicked` で実装する基底クラス。

- `clicked` (32-62 行): `PhantomSlot` ならカーソルの copy を count=1 にセットして return。`FilterSlot` ならフィルタアイテム種別を分岐。
- `canTakeItemForPickAll` (65-67 行): Phantom/Filter は shift-click pickAll 対象外。
- `setPhantom` (69-76 行): クライアント→サーバへ `setPhantom` タグで slot index + ItemStack を送信。`NetworkHandler.sendDataToServer(tag)`。
- `receive` (79-99 行): サーバで `setPhantom` を受信、対象 slot に count=1 でセット。Filter slot にフィルタ以外を入れる扱いも区別。

### 2.2 StorageTerminalMenu (主役)

ファイル: `menu/StorageTerminalMenu.java` (437 行)。`extends RecipeBookMenu<CraftingInput, CraftingRecipe>` (38 行) — レシピブック互換のため。

**Slot 配置:**
- `addStorageSlots(int lines, int x, int y)` (131-140 行): grid `lines * 9` の "SlotStorage" (実 Slot ではない仮想スロット) を `xDisplayPosition`/`yDisplayPosition` に配置。デフォルト `addStorageSlots(5, 8, 18)` (85 行) = **5 行 × 9 列 = 45 仮想スロット**。
- `addPlayerSlots(int x, int y)` (93-119 行): プレイヤーインベントリ 3×9 (`x=8, y=120`)、ホットバー 9 (`y=178`)、オフハンド 1 (105-118 行、`x` は init で `-26` or `186` に動的設定)。
- `playerSlotsStart = slots.size() - 1` (94 行): その後追加された player slot 群が「ストレージ slot より index 大」となる目印。`quickMoveStack` で `index > playerSlotsStart` 判定に使われる (252 行)。

**仮想スロット `SlotStorage` (146-186 行):**
- 通常の `Slot` を継承しない独自クラス。`xDisplayPosition` / `yDisplayPosition` / `slotIndex` / `inventory(=BE)` / `stack(StoredItemStack)` のみ保持。
- `pullFromSlot(long max)` (164-172 行) → BE の `pullStack` を呼んで実 `ItemStack` を取得。
- `pushStack(ItemStack)` (174-181 行) → BE の `pushStack`。
- これは **vanilla の slots に登録されない**。Screen 側で別途描画/ホバー検出する。

**Search / sync の Menu side:**
- フィールド (50-54 行): `sorting, modes, searchType, beaconLvl, slotCount, freeCount, search, noSort, itemsLoaded`。
- `DataSlots` (68-73 行): sorting / modes / searchType / beaconLvl / slotCount / freeCount をサーバ→クライアント自動同期 (vanilla DataSlot 機構)。
- `broadcastChanges` (222-231 行): サーバ tick 毎に呼ばれ、`TerminalSyncManager.update(changeCount, items, player, extraSync)` で差分送信。`extraSync` は `lastSearch` が変わったときだけ "s" キーで追加。
- `receiveClientNBTPacket` (233-247 行): クライアント側で `sync.receiveUpdate` を呼んで `itemList` を再構築、`onPacket.run()` で Screen 通知。`noSort` モードでは件数だけ更新して位置を保つ。
- `receive(CompoundTag)` (306-318 行): サーバが受信。`"s"`=lastSearch sync、`"a"`=sync.receiveInteract (slot 操作)、`"c"`=ボタン状態 (sort/searchType/modes)。

**Slot interact / pull-push の本体:**
- `onInteract(StoredItemStack, SlotAction, boolean mod)` (331-419 行) ← `InteractHandler` 実装。SlotAction enum (`PULL_OR_PUSH_STACK / PULL_ONE / SPACE_CLICK / SHIFT_PULL / GET_HALF / GET_QUARTER / CRAFT`) ごとに BE の `pullStack` / `pushStack` を呼び分け。SPACE_CLICK は player inventory 全体を `quickMoveStack` でストレージへ流す (335-337 行)。

**Slot 位置オフセット (tall mode 対応):**
- `slotData` (45 行) + `SlotData record` (421-431 行) で各 vanilla Slot の **基準座標を保存**しておき、`setOffset(x, y)` (127-129 行) で一括平行移動。
- `addSlot` を override (121-125 行) して保存。`AbstractStorageTerminalScreen.init` の tall mode 切替時に `menu.setOffset(0, (rowCount - textureSlotCount) * 18)` (188 行) で player inventory を下にずらす。

### 2.3 ItemFilterMenu (filter 編集 UI)

ファイル: `menu/ItemFilterMenu.java` (119 行)

- 3×3 = 9 個の `PhantomSlot` (46-50 行、`62 + j*18, 17 + i*18`) + 通常 player slot (52-60 行)。
- DataSlot を使って `matchNBT` / `allowList` を bidirectional sync (26-37 行)。
- `clickMenuButton` (64-73 行) で vanilla の button packet 経由でフラグ更新 (`btn >> 1` でビット分離)。

### 2.4 FilingCabinetMenu (PEB に最も近い)

ファイル: `menu/FilingCabinetMenu.java` (149 行)

- 内部の `Container` (= 512 slot `SimpleContainer`) を `LimitedContainer` で 9×5=45 にラップ (153 行)。
- スロット 5×9 を `addSlot` で実 `Slot` として登録 (157-208 行)。**各 Slot の `mayPlace` をその場 inner class で override**:
  - `getMaxStackSize() = 1` (162-169 行) → maxStackSize=1 専用
  - `mayPlace`: 同じ item type 縛り (175-181 行)
  - `mayPickup` / `getItem`: `isValid()` (= 該当 slot が現在の viewport offset 範囲に存在するか) でガード (184-205 行)
  - `getNoItemIcon`: invalid 時は LOCKED_SLOT スプライト (196-201 行)
- `clickMenuButton(int row)` (256-259 行): クライアント→サーバの **row scroll 通知** に vanilla の button packet を流用。`setRow` (261-263 行) で `LimitedContainer.setStartOffset(row * 9)` (62 行 LimitedContainer.java:105) を呼ぶ。

---

## 3. Screen

### 3.1 AbstractStorageTerminalScreen (主役)

ファイル: `screen/AbstractStorageTerminalScreen.java` (766 行)
継承: `PlatformContainerScreen<T> implements IDataReceiver` (73 行)

#### 検索バー widget

- フィールド: `protected EditBox searchField` (108 行)。
- 生成 (203-210 行): `init` 内で `new EditBox(font, leftPos+82, topPos+6, 89, lineHeight, narration)`。`setMaxLength(100)`, `setBordered(false)`, `setTextColor(0xFFFFFF)`, `setValue(searchLast)` で **init 跨ぎで search 文字列を保持** (`searchLast` を init で読んで再代入、その後クリア → 209 行)。
- `addWidget(searchField)` (210 行): `addRenderableWidget` ではなく **`addWidget`**（描画は自前で `render` 内 423 行で `searchField.render(st, mouseX, mouseY, partialTicks)`）。

#### viewport (画面に出る slot 固定、scroll で中身が変わる)

`rowCount = 5` (init 191 行、tall mode のときは `(height - 30 - guiSize) / 18` で動的、186 行)。

`StorageTerminalMenu.scrollTo(float)` (193-212 行 in StorageTerminalMenu.java):
- `i = (itemListClientSorted.size() + 9 - 1) / 9 - lines` (194 行) ← 余分 row 数
- `j = (int)(scroll * i + 0.5)` で row offset
- ループ `l=0..8, k=0..lines-1` で `i1 = l + (k+j)*9` を計算、`itemListClientSorted.get(i1)` を `setSlotContents(l + k*9, ...)` で SlotStorage[k*9+l].stack に代入。

つまり **SlotStorage の x/y は固定、stack だけ差し替え**。画面上の slot 数 (5×9=45) は不変。

#### mouseScrolled

`mouseScrolled` (672-682 行 in AbstractStorageTerminalScreen.java):
```java
int i = ((this.menu).itemListClientSorted.size() + 9 - 1) / 9 - 5;
this.currentScroll = (float)(this.currentScroll - p_mouseScrolled_5_ / i);
this.currentScroll = Mth.clamp(this.currentScroll, 0.0F, 1.0F);
this.menu.scrollTo(this.currentScroll);
```
`needsScrollBars()` (537-539 行): `itemListClientSorted.size() > rowCount * 9` で判定。

#### scrollbar drag

`render` (376-415 行) の前半:
- `flag = GLFW_MOUSE_BUTTON_LEFT pressed`
- scrollbar 領域 `k = leftPos+174, l = topPos+18, i1 = k+14, j1 = l+rowCount*18` (379-382 行)
- 領域内で押下開始かつ `needsScrollBars` なら `isScrolling = true` (402-404 行)
- `isScrolling` 中は `currentScroll = (mouseY - l - 7.5F) / (j1 - l - 15.0F)` でクランプ (411-414 行)
- 412 行: scroller スプライトを `(k, l + (k-l-17)*currentScroll)` に `blitSprite` (421 行、サイズ 12×15)
- 使用スプライト: `SCROLLER_SPRITE = "container/creative_inventory/scroller"`, `SCROLLER_DISABLED_SPRITE = ".../scroller_disabled"` (74-75 行) ← vanilla クリエイティブのスクローラを流用。

#### vanilla key (E / Esc / 矢印) と検索バーの干渉回避

**ここが最重要パターン。** `keyPressed` (651-662 行):
```java
@Override
public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
    if (popup.keyPressed(pKeyCode, pScanCode, pModifiers))return true;
    if (pKeyCode == 256) {            // GLFW_KEY_ESCAPE
        this.onClose();
        return true;
    }
    if(pKeyCode == GLFW.GLFW_KEY_TAB)return super.keyPressed(pKeyCode, pScanCode, pModifiers);
    if (this.searchField.keyPressed(pKeyCode, pScanCode, pModifiers) || this.searchField.canConsumeInput()) {
        return true;
    }
    return super.keyPressed(pKeyCode, pScanCode, pModifiers);
}
```
解説:
1. popup menu があれば最優先
2. Esc (256) は無条件で close (検索バーが focus でも反応する — vanilla の `EditBox.keyPressed` だと Esc を consume してしまうため明示処理)
3. **Tab だけは super に渡す** (slot ナビゲーション系)
4. それ以外は **`searchField.keyPressed` を先に試す → consume したら return true、検索バーが入力受付状態なら return true** (= E キー等が検索バーに吸収され、`super.keyPressed` で inventory close が走らない)
5. それ以外で初めて vanilla の super (= hotbar 1-9 等)

`charTyped` (664-668 行) も同様: popup → searchField → super。

#### slot 位置 ← BG image との合わせ方

- コンストラクタ引数で `textureSlotCount, guiHeight, slotStartX, slotStartY` を受け取る (121-128 行)。`StorageTerminalScreen` の場合: `(5, 202, 7, 17)` (114 行 StorageTerminalScreen.java)。
- `init`:
  - `imageWidth = 194; imageHeight = 202` (119-120 行 StorageTerminalScreen.java)
  - `inventoryLabelY = imageHeight - 92` (201 行 AbstractStorageTerminalScreen.java)
  - `addStorageSlots(rowCount, slotStartX + 1, slotStartY + 1)` (189, 193 行) ← BG 画像のスロット枠が `(slotStartX, slotStartY)` なら **+1 で 16×16 item アイコンの左上**にぴたり収まる。
- `renderBg` (687-705 行): 通常モードは `blit(getGui(), leftPos, topPos, 0, 0, imageWidth, imageHeight)` 一発 (701 行)。tall モードでは 3 段に分割 blit して中間 row を縦に繰り返す (689-699 行)。
  - 上部 (0,0)-(imageWidth, slotStartY) を 1 回 (689 行)
  - 下部 (player inv 以降) を `guiSize` 分 (693 行)
  - スロット行 1 行ずつ繰り返し (695-698 行)

#### slot ホバー / 描画 (SlotStorage 用の独自レンダリング)

- `renderLabels` (475-481 行): `drawSlots(st, mouseX, mouseY)` を呼んで `slotIDUnderMouse` を更新。
- `drawSlots` (483-490 行): 全 SlotStorage を `drawSlot` で描画、hover した index を返す。
- `drawSlot` (492-519 行): `slot.stack != null` なら `renderItem` + `renderItemDecorations` + `drawStackSize`。マウス hover 判定 `mouseX >= guiLeft + slot.xDisplayPosition - 1 && ... < +17` (512 行)。
- `getSlotUnderMouse` (738-746 行) を override し、SlotStorage hover を **FakeSlot (Integer.MIN_VALUE 座標の dummy Slot)** に詰めて返す ← JEI/REI の "what is this slot?" 互換のため。FakeSlot は `set/remove` を no-op に (728-734 行)、`allowModification = false` (724-726 行)。

#### mouseClicked

`mouseClicked` (542-586 行):
- popup → SlotStorage hover (`slotIDUnderMouse > -1`) → SPACE 押下 → 検索バーホバー (右クリで clear)/フォーカス → super
- SlotStorage 経由は `storageSlotClick(stack, SlotAction, mod)` → `menu.sync.sendInteract(...)` (589 行) でカスタム packet。

---

## 4. Slot サブクラス

### 4.1 `menu/slot/FilterSlot.java` (30 行)

```java
mayPickup(player) = getItem().getItem() instanceof IItemFilter
getMaxStackSize() = 1
mayPlace(stack)   = true
```
= 「フィルタアイテムだけが pickup 可能」「常時 place 可能だが max 1」「実体差し替えは AbstractFilteredMenu.clicked が制御」。

### 4.2 `menu/slot/ItemFilterSlot.java` (24 行)

`FilterSlot` とほぼ同じ。`mayPlace` を **override しない** (default true)。差異は意味的なマーカー (slot.instanceof チェック分岐) のため。

### 4.3 `menu/slot/PhantomSlot.java` (28 行)

```java
mayPickup(player) = false             // 取り出し不可
getMaxStackSize() = 1
mayPlace(stack)   = true              // 「ゴースト」アイテムとして配置可能
```
実際の "1 個コピーをセット" は `AbstractFilteredMenu.clicked` が `slot.set(c.copy().setCount(1))` で行う (40-59 行 AbstractFilteredMenu.java)。

### 4.4 FilingCabinet の inline Slot (149 in FilingCabinetMenu)

ファイル: `menu/FilingCabinetMenu.java` 行 159-208 (anonymous class)

- `getMaxStackSize() = 1` (162-169 行) — `SimpleContainer.getMaxStackSize` のデフォルト 64 を上書き
- `mayPlace`: container の他 slot に何か入っていれば `is.getItem() == stack.getItem()` (= 単一 item type 固定) (175-181 行)
- `mayPickup` / `getItem`: viewport offset 外なら拒否 (= `isValid()`, 203-205 行)
- `getNoItemIcon`: invalid 時 LOCKED_SLOT (196-201 行) — viewport 外を視覚的にロック表現

---

## 5. Filter / Sort 実装

### 5.1 検索: `AbstractStorageTerminalScreen.updateSearch` (271-363 行)

書式:
- `|` で **OR 結合** (276 行 `split("\\|")`)
- 各 OR 部分内は空白区切りで **AND 結合** (280 行 `split(" ")`)
- 各語の prefix:
  - `@xxx` → modid (namespace) に `xxx` を含む (284-286 行)
  - `#xxx` → tag location 文字列が `xxx` を含む (287-295 行, tagCache 経由)
  - `$xxx` → DataComponentPatch を JSON 化した文字列に regex match (296-306 行, componentCache 経由)
  - prefix なし → 表示名 or tooltip 行に regex match (307-325 行, tooltipCache 経由)
- 各語は **`Pattern.compile(s, CASE_INSENSITIVE)` を試し、失敗したら `Pattern.quote(s)` で再試行** (`buildPattern` 259-269 行) ← 不正 regex をリテラル match にフォールバック

キャッシュ (78-96 行): Guava `LoadingCache`、`expireAfterAccess(5, SECONDS)`。tooltip/component/tag を毎フレーム再計算しない。

### 5.2 ソート軸 enum: `StoredItemStack.SortingTypes` (151-165 行 in StoredItemStack.java)

```java
enum SortingTypes {
    AMOUNT(ComparatorAmount::new),    // type=0
    NAME(ComparatorName::new),        // type=1
    BY_MOD(ComparatorModName::new),   // type=2
}
```
各 Comparator は `boolean reversed` を持ち `setReversed` で反転可能 (`IStoredItemStackComparator` interface, 145-149 行)。

`AbstractStorageTerminalScreen.updateSearch` 末尾 (341 行): `Collections.sort(getMenu().itemListClientSorted, menu.noSort ? sortComp : comparator)`。`noSort` は shift 押し中の「順序固定 (= 取り出し UX 維持)」モード (384-400 行)。

### 5.3 key event での切替

専用の sort/searchType ホットキーは無い。**全部 button widget** (`buttonSortingType` / `buttonDirection` / `buttonSearchType` / `buttonCtrlMode` / `buttonGhostMode` / `buttonTallMode`、`init` 211-253 行)。

設定 bit-pack (`sendUpdate` 160-172 行 / `writeModes` 174-179 行):
- `sort` int: `comparator.type() & 0xFF` | `reversed ? 0x100` | `ghostItems ? 0 : 0x200`
- `modes` int: `controllMode & 0xF` | `tallMode ? 0x10`
- `searchType` int: bit0=auto search focus, bit1=keep last search, bit2=sync recipe book, bit3=smart search off

`searchType` の bit0 効果 = init/onPacket 時に `searchField.setFocused(true)` を強制 (143-145 行) ← **terminal 開いたら検索バーに自動 focus**。

---

## 6. Network packets

### 6.1 一覧

`network/DataPacket.java` (28 行):
```java
record DataPacket(CompoundTag tag) implements CustomPacketPayload {
    Type<DataPacket> ID = new Type<>(rl("data"));
    StreamCodec STREAM_CODEC = codec(DataPacket::write, DataPacket::new);
    // write: pb.writeNbt(tag); read: pb.readNbt(unlimitedHeap)
}
```
**唯一のメインペイロード**。CompoundTag 1 個を双方向に流すだけ。中身のキーで意味分岐 (`"s"`, `"a"`, `"c"`, `"d"`, `"l"`, `"setPhantom"` 等)。

`network/OpenTerminalPacket.java` (49 行): クライアント→サーバ、空 payload。Wireless Terminal を開く合図。

### 6.2 NetworkHandler (NeoForge 側)

ファイル: `main/java/com/tom/storagemod/network/NetworkHandler.java` (66 行)

- `onPayloadRegister` (23-27 行): `registrar.playBidirectional(DataPacket.ID, ..., new DirectionalPayloadHandler<>(handleDataClient, handleDataServer))`、`registrar.playToServer(OpenTerminalPacket.ID, ..., handleTermServer)`。
- `handleDataServer` (29-36 行): `player.containerMenu instanceof IDataReceiver` ならその `receive(tag)` を呼ぶ。
- `handleDataClient` (38-44 行): `Minecraft.getInstance().screen instanceof IDataReceiver` ならその `receive(tag)`。
- `sendDataToServer(CompoundTag)` (55-57 行), `sendTo(ServerPlayer, CompoundTag)` (59-61 行)。

`IDataReceiver` interface (util/IDataReceiver.java) は `void receive(CompoundTag tag)` だけ。Menu と Screen 双方が実装。

### 6.3 Terminal を開いた時の sync

`StorageTerminalMenu.broadcastChanges` (222-231 行) が tick 毎呼ばれ、`TerminalSyncManager.update` に丸投げ。

`TerminalSyncManager.update(changeID, items, player, extraSync)` (104-165 行 in util/TerminalSyncManager.java):
- `changeID != lastChangeID` のときだけ差分計算 (BE 側で物が変わったときだけ `changeCount++`)。
- `toWrite` リストに「新規/数量変化したスタック」+「消えたスタック (count=0 の StoredItemStack)」を入れる。
- カスタムバイトバッファ `RegistryFriendlyByteBuf workBuf` (容量 `MAX_PACKET_SIZE = 64000`、38 行) に `write(buf, stack)` を順に詰める:
  - 1 byte flags (`bit0=count==0, bit1=hasComponents, bit2=既出ID参照`)
  - VarInt: 内部 ID (`idMap` で StoredItemStack → small int に圧縮、62-66 行)
  - 既出でないときだけ VarInt(itemId) + 必要なら components patch
  - count != 0 のときだけ VarLong(count)
- バッファ容量超過したら **一旦そこまでで送って分割** (135-147 行)。`writeMiniStack` (72-81 行) は NBT が巨大すぎる単一 stack の超過対策 (count + LORE「nbt_overflow」のミニ表現)。

### 6.4 内容物変化時の delta

クライアント側 `receiveUpdate` (167-185 行):
- `tag.contains("d")` のときだけ delta、`l` で件数読み取り
- 各 entry を `read(buf)` (83-102 行) で復号、`flags & 4` (= 既出 ID 参照) なら `idMap2.get(id)` から ItemStack を引く
- `count == 0` なら `itemList.remove(s)`、それ以外は `itemList.put(s, s)` ← 上書き

`sendInteract(StoredItemStack, SlotAction, mod)` (187-199 行): クライアント→サーバ。`"a"` キーに `[flags][VarInt id][VarLong count]` または `[flags|2]` (null stack) + `[enum SlotAction]`。

### 6.5 ボタン状態 sync

`StorageTerminalMenu.receive` (306-318 行):
```java
if (message.contains("c")) {
    CompoundTag d = message.getCompound("c");
    te.setSorting(d.getInt("s"));
    te.setSearchType(d.getInt("st"));
    te.setModes(d.getInt("m"));
}
```
クライアントの `sendUpdate` (160-172 行) が送る `"c"` (`s/st/m`) を BE に書き戻す。逆方向はサーバの DataSlot 自動 sync (`addDataSlot(DataSlots.create(...))` 68-73 行 StorageTerminalMenu.java) で。

---

## 7. PEB (ItemStack 内 container + viewport) に効くパターン 5+

### Pattern A: ItemStack を `Container` で wrap + viewport を `LimitedContainer` で切る

目的: 大容量 (256+) 内部 storage を、9×N の vanilla Slot UI として viewport 単位で見せる。

参照: `util/FilingCabinetContainer.java:14-103` (`SimpleContainer` を内包し getMaxStackSize=1 等を上書き) + `util/LimitedContainer.java:11-108` (delegate Container + `sizeLimit` + `startOffset`、`getItem(i) = delegate.getItem(startOffset + i)` `setStartOffset(int)`)。

PEB への適用: `ItemStack.get(DataComponents...)` から `SimpleContainer(256)` を復元する `Container` 実装を書き、`LimitedContainer(inner, 9*N)` で viewport を切る。scroll の row が変わったら `setStartOffset(row * 9)`。

### Pattern B: vanilla button packet を row scroll の sync に流用

目的: server に「scrollbar を row=X に動かした」と伝えるための独自パケットを作らず、vanilla の `handleInventoryButtonClick` 機構を流用する。

参照: `menu/FilingCabinetMenu.java:256-263`
```java
public boolean clickMenuButton(Player p, int row) {
    setRow(row);
    return true;
}
public void setRow(int row) {
    container.setStartOffset(row * 9);
}
```
クライアント側: `screen/FilingCabinetScreen.java:85-88`
```java
private void scroll(int id) {
    getMenu().setRow(id);
    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
}
```
**メリット**: PayloadRegistrar も CustomPacketPayload も書かなくていい。scroll row が int 1 個で表せるなら最小コストで sync 完結。

### Pattern C: viewport 外 slot を「ロック」表示

目的: bookshelf の物理スロット数が viewport row 数より少ないとき、有効範囲外 slot を見た目で区別する。

参照: `menu/FilingCabinetMenu.java:195-205`
```java
@Override public boolean mayPickup(Player p) { return isValid(); }
@Override public ItemStack getItem() { return isValid() ? super.getItem() : ItemStack.EMPTY; }
@Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
    return isValid() ? null : Pair.of(InventoryMenu.BLOCK_ATLAS, LOCKED_SLOT);
}
private boolean isValid() { return container.getContainerSize() > getContainerSlot(); }
```
`LOCKED_SLOT` (139 行) は `"item/locked_slot"` スプライト (vanilla の鎖アイコン風)。

### Pattern D: 検索バーと vanilla キー (E / hotbar / Tab) の干渉回避

目的: 検索バーに文字を打ち込んでも、`E` で inventory が閉じたり `1-9` でホットバー切替が暴発しない。

参照: `screen/AbstractStorageTerminalScreen.java:651-668`
```java
public boolean keyPressed(int kc, int sc, int mods) {
    if (popup.keyPressed(kc, sc, mods)) return true;          // popup 優先
    if (kc == 256) { this.onClose(); return true; }           // Esc は無条件 close
    if (kc == GLFW.GLFW_KEY_TAB) return super.keyPressed(...); // Tab は super 直行
    if (searchField.keyPressed(kc, sc, mods) || searchField.canConsumeInput())
        return true;                                          // EditBox 優先
    return super.keyPressed(kc, sc, mods);                    // それ以外で vanilla
}
public boolean charTyped(char c, int mods) {
    if (popup.charTyped(c, mods)) return true;
    if (searchField.charTyped(c, mods)) return true;
    return super.charTyped(c, mods);
}
```
**ポイント**: 単に `searchField.keyPressed` だけでなく **`searchField.canConsumeInput()` も or 条件**にしている。focus 状態だが特定キーを consume しない (例: 矢印 left/right at start) ときも vanilla に流さないため。

### Pattern E: SlotStorage = vanilla Slot を経由しない仮想スロット (vanilla の slot count 制約を回避)

目的: 256 slot 全部を `addSlot` で登録すると AbstractContainerMenu の slot 配列が巨大化し、`broadcastChanges` のコストも厳しい。表示する 5×9 だけが本物の Slot で、残りは描画上の仮想スロットにする。

参照: `menu/StorageTerminalMenu.java:146-186` (SlotStorage record)、`screen/AbstractStorageTerminalScreen.java:483-519` (`drawSlot` 自前描画) + 738-746 (`getSlotUnderMouse` の FakeSlot ハック)。

**注意**: PEB がストレージ全体を vanilla Slot で持つなら不要。逆に「128 slot は全部 vanilla Slot で持ちたい」なら Pattern A+B+C の組合せで足りる。SlotStorage 方式は **検索でフィルタした結果を表示** が前提のとき価値が出る。

### Pattern F: scrollbar drag = `currentScroll` (0.0-1.0) を render 中に更新

目的: scrollbar をマウスで掴んで上下に動かす UI。

参照: `screen/AbstractStorageTerminalScreen.java:376-415` および `screen/FilingCabinetScreen.java:34-63`
```java
boolean flag = GLFW.glfwGetMouseButton(..., GLFW_MOUSE_BUTTON_LEFT) != GLFW_RELEASE;
int k = leftPos + 174, l = topPos + 18, i1 = k + 14, j1 = l + rowCount * 18;
if (!wasClicking && flag && mouseX >= k && mouseY >= l && mouseX < i1 && mouseY < j1)
    isScrolling = needsScrollBars();
if (!flag) isScrolling = false;
wasClicking = flag;
if (isScrolling) {
    currentScroll = (mouseY - l - 7.5F) / (j1 - l - 15.0F);
    currentScroll = Mth.clamp(currentScroll, 0.0F, 1.0F);
}
// 描画: blitSprite(SCROLLER_SPRITE, k, l + (int)((j1 - l - 17) * currentScroll), 12, 15);
```
`SCROLLER_SPRITE = "container/creative_inventory/scroller"` (vanilla 流用、74-75 行)。`mouseScrolled` (672-682 行) はホイール対応で同じ `currentScroll` を更新。

### Pattern G: 検索文字列を BE 側に保存 + 再 open 時に復元

目的: terminal を開閉しても検索文字列が消えない (UX 向上)。

参照:
- BE: `block/entity/StorageTerminalBlockEntity.java:229-235` (`lastSearch` フィールド + getter/setter)
- Menu broadcast 時に diff があれば送信: `menu/StorageTerminalMenu.java:226-229` (`!te.getLastSearch().equals(search) ? tag -> { search = te.getLastSearch(); tag.putString("s", search); } : null`)
- Menu receive: `:244-245` (`if(message.contains("s")) search = message.getString("s");`)
- Server 受信: `:308-310` (`te.setLastSearch(message.getString("s"));`)
- Screen 反映: `:147-151` (`if(!loadedSearch && menu.search != null) { loadedSearch = true; if((searchType & 2) > 0) searchField.setValue(menu.search); }`) ← **searchType bit1 (keep) が立っていれば**復元

PEB に活かす場合は `keep` フラグ ON 固定でいい。ItemStack の DataComponent に文字列を直接書く形でも可。

### Pattern H: bit-pack で sort/mode/searchType を 1 int に圧縮し DataSlot で同期

参照: `menu/StorageTerminalMenu.java:68-73` (DataSlot 登録)、`screen/AbstractStorageTerminalScreen.java:160-179` (`sendUpdate` / `writeModes` でビット組立)、`block/entity/StorageTerminalBlockEntity.java:208-224` (旧形式から新ビット配置への移行コードあり ← **migration の参考**)。

DataSlot は vanilla の機構なので追加 packet 不要。bit-pack の境界 (`0xFF` / `0x100` / `0x200` / `0xF` / `0x10`) を **マスク + シフトで extract** するパターンが Screen 側 (130-138 行) と BE 旧形式 load (211-220 行) の両方にある。

---

## 8. 罠 / 注意点

1. **`SlotStorage` は AbstractContainerMenu の `slots` に登録されない仮想 slot。**
   `slots.size()` には現れず、`quickMoveStack(player, index)` の index にも入らない (`playerSlotsStart` 判定 252 行)。描画・ホバー・クリックを **全部自前で実装**しないといけない (483 行以降、542 行以降)。JEI/REI 互換のため `getSlotUnderMouse` を override して **FakeSlot を返すハック** (738-746 行) が必須。
   → PEB が「全 slot を vanilla Slot で持つ」設計なら不採用。

2. **`broadcastChanges` を毎 tick オーバーライドで上書きしている (222-231 行)。**
   `super.broadcastChanges()` を最後に呼んでいるが、独自 sync を tick 毎走らせるコストがあるため `changeCount` で gating している (TerminalSyncManager.update 105 行)。PEB でも何らかの **change ID + dirty flag** 機構を入れないと毎 tick 全送信になる。

3. **検索バーの `init` 跨ぎでの値保持は `searchLast` フィールド経由のトリック (208-209 行)。**
   ```java
   this.searchField.setValue(searchLast);
   searchLast = "";
   ```
   `init` 関数は画面リサイズ・tall mode 切替時に再実行される。新しい EditBox を作る前に古い値を `searchLast` に退避するロジック (= ここでは省略されていて、別の場所で `searchLast = searchField.getValue()` する必要があるはず) と組で使う。**`init` で widget を作り直す Screen では検索文字列が消えるバグが起きやすい** → 退避フィールドが必須。

4. **`SCROLLER_SPRITE` の高さ 17px 想定 (`(k - j - 17) * currentScroll`、421 行)** に対し、`SCROLLER_DISABLED_SPRITE` は 15px 表示 (`blitSprite(..., 12, 15)`)。意図的に scrollbar 領域 height - 17 で割っているが、表示サイズと計算サイズが揃ってないので **新しい scrollbar スプライトを作るときは要注意**。

5. **検索バーの位置を移動した場合、`mouseClicked` の hover 判定 (575 行) もハードコード `89, lineHeight` を見直す必要がある。**
   ```java
   if (isHovering(searchField.getX() - leftPos, searchField.getY() - topPos, 89, this.getFont().lineHeight, mouseX, mouseY))
   ```
   widget の width/height を取れる API があるなら `searchField.getWidth()` / `searchField.getHeight()` で吸収すべき。

6. **`PhantomSlot.mayPlace = true` でも `clicked` の上書きが必須** (AbstractFilteredMenu 40-59 行)。`super.clicked` を呼ぶと **本物のスタックが消費される**。`menu.clicked(slot, drag, click, player)` 経由のパスをすべて潰すこと。

7. **`SimpleContainer.getMaxStackSize() = 64` がデフォルト。** FilingCabinet は inline Slot で `getMaxStackSize() = 1` + Container 側 `getMaxStackSize() = 1` (82 行 FilingCabinetContainer.java) の **両方上書き**している。片方だけだとアイテムが 1 超えて入る経路 (hopper や `Container.add`) が残る。

8. **`tooltipCache` / `componentCache` / `tagCache` の expireAfterAccess=5s** (78-96 行) は Guava。`StoredItemStack` の `equals` が `count` を含まない (190-196 行) ので、count が変わっても tooltip キャッシュは引ける設計。**PEB で同じパターン使うなら、tooltip キャッシュキーの equals を慎重に**。

9. **`mouseScrolled` の divisor**:
   ```java
   int i = (itemListClientSorted.size() + 9 - 1) / 9 - 5;
   ```
   ← この `5` は **rowCount のハードコード**。tall mode で rowCount が変わるが mouseScrolled では追従していない (676 行)。bug 寄り。**PEB は `getRowCount()` を使うこと**。

10. **`OpenTerminalPacket` は `playToServer` で空 payload**。Wireless terminal の右クリック合図に使われるが、サーバ側で `player.containerMenu` が既に開いていてもガードしていない (NetworkHandler 46-53 行)。spam されると毎回 `WirelessTerminal.open` が走る。**PEB が同様のリモート open を実装するなら開閉状態チェックを足すこと**。

11. **`DataPacket(CompoundTag)` で `NbtAccounter.unlimitedHeap()` を使っている** (17 行 DataPacket.java)。攻撃者が無制限 NBT を送れる。vanilla の `NbtAccounter.create(2 * 1024 * 1024L)` 等の制限を入れる方が安全。Tom's Storage は terminal 同期で 64KB chunked 送信するため意図的に unlimited にしているが、PEB は容量が読めるならサイズ制限を付けるべき。

12. **NeoForge 1.21.1 の `RecipeBookMenu` のジェネリクス変更** に注意。`StorageTerminalMenu extends RecipeBookMenu<CraftingInput, CraftingRecipe>` (38 行 StorageTerminalMenu.java) は 1.21.x 仕様。1.20.x からの移植時に `fillCraftSlotsStackedContents` / `clearCraftingContent` / `getResultSlotIndex` / `getGridWidth` / `getGridHeight` / `getSize` / `shouldMoveToInventory` / `recipeMatches` の override シグネチャが変わる (274-328, 433-436 行)。PEB がレシピブック互換不要なら **`AbstractContainerMenu` を継承して回避**するのが楽。

# Functional Storage 構造解析 (drawer-style)

ソース: `/tmp/mod-sources/FunctionalStorage/src/main/java/com/buuz135/functionalstorage/`
基準: NeoForge 1.21.x (`net.neoforged.neoforge.items.IItemHandler`, DataComponents 採用)
ヘルパ依存: HrznStudio Titanium (`com.hrznstudio.titanium.*`) — BlockEntity/Screen/InventoryComponent/GuiAddon の薄いラッパ。PEB がそのまま採用するかは別判断。

## 1. パッケージ構造

```
functionalstorage/
├── FunctionalStorage.java                 # mod entry, DrawerType enum を内包 (L396-441)
├── block/
│   ├── DrawerBlock.java                   # 木 drawer 本体 + 内側 DrawerItem (ItemStack capability)
│   ├── ArmoryCabinetBlock.java            # 装備専用 4096 slot block
│   ├── DrawerControllerBlock.java         # 隣接 drawer をまとめる controller
│   ├── EnderDrawerBlock.java              # frequency 共有 drawer
│   ├── FluidDrawerBlock.java              # 流体版
│   ├── (Compacting/Framed/Simple)*.java   # 派生
│   ├── config/FunctionalStorageConfig.java  # ARMORY_CABINET_SIZE=4096 等の上限値
│   └── tile/
│       ├── ControllableDrawerTile.java    # upgrade slot, lock, options
│       ├── ItemControllableDrawerTile.java# item 系の onSlotActivated / onClicked
│       ├── DrawerTile.java                # 通常 drawer の Tile
│       ├── ArmoryCabinetTile.java
│       ├── DrawerProperties.java          # record(baseSize, upgradeComponent)
│       └── ...
├── inventory/
│   ├── BigInventoryHandler.java           # block 用 drawer storage (IItemHandler 実装核)
│   ├── ArmoryCabinetInventoryHandler.java # 1-slot-1-item, 4096 slot
│   ├── EnderInventoryHandler.java         # BigInventoryHandler 派生 (frequency 共有)
│   ├── ControllerInventoryHandler.java    # 接続 drawer 群を仮想単一 handler に
│   ├── CompactingInventoryHandler.java
│   ├── ILockable.java
│   └── item/
│       ├── DrawerStackItemHandler.java    # ★ItemStack 内 container を IItemHandler で公開
│       └── CompactingStackItemHandler.java
├── client/
│   ├── DrawerRenderer.java                # BER: world 内の item + count 数字
│   ├── BaseDrawerRenderer.java
│   ├── (Compacting/Framed/Fluid/Ender)*Renderer.java
│   ├── ControllerRenderer.java
│   ├── FunctionalStorageClientConfig.java # DRAWER_RENDER_THICKNESS 等
│   ├── ClientSetup.java                   # screen 上のクリックイベント hook
│   ├── gui/
│   │   ├── DrawerInfoGuiAddon.java        # ★Container Screen 内に描く擬似 slot 群 + count
│   │   └── FluidDrawerInfoGuiAddon.java
│   ├── item/DrawerISTER.java              # ItemStack 単体プレビュ (BEWLR)
│   ├── model/ ・ loader/                  # framed block の dynamic baked model
├── item/
│   ├── FSAttachments.java                 # ★全 DataComponentType の集約
│   ├── FunctionalUpgradeItem.java         # behavior component を持つ upgrade item
│   ├── StorageUpgradeItem.java / UpgradeItem.java
│   ├── ConfigurationToolItem.java         # ConfigurationAction enum (lock/numbers/render/...)
│   ├── LinkingToolItem.java               # controller との連結
│   └── component/
│       ├── SizeProvider.java              # multiplicative factor record
│       └── FunctionalUpgradeBehavior.java # tick 時挙動 (collector/puller/pusher 等)
├── network/  recipe/  data/  world/  compat/  util/  fluid/
```

## 2. Drawer block + BlockEntity

### 内容物保持戦略 (2系統あることに注意)

**(a) block 設置中** — `BigInventoryHandler` を Tile が持つ
`DrawerTile.java:32-62` で匿名サブクラスとして new し、`@Save BigInventoryHandler handler;` で Titanium の `@Save` annotation により tile NBT に永続化。`BigInventoryHandler` は `implements IItemHandler, INBTSerializable<CompoundTag>, ILockable` (`BigInventoryHandler.java:14`)。内部は `List<BigStack>` (`BigInventoryHandler.java:21`)。`BigStack` は `(ItemStack stack, ItemStack slotStack, int amount)` の 3 メンバ record-like class (`BigInventoryHandler.java:170-198`) で、`amount` は `Integer` 範囲。

**(b) drop されて ItemStack 化** — `DrawerStackItemHandler` を `BlockItem` の capability として登録
`DrawerBlock.DrawerItem.initCapabilities` (`DrawerBlock.java:269-272`) :
```java
public IItemHandler initCapabilities(ItemStack stack) {
    return new DrawerStackItemHandler(stack, this.drawerBlock.getType());
}
```
内側 ItemStack の data component `FSAttachments.TILE` (`CompoundTag` 型, `FSAttachments.java:48`) に `BlockEntity.saveWithoutMetadata` した tag を保存し、`new DrawerStackItemHandler(stack, type)` の constructor で復元する (`DrawerStackItemHandler.java:30-62`)。

なお block→item へ NBT を入れるのは `DrawerBlock` ではなく親 `Drawer.java` 側の loot table / getDrops 系 (Armory は `ArmoryCabinetBlock.java:60-71` に `stack.set(FSAttachments.TILE, drawerTile.saveWithoutMetadata(...))` の例)。再配置時は `setPlacedBy` で `et.loadAdditional(stack.get(FSAttachments.TILE), ...)` (`ArmoryCabinetBlock.java:80-87`)。

### single-item slot pattern (drawer 哲学)

`BigInventoryHandler.insertItem` (`BigInventoryHandler.java:46-63`):
- `isValid(slot, stack)` = 同 slot の保存 stack と `ItemStack.isSameItemSameComponents(fl, stack)` (`BigInventoryHandler.java:114`)
- 空 slot に初挿入時 `bigStack.setStack(stack.copyWithCount(stack.getMaxStackSize()))` で **`maxStackSize` のテンプレ stack を保存**、`amount` を別に持つ
- 以後同 item のみ追加可能

### getSlotLimit (容量計算)

`BigInventoryHandler.getSlotLimit` (`BigInventoryHandler.java:91-101`):
```java
double stackSize = 1;
if (!getStoredStacks().get(slot).getStack().isEmpty()) {
    stackSize = getStoredStacks().get(slot).getStack().getMaxStackSize() / 64D;
}
return (int) Math.floor(getTotalAmount() * stackSize);
```
`getTotalAmount() = 64d * getMultiplier()` (`BigInventoryHandler.java:158-160`)。`getMultiplier()` は `DrawerTile.getStorageMultiplier()` 経由で storage upgrade slot から `SizeProvider.calculateAsFactor` で算出 (`ControllableDrawerTile.java:259`)。

つまり **slot 上限は「maxStackSize ベースで 64 基準を換算」** という設計。enchanted_book (max 1) なら maxStackSize/64=1/64 なので、純粋にこの式を使うと容量が極小になる罠。

## 3. Custom Slot — `DrawerStackItemHandler` (ItemStack 内 container の核心実装)

PEB のユースケース「ItemStack 内に大量 enchanted_book を抱える」と直結するクラス。

### クラス宣言と保持

```java
public class DrawerStackItemHandler implements IItemHandler, INBTSerializable<CompoundTag> {
    private List<BigInventoryHandler.BigStack> storedStacks;
    private ItemStack stack;             // 自分自身の親 ItemStack 参照
    private FunctionalStorage.DrawerType type;
    private float size;
    private boolean isVoid;
    private boolean isCreative;
```
(`DrawerStackItemHandler.java:25-32`)

constructor (`DrawerStackItemHandler.java:34-62`) の流れ:
1. `drawerType.getSlots()` 個の `BigStack(EMPTY, 0)` で `storedStacks` 初期化
2. `stack.has(FSAttachments.TILE)` なら中の `CompoundTag` から `handler` を deserialize
3. `storageUpgrades` を一時的に new ItemStackHandler で deserialize → `SizeProvider.calculateAsFactor(...)` で `size` を算出
4. `utilityUpgrades` から `VOID_UPGRADE` の有無で `isVoid` 判定

### override の核心

**`getStackInSlot(slot)`** (`DrawerStackItemHandler.java:84-92`):
```java
BigStack bigStack = this.storedStacks.get(slot);
if (isCreative) return bigStack.getStack().copyWithCount(Integer.MAX_VALUE);
ItemStack copied = bigStack.getStack().copy();
copied.setCount(bigStack.getAmount());  // 64 を超える amount をそのまま count にセット
return copied;
```
- **`amount` を `ItemStack.setCount` に直接渡す** — vanilla の 64 制約を実質無視する流儀。read-only な representation なのでOK。

**`insertItem`** (`DrawerStackItemHandler.java:95-108`):
```java
if (isValid(slot, stack)) {
    BigStack bigStack = this.storedStacks.get(slot);
    int inserted = Math.min(getSlotLimit(slot) - bigStack.getAmount(), stack.getCount());
    if (!simulate) {
        bigStack.setStack(stack);                                            // テンプレ更新
        bigStack.setAmount(Math.min(bigStack.getAmount() + inserted, getSlotLimit(slot)));
        onChange();
    }
    if (inserted == stack.getCount() || isVoid()) return ItemStack.EMPTY;
    return stack.copyWithCount(stack.getCount() - inserted);
}
return stack;
```

**`extractItem`** (`DrawerStackItemHandler.java:128-152`):
- `amount >= bigStack.amount` なら **全部抜く** が、抜いた `out.setCount(newAmount)` で `newAmount` を 64 超えで返している (上限調整なし)。これは vanilla の getMaxStackSize を超える ItemStack を返す **怪しい挙動**。PEB ではここを `Math.min(amount, stack.getMaxStackSize())` で抑える方が安全 (`BigInventoryHandler.extractItem` 側 (`BigInventoryHandler.java:71`) では `amount = Math.min(amount, bigStack.getStack().getMaxStackSize())` でちゃんと clamp している)。

**`getSlotLimit(slot)`** (`DrawerStackItemHandler.java:171-180`):
```java
if (isCreative) return Integer.MAX_VALUE;
var stored = getStackInSlot(slot);
long maxSize = Item.DEFAULT_MAX_STACK_SIZE;  // 64
if (!stored.isEmpty()) maxSize = stored.getMaxStackSize();
return (int) Math.min(Integer.MAX_VALUE, Math.floor(size * maxSize));
```
`size` = drawerType.getSlotAmount() × upgrade factor。drawer X_1 は slotAmount=32 (`FunctionalStorage.java:397`)。

**`isItemValid`** (`DrawerStackItemHandler.java:183`):
```java
return !stack.isEmpty();
```
緩い。**実 valid 判定は `isValid` (private)** で同 item 同 components チェック (`DrawerStackItemHandler.java:122-129`)。

**`mayPlace` には触れていない** — IItemHandler 仕様なので `Slot` 側で `mayPlace = handler.isItemValid()` を呼ぶのが普通 (`SlotItemHandler` 標準動作)。PEB で enchanted_book 限定にしたければ `isItemValid` 内で `stack.is(Items.ENCHANTED_BOOK)` チェックを追加。

### ghost item display + actual storage の分離 (BigStack の二重持ち)

`BigInventoryHandler.BigStack` (`BigInventoryHandler.java:170-198`) :
```java
private ItemStack stack;       // テンプレ (count=maxStackSize の 1 つ)
private ItemStack slotStack;   // ghost 表示用 (count=amount)
private int amount;            // 真の保持量 (Integer 範囲)

public void setStack(ItemStack stack) {
    this.stack = stack.copy();
    this.slotStack = stack.copyWithCount(amount);
}
public void setAmount(int amount) {
    this.amount = amount;
    this.slotStack.setCount(amount);
}
```
- `stack` は **同種判定とテンプレ取得のための「種類見本」**。setStack で受けたものを `copy()` する (count は無視され、別管理)。
- `slotStack` は **GUI / capability 表示用の "ghost"** で、`amount` に合わせて count が常に追従。
- `getStackInSlot` で `slotStack` (block 版) or `bigStack.getStack().copy()` + `setCount(amount)` (item 版) を返す。
- locked 時に空にしても `stack` (種類) は残し `amount=0` にすることで「空表示だが種類保持」を実現 (`BigInventoryHandler.java:80` `if (!isLocked()) bigStack.setStack(ItemStack.EMPTY); bigStack.setAmount(0);`)。

これが **drawer 流の ghost item パターン**。PEB の「enchanted_book 256 slot」では各 slot に 1 種類の本だけ受け付けるなら同パターンが使えるが、enchanted_book は **全て maxStackSize=1 かつ ItemStack.isSameItemSameComponents で別物** なので、count overlay する意味は薄い。本のスタッキングを諦めて `ArmoryCabinetInventoryHandler` 流 (1 slot = 1 item, `getSlotLimit=1`, 4096 slot) の方が直結する。

### 既知のバグ／怪しい点 (DrawerStackItemHandler のみ)

`DrawerStackItemHandler.java:118` `private boolean isVoid() { return true; }` — 常に true を返す壊れた実装。本来 field の `this.isVoid` を返すべき (constructor で計算済み)。
`DrawerStackItemHandler.java:155` `public boolean isLocked() { return true; }` — 同様に常に true。
これらの結果、ItemStack 内 drawer は **常に void + locked 扱い** になり、`extractItem` で全部抜いても `bigStack.setStack(ItemStack.EMPTY)` が走らない (種類が残る) し、`insertItem` で `isVoid()=true` のため余分を破棄する。**PEB 側でコピペする時は必ずフィールド参照に直す。**

## 4. Menu / Screen

### 結論: Functional Storage は標準的な `AbstractContainerMenu + Slot` 構造を **drawer については持たない**

Drawer のメイン操作はすべて **block の onSlotActivated / onClicked** で完結し、GUI は upgrade slot の管理用のみ。drawer 本体の slot は **`DrawerInfoGuiAddon` という擬似 slot 表示** (背景 + `renderItem` + tooltip + count) で、Slot オブジェクトではない (`DrawerInfoGuiAddon.java` 全体)。

つまり「shift-click で大量 transfer」は **drawer GUI 内では発生しない**。代わりに以下:

### in-world での大量 transfer

`ItemControllableDrawerTile.onSlotActivated` (`ItemControllableDrawerTile.java:55-77`):
```java
if (slot != -1 && isServer()) {
    if (!stack.isEmpty() && getStorage().insertItem(slot, stack, true).getCount() != stack.getCount()) {
        playerIn.setItemInHand(hand, getStorage().insertItem(slot, stack, false));  // 1回挿入
        return InteractionResult.SUCCESS;
    } else if (System.currentTimeMillis() - INTERACTION_LOGGER.get(playerIn.getUUID()) < 300) {
        // ★double-click 300ms 以内 → インベントリ全 slot を走査して同種を吸い込む
        for (ItemStack itemStack : playerIn.getInventory().items) {
            if (!itemStack.isEmpty() && getStorage().insertItem(slot, itemStack, true).getCount() != itemStack.getCount()) {
                itemStack.setCount(getStorage().insertItem(slot, itemStack.copy(), false).getCount());
            }
        }
    }
    INTERACTION_LOGGER.put(playerIn.getUUID(), System.currentTimeMillis());
}
```
**right-click 1 回 = 手持ち 1 stack 投入。300ms 以内に 2 度目 = インベントリ全体から同種を回収** (drawer 流 "double-tap to fill")。

### in-world での取り出し (left-click)

`ItemControllableDrawerTile.onClicked` (`ItemControllableDrawerTile.java:81-94`):
```java
ItemHandlerHelper.giveItemToPlayer(playerIn,
    getStorage().extractItem(slot,
        playerIn.isShiftKeyDown() ? getStorage().getStackInSlot(slot).getMaxStackSize() : 1,
        false));
```
**shift = maxStackSize 個、通常 = 1 個**。`removeTicks = 3` で連打 throttling (`ItemControllableDrawerTile.java:82-84`)。

### GUI 内の slot (= upgrade slot のみ)

upgrade slot は Titanium の `InventoryComponent` (`ControllableDrawerTile.java:67-94`)。これは ItemStackHandler 系で、`setInputFilter`, `setOnSlotChanged`, `setSlotLimit(1)` を chain して制約を付与する設計。drawer 本体 slot は **Slot 化していない**ので、shift-click 大量 transfer のロジックは存在しない。

→ **PEB は drawer 流の擬似 slot ではなく、本物の `AbstractContainerMenu + SlotItemHandler` を組む方が "256 slot UI + 検索 + scroll" の要求に素直**。drawer の `onSlotActivated` 系は PEB の対象外。

## 5. UI rendering

### GUI 内: 数字 overlay の描画 (DrawerInfoGuiAddon)

`DrawerInfoGuiAddon.drawBackgroundLayer` (`DrawerInfoGuiAddon.java:50-70`):
```java
guiGraphics.blit(gui, guiX + getPosX(), guiY + getPosY(), 0, 0, size, size, size, size);  // 背景
for (var i = 0; i < slotAmount; i++) {
    var itemStack = slotStack.apply(i);
    if (itemStack.isEmpty() && !slotLockedDisplay.apply(i).isEmpty()) {
        itemStack = slotLockedDisplay.apply(i);   // ghost 表示
    }
    if (!itemStack.isEmpty()) {
        var x = guiX + slotPosition.apply(i).getLeft() + getPosX();
        var y = guiY + slotPosition.apply(i).getRight() + getPosY();
        guiGraphics.renderItem(itemStack, x, y);
        var amount = NumberUtils.getFormatedBigNumber(slotStack.apply(i).getCount())
                   + "/" + NumberUtils.getFormatedBigNumber(slotMaxAmount.apply(i));
        var scale = 0.5f;
        guiGraphics.pose().translate(0, 0, 200);
        guiGraphics.pose().scale(scale, scale, scale);
        guiGraphics.drawString(font, amount,
            (x + 17 - font.width(amount) / 2) * (1 / scale),
            (y + 12) * (1 / scale),
            0xFFFFFF, true);
        guiGraphics.pose().scale(1 / scale, 1 / scale, 1 / scale);
        guiGraphics.pose().translate(0, 0, -200);
    }
}
```
**ポイント**:
- `renderItem` の上に `drawString` で **count/max を半角縮小表示** (scale 0.5)。
- z オフセット `translate(0, 0, 200)` で item icon の上に確実に描画。
- ghost item = `slotLockedDisplay.apply(i)` (空 slot で lock されている場合の表示用 ItemStack)。

`drawForegroundLayer` (`DrawerInfoGuiAddon.java:74-100`) では mouse over 判定 (18×18 box) してハイライト `fill(...-2130706433)` (半透明白) + tooltip (`item: <name>`, `amount: x/y`, `slot: i`)。

### in-world: BER による drawer 前面の item + 数字

`DrawerRenderer.renderStack` (`DrawerRenderer.java:174-205`):
- `model.isGui3d()` で 3D item と 2D icon を区別 (3D は厚み付き scale、2D は薄く scale)。
- `Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, ...)`
- `options.isActive(TOGGLE_NUMBERS)` なら `renderText` で `NumberUtils.getFormatedBigNumber(amount)` を `font.drawInBatch` で前面に描画 (`DrawerRenderer.java:208-237`)。

ヘルパ `renderIndicator` (`DrawerRenderer.java:67-110`) で充填率バーを描く (mode 0=off, 1=full only, 2=always, 3=no bg)。

PEB が world 内描画を持つかは要件次第。**GUI 内の count overlay (DrawerInfoGuiAddon パターン)** は流用価値がある。

## 6. PEB (enchanted_book only constraint + 256 slot) に効くパターン

### 採用候補

| パターン | 出典 | PEB での使い方 |
|---|---|---|
| ItemStack capability で IItemHandler を返す | `DrawerBlock.DrawerItem.initCapabilities` `DrawerBlock.java:269-272` | PEB Item の `initCapabilities` で 256-slot handler を返す |
| TILE = CompoundTag を保存する DataComponent | `FSAttachments.TILE` `FSAttachments.java:48` | PEB の本のリストを `CompoundTag` 1 個に詰めて持つ |
| 親 ItemStack を handler が握って onChange で書き戻す | `DrawerStackItemHandler.stack` field + `onChange()` `DrawerStackItemHandler.java:120-123` | enchanted_book 操作のたびに ItemStack を書き戻す |
| 1 slot 1 item の `getSlotLimit=1` + `isItemValid` で種別制約 | `ArmoryCabinetInventoryHandler.java:69-80` | enchanted_book のみ受け入れ |
| `Capabilities.ItemHandler.ITEM` を持つ stack を拒否 (ネストの罠回避) | `ArmoryCabinetInventoryHandler.java:81-87` | bag-in-bag 防止 |
| GUI の count/状態 overlay | `DrawerInfoGuiAddon.drawBackgroundLayer` `DrawerInfoGuiAddon.java:50-70` | 各本のエンチャ情報・lvl をオーバレイ |
| `BlockEntity.saveWithoutMetadata` → ItemStack の DataComponent に格納し setPlacedBy で復元 | `ArmoryCabinetBlock.java:60-87` | PEB が block ⇄ item の双方向を持つなら同パターン |

### ArmoryCabinet は PEB の最良 reference

`ArmoryCabinetInventoryHandler` (`ArmoryCabinetInventoryHandler.java` 全体, **107 行**) が PEB に最も近い:

- **4096 slot 固定 (config 値)** (`FunctionalStorageConfig.java:13`, `ArmoryCabinetInventoryHandler.java:28`)
- **`getSlotLimit = 1`** (`ArmoryCabinetInventoryHandler.java:69`)
- `isCertifiedStack` で **`stack.isDamageableItem() || isEnchantable() || JUKEBOX_PLAYABLE || AnimalArmorItem`** に限定 (`ArmoryCabinetInventoryHandler.java:81-87`)
- `insertItem` は `copyWithCount(1)` でセット、`getCount() > 1` なら残りを返却 (`ArmoryCabinetInventoryHandler.java:43-52`)
- `extractItem` は **amount 無視で 1 個丸ごと取り出し**, slot を EMPTY に (`ArmoryCabinetInventoryHandler.java:57-65`)
- NBT: `compoundTag.put(String.valueOf(i), stack.saveOptional(provider))` (空 slot は書かない) (`ArmoryCabinetInventoryHandler.java:90-97`)

**PEB の `isCertifiedStack` 等価**:
```java
return stack.is(Items.ENCHANTED_BOOK);
// or
return stack.has(DataComponents.STORED_ENCHANTMENTS);
```

### 検索 / scroll は Functional Storage に **無い**

drawer は world 内で物理配置するため search も scroll もない。PEB の「検索 + scroll」は **Functional Storage には存在しないので別 reference (例: Sophisticated Backpacks / Iron's Spells creative tab / vanilla Creative inventory) を当たる必要がある**。

## 7. 罠 / 注意点

1. **`DrawerStackItemHandler.isVoid()` / `isLocked()` がハードコード `true`** (`DrawerStackItemHandler.java:118, 155`) — フィールドを参照していない。コピペするなら必ず修正。**PEB の本人実装ミス防止のため、bool field の getter は最初から正しく書く**。

2. **`extractItem` での maxStackSize 越え返却**:
   - `DrawerStackItemHandler.extractItem` (`DrawerStackItemHandler.java:128-152`) は抜き出した `out.setCount(newAmount)` の clamp が無い。`newAmount` が 64 を超えていると、IItemHandler 仕様 (戻り値は valid な ItemStack) に違反気味。
   - 一方 `BigInventoryHandler.extractItem` (`BigInventoryHandler.java:67-90`) では `amount = Math.min(amount, bigStack.getStack().getMaxStackSize())` で先に clamp してから処理する。**PEB はこっち側のロジックを採るのが安全**。

3. **`onChange()` で `stack.set(FSAttachments.TILE, new CompoundTag())` を呼び、その直後に `.put("handler", serializeNBT(...))`** (`DrawerStackItemHandler.java:120-123`) — `set` してから取得した参照に `.put` で書き戻すパターン。これは新しい CompoundTag を作って即時上書きしているため、**parent ItemStack の他の "tile" 内データ (storageUpgrades, utilityUpgrades) は失われる**。constructor が `tile.getCompound("storageUpgrades")` を読んでいる (`DrawerStackItemHandler.java:48-56`) のに、`onChange` 後はそれらが消える。**ItemStack 内 container 設計の典型バグ。PEB では handler tag を独立コンポーネント (`FSAttachments.BOOKS = list of ItemStack`) として分離するか、`stack.update(FSAttachments.TILE, ...)` で merge する**。

4. **`BigStack.setStack(stack)` は `copy()` する** (`BigInventoryHandler.java:179-182`) が、insert 後 `setAmount` で `slotStack.setCount(amount)` するため、**slotStack を外部で改変すると本体に伝播する**。`getStackInSlot` で `slotStack` を直接返す block 版実装 (`BigInventoryHandler.java:42`) は client/外部が `setCount` 等で変更しないことを前提にしている。PEB は **常に `copy()` を返す** ことを推奨。

5. **`getSlotLimit` の `maxStackSize/64` 換算** (`BigInventoryHandler.java:91-101`) は **enchanted_book (max=1) では 1/64 倍になる**ので、drawer 流の `getTotalAmount() * stackSize` を素直に使うと容量がほぼ 0 になる。PEB は別計算式 (固定 256 slot × 1 本) を使う。

6. **drawer は GUI 内に本体 slot を持たない** (Section 4 参照)。PEB が `Menu` ベースの 256 slot UI を作るなら、Functional Storage の `DrawerInfoGuiAddon` (擬似 slot) ではなく **vanilla `AbstractContainerMenu` + `SlotItemHandler` の素朴な構成** を取る。Functional Storage の upgrade slot 部 (`ControllableDrawerTile.java:67-94`) は Titanium `InventoryComponent` 経由でしか参照例が無いので、PEB に直接流用しづらい。

7. **`ItemStack.isSameItemSameComponents` を使う等価判定** (`BigInventoryHandler.java:114`, `DrawerStackItemHandler.java:127`) — DataComponents 採用後の正しい API。`ItemStack.isSameItem` のみだと NBT/components 違いを見落とす。**enchanted_book は `stored_enchantments` component の中身で別物**なので、PEB が「同じ enchant の本をスタック」したい場合に必要。逆に「すべての enchanted_book を 1 slot 1 個で持つ」なら不要。

8. **`Item.DEFAULT_MAX_STACK_SIZE`** (`DrawerStackItemHandler.java:175`) は `64` の定数。書く時にマジックナンバ `64` ではなくこの定数を使うのが NeoForge 1.21 流。

9. **DataComponent 登録は `DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MOD_ID)`** (`FSAttachments.java:25`) で、`builder().persistent(codec).build()` で型を作る (`FSAttachments.java:62-64`)。`CompoundTag` を入れたいなら `CompoundTag.CODEC` (`FSAttachments.java:48`)。

10. **`BlockEntity.saveWithoutMetadata(registryAccess)`** で取得した tag を ItemStack の DataComponent に入れ、再配置時に `loadAdditional(tag, registryAccess())` で復元するパターン (`ArmoryCabinetBlock.java:60-87`)。`saveWithMetadata` だと block entity type ID 等も入って復元時に競合する可能性があるため `withoutMetadata` を使う。

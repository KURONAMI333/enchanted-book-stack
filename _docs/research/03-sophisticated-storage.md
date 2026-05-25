# Sophisticated Storage 構造解析 (block-based、 参考 only)

調査対象: `/tmp/mod-sources/SophisticatedStorage/src/main/java/net/p3pp3rf1y/sophisticatedstorage/` (および `SophisticatedCore` の関連 base クラス)
対象バージョン: NeoForge 1.21.x 系 (`HolderLookup.Provider` 受け取り型の `saveAdditional` / `loadAdditional` shape、`useItemOn` / `useWithoutItem` を override する shape を確認済み)

---

## 1. パッケージ構造

トップレベル: `net.p3pp3rf1y.sophisticatedstorage`

```
block/      Block + BlockEntity + StorageWrapper + ItemContentsStorage 等
client/     ClientEventHandler、StorageTextureManager、render
client/gui/ Screen 系 (StorageScreen, LimitedBarrelScreen, DecorationTableScreen 等)
common/gui/ Menu 系 (StorageContainerMenu, LimitedBarrelContainerMenu 等)
compat/     他 mod 連携
crafting/   Recipe
data/       Datagen
entity/     ItemDisplayContext entity (たぶん dropped barrel)
init/       ModBlocks, ModItems, ModBlockEntities, ModDataComponents の DeferredRegister
item/       StorageBlockItem, StackStorageWrapper, StorageTierUpgradeItem, PackingTapeItem 等
network/    Payload (StorageOpennessPayload 等)
settings/   StorageSettingsHandler (core SettingsHandler の派生)
upgrades/   storage-specific upgrade 実装
util/
```

実体抽出 (`ls block/`): `StorageBlockBase`, `StorageBlockEntity`, `StorageWrapper`, `BarrelBlock`, `BarrelBlockEntity`, `ChestBlock`, `ChestBlockEntity`, `ShulkerBoxBlock`, `ShulkerBoxBlockEntity`, `LimitedBarrelBlock`, `LimitedBarrelBlockEntity`, `WoodStorageBlockBase`, `WoodStorageBlockEntity`, `ControllerBlock` / `ControllerBlockEntity`, `StorageConnectorBlock` / `Entity`, `StorageIOBlock` / `Entity`, `StorageInputBlockEntity`, `StorageOutputBlockEntity`, `StorageLinkBlock` / `Entity`, `DecorationTableBlock` / `Entity`, **`ItemContentsStorage`**, **`ContentsFilteredItemHandler`**, `DynamicRenderTracker`, `SophisticatedOpenersCounter`, `VerticalFacing`, `BarrelMaterial`, `StoragePositionGroups` および多数の interface (`IStorageBlock`, `ILockable`, `ICountDisplay`, `IFillLevelDisplay`, `IMaterialHolder`, `ISneakItemInteractionBlock`, `ITierDisplay`, `IUpgradeDisplay`, `ITintableBlockItem`, `IDynamicRenderTracker`, `IAdditionalDropDataBlock`).

---

## 2. Block + BlockEntity

### 2.1 `StorageBlockBase` (abstract、`block/StorageBlockBase.java:42`)

```java
public abstract class StorageBlockBase extends BlockBase
        implements IStorageBlock, ISneakItemInteractionBlock, EntityBlock {
    public static final BooleanProperty TICKING = BooleanProperty.create("ticking");
    protected final Supplier<Integer> numberOfInventorySlotsSupplier;
    protected final Supplier<Integer> numberOfUpgradeSlotsSupplier;
```

- インベントリ/アップグレード枠数を `Supplier<Integer>` で持つ → Config (`Config.SERVER.woodBarrel.inventorySlotCount` 等) 経由で動的可変 (`Config.java:308`).
- 直接 `useItemOn` / `useWithoutItem` を持たず、サブクラスで実装 (`BarrelBlock.java:105,122`、`ChestBlock.java:258,278`).
- `entityInside` で `ItemEntity` の自動拾い上げ (pickup-response upgrade)`StorageBlockBase.java:92-100`.
- `hasAnalogOutputSignal` / `getAnalogOutputSignal` を override してコンパレータ出力対応 (`StorageBlockBase.java:131-138`).
- ticker は `TICKING` blockstate が `true` の時のみ取得 (`StorageBlockBase.java:107-110`).
- `onRemove` 時に controller から外す、`shouldDropContents()` ならドロップ (`StorageBlockBase.java:140-`).

### 2.2 `StorageBlockEntity` (abstract、`block/StorageBlockEntity.java:43`)

```java
public abstract class StorageBlockEntity extends BlockEntity
        implements IControllableStorage, ILinkable, ILockable, Nameable,
                   ITierDisplay, IUpgradeDisplay, Clearable {
    public static final String STORAGE_WRAPPER_TAG = "storageWrapper";
    public static final String UPDATE_BLOCK_RENDER_TAG = "updateBlockRender";
    private final StorageWrapper storageWrapper;
```

#### 2.2.1 構築時に匿名 `StorageWrapper` を実装 (`StorageBlockEntity.java:72-156`)

`new StorageWrapper(supplierForSaveHandler, onSerializeRenderInfo, markContentsDirty, numberOfDisplayItems, showsCountsAndFillRatios)` を呼び、5 個の callback と 9 個の `protected/abstract` メソッドを override:

- `getContentsUuid()` → 未設定なら lazy `UUID.randomUUID()` して `save()` する (`:80-86`).
- `getWrappedStorageStack()` → `getCloneItemStack(state, hitResult, level, pos, null)` を呼ぶ (`:88-94`).
- `onUpgradeRefresh()` → tick が必要な upgrade があれば `setTicking(true)` (`:96-101`).
- `getDefaultNumberOfInventorySlots()` / `getDefaultNumberOfUpgradeSlots()` → block の supplier に委譲.
- `isAllowedInStorage(stack)` / `emptyInventorySlotsAcceptItems()` / `getInventoryForInputOutput()` → 鍵 (lock) 状態と memorize 設定を見て `ContentsFilteredItemHandler` を返す (`:136-153`).

#### 2.2.2 NBT serialize: `STORAGE_WRAPPER_TAG` 1 個に集約

`saveAdditional(CompoundTag tag, HolderLookup.Provider registries)` (`StorageBlockEntity.java:198-205`):

```java
super.saveAdditional(tag, registries);
saveStorageWrapper(tag);          // tag.put(STORAGE_WRAPPER_TAG, storageWrapper.save(new CompoundTag()))
saveSynchronizedData(tag);        // displayName, updateBlockRender, locked, showLock/Tier/Upgrades, controllerPos
if (isLinkedToController) tag.putBoolean("isLinkedToController", isLinkedToController);
```

`saveStorageWrapper` (`:207-209`) → `tag.put("storageWrapper", storageWrapper.save(new CompoundTag()))`.

クライアント送信用 `saveStorageWrapperClientData(tag)` (`:211-213`) は `storageWrapper.saveData(tag)` のみ呼び、**`contents` tag (inventory 中身) を含めない**。`getUpdateTag` / `getUpdatePacket` で使う (`:334-358`).

`loadAdditional` (`:284-290`):
```java
super.loadAdditional(tag, registries);
loadStorageWrapper(tag, registries);     // NBTHelper.getCompound(tag, STORAGE_WRAPPER_TAG).ifPresent(storageWrapper::load)
loadSynchronizedData(tag, registries);
isLinkedToController = NBTHelper.getBoolean(tag, "isLinkedToController").orElse(false);
```

#### 2.2.3 サイズ動的変更

`changeStorageSize(int additionalInventorySlots, int additionalUpgradeSlots)` (`StorageBlockEntity.java:389-394`):
```java
int currentInventorySlots = getStorageWrapper().getInventoryHandler().getSlots();
getStorageWrapper().changeSize(additionalInventorySlots, additionalUpgradeSlots);
changeSlots(currentInventorySlots + additionalInventorySlots);
invalidateCapabilitiesAndControllerCache();
```

### 2.3 `StorageWrapper` (abstract、`block/StorageWrapper.java:34`)

`IStorageWrapper` の block 寄り実装。core 側にも `StorageWrapper` インタフェースの noop / item 寄り実装が他にある (`NoopStorageWrapper`, `StackStorageWrapper`).

主要フィールド (`StorageWrapper.java:35-72`):

```java
public static final String CONTENTS_TAG = "contents";
public static final String SETTINGS_TAG = "settings";   // IStorageWrapper にある
public static final String RENDER_INFO_TAG = "renderInfo";
public static final String NUMBER_OF_INVENTORY_SLOTS_TAG = "numberOfInventorySlots";
public static final String NUMBER_OF_UPGRADE_SLOTS_TAG = "numberOfUpgradeSlots";
public static final String SORT_BY_TAG = "sortBy";
public static final String MAIN_COLOR_TAG = "mainColor";
public static final String ACCENT_COLOR_TAG = "accentColor";

@Nullable private InventoryHandler inventoryHandler = null;     // lazy
@Nullable private InventoryIOHandler inventoryIOHandler = null;
@Nullable private UpgradeHandler upgradeHandler = null;
private CompoundTag contentsNbt = new CompoundTag();    // ★ 中身は CompoundTag に保持
private CompoundTag settingsNbt = new CompoundTag();
@Nullable protected UUID contentsUuid = null;
```

#### 2.3.1 NBT save/load API (`StorageWrapper.java:160-238`)

```java
public CompoundTag save(CompoundTag tag) {
    saveContents(tag);   // tag.put(CONTENTS_TAG, getContentsNbt().copy())
    saveData(tag);
    return tag;
}

CompoundTag saveData(CompoundTag tag) {       // package-private、client同期にも使う
    if (!settingsNbt.isEmpty())    tag.put(SETTINGS_TAG, settingsNbt);
    if (!renderInfoNbt.isEmpty())  tag.put(RENDER_INFO_TAG, renderInfoNbt);
    if (contentsUuid != null)      tag.put(UUID_TAG, NbtUtils.createUUID(contentsUuid));
    if (openTabId >= 0)            tag.putInt(OPEN_TAB_ID_TAG, openTabId);
    tag.putString(SORT_BY_TAG, sortBy.getSerializedName());
    if (columnsTaken > 0)          tag.putInt("columnsTaken", columnsTaken);
    if (numberOfInventorySlots > 0) tag.putInt(NUMBER_OF_INVENTORY_SLOTS_TAG, ...);
    if (numberOfUpgradeSlots > -1)  tag.putInt(NUMBER_OF_UPGRADE_SLOTS_TAG, ...);
    if (mainColor != -1)           tag.putInt(MAIN_COLOR_TAG, mainColor);
    if (accentColor != -1)         tag.putInt(ACCENT_COLOR_TAG, accentColor);
    return tag;
}

public void load(CompoundTag tag) {
    loadContents(tag);   // contentsNbt = tag.getCompound(CONTENTS_TAG); onContentsNbtUpdated();
    loadData(tag);
    if (inventoryHandler != null) initInventoryHandler();
    if (upgradeHandler != null)   getUpgradeHandler().refreshUpgradeWrappers();
    ...
}
```

#### 2.3.2 inventoryHandler の lazy 生成 (`StorageWrapper.java:257-266`)

```java
private void initInventoryHandler() {
    inventoryHandler = new InventoryHandler(getNumberOfInventorySlots(), this, getContentsNbt(),
            getSaveHandler.get(), StackUpgradeItem.getInventorySlotLimit(this), Config.SERVER.stackUpgrade) {
        @Override
        protected boolean isAllowed(ItemStack stack) { return isAllowedInStorage(stack); }
    };
    inventoryHandler.addListener(getSettingsHandler().getTypeCategory(ItemDisplaySettingsCategory.class)::itemChanged);
    inventoryHandler.setShouldInsertIntoEmpty(this::emptyInventorySlotsAcceptItems);
}
```

`InventoryHandler` は `contentsNbt` (CompoundTag) を直接読み書きする shape → wrapper の save 1 回で全 slot がシリアライズされる。

### 2.4 `IStorageWrapper` (core API、`SophisticatedCore/.../api/IStorageWrapper.java:19`)

`ITintable` を継承。主要メソッド:

```java
InventoryHandler getInventoryHandler();
ITrackedContentsItemHandler getInventoryForInputOutput();
SettingsHandler getSettingsHandler();
UpgradeHandler getUpgradeHandler();
Optional<UUID> getContentsUuid();
RenderInfo getRenderInfo();
ItemStack getWrappedStorageStack();             // default EMPTY
int getBaseStackSizeMultiplier();               // default 1
default int getNumberOfSlotRows() { return 0; }
String getStorageType();
Component getDisplayName();
```

`StorageWrapper.getNumberOfSlotRows()` (`block/StorageWrapper.java:305-308`) の実装:
```java
int itemInventorySlots = getNumberOfInventorySlots();
return (int) Math.ceil(itemInventorySlots <= 81 ? itemInventorySlots / 9.0 : itemInventorySlots / 12.0);
```
→ **81 slot 以下なら 9 列、それ以上は 12 列** に切り替わる。Diamond/Netherite tier (108/132 slot、`Config.java:202-203`) は 12 列レイアウトで表示される。

---

## 3. Menu (`common/gui/LimitedBarrelContainerMenu` 等)

### 3.1 core の `StorageContainerMenuBase` (`SophisticatedCore/.../common/gui/StorageContainerMenuBase.java:101`)

```java
protected StorageContainerMenuBase(MenuType<?> menuType, int containerId, Player player,
        S storageWrapper, IStorageWrapper parentStorageWrapper,
        int storageItemSlotIndex, boolean shouldLockStorageItemSlot, List<Slot> extraSlots) {
    ...
    initSlotsAndContainers(player, storageItemSlotIndex, shouldLockStorageItemSlot, extraSlots);
}

protected void initSlotsAndContainers(...) {
    addStorageInventorySlots();            // line 126
    addPlayerInventorySlots(player.getInventory(), storageItemSlotIndex, shouldLockStorageItemSlot);
    ...
}
```

`addStorageInventorySlots()` (`StorageContainerMenuBase.java:249-296`) はループで `StorageInventorySlot` を作成、`NoSortSettingsCategory` の slot だけ `addNoSortSlot()`、それ以外は `addSlot()` で登録する。slot ごとに `inaccessibleSlots`, `slotLimitOverrides`, `emptySlotIcons` map を参照して動作変更。

`addPlayerInventorySlots` (`StorageContainerMenuBase.java:302-318`) は 3×9 + 9 のプレイヤーインベントリ。座標 (0,0) で `Slot` を作るだけ → 実座標は Screen 側で後から書き換える設計。

### 3.2 `StorageContainerMenu` (`common/gui/StorageContainerMenu.java:28`、134 行)

```java
public class StorageContainerMenu extends StorageContainerMenuBase<IStorageWrapper>
        implements ISyncedContainer {
    private final StorageBlockEntity storageBlockEntity;

    public StorageContainerMenu(MenuType<?> menuType, int containerId, Player player, BlockPos pos) {
        super(menuType, containerId, player, getWrapper(player.level(), pos),
              NoopStorageWrapper.INSTANCE, -1, false);
        storageBlockEntity = WorldHelper.getBlockEntity(player.level(), pos, StorageBlockEntity.class)
                .orElseThrow(...);
        if (!player.level().isClientSide()) storageBlockEntity.startOpen(player);
    }

    public static StorageContainerMenu fromBuffer(int windowId, Inventory playerInventory, FriendlyByteBuf buffer) {
        return new StorageContainerMenu(windowId, playerInventory.player, buffer.readBlockPos());
    }
```

ポイント:
- ctor の 2 引数 `parentStorageWrapper` は `NoopStorageWrapper.INSTANCE` (block には親が無い)。
- `storageItemSlotIndex = -1`、`shouldLockStorageItemSlot = false` → block-based なので自分自身を player inventory にロックする必要が無い。
- buffer codec は `BlockPos` 1 個だけ。
- `stillValid` (`:107-114`) で `WoodStorageBlockEntity.isPacked()` を弾く ★ pack 中は GUI 開かせない.
- `openSettings` (`:88-95`) で `SophisticatedMenuProvider` を渡して別 menu (`StorageSettingsContainerMenu`) に切り替え.

### 3.3 `LimitedBarrelContainerMenu` (`common/gui/LimitedBarrelContainerMenu.java`、30 行)

```java
public class LimitedBarrelContainerMenu extends StorageContainerMenu {
    public LimitedBarrelContainerMenu(int containerId, Player player, BlockPos pos) {
        super(LIMITED_BARREL_CONTAINER_TYPE.get(), containerId, player, pos);
    }
    public static LimitedBarrelContainerMenu fromBuffer(...) { ... }
    @Override
    protected StorageSettingsContainerMenu instantiateSettingsContainerMenu(...) { return new LimitedBarrelSettingsContainerMenu(...); }
    @Override
    public List<Integer> getSlotOverlayColors(int slot) { return List.of(); }   // 多色オーバーレイ無効
}
```
→ block-specific な差分は **設定 menu を差し替える** ことと、**slot color overlay を切る** だけ。slot 自体は base クラスが Config (`limited{wood,copper,iron,gold,diamond,netherite}Barrel{1-4}`、`Config.java:208-` 周辺) の `inventorySlotCount` から 1/2/3/4 個生成。

---

## 4. Screen (`client/gui/LimitedBarrelScreen` 等)

### 4.1 core `StorageScreenBase<C>` (`SophisticatedCore/.../client/gui/StorageScreenBase.java`)

- 検索ボックス: `addSearchBox()` (`:280-296`) で `SearchBox` widget を `leftPos+x, topPos+5` に配置、`setResponder(this::onSearchPhraseChange)`。`stackFilter` (`:100-101`) は `searchBox.getValue().toLowerCase()` で部分一致するアイテム名のみフィルタ。
- スクロール: `numberOfVisibleRows < getMenu().getNumberOfRows()` の時のみ `InventoryScrollPanel` を生成 (`:363-365`)、`scrollBarOffset = 6` をボタン配置の右シフトに使う (`:434-435`)。`mouseScrolled` (`:1165-1170`) は `modalOverlay` を優先して上にいる場合はそっち、それ以外は super に委譲。
- スロット列数: `getSlotsOnLine()` (`:423-425`) = `storageBackgroundProperties.getSlotsOnLine() - getMenu().getColumnsTaken()` (upgrade panel の張り出しで動的減).
- 背景幅: `imageWidth = storageBackgroundProperties.getSlotsOnLine() * 18 + 14` (`:136`)。

### 4.2 `StorageScreen` (`client/gui/StorageScreen.java`、21 行)

```java
public class StorageScreen extends StorageScreenBase<StorageContainerMenu> {
    public static StorageScreen constructScreen(StorageContainerMenu screenContainer, Inventory inv, Component title) {
        return new StorageScreen(screenContainer, inv, title);
    }
    @Override
    protected String getStorageSettingsTabTooltip() { return StorageTranslationHelper.INSTANCE.translGui("settings.tooltip"); }
}
```
→ 通常 chest/barrel は core の base に完全委譲、tooltip 文言のみ override.

### 4.3 `LimitedBarrelScreen` (`client/gui/LimitedBarrelScreen.java`、164 行) — block-specific の参考実装

固有テクスチャ群を `TextureBlitData` で宣言 (`:23-29`):
```java
public static final ResourceLocation GUI_BACKGROUNDS = SophisticatedStorage.getRL("textures/gui/limited_barrels.png");
public static final TextureBlitData LIMITED_I_BACKGROUND   = new TextureBlitData(GUI_BACKGROUNDS, Dimension.SQUARE_256, new UV(  0,  0), new Dimension(84, 82));
public static final TextureBlitData LIMITED_II_BACKGROUND  = new TextureBlitData(..., new UV( 84,  0), ...);
public static final TextureBlitData LIMITED_III_BACKGROUND = new TextureBlitData(..., new UV(  0, 82), ...);
public static final TextureBlitData LIMITED_IV_BACKGROUND  = new TextureBlitData(..., new UV( 84, 82), ...);
```

override 一覧:
- `drawSlotBg` (`:37-41`): slot 数 1/2/3/4 に応じた `LIMITED_*_BACKGROUND` を 1 枚 blit (中央配置).
- `renderLabels` (`:47-50`): super 後に `renderBars()` でフィルレベルバーを 1〜4 個描画.
- `renderBars` (`:52-72`): slot 数で `switch`、`SMALL_BAR_FILL` (3×28) / `LARGE_BAR_FILL` (3×68) を切り替え、% を `font.drawString` で添える.
- `getStorageInventoryHeight` (`:84-86`): `STORAGE_SLOTS_HEIGHT = 82` を返して core の dynamic 計算をオーバーライド.
- `updateStorageSlotsPositions` (`:88-91`) + 静的 `updateSlotPositions` (`:93-138`): slot 数ごとに `slot.x` / `slot.y` をハードコードした座標へ書き換え (例: 1 slot は中央、2 slot は縦並び、4 slot は 2×2).
- `shouldShowSortButtons() { return false; }` (`:155-157`): ソートボタンを隠す.
- `addSearchBox() { /* No search box for limited barrels */ }` (`:159-162`): **検索ボックス無効化**.

---

## 5. tier upgrade (in-world item-use pattern)

### 5.1 `StorageTierUpgradeItem.onItemUseFirst` (`item/StorageTierUpgradeItem.java:69-80`)

```java
@Override
public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
    Level level = context.getLevel();
    if (level.isClientSide) return InteractionResult.PASS;

    BlockPos pos = context.getClickedPos();
    BlockState state = level.getBlockState(pos);
    Player player = context.getPlayer();
    return tryUpgradeStorage(stack, level, pos, state, player);
}
```

`tryUpgradeStorage` (`:82-86`) は `tier.getBlockUpgradeDefinition(state.getBlock())` で `TierUpgradeDefinition` を取り、対応 BE が存在すれば実体アップグレード。BE 型不一致や定義無しは `PASS` で素通り → 通常クリック処理に流れる。

### 5.2 `TierUpgradeDefinition<B extends BlockEntity>` strategy (`item/StorageTierUpgradeItem.java:320-370`)

```java
private abstract static class TierUpgradeDefinition<B extends BlockEntity> {
    private final List<Property<?>> propertiesToCopy;
    private final Class<B> blockEntityClass;
    private final Predicate<B> isUpgradingBlocked;
    private final StorageBlockBase newBlock;

    abstract boolean upgradeStorage(@Nullable Player player, BlockPos pos, Level level, BlockState state, B b);

    protected BlockState getBlockState(BlockState state) {
        BlockState newBlockState = newBlock().defaultBlockState();
        for (Property<?> property : getPropertiesToCopy()) {
            newBlockState = setProperty(newBlockState, state, property);
        }
        return newBlockState;
    }

    public int getCountRequired(BlockState state) { return 1; }
}
```

派生:
- `StorageTierUpgradeDefinition` (`:110`): 既存 mod block → 別 tier mod block. `propertiesToCopy` で `FACING`, `TICKING`, `FLAT_TOP`, `WATERLOGGED`, `CHEST_TYPE` を引き継ぐ.
- `LimitedBarrelTierUpgradeDefinition` (`:198`): Limited barrel 専用 (横/縦 facing + `FLAT_TOP`).
- `VanillaTierUpgradeDefinition` (`:209`): vanilla barrel/chest/shulker → mod tier 1. `WoodType` と `color` を保持して新 BE に書き戻す.
- `VanillaTintedShulkerBoxTierUpgradeDefinition` (`:203`): 色付きシュルカー専用.

### 5.3 古い block → 新 block へのデータ移譲 (`StorageTierUpgradeItem.java:128-145`)

```java
private StorageBlockEntity upgradeStorageBlock(BlockPos pos, Level level, StorageBlockEntity blockEntity,
        BlockState newBlockState, int newInventorySize, int newUpgradeSize) {
    CompoundTag beTag = new CompoundTag();
    blockEntity.saveAdditional(beTag, level.registryAccess());           // ① 旧 BE を NBT 化

    StorageBlockEntity newBlockEntity = newBlock().newBlockEntity(pos, newBlockState);
    newBlockEntity.setBeingUpgraded(true);
    newBlockEntity.loadAdditional(beTag, level.registryAccess());        // ② 新 BE に load

    blockEntity.setBeingUpgraded(true);
    level.removeBlockEntity(pos);
    level.removeBlock(pos, false);                                       // ③ 旧 block 撤去 (drop 抑止)

    level.setBlock(pos, newBlockState, 3);                               // ④ 新 block を置く
    level.setBlockEntity(newBlockEntity);
    newBlockEntity.changeStorageSize(                                    // ⑤ slot 数差分を適用
        newInventorySize - newBlockEntity.getStorageWrapper().getInventoryHandler().getSlots(),
        newUpgradeSize   - newBlockEntity.getStorageWrapper().getUpgradeHandler().getSlots()
    );
    WorldHelper.notifyBlockUpdate(newBlockEntity);
    return newBlockEntity;
}
```

★ ポイント:
- `setBeingUpgraded(true)` フラグで `setRemoved()` → `removeFromController()` を抑止 (`StorageBlockEntity.java:325-331`). 終わったら `setBeingUpgraded(false)`.
- `level.removeBlock(pos, false)` の第二引数 false は drop 抑制.
- vanilla → mod の場合は (`VanillaTierUpgradeDefinition.upgradeStorage` `:269-283`) `BarrelBlockEntity#getItem(slot)` を全 slot ループで読んで `NonNullList<ItemStack>` に詰め、新 BE の `InventoryHandler#setStackInSlot` で書き戻す。これは vanilla BE が `STORAGE_WRAPPER_TAG` を持たないため NBT 直渡しが効かないから。
- 二連 chest 対応 (`upgradeDoubleChest` `:147-170`): メイン chest 側に slot 数 2 倍を割り当て、もう一方は 1 倍に。

### 5.4 `TierUpgrade` enum (`item/StorageTierUpgradeItem.java:401-` 周辺)

`BASIC`, `BASIC_TO_COPPER`, `BASIC_TO_IRON`, `BASIC_TO_GOLD`, `BASIC_TO_DIAMOND`, `BASIC_TO_NETHERITE`, ... の列挙。各値が `Map<Block, TierUpgradeDefinition<?>>` を持ち、入力 block → 出力 block + 戦略を引く。

---

## 6. UI 差分 (Limited Barrel の slot lock 機構等)

### 6.1 lock + memorize 連動 (`block/LimitedBarrelBlockEntity.java`)

```java
@Override public boolean memorizesItemsWhenLocked()        { return true; }   // :82
@Override public boolean allowsEmptySlotsMatchingItemInsertsWhenLocked() { return false; }  // :86
```

→ Limited Barrel は常時 memorize 有効、locked 時に新規 stack を受け付けない。これに伴って `setFixedSettings` (`:60-69`) が `ItemDisplaySettingsCategory`/`NoSortSettingsCategory` で全 slot を selected にする (= ソート対象から外す、count/fill 表示 on).

### 6.2 deposit on right-click (`block/LimitedBarrelBlockEntity.java:162-` 周辺)

`depositItem(player, hand, stackInHand, slot)` (`:162-`):
- 10 tick 以内に 2 回呼ばれたら `depositFromAllOfPlayersInventory` (double-click 全投入).
- 空 slot に valid item → `setStackInSlot`, locked なら `MemorySettingsCategory.selectSlot(slot)`.
- 既存 slot に追加 → `insertItemOnlyToSlot` で simulate→commit.

### 6.3 slot 単位の dye

`applyDye(slot, dyeStack, dyeColor, applyToAll)` (`:114-141`): `slotColors` map に `DyeColor` を入れて save、`getSlotColor(slot)` (`:158-160`) で text color を取り出す。

### 6.4 search box / sort button を消す override

`LimitedBarrelScreen.addSearchBox()` / `shouldShowSortButtons()` を override して空実装 (`client/gui/LimitedBarrelScreen.java:155-162`). Limited Barrel は 1〜4 slot しかないので機能不要。

### 6.5 slot 配置の手動指定

`updateSlotPositions` (`client/gui/LimitedBarrelScreen.java:93-138`) で `slot.x` / `slot.y` を 1/2/3/4 slot ごとに手書きハードコード。core の grid layout が `if (slotNumber == 1) ...` 等の特殊ケースを全部 case で分けている → 少数固定 slot は座標決め打ちでよい、と判断したパターン。

---

## 7. PEB (item-based) が学べるパターン

PEB は **ItemStack 内に container を持つ** ので、`StorageBlockEntity` の継承木は基本使わない。ただし以下は直接適用可能:

### 7.1 ★ `ItemContentsStorage` パターン (`block/ItemContentsStorage.java`、98 行)

これが本 mod 最重要の参考。**item に container 中身を直接埋めず、`UUID` だけ持たせて `SavedData` 側に紐づける**:

```java
public class ItemContentsStorage extends SavedData {
    private static final String SAVED_DATA_NAME = SophisticatedStorage.MOD_ID;
    private final Map<UUID, CompoundTag> storageContents = new HashMap<>();
    private static final ItemContentsStorage clientStorageCopy = new ItemContentsStorage();

    public static ItemContentsStorage get() {
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                DimensionDataStorage storage = overworld.getDataStorage();
                return storage.computeIfAbsent(new Factory<>(ItemContentsStorage::new, ItemContentsStorage::load), SAVED_DATA_NAME);
            }
        }
        return clientStorageCopy;
    }
    ...
    public CompoundTag getOrCreateStorageContents(UUID storageUuid) { ... }
    public void setStorageContents(UUID storageUuid, CompoundTag contents) { ... }
}
```

なぜ必要か:
- ItemStack の `BLOCK_ENTITY_DATA` data component に 256 slot 分の NBT を毎フレーム同期するとネットワーク負荷で死ぬ.
- HUD 表示・tooltip 等の単純な情報は data component に持ち、**インベントリ本体 (CompoundTag) は overworld の `DimensionDataStorage` 側に置く** → 必要な時に `UUID` で引く.
- client は `clientStorageCopy` を保持 (`:25`)、サーバ→クライアント同期 payload で部分更新.

PEB 設計で UUID + SavedData は **そのままコピペできるパターン**。256 slot ならなおさら必須。

### 7.2 ★ `StackStorageWrapper` (`item/StackStorageWrapper.java`、170 行)

item-based storage wrapper の参考実装そのもの:

```java
public class StackStorageWrapper extends StorageWrapper {
    private ItemStack storageStack;

    public static StackStorageWrapper fromStack(HolderLookup.Provider registries, ItemStack stack) {
        StackStorageWrapper stackStorageWrapper = StorageWrapperRepository.getStorageWrapper(stack, StackStorageWrapper.class, StackStorageWrapper::new);
        UUID uuid = stack.get(ModCoreDataComponents.STORAGE_UUID);
        if (uuid != null) {
            CompoundTag compoundtag = ItemContentsStorage.get()
                .getOrCreateStorageContents(uuid)
                .getCompound(StorageBlockEntity.STORAGE_WRAPPER_TAG);
            stackStorageWrapper.load(compoundtag);
            stackStorageWrapper.setContentsUuid(uuid);
        }
        return stackStorageWrapper;
    }

    @Override
    public void setContentsUuid(@Nullable UUID contentsUuid) {
        super.setContentsUuid(contentsUuid);
        if (contentsUuid != null) {
            storageStack.set(ModCoreDataComponents.STORAGE_UUID, contentsUuid);
            ItemContentsStorage itemContentsStorage = ItemContentsStorage.get();
            CompoundTag storageContents = itemContentsStorage.getOrCreateStorageContents(contentsUuid);
            if (!storageContents.contains(StorageBlockEntity.STORAGE_WRAPPER_TAG)) {
                CompoundTag storageWrapperTag = new CompoundTag();
                storageWrapperTag.put(CONTENTS_TAG, new CompoundTag());
                storageContents.put(StorageBlockEntity.STORAGE_WRAPPER_TAG, storageWrapperTag);
            }
            onContentsNbtUpdated();
        }
    }

    @Override
    protected CompoundTag getContentsNbt() {
        return StorageBlockItem.getEntityWrapperTagFromStack(storageStack)
            .map(wrapperTag -> wrapperTag.getCompound(CONTENTS_TAG))
            .orElseGet(() -> {
                if (contentsUuid == null) contentsUuid = getNewUuid();
                return ItemContentsStorage.get()
                    .getOrCreateStorageContents(contentsUuid)
                    .getCompound(StorageBlockEntity.STORAGE_WRAPPER_TAG)
                    .getCompound(CONTENTS_TAG);
            });
    }

    @Override
    protected void save() {
        if (contentsUuid != null) ItemContentsStorage.get().setDirty();
    }
}
```

ポイント:
- **2 通りのストレージ供給** を fallback で持つ: ① `BLOCK_ENTITY_DATA` data component (block を回収して item にした時に保持される) → ② `ItemContentsStorage` の UUID 経由. block↔item の変換でデータが失われない設計.
- 通常時は `ItemContentsStorage` 経由を使う (item は UUID だけ持ち、中身は外部).
- `save()` は `setDirty()` だけ呼ぶ → 永続化は `DimensionDataStorage` 任せ.
- `StorageWrapperRepository.getStorageWrapper(stack, ...)` で stack 単位にキャッシュ (毎クリックで作り直さない).

PEB の `StackStorageWrapper`-相当を作る時の rosetta stone。256 slot あれば差分 sync が重要なので、core の `InventoryHandler` の listener 機構をそのまま使う。

### 7.3 Menu の `fromBuffer` codec

block-based 版は `buffer.readBlockPos()` 1 個だけ (`StorageContainerMenu.java:60`). item-based の場合は「どの hand の item か」を送る:
- `BlockPos` の代わりに `InteractionHand` (1 byte) を送れば足りる.
- もしくは hand 不問で「現在開いている item の UUID」を送る.

### 7.4 `getNumberOfSlotRows` の 81 閾値

`StorageWrapper.java:305-308`:
```java
return (int) Math.ceil(itemInventorySlots <= 81 ? itemInventorySlots / 9.0 : itemInventorySlots / 12.0);
```
→ PEB の 256 slot は **12 列で 22 行** (256/12 = 21.33 → 22)。core の 12 列レイアウト + scroll が標準で使える。

### 7.5 search + scroll の組み合わせ

`StorageScreenBase` の `SearchBox` + `InventoryScrollPanel` をそのまま使えば、PEB の検索バー + スクロール UI は core 側でほぼ完成形。255 slot を可視 6〜8 行に閉じ込めても OK.

### 7.6 lock + memorize で「特定 slot を空のまま予約」

`ContentsFilteredItemHandler` (`StorageBlockEntity.java:142-153`) + `MemorySettingsCategory` の組み合わせ。PEB で「エンチャント本だけ受け付ける」フィルタを実装する時、`isAllowedInStorage(ItemStack stack)` を override して `stack.is(Items.ENCHANTED_BOOK)` をチェックする最短ルート (`StorageWrapper.java:154-158` の override hook).

---

## 8. 罠 / 注意点

### 8.1 `STORAGE_WRAPPER_TAG = "storageWrapper"` の文字列 1 個に集約しているため、tier upgrade の `saveAdditional` → `loadAdditional` パススルーが成立する

`StorageTierUpgradeItem.upgradeStorageBlock` (`:128-138`) は旧 BE の全 NBT を新 BE にコピーして読ませる。これが効くのは **全 storage 系 BE が同じ tag 名で同じ wrapper を保存している** から。PEB でも単一 tag 名に集約する設計にしておくと、将来的に upgrade item を追加する時にコピペが効く.

### 8.2 `getUpdateTag` / `onDataPacket` でクライアントには `CONTENTS_TAG` を送らない

`saveStorageWrapperClientData` (`StorageBlockEntity.java:211-213`) は `storageWrapper.saveData(tag)` を呼び、`saveContents(tag)` は呼ばない。クライアントは GUI 開いた時に menu の slot sync 経由でだけ中身を見る. **256 slot を毎 chunk-update で送ったら破綻する** → PEB でも同じ分離が必須.

### 8.3 `isBeingUpgraded` フラグで `removeFromController` を抑止

`StorageBlockEntity.setRemoved` (`:325-331`):
```java
if (!isBeingUpgraded && !chunkBeingUnloaded && level != null) {
    removeFromController();
}
super.setRemoved();
```

tier upgrade 中に `level.removeBlock` を呼ぶと `setRemoved` 経由で controller 解除や drop が走るのを止める仕組み。**item-based PEB が将来「block 化 / item 化の往復」を作るなら同じ仕組みが要る**.

### 8.4 `WoodStorageBlockEntity.isPacked()` で GUI 開閉を弾く

`StorageContainerMenu.stillValid` (`:107-114`):
```java
return be instanceof StorageBlockEntity
    && (player.distanceToSqr(...) <= 64.0D)
    && (!(be instanceof WoodStorageBlockEntity wbe) || !wbe.isPacked());
```
GUI 開いた瞬間に packed 化された場合に即座に閉じる. PEB も「インベントリの中で更に PEB を持っていた時に親 PEB を閉じた」みたいなケース対策で `stillValid` を必ず実装する.

### 8.5 `setContentsUuid` が `setDirty` 系を呼ぶタイミング

`StackStorageWrapper.setContentsUuid` (`item/StackStorageWrapper.java:60-73`) は UUID 新規割り当て時に必ず `storageStack.set(STORAGE_UUID, uuid)` と `getOrCreateStorageContents(uuid)` を呼ぶ。**読み取り専用 (ホバー時の tooltip 計算等) のつもりで `getContentsNbt()` を呼んでも UUID が振られて save が走り得る** ので、PEB で tooltip 描画する時は専用の getter (UUID が無ければ早期 return) を用意した方がよい. `StackStorageWrapper.hasContents` (`:54-56`) はこの判定の参考.

### 8.6 `BLOCK_ENTITY_DATA` data component に NBT を直接ぶら下げる選択肢もあるが、256 slot だと膨らむ

`StorageBlockItem.getEntityWrapperTagFromStack` (`item/StorageBlockItem.java:25-31`):
```java
CustomData customData = componentHolder.get(() -> DataComponents.BLOCK_ENTITY_DATA);
if (customData == null) return Optional.empty();
return Optional.of(customData.copyTag().getCompound(STORAGE_WRAPPER_TAG));
```

これは **block を回収した時** の状態を読むパス. 通常運用 (item 内ストレージ) では `ItemContentsStorage` 経由を優先する設計 (`StackStorageWrapper.getContentsNbt` の `orElseGet` ブランチが本流). **NBT を `BLOCK_ENTITY_DATA` に直接持つと client 同期で全 256 slot 分の bytes が item move 毎に飛ぶ**.

### 8.7 `RenderInfo` validation が `onInit` で走る

`StorageWrapper.onInit` (`:296-302`):
```java
public void onInit(Level level) {
    IStorageWrapper.super.onInit(level);
    if (renderInfoValidationPending && !level.isClientSide()) {
        getRenderInfo().validate(this, level);
        renderInfoValidationPending = false;
    }
}
```
load 後の初回 `onInit` でレンダー情報を再検証 (item icon の同期等). item-based でも `StorageWrapperRepository` 経由のキャッシュ実体が初期化された時に同じ validation が必要なら検討.

### 8.8 `getNumberOfSlotRows` の 81 閾値は wrapper で固定化されている

`StorageWrapper.java:305-308`. **9 列 ↔ 12 列の境界が 81 slot にハードコード**. PEB は 256 slot 固定なら 12 列で計算されるが、別の列数 (例: 16 列) を取りたいなら override が必要 (`StorageScreenBase` 側にも `getSlotsOnLine` 周りで `storageBackgroundProperties` の差し替えが要る).

### 8.9 `Config.SERVER.woodBarrel.inventorySlotCount` の `defineInRange(default, 1, 180)`

`Config.java:310`. **storage 系の slot 数上限は 180 (Config 既定の範囲)**. PEB が 256 slot を取るなら NeoForge 側の仕組み (`InventoryHandler` 自体) は問題無いが、 sophisticated core の Config UI に乗せるなら range を 256 まで広げる必要がある. (PEB は core を依存に取らない予定なので関係ないが、参考まで.)

### 8.10 `setBeingUpgraded(true)` を忘れると double-chest upgrade で半壊する

`upgradeDoubleChest` (`StorageTierUpgradeItem.java:147-170`) は両 BE に `setBeingUpgraded(true)` を立ててから順次 upgrade、終わったら最後の new BE 2 個両方に `setBeingUpgraded(false)` を立てる。フラグ忘れると `setRemoved` で controller cache が破壊される. PEB が複数 item 同時操作するなら同型の問題. item-based なら隣接 BE が無いので relax できる.

# Sophisticated Backpacks 構造解析 (item-based storage の参考)

ソース: `/tmp/mod-sources/SophisticatedBackpacks/src/main/java/net/p3pp3rf1y/sophisticatedbackpacks/`
依存 core: `/tmp/mod-sources/SophisticatedCore/src/main/java/net/p3pp3rf1y/sophisticatedcore/`
NeoForge 1.21.1 系統。

---

## 1. パッケージ構造

`backpack/BackpackItem.java:68` の `package net.p3pp3rf1y.sophisticatedbackpacks` 直下の主要サブパッケージ（`ls` で確認、`api`, `client`, `command`, `common`, `compat`, `crafting`, `data`, `init`, `network`, `registry`, `settings`, `upgrades`, `util`、ルートに `Config.java` / `SophisticatedBackpacks.java`）。

- `backpack/` … BackpackItem, BackpackBlock, BackpackBlockEntity, BackpackStorage (SavedData), BackpackTemplates 等。サブ `backpack/wrapper/` に BackpackWrapper, IBackpackWrapper, BackpackInventoryHandler, BackpackSettingsHandler, BackpackFluidHandler, InventoryModificationHandler。
- `common/gui/` … BackpackContainer, BackpackContext, BackpackSettingsContainerMenu (Menu/Container 側)。
- `client/gui/` … BackpackScreen, BackpackSettingsScreen, IBackpackScreen。
- `client/render/` … BackpackItemStackRenderer (BEWLR), BackpackBlockEntityRenderer, BackpackLayerRenderer (装着描画), BackpackDynamicModel。
- `crafting/` … BackpackUpgradeRecipe, SmithingBackpackUpgradeRecipe, BackpackDyeRecipe, BasicBackpackRecipe。
- `init/ModItems.java` … 6 ティアの BackpackItem を DeferredRegister 経由で `ModItems.java:141-152` に登録。MenuType, RecipeSerializer もここで登録。
- `Config.java:71-76` に 6 ティア分の `BackpackConfig` フィールド、`Config.java:151-156` で実際の slot 数を default 値で生成（後述）。

行数規模:
- `BackpackItem.java`: 393 行
- `BackpackBlock.java`: 363 行
- `BackpackBlockEntity.java`: 347 行
- `BackpackWrapper.java`: 717 行
- `BackpackContainer.java`: 170 行
- `BackpackScreen.java`: 65 行
- `BackpackContext.java`: 593 行
- `Config.java` の Server 全体は大きい（abridged）

---

## 2. BackpackItem

パス: `backpack/BackpackItem.java`、393 行（`wc -l`）。

### 内容物保持 mechanism

`extends ItemBase implements IStashStorageItem`（`BackpackItem.java:68`）。

ItemStack 自体には実体データを置かず、UUID キーで外部 `SavedData` に保持する 3 層構造:

1. `ItemStack` の `DataComponent`:
   - `ModCoreDataComponents.STORAGE_UUID` (`UUID`): 内容物 NBT を引くキー（`BackpackWrapper.java:309-311` で `Optional.ofNullable(getBackpackStack().get(ModCoreDataComponents.STORAGE_UUID))`、`ModCoreDataComponents.java:46` に登録）。
   - `ModCoreDataComponents.NUMBER_OF_INVENTORY_SLOTS` (`Integer`): tier から取得した値を ItemStack にキャッシュ（`BackpackWrapper.java:154-172`、`ModCoreDataComponents.java:36`）。
   - `ModCoreDataComponents.NUMBER_OF_UPGRADE_SLOTS` (`Integer`): 同様（`BackpackWrapper.java:296-306`、`ModCoreDataComponents.java:38`）。
   - `ModCoreDataComponents.MAIN_COLOR` / `ACCENT_COLOR` (`int`): 色（`BackpackItem.java:87-98`）。
   - `ModCoreDataComponents.OPEN_TAB_ID` / `SORT_BY`、`ModDataComponents.COLUMNS_TAKEN` / `LOOT_TABLE` / `LOOT_FACTOR` / `TEMPLATE_NAME` 等の小さい設定値。
2. **外部 SavedData**: `BackpackStorage extends SavedData`（`backpack/BackpackStorage.java:22`）。`Map<UUID, CompoundTag> backpackContents`（`BackpackStorage.java:25`）に「UUID -> 内容物 CompoundTag」を保持。`BackpackStorage.get()` は `BackpackStorage.java:32-43` でサーバスレッドなら Overworld の `DimensionDataStorage` から、それ以外なら `clientStorageCopy`（`BackpackStorage.java:26`）を返す。
3. **メモリキャッシュ**: `BackpackWrapper.fromStack(stack)`（`BackpackWrapper.java:95-109`）が `StorageWrapperRepository` から弱参照的にラッパーを引く（UUID 無ければ毎回新規 `BackpackWrapper`）。`BackpackWrapper` 自体は `handler`, `upgradeHandler`, `settingsHandler` 等を遅延初期化（`BackpackWrapper.java:60-79`、`getInventoryHandler()` の `BackpackWrapper.java:139-151` が `getBackpackContentsNbt()` 経由で `BackpackStorage.get().getOrCreateBackpackContents(uuid)` から CompoundTag を引いて `BackpackInventoryHandler` に渡す）。

UUID 採番は `BackpackWrapper.java:313-322` の `getOrCreateContentsUuid()` で「未設定なら `UUID.randomUUID()` → `setContentsUuid()` → ItemStack に書き込み」。

migrate: Vanilla の `DataComponents.CONTAINER`（バンドル系の `ItemContainerContents`）に何か入った状態で初めて開かれた時、`BackpackWrapper.java:466-481` の `fillWithExtraItems()` が `ItemContainerContents` を読み出して内部 inventory に流し込み、`backpack.remove(DataComponents.CONTAINER)` で消す。互換移行用。

### use() で Menu 開く流れ

`BackpackItem.java:260-271`:

```
public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    if (!level.isClientSide) {
        String handlerName = hand == InteractionHand.MAIN_HAND ? PlayerInventoryProvider.MAIN_INVENTORY : PlayerInventoryProvider.OFFHAND_INVENTORY;
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 0;
        BackpackContext.Item context = new BackpackContext.Item(handlerName, slot);
        player.openMenu(new SimpleMenuProvider((w, p, pl) -> new BackpackContainer(w, pl, context), stack.getHoverName()), context::toBuffer);
    }
    return InteractionResultHolder.success(stack);
}
```

サーバ側でのみ開く。`SimpleMenuProvider` のラムダで `BackpackContainer` を生成し、`context::toBuffer`（`BackpackContext.java:48-51`）で `BackpackContext$Item` を `FriendlyByteBuf` に直列化してクライアントに渡す。クライアント側の `BackpackContainer.fromBuffer`（`BackpackContainer.java:117-119`）が `BackpackContext.fromBuffer(buffer, level)` で復元。

### Bundle-style insert/extract (overrideStackedOnOther/Me)

`BackpackItem.java:345-383` に Vanilla の `overrideStackedOnOther` / `overrideOtherStackedOnMe` を実装:

- `overrideStackedOnOther`（`:346`）: インベントリで backpack item を他スロットにクリックした時、`action == ClickAction.SECONDARY`（右クリック）かつ `storageStack.getCount() == 1` かつ slot から pickup 可能、かつ creative ピッカーで開いてない、を条件に、`stash()` で他スロット中身を吸い込む。
- `overrideOtherStackedOnMe`（`:370`）: backpack を他アイテム上にカーソルで持ってきた時、上記同条件で `stash()` してカーソルに余りを返す。
- `stash(ItemStack storageStack, ItemStack stack, boolean simulate)` (`BackpackItem.java:318-320`) は `BackpackWrapper.fromStack(storageStack).getInventoryForUpgradeProcessing().insertItem(stack, simulate)` を呼ぶ。
- `getInventoryTooltip()`（`:313-316`）が `BackpackContentsTooltip(stack)` を返し、`BackpackItem.java:339-343` の record が `TooltipComponent` を実装。ホバー時に中身プレビューが出る。

`IStashStorageItem.getItemStashable`（`:322-337`）で「空きがある / 同じアイテムでマッチがある / 空き無し」の 3 状態 (`StashResult.SPACE` / `MATCH_AND_SPACE` / `NO_SPACE`) を返し、HUD で点滅させる用。

---

## 3. BackpackContainer (Menu)

パス: `common/gui/BackpackContainer.java`、170 行。

### core の StorageContainerMenuBase をどう使ってるか

`extends StorageContainerMenuBase<IBackpackWrapper> implements ISyncedContainer`（`BackpackContainer.java:34`）。super のコンストラクタ呼び出し（`:37-38`）:

```
super(BACKPACK_CONTAINER_TYPE.get(), windowId, player,
      backpackContext.getBackpackWrapper(player),
      backpackContext.getParentBackpackWrapper(player).orElse(NoopStorageWrapper.INSTANCE),
      backpackContext.getBackpackSlotIndex(),
      backpackContext.shouldLockBackpackSlot(player));
```

`StorageContainerMenuBase`（`/tmp/mod-sources/SophisticatedCore/src/main/java/net/p3pp3rf1y/sophisticatedcore/common/gui/StorageContainerMenuBase.java`、1899 行）の責務:

- `addStorageInventorySlots()`（base `:249-297`）が `storageWrapper.getInventoryHandler().getSlots()` 数の `StorageInventorySlot` を生成して `addSlot` する。スロット数はラッパー任せ。
- `addPlayerInventorySlots`（base `:303-316`）が標準 36 player slot を追加。`shouldLockStorageItemSlot` が true なら開いた元 slot を pickup 禁止に。
- `getNumberOfRows()`（base `:348-350`）は `storageWrapper.getNumberOfSlotRows()` を返す。これが `BackpackWrapper.java:166-169` で「81 以下なら ceil(/9)、超えたら ceil(/12)」（つまり 81 を境に列数 9→12 に増やす）。
- `getInventorySlotsSize()` 経由で `lastRealSlots` / `remoteRealSlots` のリスト同期（packet 化のため）。
- upgrade slots は `instantiateUpgradeSlot` で生成、Backpack 側は `BackpackContainer.java:97-100` で `BackpackUpgradeSlot` に差し替え（`onUpgradeChanged` で context へ通知）。
- 検索フレーズ同期（`SEARCH_PHRASE_TAG`、base `:63`）、column 変更時の slot 再構築（base `:541` / `:1635`）等。

### backpack-specific な slot

- `BackpackUpgradeSlot`（`BackpackContainer.java:140-150`）: super の StorageUpgradeSlot を継承し、`onUpgradeChanged()` の最後に `backpackContext.onUpgradeChanged(player)` を呼ぶ。`BackpackContext$Item.onUpgradeChanged`（`BackpackContext.java:180-191`）が render info を `SyncClientInfoPayload` でクライアント送信。
- 開いた元の backpack slot の lock（`shouldLockBackpackSlot`、`BackpackContext.java:160-162`）。`PlayerInventoryProvider` で見えてる場合のみ lock。

### IBackpackWrapper の役割

`backpack/wrapper/IBackpackWrapper.java`、130 行、`extends IStorageWrapper`（`:17`）。Menu / Screen / Renderer から「現在の backpack の状態」にアクセスする唯一の窓口。

主要メソッド:
- `getBackpack()`（`:24`）: 元の ItemStack を返す
- `cloneBackpack()`（`:26`）
- `setSlotNumbers(int numberOfInventorySlots, int numberOfUpgradeSlots)`（`:30`） — Upgrade レシピで tier アップした時に呼ぶ
- `getContentsUuid()` / `setContentsUuid()` / `removeContentsUuid()`（`:42-44`） — UUID ライフサイクル
- `setTemplate(ResourceLocation templateName)` / `fillFromTemplate()`（`:34-36`） — datapack テンプレ
- `Noop.INSTANCE`（`:72-129`）: ヌルオブジェクトパターン。Wrapper が取れない時はこれを返す（Menu 側で NPE 回避、`BackpackContainer.java:38` で `parentBackpackWrapper.orElse(NoopStorageWrapper.INSTANCE)`）

実装の `BackpackWrapper.java:49-89` が遅延初期化フィールドを多数持ち、`getInventoryHandler()` (`:139-151`) / `getUpgradeHandler()` (`:261-289`) / `getSettingsHandler()` (`:249-258`) の都度 nullチェック。`StorageWrapperRepository` で同 UUID は同インスタンスを返すキャッシュ（`:100`）。

`BackpackContainer.java:40-42` で「Item-based context のとき」のみ `storageWrapper.onInit(player.level())` を呼ぶ（`IStorageWrapper.java:90-92` の default は `getInventoryHandler().onInit()`）。BlockEntity だと開く度の onInit は要らないため item context だけ。

---

## 4. BackpackScreen

パス: `client/gui/BackpackScreen.java`、65 行（驚くほど薄い）。

### core の StorageScreenBase をどう使ってるか

`extends StorageScreenBase<BackpackContainer> implements IBackpackScreen`（`BackpackScreen.java:17`）。Backpack 固有の追加は:

- `keyPressed`（`:27-46`）: Esc または backpack open キーで「親 backpack（=ITEM_BACKPACK）なら通常クローズ、sub backpack（=ITEM_SUB_BACKPACK）なら `BackpackOpenPayload` を投げて親を再オープン」。`getMenu().isFirstLevelStorage()` (`StorageContainerMenuBase.java:356-358` で `parentStorageWrapper == NoopStorageWrapper.INSTANCE`) で判定。
- `mouseNotOverBackpack`（`:48-51`）: hover slot が BackpackItem なら閉じない。
- `getStorageSettingsTabTooltip`（`:53-56`）: tooltip 翻訳キー差し替え。
- `render`（`:58-64`）: `getNumberOfStorageInventorySlots() == 0` なら自動 close。

検索 / scroll / tier display 等のメインロジックは **全部 core 側の `StorageScreenBase.java`**（1395 行）にある:

- **検索**: `StorageScreenBase.java:96` で `SearchBox searchBox` フィールド、`:290-300` で初期化、`:306-310` `onSearchPhraseChange()` でサーバに `setSearchPhrase` 同期、`:320-340` `updateSearchFilter()` が `@<modname>`, `#<tooltip>` のプレフィックス対応、`:100-101` `stackFilter` Predicate を slot 描画時に適用、`:300` `noResultsLabel` で「no results」表示。
- **scroll**: `:81` `InventoryScrollPanel inventoryScrollPanel`、`:358-371` `updateInventoryScrollPanel()` で「window 高さから計算した visible row 数 < menu の total row 数」のときだけ `InventoryScrollPanel` を生成。`InventoryScrollPanel`（`controls/InventoryScrollPanel.java`）は `extends ScrollPanel`（NeoForge 提供）で `getScrollAmount()=18`（`:38`、1 行 = 18px）、`getContentHeight()` (`:42-45`) で `rows*18` を返す。スロットの clip 描画と「マウスがどのスロット上にあるか」検出（`findSlot`、`:63-74`）。
- **dimensions/レイアウト**: `:131-150` `updateDimensionsAndSlotPositions(height)` で「row 数によって 9-列幅 / 12-列幅 / さらに scroll bar 分の 6px 右余白を追加」を切り替え。`:134` で 81 slot 境界で `StorageBackgroundProperties.REGULAR_9_SLOT` か `REGULAR_12_SLOT` か決定。
- **tier display**: 専用の tier 表示は無し。代わりに tier の差は「slot 数 + upgrade slot 数 + 色」で表現。tier ごとに別アイテム＝別 model（item registry name で texture path 解決）。BEWLR が `BackpackDynamicModel.BackpackBakedModel` を返してその model の中で tier ごとの texture を baked layer として表示する。

---

## 5. tier system (Basic/Copper/Iron/Gold/Diamond/Netherite)

### 6 tier の slot count 差の表現 (constructor 注入 pattern)

`init/ModItems.java:141-152` で 6 アイテムを登録（行ごとに 1 tier、`BackpackItem` を直接 `new`、`IntSupplier` で config 値を遅延参照）:

```
BACKPACK            : new BackpackItem(Config.SERVER.leatherBackpack.inventorySlotCount::get,
                                        Config.SERVER.leatherBackpack.upgradeSlotCount::get,
                                        ModBlocks.BACKPACK)
COPPER_BACKPACK     : leather → copper の差し替え
IRON_BACKPACK       : iron
GOLD_BACKPACK       : gold
DIAMOND_BACKPACK    : diamond
NETHERITE_BACKPACK  : Properties::fireResistant も追加 (BackpackItem.java:80-85 の updateProperties UnaryOperator 経由)
```

`BackpackItem` のコンストラクタ（`BackpackItem.java:76-85`）が `IntSupplier numberOfSlots, IntSupplier numberOfUpgradeSlots, Supplier<BackpackBlock> blockSupplier` を受け取り、フィールド保持（`:72-74`）。`getNumberOfSlots()` / `getNumberOfUpgradeSlots()`（`:284-290`）でいつでも config 現在値を返す。`stacksTo(1)` 固定（`:81`）。

### 各 tier の default slot 数 (Config)

`Config.java:151-156`:

```
leatherBackpack   = new BackpackConfig(builder, "Leather",   27, 1);
copperBackpack    = new BackpackConfig(builder, "Copper",    45, 1);
ironBackpack      = new BackpackConfig(builder, "Iron",      54, 2);
goldBackpack      = new BackpackConfig(builder, "Gold",      81, 3);
diamondBackpack   = new BackpackConfig(builder, "Diamond",  108, 5);
netheriteBackpack = new BackpackConfig(builder, "Netherite",120, 7);
```

`BackpackConfig`（`Config.java:375-381`）の中で:
- `inventorySlotCount = builder.defineInRange("inventorySlotCount", inventorySlotCountDefault, 1, 144);`
- `upgradeSlotCount   = builder.defineInRange("upgradeSlotCount",   upgradeSlotCountDefault, 0, 10);`

→ 最大 144 inventory slot / 最大 10 upgrade slot まで configurable。

### Recipe (BackpackUpgradeRecipe extends ShapedRecipe)

`crafting/BackpackUpgradeRecipe.java`、68 行:

- `extends ShapedRecipe implements IWrapperRecipe<ShapedRecipe>`（`:17`）。普通の Shaped recipe を「中央 slot に backpack item を置く」ような shape で書き、それを `BackpackUpgradeRecipe.Serializer` (`:63-67`、`RecipeWrapperSerializer<ShapedRecipe, BackpackUpgradeRecipe>` 経由) でラップ登録する。
- `assemble()`（`:36-45`）:
  1. `super.assemble()` で upgraded backpack item（=新 tier ItemStack）を取得
  2. `getBackpack(inv)` (`:47-55`) で「inv の中の BackpackItem instanceof」slot を見つけて、その既存 ItemStack の **全 DataComponents** を `upgradedBackpack.applyComponents(...)` で適用
  3. `BackpackWrapper.fromStack(upgradedBackpack)` → `wrapper.setSlotNumbers(newTier.getNumberOfSlots(), newTier.getNumberOfUpgradeSlots())` で slot 数の data component を新 tier の値に上書き
- `isSpecial() { return true; }`（`:31-33`） — recipe book で「これは表示しない / always re-evaluate」マーカー
- Serializer 登録: `ModItems.java:270` `BACKPACK_UPGRADE_RECIPE_SERIALIZER = RECIPE_SERIALIZERS.register("backpack_upgrade", BackpackUpgradeRecipe.Serializer::new)`
- Netherite だけは別の `SmithingBackpackUpgradeRecipe`（`crafting/SmithingBackpackUpgradeRecipe.java`、`ModItems.java:271`）。

### 内容物 transfer on upgrade

完全に上記 `applyComponents` だけで完結する。理由:
- 内容物実体は `BackpackStorage`（SavedData）の `Map<UUID, CompoundTag>` 側に置きっぱなし
- ItemStack に乗ってる `STORAGE_UUID` data component が `applyComponents` で新 ItemStack にコピーされる
- → 新 ItemStack が同じ UUID キーを持ち、同じ CompoundTag を引く
- 新 tier の slot 数だけ `setSlotNumbers` で `NUMBER_OF_INVENTORY_SLOTS` data component を書き換え
- 次に Menu を開いた瞬間 `BackpackWrapper.getInventoryHandler()`（`:139-151`）が「`numberOfSlots = data component の値 - columns*rows` で新規 `BackpackInventoryHandler`」を生成、既存の `Items` ListTag を読み直すので、容量だけ増えて中身そのまま。

→ NBT コピーや手動移し替え不要。`BackpackInventoryHandler` の `extends InventoryHandler extends ItemStackHandler` (`InventoryHandler.java:33`) は `setSize` をオーバライドして既存サイズ維持しつつ拡張対応（`InventoryHandler.java:78-81`）。

---

## 6. handheld 描画 (BackpackItemRenderer)

### BEWLR pattern

`client/render/BackpackItemStackRenderer.java`、48 行、`extends BlockEntityWithoutLevelRenderer`（`:18`）。

`renderByItem`（`:25-47`）の流れ:
1. `itemRenderer.getModel(stack, null, player, 0)` で baked model 取得
2. `model.getRenderPasses(stack, true)` で per-pass baked model を取り、各 RenderType ごとに `itemRenderer.renderModelLists(...)` で描画
3. `BackpackWrapper.fromStack(stack).getRenderInfo().getItemDisplayRenderInfo().getDisplayItem()` で「backpack に登録された display item（show-off）」があれば、`BackpackDynamicModel.BackpackBakedModel.getDisplayItemQuad()` から anchor quad を取得し、`DisplayItemAnchor.fromQuad(anchorQuad).applyTransform(poseStack)` でその quad の中心に回転＋平行移動して、display item を `ItemDisplayContext.FIXED` で重ね描画。

### chest slot バインド

2 段階:
1. **Item の slot 指定**: `BackpackItem.java:297-301` `getEquipmentSlot(ItemStack stack) { return EquipmentSlot.CHEST; }`。Curios 等の無い vanilla 環境でも `shift + 右クリック` で chest にスワップ可能になる。
2. **chest 装着時の体への描画**: `client/render/BackpackLayerRenderer.java` を `EntityRenderersEvent.RegisterRenderers` の中で `livingEntityRenderer.addLayer(new BackpackLayerRenderer(livingEntityRenderer))`（`ClientEventHandler.java:84, 96, 103`）として追加。chest slot の ItemStack が BackpackItem ならその layer が body の上にモデルを描画。

### BEWLR の登録

`client/ClientEventHandler.java:132-141`:

```
private static void registerBackpackClientExtension(RegisterClientExtensionsEvent event) {
    event.registerItem(new IClientItemExtensions() {
        private final Lazy<BlockEntityWithoutLevelRenderer> ister = Lazy.of(() -> new BackpackItemStackRenderer(...));
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() { return ister.get(); }
    }, ModItems.BACKPACK.get(), ModItems.COPPER_BACKPACK.get(), ModItems.IRON_BACKPACK.get(),
       ModItems.GOLD_BACKPACK.get(), ModItems.DIAMOND_BACKPACK.get(), ModItems.NETHERITE_BACKPACK.get());
}
```

`RegisterClientExtensionsEvent` (NeoForge 1.21.1 の正式 API) で 6 ティアまとめて 1 つの BEWLR を共有。

---

## 7. PEB (item-based, 単一 tier, 無限容量, 検索/scroll) に効くパターン 5+

PEB は「item-based」「単一 tier」「容量 256」「検索/scroll 必須」「内容物は enchanted book のみ」。直接適用 5+ パターン:

1. **「ItemStack の `STORAGE_UUID` (UUID Data Component) + サーバ側 `SavedData` の `Map<UUID, CompoundTag>`」3 層**（`BackpackWrapper.java:139-151` + `BackpackStorage.java:25, 32-43, 110-115`）
   - PEB でも 256 スロット分の本データを ItemStack に持たせると NBT 肥大 & ネットワーク帯域圧迫。UUID 参照は ItemStack を軽くしたまま無限容量に近づける王道。Vanilla の `DataComponents.CONTAINER` (`ItemContainerContents`) は最大 64 slot 制限なので 256 slot には使えない（ので必須）。

2. **`use(Level, Player, InteractionHand)` で `SimpleMenuProvider` + `BackpackContext::toBuffer`**（`BackpackItem.java:260-271` + `BackpackContext$Item.fromBuffer/addToBuffer` の `:213-223`）
   - 「どの手で持ってるか」(`MAIN_INVENTORY` / `OFFHAND_INVENTORY`) と「inventory slot index」をクライアント送信して、クライアント側で同じ ItemStack を slot から引き直す。menu 開いてる最中に別の手に持ち替えても誤動作しないようにできる。

3. **`overrideStackedOnOther` / `overrideOtherStackedOnMe` で右クリック bundle 風出し入れ**（`BackpackItem.java:345-383`）
   - 4 条件 (`hasCreativeScreenContainerOpen` でない / `count == 1` / slot の mayPickup-or-mayPlace / `action == ClickAction.SECONDARY`) のガードがそのまま使える。enchanted book を右クリックでサクッと吸い込めると UX 大幅向上。

4. **`addCreativeTabItems` で空 ItemStack を creative tab に並べる**（`BackpackItem.java:101-124`）
   - PEB なら「主要 enchant プリセット入りの ItemStack」を JEI 風に複数並べることもできるが、まず 1 個（空）でいいだろう。

5. **`getInventoryTooltip()` → record `BackpackContentsTooltip` → 別パスで `getTooltipImage()` でクライアント側 `ClientBackpackContentsTooltip` を返す TooltipComponent 分離パターン**（`BackpackItem.java:142-147, 313-316, 339-343`）
   - サーバ側ロジックとクライアント描画コードを `FMLEnvironment.dist.isClient()` で完全分離（`:143`）。1 サイドだけのクラスローダー安全性が保証される。PEB でも「ホバーで中身プレビュー」は需要が高い。

6. **`StorageContainerMenuBase` / `StorageScreenBase` を直接継承する余地が無い → 自前実装する場合のミニマル設計**
   - `BackpackScreen` がたった 65 行であることが示すように、検索 / scroll / sort / tab 等 重い部分は base 側に。PEB は core ライブラリに依存できないので、scroll / search を自前で書く必要がある。`InventoryScrollPanel`（`controls/InventoryScrollPanel.java:15-74`）が `extends ScrollPanel`（NeoForge 提供）で「`getScrollAmount()=18` / `getContentHeight()=rows*18` / `findSlot(mouseX, mouseY)` / `drawPanel()` で clip 内描画」だけで完結している ＝ NeoForge `ScrollPanel` 1 個 + `EditBox`（vanilla）1 個で MVP は組める。
   - 検索フィルタは `Predicate<ItemStack> stackFilter`（`StorageScreenBase.java:100-101`）の単純な `getHoverName().getString().toLowerCase().contains(query)` で OK。enchant 名で検索したいなら `EnchantmentHelper.getEnchantmentsForCrafting(stack)` の各 enchant の `getDescriptionId()` を結合した文字列に対して contains で。

7. **「`hasCreativeScreenContainerOpen` ガード」(`BackpackItem.java:385-387`)**
   - Creative inventory の ItemPicker menu を開いてる間は bundle 動作を無効化する。これを抜くと creative 検索画面でアイテムが消えるバグが出る。PEB でも忘れず実装すべき。

8. **`setSlotNumbers` でレシピアップグレード時に slot 数だけ書き換え、内容物は UUID 経由でそのまま継承**（`BackpackUpgradeRecipe.java:36-45` + `BackpackWrapper.java:438-441`）
   - PEB は単一 tier なので直接は使わないが、「もし将来 tier を増やす拡張をしても破壊的変更にならないように、最初から `NUMBER_OF_INVENTORY_SLOTS` data component を ItemStack に乗せる構造にしておく」価値はある。

---

## 8. 罠 / 注意点

1. **`BackpackStorage` は Overworld の `DimensionDataStorage` 専用**（`BackpackStorage.java:36-39`）。サーバ起動前 / overworld 未ロード時に `get()` を呼ぶと NPE 系で死ぬ。`Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER` チェック（`:33`）してから `ServerLifecycleHooks.getCurrentServer()` の null チェック（`:35-36`）も入っている。**PEB でも同じ saved data パターン採用なら、SERVER side でしか呼ばないように厳密に分離。クライアント側は `clientStorageCopy`（`:26, 42`）+ packet 同期。**

2. **`onDroppedByPlayer` で「自分が開いてる backpack だけドロップ拒否」**（`BackpackItem.java:292-295`）。これが無いと、自分が menu で開いてる元 ItemStack を `Q` で投げ捨てた瞬間に、開いてる menu と参照先 ItemStack が乖離して NPE / dup バグの温床になる。PEB でも `containerMenu` チェックでガード。

3. **`getBackpackContentsNbt()` (`BackpackWrapper.java:175-177`) が `getOrCreateContentsUuid()` を呼ぶ**。つまり「`getInventoryHandler()` を呼んだだけで UUID が ItemStack に書き込まれる」。検索や hover tooltip 表示のために安易に inventory handler を取ると、空 backpack に UUID が割り当てられ、`BackpackStorage` に空 entry が量産される。PEB で同パターンを採用するなら、「読み取り専用 access」と「mutation access」のメソッドを `IBackpackWrapper.fromExistingData(stack)` (`BackpackWrapper.java:111-117`) のように分けて、UUID 未設定なら `Optional.empty()` を返す API も用意する。

4. **`StorageWrapperRepository` のキャッシュ整合性**: 同じ UUID のラッパーは同じインスタンスを返す（`BackpackWrapper.java:100`）が、ItemStack 自体は menu open 毎に新オブジェクトの可能性。`setBackpackStack`（`:238-246`）で stack 参照を更新できるようになっている。**PEB で同等のキャッシュを作るなら、ItemStack 参照差し替え API を最初から用意しないと「menu 開く度に古い stack を見続ける」バグになる。**

5. **`onInit` の呼び出しタイミング**: `BackpackContainer.java:40-42` で `ITEM_BACKPACK` / `ITEM_SUB_BACKPACK` のときだけ `storageWrapper.onInit(level)` を呼ぶ。`IStorageWrapper.java:90-92` の default は `getInventoryHandler().onInit()`。block-based では `BackpackBlockEntity` の load 時に呼ぶため、menu open で 2 重呼びを避けるための分岐。**PEB は item only なので毎回 onInit でいいが、それを overload して loot 注入や migration を入れる場合は重複起動に注意。**

6. **`fillWithExtraItems` (`BackpackWrapper.java:466-481`) は `DataComponents.CONTAINER` を読んで消費する**。Vanilla bundle で先に何か詰めた状態で PEB アイテムが手に入った場合の互換移行が要る。**逆に「PEB が `DataComponents.CONTAINER` を持ち続けてはいけない」**（持ったまま放置すると bundle 表示・bundle 内容の dup / 消失バグ）。Menu 開く瞬間に必ず吸い込んで `backpack.remove(DataComponents.CONTAINER)` する。

7. **`canFitInsideContainerItems()` の挙動**: `BackpackItem.java:389-392` で config 値依存。`true` だと shulker box / bundle / 別 backpack に入れられるが、入れ子の同 UUID が「親 backpack の inventory tick で書き換え」されてカオスになる事例がある（→ `BackpackInventoryHandler.java:17-19` で `isAllowed` で「BackpackItem は基本拒否、inception upgrade あれば許可」と細かく制御）。**PEB は単純なら `canFitInsideContainerItems() = false` 固定で逃げる手も。**

8. **`shouldCauseReequipAnimation` を `slotChanged` のみで判定**（`BackpackItem.java:303-306`）。これを default の「stack の equals 比較」のままにすると、内容物変更の度に装着アニメーションが走って画面がブルブルする。PEB の chest slot 着用は無いが、同等の「ItemStack の変更検知」を menu 内で扱うなら slot 同期パケット (`StorageContainerMenuBase` の `lastRealSlots` / `remoteRealSlots` の 2 重バッファ、`:65-71`) を観察する価値あり。

9. **`hasCustomEntity` + `createEntity` で everlasting backpack entity に差し替え**（`BackpackItem.java:149-183`）。「everlasting upgrade があれば燃えない・despawn しない `EverlastingBackpackItemEntity` に置き換える」パターン。PEB で「ドロップしても消えない」要件があれば直接流用可能。

10. **`isSpecial() = true` を recipe につけないと、recipe book に「全 enchant data 入りの巨大 ItemStack」が登録されてクライアントの memory 食い潰し / packet サイズ膨張で death**。`BackpackUpgradeRecipe.java:31-33`。PEB のレシピでも同様の罠。

---

## 補足: コア API のキー定数 / DataComponent 一覧（PEB 設計時に再利用するもの）

- `ModCoreDataComponents.STORAGE_UUID` (`UUID`) … 内容物の SavedData キー
- `ModCoreDataComponents.NUMBER_OF_INVENTORY_SLOTS` (`Integer`)
- `ModCoreDataComponents.NUMBER_OF_UPGRADE_SLOTS` (`Integer`)
- `ModCoreDataComponents.MAIN_COLOR` / `ACCENT_COLOR` (`int`)
- `ModCoreDataComponents.OPEN_TAB_ID` (`int`)
- `ModCoreDataComponents.SORT_BY` (`enum SortBy`)
- vanilla `DataComponents.CONTAINER` (`ItemContainerContents`) … 容量 64 制限あり、移行用にだけ使う

これらは sophisticatedcore 側に定義（`/tmp/mod-sources/SophisticatedCore/src/main/java/net/p3pp3rf1y/sophisticatedcore/init/ModCoreDataComponents.java:36, 38, 46`）。PEB は core ライブラリに依存しないので、必要な component (`STORAGE_UUID`) を自分で `DeferredRegister<DataComponentType<?>>` で登録する。

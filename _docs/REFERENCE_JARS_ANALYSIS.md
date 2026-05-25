# Reference JARs Analysis (NeoForge 1.21.1)

PEB 実装の参考。pattern を抽出するためのメモ。全観察は bytecode (`javap -p`) と JSON を直接 grep で確認。

---

## Sophisticated Backpacks (3.25.41.1683)

### 全体構造
- `net.p3pp3rf1y.sophisticatedbackpacks.{backpack,common.gui,client.gui,crafting,init,upgrades}`
- **`sophisticatedcore` lib に強依存** (`[[dependencies]] modId="sophisticatedcore" type="required"`)。`StorageContainerMenuBase`, `InventoryHandler`, `UpgradeHandler` 等は core 側
- PEB は core を取り込めない → API 設計だけ学ぶ

### Tier 数 (確認済み)
- 確認した tier item: `BACKPACK` (base), `COPPER_BACKPACK`, `IRON_BACKPACK`, `GOLD_BACKPACK`, `DIAMOND_BACKPACK`, `NETHERITE_BACKPACK` = **6 tier** (kura prompt の「4 tier」は推測誤り)
- ただし PEB は wood/copper/iron/diamond = 4 で十分。SB は upgrade 体験を richにする為 6 tier

### Item 登録パターン
- `DeferredRegister<Item> ITEMS` (NeoForge 標準) + `DeferredHolder<Item, BackpackItem>` で型保持
- `BackpackItem` 1 クラスで全 tier を共有。tier 差は constructor 引数の **`IntSupplier numberOfSlots` / `IntSupplier numberOfUpgradeSlots`** で表現 (sub-class せず、コンストラクタ注入)
- → **PEB も `class BookshelfItem(slotCount: IntSupplier, ...)` 1 クラスで wood/copper/iron/diamond を作るのが筋がいい**

### NBT/Component
- **1.21 DataComponentType API を全面採用** (`ModDataComponents`)。NBT 直書きはしない
- 主要 component: `LOOT_TABLE` (ResourceLocation), `LOOT_FACTOR` (Float), `COLUMNS_TAKEN` (Integer), `ITEM_NAME` (String), `INVENTORY_ORDER` (custom enum)
- 大容量内容物本体は **外部 storage (`BackpackStorage`) + UUID 参照** に逃す (`getContentsUuid()`)。ItemStack 直書きしない
- 補助的に NBT も保持 (`getBackpackContentsNbt()` で CompoundTag 読む path あり、両用)

### GUI
- Menu: `BackpackContainer extends StorageContainerMenuBase<IBackpackWrapper> implements ISyncedContainer` (core 提供 base 利用)
- Screen: `BackpackScreen` + `BackpackSettingsScreen` (settings は別画面)
- **`BackpackContext` で「Item / Block / AnotherPlayer / *SubBackpack」** の 4 文脈を sealed-like enum で区別 → handheld と placed と他人 backpack を 1 つの menu で扱える設計

### Tier upgrade
- `BackpackUpgradeRecipe extends ShapedRecipe implements IWrapperRecipe<ShapedRecipe>` ← key pattern
- `assemble(CraftingInput, HolderLookup.Provider)` で grid から既存 backpack stack を見つけ、`IBackpackWrapper.fromStack(old).copyDataTo(new)` で内容物 transfer
- Recipe JSON: `"type": "sophisticatedbackpacks:backpack_upgrade"` + 標準 shaped pattern
- Smithing 版: `SmithingBackpackUpgradeRecipe` (netherite upgrade)

### handheld 時の見た目
- `BackpackItem.getEquipmentSlot()` で chest slot に bind 可能 (装備描画)
- 別途 `client/render/` 下に `BackpackItemRenderer` 系 (BEWLR pattern。深追いせず)
- PEB は curio/普通 item なので参考度低

### PEB 実装に流用できる pattern
- `IntSupplier` で tier 差を表現する constructor 注入 (slot 数を config に逃せる)
- `DataComponentType` ベースで NBT 直書きしない
- 大容量内容物は **外部 storage + UUID** で逃す (ItemStack コピー時の重さ回避)
- `Recipe extends ShapedRecipe` を override し `assemble()` で旧 stack の data 引き継ぎ
- `Context` enum で item/block/別 entity 文脈を 1 つの Menu で扱う

---

## Sophisticated Storage (1.5.43.1672)

### 全体構造
- `net.p3pp3rf1y.sophisticatedstorage.{block,common.gui,client.gui,crafting,init,item}`
- 同じく **`sophisticatedcore` 依存**

### Tier 数 (確認済み)
- Barrel/Chest tier: `barrel` → `iron_barrel` → ... (recipe `basic_tier_upgrade.json` `iron_barrel.json` 等で確認)。block 系なので PEB と直接は別

### Item 登録パターン
- `StorageBlockItem` / `BarrelBlockItem` / `ChestBlockItem` (block-item)
- **`StorageTierUpgradeItem(TierUpgrade tier)`** ← single item class、constructor で tier 種別注入
- `TierUpgradeDefinition<B extends BlockEntity>` abstract で `propertiesToCopy`, `newBlock`, `upgradeStorage()` を持つ ← **strategy pattern**

### NBT/Component
- `StackStorageWrapper extends StorageWrapper`、`fromStack(HolderLookup.Provider, ItemStack)` static factory
- `CONTENTS_TAG` で NBT 構造、`getContentsUuid()` で外部 storage 参照 (SB と同じ pattern)
- `ChestBlockItem.isDoubleChest(stack)` / `setDoubleChest(stack, boolean)` ← static helper で stack の状態取得 (PEB の `isOpen` 等 boolean state 用に流用可)

### GUI
- Menu/Screen は core 側 `StorageContainerMenuBase` を継承する pattern を再利用

### Tier upgrade
- **In-world block を「アイテム右クリック」で tier up**: `StorageTierUpgradeItem.onItemUseFirst(stack, UseOnContext)` → `tryUpgradeStorage(...)` → `TierUpgradeDefinition.upgradeStorage()`
- `propertiesToCopy` で `BlockState` の方向や open 状態を新 block に transfer
- Recipe JSON 不要 (in-world interaction で実現)
- これは PEB が item-only ならむしろ **「upgrade item を crafting せず使う」** 別 UX 案として参照可

### PEB 実装に流用できる pattern
- `TierUpgradeDefinition` 風 strategy class で「tier 間の差分」を 1 箇所にまとめる
- In-world upgrade (item 使用) vs Crafting upgrade どちらにするか選択肢
- `StackStorageWrapper.fromStack()` static factory pattern (ItemStack ↔ wrapper の boundary を明示)

---

## Carry On (2.2.4.4 / NeoForge 1.21.1)

### 全体構造
- `tschipp.carryon.{common.carry, client.render, client.modeloverride}`
- 軽量 (109 files)、独立 mod (lib 依存なし)

### Item 登録パターン
- **Item を追加しない**。player に AttachmentType でデータ attach する設計 (`CarryOnDataManager.getCarryData(Player)`)
- PEB は item 中心なので直接の参考にはならないが、**「持ち運び体験」を player attach data で表現する**選択肢の存在を知っておく

### NBT/Component
- `CarryOnData` クラスは **`Codec` + `StreamCodec<RegistryFriendlyByteBuf, CarryOnData>`** を持つ (1.21 標準)
- フィールド: `CarryType type`, `CompoundTag nbt`, `boolean keyPressed`, `CarryOnScript activeScript`, `int selectedSlot`
- `setBlock(BlockState, BlockEntity)` / `setEntity(Entity)` で何でも持てる

### GUI
- なし。inventory GUI を持たない mod

### handheld 時の見た目
- **`CarriedObjectRender.drawFirstPerson(player, MultiBufferSource, PoseStack, light, partialTick)`** で任意 block を first-person 視点で描画
- `drawThirdPerson(yaw, Matrix4f)` で third-person 側も
- `ModelOverride` で「block X を持つ時はアイテム Y/block Z を表示」の差替え定義可能 (config json で拡張)

### Tier upgrade
- 該当なし

### 依存 API
- vanilla + NeoForge のみ。クリーン

### PEB 実装に流用できる pattern
- `Codec` + `StreamCodec` で data class を serializable に (1.21 推奨スタイル)
- handheld 表示で 3D block model 出したい場合の `PoseStack` 直接操作の prior art (ただし PEB は普通 Item で十分なはず — `BakedModel` を `firstperson_righthand` perspective に 3D 化が正攻法)
- `ModelOverride` 風 config 駆動 model 差替えは PEB の「中身の本によって見た目変わる」案で参考に

---

## Curios API (9.5.1+1.21.1)

### 全体構造
- `top.theillusivec4.curios.api.{type.capability, type.inventory, type.data, client, event, extensions}`
- LGPL-3.0、外部 mod が依存しても問題ない (NeoForge soft-dep にする pattern)

### 外部 mod が Curios slot に item を登録する API
**2 つの方法 (どちらか好きな方)**

**方法 A: Item に `ICurioItem` を implement**
```java
class PEBItem extends Item implements ICurioItem {
    // 全 method が default impl 付き、override したいだけ書く
    @Override public void curioTick(SlotContext ctx, ItemStack stack) { ... }
    @Override public boolean canEquip(SlotContext ctx, ItemStack stack) { ... }
}
```

**方法 B: `ICurio` capability を ItemCapability 経由で expose**
```java
// CuriosCapability.ITEM は ItemCapability<ICurio, Void>
event.registerItem(CuriosCapability.ITEM, (stack, ctx) -> new MyCurio(stack), ModItems.PEB);
```

または runtime register:
```java
CuriosApi.registerCurio(item, ICurioItem instance);
```

### Slot 登録 (datapack 経由、no-code)
- `data/<modid>/curios/slots/<slot>.json` に 1 ファイル書くだけ
- 例 (`back.json`):
```json
{ "order": 80, "icon": "curios:slot/empty_back_slot", "validators": ["curios:tag"] }
```
- `validators: ["curios:tag"]` を入れると `curios:slot/<slot>` item tag で slot 制限可
- → **PEB を Curios 化したいだけなら**: ① `data/peb/curios/slots/back.json` (or 既存 back/charm を使う) ② item に `ICurioItem` implements ③ `data/peb/tags/item/curios/slot/back.json` で PEB item を tag 付け

### Pre-defined slots (`SlotTypePreset` enum)
- `HEAD` `NECKLACE` `BACK` `BODY` `BRACELET` `HANDS` `RING` `BELT` `CHARM` `CURIO`
- PEB は **`back` か `charm` が自然** (back: 体格物っぽさ、charm: 汎用)

### 主要 API entry: `CuriosApi`
- `getSlot(id, level)` → `Optional<ISlotType>`
- `getCurio(ItemStack)` → `Optional<ICurio>`
- `getCuriosInventory(LivingEntity)` → `Optional<ICuriosItemHandler>`
- `isStackValid(SlotContext, ItemStack)` → boolean
- `registerCurio(Item, ICurioItem)` (runtime register、deferred は推奨されない)

### Tooltip / sync
- `ICurioItem.writeSyncData()` / `readSyncData()` で server→client 同期 (PEB の本数表示等で必要なら)
- `ICurioItem.getSlotsTooltip(...)` で「装備可能 slot」を tooltip 表示

### PEB 実装に流用できる pattern
- **Curios 統合は超軽量**: soft-dep にして `class PEBItem implements ICurioItem` + slot json 1 個
- Cross-loader (Forge/NeoForge) では Curios、Fabric では Trinkets (別 API)。abstraction layer 書くか、loader 別実装
- `CuriosTags` で tag-based slot validator 使えば、PEB 以外の本棚系 mod も同じ slot に入る design 可能

---

## Inventory Profiles Next (2.2.1 / Fabric 1.21.1)

### 全体構造
- `org.anti_ad.mc.ipnext.{config, gui.inject, item, item.rule}`
- **Kotlin 製、Fabric 専用、AGPL-3 license** ← PEB (NeoForge) に直接持ち込めない。pattern level の参考のみ

### Sort 軸の列挙 (参考)
`SortingMethod` enum:
- `DEFAULT` / `ITEM_NAME` / `ITEM_ID` / `RAW_ID` / `ACCUMULATED_COUNT_DESCENDING` / `ACCUMULATED_COUNT_ASCENDING` / `CUSTOM`

→ **PEB の検索 GUI で「sort 軸」設計の素案**:
- エンチャント名 alphabet 順
- エンチャント ID 順 (mod 別にグルーピングされる)
- 冊数降順 (たくさん持ってる順)
- レベル降順 (高 level 順)
- カスタム順 (player 定義)

### Item type matching (参考)
- `ItemTypeMatcher.IsItem` / `ItemTypeMatcher.IsTag` で「item or tag」を sealed-like に表現
- PEB の filter 仕様で「Mending 本だけ」「Curse of ~ 系 tag だけ」のような matcher を作るときの pattern

### PEB 実装に流用できる pattern
- Sort 軸の enum 設計の発想 (実装コードは見ない、概念のみ)
- `ItemTypeMatcher` 風の sealed hierarchy で「単一エンチャント vs エンチャント tag」を扱う
- **コード/algorithm は AGPL なので直接参照しない**、enum 名や field 名のような UI 概念のみ拝借

---

## 横断的まとめ (PEB の決定に効く observation)

| 項目 | 採用案 | 根拠 |
|---|---|---|
| Item 登録 | `DeferredRegister<Item>` + `DeferredHolder` + 1 class で tier 差を constructor 注入 | SB の `BackpackItem(IntSupplier, ...)` pattern |
| データ保持 | `DataComponentType` (1.21 標準)。大容量は外部 storage + UUID | SB, SS 共通 |
| Tier upgrade | `Recipe extends ShapedRecipe` で `assemble()` override + 旧 stack の data copy | SB `BackpackUpgradeRecipe` 直参考 |
| Tier 戦略物 | `TierUpgradeDefinition` 風 strategy class で差分集約 | SS pattern |
| GUI Menu | `AbstractContainerMenu` 直継承 (sophisticatedcore lib は依存重すぎ) | PEB は自前で薄く書く |
| Curios 統合 | soft-dep、`implements ICurioItem` + slot json 1 個 | Curios API 設計 |
| Serialization | `Codec` + `StreamCodec<RegistryFriendlyByteBuf, T>` | Carry On 採用、1.21 標準 |
| Sort 軸 | enum で `BY_NAME` / `BY_COUNT` / `BY_LEVEL` 等を decide | IPN pattern (概念のみ) |

### 確証強度
- **強 (bytecode + json 確認済み)**: SB tier 6 個, SB の `BackpackUpgradeRecipe` が `ShapedRecipe` 継承、Curios `ICurioItem` 全 default method、Curios slot json schema、SS `StorageTierUpgradeItem` の in-world upgrade pattern
- **中 (signature から推測)**: SB の `copyDataTo` が内容物 transfer 用 (method 名と context から濃厚、実装 body は未読)
- **弱 (推測)**: PEB が `back` slot か `charm` slot に置くと自然 (これは設計判断、kura に確認推奨)

### PEB に効かなかった jar
- **InventoryProfilesNext**: AGPL + Kotlin + Fabric の三重苦で algorithm 参照不可。enum 命名と概念だけ
- **Carry On**: player attach data で item 不要の設計、PEB の方向性と直交。`Codec + StreamCodec` の serialization スタイルだけ

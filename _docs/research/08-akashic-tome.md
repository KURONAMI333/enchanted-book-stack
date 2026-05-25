# Akashic Tome 構造解析 (ItemStack 内 multi-item container)

ソース: `/tmp/mod-sources/AkashicTome/src/main/java/vazkii/akashictome/`
合計: 7 file (main) + 3 file (client) + 1 (data_components) + 3 (network) + 2 (proxy)、main package 653 行。
NeoForge 1.21.x 版（`net.neoforged.*` import 確認: `AkashicTome.java:3-7`）。

---

## 1. パッケージ構造

```
vazkii.akashictome/
├── AkashicTome.java                       (31 行)  : @Mod entry
├── AttachementRecipe.java                 (152 行) : クラフト統合（tome + 対象本）
├── ConfigHandler.java                     (102 行) : whitelist/blacklist/alias 設定
├── IModdedBook.java                       (  4 行) : マーカー interface
├── MorphingHandler.java                   (240 行) : morph/unmorph の core ロジック
├── Registries.java                        ( 35 行) : DeferredRegister + DataComponentType 5 個
├── TomeItem.java                          ( 89 行) : Item 本体・useOn/use/tooltip
├── client/
│   ├── AkashicTomeClient.java             (   ) : @Mod(dist=CLIENT)、CreativeTab 登録
│   ├── HUDHandler.java                    (   ) : ブロック注視中 HUD（自動 morph 候補表示）
│   └── TomeScreen.java                    (165 行) : grid GUI + 本 3D アニメ
├── data_components/
│   └── ToolContentComponent.java          ( 97 行) : List<ItemStack> + Codec/StreamCodec
├── network/
│   ├── MessageMorphTome.java              ( 57 行) : C→S morph 指定
│   ├── MessageUnmorphTome.java            ( 47 行) : C→S 左クリで強制 unmorph
│   └── NetworkHandler.java                ( 20 行) : Payload 登録 + sendToServer ラッパ
└── proxy/
    ├── ClientProxy.java                   : openTomeGUI 実装
    └── CommonProxy.java                   : NO-OP 親
```

`@Mod(dist=CLIENT)` の `AkashicTomeClient.java:81` と `proxy/` パターンの両方で client 分離（過渡期実装）。

---

## 2. AkashicTome (entry)

`AkashicTome.java:13-31`、わずか 31 行。

```java
@Mod(AkashicTome.MOD_ID)                                            // :13
public class AkashicTome {
    public static final String MOD_ID = "akashictome";              // :16
    public static CommonProxy proxy;                                // :17

    public AkashicTome(IEventBus bus, ModContainer modContainer, Dist dist) {  // :19
        bus.addListener(NetworkHandler::registerPayloadHandler);    // :20

        Registries.ITEMS.register(bus);                             // :22
        Registries.DATA_COMPONENTS.register(bus);                   // :23
        Registries.SERIALIZERS.register(bus);                       // :24

        modContainer.registerConfig(ModConfig.Type.COMMON, ConfigHandler.CONFIG_SPEC);  // :26

        proxy = dist.isClient() ? new ClientProxy() : new CommonProxy();  // :28
        proxy.preInit();                                            // :29
    }
}
```

ポイント:
- `Dist` を ctor 引数で受け取り proxy を分岐（NeoForge 1.21 では `DistExecutor` より素朴な分岐が公式推奨）
- DeferredRegister は 3 種（ITEMS / DATA_COMPONENTS / SERIALIZERS）。すべて event bus 登録は entry 1 行
- network 登録は `RegisterPayloadHandlersEvent` リスナとして bus に乗せる（`AkashicTome.java:20`）

---

## 3. Item クラス (`TomeItem.java`)

### 内容物保持 — DataComponent ベース

`TomeItem.java:22-26`:
```java
public TomeItem(Properties properties) {
    super(properties.stacksTo(1)                                    // :25
        .component(Registries.IS_MORPHED, false)
        .component(Registries.TOOL_CONTENT, ToolContentComponent.EMPTY));
}
```

- **`stacksTo(1)`**: ItemStack 内 container は必須（multi-stack できると state が分裂する）
- **既定値 component を Properties で注入**: `IS_MORPHED=false`、`TOOL_CONTENT=EMPTY`。空のときも component 自体は存在 → `stack.has(...)` で常に true を期待できる

### 操作

- `useOn(UseOnContext)` `:29-46`: shift + ブロッククリック → そのブロック由来 mod の本へ morph
  - `MorphingHandler.getModFromState(state)` でブロック → modid 引き、`getShiftStackForMod(stack, mod)` で新 ItemStack を作って `setItemInHand` で置き換え
  - 旧 stack の componentを書き換えるのではなく**新 stack で置換**する点が重要（後述）
- `use(Level, Player, InteractionHand)` `:49-53`: 右クリックで `AkashicTome.proxy.openTomeGUI(player, stack)` → クライアントで Screen 起動
- `appendHoverText` `:56-87`: shift 押下中だけ収納本リストを mod 名グループ化して表示

### 1.21 への移行 (data_components パッケージ)

NBT (`CompoundTag` の `getOrCreateTag()` 系) を一切使わない。すべて `DataComponentType<T>` で `stack.get(Registries.TOOL_CONTENT)` / `stack.set(...)` / `stack.has(...)` / `stack.remove(...)`。

`Registries.java:25-29` の DataComponentType は 5 個:
1. `TOOL_CONTENT` (`ToolContentComponent`) — 中身 List
2. `IS_MORPHED` (`Boolean`) — morph 中フラグ
3. `OG_DISPLAY_NAME` (`Component`) — 元アイテムの表示名
4. `DEFINED_MOD` (`String`) — 同じ mod の本を複数収納するための識別子（`mod_0`, `mod_1` で重複回避: `AttachementRecipe.java:77-80`）
5. `CUSTOM_TOME_NAME` (`Component`) — ユーザがリネームした本来名

すべて `.persistent(<Codec>).networkSynchronized(<StreamCodec>)` で**保存と同期を両方宣言**。`TOOL_CONTENT` のみ `.cacheEncoding()` 追加（`Registries.java:25`）— サイズが大きいので encode キャッシュで毎 tick の sync コスト削減。

---

## 4. ToolContentComponent

`data_components/ToolContentComponent.java:17-96`、97 行のシンプル data class。

### 構造

```java
final List<ItemStack> items;                                        // :25
public static final ToolContentComponent EMPTY = new ToolContentComponent(List.of());  // :18
```

`List<ItemStack>` を 1 個持つだけ。中身 ItemStack の **数・順序・component すべてを保存**できる（ItemStack.CODEC 経由）。

### Codec / StreamCodec

`:19-24`:
```java
public static final Codec<ToolContentComponent> CODEC = ItemStack.CODEC
    .listOf()
    .flatXmap(ToolContentComponent::checkAndCreate, component -> DataResult.success(component.items));

public static final StreamCodec<RegistryFriendlyByteBuf, ToolContentComponent> STREAM_CODEC =
    ItemStack.STREAM_CODEC
    .apply(ByteBufCodecs.list())
    .map(ToolContentComponent::new, component -> component.items);
```

- **`ItemStack.CODEC.listOf()`** で永続化 → `level.dat` / chunk セーブに自動シリアライズ
- **`ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list())`** で network 同期。`RegistryFriendlyByteBuf` を使うので registry-aware（item が registry holder で送られる）
- `flatXmap` (validate 可能) vs `map` (失敗しないなら map で十分) を使い分け

### immutable update pattern

公開フィールド `items` は `final List<ItemStack>`（unmodifiable ではないが setter なし）。更新は内側 `Mutable` クラスで:

```java
public static class Mutable {                                       // :75
    private final List<ItemStack> items;
    public Mutable(ToolContentComponent component) { this.items = new ArrayList<>(component.items); }  // :79
    public void tryInsert(ItemStack stack) {                        // :82
        if (!stack.isEmpty()) {
            ItemStack itemstack1 = stack.copy();                    // :84  ← copy 必須
            this.items.add(itemstack1);
        }
    }
    public void remove(ItemStack stack) { this.items.remove(stack); }  // :89-91
    public ToolContentComponent toImmutable() {
        return new ToolContentComponent(List.copyOf(this.items));   // :94  ← copyOf で immutable
    }
}
```

呼び出し側パターン（`MorphingHandler.java:54-57` 等）:
```java
ToolContentComponent.Mutable mutable = new ToolContentComponent.Mutable(contents);
mutable.remove(stack);
stack.set(Registries.TOOL_CONTENT, mutable.toImmutable());
```

DataComponent は**不変前提**なので、必ず new component を `set` で書き戻す。

### equals/hashCode

`:57-68`:
```java
public boolean equals(Object object) {
    return object instanceof ToolContentComponent c && ItemStack.listMatches(this.items, c.items);
}
public int hashCode() { return ItemStack.hashStackList(this.items); }
```

ItemStack の `listMatches` / `hashStackList` 静的を使う。**自前で書かない**。これがないと vanilla の `ItemStack.isSameItemSameComponents` が壊れる。

---

## 5. MorphingHandler (`MorphingHandler.java`)

morph = ItemStack の見た目を別 mod の本に切り替えること。240 行で全 morph ロジック。

### 中核: `makeMorphedStack` (`:121-181`)

入力 `(currentStack, targetMod, calledOnRemove)`、出力 = 新 ItemStack（差し替え用）。流れ:

1. `currentStack.copy()` で元を破壊しない (`:122`)
2. 現 stack の mod 判定 — `getModFromStack` か `DEFINED_MOD` component 優先 (`:123-132`)
3. `TOOL_CONTENT` 取得 → 元 stack を **`remove(TOOL_CONTENT)` してから content の 1 要素として `List.of(currentStack)`** に変換 (`:134-136`)
4. `Mutable` を作り、現 mod ≠ minecraft かつ ≠ akashictome かつ remove 呼び出しでないなら**現持ち stack を中に押し戻す** (`:194-200`)
5. target = "minecraft" なら素の TOME へ戻す、それ以外なら content から target mod の stack を引いて使う (`:143-154`)
6. mutable から target を `remove`、残りを新 stack の `TOOL_CONTENT` にセット、`IS_MORPHED=true` (`:156-159`)
7. 表示名を OG_DISPLAY_NAME を退避しつつ「親名 (現本名)」形式で `CUSTOM_NAME` にセット (`:161-177`)

つまり「持ち替え」= 古い見た目 stack を内部にしまい、内部から新しい mod 本を取り出して見た目を入れ替える、というスワップ操作。

### 持ち方 (selected slot 切り替え) — 実は slot index は持たない

PEB の「selection」とは設計が違う。Akashic Tome は **selected を持たず、その瞬間表に出ている stack 自身がそのまま inventory に存在する**。手から落とすとそれだけが落ちる仕様 (`ItemTossEvent` 処理 `:40-75`):

- shift で drop した時、 `TOOL_CONTENT` から自分自身を除いた状態を元の tome に戻し (`:54-57`)
- 元 tome を minecraft 形態に morph した stack を新規生成して落とす (`:59-64`)
- 落とす方の copy からは `TOOL_CONTENT` / `IS_MORPHED` / `CUSTOM_NAME` / `OG_DISPLAY_NAME` / `DEFINED_MOD` を `remove` してプレーンな本に戻す (`:66-73`)

→ **「現在 selected」= プレイヤー手持ち ItemStack 自身**、収納物 = TOOL_CONTENT の List。これは PEB の「ItemStack 内 ItemStack list + selection」とは選択モデルが違うので注意。

### unmorph / 左クリック空振り

`:32-38`:
```java
@SubscribeEvent
public void onPlayerLeftClick(PlayerInteractEvent.LeftClickEmpty event) {
    ItemStack stack = event.getItemStack();
    if (!stack.isEmpty() && isAkashicTome(stack) && !stack.is(Registries.TOME.get())) {
        NetworkHandler.sendToServer(new MessageUnmorphTome());
    }
}
```

morph 中の本を空中左クリ → unmorph パケット送信 → サーバが minecraft 形態へ戻す (`MessageUnmorphTome.java:33-44`)。**stack 引数なし**で payload を送り、サーバは `player.getMainHandItem()` を見て処理する（client-side stack を信用しない設計）。

### `isAkashicTome` 判定 (`:230-238`)

```java
public static boolean isAkashicTome(ItemStack stack) {
    if (stack.isEmpty()) return false;
    if (stack.is(Registries.TOME.get())) return true;
    return stack.has(Registries.IS_MORPHED) && Boolean.TRUE.equals(stack.get(Registries.IS_MORPHED));
}
```

**morph 中は別 item 名（target mod の本）になる**ので、`IS_MORPHED` component の存在で逆引きする。これが「ItemStack を別 mod の任意 item に化けさせる」核心トリック。

---

## 6. TomeScreen (`client/TomeScreen.java:35-165`)

`Screen` 直接継承（`AbstractContainerScreen` でなく、`MenuType` も使わない）。**slot は持たず、grid 描画 + クリック検出のみ**。

### 描画レイアウト (`:82-99`)

```java
int amountPerRow = 6;                                               // :86
int rows = stacks.size() / amountPerRow + 1;                        // :87
int iconSize = 20;                                                  // :88
int startX = centerX - (amountPerRow * iconSize) / 2;               // :90
int startY = centerY - (rows * iconSize) + 45;                      // :91
```

6 列固定、行は中身数で可変。背景は半透明黒の二重 fill (`:98-99`)。

### selection + tooltip (`:101-137`)

カーソル位置に応じて hover stack を判定:
```java
if (mouseX > x && mouseY > y && mouseX <= (x + 16) && mouseY <= (y + 16)) {
    tooltipStack = stack;
    y -= 2;                                                          // :111  ← 浮き上がり演出
}
```
hover 中の stack のために `Registries.DEFINED_MOD` を読み (`:129-131`)、それを `this.definedMod` に格納。

### クリック → ネットワーク送信 (`:50-58`)

```java
public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (button == 0 && this.definedMod != null) {
        NetworkHandler.sendToServer(new MessageMorphTome(this.definedMod));
        this.minecraft.setScreen(null);
        return true;
    }
}
```
描画 phase で hover 判定済みなので、クリックハンドラは何が選ばれてるか確認するだけ。**Screen を閉じてからサーバ応答を待たない**（楽観 UI）。

### 3D 本アニメ (`:139-161`)

`BookModel` + `Lighting.setupForEntityInInventory()` + 回転 PoseStack で本のページめくり描画。PEB に流用するなら不要。

### 注意点

- `MenuType` を使わないので **server に inventory contract を持たない**。クライアントは `tome.copy()` をスナップショットとして握り、操作はパケット 1 発で完結（`TomeScreen.java:45` の `this.tome = tome.copy()`）。スロットドラッグやアイテム出し入れには不向き。
- 256 slot のような数を扱うなら `AbstractContainerScreen` + `AbstractContainerMenu` + `Slot` ベースに置き換える必要がある。

---

## 7. Network

### NetworkHandler (`network/NetworkHandler.java:8-20`)

```java
public static void registerPayloadHandler(final RegisterPayloadHandlersEvent event) {
    final PayloadRegistrar registrar = event.registrar("1");        // :11
    registrar.playToServer(MessageMorphTome.ID, MessageMorphTome.CODEC, MessageMorphTome::handle);   // :13
    registrar.playToServer(MessageUnmorphTome.ID, MessageUnmorphTome.CODEC, MessageUnmorphTome::handle);  // :14
}

public static void sendToServer(CustomPacketPayload msg) {
    PacketDistributor.sendToServer(msg);                            // :18
}
```

- `registrar("1")` のバージョン文字列でプロトコル世代管理（client/server で一致しないと接続拒否）
- **両方とも `playToServer` のみ**。server → client への明示 sync は無く、ItemStack の component 同期は vanilla が自動で行う（`networkSynchronized()` 宣言の効果）

### MessageMorphTome (`MessageMorphTome.java:16-57`)

`record` で payload を実装:
```java
public record MessageMorphTome(String modid) implements CustomPacketPayload {  // :16
    public static final StreamCodec<FriendlyByteBuf, MessageMorphTome> CODEC =
        CustomPacketPayload.codec(MessageMorphTome::serialize, MessageMorphTome::new);  // :17-19
    public static final Type<MessageMorphTome> ID =
        new Type<>(ResourceLocation.fromNamespaceAndPath(AkashicTome.MOD_ID, "morph_tome"));  // :21
}
```

`handle` (`:37-56`) で `ctx.enqueueWork(() -> ...)` でメインスレッドに dispatch。main hand → off hand の順で TOME を探し (`:40-48`)、見つけたら `getShiftStackForMod` で morph、`setItemInHand` で置換。

### MessageUnmorphTome (`MessageUnmorphTome.java:15-46`)

引数なし record。`:38-41` でサーバ側が **`inventory.selected` を使い `inventory.setItem(selected, newStack)`** で書き戻す（`setItemInHand` でなく selected index で direct set）。直後 `proxy.updateEquippedItem()` でクライアントの「使用中アイテム」アニメをリセット（`ClientProxy.java:48-51` で `itemUsed(MAIN_HAND)`）。

---

## 8. PEB に効くパターン

### A. DataComponent ベースの List 保持

**仕組み**: `List<ItemStack>` を持つ自前 record / class を作り、`ItemStack.CODEC.listOf()` + `ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list())` で Codec/StreamCodec を組む。`DeferredRegister.createDataComponents` で型登録、Item の `Properties` で空既定値を `.component(...)` 注入。

**PEB への適用**: 256 slot 規模になるので `EnchantedBookContent` のような専用 component を作る。中身は `List<ItemStack>`（ItemStack 自体に enchanted_book の `stored_enchantments` component が乗る）か、enchantment 直接 List か選択。前者は vanilla の book item と相互運用しやすい。

**参考**: `ToolContentComponent.java:19-24`, `Registries.java:23-25`

### B. Mutable inner class による immutable 更新

DataComponent は不変前提。`Mutable` クラスを内部に置き、コピー → 変更 → `toImmutable()` → `stack.set(COMPONENT, immutable)` のシークエンスをパターン化。

**PEB への適用**: slot 操作（挿入・取り出し・並び替え）すべてこのパターンに統一。検索結果のフィルタ表示は `Mutable` を経由せず view-only に。

**参考**: `ToolContentComponent.java:75-95`

### C. cacheEncoding() で大容量 component の sync コスト削減

`Registries.java:25` の `.cacheEncoding()`。256 個の ItemStack を毎 tick 同期するとコストが大きい。`cacheEncoding` で encode 結果をキャッシュし、変更が無ければ再 encode しない。

**PEB への適用**: 256 slot の本 list には必須。アクセサリ用の小さい component (`SELECTED_INDEX: Int` など) には不要。

### D. ItemStack 自身が見た目を変える (morph) vs slot 内 view を別レイヤで持つ

Akashic Tome は **ItemStack そのものを target item の ItemStack に差し替える**（`MorphingHandler.makeMorphedStack:121-181`）。`IS_MORPHED` フラグで逆引き、`OG_DISPLAY_NAME` で元名退避、`setItemInHand` で hot-swap。

**PEB への適用**: PEB は通常本棚なので「見た目自体は不変」で OK。代わりに「selected slot」概念を持つなら、**`SELECTED_INDEX` を別 DataComponent にする**。これだけで GUI と Item を独立した同期にできる（list 全体を送り直さなくて済む）。

**参考**: `Registries.java:26` の `IS_MORPHED` 同型で `SELECTED_INDEX` を作る。

### E. Screen-only GUI + 単一パケット楽観 UI（small / static UI 用）

`TomeScreen.java:35-165` は `Screen` 直接継承、`MenuType` なし。クライアントは `stack.copy()` をスナップショットで握り、操作 1 アクション = 1 payload。Screen をすぐ閉じ、サーバ確認を待たない。

**PEB への適用**: **「検索バー + 256 slot」には不向き**。スロットドラッグ・shift+click ・スクロール・検索フィルタが入るなら `AbstractContainerMenu` + `AbstractContainerScreen` + `Slot` ベースが必須。Akashic 流の楽観 UI は本リスト「閲覧 + 1 click 選択」までが限度。

**反面教師として参考**: 256 slot で `Screen` 直書きすると同期と desync で詰む。

### F. `RegisterPayloadHandlersEvent` + record payload + `ctx.enqueueWork`

`NetworkHandler.java:10-15`、`MessageMorphTome.java:37-56` の payload registration と handler。NeoForge 1.21 の標準パターン。

**PEB への適用**: slot 操作（追加・取出・移動）、selected index 変更、検索クエリ送信は **すべて per-action payload** にする。`registrar("1")` のバージョン文字列で将来 breaking 変更時に互換切り。

### G. 操作系イベントを `EventBusSubscriber` でなく **instance + `NeoForge.EVENT_BUS.register(INSTANCE)`** で登録

`MorphingHandler` は `INSTANCE = new MorphingHandler()` のシングルトン (`MorphingHandler.java:28`) を `CommonProxy.preInit` (`:15-17`) で `NeoForge.EVENT_BUS.register(MorphingHandler.INSTANCE)` 登録。`@EventBusSubscriber` アノテーション式ではない。

**PEB への適用**: handler が internal state (cache 等) を持つなら instance 登録のほうが unit test しやすい。static @SubscribeEvent と混在させない。

### H. `getCreatorModId(stack)` + alias map で modid 解決

`MorphingHandler.java:81-96`:
```java
public static String getModFromStack(ItemStack stack) {
    String modId = stack.getItem().getCreatorModId(stack);          // :82
    return getModOrAlias(stack.isEmpty() ? MINECRAFT : modId != null ? modId : MINECRAFT);
}
```

`Item#getCreatorModId(stack)` で動的 mod 解決（registry ID と異なる場合あり）、それを alias map で正規化 (`:86-96`)。

**PEB には直接関係薄**（本棚は modid に依存しない）。ただし**MOD 横断的に "stored_enchantments" を持つ item を受け入れる**なら、registry id 列挙でなく `stack.has(DataComponents.STORED_ENCHANTMENTS)` 等の component 判定で受けるべき（modid フィルタは将来割れる）。

---

## 9. 罠 / 注意点

### a. `stack.copy()` を毎ステップで取る

`MorphingHandler.makeMorphedStack:122` で入口 copy、`:66` の drop 処理で copy、`ToolContentComponent.Mutable.tryInsert:84` でも copy。**書き換え対象は必ず copy してから set**。元 stack を直接弄ると inventory cache 経由で別 slot にも汚染が伝播するバグになる。

### b. component の `EMPTY` 既定値を Properties で必ず注入

`TomeItem.java:25` の `.component(Registries.TOOL_CONTENT, ToolContentComponent.EMPTY)`。これがないと `stack.get(...)` が null を返す箇所が出る。null チェックを書くより既定値で除外したほうが安全。

### c. morph 中は `stack.is(Registries.TOME.get())` が false

`MorphingHandler.isAkashicTome:234-237`。morph 中の本は target mod の item になる。判定は `IS_MORPHED` フラグで逆引き。PEB が「複数バリエーション」を持つなら同じトリックが要る。

### d. `setItemInHand` vs `inventory.setItem(inventory.selected, ...)`

`MessageMorphTome.java:52` は `setItemInHand`、`MessageUnmorphTome.java:40` は `inventory.setItem(inventory.selected, ...)`。前者は両手対応 + 検査済み、後者は selected index direct。**unmorph 時に hand 概念が壊れる**ため direct set にしているはず。PEB でも slot 直接書き換えが要る局面に注意。

### e. Screen を即閉じる楽観 UI で desync

`TomeScreen.java:53` の `this.minecraft.setScreen(null)` で送信即閉じ。サーバが reject しても client は既に閉じている。PEB のように slot 操作が多いと「サーバが拒否したのに client UI は更新済み」状態が起きる → `AbstractContainerMenu` の双方向 slot sync が必要。

### f. `ToolContentComponent.Mutable.remove(ItemStack)` は **参照比較ではなく ItemStack の equals**

`:90` の `this.items.remove(stack)` は `List#remove(Object)` 経由 → `ItemStack.equals` 比較。`stored_enchantments` 等で内容が同じ ItemStack を複数持つと**最初の 1 個だけ削除**されてしまう。PEB の本棚 200+ 冊だと事故りやすい。**index 指定の remove API を別に用意**するほうが安全。

### g. `cacheEncoding()` を入れるのは大容量だけ

小さい component (`IS_MORPHED: Boolean`、`DEFINED_MOD: String`) には未付与 (`Registries.java:26-28`)。キャッシュ自体にメモリオーバヘッドがあるため**目安は List/Map を含む component のみ**。

### h. `proxy/` パターンは NeoForge 1.21 では非推奨気味

`AkashicTomeClient.java:81` の `@Mod(value=MOD_ID, dist=Dist.CLIENT)` を別クラスで定義する公式パターンと、`proxy/ClientProxy.java` の旧パターン (1.16 系の名残) を**両方併用している**。新規プロジェクト (PEB) は `@Mod(dist=Dist.CLIENT)` 側に統一すべき (Forge → NeoForge で `DistExecutor` も deprecated 化)。

### i. アイテム drop 時の自己除去ロジック

`MorphingHandler.onItemDropped:40-75` は morph 中の本を **shift+drop** したとき、その本だけ落とし、tome 本体に「除去後の content」を残す挙動。PEB で「本棚から本を 1 冊取り出す」操作の参考にはなるが、`mutable.remove(stack)` の参照比較問題（罠 f）と同じく **index ベース API** にして再実装すべき。

### j. `stack.is(...)` と `MorphingHandler.isAkashicTome(...)` を混同しない

検査の意味が違う:
- `stack.is(Registries.TOME.get())` = **素の TOME**（未 morph）
- `MorphingHandler.isAkashicTome(stack)` = **TOME or morph 中のいずれか**

`MessageMorphTome.java:43-48` は前者、`MorphingHandler.onPlayerLeftClick:35` は後者。**PEB で同じ「2 段判定」を持つなら helper を明示分離する**こと。

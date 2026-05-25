# InvTweaks Refoxed 構造解析 (sort/filter, modern InventoryTweaks)

## 0. 重要な前提 (調査前に必読 / 認識論)

**ユーザーの依頼文と実物の不一致を最初に明示する**:

1. `/tmp/mod-sources/InvTweaks/` に置かれていたのは「**InvTweaks Refoxed (NeoForge 1.21.1)**」では**ない**。実物は次のいずれか:
   - パッケージ: `tacticalle.invtweaks` (`/tmp/mod-sources/InvTweaks/src/main/java/tacticalle/invtweaks/InvTweaks.java:1`)
   - mod id: `invtweaks` / mod name: `InvTweaks` / author: `Tacticalle` (`/tmp/mod-sources/InvTweaks/src/main/resources/fabric.mod.json:5-9`)
   - ローダー: **Fabric** (NeoForge ではない) (`/tmp/mod-sources/InvTweaks/src/main/resources/fabric.mod.json:30`)
   - MC ターゲット: **26.1.1** (1.21.1 ではない) (`/tmp/mod-sources/InvTweaks/gradle.properties:8`)
   - Java: **25** (`/tmp/mod-sources/InvTweaks/build.gradle:54-58`)
   - mod_version: `2.1.0` (`/tmp/mod-sources/InvTweaks/gradle.properties:11`)
   - README 一行目: "Better client-side inventory management for Minecraft (Fabric)" (`/tmp/mod-sources/InvTweaks/README.md:3`)

2. **機能の不一致**: ユーザーは「sort/filter 系」と想定したが、この mod は sort 系ではない。README "Features" 節 (`/tmp/mod-sources/InvTweaks/README.md:40-`) と全Javaファイル grep の結果、本 mod の機能は:
   - Modifier-key tweaks (Alt/Ctrl + click で all-but-1 / only-1 移動)
   - Shift-click 変種、Throw-half / Throw-all-but-1
   - **Scroll wheel transfer** (chest hover 中に scroll で同種アイテム転送) — PEB の scroll に最も近い
   - **Clipboard layout** (chest の中身配置をコピー/ペーストする system) — sort ではないが「現在の状態 → 目標状態へ click sequence で変換する algorithm」が含まれており、PEB の sort 実装に応用可能
   - Bundle insert/extract、Hotbar swap modifiers
   - "sort" / "Sort" の本格的 grep ヒットは `Collections.sort(...)` の使用のみ (insertion-order や rank-based tie-break)。**ユーザーが想定する"sort algorithm (NAME/LEVEL/COUNT/RECENT)"の DSL や config は存在しない** (`/tmp/mod-sources/InvTweaks/src/main/java/` 全文 grep 確認)。

3. **「Refoxed」名称**: README, fabric.mod.json, build.gradle, ソースコード全文どこにも "Refoxed" 文字列は出現しない。ユーザーが想定する別 mod (恐らく https://www.curseforge.com/minecraft/mc-mods/inventory-tweaks-renewed / inv-tweaks-refoxed) はこのソースには含まれていない。

→ 以下の解析は「**Tacticalle's InvTweaks v2.1.0 (Fabric, MC26.1, Java25)** を一次資料として」進める。PEB (NeoForge 1.21.1) に持ち込む際は API 差分 (Yarn vs Mojmap、ContainerInput vs ClickType、Inventory vs Container 等のクラス名) を別途必要に応じて調整する前提。

---

## 1. パッケージ構造

ファイル一覧と行数 (`find ... -name "*.java" | wc -l` → 16 ファイル / 計 8969 行):

```
/tmp/mod-sources/InvTweaks/src/main/java/tacticalle/invtweaks/
├── InvTweaks.java                        (   18 lines) ModInitializer エントリ (`InvTweaks.java:8`)
├── InvTweaksClient.java                  (  136 lines) ClientModInitializer + tick lifecycle + keybind 登録 + 死亡 detection
├── InvTweaksConfig.java                  (  460 lines) Config 全フィールド + GSON 永続化 + key resolution
├── InvTweaksConfigScreen.java            ( 1269 lines) GUI 設定画面
├── InvTweaksDataGenerator.java           (   11 lines) Fabric DataGen エントリ
├── InvTweaksModMenu.java                 (   11 lines) ModMenu integration
├── InvTweaksOverlay.java                 (  153 lines) 通知メッセージ overlay (右側 or 上部にフェード表示)
├── ContainerClassifier.java              (  109 lines) AbstractContainerMenu → enum (STANDARD/ENDER_CHEST/GRID9/CRAFTER/CRAFTING_TABLE/HOPPER/FURNACE/INCOMPATIBLE/PLAYER_ONLY) 分類
├── LayoutClipboard.java                  ( 3385 lines) ★中核★ snapshot capture + paste algorithm + undo + bundle layout
├── ClipboardStorage.java                 (  181 lines) JSON 永続化
├── ClipboardHistoryScreen.java           ( 1072 lines) クリップボード履歴 UI (タブ / scroll list / preview)
├── LayoutClipboard.java (再掲)
├── HalfSelectorOverlay.java              (  299 lines) 27↔54 mismatch 時の半分選択 UI
└── mixin/
    ├── AnvilScreenMixin.java             (   50 lines) Anvil text field 衝突回避
    ├── ClientPlayerMixin.java            (   66 lines) LocalPlayer.drop() フック (Q キーで半分ドロップ)
    ├── HandledScreenAccessor.java        (   20 lines) leftPos/topPos/imageWidth/imageHeight Accessor
    └── HandledScreenMixin.java           ( 1729 lines) ★中核★ AbstractContainerScreen への注入 (click/scroll/key)
```

Mixin 構成 (`/tmp/mod-sources/InvTweaks/src/main/resources/invtweaks.mixins.json`):
```json
{
  "required": true,
  "package": "tacticalle.invtweaks.mixin",
  "compatibilityLevel": "JAVA_25",
  "client": [
    "AnvilScreenMixin", "ClientPlayerMixin",
    "HandledScreenAccessor", "HandledScreenMixin"
  ],
  "injectors": { "defaultRequire": 1 }
}
```

Entrypoints (`/tmp/mod-sources/InvTweaks/src/main/resources/fabric.mod.json:13-26`):
- `main`: `tacticalle.invtweaks.InvTweaks` (LOGGER ログのみ、実体はクライアント)
- `client`: `tacticalle.invtweaks.InvTweaksClient`
- `modmenu`: `tacticalle.invtweaks.InvTweaksModMenu`

---

## 2. Sort 系

**結論: 本格的な sort algorithm (rule DSL, comparator chain) は存在しない**。"sort" の Java grep ヒットは以下のみ:

| file:line | 用途 |
|---|---|
| `ClipboardHistoryScreen.java:216` | `Collections.sort(realIndices, Collections.reverseOrder())` — 削除時 index を降順に並べる (削除中の index ずれ防止) |
| `ClipboardHistoryScreen.java:725` | `Collections.sort(sortedSlotIds)` — preview 描画時に slot id を昇順に並べる |
| `HalfSelectorOverlay.java:197` | `Collections.sort(sortedKeys)` — 半分選択 overlay の slot 順序 |
| `LayoutClipboard.java:1669` | `Collections.sort(clipboardSlotIdsSorted)` — paste 時 clipboard slot id → container slot id へ順序対応 |
| `LayoutClipboard.java:2940-2946` | **tier-based comparator** (後述、唯一の「ranking algorithm」) |
| `LayoutClipboard.java:3247, 3364` | clipboard key 昇順ソート (UI ordering) |
| `HandledScreenMixin.java:269` | partialStacks を slot id 昇順 (consistent fill order) |
| `HandledScreenMixin.java:1448-1453` | 変更スロットを `(slotId, count)` 順に sort (shift-click undo 時の matching) |

### 2.1 唯一の「ranking」: rankSourceSlots (PEB sort に直接転用可)

`LayoutClipboard.java:2864-2952` (`rankSourceSlots` overload, 中身 `2871-`)

**設計**: clipboard を paste する際、「desired item × target slot に対し、どの source slot から持ってくるか」を **tier 番号で多階層 priority 化** → 同 tier 内で 2次キーで tie-break。

中核ロジック (`LayoutClipboard.java:2935-2947`):
```java
candidates.add(new int[]{srcSlot, tier});
// ...
candidates.sort((a, b) -> {
    if (a[1] != b[1]) return a[1] - b[1];                                  // 1: tier 昇順
    boolean aIsPlayer = handler.slots.get(a[0]).container instanceof Inventory;
    boolean bIsPlayer = handler.slots.get(b[0]).container instanceof Inventory;
    if (aIsPlayer != bIsPlayer) return aIsPlayer ? 1 : -1;                 // 2: container 側を優先
    return a[0] - b[0];                                                    // 3: slot id 昇順
});
```

Tier の付け方 (`LayoutClipboard.java:2870-2940` 抜粋):
- tier 0/1: **exact identity match** (in-target-position / not)
- tier 2/3: **identity match (NBT components 完全一致)**
- tier 4/5: **name + type match**
- tier 6/7: **content-similar** (container/bundle の中身重複率)
- tier 8/9: **type-only match** (last resort)
- 偶数: 既に target position にいる (低コスト = 0クリックで済む) / 奇数: 移動が必要

**PEB への応用**: NAME/LEVEL/COUNT/RECENT sort は「tier ではなく直接 comparator」だが、本 pattern は「**複数キーを priority 順に並べて `(int[], comparator)` で 1 ステップソート**」という素直な API 設計の参考になる。後述 §5。

### 2.2 「Sort rule の DSL or config」

**存在しない**。InvTweaksConfig.java (460 行全体) に sort 関連フィールド・設定は皆無 (`InvTweaksConfig.java:33-97` のフィールド一覧参照)。設定可能なのは modifier key と enable/disable boolean のみ。

→ 本 mod から DSL の参考は得られない。PEB sort の DSL/config 設計は別ソース (例: 旧 InventoryTweaks 1.12 系の `InvTweaksTree.xml`) を参照する必要がある。

---

## 3. Container hook (vanilla 既存 Screen への注入)

**Sort button の追加は本 mod では行っていない** (button 自体無い)。代わりに `AbstractContainerScreen` の挙動を Mixin で全面的に書き換えている。PEB が「Bookshelf chest UI に sort button を追加」する場合は **Forge GuiContainerEvent** か **NeoForge ScreenEvent.Init.Post** を使うべきで、本 mod の pattern は flutter 直接の Mixin 注入なので NeoForge では別 API になる (Mixin 自体は使えるが、Forge のイベントの方が脆弱性が低い)。

### 3.1 click hook (HEAD + RETURN 両側注入の典型 pattern)

`HandledScreenMixin.java:65-67` (HEAD):
```java
@Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
        at = @At("HEAD"), cancellable = true)
private void beforeOnMouseClick(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) { ... }
```

`HandledScreenMixin.java:417-419` (RETURN):
```java
@Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
        at = @At("RETURN"))
private void afterOnMouseClick(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) { ... }
```

**設計上の重要 pattern**:
- **HEAD で snapshot を取り、cancellable で完全に vanilla を bypass するか / vanilla に走らせて RETURN で補正するか** を分岐 (`HandledScreenMixin.java:325-341`)。
- 例: `only1` mode は HEAD で `ci.cancel()` → 自分で clicks を組み立て (`HandledScreenMixin.java:325-345`)。`allbut1` mode は vanilla を走らせて RETURN で undo (`HandledScreenMixin.java:347-376` の snapshot 取得 → `HandledScreenMixin.java:422-428` で `it_handleShiftClick`)。
- **クリック合成は `MultiPlayerGameMode.handleContainerInput(containerId, slotId, button, ContainerInput.PICKUP, player)` を必要回数呼ぶだけ** (`HandledScreenMixin.java:125-129` 等、本ファイル中で 50回以上出現)。サーバーに正規プロトコルで送られるので vanilla server で動く。

### 3.2 vanilla cancel pattern (本 mod が PEB sort button に活かせる pattern)

`HandledScreenMixin.java:88-93` — modifier 押下時の `PICKUP_ALL` (double-click gather) ブロック:
```java
if (anyModDown) {
    if (actionType == ContainerInput.PICKUP_ALL) {
        ci.cancel();
        return;
    }
}
```

→ **PEB で sort button を押した瞬間 vanilla click を完全ブロックしてから sort routine を回す**、という pattern にそのまま転用可。

### 3.3 Slot 識別 (container side vs player side)

`HandledScreenMixin.java:1085` (scroll handler):
```java
boolean hoveredIsPlayer = hoveredSlot.container instanceof net.minecraft.world.entity.player.Inventory;
```

`LayoutClipboard.java:1654-1657` (paste handler):
```java
for (int i = 0; i < handler.slots.size(); i++) {
    Slot slot = handler.slots.get(i);
    if (!(slot.container instanceof Inventory)) {        // ← container 側 slot
        containerSlotIds.add(i);
    }
}
```

→ `slot.container instanceof Inventory` で player inventory 判定。`slot.getContainerSlot()` で inventory 内 index 取得 (`HandledScreenMixin.java:2963`)。**PEB の 256-slot UI で「自分の container slot だけ sort 対象、player inventory 側は触らない」を実装する際そのまま使える**。

### 3.4 Accessor mixin (leftPos/topPos 取得の定型)

`HandledScreenAccessor.java:1-19`:
```java
@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {
    @Accessor("leftPos")       int getX();
    @Accessor("topPos")        int getY();
    @Accessor("imageWidth")    int getBackgroundWidth();
    @Accessor("imageHeight")   int getBackgroundHeight();
}
```

→ PEB の overlay 描画で「container 画面の右側に出す」場合に必須の pattern。NeoForge 1.21.1 の Mojmap でも `leftPos`/`topPos`/`imageWidth`/`imageHeight` はそのまま存在する (`AbstractContainerScreen.class`).

---

## 4. Key bindings

### 4.1 登録 (Fabric API 経由)

`InvTweaksClient.java:35-40`:
```java
openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.invtweaks.open_config",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_K,
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("invtweaks", "category"))
));
```

→ NeoForge では `RegisterKeyMappingsEvent` (1.21.x) を使う。**本 mod のこの行は直接 NeoForge には移植不可** (Fabric API 限定)。

### 4.2 vanilla key conflict 回避 (実装の核心)

**興味深い設計判断**: Fabric の `KeyMapping.consumeClick()` を**わざと捨てる** (`InvTweaksClient.java:48`):
```java
// Drain Fabric KeyMapping queue (kept registered for Mod Menu integration) but don't act on it
while (openConfigKey.consumeClick()) { /* discard */ }
```

代わりに **GLFW 直接 polling**:
```java
// InvTweaksClient.java:50-58
InvTweaksConfig cfg = InvTweaksConfig.get();
boolean configKeyPressed = false;
if (cfg.openConfigKey != -1) {
    long windowHandle = client.getWindow().handle();
    boolean isDown = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, cfg.openConfigKey) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    if (isDown && !configKeyWasDown) { configKeyPressed = true; }
    configKeyWasDown = isDown;
}
```

**理由 (推測ではなく実装から読み取れる事実)**:
- KeyMapping は `screen != null` (GUI 開いている時) は click を消費しない (vanilla 仕様)。
- InvTweaks は GUI 内でも key を効かせたい (chest 中で K 押下で config を出す) → KeyMapping ではなく GLFW raw polling を選択。
- KeyMapping を残しているのは Mod Menu integration の表示のため (`InvTweaksClient.java:47` のコメント参照)。

### 4.3 Modifier 同時押し検出 (`isKeyPressed` 中核)

`InvTweaksConfig.java:267-274`:
```java
public static boolean isKeyPressed(int glfwKey) {
    long windowHandle = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
    if (GLFW.glfwGetKey(windowHandle, glfwKey) == GLFW.GLFW_PRESS) return true;
    int pair = getPairedKey(glfwKey);
    if (pair != -1 && GLFW.glfwGetKey(windowHandle, pair) == GLFW.GLFW_PRESS) return true;
    return false;
}
```

→ **左右変種 (LEFT_CTRL ↔ RIGHT_CTRL 等) を自動で両方チェック**。`getPairedKey` の 8 ケース定義は `InvTweaksConfig.java:319-330` に。

### 4.4 macOS 配慮 (Cmd 自動 fallback)

`InvTweaksConfig.java:33`:
```java
public int allBut1Key = isMacOS() ? GLFW.GLFW_KEY_LEFT_SUPER : GLFW.GLFW_KEY_LEFT_ALT;
```

`HandledScreenMixin.java:534-540` (copy/paste):
```java
boolean ctrlPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                      GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
boolean superPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS ||
                       GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS;
copyTriggered = (ctrlPressed || superPressed) && keyCode == GLFW.GLFW_KEY_C;
```

→ `Ctrl+C` も `Cmd+C` も同じく copy として扱う。

### 4.5 keyPressed Mixin (GUI 内キー hook)

`HandledScreenMixin.java:496-498`:
```java
@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
private void onKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) { ... }
```

`AnvilScreenMixin.java:18-20` (text field 入力との衝突回避):
- Anvil 画面では Ctrl+C/V/X が text field の標準動作と衝突 → **AnvilScreen に専用 Mixin を当て、これらキーが押されたら "Incompatible" overlay を出して `ci.setReturnValue(true)` で完全に握り潰す** (`AnvilScreenMixin.java:43-48`)。

→ **PEB で text-field 検索 UI を作る時の参考**: 検索 box 内では sort hotkey を吸わせない、というのは別 mixin を立てて完全 cancel するのが正解。

---

## 5. PEB の sort (NAME / LEVEL / COUNT / RECENT) に適用できる pattern

注意: PEB の sort UI 設計が PEB の SPEC.md にどう書いてあるかは未確認なので、ここでは「本 mod から借りられる部品」を列挙する。

### 5.1 「現在状態 → 目標状態 へ click sequence で変換」algorithm (★最重要★)

`LayoutClipboard.executePastePass` (`LayoutClipboard.java:1953-2078`) は **declarative target layout → click sequence** という汎用 algorithm:

入力: `Map<Integer, SlotData> targetLayout` (slot id → 何を入れたいか)
出力: vanilla server に送る `handleContainerInput(...)` 呼び出し列
副作用: 実 menu state が target に近づく

ステップ (`LayoutClipboard.java:1971-2074`):
1. 各 target slot について:
2. cursor が空でなければ一旦 dump (`LayoutClipboard.java:1974-1983`)
3. target が empty desired & 現在は何かある → `moveItemOut` で他所へ退避 (`LayoutClipboard.java:1991-2006`)
4. type & component match → 数だけ揃える (`addMore` `LayoutClipboard.java:2025-2034`)
5. 違うもの入ってる → 退避 (`moveItemOut`) → 置く (`sourceItem`/`sourceItemQuickMove`) (`LayoutClipboard.java:2039-2074`)

**PEB sort への直接転用**:
- PEB が「NAME 順に並べる」と決めたら、現在 inventory を読んで `desired = sortedBy(NAME)` を計算 → `targetLayout` を作る → `executePastePass` 相当を呼ぶ。
- メリット: クライアント側 mixin のみで完結、server に正規プロトコル送信。
- **PEB は item-stack 内の private container** なので、`AbstractContainerMenu.slots` ではなく PEB の専用 menu の slot 列挙になる。`handleContainerInput` で動くかは PEB menu が `ServerboundContainerClickPacket` 経由で動くかに依存。**もし PEB がサーバー側で完結 (1 packet で sort を要求し、サーバーが container を並べ替える) なら本 pattern は使わず、直接 `ItemStack` swap で済む**。

### 5.2 source ranking (multi-tier comparator)

`LayoutClipboard.rankSourceSlots` (`LayoutClipboard.java:2871-2952`) の tier ベース comparator は、PEB sort で「同じアイテムの中で priority 付けて並べる」場合の API 形に流用可:

```java
candidates.add(new int[]{slotId, tier});
candidates.sort((a, b) -> {
    if (a[1] != b[1]) return a[1] - b[1];
    // tie-break...
});
```

→ NAME sort で同名アイテムが複数ある時、(name, count, enchant level) の lex order で安定 sort、等。

### 5.3 cursor 退避 / temp slot 探索

`LayoutClipboard.findAnyEmptySlot` (`LayoutClipboard.java:2997-3014`), `findEmptyNonTargetSlot` (`LayoutClipboard.java:2970-2995`), `HandledScreenMixin.it_findEmptyPlayerSlot` (`HandledScreenMixin.java:1283-` 周辺) — sort 中に cursor が item を持ったまま行き場を失う場合のフォールバック。

PEB sort も「途中で cursor に乗ったアイテムを一時的にどこかに置く必要」が出るので参考になる。

### 5.4 Container 分類による sort 適用可否ゲート

`ContainerClassifier.classifyContainer` (`ContainerClassifier.java:41-89`) で `AbstractContainerMenu` の `instanceof` チェーンで category 判定し、INCOMPATIBLE は機能を全 cancel (`HandledScreenMixin.java:639-647`):

```java
if (it_containerCategory == ContainerCategory.INCOMPATIBLE) {
    if (copyTriggered || pasteTriggered || cutTriggered) {
        InvTweaksOverlay.show("Incompatible", 0xFFFF8800);
        cir.setReturnValue(true);
        return;
    }
}
```

→ **PEB sort button を vanilla chest 等にも追加する場合**: 「Anvil・Enchanting Table・Beacon は sort ボタン無効化」を category 列挙で実装すれば bug を予防できる。enum 一覧 (`ContainerClassifier.java:23-33`):
- `STANDARD` (chest/barrel/shulker), `ENDER_CHEST`, `GRID9` (dispenser/dropper), `CRAFTER`, `CRAFTING_TABLE`, `HOPPER`, `FURNACE`, `INCOMPATIBLE`, `PLAYER_ONLY`

### 5.5 Overlay (sort 後の通知メッセージ)

`InvTweaksOverlay.show("Layout pasted", 0xFF55FF55)` (`InvTweaksOverlay.java:43-50`) で 3秒表示+0.5秒フェード。位置決め (`InvTweaksOverlay.java:69-80`) は「container 右に余白あれば右、なければ上」と賢い。

→ PEB「ソートしました」「ソート対象がありません」等の通知に転用しやすい。`render` は `HandledScreenMixin.java:466-468` の `afterRenderMain` (extractContents TAIL inject) で呼ばれる。

### 5.6 GUI scroll list (256 slot scroll UI への hint)

`ClipboardHistoryScreen.HistoryEntryList extends ContainerObjectSelectionList<HistoryListEntry>` (`ClipboardHistoryScreen.java:889-906`) — vanilla の `ContainerObjectSelectionList` を直接拡張、`scrollBarX()` を override してスクロールバー位置を指定。

ただし**これは 1 dim の縦リストであり、PEB の 16x16 grid scroll UI への直接転用には不足**。`HalfSelectorOverlay.java:171-220` のほうが grid 描画の参考になる (GRID_COLS × GRID_ROWS で `slot id` を縦横に並べる: `HalfSelectorOverlay.java:199-218`)。

### 5.7 検索フィールド (search box)

**存在しない**。`EditBox` / `TextFieldWidget` の grep ヒット数 = **0** (全ソース確認済み)。

→ PEB が search box を作りたい場合、本 mod は参考にならない。vanilla の `CreativeModeInventoryScreen` (search tab) か外部 mod を参照する必要がある。

---

## 6. 罠 / 注意点

### 6.1 バージョン乖離 (最大の罠)

- 本 mod は **Fabric / MC 26.1.1 / Java 25** で書かれている (`gradle.properties:8,16`, `build.gradle:54`)。
- PEB は **NeoForge / MC 1.21.1 / 想定 Java 21** (PEB 側を未確認だがプロジェクト名が示唆)。
- 以下のクラス名/シグネチャは **1.21.1 では異なる**:
  - `net.minecraft.world.inventory.ContainerInput` enum (HandledScreenMixin で多用) → 1.21.1 では `net.minecraft.world.inventory.ClickType` enum (`PICKUP`/`QUICK_MOVE`/`SWAP`/`THROW`/`PICKUP_ALL` 等)
  - `GuiGraphicsExtractor` (`HandledScreenMixin.java:14`, `InvTweaksOverlay.java:5`) → 1.21.1 では `GuiGraphics`
  - `extractContents` method (`HandledScreenMixin.java:464`) → 1.21.1 では `render` or `renderBg`
  - `extractContent` (`ClipboardHistoryScreen.java:937`) → 1.21.1 では `render`
  - `extractRenderState` → 1.21.1 では `render`
  - `KeyEvent` パラメータ (`HandledScreenMixin.java:497`, `ClipboardHistoryScreen.java:834`) → 1.21.1 では `int keyCode, int scanCode, int modifiers`
  - `MouseButtonEvent` (`HandledScreenMixin.java:992`) → 1.21.1 では `double mouseX, double mouseY, int button`
  - `slotClicked` シグネチャ `(Slot, int, int, ContainerInput)` → 1.21.1 は `(Slot, int, int, ClickType)`
  - `Identifier.fromNamespaceAndPath` → 1.21.1 では `ResourceLocation.fromNamespaceAndPath` or `parse`
  - `Inventory` (`net.minecraft.world.entity.player.Inventory`) は同じ
  - `AbstractContainerScreen` / `AbstractContainerMenu` / `Slot` / `ItemStack` は同じ
  - `KeyMappingHelper` (Fabric API) → NeoForge では `RegisterKeyMappingsEvent`

→ **本 mod のコード片を NeoForge 1.21.1 にコピペすると 100% コンパイルエラー**。pattern として使い、API 名は 1.21.1 mojmap で書き直す必要がある。

### 6.2 GLFW raw polling の代償

`InvTweaksConfig.isKeyPressed` (`InvTweaksConfig.java:267-274`) は **window が unfocus でも値を返す** (GLFW の仕様)。実装では `client.screen == null` チェック等を組み合わせて副作用を抑えているが (`InvTweaksClient.java:60`)、PEB が同 pattern を取る場合は「フォーカス外で keybind が誤発火する」リスクに注意。Fabric KeyMapping や NeoForge `InputEvent.Key` を使うほうが安全だが、本 mod のように **GUI 内でも効かせたい hotkey** が要件なら GLFW polling を選択する設計判断は妥当。

### 6.3 macOS Cmd+Shift+Click bulk-move バグの回避

`HandledScreenMixin.java:301-318`:
> macOS fires QUICK_MOVE for ALL slots with matching items, not just the clicked one.
> Mouse Tweaks also fires QUICK_MOVE for multiple slots (shift-drag), but via reflection.

→ `hoveredSlot.index != slotId` かつ stack trace に `java.lang.reflect.` が**含まれない**場合は macOS の ghost event とみなして cancel する (`HandledScreenMixin.java:1325-1336` の `it_isCalledViaReflection`)。stack trace 検査による mod 互換性判定は **fragile** な hack だが、実用では効いている。PEB が click-driven sort を実装するなら同じ問題に当たる可能性。

### 6.4 mixin の `defaultRequire: 1` 強制

`invtweaks.mixins.json:8`:
```json
"injectors": { "defaultRequire": 1 }
```

→ 全 `@Inject` が「最低 1 回は inject 成立すること」を強制。マッピング変更で method 名が変わると `MixinTransformerError` で起動失敗。NeoForge 1.21.1 mojmap で書き直す時、`method = "slotClicked(...)V"` のシグネチャを正確に合わせる必要がある (intermediary 名と異なる)。

### 6.5 cursor 状態管理の難しさ

`LayoutClipboard.executePastePass:1974-1983` のように「mod が cursor を一時的に持つ → 想定外の場所に取り残されると inventory が壊れる」を防ぐため、安全策 (`findAnyEmptySlot` → 強制 dump) を随所に入れている。**PEB sort が click sequence 方式を取るなら、各 click 後に `menu.getCarried().isEmpty()` を確認・回復する仕組みが必須**。

### 6.6 INCOMPATIBLE container での text field 衝突

`AnvilScreen` 等 text field を持つ画面では Ctrl+C/V が衝突 → 専用 mixin で完全に握り潰す (`AnvilScreenMixin.java:42-49`)。PEB 検索 box 実装時に**同じ問題が発生する**。「検索 box にフォーカスがある時は sort hotkey を吸わない」を最初から組み込むべし。

### 6.7 paste algorithm の partial failure

`LayoutClipboard.PasteResult` (`LayoutClipboard.java:1498-1556`) は `success`/`partial`/`alreadyMatched`/`noClipboard`/`noMatchingItems`/`sizeMismatch`/`typeMismatch`/`quantityMaxOnly` の **8 種類の result enum** を持つ。「部分的に成功」を表現する必要が現実の inventory 操作にはある (slot 不足、type mismatch 等)。**PEB sort の result も同様に「全部 sort 出来た / 一部 sort 出来なかった (cursor 占有等) / NOOP (既に sort 済み)」を区別したほうがユーザー体験が良い**。

### 6.8 server tick との非同期

Mod は client tick (`ClientTickEvents.END_CLIENT_TICK` `InvTweaksClient.java:45`) で動くが、`handleContainerInput` の結果は server から戻る packet 受信後に menu に反映される。**1 click の直後に slot の最新状態を読むと古い値が返るリスク**がある — 実装では各 click 後 `menu.slots.get(slotId).getItem()` を再読み込みしているが、これは client 側の予測 (`predictionStorage`) に基づく値で、サーバー reject されれば desync する。InvTweaks は許容しているが、PEB sort で重要操作なら sync 対策を検討。

---

## 付録: ソースに無くて確認した事項

| 確認項目 | 結果 | 確認方法 |
|---|---|---|
| "Refoxed" 文字列 | 0 ヒット | 全ソース grep |
| "NeoForge" 文字列 | 0 ヒット (Fabric 専用) | build.gradle / fabric.mod.json |
| Sort algorithm DSL | 存在しない | InvTweaksConfig.java 全フィールド確認 |
| Search box (EditBox/TextFieldWidget) | 存在しない | 全ソース grep |
| InventoryTweaks 旧 mod との関連 | コード上関連性なし | パッケージ名 `tacticalle.invtweaks` (Tacticalle 個人作) |
| sort rule config file | 存在しない | resources 配下に config ファイル無し |
| NAME/LEVEL/COUNT/RECENT comparator | 存在しない | grep `Comparator` ヒット 0 |
| 256 slot UI 例 | 存在しない (履歴 list は ContainerObjectSelectionList の縦 1 列) | ClipboardHistoryScreen.java 構造確認 |

→ **PEB が想定する「sort/filter/256slot/search」のうち、本 mod から直接借りられるのは ① container hook の click/scroll/key mixin pattern、② executePastePass の "現在 → 目標" 変換 algorithm、③ rankSourceSlots の multi-tier comparator、④ overlay 通知、⑤ container classifier の disable gate のみ**。NAME/LEVEL/COUNT/RECENT の comparator と検索 UI と grid scroll は別ソース調査が必要。

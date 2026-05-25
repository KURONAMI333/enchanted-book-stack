# Inventory Profiles Next 構造解析 (sort/filter algorithm 学習、 AGPL コード copy 禁止)

調査対象: https://github.com/blackd/Inventory-Profiles (`README.md` 4行目で「開発は Codeberg に移動、 archived」と明記、 最終 mc 1.21.6 まで platform あり)。 ライセンス: AGPL-3.0 (`LICENSE`)。 本ドキュメント内のコード片はパターン理解のための極小引用のみ。

調査範囲: `platforms/shared-1.20.5+/src/main/{java,kotlin}/` + `platforms/neoforge-1.21/src/main/java/`。 NeoForge 1.21 PEB が直接参照しうるバージョン。

`api/` パッケージは `MIT License` 単独適用 (`IPNButton.java:1-25`)、 残り (`item/` `inventory/` `gui/` `input/` `parser/`) は AGPL。

---

## 1. パッケージ構造

shared 配下 (Kotlin / mc-version 共通):

| パス | 役割 | LoC概算 |
|---|---|---|
| `org/anti_ad/mc/ipnext/item/rule/` | sort rule の DSL/型/parser ターゲット | 868 (rule本体) |
| `org/anti_ad/mc/ipnext/item/rule/natives/` | 組込比較器 (`DefinedNativeRules.kt`, `NativeRule.kt`) | 158 + 455 |
| `org/anti_ad/mc/ipnext/item/rule/parameter/` | rule引数 / matcher (`ItemTypeMatcher.kt`, `NativeParameters.kt`) | 69 + 127 |
| `org/anti_ad/mc/ipnext/item/rule/file/` | rule定義ファイルから生RuleへBind | 71 + 158 + 208 |
| `org/anti_ad/mc/ipnext/parser/` | ANTLR4 ベース `.txt` パーサ | 484 (Loader) + 93 (Parser) |
| `org/anti_ad/mc/ipnext/inventory/action/` | `sort()`, `moveAllTo`, `restockFrom`, `PostActions` | 199+252+254 |
| `org/anti_ad/mc/ipnext/inventory/` | `AdvancedContainer`, `AreaTypes`, `ContainerClicker`, `GeneralInventoryActions` | 244+383+404+459 |
| `org/anti_ad/mc/ipnext/inventory/sandbox/` | 仮想click sandbox + diffcalculator (vanilla server 互換) | (今回未深掘) |
| `org/anti_ad/mc/ipnext/inventory/data/` | `ItemTracker`, `MutableSubTracker`, `ItemBucket` | - |
| `org/anti_ad/mc/ipnext/gui/inject/` | vanilla `ContainerScreen` 横に widget 追加 | 2342 |
| `org/anti_ad/mc/ipnext/gui/inject/base/` | sort/profile button widget | - |
| `org/anti_ad/mc/ipnext/input/` | `InputHandler`, `InventoryInputHandler` (hotkey dispatch) | - |
| `org/anti_ad/mc/ipnext/event/` | autorefill, lockslots, sounds, mouse trace | - |
| `org/anti_ad/mc/ipnext/config/` | Configs / Settings / Enums | - |
| `org/anti_ad/mc/ipn/api/` (Java, MIT) | `IPNButton`, `IPNGuiHint`, `IPNIgnore` 等 他mod 向けの hint API | - |
| `org/anti_ad/mc/ipn/features/scrolling/` | `ScrollingUtils.kt` ホイールで chest↔player の転送 | - |

neoforge-1.21 platform:

- `platforms/neoforge-1.21/src/main/java/org/anti_ad/mc/ipnext/mixin/MixinAbstractContainerScreen.java` — `slotClicked` HEAD/TAIL inject (ロックslot 妨害用)
- `platforms/neoforge-1.21/src/main/java/org/anti_ad/mc/ipnext/neoforge/ForgeEventHandler.java` — `@SubscribeEvent` で `ScreenEvent.Init.Post`, `ScreenEvent.Render.Pre/Post`, `ContainerScreenEvent.Render.Background/Foreground` を受け、 shared 側 `ScreenEventHandler` / `ContainerScreenEventHandler` に橋渡し
- `mixins.ipnext.json` (file:14-18) は 14個の client mixin を宣言、 ただし「画面 widget 挿入」は mixin ではなく **NeoForge ScreenEvent (`ScreenEvent.Init.Post`)** 経由 (`ForgeEventHandler.java:85-90`)

PEB 着目: widget inject は **mixin不要**で `ScreenEvent.Init.Post` だけで足りる、 が IPN の確認。

---

## 2. Sort 系

### 2-1. SortingMethod enum (`config/ConfigEnums.kt:29-39`)

```kotlin
enum class SortingMethod(val ruleName: String?) {
    DEFAULT("default"),
    ITEM_NAME("item_name"),
    ITEM_ID("item_id"),
    RAW_ID("raw_id"),
    ACCUMULATED_COUNT_DESCENDING("accumulated_count_descending"),
    ACCUMULATED_COUNT_ASCENDING("accumulated_count_ascending"),
    CUSTOM(null)
}
```

各 enum値が **rule 名** (string) を持ち、 `RuleFileRegister.getCustomRuleOrEmpty(name)` で `.txt` 定義済み Rule を引く構造 (`config/ConfigEnumsExt.kt:36-38`)。 つまり **enum と algorithm は分離**、 algorithm 側は rule DSL に書く。

`SortingMethodIndividual` (`ConfigEnums.kt:41-58`) は per-button override 用、 `GLOBAL` で `ModSettings.SORT_ORDER.value.rule` を継承し、 ボタン3種 (REGULAR / IN_COLUMNS / IN_ROWS) ごとに別の rule + post-action を持てる (`Configs.kt:303-315`)。

### 2-2. PostAction enum (`config/ConfigEnums.kt:61-72`)

```
NONE, GROUP_IN_ROWS, GROUP_IN_COLUMNS, DISTRIBUTE_EVENLY, SHUFFLE, FILL_ONE, REVERSE
```

sort 後の 2次変形。 `inventory/action/SubTrackerActions.kt:204-220` で switch 適用。 `GROUP_IN_ROWS/COLUMNS` は矩形 (width×height = slots.size) 前提で `PostActions.group()` を呼び、 `GroupInColumnsCalculator` で各 item を「列にまとまる」配置を計算 (`PostActions.kt:50-78`)。

### 2-3. Rule 階層

- `Rule` interface (`item/rule/Rule.kt:40-46`): `Comparator<ItemType>` を extend、 `arguments: ArgumentMap` を持つ
- `BaseRule` (`Rule.kt:59-120`): 全 native rule の親、 `reverse` と `sub_rule` の 2引数を init で defineParameter
  - `lazyCompare` (`Rule.kt:94-115`): comparator 結果が0なら `sub_rule` に委譲、 `reverse=true` で `mul=-1` を掛ける = **comparator chain + 反転**を 1個の rule で表現
- `NativeRule` (`natives/NativeRule.kt:60`): native の親、 `TypeBasedRule<T>` (`NativeRule.kt:62-64`) は `valueOf: Rule.(ItemType) -> T` を持ち comparator はその T 同士の比較に reduce
- 3 native 派生:
  - `StringBasedRule` (`NativeRule.kt:66-104`): `Collator + LogicalStringComparator` で文字列比較、 `blank_string` で空白の前後扱いを `Match.FIRST/LAST` で制御、 `string_compare = UNICODE / IGNORE_CASE / LOCALE` を選択
  - `NumberBasedRule` (`NativeRule.kt:106-124`): `NumberOrder.ASCENDING/DESCENDING` で `Double` 化して `Comparator`
  - `BooleanBasedRule` (`NativeRule.kt:126-156`): `match=FIRST/LAST` で「true がどっち端か」を決定、 さらに `sub_rule_match` / `sub_rule_not_match` で 2 グループ各々の中の sub-sort を委譲

### 2-4. カスタム sort rule の DSL (`.txt` ファイル)

文法サンプル (`resources/assets/inventoryprofilesnext/config/rules.txt:1-25`):

```
@default
    @creative_menu_order

@creative_menu_order
    ::custom_name
    ::search_tab_index
    @default_components_rule

@default_components_rule
    ::enchantments_tooltip_order
    ::damage
    ::display_name
    ::potion_effect
    ::components_comparator
```

- `@name` = **カスタム rule の宣言/参照**、 `::name` = **native rule の参照**
- インデント (4-space) で sub-rule の列を表現、 上から順に **辞書式 comparator chain**
- `::native(param=arg, param=arg)` で引数 (`RuleParser.kt:88-93`)
- 短縮形: 行先頭が `item_id` or `#tag_id` ならば `is_item` / `is_tag` rule に脱糖 (`RuleParser.kt:71-86`)

パーサ: ANTLR4。 文法は `platforms/shared-1.20.5+/src/main/antlr/org/anti_ad/mc/common/rules/` 下 (今回詳細未読、 grep済み存在確認)。 lexer `RulesLexer` の `mSubRule` モードで sub-rule 単独 parse もサポート (`RuleParser.kt:51-55`)。

### 2-5. sort 実行パイプライン (`inventory/action/SubTrackerActions.kt:154-189`)

```
MutableSubTracker.sort(rule, postAction, isRectangular, width, height)
  └── slots.sortItems(rule)
        ├── overstacked (maxCount超え個別保持) を index-map で退避
        ├── bucket = (this - overstacked).collect()       // ItemBucket = Map<ItemType, Int>
        ├── bucket.elementSet.toList().sortedWith(rule)   // ★ ItemType だけ sort
        ├── map { itemType -> itemType to pack(count, maxCount) }.flatten(slot数)
        └── overstacked を元 index へ復元
  └── postAction(GROUP_IN_ROWS等の再配置)
  └── writeTo(slots)                                       // index毎に itemType+count 上書き
```

学べる pattern:

1. **同 ItemType を bucket で 1回まとめる**: comparator は ItemType 集合内のみで呼ばれるため計算量は O(N×log(unique)) で済む
2. **overstacked 例外退避**: maxCount を超えた個別 stack (creative grabbed items) は index 固定で sort 対象外にし、 sort 後に index 復元
3. **pack(count, maxCount)**: count を maxCount 単位で分割して slot 列を生成 (= 自然な stack 化)
4. **sort と display は分離**: sort 結果は `List<ItemStack>`、 `writeTo` で MutableItemStack の itemType/count を上書き → 実際の slot click は別レイヤ (sandbox/ItemPlanner) が diff を出して click 列を生成 (`AdvancedContainer.kt:39-50`)

### 2-6. 比較器 chain による「multi-key sort」(`Rule.kt:94-115`)

```
compare(a, b):
  result = this.comparator(a, b) * reverse_mul
  if result != 0: return result
  return sub_rule.compare(a, b)
```

`@default_components_rule` のように **5つの native rule を順に試行** = 安定 multi-key sort。 PEB の NAME / LEVEL / COUNT / RECENT を同じ chain 構造に乗せられる。

---

## 3. Filter 系

**IPN 自体には「文字検索ボックス」は存在しない** (`lang/en_us.json` を `search` で grep しても creative menu 用の category 名のみヒット)。 filter 概念は2系統:

### 3-1. ItemTypeMatcher (sealed class) — id/tag による包含判定 (`parameter/ItemTypeMatcher.kt:30-58`)

```kotlin
sealed class ItemTypeMatcher {
    abstract fun match(itemType: ItemType): Boolean
    class IsTag(identifier: Identifier) : ItemTypeMatcher() { ... tag.contains(itemType.item) }
    class IsItem(identifier: Identifier) : ItemTypeMatcher() { ... itemType.item == item }
}
```

`lazy` で初回 match 時に Registry lookup、 不正な id は `Log.warn` して `false` を返す (例外で fail しない)。

PEB は本（書物）固定だが、 enchant 別にカテゴリ分けする時 (`stored_enchantments` の tag/id) に同 pattern が使える。

### 3-2. NBT-aware filter — `MatchNbtRule` / `ByNbtRule` (`natives/NativeRule.kt:158-204`, `213-309`)

- `MatchNbtRule` (BooleanBasedRule 派生): `component_id` で指定された data component の NBT を取り、 `NbtUtils.matchNbt(expected, actual)` で **部分一致** (expected の key 全部が actual にあって値も一致) を判定。 `allow_extra=false` なら **完全一致** (`matchNbtNoExtra`)。
- `ByNbtRule`: 同様だが `nbt_path` で深掘り、 取れた tag を type 別に compare:
  - `compareTag` (`NativeRule.kt:336-345`): isNumber → number、 isCompound → `NbtUtils.compareNbt`、 isList → element 毎 recursive、 else → string
  - tag が両方 null なら 0、 片方だけ null なら `not_found = FIRST/LAST` で位置決定
- `is_item` / `is_tag` (`DefinedNativeRules.kt:120-130`): `SimpleParameterBasedRule` で「item_id一致 AND require_nbt 条件一致 AND nbt 部分一致」を AND 合成

### 3-3. partial match algorithm — `compareByMatch` / `compareByMatchSeparate` (`NativeRule.kt:211-261`)

```
compareByMatch(v1, v2, matchBy, match, sameCompare):
  b1 = matchBy(v1); b2 = matchBy(v2)
  if b1 == b2: return sameCompare(v1, v2)
  else: return match.multiplier * (b1 ? -1 : 1)  // FIRST=+1, LAST=-1
```

「マッチした要素を先頭/末尾に固める」 という汎用 partition primitive。 `Boolean` 1bit を sort key の最上位 bit として使い、 同bit内は `sub_rule_match` / `sub_rule_not_match` で内部 sort。

**PEB 適用**: 「enchanted book を unenchanted book より前に出す」 「fortune III のような特定 enchant を先頭に固める」 が `BooleanBasedRule` + `compareByMatchSeparate` で表現できる。 自前の comparator を書く時にも `(matched, !matched, subCompare)` の 3引数化は使える。

### 3-4. ItemBucket — frequency-based filter (`inventory/data/ItemBucket.kt:30-83`)

`Bucket<ItemType>` = `Map<ItemType, Int>` 抽象。 `addAll(stacks)` で全 slot を 1 map に潰し、 `contains` で `count 以上含む?` の判定、 `minus` で差集合。 sort/move 系がほぼ全部この上で動く。

PEB 適用: 256slot 表示で「同名 enchanted book を merge して "count 個" 表示」する時、 ItemBucket と同じ「Map<ItemType, Int>」を持つだけで NAME sort / COUNT sort 両方に流用できる。

### 3-5. `ItemType` の `==` (filter の identity)

`item/ItemTypeExtensions.kt` (neoforge-1.21 platform 側) と `ItemType.kt` (shared) で `equals` を component map ベースに定義 (今回詳細未読、 ただし `ignoreDurability = true` を `copy()` でセットして「durability 違いを同一視」する箇所が `SubTrackerActions.kt:64-66`, `ScrollingUtils.kt:121` に複数存在)。 = **equality 軸も filter parameter**。 PEB で「同 book は enchant違いも 1 entry」 vs 「enchant別」 を切り替える時に同 pattern を採れる。

---

## 4. GUI injection (vanilla container に IPN UI を inject する pattern)

### 4-1. inject の入口 (`gui/inject/ContainerScreenEventHandler.kt:36-67`)

```kotlin
fun onScreenInit(target: ContainerScreen<*>, addWidget: (ClickableWidget) -> Unit) {
    if (target != Vanilla.screen()) return
    val hints = HintsManagerNG.getHints(target.javaClass)   // ★ class 別 hint 取得
    val ignore = hints.ignore
    val editor = EditorWidget(target, hints)
    val settings = SettingsWidget(target, hints)
    if (GuiSettings.ENABLE_INVENTORY_BUTTONS.booleanValue && !ignore) {
        widgetsToInset.add(SortingButtonCollectionWidget(target, hints))
    }
    if (GuiSettings.ENABLE_PROFILES_UI.booleanValue && !ignore && !hints.hintFor(IPNButton.PROFILE_SELECTOR).hide) {
        widgetsToInset.add(ProfilesUICollectionWidget(target, hints))
    }
    InsertWidgetHandler.insertWidget(currentWidgets)
}
```

### 4-2. NeoForge 側 trigger (`platforms/neoforge-1.21/.../ForgeEventHandler.java:84-90`)

```java
@SubscribeEvent
public void onInitGuiPost(Init.Post e) {
    ScreenEventHandler.INSTANCE.onScreenInit(e.getScreen(), x -> {
        e.addListener(x);
        return Unit.INSTANCE;
    });
}
```

`net.neoforged.neoforge.client.event.ScreenEvent.Init.Post` 1個で全 ContainerScreen サブクラスを捕捉。 mixin 不要。

### 4-3. 既存 Screen の hook 方法 — 3層パターン

1. **ScreenEvent.Init.Post** で「画面が初期化されたよ」を契機に IPN は `InsertableWidget` 群を `InsertWidgetHandler` に登録 (`InsertWidgetHandler.kt:38-46`)
2. **`InsertWidgetHandler` implements `ScreenEventListener`** が `mouseClicked` / `mouseRelease` / `keyPressed` / `mouseScrolled` / `resize` を `currentWidgets.forEach` で配布 (`InsertWidgetHandler.kt:54-103`)、 各 widget が `r = r || it.method()` で event 消費を返す
3. **`ContainerScreenEventHandler.onBackgroundRender` / `onForegroundRender`** で `ContainerScreenEvent.Render.{Background,Foreground}` 経由の描画を全 widget に配布 (`ContainerScreenEventHandler.kt:99-127`)

### 4-4. Widget の position — bounds は毎フレーム再計算

`SortingButtonCollectionWidget` (`gui/inject/SortingButtonCollectionWidget.kt:78-89`):

```kotlin
override fun postForegroundRender(...) {
    overflow = Overflow.VISIBLE
    absoluteBounds = screen.`(containerBounds)`     // ★ screen の container 矩形に合わせる
    init()
    super.render(...)
}
```

`screen.(containerBounds)` は accessor mixin で取得した getGuiLeft/Top/XSize/YSize の矩形 (`ingame/VanillaAccessors.kt`)。 widget はその矩形に対して `setTopRight` / `setBottomRight` で位置 (`gui/layout/`)。 → **resize/scale 変化に追従**。

### 4-5. search bar 追加位置

IPN は **search bar 自体を持たない** (lang grep 確認済み)。 ただし下記の知見が PEB の検索バーに転用可:

- `gui/inject/InsertWidgetHandler.kt:80-103` の `keyPressed` 配布が全 widget へ → search field を `Widget` 派生で作って `keyPressed` を消費 (return true) すれば、 vanilla の inventory hotkey (`E` 閉じる、 hotbar key) と干渉する場合は **`return true` で event を吸う**ことで衝突回避
- `gui/inject/EditorWidget.kt` (158行) は 「ボタン位置の drag 編集モード」用の sub-screen、 `BaseScreen` を別画面として `Vanilla.screen()` を差し替える方式 → search が複雑化する場合は **subscreen を開く** 選択肢もある
- `isInputFieldActive(scr)` (`InventoryInputHandler.kt:73`) — anvil の rename field / recipe book search field を **vanilla の TextFieldWidget refl 経由で検出**し、 active なら mod の hotkey 全停止。 PEB の検索 field を作る際は同様の field-active 判定で hotkey suppress すべき

---

## 5. Key bindings

### 5-1. 二段 dispatch (`input/InputHandler.kt:45-87`, `input/InventoryInputHandler.kt:35-90`)

```
GlobalInputHandler.register(InputHandler)            // 最上位
GlobalInputHandler.registerCancellable(CancellableInputHandler)

InputHandler.onInput:
  ModpackInputHandler → RELOAD_CUSTOM_CONFIGS → OPEN_GUI_EDITOR → InventoryInputHandler

InventoryInputHandler.onInput:
  if (scr is ContainerScreen<*> && !isInputFieldActive(scr)):
    Hotkeys.SORT_INVENTORY           run ::doSort
    || Hotkeys.SORT_INVENTORY_IN_COLUMNS run ::doSortInColumns
    || Hotkeys.SORT_INVENTORY_IN_ROWS    run ::doSortInRows
    || Hotkeys.MOVE_ALL_ITEMS            run ::doMoveMatch
    || ...
    || Hotkeys.SCROLL_TO_CHEST           run ::scrollToChest
    || Hotkeys.SCROLL_TO_INVENTORY       run ::scrollToPlayer
```

`||` の短絡で **最初に当たった hotkey で early-return** = 同キーに複数 binding が割り当てられても先勝ち。

### 5-2. KeybindSettings (context restriction)

`Configs.kt:368-379` で hotkey 定義時に `KeybindSettings.GUI_DEFAULT` / `GUI_EXTRA` / `INGAME_DEFAULT` / `ANY_DEFAULT` を渡す。 enum 自体は `platforms/shared-1.20.5+/` 下には無く、 別 module (malilib 系 common) で定義。 用途:

- `GUI_DEFAULT` — Screen 表示中のみ発火 (SORT_INVENTORY="R" など)
- `INGAME_DEFAULT` — Screen 閉じてる時だけ (OPEN_CONFIG_MENU="R,C")
- `GUI_EXTRA` — GUI内、 修飾 key として使う
- `ANY_DEFAULT` — どちらでも

→ vanilla の `KeyMapping` だけでは「Screen 表示時にだけ動く」 が表現しづらいので **自前 context enum** を持ち `IInputHandler.onInput` 内で screen 状態を見て branch する方式。 PEB の sort/filter キーも同じ要領でブックシェルフ Screen 表示時だけ反応させる pattern を採れる。

### 5-3. Hotkey 文字列 syntax (`Configs.kt:88-95`)

```
"R"            — 単 key
"R,T"          — 順次 chord (R 押下後 T)
"R,C"          — config menu trigger
"LEFT_SHIFT"   — modifier
"MOUSE_SCROLL_UP" — マウスホイール
""             — 未割当 (デフォルト)
```

modifier hotkey (`SCROLL_FULL_STACK = "LEFT_SHIFT"`) は「**他 hotkey の修飾子としてのみ機能**」する仕組み、 `KeybindSettings.GUI_EXTRA` がそれを示す。

### 5-4. vanilla key との conflict 回避

- `isInputFieldActive(scr)` で recipe book search / anvil rename 等の vanilla text field 入力中は IPN hotkey 全停止 (`InventoryInputHandler.kt:73`)
- `IMixinKeyBinding` (mixin) で `KeyMapping.matches` を hook、 IPN hotkey が活性中は vanilla 同キーを suppress (推定、 mixins.ipnext.json:7)
- `CancellableInputHandler` (`input/CancellableInputHandler.kt`) で **event をキャンセル**するルートを別途持つ
- マウスホイール: `SortButtonWidget.kt:147-155` (= `gui/inject/base/TexturedButtonWidget.kt:73-89`) で `mouseScrolled` を override し、 button hover 中に scroll した時だけ `onClick(MOUSE_SCROLL_UP/DOWN)` を発火。 vanilla の hotbar scroll とは「button 矩形上か否か」で物理的に分離

---

## 6. PEB の sort (NAME / LEVEL / COUNT / RECENT) に適用できる pattern

### 6-1. 4軸 = 4 native rule + 1 chain rule = OK

PEB 用 SortingMethod (enum) を `NAME / LEVEL / COUNT / RECENT` で持ち、 内部実装は **comparator chain**:

```
NAME chain:   display_name → enchantments_score → translation_key
LEVEL chain:  enchantments_score (DESC) → display_name → translation_key
COUNT chain:  accumulated_count (DESC) → display_name
RECENT chain: insertion_index (DESC, 自前 tracking) → display_name
```

各軸の primary key で 0 が返ったら secondary key へ落ちる = `BaseRule.lazyCompare` の `sub_rule` chain そのもの。 PEB は AGPL コード持ち込めないので、 自前で `Comparator<ItemStack>` の chain 構造を実装する (Java 8 `Comparator.thenComparing` で素直に書ける、 ANTLR や DSL は不要)。

### 6-2. NAME sort — Collator + 論理数字比較

`StringBasedRule` (`NativeRule.kt:78-93`) の lesson:

- `Collator.getInstance(locale).strength = PRIMARY` で大文字/小文字/アクセント差を吸収
- `LogicalStringComparator` でラップ → "Book 10" が "Book 2" より後に来る
- `BlankString.LAST` 相当で displayName 空 stack を末尾固定

PEB は `ItemStack.getHoverName().getString()` を Collator にかける。 言語コードは `Minecraft.getInstance().options.languageCode` (mc 内 `Language.getInstance()`)。

### 6-3. LEVEL sort — enchantments_score の自作版

IPN の `enchantments_score` (`item/ItemStackExtensions.kt` 推定、 `DefinedNativeRules.kt:105` で `it.enchantmentsScore`) は「全 enchant level の合計」を返す。 PEB では Book 個別の enchant に対し:

```
score = Σ (enchant.level × weight)
weight = vanilla rarity (COMMON=1, UNCOMMON=2, RARE=5, VERY_RARE=10) or 固定
```

NumberOrder.DESCENDING で「強い book を先頭」。 同 score 内は NAME sort に fallback (sub_rule)。

### 6-4. COUNT sort — ItemBucket で集約後

IPN は `accumulated_count` を「全 area で同 ItemType の総 count」と定義し、 `(item/ItemStackExtensions.kt)` で sum (`DefinedNativeRules.kt:110`)。 256 slot は 1 container 内のため:

```
1. Map<ItemType, Int> = stacks.groupBy { it.itemType }.mapValues { it.value.sumOf { s -> s.count } }
2. itemTypes.sortedBy { -map[it]!! }
3. pack each into stacks
```

= IPN の `sortItems` (`SubTrackerActions.kt:170-188`) と同型。

### 6-5. RECENT sort — IPN にも該当 native rule なし

「挿入順」軸は IPN にも対応 native rule が無い (insertion order を NBT に持たないため)。 PEB 自前実装が必須:

- `ItemStack` の DataComponent に `peb:insert_tick = world.gameTime` を埋め、 取り出し時にクリア
- COUNT sort と同じく Number 軸 (DESC が新しい順)

PEB 独自仕様。 IPN から借りるのは「**sort key を NBT/component に焼く**」 という設計だけ。

### 6-6. PostAction 適用

PEB の 16×16 グリッドは矩形なので `GROUP_IN_ROWS` / `GROUP_IN_COLUMNS` を **そのまま使う設計が成立**。 IPN の `PostActions.group()` (`PostActions.kt:48-77`) の algorithm idea (頻度ベース greedy: 多い ItemType から列/行に詰めて、 端の半端を後の ItemType で埋める) を `GroupInColumnsCalculator` (今回未深掘) を参考に自前再実装。

### 6-7. sort 実行は immutable list → write-back

IPN の `writeTo` (`SubTrackerActions.kt:218-224`):

```kotlin
private fun List<ItemStack>.writeTo(destination: List<MutableItemStack>) {
    destination.forEachIndexed { index, item ->
        val (newItemType, newCount) = source.getOrElse(index) { ItemStack.EMPTY }
        item.itemType = newItemType
        item.count = newCount
    }
}
```

PEB はサーバ同期するためそのまま MutableItemStack 書き換えではなく **`ItemStackHandler.setStackInSlot(i, sorted[i])` を 256 回呼ぶ**。 Container は ItemStack 単位の sync で十分。 IPN が sandbox/diff/click列を作るのは「vanilla server 側に書き換え権限が無い container (chest etc.) でユーザの click を再現する」ためで、 PEB は自前 container handler なので不要。

### 6-8. UI ボタン3個 + scroll で sort method 切替

`SortingButtonCollectionWidget.kt:157-180` の SortButton:

```kotlin
clickEvent = { button ->
    when (button) {
        0                          -> doSort(...)
        KeyCodes.MOUSE_SCROLL_UP   -> ModSettings.SORT_ORDER.togglePrevious()
        KeyCodes.MOUSE_SCROLL_DOWN -> ModSettings.SORT_ORDER.toggleNext()
    }
}
```

= **同 button 上で左 click=sort 実行、 scroll=sort method 切替**。 button4個並べる代わりに 1 button + scroll で 4軸切替 → PEB の UI 省スペース化 idea として有用。 ただし scroll discoverability は低いので **tooltip に key_help を出す** (同 file 256-260) のセットで使う。

---

## 7. 罠 / 注意点

### 7-1. AGPL コードは algorithm idea のみ抽出

- IPN 本体 (item/ inventory/ gui/ input/ parser/) は **AGPL-3.0**。 copy 改変 → PEB も AGPL 配下が viral 拡散
- 関数構造の模倣も「実質コピー」と判定される境界は曖昧。 安全側は:
  - **APIシグネチャ・enum 値・rule 名 (文字列)** までは OK (機能要件として書ける)
  - **メソッド body** は読まずに、 仕様書化してから自前で書く
  - 短い snippet 引用 (本ドキュメント内 < 5行) は fair use 範囲、 ただし配布物 (PEB の repo) には含めない
- `org/anti_ad/mc/ipn/api/` (Java) のみ **MIT** (`IPNButton.java:1-25`) で別ライセンス、 ここの enum 値や interface 設計だけは比較的安全に借りられる

### 7-2. ANTLR4 を持ち込む価値が無い

IPN の rule DSL parser は ANTLR4 製 (`src/main/antlr/`)。 PEB はカスタム rule を user に書かせる要件は無い (NAME/LEVEL/COUNT/RECENT 固定で十分)。 → **DSL は不要**、 enum + `Comparator.thenComparing` で済む。 ANTLR runtime 持ち込みは 300KB+ + maven dep を増やすだけ。

### 7-3. NeoForge 1.21.1 の API 名は IPN 1.21 と微妙に違う

IPN platform は `forge-1.21`, `neoforge-1.21`, `neoforge-1.21.3` ... と細分化 (`platforms/` 配下 14個)。 1.21 → 1.21.1 (yarn/mojmap) 間でも `ContainerScreenEvent.Render.Background` の event 名・ scoreboard 変更等の差分あり。 IPN コードを「読んで動作確認」しても version 違うと NeoForge 側 event 名が違う可能性 → **PEB 開発 mc-version で NeoForge javadoc を必ず再確認**。

### 7-4. ItemStack の equality が NBT 全体を見る罠

IPN は `ItemType` という「ItemStack から count を除いたもの (item + tag)」型を独自定義し、 sort/filter の identity を `ItemType.equals` で揃えている (`item/ItemType.kt`, `equals` は components map 比較)。 vanilla `ItemStack.areItemsAndComponentsEqual(a, b)` 等の便利関数もあるが、 1.21 と 1.21.1 で名称変動あり (`isSameItemSameComponents` → `isSameItemSameComponents` のまま、 ただし mojmap/srg 差)。 PEB で `Map<ItemStack, Int>` の key にすると component 差で別 entry になり集約失敗。 必ず **同一 ItemStack 識別関数を 1箇所に集約** (`ItemStackUtils.areSameBook(a, b)` 等) してそこ経由でしか比較しない規律を最初から敷く。

### 7-5. Screen widget は Init.Post で挿入、 Render は Render.Background/Foreground で配布

IPN は `ScreenEvent.Init.Post` で widget 登録、 `ScreenEvent.Render.Pre/Post` でテキストや tooltip、 `ContainerScreenEvent.Render.Background/Foreground` で slot レイヤと同 z-index の描画、 と 4 イベントを使い分け (`ForgeEventHandler.java:84-119`)。 PEB が search bar / sort button を描く時:

- button = `ScreenEvent.Init.Post` で `addRenderableWidget()` 登録のみで OK (vanilla の Button が自動描画)
- カスタム描画 (count badge 等) は `ContainerScreenEvent.Render.Foreground` でないと slot 上に乗らない
- tooltip は `ScreenEvent.Render.Post` で **最後に描く** (他 widget の上に出すため)

### 7-6. inventory sandbox を作らずに済む方針を最初に確定

IPN の `inventory/sandbox/` (`ContainerSandbox`, `ItemPlanner`, `diffcalculator/NoRoomException`) は **vanilla container (chest, ender chest 等) を相手にクライアント側で sort 結果を作り、 click 列に diff 変換、 サーバへ 1 click ずつ送る**ための層。 PEB は自前 `IItemHandler` で server-authoritative なら **sandbox 不要**、 単に handler.setStackInSlot を 256 回叩いて NetworkChannel で 1 packet 送るだけ。 ここを最初に決めないと「IPN の sandbox を真似て巨大化」する罠。

### 7-7. scrolling feature は実は AreaType 操作

`platforms/shared-1.20.5+/src/main/kotlin/org/anti_ad/mc/ipn/features/scrolling/ScrollingUtils.kt:64-101` の `withEnvironmentDo` は「scroll 方向に応じて source/target area を選び、 vanilla container 上で focused slot の item を反対 area へ転送」。 PEB は「inventory ↔ PEB の bookshelf」転送機能を入れるなら同パターンが流用可。 ただし mouseScroll event を取る場所 (`gui/inject/base/TexturedButtonWidget.kt:73-89` は button hover scroll、 IPN は別ルート で screen scroll を取っているはず) は別途調査要。

### 7-8. ANTLR generated source は build 中生成

`platforms/shared-1.20.5+/src/main/antlr/` 配下 `.g4` から `RulesLexer.kt` / `RulesParser.kt` が generated。 IPN を読む時 `org.anti_ad.mc.common.gen.*` import が見えるが、 これは生成 source なので **repo 内には実体無し** (`build/generated/` 系)。 同 import が解決できなくて当然。

### 7-9. mc-version 跨ぎ shared module 名 `shared-1.20.5+`

shared module 名末尾 `1.20.5+` は「1.20.5 以降の共通 source」を意味する命名規約。 1.20.4 以前は `shared-1.20.4-` 等の別 module 想定。 PEB が複数 mc-version 同時 support するなら **shared module を mc-API breaking change 単位で切る**設計を IPN から学べる。 が、 PEB が NeoForge 1.21.1 単一 target なら単一 source-set で十分。

---

## 出典 file:line index (本ドキュメントで参照した位置)

- `README.md:4` — IPN dev moved to Codeberg
- `LICENSE` — AGPL-3.0
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipn/api/IPNButton.java:1-25` — MIT (api のみ)
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/config/ConfigEnums.kt:29-72` — SortingMethod / PostAction / SortingMethodIndividual
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/config/ConfigEnumsExt.kt:29-38` — rule lookup
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/config/Configs.kt:71, 303-315, 368-379` — config 定義
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/item/rule/Rule.kt:40-46, 59-120` — Rule interface, BaseRule chain
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/item/rule/ArgumentMap.kt:31-117` — Parameter / ArgumentMap
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/item/rule/parameter/ItemTypeMatcher.kt:30-68` — IsTag / IsItem
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/item/rule/parameter/NativeParameters.kt:87-126` — enums (StringCompare, NumberOrder, Match, RequireNbt)
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/item/rule/natives/DefinedNativeRules.kt:87-131` — 全 native rule のカタログ
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/item/rule/natives/NativeRule.kt:60-156, 211-261, 270-345` — TypeBasedRule, compareByMatch, ByNbtRule
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/item/rule/file/RuleFile.kt`, `RuleDefinition.kt`, `SubRuleDefinition.kt` — rule file load
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/parser/RuleParser.kt:36-93` — ANTLR4 ベース parser
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/inventory/action/SubTrackerActions.kt:154-224` — sort パイプライン
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/inventory/action/PostActions.kt:30-90` — post action 矩形配置
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/inventory/GeneralInventoryActions.kt:76-120, 410-446` — doSort entry
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/inventory/AdvancedContainer.kt:39-100` — sandbox/tracker DSL
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/inventory/AreaTypes.kt:58-100` — area 定義
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/inventory/data/ItemBucket.kt:30-83` — bucket abstraction
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/inventory/data/ItemStackListExtensions.kt:32-55` — collect/processAndCollect
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/gui/inject/ContainerScreenEventHandler.kt:36-67` — widget inject 入口
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/gui/inject/InsertWidgetHandler.kt:30-103` — event 配布
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/gui/inject/SortingButtonCollectionWidget.kt:78-180, 256-260` — sort button + scroll で method 切替
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/gui/inject/base/SortButtonWidget.kt:31-92` — texture button
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/gui/inject/base/TexturedButtonWidget.kt:38-89` — mouseScrolled で onClick(SCROLL_UP/DOWN)
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/input/InputHandler.kt:45-87` — top-level dispatch
- `platforms/shared-1.20.5+/src/main/java/org/anti_ad/mc/ipnext/input/InventoryInputHandler.kt:35-90` — hotkey ladder
- `platforms/shared-1.20.5+/src/main/kotlin/org/anti_ad/mc/ipn/features/scrolling/ScrollingUtils.kt:48-101` — area-based scroll transfer
- `platforms/neoforge-1.21/src/main/java/org/anti_ad/mc/ipnext/neoforge/ForgeEventHandler.java:49-120` — NeoForge SubscribeEvent 群
- `platforms/neoforge-1.21/src/main/java/org/anti_ad/mc/ipnext/mixin/MixinAbstractContainerScreen.java:36-58` — slotClicked HEAD/TAIL inject
- `platforms/neoforge-1.21/src/main/resources/mixins.ipnext.json:14-18` — mixin 宣言
- `platforms/shared-1.20.5+/src/main/resources/assets/inventoryprofilesnext/config/rules.txt:1-25` — DSL サンプル

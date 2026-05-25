# JEI search algorithm 構造解析

source path: `C:\Users\naoki\AppData\Local\Temp\mod-sources\JustEnoughItems\` (= `/tmp/mod-sources/JustEnoughItems/`)
JEI version: HEAD (file 配置から NeoForge 1.21.x マルチプロジェクト構成。Core/Common/Gui に分離)

---

## 1. ISearchable / ISearchStorage interface

### 役割と抽象化

- **`ISearchable<T>`** — 「検索可能なもの」の最小契約。token を渡すと結果を Consumer に push する。 (`Core/src/main/java/mezz/jei/core/search/ISearchable.java:6-14`)
  ```java
  public interface ISearchable<T> {
      void getSearchResults(String token, Consumer<Collection<T>> resultsConsumer);
      void getAllElements(Consumer<Collection<T>> resultsConsumer);
      default SearchMode getMode() { return SearchMode.ENABLED; }
  }
  ```
- **`ISearchStorage<T>`** — index 構造。`put(key,value)` で構築、`getSearchResults(token, consumer)` で取り出し。 (`Core/src/main/java/mezz/jei/core/search/ISearchStorage.java:6-14`)
  ```java
  public interface ISearchStorage<T> {
      void getSearchResults(String token, Consumer<Collection<T>> resultsConsumer);
      void getAllElements(Consumer<Collection<T>> resultsConsumer);
      void put(String key, T value);
      String statistics();
  }
  ```

### 設計のポイント

- 結果を `return Collection` ではなく `Consumer<Collection<T>>` に渡す pull→push 反転。複数の内部コレクションを **コピーせず順次** 流せる (intersection/union を呼び側で組む)。
- `ISearchable` (検索ロジック層) と `ISearchStorage` (index 実装) を分離。`PrefixedSearchable` が両者を束ねる。
- `getMode()` で 1 検索器ごとに ENABLED / DISABLED / REQUIRE_PREFIX を切り替え可能 (config 連動)。

実装提供されている `ISearchStorage`:

- `GeneralizedSuffixTree<T>` — Ukkonen's algorithm ベースの suffix tree。検索 O(m), m = token 長。 (`Core/src/main/java/mezz/jei/core/search/suffixtree/GeneralizedSuffixTree.java:27-41,67`)
- `LimitedStringStorage<T>` — `SetMultiMap` + suffix tree (set 値を共有)。同一 key に値が多い時にメモリ節約。 (`Core/src/main/java/mezz/jei/core/search/LimitedStringStorage.java:20-22`)

---

## 2. PrefixedSearchable

`Core/src/main/java/mezz/jei/core/search/PrefixedSearchable.java:6-37`

```java
public class PrefixedSearchable<T, I> implements ISearchable<I> {
    private final ISearchStorage<I> searchStorage;
    private final PrefixInfo<T, I> prefixInfo;
    ...
    public Collection<String> getStrings(T element) { return prefixInfo.getStrings(element); }
    @Override public SearchMode getMode() { return prefixInfo.getMode(); }
    @Override public void getSearchResults(String token, Consumer<Collection<I>> rc) { searchStorage.getSearchResults(token, rc); }
}
```

### prefix とは

token の **先頭 1 文字** を prefix 文字に割り当てて、index 領域を切り替える。`@mod` → mod 名検索、`#tag` → tag 検索、など。

JEI が登録する prefix (`Gui/src/main/java/mezz/jei/gui/search/ElementPrefixParser.java:39-110`):

| prefix | 用途 | storage type | config |
|---|---|---|---|
| (none) `'\0'` | display name (translated)。デフォルト全文検索 | `GeneralizedSuffixTree` | always ENABLED |
| `@` | mod 名 / mod id / mod alias / 短縮 mod 名 | `LimitedStringStorage` | `getModNameSearchMode` |
| `#` | tag | `LimitedStringStorage` | `getTagSearchMode` |
| `$` | tooltip 全文 | `GeneralizedSuffixTree` | `getTooltipSearchMode` |
| `%` | creative tab 名 | `LimitedStringStorage` | `getCreativeTabSearchMode` |
| `^` | 主要色名 (近似色名へマップ) | `LimitedStringStorage` | `getColorSearchMode` |
| `&` | identifier (resource location) | `GeneralizedSuffixTree` | `getIdentifierSearchMode` |

storage 選択指針 (上表より逆算):

- **1 key に値が多い** → `LimitedStringStorage` (mod 名・tag・creative tab・色: 同じ名前を多数の item が共有)
- **1 key に値がほぼ 1 対 1** → `GeneralizedSuffixTree` (display name・identifier・tooltip 文字列)

### token / partial match

- すべて `String.contains` 相当 (suffix tree が部分文字列 contains を O(m) で評価)。
- ElementPrefixParser は元の string を保持して storage に `put` する。`Gui/src/main/java/mezz/jei/gui/search/ElementSearch.java:74-78` で `storage.put(string, element)` ループ。
- 検索時の token は **lowercase 化** された状態で渡される (`IngredientFilter.getElements()` で `filterText.toLowerCase()`, file:188-190)。

### token の正規化

mod 名処理だけ特別 (`ElementPrefixParser.java:64-68`):

```java
for (String modName : modNames) {
    modName = modName.toLowerCase();
    modName = SPACE_PATTERN.matcher(modName).replaceAll("");  // 空白除去
    sanitizedModNames.add(modName);
}
```

display name 等は `Translator.toLowercaseWithLocale` でロケール付き小文字化 (`DisplayNameUtil.java:11-16`, `Common/src/main/java/mezz/jei/common/util/Translator.java:22-25`)。

---

## 3. CombinedSearchables

`Core/src/main/java/mezz/jei/core/search/CombinedSearchables.java:8-32`

```java
public class CombinedSearchables<T> implements ISearchable<T> {
    private final List<ISearchable<T>> searchables = new ArrayList<>();
    @Override public void getSearchResults(String word, Consumer<Collection<T>> rc) {
        for (ISearchable<T> s : this.searchables) {
            if (s.getMode() == SearchMode.ENABLED) s.getSearchResults(word, rc);
        }
    }
    @Override public void getAllElements(Consumer<Collection<T>> rc) { ... }
    public void addSearchable(ISearchable<T> s) { this.searchables.add(s); }
}
```

- 複数 `ISearchable` を合成して 1 つの `ISearchable` に。
- **`REQUIRE_PREFIX` は素通し** されない (file:14)。つまり「prefix 必須」の searchable は no-prefix 検索では呼ばれず、明示的 prefix 経由のみマッチする。
- `ENABLED` のみ走らせる。`DISABLED` は完全に呼ばれない。
- 結果は重複し得る (suffix tree 同士で同じ element がヒット) → 呼び側 (`ElementSearch`) で `IdentityHashMap`-backed Set にまとめて重複排除 (`ElementSearch.java:49`)。

---

## 4. ElementSearch / ElementSearchLowMem

### IElementSearch interface

`Gui/src/main/java/mezz/jei/gui/search/IElementSearch.java:13-26`

```java
public interface IElementSearch {
    <T> void add(IListElementInfo<T> info, IIngredientManager im);
    void addAll(Collection<IListElementInfo<?>> infos, IIngredientManager im);
    Collection<IListElement<?>> getAllIngredients();
    Set<IListElement<?>> getSearchResults(ElementPrefixParser.TokenInfo tokenInfo);
    @Nullable <T> IListElement<T> findElement(ITypedIngredient<T> i, IIngredientHelper<T> h);
    void logStatistics();
}
```

### ElementSearch (高速版)

`Gui/src/main/java/mezz/jei/gui/search/ElementSearch.java`

- 各 PrefixInfo ごとに `PrefixedSearchable` を生成し、`CombinedSearchables` に登録 (`:33-40`)
  ```java
  for (PrefixInfo<...> prefixInfo : elementPrefixParser.allPrefixInfos()) {
      ISearchStorage<...> storage = prefixInfo.createStorage();
      var prefixedSearchable = new PrefixedSearchable<>(storage, prefixInfo);
      this.prefixedSearchables.put(prefixInfo, prefixedSearchable);
      this.combinedSearchables.addSearchable(prefixedSearchable);
  }
  ```
- 検索ロジック (`:43-63`):
  - token 空なら `Set.of()`
  - `NO_PREFIX` または DISABLED prefix → `combinedSearchables` 経由で全 searchable を走らせる
  - 明示 prefix → その storage だけ叩く
  - 結果は `Collections.newSetFromMap(new IdentityHashMap<>())` で重複排除 (= 参照同一性で比較、`equals/hashCode` 計算回避)
- index 構築 (`:66-81`): 各 element について、有効な prefix の `getStrings(info)` で得た全文字列を `storage.put(string, element)`。**1 element が複数 string を index** する。
- `allElements: Map<Object, IListElement<?>>` — uid → element の逆引きキャッシュ (visibility 変更等の高速 lookup 用、`:31, :110-119`)

### ElementSearchLowMem (低メモリ版)

`Gui/src/main/java/mezz/jei/gui/search/ElementSearchLowMem.java`

- index を持たず、毎回 **全 element を線形スキャン** (`:41-45`)
  ```java
  return this.elementInfoList.stream()
      .filter(elementInfo -> matches(token, prefixInfo, elementInfo))
      .map(IListElementInfo::getElement)
      .collect(Collectors.toSet());
  ```
- match 判定は `String.contains` (`:47-58`)
- メモリ削減 (suffix tree なし) と引き換えに検索遅延。`IClientConfig.isLowMemorySlowSearchEnabled()` で切替 (`IngredientFilter.java:102-108`)

### 構築コスト vs 検索コスト

| 実装 | put コスト | search コスト | メモリ |
|---|---|---|---|
| ElementSearch | O(string 長) × suffix tree update | O(token 長) | 高 |
| ElementSearchLowMem | O(1) | O(N × string 長) | 低 |

---

## 5. GuiTextFieldFilter (検索 textfield 実装)

`Gui/src/main/java/mezz/jei/gui/input/GuiTextFieldFilter.java`

### vanilla EditBox との差分

- **継承して拡張** している (`:20` `public class GuiTextFieldFilter extends EditBox`)。
- 追加:
  - `maxSearchLength = 128` (`:21`)
  - 静的 `TextHistory` で履歴共有 (`:22`)
  - `filterEmpty: BooleanSupplier` で結果 0 件時に **テキストを赤くする** (`:54-60`):
    ```java
    int color = filterEmpty.getAsBoolean() ? 0xFFFF0000 : 0xFFFFFFFF;
    setTextColor(color);
    ```
  - 独自背景 `ScalableDrawable background` (`:39-40, :104-109`)
  - `setBordered(false)` で vanilla 枠を消す (`:41`)
- `updateBounds(ImmutableRect2i)` で動的にレイアウト追従 (`:44-51`)
- `isMouseOver` を rectangle で再定義 (`:68-70`)

### key event handling

GuiTextFieldFilter は **vanilla `EditBox.keyPressed` を直接使う**。独自に override せず、外側の `TextFieldInputHandler` が `input.callVanilla(...)` で vanilla メソッドを呼ぶ (`Gui/src/main/java/mezz/jei/gui/input/handlers/TextFieldInputHandler.java:43-50`):

```java
if (input.callVanilla(
    textFieldFilter::isMouseOver,
    textFieldFilter::mouseClicked,
    textFieldFilter::keyPressed
)) {
    handleSetFocused(input, true);
    return true;
}
```

特殊キー処理 (`TextFieldInputHandler.java:28-63`):

- Enter / Escape → focus 外す
- `focusSearch` キー (JEI 独自 keybind) → focus 取得
- `hoveredClearSearchBar` キー + マウス hover → 値クリア + focus
- `previousSearch` / `nextSearch` → 履歴ナビゲーション
- vanilla 経由で処理されなかった文字入力でも、`textFieldFilter.canConsumeInput() && input.isAllowedChatCharacter()` なら "handled" 扱いにして他ハンドラへ伝播を止める (`:62`)

### **重要: focused 時に vanilla key (E etc) を奪わない実装**

これは **二段構え** で実現:

#### (a) ScreenFocusHandler — JEI textfield 取得時に元 EditBox の focus を退避

`Gui/src/main/java/mezz/jei/gui/input/focus/ScreenFocusHandler.java:16-65`

`GuiTextFieldFilter.setFocused(true)` が呼ばれると (`GuiTextFieldFilter.java:77-101`):

```java
@Override public void setFocused(boolean keyboardFocus) {
    final boolean previousFocus = isFocused();
    super.setFocused(keyboardFocus);
    if (previousFocus != keyboardFocus) {
        Minecraft minecraft = Minecraft.getInstance();
        if (keyboardFocus) {
            Screen screen = minecraft.screen;
            if (screen != null) {
                screenUnfocusHandler = ScreenFocusHandler.create(screen);
                if (screenUnfocusHandler != null) {
                    screenUnfocusHandler.unFocus();   // ← 元の EditBox から focus を剥がす
                }
            }
        } else {
            if (screenUnfocusHandler != null) {
                screenUnfocusHandler.focus();          // ← 戻すとき復帰
                screenUnfocusHandler = null;
            }
        }
        String text = getValue();
        history.add(text);
    }
}
```

`ScreenFocusHandler.create` は `screen.getFocused()` で現在 focus 中の要素を取り出し、なければ `reflectionUtil.getFieldWithClass(screen, EditBox.class).findFirst()` で screen 内の任意 EditBox を探す (`:16-35`)。`unFocus()` でその要素の focus を一時解除し、`focus()` で戻す。

→ JEI search が active 中は、コンテナ画面側の EditBox / focus 要素はキー入力を取らない。

#### (b) ClientInputHandler — Pre/Post 振り分けで vanilla key 処理を譲る

`Gui/src/main/java/mezz/jei/gui/input/ClientInputHandler.java:43-86, 120-129`

```java
public boolean onKeyboardKeyPressedPre(Screen screen, UserInput input) {
    if (!isContainerTextFieldFocused(screen)) {    // ← Container 側 EditBox が focus 中なら JEI は触らない
        ... return this.inputRouter.handleUserInput(...);
    }
    return false;
}
public boolean onKeyboardKeyPressedPost(Screen screen, UserInput input) {
    if (isContainerTextFieldFocused(screen)) {     // ← Container EditBox focus 中は Post で拾う
        ... return this.inputRouter.handleUserInput(...);
    }
    return false;
}
...
private boolean isContainerTextFieldFocused(Screen screen) {
    return reflectionUtil.getFieldWithClass(screen, EditBox.class)
        .anyMatch(textField -> textField.isActive() && textField.isFocused());
}
```

- **Pre** (vanilla の前) → コンテナ EditBox が focus してないときだけ JEI が先取りして E などをキャプチャ
- **Post** (vanilla の後) → コンテナ EditBox が focus 中はそちらが先、vanilla が消費したものは Post まで来ない
- 結果: コンテナ EditBox にキー入力が常に優先される

つまり **「vanilla E キーを奪わない」のは GuiTextFieldFilter 自身のロジックではなく、外側 ClientInputHandler の Pre/Post 切り替え + ScreenFocusHandler の focus 退避** が責任を持つ。GuiTextFieldFilter 自体は vanilla EditBox のキー処理に乗っかる。

### TextHistory

`mezz.jei.core.util.TextHistory` (filter から見えない内部) で前/次の検索文字列を保存。`setFocused(false)` 時に現在値を `history.add(text)` (`GuiTextFieldFilter.java:99`)。

---

## 6. SearchMode (enum)

`Core/src/main/java/mezz/jei/core/search/SearchMode.java:3-5`

```java
public enum SearchMode {
    ENABLED, REQUIRE_PREFIX, DISABLED
}
```

| 値 | 動作 |
|---|---|
| `ENABLED` | 全文検索でも prefix 検索でもヒットする |
| `REQUIRE_PREFIX` | prefix 明示時のみヒット。NO_PREFIX 検索からは除外 |
| `DISABLED` | index 構築も検索もしない |

### 実装上の取り扱い

- index 構築時: `prefixedSearchable.getMode() != DISABLED` のときだけ `storage.put` を実行 (`ElementSearch.java:72-79`)
  → DISABLED の prefix は **index にすら入らない** (config 変更時は `rebuildItemFilter()` で再構築、`IngredientFilter.java:123-129`)
- 全文検索: `CombinedSearchables` が `ENABLED` のみ走らせる (`CombinedSearchables.java:14, 23`)。`REQUIRE_PREFIX` は全文からは外れる。
- 明示 prefix 検索: `ElementSearch.getSearchResults` で `searchable.getMode() == DISABLED` なら fallback で全文検索 (`ElementSearch.java:57-60`)
- `NO_PREFIX` 自体は常に ENABLED 固定 (`ElementPrefixParser.java:27-32`)

### config 連動

`IIngredientFilterConfig` の各 `getXxxSearchMode()` メソッドが `IModeGetter` として `PrefixInfo` に注入される (`ElementPrefixParser.java:38-110`)。ユーザーが config で mod 名検索を REQUIRE_PREFIX にすれば、`@` 付き以外ではヒットしなくなる。

---

## 7. PEB の検索 (localized name / enchant ID / level partial match) に適用するパターン

### 7.1 PrefixInfo を PEB 用に再設計

PEB の検索対象は **enchanted book ItemStack**。検索次元:

| 検索軸 | 抽出方法 | suggested prefix | suggested storage |
|---|---|---|---|
| 翻訳名 (display name) | `book.getHoverName().getString()` (lowercase + locale) | (none, default) | `GeneralizedSuffixTree` |
| エンチャント翻訳名 (Fortune, 幸運, etc) | `EnchantmentHelper.getEnchantments(stack)` → 各 `Enchantment.getFullname(level).getString()` | (none, default) | `GeneralizedSuffixTree` (1 key = 1 book でも可) |
| エンチャント ID (`minecraft:fortune`) | `ResourceKey<Enchantment>` から `location().toString()` | `&` (JEI 流儀踏襲) | `GeneralizedSuffixTree` |
| エンチャント level (III, 3) | level を数字 + ローマ数字両方で index | `level:` か `lvl` か独自 | `LimitedStringStorage` (同 level の本は多数) |
| MOD id (`apotheosis`, `enchantment_industry`) | `ResourceKey.location().getNamespace()` | `@` | `LimitedStringStorage` |
| カテゴリ (curse / treasure / weapon armor tool etc) | `Enchantment` の TagKey / category | `#` | `LimitedStringStorage` |

→ JEI と同じく `PrefixInfo + ISearchStorage` の組合せで素直に乗る。

#### 構築コード例 (擬似)

```java
new PrefixInfo<BookEntry, BookEntry>(
    '\0',
    () -> SearchMode.ENABLED,
    book -> List.of(  // display name + 全 enchantment full name (localized)
        normalize(book.stack().getHoverName().getString()),
        // 各エンチャントの localized fullname (e.g. "幸運 III")
        ... book.enchantments().stream().map(e -> normalize(e.fullname())).toList()
    ),
    GeneralizedSuffixTree::new
);
new PrefixInfo<>(
    '@', config::getModSearchMode,
    book -> book.enchantments().stream()
        .map(e -> e.key().location().getNamespace())
        .distinct().toList(),
    LimitedStringStorage::new
);
```

### 7.2 「Fortune III × 3」のような複合条件

JEI 流の **token split + intersection**:

`IngredientFilter.java:208-230`

```java
String[] filters = filterText.split("\\|");  // OR for top-level
List<SearchTokens> searchTokens = ... ;
elementStream = searchTokens.stream()
    .map(this::getSearchResults)
    .flatMap(Set::stream)
    .distinct();
```

そして `parseSearchTokens` (`:265-292`) が `(-?".*?(?:"|$)|\S+)` で:

- `"..."` で空白を含む引用句
- `-` プレフィックスで negation (除外)
- 残りは空白区切り = **AND (intersection)**

`intersection` (`:320-337`) は **最小集合を起点に retainAll** で剪定。

#### PEB への落とし込み

- `Fortune III` → 空白区切り 2 token → 「Fortune を含む」AND 「III を含む」の積集合
- `&minecraft:fortune III` → `&minecraft:fortune` (identifier prefix) AND `III` (no-prefix 全文) の積集合
- `@apotheosis -curse` → mod が apotheosis AND curse を含まない
- `"protection IV"` → 引用句で「protection iv」を 1 token として substring 検索 (空白も保持)

→ そのまま流用可能。`FILTER_SPLIT_PATTERN` と `intersection()` をコピーすれば最短。

#### 「× 3 (3 冊以上)」の数量条件

これは **検索構文では JEI に存在しない**。PEB 独自に拡張するなら:

- token に専用 prefix を追加: 例 `count>=3` または `*3`
- ただし substring index には載らないので、`getSearchResults` の後段で **post filter** として実装するのが素直 (suffix tree とは別経路)。

### 7.3 日本語名対応

JEI と同じく **lowercase + locale** で済む:

```java
// DisplayNameUtil.java:11-16 を踏襲
String name = stack.getHoverName().getString();
name = StringUtil.removeChatFormatting(name);
name = name.toLowerCase(Minecraft.getInstance().getLocale());
```

- 日本語には大文字小文字概念がないので `toLowerCase` は実質 no-op だが、Java の `Locale` を渡しておけばトルコ語の `I/ı` 問題なども踏まない (`Translator.java:22-25`)。
- 索引化文字列も検索 token も **同じ正規化関数** を通すこと (片方だけ lowercase だと一致しない)。
- 全角/半角の正規化 (`NFKC`) は JEI もしていない。PEB で必要なら `Normalizer.normalize(s, Form.NFKC)` を被せて両方統一する (Fortune → ｆｏｒｔｕｎｅ など互換)。
- suffix tree はマルチバイト文字も OK (Java の `String` は UTF-16 char 単位、`String.contains` 相当の動作)。

### 7.4 modded enchantment 対応

- `EnchantmentHelper.getEnchantments(stack).keySet()` で `Holder<Enchantment>` を取得 → `holder.unwrapKey()` から `ResourceKey<Enchantment>` → `.location()` で `ResourceLocation` (modid + path)。
- 翻訳名は `Enchantment.getFullname(level)` (NeoForge 1.21.1 では `Component`) を `getString()` で抽出。未翻訳でも `enchantment.modid.id` の翻訳キー文字列が出るので、検索の手がかりにはなる。
- **重要**: modded enchantment は registry が **動的** (datapack で追加可能)。PEB は plays 開始時 or world 入場時に index を構築し、 `RegistriesDatapackRegistryEvent` 系で再構築するか、**book を見つけた時点で lazy に index 更新**する設計が必要。
- JEI は `IIngredientManager.IIngredientListener.onIngredientsAdded/Removed` (`IngredientFilter.java:233-257`) で動的追加に対応。PEB なら shelf に book が出入りする度に対応する形でもよい。

### 7.5 アーキテクチャ提案 (要約)

```
PortableShelfSearch
├── ShelfElementPrefixParser            // @, #, &, level の prefix 定義
├── ShelfElementSearch (extends IElementSearch 相当)
│   ├── PrefixedSearchable: display name → GeneralizedSuffixTree
│   ├── PrefixedSearchable: enchantment fullname → GeneralizedSuffixTree
│   ├── PrefixedSearchable: @mod id → LimitedStringStorage
│   ├── PrefixedSearchable: #category/tag → LimitedStringStorage
│   ├── PrefixedSearchable: &identifier → GeneralizedSuffixTree
│   └── PrefixedSearchable: level (1..5 + I..V) → LimitedStringStorage
├── ShelfFilter
│   ├── parseSearchTokens (FILTER_SPLIT_PATTERN + - で negation)
│   ├── getSearchResults (token ごとに retrieve → intersection)
│   └── post-filter for count/level range (suffix tree 外)
└── GuiSearchField (extends EditBox)
    ├── setFocused() で screen の現 focus を退避 (ScreenFocusHandler 相当)
    └── 空結果時 setTextColor(0xFFFF0000)
```

---

## 8. 罠 / 注意点

### 8.1 GuiTextFieldFilter / E キー奪取の罠

- **vanilla EditBox を継承するだけでは不十分**。JEI は `ClientInputHandler` で **`ScreenEvent.KeyPressed.Pre / Post` を両方購読** して focus 状態で Pre/Post を切替。NeoForge では `ScreenEvent.KeyPressed.Pre` を listen して、自前 textfield が focus 中なら `event.setCanceled(true)` で vanilla の `InventoryScreen` 側 hotkey 処理 (`E` で閉じる) をブロックする必要がある。
- ただし、自前 textfield の `keyPressed` を vanilla の Screen 経由で呼んでもらう設計 (= `Screen.children()` に追加して `setFocused()`) なら、vanilla の Screen 側が「focused == EditBox → typed key を消費」してくれるため、E は char input として消費されて screen を閉じない。**重要**: `Screen.keyPressed` の vanilla 実装 (`AbstractContainerScreen.keyPressed`) は EditBox に focus がある場合、 `inventoryKey` (E) の close 動作を スキップしているので、これに乗るのが最も簡単。JEI のような Pre/Post 二段は **JEI が独立 widget (Screen に登録できない外側) だから必要**。PEB は自身の `AbstractContainerScreen` 内で EditBox を `addRenderableWidget` するだけで足りる可能性が高い。
- もし **ItemStack 内インベントリ画面で EditBox + 通常 vanilla key (Q drop, hotbar 1-9) を競合させる** なら、 EditBox focus 中は AbstractContainerScreen のキー処理を抑止する必要がある。これは EditBox に focus がある場合、vanilla が自動でハンドリングするので追加実装不要。**ただし `slotClicked` / drop key はチェックを入れたほうが安全**。

### 8.2 入力長さ制限

- JEI は `maxSearchLength = 128` (`GuiTextFieldFilter.java:21`)。
- vanilla EditBox の default は 32 char (chat も 256 だが、 GuiTextField の default は短い)。**`setMaxLength` を明示しないと長い検索文字列でフラストレーションが出る**。

### 8.3 大文字小文字・ロケール

- `Translator.toLowercaseWithLocale` は `localeSupplier` 経由 (`Translator.java:9, 22-25`)。**初期化前に呼ぶと `Locale.ROOT` で lowercase される**。クライアント起動 hook で `setLocaleSupplier` する設計。トルコ語 `I→ı` 問題に注意。
- **トルコ語ロケールで `"I".toLowerCase()` は `"ı"` (dotless i)** になり、Fortune III の `I` がマッチしない。PEB でも `Locale.ROOT` 固定 or `Minecraft.getInstance().getLanguageManager().getSelected()` を選択する判断が要る。JEI はクライアントロケールに任せている。

### 8.4 suffix tree の構築コスト

- `GeneralizedSuffixTree.put` は文字列長に対して線形だが定数倍が重い。**N=10,000 個の book を 50 種類の string で index すると 50 万 put**。
- JEI は起動時に「Adding X ingredients」「Added X ingredients」ログを出している (`IngredientFilter.java:85, 89`)。PEB の shelf 1 個に大量 book を入れる UC では **shelf 開いた瞬間に毎回 index 構築は重い** ので:
  - 小規模 (< 256 slot) なら `ElementSearchLowMem` 相当 (毎回 linear scan) で十分
  - 大規模なら shelf を開いた時に lazy build + slot 変更で differential update

### 8.5 重複排除

- JEI は **`IdentityHashMap`-backed Set** を多用 (`ElementSearch.java:49`, `IngredientFilter.java:325`)。`IListElement` は heap 上の一意オブジェクトなので参照同一性で十分かつ高速。
- **`equals/hashCode` を実装した値オブジェクトで参照同一性を使うとバグる**。PEB で book ItemStack を直接比較対象にする場合、`ItemStack.equals` / NBT equality に依存するか、JEI 流儀の wrapper element を介して identity 比較するか設計判断が必要。

### 8.6 SearchMode 変更時の再構築

- DISABLED の prefix は index に入らないので、ユーザーが config で OFF→ON した瞬間に有効化されない。JEI は `rebuildItemFilter()` で **全 index を捨てて作り直す** (`IngredientFilter.java:123-129`)。PEB も config 変更 → 再構築フローを忘れずに。

### 8.7 ScreenFocusHandler のリフレクション依存

- `Gui/src/main/java/mezz/jei/gui/input/focus/ScreenFocusHandler.java:24-26` で `reflectionUtil.getFieldWithClass(screen, EditBox.class)` を使い、screen 内の EditBox を見つけている。
- **NeoForge / Mojang の難読化変更で field name や型が変わると壊れる**。PEB が自前 Screen 内で完結するなら不要 (自分の EditBox 参照を持っている)。

### 8.8 token 空 / prefix 1 文字

- `ElementPrefixParser.parseToken` (`:125-139`):
  - 空 token → `Optional.empty()` (検索しない)
  - 既知 prefix + token 長 1 (= prefix 文字だけ) → `Optional.empty()` (検索しない)
  - 未知 prefix / DISABLED prefix → 全文検索に降格
- 「`@` だけ打って入力中」のときに **全 item が消えるのを防ぐ** 仕様。PEB でも同様に「prefix だけ」を空扱いにしないと UX が悪い。

### 8.9 FILTER_SPLIT_PATTERN regex の罠

`IngredientFilter.java:47` `Pattern.compile("(-?\".*?(?:\"|$)|\\S+)")`

- `"` 開いて閉じない → `$` (行末) まで一括取り込み (途中で諦めない)
- `-"foo bar"` の場合、 `-` の後に `"` を **消去** してから検索 (`:278`)
- `-` 単独や `""` だけは isEmpty で skip (`:279-281`)

PEB が `-` を level negation などで別用途に使うと衝突する。**`-` を除外 prefix としてユーザー教育するか、別記号を使うかを最初に決める**。

### 8.10 `'|'` による OR の優先度

- 最上位 split が `|` (`:209`)。 `"a b | c d"` は `(a AND b) OR (c AND d)`。
- 入れ子・括弧はサポートなし。PEB でも同じ簡易構文で十分な可能性が高いが、ユーザー期待値とずれる場合はドキュメント明記が必要。

### 8.11 partial 検索 vs 完全一致

- JEI は **全て contains (部分一致)**。`fortune` は `misfortune` にも `fortune III` にもヒット。
- PEB で「Fortune だけにヒットさせたい」UC なら **boundary marker** を index 化時に付ける (例: `|fortune iii|`)。または token を完全一致モード `=fortune` などで別 storage を用意。

---

## 参考 file:line 一覧 (主要)

- `Core/src/main/java/mezz/jei/core/search/ISearchable.java:6-14`
- `Core/src/main/java/mezz/jei/core/search/ISearchStorage.java:6-14`
- `Core/src/main/java/mezz/jei/core/search/PrefixedSearchable.java:6-37`
- `Core/src/main/java/mezz/jei/core/search/CombinedSearchables.java:8-32`
- `Core/src/main/java/mezz/jei/core/search/PrefixInfo.java:8-58`
- `Core/src/main/java/mezz/jei/core/search/SearchMode.java:3-5`
- `Core/src/main/java/mezz/jei/core/search/LimitedStringStorage.java:20-52`
- `Core/src/main/java/mezz/jei/core/search/suffixtree/GeneralizedSuffixTree.java:27-77`
- `Gui/src/main/java/mezz/jei/gui/search/ElementPrefixParser.java:27-160`
- `Gui/src/main/java/mezz/jei/gui/search/ElementSearch.java:26-135`
- `Gui/src/main/java/mezz/jei/gui/search/ElementSearchLowMem.java:24-128`
- `Gui/src/main/java/mezz/jei/gui/search/IElementSearch.java:13-26`
- `Gui/src/main/java/mezz/jei/gui/input/GuiTextFieldFilter.java:20-110`
- `Gui/src/main/java/mezz/jei/gui/input/ClientInputHandler.java:43-129`
- `Gui/src/main/java/mezz/jei/gui/input/focus/ScreenFocusHandler.java:9-65`
- `Gui/src/main/java/mezz/jei/gui/input/handlers/TextFieldInputHandler.java:13-102`
- `Gui/src/main/java/mezz/jei/gui/ingredients/IngredientFilter.java:46-337`
- `Gui/src/main/java/mezz/jei/gui/ingredients/DisplayNameUtil.java:11-16`
- `Gui/src/main/java/mezz/jei/gui/ingredients/IListElementInfo.java:14-43`
- `Common/src/main/java/mezz/jei/common/util/Translator.java:8-30`

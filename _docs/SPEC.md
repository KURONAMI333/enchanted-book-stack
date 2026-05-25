---
title: Portable Enchanted Bookshelf
short: PEB
status: spec-locked
created: 2026-05-25
revised: 2026-05-25 (kura レビュー 2 回反映: tier 廃止 + 無限容量)
verification: ADDON_RESEARCH_PROTOCOL 5-step pass (★ GO 判定、scope M)
research_files:
  - _docs/REFERENCE_JARS_ANALYSIS.md
  - _docs/REDDIT_DEMAND_ANALYSIS.md
  - _docs/EXISTING_MODS_ANALYSIS.md
---

# Portable Enchanted Bookshelf (PEB) — v0.1.0 SPEC

> kura アイデア起点: 「司書ファームでエンチャント本がインベントリかさばる、本棚を持ち歩いてしまえたら」

**改訂 2 回目の確定仕様**: tier 廃止 (kura 判断「アイテム 1 種限定なのでバランス崩壊しない」)、容量無限化、scope M。

---

## 1. コンセプト (1 行)

**エンチャント本専用の Bundle** — 異種エンチャント本を 1 スロットに**無制限**保管できる持ち歩き型本棚。tree 展開 GUI で「エンチャント種類 → level 別 breakdown」を即検索、modded enchantment / level 上限拡張 mod にも動的対応。

vanilla Bundle が「64/max_stack_size」公式でエンチャント本 1 冊しか入らない問題を、**エンチャント本に限定することで**バランス崩さず解決。

## 2. なぜ存在するか

### 痛み (Reddit 実証)
- 司書ファームで異種エンチャント本が大量に貯まる、各本 stack=1 + NBT 違い = **全部別アイテム**
- 数百冊集まると倉庫圧迫、目当ての本探しが苦行 (Reddit `r/Minecraft` 629 upvote: "chiseled bookshelf tooltip なしで探すのに 16 分かかる")
- vanilla Bundle (1.21+) は「64/max_stack_size」公式で **エンチャント本 1 冊で bundle 1 個分占有** = 機能しない

### 既存状況 (3-agent 詳細調査結果)

**直接競合: 2 件**
| mod | DL | Loader/MC | 機能 | PEB との差別化 |
|---|---:|---|---|---|
| **Enchanted BookShelf** (jorgej_ramos) | 225 | NeoForge 1.21.1 | block 設置、1000 冊 + 検索 | item 持ち歩き型 vs block 設置型 |
| **Enchantment Library at Home** | 1.1M | Forge 1.20.1 | block 設置、point 変換式 | 物理本維持 vs point 変換 |

**間接競合 (Apotheosis 派が一部需要を吸収)**
- Apothic Enchanting (18.7M DL), Apotheosis Totem (5.8k DL) — modpack ユーザー層

### PEB 残る差別化軸 (8 軸)
1. **持ち歩ける item 型** (全競合 block 型)
2. **物理本維持** (Library at Home / Standalone は point 変換)
3. **司書交易追加** (全競合無し)
4. **multi-loader 5 環境** (Fabric 帯は競合ほぼ空白)
5. **日本語 native**
6. **modded enchantment 動的対応** (vanilla enchant 以外も自動扱い)
7. **level 上限なし** (Limitless Enchantments 等で Fortune X+ 作成可)
8. **tree 階層 GUI** (フラット list が一般、tree は kura 独自)

### ⑤ 生存性 (本家・競合の侵食リスク)

- Mojang Bundle は「全アイテム共通容量公式」維持 → エンチャント本特化はやらない (公式吸収リスク **低**)
- Enchanted BookShelf (225 DL) は block 派で固定、item 派には来ない
- Library at Home は 1.20.1 止まり、1.21.1 移植兆候なし
- → 1 年以上 PEB 差別化軸は維持可能

---

## 3. 機能仕様 (tier 廃止版)

### 3.1 アイテム: Portable Enchanted Bookshelf (単一 item)

**「持ち歩く本棚」** をテーマにした handheld block-item:
- 見た目: 木枠 + 革ベルト付きの小型本棚 (折りたためる本棚イメージ)
- handheld 時は 3D ブロック風レンダリング
- **単一 item、無限容量、tier なし**

`mod_id` 内 item ID: `portableenchantedbookshelf:portable_enchanted_bookshelf`

### 3.2 容量: 無限 (kura 判断)

- 内容物上限なし
- エンチャント本だけが入る (他アイテム拒否) → 1 種限定でバランス影響ゼロ
- NBT サイズ実用上限: ~10,000 冊 で数百 KB、技術的問題なし
- tooltip は **容量数字を出さず、内容物概要**を表示:
  ```
  Portable Enchanted Bookshelf
  21 enchanted books inside
    Fortune III × 3, Mending I × 2, Aqua Affinity I × 1, ...
  ```

### 3.3 統合表示 + tree 階層 (kura 設計)

**「エンチャント種類で 1 行に集約 → 展開で level 別 breakdown」**

```
GUI 内表示:
┌──────────────────────────────────┐
│ 🔍 Search                         │
├──────────────────────────────────┤
│ ▼ Fortune (6 books)               │
│     I    × 3                      │
│     III  × 2                      │
│     X    × 1   ← modded 拡張 OK   │
│ ▼ Mending (2 books)               │
│     I    × 2                      │
│ ▶ Aqua Affinity (1 book)          │
│ ▶ Curse of Vanishing I (1) ⚠     │
│ ▶ Apotheosis: Soulbound (3 books) │ ← modded enchant 自動対応
└──────────────────────────────────┘
```

- ▼/▶ クリックで展開・折りたたみ
- 展開 line で left-click → 1 冊取り出し、shift+click → ホットバーへ、right-click → 全部取る
- folded でも level でも検索ヒット

内部 NBT (NeoForge 1.21.1 `DataComponentType`):
```
PouchContents = List<Entry> where Entry = {
  enchantId: ResourceLocation,
  level: int,        // 上限なし
  isCurse: boolean,
  count: int
}
```

### 3.4 検索仕様

- partial match: `"fort"` → Fortune ヒット
- level filter: `"III"` で全 Level 3 enchant ヒット
- enchant ID 直接: `"minecraft:fortune"` / `"apotheosis:soulbound"`
- **localized name 対応**: `"幸運"` で Fortune ヒット (kura 必須要件)
- Sort: Name asc / Recently added / Level desc (default: Name)

### 3.5 動的 enchantment 対応 (kura 必須要件)

**ハードコード厳禁**:
- エンチャント判定: vanilla `Items.ENCHANTED_BOOK` のみ
- 内容取得: `ItemEnchantments` component から動的 read
- vanilla / Apotheosis / Iron's Spellbooks / Quark / 任意の modded enchant が全部同じパスで扱える
- **level 上限なし**: int 値そのまま保持。Limitless Enchantments で作った Fortune XV も保管可
- 表示: I-X はローマ数字、11+ は `Lvl 11` `Lvl 15` のような数字

```java
// 動的 enchantment 取得 pattern (REFERENCE_JARS_ANALYSIS.md 参照)
ItemEnchantments enchantments = bookStack.get(DataComponents.STORED_ENCHANTMENTS);
for (Holder<Enchantment> ench : enchantments.keySet()) {
    int level = enchantments.getLevel(ench);
    boolean isCurse = ench.is(EnchantmentTags.CURSE);
    ResourceLocation id = ench.unwrapKey().get().location();
    // ...
}
```

### 3.6 司書交易追加 (差別化軸 3)

vanilla `librarian` (司書) trade list:
- **Level 4 (Expert)**: Portable Enchanted Bookshelf ×1 ↔ **24 emerald + 4 leather + 4 paper**

価格設計の根拠:
- 24 emerald = 中位帯 (Mending 本が 30 emerald 級なのと整合)
- レザー + 紙 = レシピと同じ素材、craft 派と並列入手路の象徴
- 単一 item なので Level 5 trade は不要、Level 4 で完結

実装:
- NeoForge 1.21.1: `RegisterTradesEvent`
- Forge 1.21.1: 同上 (NeoForge 互換)
- Forge 1.20.1: `VillagerTradesEvent` (旧 API)
- Fabric: `TradeOfferHelper.registerVillagerOffers(VillagerProfession.LIBRARIAN, 4, ...)`

### 3.7 レシピ (単一、upgrade 全廃)

「本棚を持ち歩けるようにする」コンセプト:

```
[paper] [leather] [paper]
[leather][bookshelf][leather]   → Portable Enchanted Bookshelf ×1
[paper] [leather] [paper]
```

素材選定の理由:
- **本棚** (中央) = エンチャント連想 + 「本棚を加工する」のメンタルモデル
- **革** = 持ち歩き感、製本イメージ
- **紙** = 軽量化、エンチャント本との視覚的整合
- vanilla 素材のみ = modpack 互換性最大

### 3.8 制約 (バランス守る)

- **エンチャント本以外は入らない**: 通常の本 / 書ける本 / 設計図等は拒否
- **PEB を PEB の中に入れる: 禁止** (無限再帰防止)
- shulker box / enderchest / Sophisticated Backpacks の中: OK (vanilla item として扱える)
- アンビル rename で名前変更: 可 (UX 自由度)
- enchantability: 不可 (PEB 自体に enchant 付与しない)

---

## 4. 対象環境

KURONAMI 標準 5 環境:

| Loader / MC | 対応 |
|---|---|
| NeoForge 1.21.1 | ✅ first release |
| Forge 1.21.1 | ✅ |
| Forge 1.20.1 | ✅ |
| Fabric 1.21.1 | ✅ |
| Fabric 1.20.1 | ✅ |

**両側必須** (client+server): GUI は client、司書交易 + NBT は server。

---

## 5. 実装上の留意点 (3-agent 調査結果反映)

### 5.1 NBT / Component 設計
- 1.21+: `DataComponentMap` ベース、`PouchContentsComponent extends DataComponentType<PouchContents>`
- 1.20.1: 旧 CompoundTag 方式、loader subproject ごとに別実装
- versioning flag (`schema_version: 1`) を入れて future-proof
- `Codec + StreamCodec<RegistryFriendlyByteBuf, T>` で 1.21 標準 serialization (Sophisticated 系の pattern)

### 5.2 GUI 実装
- **`sophisticatedcore` lib に依存できない** (REFERENCE_JARS_ANALYSIS の発見): SB/SS の Menu/Screen base は流用不可
- → vanilla `AbstractContainerMenu` 直接拡張で `PouchMenu` 自前実装
- `PouchScreen` は tree 展開 list + 検索バー
- vanilla `EditBox` widget で search bar
- scrollable list は `AbstractSelectionList` 拡張、expanded state を per-row 管理
- Sodium / Optifine 互換性配慮 (vanilla widget 使う限り問題なし)

### 5.3 動的 enchantment 取得
上の §3.5 のコード pattern。modded enchant も同じパスで取れる、ハードコード皆無。

### 5.4 司書交易 multi-loader 実装
- NeoForge 1.21.1: `RegisterTradesEvent` (新 API)
- Forge 1.21.1: 同上
- Forge 1.20.1: `VillagerTradesEvent`
- Fabric: `TradeOfferHelper.registerVillagerOffers`

### 5.5 容量無限の技術的考慮
- ItemStack の NBT サイズ上限はネットワーク packet 制約 (~2MB) のみ
- 1 万冊で ~500KB 程度、実用上限内
- Codec で List<Entry> serialize 時、`StreamCodec` で chunked send 検討 (将来要件)

### 5.6 PEB 内 PEB 禁止 (再帰防止)
- ItemStack を挿入する前に `stack.getItem() instanceof PortableEnchantedBookshelfItem` チェック → 拒否

---

## 6. v0.1.0 開発タスクリスト (scope M 版)

### Phase 1: Core 実装 (NeoForge 1.21.1)
- [ ] Item 1 種登録 (`PortableEnchantedBookshelfItem`)
- [ ] `PouchContents` data structure + `DataComponentType` 登録
- [ ] `Codec + StreamCodec` for PouchContents
- [ ] Insert / Extract ロジック (右クリック / GUI 操作)
- [ ] Recipe (1 つ、本棚 + 革 + 紙)
- [ ] `PouchMenu` (vanilla `AbstractContainerMenu` 直拡張)
- [ ] `PouchScreen` (tree 展開 GUI、検索バー、sort)
- [ ] 検索: partial match + level filter + 日本語名対応
- [ ] 司書交易追加 (Level 4 Expert)
- [ ] tooltip (内容物概要、容量数字無し)
- [ ] 日本語 lang (en/ja 最低)
- [ ] Texture / 3D model (handheld block-item、1 種のみ)

### Phase 2: マルチローダー展開
- [ ] Forge 1.21.1
- [ ] Forge 1.20.1 (CompoundTag 系)
- [ ] Fabric 1.21.1
- [ ] Fabric 1.20.1

### Phase 3: 公開準備
- [ ] LICENSE / README (scaffold 済)
- [ ] STORE_BODY_EN.md (差別化軸 8 を本文で明示)
- [ ] ロゴ (256x256、本棚 + 革ベルト)
- [ ] スクリーンショット (GUI tree 展開 + 大量本 demo + 司書交易 + handheld 見た目)
- [ ] GitHub repo
- [ ] Modrinth / CurseForge project
- [ ] PROJECT_REGISTRY / stats.py / release.py 更新

scope L → **scope M に縮小** (tier 廃止で約 40% タスク削減、Phase 1 = 2-3 セッション目安)

---

## 7. 命名一式 (lock 済)

| 項目 | 値 |
|---|---|
| 公開タイトル | **Portable Enchanted Bookshelf** |
| 内部コード | **PEB** |
| mod_id | `portableenchantedbookshelf` |
| Java package | `com.kuronami.portableenchantedbookshelf` |
| Java main class | `PortableEnchantedBookshelf` |
| Modrinth slug | `portable-enchanted-bookshelf` |
| GitHub repo | `KURONAMI333/portable-enchanted-bookshelf` |
| Local path | `mod-027-portable-enchanted-bookshelf` |
| ja name | 持ち歩けるエンチャント本棚 |

---

## 8. STORE 訴求軸 (Reddit 需要反映)

**主訴求**: "Search through hundreds of enchanted books in one slot" (検索 + 大量収納)
**副訴求**: "Take your enchanted books with you" (持ち歩き)
**差別化**: "Item-based, not a block. Physical books preserved, no point conversion."

Apotheosis ユーザーは既存解で足りる → ターゲットは **vanilla 寄り司書ファーム民** + **Fabric 帯** (競合空白)。

---

## 9. 未確定事項 (実装中に判断)

- [ ] handheld 時の 3D 描画モデル詳細 (本棚 block 流用 vs 専用 model)
- [ ] tooltip の内容物プレビュー文字数上限 (3-5 種類くらい + "..." 等)
- [ ] PEB が破壊された時の挙動 (中身ドロップ vs 復元不能、要 kura 判断)
- [ ] GUI hotkey (none = 右クリックのみで OK か、F/P 等の hotkey 追加するか)

---

## 10. リスクと対策 (scope M 版)

| リスク | 対策 |
|---|---|
| scope M = 2-3 セッション開発 | Phase 制で kura チェックポイント、各 Phase 完成で kura 確認 |
| GUI 5 loader 対応の作業量 | NeoForge 1.21.1 で完全動作 → cp + patch で水平展開 |
| Component API は 1.21+ のみ = 1.20.1 別実装必要 | 1.20.1 は CompoundTag で実装、共通 interface で吸収 |
| 直接競合 (Enchanted BookShelf 225 DL) との混同 | STORE_BODY で「item 派の選択肢」明示、Apotheosis 系は捨て |
| Reddit「持ち歩き」需要弱い | 主訴求を「検索 + 大量収納」に、持ち歩きは副次的に |
| Sophisticated 等の core lib に依存できない | Menu/Screen 自前実装 (vanilla `AbstractContainerMenu` 直拡張) |

---

## 11. 次のアクション

1. ✅ scaffold (mod-027) — 完了
2. ✅ SPEC 確定 — 完了
3. [ ] **`PortableEnchantedBookshelfItem` 単一 item 登録** (Phase 1 開始)
4. [ ] `PouchContents` data structure + `DataComponentType`
5. [ ] Insert / Extract logic
6. [ ] Recipe (1 つ)
7. [ ] Menu / Screen 骨組み
8. [ ] 検索 / sort
9. [ ] 司書交易
10. [ ] kura チェックポイント (NeoForge 1.21.1 で完動 → 残り 4 loader 展開)

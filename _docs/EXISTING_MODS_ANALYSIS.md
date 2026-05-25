---
title: Portable Enchanted Bookshelf — 既存 MOD 詳細調査
created: 2026-05-25
agent_research: ② 既存 mod 全面調査 (Modrinth + CurseForge 網羅、両 API 直叩き)
scope: 1.21.1 / 1.20.1 × Forge / NeoForge / Fabric / Quilt
---

# Portable Enchanted Bookshelf — 既存 MOD 詳細調査

## サマリ (結論)

- **直接競合 (機能オーバーラップ大)**: **2 件**
  - Enchanted BookShelf (jorgej_ramos, 225 DL, 1.21.1 NeoForge only) — block ベース、ほぼ同コンセプトの先行 mod
  - Enchantment Library at Home (UberHelixx, 1.1M DL, 1.20.1 Forge only) — block ベース + ポイント変換式、Apotheosis 切出版
- **部分被り (発想は近い、別アプローチ)**: **6 件**
  - Apothic Enchanting 本体の Enchantment Library (18.7M DL, NeoForge 1.21.x)
  - Apotheosis 本体 (122M DL, 1.20.1 + 1.21.1 両対応 Forge/NeoForge)
  - Enchantment Library Standalone (Sotnah, 256 DL Modrinth / 496 DL CF, 1.21.1 NeoForge)
  - Enchantment Library (kurrycat2004, 176k DL, 1.12.2 only — 死蔵だが歴史的存在)
  - Apotheosis Totem (5.8k DL, Forge/NF 1.20.1 + 1.21.1) — トーテム特化版 PEB
  - Stacks Are Stacks / Bigger Stacks (28k / 8.7k DL) — 同種 stack のみ、異種統合無し
- **参考になる隣接 (機能無関係、pattern 学べる)**: **5 件**
  - Akashic Tome (340k DL) / Eccentric Tome (1M DL) — 多 item 単一スロット格納の元祖 pattern
  - Sophisticated Backpacks (12.4M DL) — 4 tier upgrade UX 規範
  - Tom's Simple Storage (46.4M DL) — vanilla 風 GUI 規範
  - Bookshelf Inspector (1.28M DL) — bookshelf 内検索 UI 規範
  - Librarian Trade Finder (231k DL) — 司書交易系の UX 規範
- **判定**: **着工 GO (条件付き)**
  - PEB の差別化軸 (**item 持ち歩き** / **4 tier upgrade** / **tree 階層 GUI** / **司書交易追加** / **multi-loader (Fabric 含む)** / **1.20.1 + 1.21.1 両対応** / **modded enchant 動的対応** / **日本語**) は既存 mod に全て揃っているものはない
  - ただし **Enchanted BookShelf (jorgej_ramos)** という非常に近い先行 mod が存在することは STORE_BODY に明記してリスペクト+差別化を明示推奨
  - **1.20.1 Forge 対応** は Apotheosis (122M DL) が同領域を block ベースで完全カバーしているため、kura のターゲット層 (司書ファーム民) のうち Apotheosis 派には届かない可能性。ただし vanilla 派 / Apotheosis 未導入派には十分需要あり

---

## A. 直接競合 (PEB と機能オーバーラップ大)

### A-1. Enchanted BookShelf (jorgej_ramos) — ★最重要警戒

| 項目 | 値 |
|---|---|
| 配布 | CurseForge のみ |
| URL | https://www.curseforge.com/minecraft/mc-mods/enchanted-bookshelf |
| 作者 | jorgej_ramos |
| DL | 225 (低人気) |
| 最終更新 | 2026-01-26 |
| MC / Loader | **1.21.1 NeoForge only** (1.21.0 も対応) |
| ライセンス | All Rights Reserved (source 非公開) |
| GitHub | 非公開 (JorgeJRamos アカウント repos 0 件) |

**機能比較表** (PEB 仕様との被り):

| 機能 | PEB | Enchanted BookShelf | 被り判定 |
|---|---|---|---|
| エンチャ本大量保管 | 64-4096 冊 (4 tier) | **1000 冊** (1 tier) | ★完全被り |
| 検索 GUI | partial + level + 日本語 | enchant 名 + ローマ数字 | ★完全被り |
| 表示 | tree 階層 (種類→level) | 54-slot grid + side enchant index | △ 別表示 |
| 持ち歩き | **item handheld** | **placeable block (2 高)** | ✗ ここが最大差別化 |
| 4 tier upgrade | 4 段階 | 単一 tier | ✗ |
| 司書交易 | あり | なし | ✗ |
| modded enchant 動的対応 | 必須要件 | 不明 | ? (source 非公開) |
| 多 loader | NF/Forge/Fabric × 1.20.1/1.21.1 | NeoForge 1.21.1 only | ✗ |
| 多言語 (ja) | 必須 | なし | ✗ |
| Enchant 分割機能 | なし | あり (Ink+Quill+Book 消費) | ✗ (向こうの独自) |

**PEB との差別化軸 (明確に存在)**:
1. **持ち歩き = item** vs Enchanted BookShelf = 設置式 block (2 ブロック高)
2. **4 tier upgrade** (64 → 4096) vs 1 tier 1000 固定
3. **司書交易** vs 無し (PEB は司書ファーム民へ訴求できる)
4. **multi-loader 5 環境** vs NeoForge 1.21.1 only
5. **日本語ローカライゼーション**
6. **動的 modded enchant 対応** (確認できないが PEB は必須要件)
7. **tree 階層 GUI** (kura 独自 UX) vs grid + 横一覧

**生存性チェック**:
- DL 225 / 2026-01 更新 → **低活性、roadmap 不明、source 非公開**で拡張困難
- 「持ち歩き化」「Forge/Fabric 移植」「1.20.1 backport」全部未対応、PEB が埋める空白あり

**警戒度**: ★★★ (STORE_BODY で言及・差別化必須)

---

### A-2. Enchantment Library at Home (UberHelixx → Syi-I) — ★1.20.1 帯の最大競合

| 項目 | 値 |
|---|---|
| 配布 | CurseForge メイン (Modrinth では未確認) |
| URL | https://www.curseforge.com/minecraft/mc-mods/enchantment-library-at-home |
| 作者 | UberHelixx (CF)、Syi-I (GitHub) |
| DL | **1,145,616** (1.1M、極めて高人気) |
| 最終更新 | 2025-07-29 (v1.0.2) |
| MC / Loader | **1.20.1 Forge only** |
| ライセンス | MIT |
| GitHub | https://github.com/Syi-I/EnchantmentLibraryAtHome |
| 派生 | Enchantment Library Compat (1.1k DL) — ピンイン検索追加版 |

**機能比較**:

| 機能 | PEB | EL at Home | 被り判定 |
|---|---|---|---|
| 保管方式 | 物理本維持 (count) | **ポイント変換** (本消費、内部ポイント蓄積) | 別軸 |
| 出力 | 取り出し = 元の本 | 任意 level の本を新規生成 | △ |
| 検索 GUI | 検索バー + tree | 検索バー + ツール種フィルタ | △ |
| 持ち歩き | item | **block (2 tier、固定設置)** | ✗ |
| Tier | 4 段 | 2 段 (Library / Library of Alexandria) | △ |
| 司書交易 | あり | なし | ✗ |
| 多 loader | 5 環境 | **1.20.1 Forge only** | ✗ |

**生存性チェック**:
- DL 1.1M は完全に「1.20.1 Forge 帯の定番」化済
- 2025-07 最終更新、1.21 移植の言及なし (GitHub README に roadmap 無し)
- **派生で中国語ピンイン検索追加 (yiran1457) 等のコミュニティ拡張も発生済**
- 「物理本維持」 vs 「ポイント変換」は思想差。kura の SPEC は物理本維持 → 司書交易で得た本を「そのまま渡せる/取り出せる」UX 重視

**警戒度**: ★★ (1.20.1 帯の vanilla 派ユーザーの一部は既にこの mod を使用済の可能性高)

**重要差別化**: PEB は **物理本維持で「保管庫」**、EL at Home は **ポイント変換で「変換工房」**。コンセプトが違うので両立可能。STORE_BODY で「Apotheosis 系の point 変換とは違って物理本をそのまま保持」と明示すべき。

---

### A-3. Apothic Enchanting 本体の Enchantment Library — ★1.21.1 帯メジャー

| 項目 | 値 |
|---|---|
| URL | https://www.curseforge.com/minecraft/mc-mods/apothic-enchanting |
| 作者 | Shadows_of_Fire |
| DL | **18,747,674** (18.7M) |
| 最終更新 | 2026-05-18 (アクティブ) |
| MC / Loader | NeoForge **1.21.1, 1.21, 1.20.4** (1.20.1 なし) |
| 1.20.1 別配布 | Apotheosis 本体に内包 |

- Library 機能の説明: "store all of your enchanted books in one place, search through them, and extract only the ones you need"
- **ポイント変換式 (本消費)** = EL at Home と同方式
- 1.21.1 帯では Apothic Enchanting がデファクト。NeoForge ユーザーで本格 enchant overhaul を入れる派は既にこれで足りている

**PEB との関係**: Apotheosis/Apothic を入れない vanilla 派が PEB のターゲット。Apothic 派は PEB を導入しない可能性高い → ターゲットは「vanilla 寄りで本棚問題だけ解きたい層」明確化推奨

---

### A-4. Enchantment Library Standalone (Sotnah) — 1.21.1 NeoForge

| 項目 | 値 |
|---|---|
| URL | https://modrinth.com/mod/enchantment-library-standalone |
| GitHub | https://github.com/Sotnah/Enchantment-Library-Standalone |
| 作者 | Sotnah / MagicStorage |
| DL | 256 (Modrinth) / 496 (CF) |
| 最終更新 | 2026-04-08 (Modrinth)、GitHub に 2026-04-11 表示 |
| MC / Loader | NeoForge 1.21.1, 26.1.1 |
| ライセンス | MIT |

- Apothic Enchanting の Library 単体を NeoForge 1.21.1 に切り出した版
- 3 tier 構成 (Tier 1: Lvl V / Tier 2: Lvl X / Tier 3: Lvl XXX)
- 動的 XP コスト (二次関数スケール)、disenchant ボタン、cursor 操作 (left-click 1 lv 抽出 / shift+click max / ctrl+click 払戻)
- **やはりポイント変換式** (物理本維持ではない)

**PEB との関係**: 思想差で住み分け可能 (上記 A-2 同じ理由)

---

### A-5. Enchantment Library (kurrycat2004) — 1.12.2 死蔵

| 項目 | 値 |
|---|---|
| 配布 | CurseForge (176k DL) / Modrinth (1.3k DL) |
| 最終更新 | 2026-03-12 (Modrinth では極めて最近に表示、CF は 13 年塩漬けと kura メモ) |
| MC / Loader | 1.12.2 Forge only |

**歴史的存在として記録。1.21 / 1.20 ユーザーには無関係。**
ただし「Modrinth に 2026-03 表示」は版上げ無しの再アップロード可能性が高い (要 GitHub commit 確認)。kura メモの「13 年塩漬け」と表示更新日のズレは「公式 PR の merge 履歴は source の延長」原則に抵触しない範囲 (1.12.2 から動かしていない)。**警戒度ゼロ**。

---

### A-6. Apotheosis Totem — 発想同型、対象違い

| 項目 | 値 |
|---|---|
| URL | https://modrinth.com/mod/apotheosis-totem |
| DL | 5,860 |
| 最終更新 | 2026-05-25 (極めてアクティブ) |
| MC / Loader | Forge + NeoForge × 1.20.1 + 1.21.1 |

- **「Totem Container」アイテム** で複数 Totem of Undying を 1 スロット格納、自動消費
- **PEB と思想完全同型 (item ベース、multi-NBT 集約、単一スロット)**、対象だけが Totem
- ライセンス・GitHub 不明だが Adorned/Curios 対応に言及あり

**PEB との関係**: 直接競合ではないが「**この pattern が Minecraft community に受け入れられてる証拠**」。PEB の着工根拠を強める材料。

---

### A-7. Stacks Are Stacks / Bigger Stacks / Limitless Enchantments — 既知

| mod | DL | 対応 | PEB との関係 |
|---|---|---|---|
| Stacks Are Stacks | 28,055 | 1.21.10-11, Fabric | **同種 stack のみ**、異種統合無し |
| Bigger Stacks (Unofficial) | 8,737 | 1.21.1, NeoForge | 同上 (per-item cap 設定可能) |
| Limitless Enchantments | 43,000 (kura メモ) | 不明 | level cap 撤廃のみ、保管解決せず |
| Enchanted Bookshelves | 168,180 | 1.21.10-11 | テクスチャ (glint) のみ、保管無関係 |

これらは **「異種エンチャ本を 1 スロットに大量」を一切解いてない**。PEB の存在価値を補強する補助証拠。

---

## B. 参考 mod (PEB 実装に学べる pattern)

### B-1. Akashic Tome (340k DL, 1.21.1 + 多バージョン)

| 項目 | 値 |
|---|---|
| URL | https://modrinth.com/mod/akashic-tome |
| Loader | Forge + NeoForge |
| MC | 1.21.1, 1.20.1, 1.19.2, 1.18.2, 1.16.5, 1.15.2, 1.12.2 (極めて広い) |
| ライセンス | CC-BY-NC-SA-3.0 |

**学べる pattern**:
- **NBT に sub-item を直接埋め込み**、右クリックで「実体の mod item」に変身する dispatcher
- 「実 item を生成」する pattern は PEB の「本取り出し」と完全同型 (右クリックで本を取り出すロジック)
- Sneak+drop = eject pattern (PEB の本取り出し操作 UX 参考)
- 1.12.2 まで対応 = **長年メンテされてる multi-loader / multi-version pattern の規範**
- 依存: 1.19.2 以下は AutoRegLib

**PEB 設計への直接影響**:
- 内部 NBT の `PouchContents = List<Entry>` 設計は Akashic Tome を踏襲できる
- 取り出し UX (右クリック / Shift+Q / Drop key) の操作系を流用可能

### B-2. Eccentric Tome (1.0M DL, 1.21.1)

| 項目 | 値 |
|---|---|
| URL | https://modrinth.com/mod/eccentric-tome |
| GitHub | https://github.com/Porting-Dead-Mods/EccentricTome-Updated |
| ライセンス | LGPL-3.0-only |

**Akashic Tome の rewrite 版。読みやすい現代 Java + LGPL ライセンス**。Akashic Tome (CC-BY-NC-SA) より参考実装として優秀。
- 警告事項として "Tome of Helmets" 等 vanish/break する item は格納すると Eccentric Tome ごと消える → **PEB は本以外を厳しく弾く必要 (Pouch 内 Pouch 禁止と同じ原則)**

### B-3. Apotheosis Totem (5.8k DL, 1.20.1 + 1.21.1)

**PEB と完全同型 pattern の先行実装**。対象 Totem は単一 NBT 種類なので、PEB の「Entry by (enchantId, level, isCurse)」より単純だが、collection insert/extract API の参考にはなる。
- Curios / Adorned 対応 = 「他 mod のスロット内に置く時の挙動」のヒント

### B-4. Sophisticated Backpacks (12.4M DL, 1.21.11) + Fabric port (7.4M DL)

**4 tier upgrade UX 規範 (kura 仕様の元ネタ)**:
- Wood → Iron → Gold → Diamond → Netherite の段階拡張 pattern
- crafting grid 内で前 tier item + ingot 8 個で囲む UX
- 内容物自動転送 (NBT を新 item に move)
- handheld 時の 3D ブロック風レンダリング pattern
- **Fabric/Forge/NeoForge multi-loader 維持の規範**
- 関連 addon (easier upgrade / emerald upgrade / TAN compat) 多数 = ecosystem 成熟事例

**PEB 実装での直接活用**:
- Upgrade recipe pattern (前 tier 中央 + ingot 8 個外周) はそのまま流用
- Handheld 3D model 実装パターン参照
- 内容物転送のテンプレ実装が公開 mod source で確認可能

### B-5. Tom's Simple Storage (46.4M DL, 1.21.11) + Fabric (25.4M DL)

**vanilla 風 storage GUI 規範**:
- 検索バー実装パターン
- スクロール可能 list
- ターゲット層: 「Refined Storage / AE2 が overkill な vanilla 派」 — **PEB のターゲット層と完全一致**
- Sodium / Optifine 互換性維持の作法

### B-6. Bookshelf Inspector (1.28M DL, 1.21+)

**Chiseled Bookshelf に何が入ってるか hover で確認できる**。
- vanilla widget で tooltip 出す UX
- 「本棚の中身を見たい」需要が 1.28M DL を生む証拠 = PEB の市場存在証明

### B-7. Librarian Trade Finder (231k DL, 1.19.2-1.21.11)

**司書交易検索 UX 規範**。PEB の司書交易追加とは別軸 (こちらは「司書から欲しい本を見つける」、PEB は「本棚自体を司書から買う」) だが、UI 設計の参考。

### B-8. Universal Shelf (83 DL, 1.21.1)

**Chiseled Bookshelf に任意 item 格納可** — 隣接アイデア (ニッチだが存在証明)。PEB は item 化アプローチで完全差別化。

---

## C. 隠れた gap 検証

### C-1. 「異種エンチャ本を 1 スロットに大量、item ベース、持ち歩き」を解いてる mod

**1.21.1 / 1.20.1 共に該当 0 件** を確認。
最も近い Enchanted BookShelf (jorgej_ramos) は **block 設置式 (2 ブロック高、固定)** で「持ち歩き」「item」要件を満たさない。
Apothic / Apotheosis / EL at Home / EL Standalone は全て **block + ポイント変換式** で「物理本維持」「持ち歩き」「item」要件全て満たさない。
Akashic Tome / Eccentric Tome は item ベースだが対象が **documentation book** のみで enchanted book 未対応。
Apotheosis Totem は item ベース multi-NBT 集約だが対象が **Totem** のみ。

**結論**: PEB の core 仕様 (異種エンチャ本 + item + 持ち歩き + 4 tier + tree GUI + 司書交易) は誰も解いていない。

### C-2. 周辺で「これで足りる」回避策の網羅性

司書ファーム民が現状取れる選択肢:

| 回避策 | 効果 | 残る問題 |
|---|---|---|
| Apothic / Apotheosis | block で point 変換、検索可 | 物理本失う、block 設置必要、Forge/NF のみ |
| EL at Home (1.20.1) | 同上 | 1.20.1 Forge only |
| Enchanted BookShelf (jorgej_ramos) | block で 1000 冊保管、検索可 | 物理本維持◯だが block 固定、NeoForge 1.21.1 only |
| Stacks Are Stacks | enchant 本も 64 stack | 同種だけ、異種は別 stack |
| Limitless Enchantments | level cap 撤廃 | 保管解決せず |
| Bookshelf Inspector | bookshelf 内可視化 | 容量増えず、検索のみ |
| Akashic Tome / Eccentric Tome | 多 doc book を 1 item | enchanted book 非対応 |
| 大型 storage (Tom's / SBP / SS / RS / AE2) | 大量保管 | enchant 本特化検索 UI なし、grid 表示で見つけにくい |

PEB が埋める **残り gap**:
1. **持ち歩き形態 (item)** — Enchanted BookShelf も含めて誰も解いてない
2. **物理本維持 + 検索 + tree 階層** — 物理本維持系の保管 mod に検索 GUI ない、検索ある mod は全部 point 変換式
3. **4 tier upgrade で容量段階拡張** — エンチャ本特化では誰もやってない
4. **司書交易追加 (本棚を司書から買う)** — vanilla や全競合とも未追加
5. **multi-loader 5 環境 (Fabric 1.21.1/1.20.1 含む)** — Fabric 帯にはこの種の mod が皆無
6. **日本語 native 対応** — 全競合英語 only
7. **modded enchant 動的対応** — Apothic 連携前提の競合は modded enchant の扱いが不安定

### C-3. 「市場に該当無し」確証強度

検索した検索クエリ群 (Modrinth + CurseForge 両方):
- enchanted book storage / pouch / bundle / library / vault / chest / box / tome / codex
- book pouch / book bag / book vault / book library / book holder / book stacker / book container
- enchanted book stacker / enchanted book manager / enchanted book autocrafter
- spell book storage / enchant vault / enchanting storage
- portable bookshelf / bookshelf portable / bookshelf inventory
- chiseled bookshelf
- akashic tome / eccentric tome / ancient tome / tome enchantment / tome storage
- apothic enchanting / apotheosis / zenith
- sophisticated backpacks (関連 ecosystem)
- enchant combine / merge enchantments / enchanted vault / loot pouch / ender pouch
- storage pouch / condense items / item stacker enchanted
- librarian trade

**カバレッジ判定**: かなり広い検索範囲、複数同義語 + 関連領域も網羅。検索漏れリスクは低い。

---

## ⑤ 生存性チェック (主要競合の今後拡張可能性)

| 競合 | 拡張兆候 | PEB 領域に来る確率 |
|---|---|---|
| Enchanted BookShelf (jorgej_ramos) | source 非公開・GitHub アカウント空・DL 225 で低活性。2026-01 以降更新なし | **低** (持ち歩き化・multi-loader 移植は来そうにない) |
| Apothic Enchanting | 18.7M DL でアクティブ (2026-05 更新)。Library は core 機能、Shadows_of_Fire は持ち歩き化はやらない方針 (block design 哲学) | **極低** |
| Apotheosis | 122M DL、modular design、新機能追加は別 module 形式。Library 持ち歩き化は thematic に矛盾 | **極低** |
| EL at Home (Syi-I) | 2025-07 最終更新、1 open issue、roadmap 未公開、1.21 移植も Fabric 移植も言及なし | **低** |
| EL Standalone (Sotnah) | 2026-04 更新、active だが NeoForge 1.21.1 only、思想は point 変換式維持 | **低** (持ち歩き化は思想転換が必要) |
| Apotheosis Totem | 2026-05 更新、Totem 対象のまま enchant 本に拡張する理由ない | **極低** |
| Mojang vanilla Bundle | 「全 item 共通容量公式」維持 → エンチャ本特化はやらない (kura SPEC 通り) | **極低** |

**総合**: 主要競合の PEB 領域侵食リスクは **全体的に低**。短期 (1 年) では PEB の差別化軸 (item / 持ち歩き / 4 tier / 司書交易 / multi-loader + Fabric / ja / modded enchant) は維持できる。

ただし **Enchanted BookShelf (jorgej_ramos)** の存在を見落とすと「ほぼ同コンセプト先行 mod を知らずに作った」と見られかねないため、STORE_BODY で「block 派の Enchanted BookShelf に対して item 持ち歩き派の選択肢を提供」と明示するのが誠実かつ差別化アピールにも有効。

---

## 補足: kura のサクッと検索を補強した発見

kura メモの「Modrinth + CurseForge で 1.21 類似 0 件」の見落とし:

1. **Enchanted BookShelf (jorgej_ramos, CF 225 DL, 1.21.1 NeoForge)** — kura 検索で見落とし。コンセプトはほぼ同じ (block / 1000 冊 / 検索 GUI / enchant index)。**最重要警戒対象**。
2. **Enchantment Library at Home (CF 1.1M DL, 1.20.1 Forge)** — kura メモにない。1.20.1 帯ではデファクト。思想は point 変換式で住み分け可能だが、ターゲット層の一部 (Apotheosis 派) は既にこれで満足している可能性。
3. **Enchantment Library Standalone (Sotnah, Modrinth 256 / CF 496)** — kura メモにない。Apothic から分離した 1.21.1 NeoForge 版、新規だが低 DL。
4. **Apothic Enchanting (CF 18.7M DL)** と **Apotheosis (122M DL)** に Library 機能が内包されている事実 — 1.21.1 / 1.20.1 帯の Forge/NF ユーザーの大半がこれで足りている現実を認識すべき。
5. **Apotheosis Totem (Modrinth 5.8k DL)** — PEB と発想完全同型の先行実装、対象だけ違う。**PEB の着工根拠補強** (この pattern が市場で受け入れられてる証拠)。

これらを踏まえても **PEB の差別化軸は十分残っている** (item 持ち歩き / 4 tier / tree GUI / 司書交易 / multi-loader / Fabric / ja / modded enchant 動的) ため **着工 GO 判定**。ただし STORE_BODY と README で先行 mod へのリスペクトと明確な差別化説明を入れることを推奨。

---

## 出典 (主要 URL)

- Modrinth: https://api.modrinth.com/v2/search (網羅検索、20+ クエリ)
- CurseForge: https://www.curseforge.com/minecraft/search (網羅検索、10+ クエリ)
- Enchanted BookShelf: https://www.curseforge.com/minecraft/mc-mods/enchanted-bookshelf
- Enchantment Library at Home: https://www.curseforge.com/minecraft/mc-mods/enchantment-library-at-home
- Enchantment Library Standalone (Modrinth): https://modrinth.com/mod/enchantment-library-standalone
- Enchantment Library Standalone (GitHub): https://github.com/Sotnah/Enchantment-Library-Standalone
- EL at Home (GitHub): https://github.com/Syi-I/EnchantmentLibraryAtHome
- Apothic Enchanting: https://www.curseforge.com/minecraft/mc-mods/apothic-enchanting
- Apotheosis: https://www.curseforge.com/minecraft/mc-mods/apotheosis
- Apotheosis Totem: https://modrinth.com/mod/apotheosis-totem
- Akashic Tome: https://modrinth.com/mod/akashic-tome
- Eccentric Tome: https://modrinth.com/mod/eccentric-tome
- Sophisticated Backpacks: https://modrinth.com/mod/sophisticated-backpacks
- Tom's Simple Storage: https://modrinth.com/mod/toms-storage
- Bookshelf Inspector: https://modrinth.com/mod/bookshelf-inspector
- Handy Bookshelf: https://modrinth.com/mod/handy-bookshelf
- Librarian Trade Finder: https://modrinth.com/mod/librarian-trade-finder
- Universal Shelf: https://modrinth.com/mod/universal-shelf

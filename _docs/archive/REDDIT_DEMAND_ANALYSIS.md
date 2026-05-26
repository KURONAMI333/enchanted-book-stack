---
title: Portable Enchanted Bookshelf — Reddit 需要調査
created: 2026-05-25
researcher: Win Claude (background agent)
sources: old.reddit.com search.json + WebSearch (CurseForge / Modrinth)
period_scope: 直近 1-3 年 (2024-2026, 1.20.1+ 系)
status: draft (kura レビュー待ち)
---

# Portable Enchanted Bookshelf — Reddit 需要調査

## サマリ (4段階評価)

**痛みの存在: 強 (高 upvote 痛みスレ複数 + 自作願望コメント複数)**
**PEB 仕様 (持ち歩き本棚) への需要: 中**

エンチャント本の収納・識別の痛みは確実に広く共有されている (chiseled bookshelf の tooltip 不在を訴える 629 upvote スレ、「数百冊ハマって苦しい」系投稿が散発、CreateMod でも「皆どう整理してる？」スレに 55 upvote)。しかし **「持ち歩ける本棚が欲しい」** という形での具体的需要表明は、本調査範囲内では**直接ヒットしなかった**。検出されたのは:

- (A) 「視覚で本の中身を識別したい」(tooltip / glint / sort) — 最強シグナル
- (B) 「異種エンチャント本を 1 箇所に大量収納したい」 — 中シグナル
- (C) 「持ち歩きたい」 — 直接シグナルなし。ただし類似の "carry your X" 系の痛み (`i got mending and i left my anvil at home`, 1195 up) が存在し、modded プレイヤーが既存 mod (Giacomo's Bookshelf 444K DL) を選んでいる傍証あり

PEB の「持ち歩き」「tier 拡張」「tree 展開 GUI」のうち、**GUI による識別** が最も Reddit シグナルと一致。**「持ち歩き」自体は SPEC 当初想定より直接シグナルが弱い** ので、コンセプト訴求は「持ち歩き本棚」より「**エンチャント本ライブラリ (持ち運び可能)**」寄りの方が市場と合う可能性が高い (epistemics: これは agent の解釈であって実証ではない)。

---

## 強い需要シグナル (関連スレ TOP)

### 1. chiseled bookshelf に tooltip がない問題 (★最強シグナル)
- URL: https://reddit.com/r/Minecraft/comments/1t882pb/the_chiseled_bookshelf_really_needs_tooltips_for/
- 投稿日: 2026-05-09 / r/Minecraft
- **629 upvote / 69 comments**
- 主訴: 「本棚から本を全部取り出して、E 押してホバーしないと中身がわからない。これじゃ chiseled bookshelf は実質使えないから chest に戻してる」
- 注目コメント抜粋:
  - [290 up] eyeCsharp: "if you're using them for functionality, chests are always better since they have more storage space"
  - [89 up] Burger_Bell: "Just use 1 kind of enchantment per shelf. ... maybe put them in a shulker box instead"
  - [77 up] Tuckertcs: "Bro we need more storage options, why can't this be one, when it's literally meant to be?"
  - [13 up] minequack: "In real life, you can read book titles on a shelf. You shouldn't have to take it off the shelf to see what it is."
- → PEB の **GUI 内エンチャント識別** はこの痛みに**直撃**

### 2. 「皆どうエンチャント本整理してる？」 (Create コミュニティ)
- URL: https://reddit.com/r/CreateMod/comments/1m9c60n/got_this_idea_to_make_a_library_to_organize/
- 投稿日: 2025-07-25 / r/CreateMod
- **55 upvote / 8 comments**
- 主訴: 「ライブラリを作ろうと思った。みんなはどう整理してる？」
- 注目コメント:
  - [10 up] trainman1000: chiseled bookshelf + packager + stock links (= AE2 風自動仕分け) を勧める
  - [3 up] NotBentcheesee: **"I throw them in the chest and spend sixteen minutes finding the one I want because I refuse to install a resource pack to customize them"** ← まさにこの 16 分を PEB は解決する
  - [1 up] Dangerous-Quit7821: **"I was thinking about doing this as well."** ← 自作願望表明
- → ライブラリ構築の手間と中身識別の痛みが両方出ている

### 3. 「エンチャント本を見えるようにする mod ある？」
- URL: https://reddit.com/r/Minecraft/comments/1np7q12/mod_for_showing_what_enchantment_a_book_has/
- 投稿日: 2025-09-24 / r/Minecraft
- 0 upvote (低エンゲージだが内容直球) / 3 comments
- 主訴: **"I desperately need a way to organize enchanted books, as I need to manually hover over each and every single one to see what enchantment it has, and it is a pain to make max level enchantments. I have chests full of them."**
- 解決策として `better enchanted books` (resource pack) を推薦されただけ → mod 単独解はまだ薄い
- → PEB の tree GUI が直接解になるパターン

### 4. 「エンチャント本を見たい」 (ATM10)
- URL: https://reddit.com/r/allthemods/comments/1o4tho0/a_way_to_see_my_enchanted_books/
- 投稿日: 2025-10-12 / r/allthemods
- 4 upvote / 4 comments
- 主訴: 「ストレージシステム上で全部同じに見える、エンチャント種類別に sort してくれるライブラリない？」
- 推薦された既存 mod: **Apotheosis "Library of Alexandria"** (ATM 内蔵)
- → modded 民は既存ソリューション (Apotheosis) が満たしている領域、PEB が ATM 系 modpack に入る余地は要慎重評価

### 5. エンチャント本の整理 (vanilla / Bedrock)
- URL: https://reddit.com/r/Minecraft/comments/1n7rjip/enchanted_book_organization_storage/
- 投稿日: 2025-09-03 / r/Minecraft
- 0 upvote / 4 comments
- 主訴: 「fishing で本どんどん溜まる。マルチエンチャント本の整理どうすれば？」
- コメント: chiseled bookshelf に枚数別 sort + 案内本を入れる工夫 / hopper 自動補充
- → 工夫で凌いでいるが GUI 一発解はない

### 6. lectern reroll 苦行と enchant trade 改革 (★ メタ的に重要)
- URL: https://reddit.com/r/Minecraft/comments/1rq4b8a/finally_my_2011_idea_for_enchantments_trades_is/
- 投稿日: 2026-03-10 / r/Minecraft
- **158 upvote / 56 comments**
- 主訴: 「2011 年以来の願望: lectern を 2000 回壊して reroll するのをやめたい。datapack でマスター司書に本を覚えさせる仕組みを作った」
- > "Currently? It takes me, on average, over **2,000 lectern place/breaks** to RNG my way to getting all tradable enchantments. Not...fun...or...rewarding."
- 関連機能: **「マルチエンチャント本を grindstone + 通常本で個別本に分離」** という同 datapack の機能 → 「マルチエンチャント本の扱いに困っている」需要の裏付け
- → 司書ファーム周りの「本が大量に溢れる」状況が広く共有されている

### 7. trade rebalance 対応ライブラリ建築
- URL: https://reddit.com/r/Minecraft/comments/1qcolsy/prepping_for_the_trade_rebalance/
- 投稿日: 2026-01-14 / r/Minecraft
- **883 upvote / 133 comments**
- 主訴: 「trade rebalance だと biome 別に司書を集めないといけない。World Library として biome ごとに司書を住まわせる建築を作った」
- → 「ライブラリ建築をしたい」需要は強い (上記 #2 の `library to organize` と同方向)
- ただし「持ち歩きたい」ではなく「**置き場所**としてのライブラリ」需要

### 8. enchant 本ライブラリ建築 (vanilla)
- URL: https://reddit.com/r/Minecraft/comments/1nz2ixx/i_built_a_library_to_host_almost_all_the/
- 投稿日: 2025-10-05 / r/Minecraft
- **298 upvote / 34 comments**
- 主訴: Trinity College 風に「ゲーム内ほぼ全エンチャント本を置く図書館を建築」
- → 「全種コレクションして展示したい」需要のシグナル

---

## 中程度の言及

| URL | 主訴 | 評価 |
|---|---|---|
| https://reddit.com/r/Minecraft/comments/1sj97mu/i_got_mending_and_i_left_my_anvil_at_home/ (1195 up) | 「mending 本拾ったのに anvil 家に置いてきた」 | エンチャント本ではなく anvil の話だが、**「持ち歩けない不便」** という類似シグナル |
| https://reddit.com/r/Minecraft/comments/1mcbs71/spent_4_days_grinding_123_xp_levels_and_this_is/ (727 up) | XP 大量消費 + 望まない結果 | エンチャント周りの不満一般 |
| https://reddit.com/r/Minecraft/comments/1okrvwz/improved_inventory/ (3 up) | 大量本対応のインベントリ改善 mod 紹介 | 直接的だが低エンゲージ |
| https://reddit.com/r/allthemods/comments/1oj9g8d/how_do_you_combine_extremely_highlevel/ (18 up) | 「ATM10 で超高 level の本をどう combine」 | 本扱いの操作性需要 |
| https://reddit.com/r/Minecraft/comments/1q14n2q/401_bookshelves_is_a_set_that_adds_401/ (9657 up) | 「本棚 401 種類追加 mod」 | 本棚装飾自体への興味は爆発的に高い (装飾文脈、機能ではない) |

---

## ユーザーが既存で使ってる回避策

調査範囲で頻出した回避策:

1. **chest にぶち込む** — 一番多い。tooltip は出るが大量だと検索不能 (上記 #1, #5)
2. **chiseled bookshelf を種類別 / level 別に並べる** — 工夫派。本棚に案内 (book and quill) を 1 冊入れて目録化 (1n7rjip コメント)
3. **resource pack で texture を変える** (`better enchanted books` 等) — 一冊単位の識別だけ解決、収納問題は残る
4. **shulker box** — 持ち歩き派の頂点。ただし開いて hover が必要 (1m9c60n, 1t882pb コメント)
5. **AE2 / RS + chiseled bookshelf + packager** (Create / ATM) — 自動仕分けの極致、ライブラリ規模が必要
6. **Apotheosis "Library of Alexandria"** (ATM modpack 標準) — modded で最も体感価値の高い既存解
7. **datapack で本に grindstone + 通常本で分離 (Testificate_2011 datapack)** — マルチエンチャント本の扱い改善

---

## 既存 mod (PEB 直接競合 / 隣接)

WebSearch (CurseForge / Modrinth) で発見したもの:

| Mod 名 | DL | 最終更新 | 対応 | できること | PEB との関係 |
|---|---:|---|---|---|---|
| **Giacomo's Bookshelf** | **444,975** | 2024-07-25 | 〜1.20.4 Forge/NeoForge | **本/地図/紙を保管するコンテナ本棚。silk touch で中身保持して持ち運び可。エンチャント本対応。エンチャントテーブルへの power 供給も** | **PEB 核機能 (持ち歩ける本棚) と直接競合**。ただし 1.20.4 止まり、1.21+ 未対応 |
| Enchanted BookShelf | 225 | 2026-01-26 | 1.21/1.21.1 NeoForge | 1000冊収納、検索機能、マルチエンチャント本の個別エンチャント抽出 | 1.21 系の同コンセプト。低 DL なので普及していない |
| Bibliocraft Bookcase | (historical) | 1.12 系まで | 古いバージョン | GUI で本棚に本を入れる伝統 mod | 1.20+ 不在、レガシー |
| Handy Bookshelf | 337 | 2025+ | 1.21.1 Fabric | chiseled bookshelf 内エンチャント本に glint + 名前 hover 表示 | 視覚改善のみ、収納増なし |
| Enchanted Bookshelves | **168,200** | ~2024 | 1.21 Fabric | chiseled bookshelf 内のエンチャント本に視覚 glint | 視覚改善のみ。**装飾系で 16万 DL** という事実は「視覚で本を見分けたい需要」の強さを裏付け |
| Apotheosis Library of Alexandria | (ATM 同梱) | active | 1.20+ | エンチャント本の大規模ストレージ + 検索 + tier up 機能 | ATM modpack 内では事実上の標準解 |

**重要な observation**:
- Giacomo's Bookshelf 44万 DL は、「本棚を中身ごと持ち運びたい」需要が**実証済み**であることを示す (該当 mod が 1.20.4 で更新止まりなのに DL 数が積み上がっている)
- ただし「持ち歩く」は手段で、目的は「ライブラリの引っ越し / セカンドベース化」の可能性。Reddit 上で直接「持ち歩きたい」が高 upvote で出ていない点と整合的
- Enchanted Bookshelves (視覚 glint だけで 168K DL) は **視覚識別需要の強さ** を強く示す。PEB の tree GUI 価値は高い

---

## kura PEB との fit (率直評価)

### 強く当たる仕様
- **tree 展開 GUI による種類別集約 + level breakdown** ← 最も強い Reddit 需要 (629 up "chiseled bookshelf needs tooltips" 直撃)
- **modded enchant への動的対応 + level X (10) 等の拡張** ← ATM 民の "high level book combine" 需要に響く
- **異種エンチャント本 1 スロット大量収納** ← 「chest に投げて 16 分探す」(NotBentcheesee) の置換に直接価値
- **検索バー** ← `$unbreaking` 等で検索したいという ATM 民のコメント (1o4tho0) と一致

### 弱い / 再検討余地ある仕様
- **「持ち歩き」自体のフッキング** ← Reddit で直接「本棚を持ち歩きたい」と訴える高 upvote スレは本調査範囲では**検出されず**。コンセプト訴求としては「持ち歩き」より「**1 スロットに収まる携帯ライブラリ**」「**チェスト 1 個分の本を 1 スロットに**」の方が痛みに直撃しやすい (epistemics: agent の解釈、実証ではない)
- **4 tier (64/256/1024/4096)** ← Tier 進行は modded 民は好む傾向だが、Reddit シグナルでは tier への言及なし。1024/4096 は Giacomo's Bookshelf や Apotheosis Library of Alexandria の規模感と被る
- **Apotheosis 互換性確認必須** ← ATM 系で Library of Alexandria が事実上標準。PEB が ATM modpack に入る差別化が必要 (Apotheosis は据え置き型、PEB は携帯型 = ニッチは存在)

### SPEC 修正候補 (kura レビュー材料)
1. **競合表の更新必須**: SPEC §2 「既存の不在」の記述 (>1.21 系で「異種エンチャント本を 1 スロット大量収納」mod **0 件**) は、Enchanted BookShelf (1000冊, 1.21 NeoForge, 225 DL) という反例があるため**厳密には不正確**。「低 DL かつ機能差別化可」と書き換えるのが誠実
2. **Giacomo's Bookshelf の取り扱い**: 1.20.4 で更新止まりだが 44万 DL の事実は、(a) 競合と捉えるか (b) 「同コンセプトが 1.21 で空席化している」と捉えるかで戦略が変わる。kura 判断材料
3. **訴求ワード再考**: 「持ち歩き本棚」より「**Pocket Enchanted Library**」「**Bookshelf-in-a-slot**」のような「インベントリ圧縮」フッキングの方が Reddit 痛みと一致する可能性

### epistemics 上の留保
- 上記 fit 評価は本調査 (検索クエリ十数件、確認スレ 15-20 件、確認 mod 6 件、合計 1 時間程度) に基づくもの。**包括的市場調査ではない**
- 「直接シグナルなし」は「需要がない」を意味しない (検索キーワードが拾えていない可能性、日本語コミュニティ未調査、Discord / Minecraft Forum 未確認)
- 「強」「中」「弱」評価は upvote の絶対値とテーマ関連度から agent が判定したもので、実際の購買/採用率は未検証

---

## 検索した query 一覧 (再現性確保)

old.reddit.com search.json 経由 (subreddit 内、relevance sort, t=year または t=all):

- r/feedthebeast: "enchanted book storage", "librarian farm storage", "portable bookshelf", "tome enchant consolidate", "enchanted book storage mod", "portable enchanting"
- r/Minecraft: "enchanted book inventory too many", "villager trading books organize", "chiseled bookshelf enchanted", "bundle enchanted book", "enchanted book shulker box", "enchanted book organize library", "enchant book inventory full"
- r/allthemods: "enchanted book storage"
- r/CreateMod: "enchanted book storage"

WebSearch (Google 経由):
- `site:reddit.com "enchanted books" "too many" inventory minecraft modded 2024 2025`
- `minecraft mod "portable bookshelf" OR "carry bookshelf" enchanted books`
- `reddit minecraft "villager trading hall" "too many books" librarian organize`

未調査 (時間都合):
- r/feedthememes (痛みネタ専門)
- 日本語コミュニティ (5ch, X, 個人ブログ)
- Minecraft Forum, CurseForge コメント欄 (個別 mod ページ)
- Discord (アクセス困難)

---

## 次アクション提案 (kura 用)

1. **SPEC §2 の競合 mod 表を更新** (Giacomo / Enchanted BookShelf / Enchanted Bookshelves / Handy Bookshelf を追加し差別化軸を明文化)
2. **訴求軸の再選択**: 「持ち歩き」 vs 「インベントリ 1 スロット」 vs 「視覚識別ライブラリ」 の優先度を再考
3. **Apotheosis Library of Alexandria の機能をデータ確認** (PEB が ATM modpack に入る余地評価のため)
4. (option) r/feedthememes と日本語コミュニティの追加調査 (各 15 分)

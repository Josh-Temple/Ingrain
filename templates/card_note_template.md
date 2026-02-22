# Card/Note Template (Markdown + YAML front matter)

`Ingrain` の推奨インポート形式は **Markdown + YAML front matter** です。  
複数カードは `===` で区切ります。

## 必須ルール
- 各カードは先頭に YAML front matter を置く
- 必須キー: `deck`, `tags`
- 本文は `### Front` と `### Back` の両方を含む
- カード同士の区切りは `===`（単独行）

## テンプレート
```markdown
---
deck: "<Deck Name>"
tags: ["tag1", "tag2"]
---

### Front
<question>

### Back
<answer>
===
---
deck: "<Deck Name>"
tags:
  - tag1
  - tag2
---

### Front
<another question>

### Back
<another answer>
```

## メモ
- Import は JSON Lines も後方互換として利用可能です。
- `deck + front + back` が重複する行（カード）はスキップされます。
- Markdown は CSS ではなく、スタイルトークン（heading3 / strong / emphasis / list / paragraph）にマッピングして描画します。

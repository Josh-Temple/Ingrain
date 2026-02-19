# Card/Note Template (JSON Lines)

`Ingrain` のインポート形式は **JSON Lines（1行=1カード）** です。

## 必須フィールド
- `deck`: string
- `front`: string
- `back`: string

## 任意フィールド
- `tags`: string[]
- `due_at`: number (epoch millis)
- `interval_days`: number
- `ease_factor`: number
- `repetitions`: number
- `lapses`: number

## テンプレート
```json
{"deck":"<デッキ名>","front":"<表面の問題文>","back":"<裏面の答え>","tags":["<タグ1>","<タグ2>"]}
{"deck":"<デッキ名>","front":"<表面の問題文>","back":"<裏面の答え>","due_at":1700000000000,"interval_days":1.0,"ease_factor":2.5,"repetitions":0,"lapses":0}
```

## メモ
- 1行ごとに独立して処理されます。
- `deck + front + back` が重複する行はスキップされます。
- 不正な scheduling 値は無視され、新規カード扱いになります。

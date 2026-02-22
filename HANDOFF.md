# HANDOFF

## Documentation policy
- Keep all in-app UI copy in English.

## Current state summary
- Deck一覧の下部ナビゲーションは `ADD` 表示で、タップ時に「Add deck / Add card」を選択するダイアログを表示します。`Add card` はグローバル追加画面（`import-global`）に遷移します。
- カード追加画面の `Target deck` は任意選択です。`None` 選択時は deck 名入力（または JSON Lines / Markdown側の `deck` 指定）で複数デッキへ追加できます。
- デッキ一覧/デッキ詳細のどちらからカード追加画面へ遷移しても、`Target deck` はデフォルト未選択です（デッキ詳細遷移時は deck 名の初期候補のみセット）。

- Study画面は front を上寄せ表示、`PROGRESS/cards` と旧 `STUDY` タグ表示を削除、ヘッダーにデッキ名表示、表裏境界線を細く調整済みです。
- Study画面は上方向スワイプで `Edit Card` に遷移できます。

- Import/Export は Markdown + YAML front matter と JSON Lines の両対応です。
  - Markdownカードは `---` front matter + `## Front` / `## Back` + `===` 区切りを標準としています。
  - パーサーは後方互換のため `### Front` / `### Back` も受理します。
- Backup/Restore のエクスポート形式は Markdown（`ingrain-backup.md`）です。
- Renderer は CSS ではなくスタイルトークン（heading3 / paragraph / list / strong / emphasis）で表示します。

- Add Card の `Templates` では、`Markdown` / `JSON Lines` を選択して `Copy template` できます。
- 書式ガイドは Decks 画面右上 `Menu` から開く「App formatting guide」に集約しました（H3/Bold/Italic/List の説明 + コピー）。

## This session (latest)
- `Screens.kt` をリファクタリングし、Decks画面の書式ガイドダイアログを `AppFormattingGuideDialog` に分離しました。
- 書式サンプルを定数の重複定義から `formattingExamples` リスト（`FormattingExample`）へ統合し、表示とコピー処理をループで共通化しました。
- HANDOFF 全体を現仕様へ更新し、古い `###` 固定前提の記述を削除/修正しました。

## Next steps / plan
1. Markdown card syntax v1 ドキュメントを最終固定する。
   - 現在実装: YAML front matter (`deck`, `tags`) + `## Front` / `## Back`（`###` 互換受理）+ `===`
   - 残タスク: エラー文言/行番号精度の微調整、テンプレート文書との完全同期
2. Parser/Validator を段階的に強化する。
   - 現在実装: 必須セクション不足/不正front matter/空Front/Backの検知
   - 残タスク: YAMLエスケープ・複雑構造など厳密ケースの扱い
3. Renderer方針を拡張する。
   - 現在実装: h3, bold, italic, list, paragraph
   - 残タスク: 複数段落間スペーシング、コード/引用サポートの判断
4. Data migration方針を確定する。
   - 現状: DBは `front/back` 文字列保持、Import/ExportでMarkdown入出力
   - 残タスク: AST/JSONキャッシュ導入の要否判断

## Notes for next session
- `Target deck` は現状ボタン列。Deck数が増える場合は `ExposedDropdownMenuBox` 等への切替を検討。
- この環境では Gradle 実行時に JDK/Gradle 互換性エラー（`Unsupported class file major version 69`）が発生するため、CI またはローカルJDK整備済み環境でのビルド確認が必要。


## Known issues / product challenges
- **UI責務の集中**: `Screens.kt` が複数画面（Decks/Detail/Import/Study/Edit/Settings/Backup）を抱えており、変更影響が読みにくくなりやすい。
- **Menu情報設計の混在**: `App formatting guide` に Markdown書式ガイドとグローバル見た目設定（Accent / Button size）が同居しているため、目的別導線としては再整理余地あり。
- **テーマ設定粒度の不足**: 現在の全体スタイルは主に `primary` と `shapes.small` への反映で、他の視覚要素（surface/typography等）との一貫したカスタマイズ方針が未整理。
- **Study操作の発見性**: タップで解答表示・上スワイプで編集というジェスチャー中心設計は、初見ユーザーに気づかれにくい可能性あり。
- **Import失敗時の可観測性**: `importParsed` で例外を集約して `failed++` する箇所があり、原因調査やUIへの詳細表示が弱い。
- **ビルド再現性**: 現環境では Gradle 実行時に `Unsupported class file major version 69` が発生し、ローカル検証が不安定。

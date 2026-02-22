# HANDOFF

## Documentation policy
- Keep all in-app UI copy in English.

## This session
- Deck一覧の下部ナビゲーションを `ADD` 表示に変更し、タップ時に「Add deck / Add card」を選択するダイアログを追加しました。`Add card` はグローバル追加画面（`import-global`）へ遷移します。
- カード追加画面の `Target deck` を固定表示から任意選択に変更しました。`None` を含む選択UIを追加し、未選択時は従来どおり Deck 名入力（または JSON Lines の deck 指定）で複数デッキへ一括追加できる状態にしています。
- デッキ一覧/デッキ詳細のどちらからカード追加画面へ遷移した場合でも、`Target deck` はデフォルト未選択にしました（デッキ詳細遷移時は deck 名入力欄の初期候補としてのみ利用）。

- Study画面でカード表面（front）を画面中央寄せから上寄せに変更し、学習時に先頭情報がより上部に表示されるように調整しました。
- Study画面で答え表示時に出ていた「Image-based study is not supported yet...」の文言を削除しました。
- すべての入力欄を `OutlinedTextField` から共通 `AppTextField`（下線スタイル）へ切り替え、四角枠ではなく下線表示に統一しました。

- 学習画面の左上にあった `PROGRESS` / `cards` 文言を削除し、見た目を簡素化しました。
- 学習画面の表裏境界線を極細化（0.5dp）し、左右マージンを抑えて横長に表示するよう調整しました。
- 学習画面の `STUDY` タグ表示を廃止し、デッキ名をヘッダー位置に表示するよう変更しました。
- 学習画面で上方向スワイプすると、表示中カードの編集画面（`Edit Card`）に遷移できるようにしました。

- Markdown + YAML front matter を実装しました。
  - 取り込み時、`---` front matter + `### Front` / `### Back` を持つMarkdownカードを解析できるようにしました。
  - 複数カード区切りは `===` を採用しました（`---` との衝突回避）。
  - `BulkImportParser` を追加し、Markdown形式を優先判定しつつ JSON Lines も後方互換で取り込めるようにしました。
- Backup/Restore を更新しました。
  - エクスポート形式を Markdown に変更（`ingrain-backup.md`、カード区切りは `===`）。
  - インポートは Markdown / JSONL の両対応を維持しました。
- スタイル方針を反映しました。
  - CSSではなくスタイルトークン（heading3 / paragraph / list / strong / emphasis）を使う `MarkdownTokenText` を追加し、Study表示で利用するようにしました。

- 他AI（GPTs / Gems）へカード作成を依頼するための指示文テンプレートを `templates/ai_card_writer_prompt.md` として追加しました。
- テンプレート文書 `templates/card_note_template.md` を Markdown + YAML front matter 仕様へ更新しました。

- Markdownパーサーを再点検してリファクタリングしました。
  - UTF-8 BOM付き入力を吸収し、空入力/空ブロックの扱いを明確化しました。
  - YAML front matter で `tags` キーを必須化し、仕様（deck+tags必須）とのズレを修正しました。
  - `BulkImportParser` の判定を調整し、先頭が `{` の場合はJSON Linesとして優先解釈するようにしました。
- テストを補強しました。
  - `tags` 欠落時にエラーとなるケースを `MarkdownCardsParserTest` に追加しました。
- `templates/ai_card_writer_prompt.md` を再確認し、`deck`/`tags` を必須・引用符付きで出力するテンプレートへ揃えました。

## Next steps / plan (partially implemented)
1. Markdown card syntax v1 を最終固定する。
   - 現在実装: YAML front matter (`deck`, `tags`) + `### Front` / `### Back` + `===`
   - 残タスク: エラー文言/行番号精度の調整、仕様ドキュメントの完全同期
2. Parser/Validator を強化する。
   - 現在実装: 必須セクション不足/不正front matter/空Front/Backの検知
   - 残タスク: より厳密なYAML（エスケープ・複雑構造）対応
3. Renderer方針を拡張する。
   - 現在実装: Markdownサブセット（h3, bold, italic, list, paragraph）
   - 残タスク: 複数段落間スペーシング、コード/引用など拡張可否
4. Data migration方針を確定する。
   - 現状: DBは従来どおり `front/back` 文字列保持、Import/ExportでMarkdown入出力
   - 残タスク: AST/JSON キャッシュの導入判断

## Notes for next session
- Material3 の `ExposedDropdownMenuBox` などへ切り替えると、`Target deck` の選択UIをさらにスケールしやすくできます（現状はボタン一覧）。
- この環境では Gradle 実行時に JDK/Gradle 互換性エラー（Unsupported class file major version 69）が出る場合があるため、CI かローカルJDK整備済み環境でのビルド確認が必要です。

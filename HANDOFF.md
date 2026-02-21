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

## Notes for next session
- Material3 の `ExposedDropdownMenuBox` などへ切り替えると、`Target deck` の選択UIをさらにスケールしやすくできます（現状はボタン一覧）。
- この環境では Gradle 実行時に JDK/Gradle 互換性エラー（Unsupported class file major version 69）が出るため、CI かローカルJDK整備済み環境でのビルド確認が必要です。

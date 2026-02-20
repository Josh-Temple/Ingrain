# Ingrain — AI-friendly Flashcards (personal)
Ingrainは、Ankiの思想を参考にしつつ「登録のしにくさ」を解消するために作る、個人用フラッシュカードAndroidアプリです。  
AIが生成した出力をコピペして大量登録できることを最優先にし、復習ロジックはSuperMemo（SM-2）をベースにしたシンプル版にします。

## Goals
- **単一入力欄**に貼り付け → 自動でカード化（UI上でFront/Backを分けない）
- **AIフレンドリーなインポート**：JSON Lines（1行1カード）
- **手動バックアップ/復元**：エクスポート/インポート
- **デッキ分割**
- 復習アルゴリズム：**SM-2簡易（2択評価）**

## Non-goals (MVPではやらない)
- Anki(apkg)完全互換、同期、共有
- Cloze、画像/音声などのメディア管理
- 複雑な統計や高度なスケジューリングUI

---

# Tech stack (MVP)
- Kotlin
- Jetpack Compose
- Room (SQLite)
- DataStore (Preferences)
- kotlinx.serialization
- Storage Access Framework (SAF) for import/export

---

# User flows
## Decks
- デッキ一覧（作成/名前変更/削除）
- デッキ詳細：Study / Add&Import / Export / Settings

## Add & Import (single input)
- 画面に**1つのテキスト入力欄**のみ（Front/Backに分けない）
- ここにJSON Linesを貼り付け → 行ごとにパース
- プレビュー表示（成功/失敗/エラー理由）
- ImportでDBへ保存（重複はスキップ）

## Study
- デッキの「期限到来（due）」カードを順番に提示
- 表示：Front → Show Answer → Back
- 評価は2択：
  - Again（失敗）
  - Good（成功）

## Backup / Restore
- Export：JSON LinesとしてSAFで選んだ場所へ書き出し
- Import：SAFでファイル選択 → 読み込み → 取り込み（マージ）

---

# Data model (Room)
## Deck
- id (PK)
- name (unique)
- created_at
- updated_at

## Card
- id (PK)
- deck_id (FK)
- front
- back
- tags_json  // JSON array string
- created_at
- updated_at

### Scheduling fields (MVP: Cardに保持)
- due_at: Long (epoch millis)
- interval_days: Double
- ease_factor: Double (default 2.5)
- repetitions: Int
- lapses: Int
- last_reviewed_at: Long?

## ReviewLog (recommended)
- id (PK)
- card_id (FK)
- reviewed_at
- rating ("AGAIN" / "GOOD")
- prev/new: due_at, interval_days, ease_factor

---

# Scheduling algorithm — SM-2 simplified (2-button)
SM-2の品質(0-5)を、2択に合わせて簡略化します。
- GOOD = quality 4 相当
- AGAIN = quality 2 相当

## Settings (configurable)
- initial_interval_good_days (default 1.0)
- second_interval_good_days  (default 6.0)
- min_ease_factor (default 1.3)
- ease_factor_step_good (default +0.05)
- ease_factor_step_again (default -0.20)
- again_delay_minutes (default 10)

## New card defaults
- repetitions = 0
- lapses = 0
- interval_days = 0
- ease_factor = 2.5
- due_at = now

## On GOOD
If repetitions == 0:
  interval_days = initial_interval_good_days
Else if repetitions == 1:
  interval_days = second_interval_good_days
Else:
  interval_days = interval_days * ease_factor

repetitions += 1
ease_factor = ease_factor + ease_factor_step_good
due_at = now + interval_days days

## On AGAIN
lapses += 1
repetitions = 0
ease_factor = max(min_ease_factor, ease_factor + ease_factor_step_again)
interval_days = 0
due_at = now + again_delay_minutes minutes

---

# Import/Export format (AI-friendly)
## JSON Lines (1 line = 1 card)
Each line is one JSON object.

### Required
- deck: string (deck name)
- front: string
- back: string

### Optional
- tags: string[]
- due_at: number (epoch millis)
- interval_days: number
- ease_factor: number
- repetitions: number
- lapses: number

### Examples
{"deck":"English","front":"ubiquitous","back":"(adj) present everywhere; widespread","tags":["vocab"]}
{"deck":"Stats","front":"ベースレート無視とは？","back":"事前確率（ベースレート）を無視して判断する誤り","tags":["cogbias","stats"]}

### Import rules
- deckが存在しなければ作成
- front/backが空ならその行は失敗としてスキップ
- 重複判定：同一(deck + front + back)が存在したらスキップ
- scheduling fieldsが不正なら無視して「新規カード」として扱う

### Export rules
- 全デッキ or 選択デッキをJSON Linesで出力
- scheduling fieldsも出力（完全復元できるように）

---

# Build strategy: develop on phone, build APK in the cloud (GitHub Actions)
スマホ開発を維持しつつ、ビルドの地雷を減らすために **GitHub Actionsでdebug APKを自動生成**します。

## Step 1: Add workflow
Create:
- `.github/workflows/android-debug-apk.yml`

Paste:

```yaml
name: Android Debug APK

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
          cache: gradle

      - name: Restore fixed debug keystore
        run: |
          echo "${{ secrets.DEBUG_KEYSTORE_BASE64 }}" | base64 -d > "$RUNNER_TEMP/debug.keystore"

      - name: Grant execute permission for gradlew
        run: chmod +x ./gradlew

      - name: Build Debug APK (updatable)
        run: |
          ./gradlew :app:assembleDebug \
            -Pandroid.injected.version.code="${{ github.run_number }}" \
            -Pandroid.injected.version.name="1.0.${{ github.run_number }}" \
            -Pandroid.injected.signing.store.file="$RUNNER_TEMP/debug.keystore" \
            -Pandroid.injected.signing.store.password="${{ secrets.DEBUG_KEYSTORE_PASSWORD }}" \
            -Pandroid.injected.signing.key.alias="${{ secrets.DEBUG_KEY_ALIAS }}" \
            -Pandroid.injected.signing.key.password="${{ secrets.DEBUG_KEY_PASSWORD }}"

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: ingrain-debug-apk
          path: app/build/outputs/apk/debug/*.apk


### APK更新が失敗する時の原因と対策
CIで作るdebug APKは、**毎回同じ署名鍵**と**単調増加するversionCode**でないと上書きインストールできません。

上のworkflowは次を満たします：
- `github.run_number` を `versionCode` に使う（毎回増える）
- `DEBUG_KEYSTORE_BASE64` などのSecretsで固定keystoreを復元して署名する（毎回同じ署名）

必要なSecrets（Repository settings → Secrets and variables → Actions）：
- `DEBUG_KEYSTORE_BASE64`
- `DEBUG_KEYSTORE_PASSWORD`
- `DEBUG_KEY_ALIAS`
- `DEBUG_KEY_PASSWORD`

> workflowのデフォルト動作はFail Fastです。
> - Secretsが揃っている: 固定keystoreで署名（既存APKへ上書き更新できる）
> - Secretsが不足している: **通常トリガー（push / pull_request）ではビルド失敗**
>
> 例外として `workflow_dispatch` では `allow_ephemeral_signing=true` を指定すると、
> GitHub Actions標準のdebug鍵でビルドできます（**新規インストール用**）。
> この場合のartifact名は `ingrain-debug-apk-fresh-install-only` です。

> 以前のAPKと署名鍵が違う状態で入っている場合は、一度アンインストールしてから入れ直してください。

Step 2: Download APK

GitHubのActions → 該当run → Artifacts → ingrain-debug-apk をDL

端末へインストール（不明なアプリ許可が必要な場合あり）


If module name is not app

./gradlew :app:assembleDebug が失敗する場合、モジュール名が違う可能性があります。

対応：

workflowのビルドコマンドを ./gradlew assembleDebug にして試す

それでもダメなら ./gradlew tasks をCIで実行してタスク名を確認する



Release (signed) build (later)

個人利用MVPではdebugで十分。必要になったら以下で拡張：

keystoreをGitHub Secretsにbase64で保存

workflow内で復元して assembleRelease を実行

署名情報はSecretsからGradleへ渡す



---

Definition of Done (MVP)

デッキCRUDが動作

JSON Linesを貼り付けインポート（プレビュー/エラー表示つき）

dueカードをStudyでき、Again/GoodでSM-2簡易スケジューリング更新

Export/Import（SAF）でバックアップ/復元ができる

GitHub Actionsでdebug APKがartifactとして出力される

不正入力でもクラッシュしない（すべて防御的に処理）



---

Codex tasks (implementation plan)

Task 0: Project sanity

Kotlin + Compose + Room + DataStore + kotlinx.serialization

Navigation scaffold

Minimal theme


Task 1: Database

Entities: Deck, Card, ReviewLog

DAOs

Repository


Task 2: Scheduler

SM-2 simplified module

DataStore settings

Unit tests with fixed timestamps


Task 3: Import/Export

JSON Lines parser (line-by-line, defensive)

Preview models

Import executor (create deck, dedup, insert)

Export writer (stream JSON lines)


Task 4: UI

DeckListScreen

DeckDetailScreen

AddImportScreen (single text area + preview + import summary)

StudyScreen (front/back toggle, Again/Good)

SettingsScreen (interval/EF params)


Task 5: GitHub Actions

Add .github/workflows/android-debug-apk.yml

Ensure artifact upload path matches actual output


Task 6: Polish

Empty states

Basic search (optional)

Performance sanity (paging不要。MVPはシンプルに)

---

## 次回セッションへの引き継ぎ項目
- GitHub Actionsで `Android Debug APK` workflow を1回実行し、`ingrain-debug-apk` artifact が実際に生成されることを確認する。
- Import/Export系（JSON Linesの異常入力、SAFファイルI/O失敗時）のテストを追加し、防御的実装の回帰を防ぐ。
- UIを画面別ファイルへさらに分割（`Screens.kt` の分離）し、保守しやすい構成にする。
- READMEのDefinition of Doneに対するチェックリストをIssue化し、完了証跡（スクリーンショット/ログ）を残す。


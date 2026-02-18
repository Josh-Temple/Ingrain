# Ingrain — AI-friendly flashcard app (personal use)
Ankiの思想を参考にしつつ、「登録のしやすさ」と「AI出力からの高速インポート」に最適化した個人用フラッシュカードAndroidアプリ。

## Product goals
- 1つの入力欄に貼り付け → 自動でカード化（Front/BackをUI上で分けない）
- AIが生成しやすい形式（JSON Lines）で一括インポート/エクスポート
- シンプルな復習ロジック：SuperMemo (SM-2) をベースにした簡易アルゴリズム
- デッキ分割
- 手動バックアップ（エクスポート）/ 手動復元（インポート）

## Non-goals (MVPではやらない)
- Anki(apkg)完全互換
- Cloze、画像/音声メディア、リッチエディタ、共有/同期、複雑な統計

---

# 1) Tech decisions (MVP)
- Language: Kotlin
- UI: Jetpack Compose
- Local DB: Room (SQLite)
- File access: Storage Access Framework (SAF)
- JSON: kotlinx.serialization

> NOTE: AIDE環境を想定しつつも、依存関係は最小化。Compose/Room/serialization のみに寄せる。

---

# 2) User flows
## 2.1 Decks
- Deck list screen
  - Create deck
  - Rename deck
  - Delete deck (with confirm; delete its cards)
  - Tap deck → Study screen or Add/Import screen

## 2.2 Add cards (single input box)
- Add screen has ONE text area (multiline)
- User pastes either:
  - JSON Lines (preferred)
  - optionally: a very strict fallback plain format (MVPではJSON LinesのみでもOK)
- App shows a preview list of parsed cards:
  - front snippet, back snippet, tags, deck
  - validation errors per line
- User taps "Import" → insert cards into DB

## 2.3 Study
- Study screen for selected deck (and optionally "All decks")
- Show next due card (front first)
- Tap "Show answer" → show back
- Two buttons:
  - Again (fail)
  - Good (pass)
- After rating, schedule updated and next card shown
- Session ends when due queue empty

## 2.4 Backup / Restore
- Export (manual backup): choose location via SAF → write JSON Lines
- Import (restore): choose file via SAF → parse and insert (optionally merge)
- Conflict policy (MVP):
  - Deduplicate by (deck_id + front_hash + back_hash) OR (deck + front + back)
  - If duplicate exists: skip (count as skipped)

---

# 3) Data model (Room entities)
## 3.1 Deck
- id: Long (PK)
- name: String (unique)
- created_at: Long
- updated_at: Long

## 3.2 Card
- id: Long (PK)
- deck_id: Long (FK)
- front: String
- back: String
- tags_json: String (JSON array string)  // simple
- created_at: Long
- updated_at: Long

## 3.3 Scheduling fields (stored on Card or separate table)
Store on Card for MVP simplicity:
- due_at: Long                // next due time (epoch millis)
- interval_days: Double       // current interval in days
- ease_factor: Double         // EF (SM-2), default 2.5
- repetitions: Int            // consecutive successful reviews
- lapses: Int                 // number of fails
- last_reviewed_at: Long?     // nullable

## 3.4 ReviewLog (optional but recommended)
- id: Long (PK)
- card_id: Long (FK)
- reviewed_at: Long
- rating: String ("AGAIN" or "GOOD")
- prev_due_at: Long
- new_due_at: Long
- prev_interval_days: Double
- new_interval_days: Double
- prev_ease_factor: Double
- new_ease_factor: Double

---

# 4) Scheduling algorithm: SM-2 simplified (2-button)
We adapt SM-2 for only two outcomes:
- GOOD = pass (treat as quality=4)
- AGAIN = fail (treat as quality=2)

Parameters (configurable in Settings):
- initial_interval_good_days (default 1.0)
- second_interval_good_days  (default 6.0)
- min_ease_factor (default 1.3)
- ease_factor_step_good (default +0.05)   // simplified positive drift
- ease_factor_step_again (default -0.20)  // penalty on fail
- lapse_interval_factor (default 0.0)     // fail resets interval to 0 or 1 day; MVP: 0 => due soon
- again_delay_minutes (default 10)        // same-session relearn, due in 10 min
- day_start_hour (default 4)              // optional: "day boundary" to keep due stable

### State defaults for new cards
- repetitions = 0
- lapses = 0
- interval_days = 0
- ease_factor = 2.5
- due_at = now

### On GOOD
If repetitions == 0:
  interval_days = initial_interval_good_days
Else if repetitions == 1:
  interval_days = second_interval_good_days
Else:
  interval_days = interval_days * ease_factor

repetitions += 1
ease_factor = ease_factor + ease_factor_step_good
due_at = now + interval_days days

### On AGAIN
lapses += 1
repetitions = 0
ease_factor = max(min_ease_factor, ease_factor + ease_factor_step_again)

Option A (recommended for MVP):
  due_at = now + again_delay_minutes minutes
  interval_days = 0

> This implements “same-session short retry” behavior.
> If user wants “fail => tomorrow”, then set again_delay_minutes to 1440 and/or set lapse_interval_factor to 1 day.

---

# 5) Import/Export format (AI-friendly)
## 5.1 JSON Lines (one card per line)
Each line: one JSON object.

Required:
- "deck": string (deck name)
- "front": string
- "back": string

Optional:
- "tags": array of strings
- "due_at": number (epoch millis)  // if provided, respects it on import
- "interval_days": number
- "ease_factor": number
- "repetitions": number
- "lapses": number

Example:
{"deck":"English","front":"ubiquitous","back":"(adj) present everywhere; widespread","tags":["vocab"]}
{"deck":"Stats","front":"ベースレート無視とは？","back":"事前確率（ベースレート）を無視して判断する誤り","tags":["cogbias","stats"]}

### Import rules
- If deck does not exist, create it.
- Empty front/back => error (skip line).
- Maximum length (MVP): front<=4000, back<=8000 (soft limit).
- Dedup policy: if same (deck + front + back) already exists => skip.
- If scheduling fields exist, validate ranges:
  - ease_factor >= min_ease_factor
  - interval_days >= 0
  - repetitions/lapses >= 0
  - due_at reasonable (>= 0)
  If invalid: ignore scheduling fields and treat as new.

### Export rules
- Exports all decks (or selected deck) to JSON Lines
- Include scheduling fields by default to allow full restore

---

# 6) Screens (MVP)
1. DeckListScreen
2. DeckDetailScreen (Study / Add / Import / Export)
3. AddImportScreen (single text area + preview + import)
4. StudyScreen (front, show answer, again/good)
5. SettingsScreen (algorithm parameters)

---

# 7) Acceptance criteria (definition of done)
- Create/rename/delete deck
- Import JSON Lines by paste (text area) with preview and per-line errors
- Study due cards in a deck with Again/Good and SM-2 scheduling updates
- Export JSON Lines to user-selected location
- Import file JSON Lines via SAF
- No crashes on invalid JSON lines; errors counted and shown

---

# 8) Implementation plan (tasks for Codex)
## Task 0: Project setup
- Kotlin + Compose + Room + kotlinx.serialization
- App name: Ingrain
- Package name: use existing repo settings
- Basic navigation scaffold

## Task 1: Database layer
- Room entities: DeckEntity, CardEntity, ReviewLogEntity
- DAOs: DeckDao, CardDao, ReviewLogDao
- Repository layer

## Task 2: Scheduling module
- Scheduler interface:
  - scheduleOnGood(card, now): CardUpdate
  - scheduleOnAgain(card, now): CardUpdate
- Settings storage: DataStore (preferences) for parameters
- Unit tests for scheduler with fixed timestamps

## Task 3: Import/export parsing
- JSON Lines parser:
  - parseLines(text): List<ParseResult> (success or error)
- Preview model
- Import executor:
  - create missing decks
  - dedup
  - insert cards
- Export writer:
  - select deck/all
  - stream write JSON lines

## Task 4: UI
- Deck list CRUD
- Deck detail actions
- AddImport screen:
  - single input box
  - "Preview" (auto as user types or manual button)
  - Import button + summary (added/skipped/failed)
- Study screen:
  - due queue query
  - show front/back toggling
  - Again/Good updates DB + logs

## Task 5: SAF file I/O
- Pick export destination (CreateDocument)
- Pick import file (OpenDocument)
- Read/write using content resolver streams

## Task 6: Polish
- Basic search within deck (optional)
- Empty states
- Minimal theming

---

# 9) Notes for Codex
- Prefer simple, robust code over cleverness.
- All parsing must be defensive.
- Keep dependencies minimal.
- No background sync; no network.
- Any “later” features must be behind TODOs, not half-implemented.
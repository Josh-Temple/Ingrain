# Ingrain

Ingrain is a personal Android flashcard app inspired by Anki, optimized for **fast card creation from AI output** and simple daily review.

## UI language policy
- All in-app UI copy must be written in English.

---

## Product goals
- Paste AI-generated content and import many cards quickly.
- Keep review workflow minimal (Again / Good).
- Support practical backup/restore without cloud sync.
- Provide app-level style customization for readability and personal preference.

## Out of scope (current MVP)
- APKG compatibility
- Sync and collaboration
- Media-heavy card management (image/audio/video)
- Advanced analytics dashboards

---

## Design direction: Concept mastery mode (includes laws/effects)
The following is a product-direction draft for specializing Ingrain toward **concept memorization** across domains. Academic laws/effects are a primary use case, but the same model should support broader content (terminology, principles, frameworks, and exam concepts).

### Scope and compatibility policy
- Keep existing `front/back` cards as the baseline format.
- Add optional concept metadata so current decks remain valid.
- Treat laws/effects as one subtype of a general concept model.

### Card schema extension (content quality + anti-confusion)
In addition to existing front/back fields, each concept card should support:
- `domain` (psychology/economics/law/medicine/etc.)
- `one_liner_definition` (short recall phrase)
- `proposer` / `year` (optional when applicable)
- `canonical_example` and `counter_example`
- `common_misuse` (frequent misunderstanding)
- `contrast_points` (difference from similar concepts)
- `evidence_level` and `sources`
- `confusion_cluster` (concepts frequently mixed up)

### Quiz modes (text-first interaction)
- Name → definition
- Definition → name
- Scenario → concept
- Error-finding (identify incorrect statement)
- A/B contrast (differentiate similar concepts)

UI policy for all quiz modes (including 4-choice style prompts):
- No dedicated answer buttons are required.
- Show question text first, then reveal answer/explanation by tap.
- Keep interaction consistent with the current tap-to-reveal study behavior.

### Scheduling policy
Keep SRS as the core, but weight scheduling with:
- forgetting risk
- confusion-cluster error history
- response latency (slow-but-correct should still be reviewed sooner)

### Widget feasibility (design note)
Widget-based study is feasible if interaction stays lightweight:
- Question text on widget
- Tap to reveal answer text
- Open app for deeper actions/editing

### Widget implementation policy (implemented v1)
To keep widget scope aligned with current UX principles, implement in phases:
- **v1 scope (minimal):** show one due card front, tap to reveal back, deep-link to Study for grading/editing.
- Keep widget interactions text-first and tap-to-reveal; do not add dedicated answer buttons in widget UI.
- Treat widget state as lightweight (`QUESTION` -> `ANSWER` -> `OPEN_APP`) and keep scheduling decisions in-app.
- Use graceful fallback states for empty/error conditions (for example: `Open app to study`).
- Keep payload backward-compatible with current `front/back` cards; concept metadata remains optional.


### MVP sequence for this specialization
1. Add concept metadata fields + source validation (backward-compatible)
2. Implement text-first multi-direction quiz flow (tap to reveal)
3. Add confusion-cluster comparison review
4. Add dashboard metrics (retention, confusion error rate)

---

## Current features

### Decks
- Create, rename, and delete decks.
- Open deck details and navigate to Study / Add & Import / Export / Settings.

### Add & Import
- Manual card entry.
- Bulk import via:
  - **Markdown + YAML front matter** (recommended)
  - **JSON Lines** (backward compatibility)
- Preview parse results before importing.
- Duplicate cards are skipped.
- Built-in AI prompt support: users can copy `ai_card_writer_prompt.md` directly from the app and paste it into GPTs/Gems or chat tools.

### Study
- Shows due cards in sequence.
- Card flow: Front → Show Answer → Back.
- Two review ratings:
  - Again
  - Good

### Home screen widget (minimal study)
- Displays one due card (question/front) from the next available deck.
- `Show answer` reveals the back text on the widget.
- `Open` deep-links into app Study for grading/editing actions.
- `Refresh` reloads widget content when deck/card state changes.

### Backup / Restore
- Export as Markdown.
- Import from Markdown or JSON Lines.

### Home screen widget (known limitations and next hardening)
- If app is already running, widget deep-link now routes to Study via runtime navigation event handling.
- Due card selection now prioritizes the earliest due candidate across eligible decks (not deck-name order).
- Widget update work runs off the main thread to reduce ANR risk during launcher-triggered refresh/reveal events.

### App formatting guide (Decks → Menu)
Global style controls are available from the formatting guide:
- Theme: Primary / Background / Surface / Text
- Typography: System default / Helvetica preferred
- Markdown style controls:
  - H3 / H4 / Body / List: size + color
  - Bold / Italic: color
- Button corner radius
- Live preview
- Reset to defaults

---

## Markdown format (recommended)

### Supported card structure
- YAML front matter (`deck`, `tags`)
- `## Front` and `## Back` sections
- `===` delimiter between cards

> Preferred in docs/templates: `## Front` / `## Back`. Backward compatibility: `### Front` / `### Back` are accepted by parser and AI prompt template.

### Example
```markdown
---
deck: "English"
tags: ["vocab"]
---

## Front
What does **ubiquitous** mean?

## Back
(Adj) present everywhere; widespread.
===
---
deck: "Stats"
tags:
  - cogbias
  - stats
---

## Front
What is base rate neglect?

## Back
The error of ignoring prior probability (base rate) when making a judgment.
```

---

## JSON Lines format (compatibility)
Each line should be a JSON object:

```json
{"deck":"English","front":"term","back":"definition"}
```

Required fields:
- `deck` (string)
- `front` (string)
- `back` (string)

---

## Scheduling model (SM-2 simplified)
Ingrain uses a simplified SM-2 style scheduler mapped to two ratings:
- `GOOD` ≈ quality 4
- `AGAIN` ≈ quality 2

Main tunable settings:
- `initial_interval_good_days` (default 1.0)
- `second_interval_good_days` (default 6.0)
- `min_ease_factor` (default 1.3)
- `ease_factor_step_good` (default +0.05)
- `ease_factor_step_again` (default -0.20)
- `again_delay_minutes` (default 10)

---


## Project structure notes
- `templates/` stores authoring references for humans (for example `ai_card_writer_prompt.md`).
- `app/src/main/assets/` contains runtime-accessible assets used by the app UI.

## Tech stack
- Kotlin
- Jetpack Compose
- Room (SQLite)
- DataStore (Preferences)
- kotlinx.serialization
- Storage Access Framework (SAF)

---

## Local development note
In this environment, Gradle tasks can fail with:
- `Unsupported class file major version 69`

If that appears, run verification in CI or a local setup with matching JDK/Gradle versions.

---

## CI build strategy (debug APK)
If you develop mainly on mobile, generate installable debug APKs via GitHub Actions.

- Standard workflow in this repo: `.github/workflows/android-debug-apk.yml`
- Reusable workflow for other repositories: `.github/workflows/reusable-android-debug-apk.yml`
- Uses a fixed debug keystore + monotonically increasing `versionCode` for updatable installs.

Required repository secrets:
- `DEBUG_KEYSTORE_BASE64`
- `DEBUG_KEYSTORE_PASSWORD`
- `DEBUG_KEY_ALIAS`
- `DEBUG_KEY_PASSWORD`

Without secrets, builds can still run, but APK signing may differ and block upgrade install over previous builds.

### Reuse from another repository
In another repository, call this reusable workflow:

```yaml
name: Android Debug APK

on:
  workflow_dispatch:

jobs:
  build:
    # Replace OWNER/REPO with this repository path.
    # Use a commit SHA (recommended) or an existing tag/branch name.
    uses: Josh-Temple/Ingrain/.github/workflows/reusable-android-debug-apk.yml@main
    with:
      app_module: app
      gradle_task: assembleDebug
      allow_ephemeral_signing: false
    secrets:
      DEBUG_KEYSTORE_BASE64: ${{ secrets.DEBUG_KEYSTORE_BASE64 }}
      DEBUG_KEYSTORE_PASSWORD: ${{ secrets.DEBUG_KEYSTORE_PASSWORD }}
      DEBUG_KEY_ALIAS: ${{ secrets.DEBUG_KEY_ALIAS }}
      DEBUG_KEY_PASSWORD: ${{ secrets.DEBUG_KEY_PASSWORD }}
```

> Important:
> - Do not use placeholder values such as `your-org/Ingrain`.
> - If you reference `@v1`, confirm that the `v1` tag exists in this repository first.
> - For safest operation, pin to a commit SHA instead of a moving ref.

If your repository is private, ensure workflow access policy allows calling workflows from this repository (or copy the reusable workflow file into your own repository).


## Implementation status (current session)
- Began implementation for concept/law-effect specialization in runtime code.
- Import format now accepts concept metadata keys in both Markdown front matter and JSON Lines (`concept_domain`, `concept_one_liner`, `concept_proposer`, `concept_year`, `canonical_example`, `counter_example`, `common_misuse`, `contrast_points`, `evidence_level`, `sources`, `confusion_cluster`).
- Card storage schema now includes concept metadata columns, and import/export paths persist these fields.
- Add Card templates now include a `Law/Effect` preset for faster authoring.
- Existing cards can now be edited with concept metadata fields from Edit Card.
- Widget work is intentionally deferred for now (as requested).

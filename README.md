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

### Backup / Restore
- Export as Markdown.
- Import from Markdown or JSON Lines.

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

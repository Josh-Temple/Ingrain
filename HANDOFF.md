# Handoff Notes

## Current Product State
- The bottom navigation in Decks shows `ADD`; tapping it opens a dialog with `Add deck` and `Add card`.
- `Add card` navigates to the global add screen (`import-global`).
- `Target deck` on the add-card screen is optional. If `None` is selected, users can add cards to multiple decks via deck-name input or `deck` in JSON Lines/Markdown.
- Whether users open add-card from Deck list or Deck detail, `Target deck` defaults to unselected.
- The Study screen aligns front content toward the top, removes old `PROGRESS/cards` and `STUDY` tag display, shows deck name in the header, and uses a thinner front/back divider.
- Users can swipe up on Study to open `Edit Card`.
- Import/Export supports both Markdown + YAML front matter and JSON Lines.
  - Markdown standard: `---` front matter + `## Front` / `## Back` + `===` separator.
  - For backward compatibility, `### Front` / `### Back` are also accepted.
- Backup/Restore export format is Markdown (`ingrain-backup.md`).
- The renderer uses style tokens (heading3 / heading4 / paragraph / list / strong / emphasis), not CSS, and reflects App formatting guide settings.
- In Add Card, `Templates` allows choosing `Markdown` / `JSON Lines`, copying a format template, and copying the built-in AI card writer prompt (`ai_card_writer_prompt.md` from app assets).
- The formatting guide is centralized under Decks → top-right `Menu` → `App formatting guide`.
  - Users can change theme (Primary/Background/Surface/Text), font (System default / Helvetica preferred), and Markdown style size/color (H3/H4/Body/List/Bold/Italic).
  - Live preview and reset are supported.

## Recent Refactoring
- Refactored recently added style features to clarify responsibilities across settings model, renderer, and UI.
- Added `bodyColorIndex` to `UiStyleSettings` so body text color is applied consistently in both UI and renderer.
- Kept `uiStyleStore` wiring into `StudyScreen`; verified front/back Markdown rendering always reads current global style.
- Simplified font selection in `App formatting guide` to direct `AppFontMode` references for readability.

## Next Tasks
1. Strengthen parser/validator incrementally.
   - Current: YAML front matter (`deck`, `tags`) + `## Front` / `## Back` (and legacy `###`) + `===` is documented in README/templates and supported in parser.
   - Remaining: fine-tune error wording/line-number precision and strict handling for edge-case YAML.
2. Continue import UX hardening.
   - Current: detects missing required sections, invalid front matter, empty Front/Back.
   - Remaining: strict handling for YAML escaping and complex structures.
3. Expand renderer policy.
   - Current: h3, bold, italic, list, paragraph.
   - Remaining: spacing rules between multiple paragraphs; decision on code/quote support.
4. Finalize data migration policy.
   - Current: DB stores `front/back` strings; Import/Export handles Markdown I/O.
   - Remaining: decide whether AST/JSON cache is needed.

## Operational Notes
- `Target deck` currently uses button rows. If deck count grows, consider switching to `ExposedDropdownMenuBox`.
- In this environment, Gradle can fail with JDK/Gradle compatibility error (`Unsupported class file major version 69`); validate builds in CI or a local environment with aligned JDK.

## Risks / TODO
- **UI responsibility concentration**: `Screens.kt` includes many screens (Decks/Detail/Import/Study/Edit/Settings/Backup), making impact analysis harder.
- **Mixed menu information architecture**: `App formatting guide` currently combines Markdown format guidance with global appearance settings.
- **Theme granularity gap**: Global styling currently focuses on `primary` and `shapes.small`; broader customization strategy is still unclear.
- **Study discoverability**: Tap-to-reveal and swipe-up-to-edit gesture model may be hard for first-time users to discover.
- **Import failure observability**: `importParsed` aggregates exceptions with `failed++`, making root-cause analysis and UI detail weak.
- **Template drift risk**: keep `templates/ai_card_writer_prompt.md` and `app/src/main/assets/ai_card_writer_prompt.md` synchronized when updating prompt text.
- **Build reproducibility**: Current environment may hit `Unsupported class file major version 69`, causing unstable local verification.

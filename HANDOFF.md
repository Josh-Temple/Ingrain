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

## Session Update (Academic Laws/Effects Specialization Draft)
- Added a design-direction section to README for specializing Ingrain into an academic laws/effects memorization app.
- Proposed schema additions: domain, proposer/year, canonical/counter examples, misuse notes, contrast points, evidence level, sources, and confusion clusters.
- Proposed quiz expansion: name→definition, definition→name, scenario→concept, error-finding, and concept comparison.
- Proposed SRS prioritization update: include confusion history and response latency, not only binary correctness.
- Proposed MVP sequence focuses on metadata + validation first, then multidirectional quiz, then confusion review and dashboard metrics.

### Suggested next implementation slice
1. Define storage model changes for concept metadata and source fields.
2. Introduce validation rules to prevent source-less academic cards.
3. Add first multidirectional quiz mode pair (definition↔name) before scenario mode.
4. Add analytics events for confusion-cluster misses and answer latency.


## Session Update (Direction Refinement)
- Refined the design direction from a strict "laws/effects" focus to a broader **concept mastery** model so the same architecture can cover non-law content as well.
- Documented a compatibility rule: keep existing front/back cards as baseline and add metadata as optional extensions.
- Clarified quiz UX policy: even for 4-choice style content, use text-first display with tap-to-reveal answer/explanation; no dedicated option buttons required.
- Added widget feasibility guidance: widget can support lightweight question text and tap-to-reveal answer, while advanced flows stay in-app.

### Suggested next implementation slice (updated)
1. Add optional concept metadata columns/fields without breaking current imports.
2. Implement text-first question/reveal templates for name/definition/scenario patterns.
3. Add confusion-cluster logging and review queue hooks.
4. Prototype a minimal widget read/reveal path that deep-links into Study for grading actions.


## Session Update (Implementation Started: Law/Effect Specialization)
- Implemented DB-level concept metadata support on cards (domain, one-liner, proposer/year, examples, misuse, contrast points, evidence, sources, confusion cluster).
- Added DB migration to preserve existing installs while introducing new optional concept columns.
- Extended Markdown and JSON Lines import models to parse/store concept metadata fields.
- Extended export paths (Markdown/JSON Lines) to include concept metadata when present.
- Added a `Law/Effect` Add template to speed up authoring in the current UI.
- Widget implementation is deferred intentionally per latest product direction.

### Follow-up implementation suggestions
1. Add dedicated UI fields for concept metadata in detailed add/edit screens (currently mostly import-driven).
2. Add study-side rendering that highlights one-liner, misuse, and contrast points separately.
3. Add validation policy for source/evidence requirements when concept metadata is present.
4. Add migration/unit tests for round-trip import/export of concept metadata.


## Session Update (Refactor + Editing + Prompt Audit)
- Refactored card update flow to introduce `ConceptMetadataInput` in repository and centralized concept metadata normalization.
- Expanded `Edit Card` UI so existing cards can update concept metadata fields (domain, one-liner, proposer/year, examples, misuse, contrast, evidence, sources, confusion cluster).
- Added repository test coverage for updating concept metadata via `updateCardContent`.
- Audited and synchronized AI prompt templates (`app` asset and `templates`) to remove drift and include optional concept metadata keys aligned with current parser support.


## Session Update (Widget Strategy Clarification)
- Agreed to keep widget delivery as a minimal read/reveal surface first, not a full grading surface.
- Confirmed interaction model parity with Study: text-first prompt and tap-to-reveal answer.
- Defined recommended v1 state flow: `QUESTION` -> `ANSWER` -> deep-link into app for grading/actions.
- Confirmed fallback behavior for no-data/error widget states should be simple and actionable.

### Suggested next implementation slice (widget)
1. Define widget data contract (`cardId`, `deckName`, `front`, `back`) with optional concept metadata passthrough.
2. Implement a small widget UI state machine for reveal behavior only.
3. Add deep link routing into Study with card context handoff.
4. Add minimal analytics events (`impression`, `reveal`, `open_app`).


## Session Update (Widget MVP Implemented)
- Implemented Android home-screen widget provider (`StudyWidgetProvider`) with minimal read/reveal flow.
- Added widget actions: `Show answer` (toggle reveal), `Refresh`, and `Open` (launch app with Study deck context when available).
- Added repository helper `nextWidgetDueCard` to fetch first eligible due card across decks using existing daily-limit logic.
- Wired MainActivity startup extra to open Study route directly when launched from widget with deck id.
- Added app widget resources (layout + provider XML) and manifest receiver registration.
- Added repository test coverage for widget due-card selection helper.

### Suggested next implementation slice
1. Add optional widget configuration (specific deck vs any deck).
2. Add widget analytics hooks for `impression`, `reveal`, and `open_app`.
3. Add truncation/markdown-stripping policy for long card text in widget body.
4. Add screenshot/update docs for launcher differences across Android versions.


## Session Update (Widget Hardening Cycle 1-3)
- Cycle 1: Fixed widget-to-Study routing when app receives new intents while already alive (`onNewIntent` + runtime nav event).
- Cycle 2: Improved widget card selection to choose the earliest due candidate across eligible decks, rather than first deck order.
- Cycle 3: Moved widget refresh/reveal/update workload off main thread using `goAsync` + IO coroutine execution.
- Updated repository test to validate earliest-due selection behavior.

### Suggested next implementation slice
1. Add integration/UI tests for widget deep-link behavior from warm app state.
2. Add analytics for widget reveal/open/refresh interactions.
3. Consider widget configuration (specific deck binding).
4. Add markdown truncation/normalization for long card fronts/backs in widget text view.

## Session Update (Build Fix: Add Template String Literal)
- Fixed Kotlin compilation errors in `Screens.kt` by converting the multiline `Law/Effect` add-template back text from an invalid quoted string into a valid Kotlin raw string literal (`"""..."""`).
- This addresses the CI parse errors around `Screens.kt` lines near the add-template definition (`Expecting '"'`, `Expecting an element`, and follow-on syntax errors).
- Local build re-check in this container is blocked by environment JDK/Gradle cache compatibility (`Unsupported class file major version 69`), so final assemble verification should run in CI with Java 17 toolchain.

### Suggested next implementation slice
1. Add a lightweight Kotlin compilation/unit check in CI before full assemble to catch syntax regressions earlier.
2. Add a tiny test or lint guard around predefined template constants to prevent accidental invalid multiline string edits.

## Session Update (Refactor: Add Template Definitions)
- Refactored `Screens.kt` add-template definitions to improve maintainability.
- Made `AddPreset` explicitly private to keep template model scoped to `Screens.kt`.
- Extracted the `Law/Effect` multiline back template into a dedicated constant and applied `trimIndent()` to avoid accidental indentation drift.
- Switched add-template construction to named arguments for clearer field mapping during future edits.

## Session Update (Widget Health Check)
- Re-validated the current widget scope against product docs: minimal read/reveal flow plus deep-link to in-app Study remains the intended behavior.
- Attempted to run repository tests (including widget-related selection coverage), but the local Gradle run failed before test execution due to Java/Gradle incompatibility in this container (`Unsupported class file major version 69`).
- No source-code widget logic changes were required in this pass; this update records validation status and environment constraints for the next session.

### Suggested next implementation slice
1. Re-run `./gradlew test --tests com.ingrain.IngrainRepositoryTest` in CI or a local Java 17 environment to confirm widget regression status.
2. Add instrumentation coverage for widget reveal/open flows from launcher interactions (cold and warm app process).
3. Add widget interaction analytics (`impression`, `reveal`, `open_app`) once event pipeline decisions are finalized.

## Session Update (Widget v2: Stable On-Widget Grading)
- Added widget grading actions after reveal: `Unsolved` (maps to `AGAIN`) and `Solved` (maps to `GOOD`).
- Kept existing `Show answer`, `Refresh`, and `Open` actions; grading buttons are hidden until answer reveal.
- Implemented stale-card guard by persisting `displayed_card_<widgetId>` and validating it before applying grade.
- Widget grade path now uses shared scheduler settings (`SchedulerSettingsStore`) so spacing behavior stays aligned with in-app study review.
- Added new widget string resources for grading labels.
- Updated README widget policy from v1-only to include v2 grading scope and state-flow notes.

### Suggested next implementation slice
1. Add widget interaction analytics for `grade_again` and `grade_good` alongside existing reveal/open/refresh metrics.
2. Add truncation/markdown normalization for long card text before widget rendering.
3. Add instrumentation tests around widget action race scenarios (rapid refresh + grade taps).


## Session Update (Widget Deck Picker + Deck Card List Navigation)
- Added per-widget deck selection support using a deck picker activity with dropdown (`All decks` or a specific deck).
- Widget now shows a `Deck: ... ▾` control and filters due-card selection by the selected deck when configured.
- Existing widget reveal/grade/open behavior remains unchanged; only card source scope is configurable per widget instance.
- Added deck-management navigation: Deck details now includes `View & Edit Cards`, opening a deck-scoped card list.
- Added `DeckCardsScreen` so users can browse cards inside a deck and open `Edit Card` directly from the list.
- Added repository support `observeCardsByDeck` and test coverage for selected-deck widget filtering.

### Suggested next implementation slice
1. Add search/sort controls (created/due/tags) to `DeckCardsScreen` for large decks.
2. Add widget telemetry for deck-scope changes and picker saves.
3. Add instrumentation test for widget picker flow (configure -> refresh -> due selection) and deck-cards navigation flow.

## Session Update (Widget Text Clamp Removal)
- Removed the widget body line clamp/ellipsis so card text is no longer forcibly truncated to 7 lines.
- Widget now renders as much of the front/back text as can fit in the current widget size without explicit max-lines clipping.
- Updated README widget feature notes to reflect full text display behavior and adjusted next-step wording to avoid hard truncation guidance.

### Suggested next implementation slice
1. Add optional markdown/plain-text normalization for widget display while keeping full-text behavior.
2. Validate rendering on small/medium/large widget sizes across OEM launchers to confirm readability.


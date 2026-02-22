# UI/UX Audit Report (All Screens)

Scope: `app/src/main/java/com/ingrain/ui/screens/Screens.kt` and `app/src/main/java/com/ingrain/ui/IngrainApp.kt`

## Overall Assessment
- The information architecture follows a natural learning-app flow: `Decks → Detail → Study / Add & Import / Settings`.
- Visual consistency is generally good; spacing and hierarchy are mostly predictable.
- Main improvement opportunities are discoverability, consistency of naming, and better feedback for error/failure states.

## Strengths
- Clear primary flow from deck management to study.
- Functional coverage is broad (study, import/export, backup/restore, formatting).
- Formatting customization with live preview improves user confidence.
- Markdown rendering is constrained by style tokens, helping preserve consistent presentation.

## Key Issues
1. **Discoverability of gestures**
   - Study interaction relies on tap-to-show-answer and swipe-up-to-edit, which can be easy to miss.
2. **Screen responsibility concentration**
   - `Screens.kt` owns many screens and concerns, increasing coupling and maintenance cost.
3. **Inconsistent terminology and labels**
   - Some labels and helper texts are not fully aligned across screens.
4. **Limited failure transparency in import**
   - Aggregated failures reduce diagnosability for users.
5. **Settings IA overlap**
   - Formatting guidance and global appearance controls are mixed in one entry point.

## Recommendations by Priority

### P0 (Must fix)
- Add explicit onboarding cues for Study gestures (inline hint, tooltip, or first-run coach mark).
- Improve import error reporting with per-card reason and line-level context where possible.

### P1 (Should fix)
- Split large screen file into feature-focused modules/components.
- Unify naming/wording patterns across deck, card, and settings surfaces.
- Add stronger empty-state CTAs (e.g., after study completion).
- Add due-count summary in deck list for clearer progress context.

### P2 (Nice to have)
- Add search/sort for large deck collections.
- Standardize edit/delete affordances across detail and list contexts.
- Extend settings with contextual help and validation.

## Suggested Success Metrics
- Reduction in first-session confusion around Study gestures.
- Lower import failure support requests due to better diagnostics.
- Faster implementation velocity after screen-level decomposition.
- Better retention from improved “next action” clarity in empty states.

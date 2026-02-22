# Card Note Template (Markdown)

`Ingrain` recommends **Markdown + YAML front matter** as the primary import format.
Separate multiple cards with `===`.

## Required Rules
- Each card must start with YAML front matter.
- Required keys: `deck`, `tags`.
- The body must include both `### Front` and `### Back`.
- Use `===` (single line) as the separator between cards.

## Template

```markdown
---
deck: Biology
tags: [photosynthesis, basics]
---
### Front
What is photosynthesis?

### Back
Photosynthesis is the process by which plants convert light energy into chemical energy.
===
---
deck: Biology
tags: [chloroplast]
---
### Front
Where does photosynthesis occur?

### Back
It primarily occurs in chloroplasts, especially in leaf cells.
```

## Notes
- JSON Lines import is also supported for backward compatibility.
- Rows (cards) with duplicate `deck + front + back` are skipped.
- Markdown is rendered through style tokens (heading3 / strong / emphasis / list / paragraph), not CSS.

# UI Foundation: Mizan v2
**UX Reference**: docs/ux-mizan-v2.md
**Version**: 1.0 | **Date**: 2026-10-22 | **Author**: UI Designer

## 1. Design Approach
- **Strategy**: Angular Material 3 (M3 theming via `mat.theme()` and system CSS variables) + a thin token layer.
- **Rationale**: forms, menus, dialogs, chips and expansion panels come for free and are RTL-aware; custom work limited to the drop zone, status stepper and risk chips (YAGNI). Keycloak login pages get a matching minimal theme (colors, logo, fonts).

## 2. Design Tokens
```css
:root {
  /* Brand — carried from v1 */
  --mz-primary:      #0F5C4C;  /* deep green: trust */
  --mz-accent:       #C9A24B;  /* muted gold */
  --mz-background:   #F7F7F5;
  --mz-surface:      #FFFFFF;
  --mz-text:         #1B1B1B;
  --mz-text-muted:   #5F5F5F;
  --mz-error:        #B3261E;

  /* Risk (semantic; each ≥ 4.5:1 on surface as text) */
  --mz-risk-high:    #B3261E;
  --mz-risk-medium:  #8A6400;
  --mz-risk-low:     #2E6B30;

  /* Type */
  --mz-font: "Inter", "Noto Sans Arabic", system-ui, sans-serif;
  --mz-fs-sm: 0.875rem; --mz-fs-md: 1rem; --mz-fs-lg: 1.25rem; --mz-fs-xl: 1.75rem;

  /* Spacing (8 px grid) */
  --mz-sp-xs: 4px; --mz-sp-sm: 8px; --mz-sp-md: 16px; --mz-sp-lg: 24px; --mz-sp-xl: 40px;
}
@media (prefers-color-scheme: dark) {
  :root { --mz-background: #121614; --mz-surface: #1B201E; --mz-text: #ECEDEB; --mz-text-muted: #A9AEAB;
          --mz-primary: #6FC2AE; --mz-risk-high: #F2A39D; --mz-risk-medium: #E3C277; --mz-risk-low: #9BD59E; }
}
```
Risk is never conveyed by color alone: each chip carries the word (HIGH / ÉLEVÉ / مرتفع) and an icon.

## 3. Component Inventory
| Component | Reuse Existing | Build New | Notes |
|---|---|---|---|
| App shell, nav, user menu | mat-toolbar, mat-menu | No | Language switch flips `dir` |
| Drop zone | — | Yes | `<input type=file>` + drag/drop, keyboard accessible, `aria-describedby` for limits |
| Language select | mat-select | No | fr / ar / ary |
| History list | mat-table → cards < 768 px | No | Status chip per row |
| Status chip | mat-chip | Small | PENDING / ANALYZING / COMPLETE / FAILED |
| Analysis stepper | — | Yes | Received → Analyzing (elapsed) → Done; `aria-live="polite"` |
| Risk chip + filter chips | mat-chip-listbox | Small | Uses risk tokens |
| Flag panel | mat-expansion-panel | No | Copy button for suggestion |
| Quota chip | mat-chip | No | "3 left today" |
| Confirm dialog | mat-dialog | No | Delete |
| Admin cards + bar chart | mat-card + plain SVG | Small | No chart library (YAGNI) |
| Disclaimer footer | — | Small | On every authenticated page |

## 4. Responsive Breakpoints
| Breakpoint | Width | Layout Notes |
|---|---|---|
| Mobile | < 768px | Single column, history as cards, full-width buttons |
| Tablet | 768–1024px | Single column, wider cards |
| Desktop | > 1024px | Analysis view: summary left, flags right (max width 1200 px) |

## 5. Accessibility Baseline
- WCAG 2.2 AA contrast; risk colors verified on light and dark surfaces.
- Visible focus on every control (Material focus indicators kept).
- Live regions for analysis status; errors announced and linked to the field.
- Full RTL for Arabic and Darija (logical CSS properties only: `margin-inline-start`, etc.).
- Model output is rendered as plain text with `dir="auto"` per paragraph (mixed FR/AR clauses).

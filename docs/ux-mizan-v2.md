# UX Foundation: Mizan v2
**PRD Reference**: docs/prd-mizan-v2.md
**Version**: 1.0 | **Date**: 2026-10-22 | **Author**: UX Designer

## 1. User Personas (minimal — YAGNI)
| Persona | Role | Goal | Pain Point |
|---|---|---|---|
| Karim, freelance developer (auto-entrepreneur) | user | Understand a client contract before signing | A lawyer costs more than the gig is worth |
| Fatima Zahra, shop owner | user | Check a supplier or lease contract in Arabic or Darija | Legal French is opaque to her |
| Hiring manager / ESN recruiter | visitor (demo) | See in 2 minutes that the app works and how it is built | Most portfolios have no live demo |
| Owner | admin | Watch usage, failures and the Claude bill | Needs numbers, not a BI tool |

## 2. Information Architecture / Site Map
```
/                     Landing: what it does, not-legal-advice notice, [Try it] → Keycloak sign-up
/auth/**              Keycloak hosted pages (sign-up, verify email, login, reset) — themed
/contracts            History (default after login) + quota chip "3 analyses left today"
/contracts/new        Upload: drop zone + language select
/contracts/:id        Analysis view (live status while analyzing)
/admin                Admin stats (admin role only)
/about                How it is built: architecture diagram + links to repo, docs, Grafana snapshot
```

## 3. Core User Flows (top 3 journeys)
### Flow 1: First visit → first analysis
```
[Landing] → [Try it] → [Keycloak sign-up: e-mail + name] → [Verify email] → [Set password] → [/contracts empty state]
   → [Upload PDF + language] → [/contracts/:id: "Reading…" → "Analyzing…" (live)] → [Result]
                     ↓ invalid PDF / too big / scanned
               [Inline error under drop zone, file kept selected for retry]
                     ↓ quota reached
               [Notice: "Daily limit reached — resets at midnight (Casablanca)"]
```

### Flow 2: Review flagged clauses
```
[Result] → [Summary card] → [Flags sorted HIGH → LOW, filter chips]
   → [Expand flag: original clause | why it is risky | suggested wording | Copy]
```

### Flow 3: Re-analyze in another language / delete
```
[Result] → [Language menu: Analyze in Arabic] → [Live status] → [New result; previous kept in tabs]
[History row menu] → [Delete] → [Confirm dialog: "This removes the text and all analyses"] → [Row gone + snackbar]
```

> Keycloak 26.8 asks for the password only after the e-mail is verified (required actions
> VERIFY_EMAIL + UPDATE_PASSWORD), so nobody can pre-claim someone else's address (Story 1.4).

## 4. Key Screen Wireframes (text-based)
### Screen: Upload
```
┌──────────────────────────────────────────────┐
│ Mizan   Contracts  About        FR|AR  [Me ▾] │
├──────────────────────────────────────────────┤
│  Analyze a contract           3 left today    │
│  ┌──────────────────────────────────────────┐ │
│  │   Drop a PDF here or [Choose a file]     │ │
│  │   Text PDF · max 10 MB · max 30 pages    │ │
│  └──────────────────────────────────────────┘ │
│  Analysis language  [Français ▾]              │
│  [ Analyze ]                                  │
├──────────────────────────────────────────────┤
│ Informational analysis — not legal advice.    │
└──────────────────────────────────────────────┘
```

### Screen: Analysis view
```
┌──────────────────────────────────────────────┐
│ ← Contracts   Bail_commercial.pdf  [FR ▾][⋯] │
├──────────────────────────────────────────────┤
│ Summary                                       │
│ [plain-language paragraph]                    │
│ Flags   [All 7] [High 2] [Medium 3] [Low 2]   │
│ ▸ HIGH   Art. 12 — Pénalité de résiliation    │
│ ▾ HIGH   Art. 4 — Révision unilatérale du …   │
│     Clause:      "…"                          │
│     Why risky:   …                            │
│     Suggested:   "…"            [Copy]        │
│ ▸ MEDIUM Art. 7 — Renouvellement tacite       │
├──────────────────────────────────────────────┤
│ Informational analysis — not legal advice.    │
└──────────────────────────────────────────────┘
```

## 5. Screen States
| Screen | Empty State | Loading | Error | Success |
|---|---|---|---|---|
| History | "No contracts yet" + [Analyze a contract] | Skeleton rows | "Couldn't load your contracts" + Retry | Rows with status chips |
| Upload | — | Button spinner "Reading PDF…" | Inline: not a PDF / too big / too many pages / no text / quota | Navigate to analysis view |
| Analysis view | — | Stepper: Received → Analyzing (elapsed s) | "Analysis failed" + [Try again] (does not use quota twice) | Summary + flags |
| Admin | "No analyses yet" | Skeleton cards | "Couldn't load stats" | Cards + bar chart |

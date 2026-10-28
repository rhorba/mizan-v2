# Project Metrics
<!-- KPI snapshots over time -->
<!-- Format: ### [YYYY-MM-DD HH:MM] SPRINT_SNAPSHOT/DAILY_SNAPSHOT — Title -->


### [2026-10-22] SPRINT_SNAPSHOT — Sprint 0 (foundation)
Docs 10/10 approved · Stories 0/21 done · Coverage n/a (no code) · CI n/a

### [2026-10-22] COVERAGE — Story 1.1
Backend (JaCoCo aggregate, no exclusions): lines 6/6 = 100%. Web (Vitest v8): statements 100%, branches 100%, functions 100%, lines 100%. Gate 80%: PASS. Tests: 13 total (api 8, analyzer 4, web 1), 0 failures.

### [2026-10-22] COVERAGE — Story 1.2
No new application logic (one config line). Backend 100% lines (6/6), web 100%. Stack AC: fresh 75 s, warm+apps 63–75 s (limit 120 s).

### [2026-10-22] COVERAGE — Story 1.3
No app code changed. Backend 100% / web 100%. CI enforces both gates (proved failing at 101%).

### [2026-10-23] COVERAGE — Story 1.4
Backend (JaCoCo aggregate, no exclusions): lines 87/87 = 100 %, branches 7/8 = 87.5 %. Web (Vitest v8, all src/app files): lines 100 %, statements 100 %, functions 100 %, branches 98.75 %. Gate 80 %: PASS. Tests: 80 unit/integration + 1 E2E.

### [2026-10-23] SPRINT_SNAPSHOT — Sprint 1 (goal: skeleton that builds, tests, scans, runs locally + sign-up/login)
- **Planned stories**: 4 (1.1 M, 1.2 S, 1.3 M, 1.4 L ≈ 5 days) — **Completed**: 4 — Dropped: 0
- **Approach chosen**: 🔴 comprehensive on all four (user)
- **Tests**: backend 40 (incl. 8 Testcontainers Keycloak), web 40 unit, 1 E2E; coverage backend 100 % lines, web 100 % lines / 98.75 % branches
- **CI**: 6 required checks (lint, test-backend, test-frontend, security, stack, e2e); red runs: 3 (exec bit, ng build vs test helper, Keycloak CVE) — all fixed same session
- **Bugs/defects found by our own gates**: 12 (false-100 % coverage ×2, Keycloak bootstrap health, analyzer exit, CRLF/Spotless, OIDC leftovers ×2, contrast ×2, bidi wordmark, Trivy 429, lz4 CVE) — all fixed
- **Security**: 2 HIGH/CRIT dependency CVEs fixed (lz4, vite), 1 CRITICAL time-boxed exception to 2026-11-30 (Keycloak base ca-certificates), 0 open SAST/secret findings
- **Scope changes**: Boot 4.0.x → 4.1.2; per-user cap 3 (doc said 5); sign-up flow verify → set password (Keycloak 26.8)

### [2026-10-23] COVERAGE — Sprint 2
Backend (aggregate, no exclusions): lines 99 %, branches 91 % (110 tests incl. Testcontainers Postgres + Keycloak). Web: lines 100 %, branches 97.3 % (82 tests). E2E 2. Gate 80 %: PASS.

### [2026-10-23] SPRINT_SNAPSHOT — Sprint 2 (goal: upload, validate, extract; history; delete)
- **Planned stories**: 3 (2.1 L, 2.2 M, 4.2 M) — **Completed**: 3 — Dropped: 0; approach 🔴 (user) incl. ClamAV, rate limit, progress, keyset
- **Tests**: backend 110 (+70), web 82 (+44), E2E 2 (+1); coverage backend 99 % lines, web 100 % lines
- **CI**: first push green (6/6); e2e now runs real ClamAV with cached signatures
- **Defects caught by our own checks before ship**: 14 (unscoped test query, fake FileList, deep-link return, retry disabled, 0.0 Mo, phone toolbar overflow, upload spec not running + misread result, Enforcer only in aggregator, parallel m2 race, real RLO char in source, Semgrep socket findings ×3, test regex escaping, meaningless self-comparing assertion) — all fixed
- **Security**: live EICAR blocked by real ClamAV; IDOR suite auto-discovers endpoints; open-redirect guard on login return path; 0 open SAST/secret/vuln findings
- **Decisions**: ClamAV absent = skip+warn (owner), configured-but-down = 503 (accepted); keyset index adds id DESC

### [2026-10-26] COST — Live Claude spend so far ~$0.05 (1 Haiku 4.5 call by mistake, 1 Opus 5.5 call 1358/1823 tokens). Cap $3/month.

### [2026-10-26] COVERAGE — Sprint 3 B4: lines 96.5% (api 96.4%, analyzer 96.8%); web 94 tests

### [2026-10-26] COST — Live Claude spend this sprint ≈ $0.09 (Opus ×2 ≈ $0.08, Haiku 4.5 + Haiku 5.5 cache check < $0.01). Cap $3/month.

### [2026-10-26] SPRINT_SNAPSHOT — Sprint 3 (goal: outbox → Kafka, Claude analyzer, idempotent results)
- **Planned stories**: 3 (3.1 M, 3.2 L, 3.3 M) + 🔴 extras (budget, caching, wire contracts, DLT screen) — **Completed**: all — Dropped: 0; finished in 1 day vs 5–6 estimated
- **Tests**: API 126 (+16), analyzer 37 (+34), web 94 (+12), E2E 4 (+2, 1 opt-in real Claude); coverage lines 96.5 %
- **CI**: first push red only on Trivy (new base-image CVE, not sprint code) → fixed → 6/6 green (push + PR #11)
- **Defects caught by our own checks before ship**: 11 — wrong model (Haiku 4.5 fallback, live test), DLT topic name, Modulith __TypeId__ headers, at-least-once duplicate in crash test, test format-string %, compose API without Kafka, Modulith CREATE SCHEMA as mizan_app, 1-partition topic auto-create, heredoc/escaping ×3 — all fixed
- **Security**: review of sprint diff: 0 HIGH/MEDIUM; admin 403 tests; DLT view never returns text; Trivy/Semgrep/Gitleaks green
- **Cost**: live Claude ≈ $0.13 total this sprint (Opus ×3 incl. E2E) of $3/month cap
- **Decisions**: model claude-opus-5-5 (owner), no sampling params, live tests opt-in, budget in API, results accepted while PENDING/ANALYZING, V2/V3 migration swap

### [2026-10-27] COVERAGE — Sprint 4: lines 97.0% (api 96.9%, analyzer 97.1%); web lines 98.1% / branches 94.3% (only app.routes.ts < 90%: lazy loadComponent arrows, covered by E2E). Gate 80% → PASS. Tests: API 154, analyzer 40, web 130.

### [2026-10-27] COST — Live Claude spend this sprint ≈ $0.29 (9 Opus analyses: 2 opt-in E2E calls + 7 unintended, from E2E uploads while the local analyzer had the key; see issues.md). Running total ≈ $0.42 of the $3/month cap.

### [2026-10-27] SPRINT_SNAPSHOT — Sprint 4 (goal: live status, results, re-analysis, quotas, admin statistics)
- **Planned stories**: 5 (3.4, 4.1, 4.3, 5.1, 5.2) + 🔴 extras (admin charts, axe audit, multi-instance SSE fan-out) — **Completed**: all — Dropped: 0; ~2 days vs 4.5 planned (estimate ~29 h)
- **Tests**: API 154 (+28), analyzer 40 (+3), web 130 (+36), E2E 5 (+1 net: +2 new, Sprint 3 Claude test folded in; 1 opt-in real Claude, axe audit runs in CI); coverage lines 97.0 % backend, 98.1 % web
- **CI**: see the PUSH / CI entries in activity.md (PR #12)
- **Defects caught by our own checks before ship**: 12 — JDK tzdata drift (quota day 1 h early, every env), red/green chart CVD failure, chart text scaling (19 px desktop / 4 px phone), detached risk values, style budget, svg role=img hiding focusable days (axe), unbounded SSE streams (security review), tab choice hiding a result behind a later failure, re-analysis double click (lock), 3 stale E2E assertions from B1/B2, local E2E uploads billed — all fixed or mitigated
- **Security**: review of sprint diff: 1 MEDIUM (SSE stream cap) fixed; 0 HIGH; admin 403/401 + no-PII tests on stats; IDOR suite now posts valid bodies
- **Accessibility**: axe WCAG 2.2 AA clean on history, upload, contract (pending, result fr, result ar tab), admin statistics fr/ar
- **Decisions**: PostgreSQL decides the day (owner); chart palette from the validated reference status set; risk split single-hue; re-analysis rules (409s, FAILED re-requestable); 10 streams per user

### [2026-10-28] Sprint 5 B3 — tests + coverage
API 154 · analyzer 39 · web 133 · backend line coverage 97.0% · web initial bundle 481.3 kB. Claude spend this session $0.072 (incl. $0.053 unapproved, issues.md); total ≈ $0.51 of $3.

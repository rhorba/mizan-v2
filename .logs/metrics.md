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

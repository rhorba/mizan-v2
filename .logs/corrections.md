# Corrections & Plan Changes
<!-- Tracks scope changes, pivots, plan adjustments -->
<!-- Format: ### [YYYY-MM-DD HH:MM] SCOPE_CHANGE/PIVOT/REPLAN — Title -->


### [2026-10-22] CORRECTION — Boot 4.0.x → 4.1.2
Architecture said "Spring Boot 4.0.x"; Story 1.1 says "latest compatible GA". 4.1.2 is current GA and spike proves Modulith 2.1.2 + Spring AI 2.0.2 work on it. Pin 4.1.2; record in ADR-8 and update architecture tech-stack row.

### [2026-10-22] NOTE — Checkstyle ConstantName suppressed for *ArchitectureTests.java (ArchUnit idiom: camelCase static final rules).

### [2026-10-22] CORRECTION — JaCoCo *Application exclusion removed; main() covered by tests instead (gate must reflect raw coverage).

### [2026-10-23] CORRECTION — per-user daily cap 5 → 3 in docs/security STRIDE row
User chose 3 for Story 1.4 (MIZAN_QUOTA_DAILY_PER_USER=3); security doc said 5. Doc aligned to the user decision.

### [2026-10-23] CORRECTION — sign-up flow (UX doc)
Keycloak 26.8 + verify-email: password set after verification. Flow 1 updated.

### [2026-10-23] CORRECTION — idx_contracts_owner_created adds id DESC
Keyset pagination (🔴 Sprint 2) orders by (created_at, id); index is (owner_sub, created_at DESC, id DESC) instead of the DB doc's two columns. DB doc to update in B7.

### [2026-10-26] CORRECTION — Migration order swapped: V2 = modulith event_publication (Sprint 3), V3 = daily_usage (Sprint 4). Flyway rejects a lower version added after a higher one. docs/database updated.

### [2026-10-26] CORRECTION — Results apply while status IN (PENDING, ANALYZING)
Nothing sets ANALYZING yet (analyzer emits no "started" event). Story 3.3 guard = "not yet terminal" via one conditional UPDATE. The PENDING→ANALYZING step moves to Story 3.4 (SSE stepper), where it is needed. docs/database access pattern updated.

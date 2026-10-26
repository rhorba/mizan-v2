# Risk Log
<!-- Tracks risks identified and their mitigations -->
<!-- Format: ### [YYYY-MM-DD HH:MM] SECURITY/PERFORMANCE/DEPENDENCY — Title -->


### [2026-10-22] DEFERRED (DONE 2026-10-23, Story 1.4 B2) — Keycloak passwordBlacklist
Security doc asks for Keycloak password blacklist; needs a blacklist file mounted into Keycloak. Deferred to Story 1.4 (Keycloak theme/hardening). Admin TOTP likewise 1.4.

### [2026-10-22] RISK — Keycloak 26.8.1 base image CVEs (2026-10-23: ca-certificates now "fixed" in RHEL only → time-boxed exception to 2026-11-30, see decisions)
UBI9 ca-certificates CVE-2026-114406 (CRITICAL, distrust eMudhra intermediate) and java-21-openjdk CVE-2026-91415 (HIGH, font rendering) — no fixed package yet. Local only for now; recheck on Keycloak 26.8.2 / before Story 7.2 preprod.

### [2026-10-23] RISK — local cold-start margin under the 120 s target
Fresh-volume runs: 75, 81, 98, 148 (host busy: Playwright install), 81, 87 s. Keycloak's silent schema creation (≈30 s, 73 s under load) dominates. CI: 58 s. Mitigation if it bites: Keycloak DB on tmpfs for local, or pre-seeded volume. Watch in CI stack job.

### [2026-10-26] RISK — Sprint 3 scope (🔴) estimated 5–6 days vs 4-day capacity; may spill into Sprint 4. Mitigation: replay screen last, can move to Sprint 4.

# Risk Log
<!-- Tracks risks identified and their mitigations -->
<!-- Format: ### [YYYY-MM-DD HH:MM] SECURITY/PERFORMANCE/DEPENDENCY — Title -->


### [2026-10-22] DEFERRED — Keycloak passwordBlacklist
Security doc asks for Keycloak password blacklist; needs a blacklist file mounted into Keycloak. Deferred to Story 1.4 (Keycloak theme/hardening). Admin TOTP likewise 1.4.

### [2026-10-22] RISK — Keycloak 26.8.1 base image unfixed CVEs
UBI9 ca-certificates CVE-2026-114406 (CRITICAL, distrust eMudhra intermediate) and java-21-openjdk CVE-2026-91415 (HIGH, font rendering) — no fixed package yet. Local only for now; recheck on Keycloak 26.8.2 / before Story 7.2 preprod.

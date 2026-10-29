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

### [2026-10-26] RISK — Spend budget resets if the user deletes today's contracts
Analyses cascade-delete with contracts, so deleting lowers today's spend. Org cap $3/month bounds the damage. Fix with daily_usage (Story 5.1): record tokens/cost there, not derived from analyses.

### [2026-10-26] RISK — Tokens of failed analyses are not counted in the spend budget
analysis-failed (schema v1) carries no token counts, so the 2 billed attempts of an invalid-output failure don't reach analyses.input/output_tokens. Small (bounded by the $3 cap). Fix with a v2 failed message or with daily_usage (Story 5.1).

### [2026-10-26] RISK — Contract text outlives deletion in Kafka topics
analysis-requested and .DLT keep text until retention (compose 24 h). Set retention.ms on these topics for preprod (Sprint 5–6 infra). Also Jackson parse errors may quote ≤500 chars of a malformed payload in analyzer logs / DLT headers (not attacker-reachable).

### [2026-10-26] RISK — Sprint 4 🔴 ~6 days vs 4.5-day capacity. Mitigation: multi-instance SSE fan-out and charts last; first to move to Sprint 5.

### [2026-10-27] RISK — time-zone data drift between JDK and PostgreSQL
Two tz databases (JDK tzdb.dat, Alpine tzdata) update on different schedules; any day math split across them can disagree. Mitigation: single source of truth for "which day is it" (decision pending).

### [2026-10-28] RISKS — from the Sprint 5 security review (act in Sprint 6 / Story 7.2)
- R-OTLP (MEDIUM, preprod): browsers post spans to the OTLP collector; exposed publicly it accepts anything (spam, cost, log injection). Before preprod: same-origin `/otlp` path via Traefik with CORS = app origin, body-size + rate limit, or disable browser tracing in the preprod build.
- R-GRAFANA (MEDIUM, preprod): otel-lgtm Grafana is anonymous Admin locally; in the cluster it must sit behind basic auth (docs/devops §3) and never be exposed without it.
- R-ADMIN-IP (LOW): owner IP is dynamic; SSH breaks when it changes (fail-closed). Update admin_cidr and re-apply (security-list-only change).
- R-A1 (LOW): node is the whole Always Free A1 allowance in a single-AD region; a lost instance may not be re-creatable immediately (capacity). Backups to mizan-backups (Story 7.2) are the recovery path.
- R-SMTP-CHAT (LOW, 2026-10-29): the Gmail app password `mizan-preprod` was pasted in chat (session transcript). Owner to revoke it and create a new one pasted straight into .env before 7.4 / v2.0, then re-run deploy/secrets.sh. Scope: send-mail only for rrhorba.mohamed@gmail.com.

# Decision Log
<!-- Tracks architecture decisions, approach selections, tool choices -->
<!-- Format: ### [YYYY-MM-DD HH:MM] ARCHITECTURE/APPROACH/TOOL — Title -->

### [2026-10-22] APPROACH — Mizan v2 as a fresh repo built with CTS
Market scan of on-site/hybrid Java offers (Free-Work Tech Lead + Java/Spring missions; ALTEN, Capgemini, Sofrecom, Devoteam Morocco) showed gaps: Spring Boot 4, Kafka with Java, cloud + Terraform, OpenTelemetry, AI in a Java service, Sonar gate, and no live Java app. User chose: same product as mizan-ma (AI contract analysis), rebuilt from scratch in a new PUBLIC repo rhorba/mizan-v2, CTS framework, hosting on Oracle Cloud Always Free + k3s via Terraform. mizan-ma stays untouched as v1.

### [2026-10-22] ARCHITECTURE — v2 foundation choices (pending approval)
2 Spring Boot 4 apps (modular monolith API + stateless analyzer) instead of v1's 6 services; Keycloak replaces hand-written auth; Spring Modulith event externalization as outbox; PDFs not stored (text only); SSE for status; grafana/otel-lgtm; Argo CD pull deploy (cluster API never exposed); BUSINESS role dropped.

### [2026-10-22] DECISION — Story 1.1 approach: 🔴 COMPREHENSIVE
Parent POM + BOMs (Boot, Spring AI, Modulith) + plugin mgmt, JaCoCo aggregate, compatibility spike with fallbacks (Arch §8), plus Maven Enforcer (Java 25, dependency convergence/banned dups), ArchUnit + Modulith verification tests, shared Checkstyle config. Chosen by user.

### [2026-10-22] DECISION — Story 1.2 approach: 🔴 COMPREHENSIVE
compose.yaml with infra (default) + `apps` profile (mizan-api, mizan-analyzer Dockerfiles) + `mail` profile (Mailpit); DB init script (schemas mizan/keycloak + roles); minimal realm JSON; `up --wait` 120 s health proof. Chosen by user. Env defaults pending user confirmation at PLAN gate.

### [2026-10-22] DECISION — Story 1.3 approach: 🔴 COMPREHENSIVE
ci.yml: lint, test-backend, test-frontend, security (Gitleaks, Semgrep SARIF, Trivy fs+config SARIF), stack (compose build + stack-check --profile apps + Trivy image scan). SHA-pinned actions, least-privilege permissions, concurrency, caches, coverage artifacts + summary, dependabot.yml weekly. Branch protection on main requiring checks. Chosen by user.

### [2026-10-23] DECISION — Story 1.4 approach: 🔴 COMPREHENSIVE; daily cap 3
Resource server + /me/quota (cap from MIZAN_QUOTA_DAILY_PER_USER=3, counting in 5.1), Testcontainers Keycloak abuse tests, Angular Material, Transloco fr/ar + RTL, landing per UX doc, full Keycloak login theme, password blacklist, admin TOTP, Playwright E2E + sprint video, e2e CI job. Chosen by user.

### [2026-10-23] DECISION — CVE-2026-114406 time-boxed exception (user chose A)
docker/keycloak/trivyignore.yaml: this CVE only, Keycloak image only (CI --ignorefile for mizan-keycloak:local), expired_at 2026-11-30, statement with reason. Fixed ca-certificates only in RHEL subscription repos; UBI + Keycloak 26.8.1 still vulnerable.

### [2026-10-23] DECISION — Sprint 2 approach: 🔴 COMPREHENSIVE
🟡 (hardened PDF handling, stable problem types + i18n mapping, full IDOR suite, audit log, accessible drop zone, placeholder contract view, E2E + video) + per-user upload rate limit in the API, ClamAV scan, upload progress bar, keyset pagination. Chosen by user.

### [2026-10-23] DECISION — ClamAV configured-but-down = fail-closed (503)
Owner chose "skip scanning" when no scanner is configured. When MIZAN_CLAMAV_HOST IS set and clamd is unreachable, uploads are refused with 503 scanner-unavailable rather than silently unscanned (an explicit opt-in must not degrade silently). Flagged to owner for confirmation.

### [2026-10-26] DECISION — Analyzer model: claude-opus-5-5 (owner choice)
Options shown: Haiku 5.5 (~$0.002/analysis), Sonnet 5.5 (~$0.035), Opus 5.5 (~$0.07, more with always-on thinking). Owner picked Opus 5.5. Org spend cap $3/month is the hard ceiling. Model + max tokens via env (MIZAN_AI_MODEL, MIZAN_AI_MAX_TOKENS).

### [2026-10-26] DECISION — ADR-6 amendment: no temperature
Claude API rejects temperature/top_p/top_k since 2026-10-21; Opus 5.5 also rejects disabled thinking. Analyzer sends neither; depth controlled by effort only.

### [2026-10-26] DECISION — Live Claude test is opt-in
Stub ChatModel in all automated tests; one test tagged `live` runs only when ANTHROPIC_API_KEY is set (never in CI).

### [2026-10-26] DECISION — Sprint 3 approach: 🔴 COMPREHENSIVE (owner choice)
🟡 (outbox→Kafka, analyzer w/ structured output + 3 retries + DLT, idempotent apply, prompt-injection guard + output validation, fr/ar/en prompts, schemaVersion, model/token capture, OTel metrics, crash-after-commit test) + per-user daily cost budget in analyzer, system-prompt caching, admin DLT replay screen, Kafka schema contract tests. Est. 5–6 days vs 4-day sprint → overrun risk logged.

### [2026-10-26] DECISION — Sprint 4 caps and usage data (owner)
Per-user cap 5/day (PRD value; was 3), global cap 20/day (MIZAN_QUOTA_DAILY_GLOBAL, sized to the $3/month org cap). daily_usage records tokens too, so the spend budget survives contract deletion and counts failed analyses (analysis-failed gains token counts → schema v2).

### [2026-10-26] DECISION — Sprint 4 approach: 🔴 COMPREHENSIVE (owner)
🟡 (all 5 stories, SSE + 3 s polling fallback, analysis-started message from analyzer sets ANALYZING, analysis-failed v2 with tokens, view with sort/filter/expand/copy/RTL, re-analyze tabs, admin stats, E2E) + admin charts (14-day trend, risk split), axe accessibility audit on new pages, multi-instance SSE via Kafka fan-out. Est. ~6 days vs 4.5.

### [2026-10-27] DECISION — PostgreSQL is the single source of "which day" (owner choice, tz drift issue)
DailyUsage.today()/dayOf() ask the DB (ts AT TIME ZONE zone) using the injected Clock instant; stats window bounds computed in SQL too. Rejected: JDK TZUpdater (3 places to maintain), fixed UTC (hides drift), defer.

### [2026-10-27] DECISION — admin chart colors (dataviz validator, both modes)
14-day stacked columns: done=status-good #0ca30c, in progress=categorical blue #2a78d6 (dark #3987e5), failed=status-critical #d03b3b, stacked done→in progress→failed so red/green never touch: ALL PASS light (#fff) and dark (#1b201e). App text tokens (risk-*/primary) FAIL as fills (lightness band, deutan 5.8) — kept for text only. Risk split: single series (flag counts by level) → one hue (blue), rows labelled in words; the 3-hue risk set failed CVD (protan 5.6).

### [2026-10-27] DECISION — Sprint 5 approach (owner)
6.1 🔴: backend OTel through Kafka/analyzer/model/apply, Loki logs with trace ids, no-text test, browser OTel (trace starts at upload click), mizan.analyses.tokens, Grafana dashboards + alert rules as code (notification channel waits for SMTP, Sprint 6). 6.2: sonar CI job with quality-gate wait. 7.1 🟡: bootstrap config (state + backup buckets), network/firewall/A1/reserved IP/k3s, AD retry, Trivy IaC in CI; apply only after owner OK. Terraform upgrade via winget approved.

### [2026-10-28] DECISION — SonarCloud setup
Free org `rhorba`, project `rhorba_mizan-v2`; GitHub App limited to mizan-v2 only (not all repos, no auto-import). CI-based analysis (Automatic Analysis off: it cannot ingest coverage). Sonar job runs on main + PRs only; skipped without the secret (Dependabot PRs) rather than failing. Token: owner-generated, expiry chosen by owner.

### [2026-10-28] DECISION — guava CVE-2026-102554 in the Keycloak image: option A
Owner accepted a time-boxed Trivy exception (docker/keycloak/trivyignore.yaml, expires 2026-11-30, same as CVE-2026-114406). Recheck on every Keycloak release and before preprod (Epic 7).

### [2026-10-28] DECISION — Sonar java:S5693 on the upload limit: ignore in repo, file-scoped
Owner accepted: 10 MB stays the product limit; suppression lives in git (sonar-project.properties) with the reason, scoped to mizan-api application.yml only.

### [2026-10-29] Sprint 6 env/hosting
sslip.io host, Gmail SMTP, public GHCR multi-arch (amd64+arm64) images. New gap found: CI publishes no images yet — part of 7.2.

### [2026-10-29] 7.2 promotion = A (CI commits newTag <sha> to overlays/preprod, [skip ci], Argo CD auto-sync)
Rejected B (Image Updater: extra controller + in-cluster Git credential), C (mutable tag: not reproducible).

### [2026-10-29] B2 design calls (DevOps + Security)
- Keycloak on its own host auth.<ip>.sslip.io (http-relative-path is build-time; /auth would change the local issuer). Grafana on grafana.<ip>.sslip.io (cookie isolation). Web, /api, /otlp share the app origin.
- R-GRAFANA: Grafana's own login (anonymous off, generated admin password) instead of Traefik basic auth in front — one login, Grafana's brute-force protection. devops §3 updated.
- One realm file for local and preprod: ${MIZAN_WEB_ORIGIN:…} and ${SMTP_*:…} placeholders, defaults = previous local values (verified both ways).
- ClamAV: clamav/clamav-debian:1.5.4 (clamav/clamav is amd64-only).
- Overlays: preprod only (local = compose). Files shared with compose referenced in place → kustomize --load-restrictor LoadRestrictionsNone (CI + argocd-cm).
- ClusterIssuer created from .env at bootstrap (keeps LETSENCRYPT_EMAIL out of the public repo).

### [2026-10-29] Code scanning KSV-0037 ×2 (HelmChart in kube-system) dismissed — owner approved
k3s helm-controller convention; workloads run in argocd / cert-manager. Alerts #47, #48 "won't fix"; PR #17 threads resolved.

### [2026-10-30] DECISION — B4 (Story 7.3) design, owner approved
Backup: CronJob nightly 02:30 UTC, own image (postgres:18.6-alpine + age + curl, published multi-arch), `pg_dump -Fc mizan | age -r $BACKUP_AGE_RECIPIENT | curl -T - $BACKUP_PAR_URL`. Bucket: write-only PAR (AnyObjectWrite, no read/list, 1-year expiry) + lifecycle delete > 7 days (Terraform). Restore test once into a throwaway local Postgres. Uptime: public /api/health → Traefik replacePath → /actuator/health/readiness; GH Actions cron 30 min, issue after 2 consecutive failures, closed on recovery. age private key outside repo (~/.mizan/backup-age.key) + owner's password manager. Kafka not backed up (24 h retention; Postgres is the source of truth).

### [2026-10-30] DECISION — CVE-2026-91415 (Keycloak JDK font rendering): option A, owner
Time-boxed Trivy exception to 2026-11-30 (docker/keycloak/trivyignore.yaml), same policy as guava/ca-certificates. No fixed Keycloak image (26.8.1 and 26.8.1-1 both JDK 21.0.12.1; ubi9-micro has no package manager). Rejected: B JDK swap (untested upstream), C wait (CI red). Verified locally: Trivy exit 0 with the ignorefile.

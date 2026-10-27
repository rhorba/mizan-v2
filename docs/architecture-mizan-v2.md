# Architecture: Mizan v2
**PRD Reference**: docs/prd-mizan-v2.md
**Version**: 1.1 | **Date**: 2026-10-22 | **Author**: Software Architect / Tech Lead

## 1. Overview
Two Spring Boot 4 applications on Java 25 — a modular monolith API and a stateless analyzer worker —
connected by Kafka through a transactional outbox, with Keycloak for identity and an Angular 22 SPA.
Exact library versions are pinned in ADR-8 (Story 1.1 spike).

| Layer | Choice |
|---|---|
| Language / runtime | Java 25 LTS, virtual threads on |
| Framework | Spring Boot 4.1.x (Spring Framework 7, Jackson 3, Security 7) — exact pins in ADR-8 |
| Modularity | Spring Modulith 2.x (module boundaries verified in tests, event publication registry) |
| Messaging | Apache Kafka 4.x (KRaft), Spring for Apache Kafka |
| AI | Spring AI 2.x, Anthropic starter, structured output to Java records |
| Identity | Keycloak 26 (OIDC, PKCE), API = OAuth2 resource server |
| Data | PostgreSQL 18, Spring Data JPA, Flyway |
| PDF | Apache PDFBox 3 |
| Frontend | Angular 22 (standalone, signals, zoneless), Angular Material, angular-oauth2-oidc, Transloco (fr/ar) |
| Build | Maven multi-module wrapper; npm |
| Tests | JUnit Jupiter, Testcontainers 2 (Postgres, Kafka, Keycloak), Modulith `ApplicationModules.verify()`, Vitest, Playwright |
| Observability | Spring Boot OpenTelemetry starter (traces, metrics, logs over OTLP) |
| Infra | Terraform (OCI provider), k3s, Kustomize, Argo CD, cert-manager, GHCR multi-arch images |

## 2. Architecture Decision Records
### ADR-1: Modular monolith + one worker (not microservices)
- **Context**: v1 had 6 services for one product with zero traffic.
- **Decision**: `mizan-api` with Spring Modulith modules `contracts`, `quota`, `admin`, `shared`; `mizan-analyzer` as a separate deployable because it has a different failure mode (slow third-party calls) and no database.
- **Alternatives**: v1 layout (rejected: cost, duplicated config); single app with an in-process async executor (rejected: loses requests on restart and gives no Kafka proof).
- **Consequences**: one DB schema owned by one app; module boundaries enforced by `ApplicationModules.verify()` in CI.

### ADR-2: Spring Modulith event externalization as the transactional outbox
- **Context**: "save contract" and "request analysis" must succeed or fail together.
- **Decision**: the `contracts` module publishes `AnalysisRequested` as a Spring application event inside the upload transaction; Modulith's event publication registry stores it in the same transaction (`event_publication` table) and externalizes it to Kafka (`@Externalized("contract.analysis-requested::#{contractId}")`). Incomplete publications are resubmitted on startup and by a scheduled job.
- **Alternatives**: hand-written outbox table + poller (more code, same guarantees); Debezium CDC (extra Kafka Connect cluster — YAGNI).
- **Consequences**: at-least-once delivery → consumers must be idempotent (ADR-4).

### ADR-3: Keycloak instead of hand-written JWT auth
- **Context**: v1 built register, login, refresh, email verification and lockout by hand (38 classes).
- **Decision**: Keycloak 26 realm `mizan`, public client `mizan-web` (Authorization Code + PKCE), self-registration with verify-email, brute-force detection on. API validates JWTs (issuer + audience). Roles `user`, `admin` as realm roles.
- **Alternatives**: Spring Authorization Server (still need to build UI flows); v1 code (more attack surface).
- **Consequences**: +1 GB RAM; realm config exported as JSON in the repo and imported at start.

### ADR-4: Idempotent consumers keyed by analysis id
- **Decision**: every analysis request has an `analysisId` (UUID). The analyzer is stateless and may, on redelivery, call Claude twice (accepted, rare). The API applies a result only if the analysis is still `ANALYZING`; duplicates are ignored. Failures after Spring Kafka retries (3, exponential back-off) go to `contract.analysis-requested.DLT` and are reported as `FAILED`.

### ADR-5: Do not store PDFs
- **Decision**: extract text with PDFBox during upload, store only the text (in Postgres), discard the bytes. Removes object storage, signed URLs and a whole class of leaks.
- **Consequences**: no "download original" feature; re-analysis reuses the stored text.

### ADR-6: Spring AI structured output
- **Decision**: `ChatClient.prompt().system(...).user(...).call().entity(AnalysisOutput.class)` where `AnalysisOutput(String summary, List<ClauseFlag> flags)`; model, max tokens and temperature from config. Contract text is placed in a delimited block and the system prompt instructs the model to treat it as data (prompt-injection guard).
- **Amendment (2026-10-26, Sprint 3)**: the Claude API rejects `temperature`/`top_p`/`top_k` since 2026-10-21, so none is sent. The analyzer calls `ChatModel` directly with plain `SystemMessage`/`UserMessage` (no template engine: braces in a contract would break rendering) and the API's native structured output (`outputSchema` from `AnalysisOutput`). Model and max tokens are set on **every** request from `MIZAN_AI_MODEL` / `MIZAN_AI_MAX_TOKENS` (default `claude-opus-5-5`, 16000): per-call options do not inherit Spring AI defaults, and a live run silently fell back to Spring AI's default model. The system prompt is cached (`SYSTEM_ONLY`, 5 min). An unusable answer is retried once; `OutputValidator` enforces counts and lengths. Reported input tokens include cache reads and writes.

### ADR-7: Server-Sent Events for status
- **Decision**: `GET /api/v1/contracts/{id}/events` streams status changes; the SPA falls back to polling if SSE drops.
- **Amendment (2026-10-27, Sprint 4)**: the analyzer publishes `contract.analysis-started` (PENDING → ANALYZING); the API externalizes every status change to `contract.analysis-status`, which each API instance consumes with its **own** consumer group (`mizan-api-status-<random>`), so a browser connected to any instance hears every change. The stream carries ids and status only; the SPA reads it with `fetch` (bearer header — `EventSource` would need the token in the URL) and polls every 3 s when it cannot open or ends. Heartbeat 15 s, timeout 5 min. At most 10 open streams per user (security review: unbounded streams could exhaust server connections); beyond that, 429 and the page polls.

### ADR-8: Pinned versions (Story 1.1 spike, 2026-10-22)
- **Context**: the stack was chosen before checking which GA releases work together on Boot 4. Architecture §8 listed fallbacks (Anthropic Java SDK, hand-written outbox) in case Spring AI or Modulith were not ready.
- **Decision**: pin the latest GA of each, proven by a spike (context start, `@Externalized` + JDBC event registry + `spring-modulith-events-kafka`, `ChatClient...entity()`, `ApplicationModules.verify()`). No fallback needed.

| Component | Version | Managed by |
|---|---|---|
| Java (Temurin) | 25.0.3 LTS | Enforcer `[25,)` |
| Maven (wrapper 3.3.4) | 3.10.0 (Dependabot #7; SHA-256 re-verified against Central's SHA-512) | Enforcer `[3.9,)` |
| Spring Boot | **4.1.2** (Framework 7.0.10, Security 7.1.2, Jackson 3.1.7) | parent POM |
| Spring for Apache Kafka / Kafka client | 4.1.2 / 4.2.2 (broker image 4.3.1: newer brokers accept older clients) | Boot BOM |
| Spring Modulith | 2.1.2 | `spring-modulith-bom` |
| Spring AI | 2.0.2 (anthropic-java-core 2.72.0) | `spring-ai-bom` |
| victools jsonschema | 5.0.0 | `jsonschema-generator-bom` (see below) |
| Testcontainers / JUnit Jupiter | 2.0.5 / 6.0.3 | Boot BOM |
| ArchUnit | 1.5.1 | parent POM |
| Spotless (google-java-format) / Checkstyle / JaCoCo / Enforcer | 3.10.4 / 14.3.0 / 0.8.15 / 3.6.3 | parent POM |
| Angular / CLI | 22.2.x / 22.2.3 | `package.json` |
| TypeScript / Vitest / angular-eslint | 6.0 / 5.0 / 22 | `package.json` |
| angular-oauth2-oidc / Angular Material + CDK / Transloco | 22.0.2 / 22.2.4 / 8.4.0 | `package.json` |
| PDFBox / Bucket4j / ClamAV image | 3.0.8 / 8.21.0 / `clamav/clamav:1.5.4` | parent POM / compose |
| Playwright / testcontainers-keycloak | 1.64.1 / 4.4.0 | `package.json` / parent POM |
| Node | ^22.22.3 \|\| ^24.15.0 (dev: 22.23.3) | `engines` |

- **Deviations from this document**:
  - Boot **4.1.2** instead of 4.0.x — 4.1 is the current GA line and Modulith 2.1 targets it.
  - victools jsonschema forced to **5.0.0**: Spring AI 2.0.2 needs 5.x, anthropic-java-core 2.72.0 asks for 4.38 (runtime). Enforcer `dependencyConvergence` flagged it; we only use Spring AI's schema path. Re-check on every Spring AI upgrade.
  - lz4-java forced (1.11.4, now **1.12.0** via Dependabot #6): kafka-clients 4.2.2 brings 1.11.2 (CVE-2026-106451, HIGH, found by Trivy on the analyzer image). Remove when Boot/Kafka bump it.
  - Local images (Story 1.2): `postgres:18.6-alpine`, `apache/kafka:4.3.1` (Dependabot #5), Keycloak **26.8.1 pre-built** (`docker/keycloak.Dockerfile`, `start --optimized` — start-dev re-runs the Quarkus build each boot and missed the 2-minute target), `grafana/otel-lgtm:0.37.0`, `axllent/mailpit:v1.31.6`; app runtime `eclipse-temurin:25.0.4.1_1-jre-alpine` pinned by digest.
  - npm `overrides` **vite 8.3.5**: `@angular/build` 22.2.4 pins 8.3.0, which has dev-server advisories (GHSA-9jrq-w75r-8gcw et al.). Remove when Angular bumps Vite.
- **Build rules**: Enforcer (Java, Maven, dependency convergence, no duplicate POM deps), Spotless + Checkstyle at `validate`, JaCoCo ≥ 80 % line coverage per module (no exclusions) with an aggregate report in `coverage-report`, ArchUnit + Modulith verification tests; Vitest thresholds 80 % on all metrics.
- **Consequences**: upgrades are one property each in the parent POM; convergence failures surface on the first build instead of at runtime.

### ADR-9: Authentication as built (Story 1.4, 2026-10-23)
- **Context**: ADR-3 chose Keycloak + PKCE; building it surfaced choices the doc did not fix.
- **Decision — API**: explicit `JwtDecoder` (JWK set URI) with our own validator chain (timestamp 30 s skew, issuer, audience `mizan-api`) so every rule is unit-testable; `realm_access.roles` → `ROLE_*`; stateless, CSRF off (bearer header only, no cookies); `/actuator/health` public, `/api/v1/admin/**` admin, `/api/**` authenticated, everything else denied; 401/403 as RFC 9457 with `WWW-Authenticate`; RFC 9728 protected-resource metadata names Keycloak. Issuer (browser view, `localhost:8180`) and JWK set URI (in-network `keycloak:8080`) are configured separately.
- **Decision — SPA**: angular-oauth2-oidc, code + PKCE S256, `prompt=create` for sign-up, `ui_locales` from the UI language, refresh via refresh token (no iframe). `TransientOAuthStorage`: tokens in memory only; PKCE verifier + nonce in `sessionStorage` for the redirect and removed after login; RFC 9207 `iss` stripped from the URL. Bearer token sent only to `allowedUrls = [apiUrl]`. A reload re-authenticates silently through the Keycloak SSO session.
- **Decision — Keycloak**: CSS-only `mizan` login theme on `keycloak.v2` (no template overrides; RTL from parent), password blacklist (117 k entries, SecLists, MIT) baked into the image, admin via `scripts/make-admin.sh` (role + CONFIGURE_TOTP). Health check waits for the realm discovery document: `/health/ready` is UP before the realm import finishes.
- **Consequences**: with verify-email on, Keycloak 26.8 defers the password to after verification (sign-up → verify → set password); E2E and UX doc follow it. TOTP is enforced once configured; an always-OTP flow for the `admin` role is left for preprod hardening (Epic 7). `/me/quota` returns the configured cap with `used = 0` until Story 5.1 counts usage.

### ADR-10: Upload, history and deletion as built (Sprint 2, 2026-10-23)
- **Context**: Stories 2.1, 2.2, 4.2 with the owner's comprehensive option (ClamAV, rate limit, progress bar, keyset paging).
- **Decision — persistence**: Flyway V1 exactly as the DB doc (plus `id DESC` in the history index for keyset); Hibernate `validate`; UUIDv7 ids generated by Hibernate; app connects as `mizan_app` (no schema creation). Tests use one PostgreSQL 18 Testcontainer per JVM through the `jdbc:tc:` URL; raw test queries are always scoped to their own rows.
- **Decision — upload pipeline** (`POST /api/v1/contracts`, 202): per-user rate limit (Bucket4j, 10/min, servlet filter after Security → 429 + `Retry-After`) → ClamAV INSTREAM (before any parser; 422 `malware-detected`; no host configured = skipped with a start-up warning, host configured but down = 503 `scanner-unavailable`) → size → `%PDF-` → PDFBox (16 MB heap, rest temp files) → encrypted (also owner-only) → ≤ 30 pages before extraction → text (NUL stripped) → < 20 visible chars/page = scanned. File names: last segment, NFC, no control/bidi/zero-width characters, ≤ 255. PDF bytes are never stored (no `bytea` column exists — asserted by a test).
- **Decision — errors**: RFC 9457 with stable `type` URNs `urn:mizan:problem:{slug}`; the SPA maps slugs to fr/ar messages and decides which are retryable with the same file.
- **Decision — history and access**: keyset pagination over `(created_at, id)` with an opaque cursor (tampered → 400 `bad-cursor`); latest analysis of a page in one query; foreign and missing ids both 404 with identical bodies. The IDOR suite discovers every `/api/v1/contracts/{id}…` endpoint from Spring's handler mappings, so new endpoints are covered automatically.
- **Decision — SPA**: XHR backend (fetch cannot report upload progress); the page requested before login travels in the OIDC `state` and is restored only if it is a same-origin path; audit log lines carry ids only.
- **Consequences**: ClamAV costs ~1–1.5 GB RAM and a ~300 MB signature download, so it is the opt-in compose profile `av` (CI e2e runs it with a weekly signature cache). Rate-limit buckets live in memory: correct for one API instance; revisit if the API is ever scaled out.

## 3. System Design
```
mizan-web (Angular) ──REST/SSE──> mizan-api
                                   ├── contracts  (upload, extract, history, delete, re-analyze, SSE)
                                   ├── quota      (per-user + global daily caps)
                                   ├── admin      (stats queries)
                                   └── shared     (security config, problem details, tracing)
mizan-api ──outbox──> Kafka ──> mizan-analyzer ──Spring AI──> Anthropic
mizan-api <────────── Kafka <── mizan-analyzer (completed | failed)
```

## 4. Data Model
```
Contract (owner_sub = Keycloak subject) ──1:N──> Analysis ──1:N──> ClauseFlag
DailyUsage (owner_sub, day) ; event_publication (Modulith)
```
Full schema: `docs/database-mizan-v2.md`.

## 5. API Design
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | /api/v1/contracts | Upload PDF (multipart: file, language) → 202 + contract | user |
| GET | /api/v1/contracts | List own contracts (paged, newest first) | user |
| GET | /api/v1/contracts/{id} | Contract, latest analysis and every analysis (newest first) | owner |
| GET | /api/v1/contracts/{id}/analyses/{analysisId} | One analysis: summary, status, flags in model order (no model/tokens) | owner |
| GET | /api/v1/contracts/{id}/events | SSE status stream (current status first; 429 `too-many-streams` beyond 10 per user) | owner |
| POST | /api/v1/contracts/{id}/analyses | Re-analyze `{language}` from the stored text → 202 + Location; one quota unit; 409 `analysis-in-progress` / `already-analyzed` | owner |
| DELETE | /api/v1/contracts/{id} | Delete contract, analyses, text | owner |
| GET | /api/v1/me/quota | Analyses left today | user |
| GET | /api/v1/admin/stats | 14 days by status, failure rate, risk split, today's global usage/tokens/spend (counts only) | admin |
| GET | /api/v1/admin/dead-letters, POST /api/v1/admin/analyses/{id}/retry | DLT list and retry | admin |

Errors use RFC 9457 Problem Details. OpenAPI generated by springdoc and published as a CI artifact.

**Kafka contracts** (JSON, versioned by a `schemaVersion` field):
- `contract.analysis-requested` — `{analysisId, contractId, language, text, requestedAt}`
- `contract.analysis-completed` — `{analysisId, contractId, summary, flags[], model, inputTokens, outputTokens}`
- `contract.analysis-failed` — `{analysisId, contractId, reason, inputTokens, outputTokens}` (schema v2, Sprint 4)
- `contract.analysis-requested.DLT` — requests that failed after retries (raw bytes if unreadable), with `kafka_dlt-*` headers; listed and retried from the admin screen (`/api/v1/admin/dead-letters`)

Each message is plain JSON without Java type headers; `reason` is `invalid-output` or `model-unavailable`. The exact schemas live in `docs/schemas/kafka/*.v1.schema.json` and both apps' builds check their records against them.

## 6. Security Considerations
See `docs/security-mizan-v2.md`. Key points: OIDC + PKCE, owner check on every contract query
(`WHERE id = ? AND owner_sub = ?`), caps, prompt-injection guard, secrets only in Kubernetes Secrets.

## 7. Infrastructure
- Hosting: Oracle Cloud Always Free, 1 × A1.Flex, k3s — `docs/devops-mizan-v2.md`
- Database: PostgreSQL 18 StatefulSet, nightly `pg_dump` to OCI Object Storage
- CI/CD: GitHub Actions (build, test, scan, Sonar, multi-arch images to GHCR) → Argo CD pulls
- Monitoring: OpenTelemetry → grafana/otel-lgtm

## 8. Technical Risks
| Risk | Mitigation | Owner |
|---|---|---|
| Spring AI / Modulith not yet GA for Boot 4 at pin time | Sprint 1 spike; fallback: Anthropic Java SDK behind a port, hand-written outbox | Tech Lead |
| Kafka + Keycloak make local dev heavy | Docker Compose profiles; Testcontainers reuse | Backend Dev |
| Claude returns invalid JSON | Structured output + one repair retry, then `FAILED` | Backend Dev |

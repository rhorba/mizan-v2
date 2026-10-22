# Architecture: Mizan v2
**PRD Reference**: docs/prd-mizan-v2.md
**Version**: 1.0 | **Date**: 2026-10-22 | **Author**: Software Architect / Tech Lead

## 1. Overview
Two Spring Boot 4 applications on Java 25 — a modular monolith API and a stateless analyzer worker —
connected by Kafka through a transactional outbox, with Keycloak for identity and an Angular 22 SPA.
Exact library versions are pinned in Sprint 1 (Story 1.1) to the latest GA compatible with Spring Boot 4.

| Layer | Choice |
|---|---|
| Language / runtime | Java 25 LTS, virtual threads on |
| Framework | Spring Boot 4.0.x (Spring Framework 7, Jackson 3, Security 7) |
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

### ADR-7: Server-Sent Events for status
- **Decision**: `GET /api/v1/contracts/{id}/events` streams status changes; the SPA falls back to polling if SSE drops.

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
| GET | /api/v1/contracts/{id} | Contract + latest analysis + flags | owner |
| GET | /api/v1/contracts/{id}/events | SSE status stream | owner |
| POST | /api/v1/contracts/{id}/analyses | Re-analyze in another language → 202 | owner |
| DELETE | /api/v1/contracts/{id} | Delete contract, analyses, text | owner |
| GET | /api/v1/me/quota | Analyses left today | user |
| GET | /api/v1/admin/stats | Usage / status / risk statistics | admin |

Errors use RFC 9457 Problem Details. OpenAPI generated by springdoc and published as a CI artifact.

**Kafka contracts** (JSON, versioned by a `schemaVersion` field):
- `contract.analysis-requested` — `{analysisId, contractId, language, text, requestedAt}`
- `contract.analysis-completed` — `{analysisId, contractId, summary, flags[], model, inputTokens, outputTokens}`
- `contract.analysis-failed` — `{analysisId, contractId, reason}`

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

# Mizan v2 — AI contract analysis for Moroccan freelancers and SMEs

Upload a contract PDF and get a clause-by-clause summary in French, Arabic or Darija, with risky terms
flagged and corrections suggested. Document analysis only, not legal advice.

Full rebuild of [mizan-ma](https://github.com/rhorba/mizan-ma) on a 2026 enterprise Java stack:
Java 25 · Spring Boot 4 · Angular 22 · Apache Kafka (transactional outbox) · Spring AI ·
OpenTelemetry · PostgreSQL · Kubernetes (k3s) on Oracle Cloud, provisioned with Terraform.

**Status:** Sprint 5 — every upload is one OpenTelemetry trace from the browser through the API, Kafka and the analyzer (model call included) back to the result, with logs in Loki and no contract text in any span or log; Grafana dashboard and alert rules are code (`docker/grafana/`); SonarCloud quality gate in CI; Terraform for one Always Free A1 k3s node on Oracle Cloud (`infra/terraform`). Kubernetes manifests and GitOps arrive in Sprint 6. Design docs in `docs/`, pinned versions in ADR-8 (`docs/architecture-mizan-v2.md`).

## Build

Prerequisites: JDK 25, Node 22.22+ (or 24.15+). Maven comes with the wrapper.

| Part | Path | Build and test |
|---|---|---|
| API (Spring Boot, modular monolith) | `mizan-api/` | `./mvnw verify` (from the repo root) |
| Analyzer worker (Spring AI + Kafka) | `mizan-analyzer/` | same command |
| Web app (Angular) | `mizan-web/` | `npm ci && npm run lint && npm test` |

`./mvnw verify` runs formatting (Spotless) and Checkstyle, the Enforcer rules, unit and
architecture tests, and fails under 80 % line coverage. The combined coverage report lands in
`coverage-report/target/site/jacoco-aggregate/index.html`; Modulith module diagrams in
`mizan-api/target/spring-modulith-docs/`. Fix formatting with `./mvnw spotless:apply` and
`npm run format`.

## Run locally

Prerequisites: Docker with Compose v2.24+.

```bash
cp .env.example .env                        # local-only values; change ports if they clash
docker compose up -d --wait                 # Postgres 18, Kafka 4, Keycloak 26, otel-lgtm
docker compose --profile apps up -d --wait  # + mizan-api and mizan-analyzer in containers
docker compose --profile mail up -d --wait  # + Mailpit for Keycloak e-mails
docker compose --profile av up -d --wait    # + ClamAV (set MIZAN_CLAMAV_HOST=clamav; ~300 MB first start)
scripts/stack-check.sh [--fresh] [--profile apps]   # asserts everything healthy within 120 s
```

Web app and sign-in (stack running with `--profile apps --profile mail`):

```bash
cd mizan-web && npm ci && npm start         # http://localhost:4200 — sign up, verify via Mailpit
scripts/make-admin.sh you@example.com       # from the repo root: admin role + TOTP at next login
cd mizan-web && npx playwright test         # E2E: sign-up … logout; upload, history, delete (E2E_AV=1 with ClamAV)
# E2E_CLAUDE=1 adds one real analysis (needs ANTHROPIC_API_KEY in .env, a few cents on Opus)
cd mizan-analyzer && ../mvnw test -Dgroups=live -Dsurefire.excludedGroups=   # live model tests, same key
```

Sprint recordings are kept locally in `.recordings/` (git-ignored) and, for every CI run, in the `e2e-videos-and-traces` artifact.

| Service | URL (default ports) |
|---|---|
| Keycloak (realm `mizan`, admin console) | http://localhost:8180 |
| Grafana (traces, metrics, logs; dashboard "Mizan — overview", alert rules under Alerting) | http://localhost:3000 |
| mizan-api | http://localhost:8080/actuator/health |
| Mailpit | http://localhost:8025 |
| PostgreSQL | `localhost:5435`, database `mizan` |
| Kafka | `localhost:9092` |

Keycloak imports `keycloak/realm-mizan.json` on first start; delete the volumes
(`docker compose down -v`) to re-import after editing it.

Built with [Claude Team Skills (CTS)](https://github.com/rhorba/CTS).

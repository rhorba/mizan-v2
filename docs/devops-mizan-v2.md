# DevOps Foundation: Mizan v2
**Architecture**: docs/architecture-mizan-v2.md
**Security**: docs/security-mizan-v2.md
**Version**: 1.0 | **Date**: 2026-10-22 | **Author**: DevOps/DevSecOps

## 1. Environment Strategy
| Environment | Purpose | Deploy Trigger |
|---|---|---|
| local | Development — Docker Compose (Postgres, Kafka, Keycloak, otel-lgtm) + apps from IDE | Manual |
| preprod | Public demo on OCI k3s | Merge to `main` → CI pushes images → Argo CD syncs `deploy/overlays/preprod` |
| production | Not planned (YAGNI) | — |

## 2. CI Pipeline (GitHub Actions)
```yaml
jobs:
  lint:           # Spotless (Java), ESLint + Prettier (Angular), markdownlint, terraform fmt
  test-backend:   # ./mvnw verify — unit + Testcontainers + Modulith verify; JaCoCo gate 80 %
  test-frontend:  # vitest --coverage (gate 80 %), Playwright on compose stack
  sonar:          # SonarCloud scan + quality gate (needs SONAR_TOKEN); org rhorba, project rhorba_mizan-v2; main + PRs; Automatic Analysis off
  security:       # Gitleaks, Semgrep, Trivy fs + IaC (fail on CRITICAL/HIGH)
  publish:        # main only, after every gate: buildx linux/amd64,linux/arm64 →
                  # ghcr.io/rhorba/mizan-{api,analyzer,web,keycloak}:<sha>; SBOM; Trivy on both platforms
  promote:        # main only: force-push env/preprod = main@<sha> + one commit setting every newTag
                  # to <sha> (main is protected, so the bump never lands there); Argo CD tracks env/preprod
  # deploy: Argo CD in the cluster pulls — the cluster API is never exposed to CI
```
Dependabot: Maven, npm, GitHub Actions, Docker — weekly.

## 3. Infrastructure
- **Hosting**: Oracle Cloud Always Free — region chosen at setup (home region).
- **Terraform (`infra/terraform`)**: VCN, public subnet, internet gateway, route table, security list (22 from owner IP only, 80/443 from anywhere, 6443 closed), A1.Flex instance (4 OCPU / 24 GB, Ubuntu 24.04 aarch64, 100 GB boot volume), reserved public IP, Object Storage bucket for backups, cloud-init installing pinned k3s (Argo CD bootstrap moves to Story 7.2). Remote state in an OCI Object Storage bucket (Terraform `oci` backend, created by `bootstrap/`). Home region eu-marseille-1 has one availability domain: an A1 capacity error means retry later. Built and verified 2026-10-28 (reserved IP, 80/443 open, 6443 closed); runbook `infra/terraform/README.md`.
- **Compute**: k3s (Traefik, local-path storage), namespaces `mizan`, `platform`, `observability`, `argocd`.
- **Database**: PostgreSQL 18 StatefulSet; CronJob nightly `pg_dump | age` → backup bucket (keep 7).
- **Messaging**: Apache Kafka 4 official image (KRaft, single broker), StatefulSet, internal listener only.
- **Secrets**: Kubernetes Secrets created by `deploy/secrets.sh` from local `.env` (not in Git; missing passwords generated). Sealed Secrets if multi-maintainer later (YAGNI).
- **TLS / DNS**: cert-manager + Let's Encrypt HTTP-01 on sslip.io hosts: `144.24.200.83.sslip.io` (web, `/api`, `/otlp`), `auth.…` (Keycloak: `/realms/mizan`, `/resources` only; master realm and admin console via port-forward), `grafana.…`. Keycloak has its own host because `http-relative-path` is a build-time option.
- **Monitoring**: `grafana/otel-lgtm` pod; Grafana at `grafana.…` with anonymous access off and its own login (R-GRAFANA); browser spans only via same-origin `/otlp/v1/traces`, 512 KB body cap, rate limit (R-OTLP).

Repository layout:
```
mizan-api/  mizan-analyzer/  mizan-web/  keycloak/ (realm + theme)
deploy/base/  deploy/overlays/preprod/  deploy/argocd/   (local = compose.yaml)
infra/terraform/  compose.yaml  .github/workflows/
```

## 4. Security Scanning Gates
| Scanner | Scan Type | Fail Threshold |
|---|---|---|
| Semgrep | SAST — Java + TypeScript | Any ERROR finding |
| Trivy | SCA (Maven/npm), images, IaC (Terraform, k8s) | CRITICAL or HIGH with a fix available |
| Gitleaks | Secrets in history | Any finding |
| SonarCloud | Quality gate (coverage, duplication, security hotspots) | Gate failed |

## 5. Docker Setup
```dockerfile
# mizan-api / mizan-analyzer — layered Spring Boot jar, multi-arch base
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN ./mvnw -q -pl mizan-api -am package -DskipTests && \
    java -Djarmode=tools -jar mizan-api/target/*.jar extract --layers --destination /out

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /out/dependencies/ ./
COPY --from=build /out/spring-boot-loader/ ./
COPY --from=build /out/snapshot-dependencies/ ./
COPY --from=build /out/application/ ./
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
```
`mizan-web`: `node:22` build stage → `nginxinc/nginx-unprivileged` runtime serving `/browser`.

## 6. Monitoring Baseline
| Signal | Tool | Alert Threshold |
|---|---|---|
| Traces | Tempo (via otel-lgtm) | — (inspected in demo) |
| Logs | Loki | > 5 ERROR / 5 min |
| Metrics | Prometheus | API p95 > 1 s for 10 min; consumer lag > 50 for 10 min; analyses FAILED > 20 % per hour |
| Cost | `mizan.quota.global.used` / `mizan.quota.global.cap` (gauges, mizan-api); tokens in `mizan.analysis.tokens` (dashboard) | > 80 % of daily global cap |
| Uptime | GitHub Actions cron hitting `/api/actuator/health` every 30 min | 2 consecutive failures → issue opened |
Dashboard and alert rules are code: `docker/grafana/` (mounted into otel-lgtm; see `skills/devops-devsecops/references/observability.md`). In the cluster, alerts notify by email (contact point `deploy/base/grafana/contact-point.yaml`, same SMTP as Keycloak); locally they show under Alerting only.

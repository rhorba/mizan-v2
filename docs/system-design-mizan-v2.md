# System Design: Mizan v2
**PRD Reference**: docs/prd-mizan-v2.md
**Version**: 1.0 | **Date**: 2026-10-22 | **Author**: System Designer

## 1. Non-Functional Requirements
| Attribute | Target | Notes |
|---|---|---|
| Availability | Best effort (~99 %) | Single OCI node; no SLA |
| Latency (p95) | API ≤ 300 ms; upload ≤ 2 s; analysis ≤ 45 s | Analysis is bounded by Claude |
| Throughput | ≤ 5 RPS API; ≤ 200 analyses/day | Global cap enforces the upper bound |
| Data Volume | < 50 MB/day | Text only, no PDFs |
| Retention | Contracts until user deletes; Kafka 24 h; traces 3 d; logs 7 d | |
| Recovery (RTO) | 60 min | Re-run Terraform + Argo CD sync + restore dump |
| Recovery (RPO) | 24 h | Nightly `pg_dump` to OCI Object Storage |

## 2. Component Topology
```
[Browser: Angular 22 SPA]
        │ HTTPS (Let's Encrypt via cert-manager)
        ▼
[Traefik ingress (k3s built-in)]
   ├── /            → [mizan-web]   nginx serving the SPA
   ├── /api/**      → [mizan-api]   Spring Boot 4 modular monolith
   └── /auth/**     → [Keycloak 26] OIDC provider (own DB schema)
                          │
[mizan-api] ──JDBC──> [PostgreSQL 18]  (schemas: mizan, keycloak)
     │  Spring Modulith event publication registry (= transactional outbox)
     ▼
[Kafka 4 (KRaft, single broker)]
   topic contract.analysis-requested ──> [mizan-analyzer] ──HTTPS──> [Anthropic API]
   topic contract.analysis-completed <── [mizan-analyzer]
   topic contract.analysis-failed    <── [mizan-analyzer]
     │
[mizan-api] consumes completed/failed → updates contract → pushes SSE to browser

[All JVMs + nginx] ──OTLP──> [grafana/otel-lgtm: Tempo · Prometheus · Loki · Grafana]
[Argo CD] ── pulls k8s manifests from GitHub ──> applies to the cluster
```

## 3. Integration Patterns
| Integration | Pattern | Reason |
|---|---|---|
| Browser → API | REST + JSON, OIDC bearer token | Standard, cacheable, easy to test |
| API → browser status | Server-Sent Events (fallback: poll every 3 s) | One-way updates, no WebSocket infra |
| API → analyzer | Kafka event via outbox | Survives restarts; decouples the slow Claude call from the request |
| Analyzer → API | Kafka event | Same; analyzer has no database |
| Analyzer → Anthropic | Spring AI ChatClient, HTTPS | Structured output, retries, observability built in |
| API → Keycloak | JWT validation via JWKS | API never sees passwords |

## 4. Scalability Strategy
- Scaling approach: vertical on one node. The analyzer is stateless and can scale out by Kafka partitions (topic created with 3 partitions, 1 replica) if ever needed.
- Cache strategy: none (YAGNI).
- Queue strategy: Kafka, because analysis is slow (10–40 s) and must not be lost.

## 5. System Design Decision Records
### SDR-1: Two Spring Boot apps, not six services
- **NFR Driver**: delivery speed, 24 GB RAM budget, part-time maintenance.
- **Decision**: `mizan-api` (modular monolith: identity-facing, contracts, quota, admin modules) + `mizan-analyzer` (stateless Kafka worker). Auth delegated to Keycloak; routing to Traefik — no Spring Cloud Gateway.
- **Alternatives**: v1's 6 services (gateway, auth, user, contracts, ai, common) — duplicated security config and deploy cost for no traffic benefit.
- **Re-evaluate when**: a module needs independent scaling or a separate team.

### SDR-2: Kafka for the analysis hand-off
- **NFR Driver**: NFR-4 (no lost requests), latency isolation.
- **Decision**: Kafka 4 single broker, KRaft, 3 partitions keyed by contract id, 24 h retention.
- **Alternatives**: synchronous REST (v1 — request blocked 30 s, lost on crash); RabbitMQ (fine, but Kafka is what the target missions ask for); DB polling queue (no).
- **Re-evaluate when**: never needs more than one broker at this scale.

### SDR-3: Single node k3s on Oracle Cloud Always Free
- **NFR Driver**: zero monthly cost, real Kubernetes.
- **Decision**: one VM.Standard.A1.Flex (4 OCPU / 24 GB), Ubuntu 24.04 arm64, k3s.
- **Alternatives**: AWS EKS (~100 USD/month); Railway (no Kubernetes/Terraform).
- **Re-evaluate when**: real users depend on uptime → managed Postgres + 2 nodes.

### SDR-4: Grafana LGTM all-in-one for observability
- **NFR Driver**: NFR-6 with a small RAM budget.
- **Decision**: `grafana/otel-lgtm` single pod (OTLP in; Tempo, Prometheus, Loki, Grafana out), Grafana on `/grafana` behind basic auth.
- **Alternatives**: separate Tempo/Loki/Prometheus Helm charts (heavier); Grafana Cloud free (data leaves the cluster, fine too — kept as fallback).

### Memory budget (24 GB)
| Component | Request / limit |
|---|---|
| mizan-api (JVM, -Xmx768m) | 1 / 1.5 GB |
| mizan-analyzer (-Xmx512m) | 0.75 / 1 GB |
| Keycloak | 1 / 1.5 GB |
| Kafka (-Xmx1g) | 1.5 / 2 GB |
| PostgreSQL | 1 / 2 GB |
| otel-lgtm | 2 / 3 GB |
| Argo CD | 1 / 1.5 GB |
| Traefik, cert-manager, mizan-web, k3s system | ~1.5 GB |
| **Total limits** | **~14 GB** — headroom for builds and spikes |

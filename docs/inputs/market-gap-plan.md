# Mizan.ma v2 — modern-stack plan (Sprints 5–9)

Status: **PLAN — awaiting user confirmation** (2026-10-22)

## Why

Market scan of on-site/hybrid Java offers, Oct 2026 (Free-Work Tech Lead + Java/Spring missions, ALTEN /
Capgemini / Sofrecom / Devoteam Morocco) against the CV and repos. Gaps: Spring Boot 4, Kafka with Java,
AWS-style cloud + Terraform, OpenTelemetry tracing, AI in a Java service, Sonar gate — and no Java app
of the owner's is live anywhere. Mizan.ma v2 closes all of them in one app with a public preprod URL.

Decisions taken (2026-10-22): base app = mizan-ma; hosting = Oracle Cloud Always Free (A1.Flex ARM,
4 OCPU / 24 GB) running k3s, provisioned with Terraform.

## Current state

- Spring Boot 3.3.4, Spring Cloud 2023.0.3, Java 21; Angular 22 (already current)
- 6 Maven modules: gateway, auth, user, contracts, ai-analysis, mizan-common
- contracts-service calls ai-analysis-service **synchronously** over REST (`AiAnalysisClient`)
- ai-analysis-service talks to Anthropic through a hand-written `RestClient` (`AnthropicClient`)
- k8s/staging manifests exist (kind only), CI 11 jobs green, nothing deployed

## Sprints

| # | Sprint | Scope | Done when |
|---|---|---|---|
| 5 | Platform upgrade | Java 25, Spring Boot 4.0.x, Spring Cloud 2025.1, springdoc/Testcontainers/jjwt bumps, CI on JDK 25 | `./mvnw verify` green, coverage ≥ 80 %, CI green |
| 6 | Kafka (async analysis) | Upload stores contract + **outbox row in one transaction** → relay publishes `contract.analysis-requested` → ai-analysis consumes, publishes `contract.analysis-completed` / `-failed` → contracts updates status; retries + dead-letter topic; idempotent consumer; frontend polls status. Single-node Kafka 4 (KRaft) in compose and k8s | Testcontainers Kafka integration tests green; sync REST path removed |
| 7 | Spring AI | Replace `AnthropicClient` with Spring AI `ChatClient` + structured output into a Java record; per-user daily analysis cap (protects the API bill on a public demo) | Same contract test suite passes on the new client |
| 8 | Observability + quality | Micrometer Tracing + OTLP: one trace gateway → contracts → Kafka → ai-analysis; Prometheus + Grafana + Tempo in the cluster; SonarCloud quality gate in CI | A single upload shows as one trace in Grafana; Sonar gate green |
| 9 | Preprod on OCI | Terraform (VCN, subnet, security list, A1.Flex instance, cloud-init k3s); multi-arch (arm64) images on GHCR; Kustomize `preprod` overlay from k8s/staging; Traefik + cert-manager (Let's Encrypt); OCI Object Storage via S3 API replaces R2; GitHub Actions deploy job | Public HTTPS URL, smoke test + Playwright recording `.recordings/v2-<date>.webm` |

After Sprint 9: README "Architecture" + case study; CV / LinkedIn / site proof points updated (separate task).

## Env / accounts needed (rule 10)

| Item | Needed by | Notes |
|---|---|---|
| Oracle Cloud account (Always Free) | Sprint 9 | Tenancy OCID, user OCID, API key fingerprint + private key path, region, compartment OCID |
| ANTHROPIC_API_KEY | Sprint 7 (real calls) | Never provided so far; tests run on mocks. Paid usage, capped per user |
| Host name | Sprint 9 | Own (sub)domain, or free `<ip>.sslip.io` |
| SonarCloud | Sprint 8 | Sign in with GitHub, create SONAR_TOKEN repo secret |
| SMTP (Gmail App Password) | Sprint 9 | Deferred since 2026-07-17; public sign-up needs real verification email |

## Risks

- A1.Flex capacity is often "out of host capacity" in popular regions → retry loop or a smaller shape.
- Spring Boot 4 removes deprecated APIs (Jackson 3, Security 7 DSL) → Sprint 5 may need more than version bumps.
- Kafka + 5 services + observability on 24 GB: fine, but JVM heaps must be set explicitly.

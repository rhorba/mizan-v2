# Decision Log
<!-- Tracks architecture decisions, approach selections, tool choices -->
<!-- Format: ### [YYYY-MM-DD HH:MM] ARCHITECTURE/APPROACH/TOOL — Title -->

### [2026-10-22] APPROACH — Mizan v2 as a fresh repo built with CTS
Market scan of on-site/hybrid Java offers (Free-Work Tech Lead + Java/Spring missions; ALTEN, Capgemini, Sofrecom, Devoteam Morocco) showed gaps: Spring Boot 4, Kafka with Java, cloud + Terraform, OpenTelemetry, AI in a Java service, Sonar gate, and no live Java app. User chose: same product as mizan-ma (AI contract analysis), rebuilt from scratch in a new PUBLIC repo rhorba/mizan-v2, CTS framework, hosting on Oracle Cloud Always Free + k3s via Terraform. mizan-ma stays untouched as v1.

### [2026-10-22] ARCHITECTURE — v2 foundation choices (pending approval)
2 Spring Boot 4 apps (modular monolith API + stateless analyzer) instead of v1's 6 services; Keycloak replaces hand-written auth; Spring Modulith event externalization as outbox; PDFs not stored (text only); SSE for status; grafana/otel-lgtm; Argo CD pull deploy (cluster API never exposed); BUSINESS role dropped.

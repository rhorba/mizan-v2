# Mizan v2 — AI contract analysis for Moroccan freelancers and SMEs

Upload a contract PDF and get a clause-by-clause summary in French, Arabic or Darija, with risky terms
flagged and corrections suggested. Document analysis only, not legal advice.

Full rebuild of [mizan-ma](https://github.com/rhorba/mizan-ma) on a 2026 enterprise Java stack:
Java 25 · Spring Boot 4 · Angular 22 · Apache Kafka (transactional outbox) · Spring AI ·
OpenTelemetry · PostgreSQL · Kubernetes (k3s) on Oracle Cloud, provisioned with Terraform.

**Status:** foundation docs in progress — see `docs/`.

Built with [Claude Team Skills (CTS)](https://github.com/rhorba/CTS).

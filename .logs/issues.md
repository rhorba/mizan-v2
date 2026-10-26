# Issue Log
<!-- Tracks bugs, errors, blockers with status -->
<!-- Format: ### [YYYY-MM-DD HH:MM] BUG/BLOCKER/ERROR — Title -->


### [2026-10-22] ISSUE (resolved) — victools jsonschema convergence
Enforcer dependencyConvergence: Spring AI 2.0.2 → victools 5.0.0, anthropic-java-core 2.72.0 → 4.38.0 (runtime). Fix: import jsonschema-generator-bom 5.0.0 in parent. Risk: Anthropic SDK's own schema helpers run on 5.x — we only use Spring AI's path. Re-check on Spring AI upgrades.

### [2026-10-22] ISSUE (resolved) — vite 8.3.0 advisories (dev-only)
@angular/build 22.2.4 pins vite 8.3.0 (GHSA-9jrq-w75r-8gcw high + launch-editor/fs.allow advisories, dev server only). Fix: npm overrides vite 8.3.5 (patch). Audit 0. Remove override when @angular/build bumps vite.

### [2026-10-22] ISSUE (resolved) — line endings
Global core.autocrlf=true rewrote working files as CRLF after .gitattributes (eol=lf) was added; Spotless failed. Fix: git config --local core.autocrlf false + convert working files. No content changes.

### [2026-10-22] ISSUE (resolved) — lz4-java CVE-2026-106451 (HIGH) in analyzer image
kafka-clients 4.2.2 → lz4-java 1.11.2. Pinned 1.11.4 in parent dependencyManagement. Remove when Boot/Kafka bump it.

### [2026-10-23] ISSUE (resolved) — Keycloak healthy before realm import
/health/ready reported UP while BootstrapFilter still returned 503 and realm 'mizan' was not imported yet. Healthcheck switched to the realm discovery endpoint. Affects stack-check, CI stack job, E2E.

### [2026-10-23] ISSUE (workaround) — OneDrive locks target/ during mvn clean
Repo lives in a OneDrive-synced folder; deleting freshly emptied target/ dirs fails. Workaround: rm -rf */target then ./mvnw verify. Long-term: move repo out of OneDrive or exclude target/ and node_modules/ from sync.

### [2026-10-23] BLOCKER — CI stack job: Keycloak image ca-certificates CVE-2026-114406 (CRITICAL)
Fixed package (2026.2.92_v9.0.319-90.3.el9_8) exists only in RHEL subscription repos; UBI repos still ship 2025.2.80; no Keycloak > 26.8.1 yet. dnf --installroot patch stage tried → installs the same vulnerable version → reverted. Awaiting user decision (accept with expiry vs alternatives).
Second failure (e2e: ng serve TS2304 'vi' in src/testing) fixed: tsconfig.app.json excludes src/testing/**.

### [2026-10-26] ISSUE (resolved) — Modulith 2.1.2 enable-json uses ByteArrayJacksonJsonMessageConverter which always writes __TypeId__ headers, ignoring spring.json.add.type.headers. Fixed by enable-json=false + JacksonJsonSerializer.

### [2026-10-26] ISSUE (resolved) — Live run used claude-haiku-4-5 instead of Opus
Per-call AnthropicChatOptions (outputSchema) did not inherit spring.ai chat defaults → Spring AI default model. Caught only by the live test. Fix: model + max tokens set on every request from mizan.analyzer.*; unit test asserts prompt options carry the model.

### [2026-10-26] ISSUE (resolved) — spring-kafka 4 default DLT name is "<topic>-dlt"
Architecture §5 names contract.analysis-requested.DLT; destination resolver set explicitly.

### [2026-10-26] ISSUE (fixed) — compose mizan-api had no KAFKA_BOOTSTRAP_SERVERS
In the apps profile the API would default to localhost:9092 inside its container and never reach Kafka. Added kafka:29092, budget env passthrough, and MIZAN_AI_MODEL/_MAX_TOKENS for the analyzer. Found while preparing the E2E recording.

### [2026-10-26] ISSUE (fixed) — API crashed in compose: Modulith tried CREATE SCHEMA as mizan_app
spring.modulith.events.jdbc.schema=mizan triggers Modulith's schema initializer (CREATE SCHEMA IF NOT EXISTS) → "permission denied" for the least-privilege user. Tests ran as superuser so never saw it. Fix: remove the property, set spring.datasource.hikari.schema=mizan. Found by the E2E stack.

### [2026-10-26] ISSUE (fixed) — Analyzer auto-created a 1-partition request topic when it started first
Consumer auto-creation made contract.analysis-requested with 1 partition; after the API expanded it to 3, the analyzer only saw partition 0 until a metadata refresh (≤5 min). Fix: analyzer declares the request topic too; both consumers set allow.auto.create.topics=false.

### [2026-10-26] ISSUE (fixed) — CI "stack" red: CVE-2026-111497 (HIGH) in brotli-libs of the pinned Temurin JRE Alpine image
Newly published, unrelated to Sprint 3 code (main would fail too). Alpine has 1.2.1-r0: runtime stage now runs `apk upgrade --no-cache brotli-libs` until the Temurin digest is bumped. Local Trivy (CI image + flags): api 0, analyzer 0, keycloak 0 (with its ignorefile).

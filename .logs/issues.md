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

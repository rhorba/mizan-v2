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

### [2026-10-26] ISSUE (fixed) — "v1 messages read as 0" was false: Jackson 3 fails on missing primitive int. Failed tokens are Integer (null → 0) so a not-yet-redeployed analyzer still applies.

### [2026-10-26] ISSUE (fixed) — Web worker heap OOM hid a red run ("103 passed", exit 1)
vi fake timers + advanceTimersByTime looped Angular's test scheduler → worker crash (exit 134). Fix: injectable POLL_MS, real timers at 20 ms. Also found: switchMap polling cancels in-flight requests (→ exhaustMap); resource.value() throws in error state (→ hasValue guard); a test-provider insert silently missed (edit scripts now assert matches).

### [2026-10-26] ISSUE (fixed) — result fetched twice after completion: httpResource re-ran on a new analysis object with the same URL; now keyed on a computed URL string. Also: @angular/animations is not installed (Angular 22 Material needs none).

### [2026-10-27] ISSUE — JDK tzdata stale for Africa/Casablanca (found by StatsTests day-boundary case)
IANA tzdata 2026c (2026-07-08) moved Morocco to permanent UTC+00 from 2026-09-20. Local JDK 25.0.3 has tzdb 2026a, runtime image JRE 25.0.4.1 has 2026b: both still apply UTC+1. PostgreSQL 18.6-alpine uses system tzdata 2026e (correct). Impact: DailyUsage "today"/dayOf (Story 5.1 caps, refunds, tokens) start the day at 23:00 UTC instead of 00:00 — 1 h early, in every environment; stats (SQL AT TIME ZONE) disagree with Java for that hour. Status: OPEN, fix approach awaiting owner choice.

### [2026-10-27] ISSUE CLOSED — JDK tzdata drift: DailyUsage.today()/dayOf() and the stats window now use PostgreSQL (tzdata 2026e); DayBoundaryTests pins 2026-10-26T23:30Z → 2026-10-26 and 2026-06-10T23:30Z → 2026-06-11. Tests take "today" from DailyUsage, not LocalDate.now. Follow-up: drop the comment once the JDK ships tzdata ≥ 2026c.

### [2026-10-27] ISSUE — 10 analyses stuck in PENDING on the local stack (requested 2026-10-23)
Seen on /admin while checking B5. Likely Sprint 2 E2E data from before the analyzer existed (no publication to resubmit), but unverified. Status: OPEN — check in B6 VERIFY (event_publication rows? analyzer logs?) before calling it dev-data only.

### [2026-10-27] ISSUE CLOSED — stuck PENDING ×10: created 2026-10-23 13:58 within 4 s by one E2E user (Sprint 2 paging test), before V2 outbox existed (installed 2026-10-26) → never requested. Dev data, not a pipeline bug; event_publication empty. Local rows left as is.

### [2026-10-27] ISSUE — local E2E uploads billed real Claude calls
With ANTHROPIC_API_KEY in .env the analyzer analyzes every E2E upload, not only E2E_CLAUDE tests (spec comments claimed otherwise). Today: 9 analyses, 10 367 in / 12 651 out tokens ≈ $0.29 (cap $3/month). Mitigation: playwright.config documents stopping mizan-analyzer for non-Claude runs (CI has no key). Status: MITIGATED (doc + practice); a stack profile without the analyzer could make it automatic — deferred (YAGNI until it bites again).

### [2026-10-27] ISSUE — stale E2E assertions from B1/B2 caught by the real-stack run
sign-up spec still expected the Sprint 1 cap (3) in UI and API; upload spec read a status chip the contract page no longer has (stepper since B2) and asserted PENDING where the live page moves on. Unit suites could not see these; nothing was pushed, so CI never ran them. Fixed.

### [2026-10-28] ISSUE (resolved) — OneDrive resurrected 4 files deleted in 4b2a088
contracts/BudgetProperties, SpendBudget, TokenSpend, SpendBudgetTests reappeared untracked (mtime 2026-10-26 13:38); SpendBudget calls the removed AnalysisRepository.spendSince → would break compile. Moved to session scratchpad (recoverable from 4b2a088^). Watch: re-check `git status` for untracked files at each session start while the repo lives in OneDrive.

### [2026-10-28] ISSUE (fixed) — Browser tracing dead in the real app; upload progress bar never moved
Angular 22 HttpClient defaults to FetchBackend (XHR needs withXhr()); app.config assumed the pre-22 default ("no withFetch = XHR"). Effect: XHR instrumentation (B2) never saw a request → no traceparent, no mizan-web spans; reportProgress upload events never emitted (Story 2.2 bar stuck since skeleton). B2 unit tests passed because they drive XMLHttpRequest directly and provideHttpClientTesting replaces the backend. Fix: provideHttpClient(withXhr(), withInterceptorsFromDi()); app.config.spec asserts HttpBackend is HttpXhrBackend (red → green). Verified on stack: traceparent on API calls, OTLP 200, mizan-web span parent of mizan-api span.

### [2026-10-28] ISSUE — 2 unapproved real Opus calls ($0.053)
Before starting mizan-analyzer for the 1 approved proof call, tried to skip 2 leftover 10-27 E2E requests by resetting the group offset; `docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh` (direct exec) failed with "runtime exec failed" and the chain continued (`&&` only guarded the awk pipe). Analyzer billed both: 2262 in / 2205 out ≈ $0.053. Lesson: run Kafka CLI via `sh -c` in that image, and verify the reset (describe group) BEFORE starting the analyzer. Live Claude spend ≈ $0.49 of $3 (incl. approved proof call 1086/728 ≈ $0.019).

### [2026-10-28] ISSUE (fixed) — First CI run of Sprint 5 branch red (PR #14)
1) test-backend: TracingTests.applyingAResultContinuesTheAnalyzersTrace timed out — several cached test contexts consume as group "mizan-api" on the shared Kafka container, so the result could land in a context whose span exporter the test never sees (passed locally by luck of partition ownership). Fix: own consumer group for that context + match the consumer span by the expected trace id (the new group replays other tests' results). 2) stack: Trivy image scan — snappy-java 1.1.10.7 via kafka-clients, CVE-2026-108106 + CVE-2026-93451 (HIGH, fixed 1.1.10.9) → pinned 1.1.10.9 in root dependencyManagement (same pattern as lz4-java). Local Trivy image scans FATAL on this Windows Docker (layer analysis) — CI remains the judge. sonar job had been skipped (needs test-backend).

### [2026-10-28] ISSUE (fixed, not pushed) — Gitleaks false positive on sonar.projectKey
generic-api-key fired on `sonar.projectKey=rhorba_mizan-v2` (c73a04d, sonar-project.properties:4) — a public project id. Fingerprint added to new .gitleaksignore with reason; local full-history scan: no leaks found.

### [2026-10-28] ISSUE (accepted, time-boxed to 2026-11-30) — guava CVE-2026-102554 (HIGH) in Keycloak image
quay.io/keycloak/keycloak:26.8.1 (newest, 2026-10-13) bundles guava 33.6.0-jre; fixed in 33.7.2. Only remaining red in CI `stack` (Trivy images); mizan-api / mizan-analyzer images clean after the snappy-java pin. Options: A) time-boxed trivyignore entry to 2026-11-30 (recommended), B) replace the jar in keycloak.Dockerfile, C) wait for upstream. Owner ended the session before choosing.

### [2026-10-28] ISSUE (fixed) — Gitleaks flagged my own explanation of the false positive
The comment in .gitleaksignore and the issues.md note quoted the Sonar project-key line verbatim → 2 new generic-api-key findings (commit 4d281ce). Fingerprints added. Lesson: never quote a flagged line in prose; scan COMMITTED history locally (gitleaks git) before pushing, not the working tree.

### [2026-10-28] ISSUE (fixed) — Semgrep detect-non-literal-regexp in browser-tracing.ts (B2)
Hidden until now: the security job stopped at Gitleaks before Semgrep ran. `new RegExp('^' + escaped url)` built from build-time environment.ts (not user input), all metacharacters escaped → literal prefix, no backtracking/ReDoS. OTel accepts only exact strings or RegExps. Suppressed with reason + `nosemgrep` (repo convention, as ClamdClient). Local run of CI's exact Semgrep command: 491 rules, 280 files, 0 findings. Lesson: when a job fails at step N, the steps after N are unverified — run the whole job locally before calling it fixed.

### [2026-10-28] ISSUE (fixed) — main quality gate FAILED after PR #14 merge
First analysis of main judges all code: java:S5693 ×2 (MAJOR "vulnerability") on mizan-api application.yml lines 12–13 (multipart 10MB / 11MB > Sonar's 8 MB default) → security rating C. PR analysis passed because those lines (Sprint 2) were not in the PR diff. Owner chose a justified, file-scoped ignore in sonar-project.properties (10 MB is the Story 2.1 limit; auth + rate limit + ClamAV + PDF checks). Also: PR #14 was merged at b2e321c before the B6 push → B6 goes to main via chore/sprint-5-close.

### [2026-10-29] CI red — Trivy KSV-0014 on B2 manifests (run 40861687011)
security job: readOnlyRootFilesystem false on kafka, keycloak, clamav, otel-lgtm (HIGH). Fixed, not ignored: each image run with --read-only to find its write paths (Kafka /opt/kafka/config via init-container copy + /opt/kafka/logs; ClamAV /run, /var/lock, /var/log/clamav, /tmp; otel-lgtm /data, /var/tempo, /tmp; Keycloak /tmp, data/tmp, data/transaction-logs — the last also cut start-up 75 s → 39 s). All four verified running read-only; local Trivy misconfig exit 0.

### [2026-10-29] main CI red — run 40864495168 @ d45802d (owner merged PR #16 = B1+B2)
publish(mizan-web) Trivy: brotli-libs CVE-2026-111497 CRITICAL, curl/libcurl CVE-2026-92392 HIGH from the nginx-unprivileged 1.30.6 base; fail-fast cancelled the other images, promote skipped → nothing deployed (gate worked). Fix: upgrade brotli-libs, remove curl (unused at runtime); verified Trivy 0, uid 101, read-only, /healthz, arm64 build. Root cause of reaching main: PR CI never scanned the web image → stack job now builds and scans mizan-web on every PR.

### [2026-10-30] ISSUE — first preprod sync: 4 manifest bugs (fixed on chore/sprint-6-b3-live)
1. bootstrap.sh waited for `argocd-server`; the Helm release names it `argo-cd-argocd-server` (also README/argocd.yaml port-forward). Fixed, re-ran bootstrap (idempotent).
2. Kafka init `cp -a` → "preserving times for '/config/.': Operation not permitted" (non-root, emptyDir root) → CrashLoop; api/analyzer crash on unresolvable kafka DNS as a knock-on. Fix: `cp -R`.
3. Let's Encrypt HTTP-01 502: default-deny blocked Traefik → cert-manager solver pods (8089). Fix: acme-http01-solver NetworkPolicy ×3.
4. Kafka/Postgres StatefulSets OutOfSync forever: claim templates lacked apiVersion/kind/volumeMode. Fix: add them.
Verified: kustomize build (55 objects) + server-side dry-run on the node. Lesson: the overlay was never applied to a real cluster before B3 — add a server-side dry-run against a throwaway k3s (kind/k3d) to CI.

### [2026-10-30] ISSUE — main RED after 7 Dependabot merges (#19–#25)
Rule 11 stop. (a) #21 logback appender 2.32.0-alpha → semconv 1.44.0 vs 1.41.1 from Spring Boot 4.1.2 → enforcer DependencyConvergence fails (lint, test-backend, stack, e2e). Pom/ADR-8 already pinned 2.28.1-alpha. (b) #20/#25 @opentelemetry/exporter-trace-otlp-http + instrumentation-xml-http-request 0.300.0 = OTel JS 3.0 line, needs sdk-trace 3 + newer api → browser-tracing.spec "Named export 'logs' not found".
Fix (chore/sprint-6-b3-live, with the deploy fixes): back to 2.28.1-alpha and 0.223.0; dependabot.yml ignores the appender (moves with Spring Boot) and groups @opentelemetry/* npm. Verified: mvn validate OK, web lint/build OK, npm test 133/133, 97.9 % stmts. OTel JS 3.0 upgrade = future story if wanted.
### [2026-10-30] ISSUE — axe-core 4.14 (#22) flags label-content-name-mismatch on .lang-switch
Visible "العربية" vs aria-label "Afficher en arabe" (WCAG 2.5.3, serious). Real finding, fixed not suppressed: no aria-label; name = visible text (lang=ar/fr) + visually-hidden explanation in page language. Unit + E2E selectors updated.

### [2026-10-30] ISSUE — main RED again: #28 (grouped OTel JS bump) merged with red CI
Partial 3.0 line (api 1.10, resources 3.0, 0.300 packages; sdk-trace-web 3.0 still in cooldown). Also #27 and #28 merges cancelled the #26 publish/promote runs (R-CI-CANCEL). Fix: revert #28 + dependabot ignore OTel JS >=3 / >=0.300 (fix/revert-otel-js-3); owner OK'd branch protection on main (required checks).

### [2026-10-30] ISSUE — preprod after promote 46db710: two rollout snags
1. kafka-0 kept the old `cp -a` pod: a StatefulSet RollingUpdate never replaces a pod that never became Ready. Deleted kafka-0 by hand (no data yet) → Ready with `cp -R`. api/analyzer recovered on their own.
2. New Keycloak pod never Ready: Keycloak 26.8 treats `cache` as runtime, so KC_CACHE=local baked at build was dropped → default ispn + JDBC_PING → JOIN to old pod over JGroups blocked by NetworkPolicy → cluster health DOWN. Fix: KC_CACHE=local on the Deployment, dead build line removed (fix/keycloak-local-cache). Old pod keeps serving meanwhile.
Certificates: all 3 Ready (ACME policy fix works). Argo CD: Synced (claim-template fix works).

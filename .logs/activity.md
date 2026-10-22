# Activity Log
<!-- Tracks completed tasks, milestones, and deliverables -->
<!-- Format: ### [YYYY-MM-DD HH:MM] COMPLETED/MILESTONE — Title -->


### [2026-10-22] MILESTONE — Repo created and bootstrap pushed
PUBLIC github.com/rhorba/mizan-v2 created on user request; commit 9967d0b pushed to main (secret scan clean before push).

### [2026-10-22] COMPLETED — Foundation docs drafted (10/10), awaiting user approval
PRD, system design, architecture (ADR-1..7), security, database, UX, UI, test strategy, DevOps, stories (7 epics, 21 stories, 6 sprints to 2026-12-04). Not yet committed — rule 13: push after approval.

### [2026-10-22] MILESTONE — Foundation docs approved and pushed
User approved all 10 docs. PUSH: main 9967d0b..6cf4951 → https://github.com/rhorba/mizan-v2 (secret scan clean). No code yet — Sprint 1 (Stories 1.1–1.4, target 2026-10-30) starts next session from the repo root.

### [2026-10-22] PHASE UNDERSTAND — Story 1.1
Scope: pin Java 25 / Boot 4.0.x / Modulith / Spring AI / Spring Kafka / Testcontainers / Angular 22; Maven multi-module (mizan-api, mizan-analyzer) + Angular mizan-web; one smoke test each; ADR-8. No new env vars for 1.1.

### [2026-10-22] HANDOFF Orchestrator → Scrum Master
Context: user chose 🔴 for Story 1.1. Need: batch plan. Constraints: Maven wrapper (no global mvn), JDK 25, Node 22.23, fallbacks per Arch §8.

### [2026-10-22] PHASE PLAN — Story 1.1 (awaiting approval)
B1 spike+parent POM (1.1a-d) · B2 api/analyzer modules + smoke/arch tests (2a-d) · B3 Angular 22 web (3a-c) · B4 build rules + ADR-8 (4a-d). Branch feature/s1-skeleton.

### [2026-10-22] HANDOFF Scrum Master → Tech Lead
Context: plan approved (B1–B4). Need: version spike + parent POM. Constraints: 🔴 build rules, fallbacks per Arch §8.

### [2026-10-22] DONE B1 — spike + parent POM
1a latest GA: Boot 4.1.2 (Framework 7.0.10, Security 7.1.2, Kafka client 4.2.2, Spring Kafka 4.1.2, Testcontainers 2.0.5, JUnit 6.0.3), Modulith 2.1.2, Spring AI 2.0.2, ArchUnit 1.5.1, Angular 22.2.3 (node ^22.22.3 OK), Maven 3.9.16.
1b spike GREEN: Boot 4.1.2 + Modulith JDBC registry + @Externalized + spring-modulith-events-kafka + Spring AI Anthropic ChatClient.entity() — context loads, ApplicationModules.verify() passes. No fallback needed.
1c mvnw 3.3.4 (only-script, sha256 pinned, sha512 verified vs Central) + parent pom.xml (BOMs, Enforcer, Spotless/GJF, Checkstyle, JaCoCo 80% line gate). 1d config/checkstyle/checkstyle.xml + .editorconfig.

### [2026-10-22] HANDOFF Tech Lead → Backend Dev
Context: versions pinned (Boot 4.1.2, Modulith 2.1.2, Spring AI 2.0.2), parent POM ready. Need: mizan-api (modules contracts/quota/admin/shared), mizan-analyzer, smoke + Modulith verify + ArchUnit tests, coverage-report aggregate. Constraints: package root ma.mizan; no persistence/security yet (Stories 1.4, 2.1, 3.1).

### [2026-10-22] DONE B2 — backend modules
mizan-api (webmvc, actuator, modulith core; modules contracts/quota/admin + OPEN shared), mizan-analyzer (kafka, spring-ai anthropic, actuator), coverage-report (report-aggregate). Tests: api 7 (health smoke via MockMvcTester, Modulith verify + PlantUML docs, 4 ArchUnit rules), analyzer 3 (context w/ KafkaTemplate+ChatModel, 2 ArchUnit rules). ./mvnw verify GREEN.

### [2026-10-22] HANDOFF Backend Dev → Frontend Dev
Context: backend skeleton green (10 tests). Need: mizan-web Angular 22.2.3 (standalone, zoneless, strict), 1 smoke test, coverage, ESLint, npm test/lint scripts. Constraints: Node 22.23.3; no Material/Transloco/OIDC yet (Stories 1.4/2.2) — YAGNI.

### [2026-10-22] DONE B3 — mizan-web
Angular 22.2.3 (standalone, zoneless, strict, scss, Vitest 5, no SSR). Welcome page replaced by shell (h1 + router-outlet). angular-eslint 22, @vitest/coverage-v8, thresholds 80% all metrics in angular.json. Scripts: test (coverage, CI mode), test:watch, lint, format, format:check. 1 test pass, 100% coverage, lint clean, prod build OK.

### [2026-10-22] DONE B4 — ADR-8 + README
4a Enforcer rules active (proved by real convergence catch in B2). 4c ADR-8 added to docs/architecture-mizan-v2.md (v1.1, stack row → Boot 4.1.x). 4d README Build section.

### [2026-10-22] HANDOFF Frontend Dev / Tech Lead → Tester
Context: B1–B4 done. Need: clean full run of backend + web, coverage gate, security check. Constraints: skeleton only — coverage is over near-empty code.

### [2026-10-22] PHASE VERIFY — Story 1.1 PASS
Backend clean verify: api 8 tests, analyzer 4 tests, 0 fail. Web: 1 test, lint clean, prettier clean, prod build OK. Security: Semgrep (p/default, p/java, p/typescript) 0; Gitleaks v8.28.0 no leaks; Trivy 0.67.2 fs (vuln/misconfig/secret, HIGH+CRIT) 0; npm audit 0. Correction during verify: raw backend coverage was 33% (only main() lines, excluded from gate) → added main() tests, removed JaCoCo exclusion → 100%.

### [2026-10-22] HANDOFF Tester → DevOps/Deployment
Context: Story 1.1 verified. Need: commit + push feature/s1-skeleton. Constraints: no CI yet (Story 1.3), so CI monitoring starts with 1.3; no version completion → no video.

### [2026-10-22] PUSH — feature/s1-skeleton @ 17994f8
Story 1.1 shipped to branch. Added .gitattributes (LF, CRLF for *.cmd) + mvnw exec bit. CI: no workflow exists yet (Story 1.3) — nothing to monitor. Video: N/A (not a version completion).
MILESTONE: Story 1.1 DONE.

### [2026-10-22] PHASE UNDERSTAND — Story 1.2 (local stack)
AC: `docker compose up -d` → Postgres 18, Kafka 4, Keycloak 26 (realm imported), otel-lgtm healthy < 2 min. Images: postgres:18.6-alpine, apache/kafka:4.2.2 (= client) / 4.4.0, keycloak 26.8.1, grafana/otel-lgtm 0.37.0. Host port 5432 already taken (madrasti-db) → host ports must be configurable. DB layout: db `mizan`, schemas `mizan` (mizan_app) + `keycloak` (keycloak). Env vars needed: POSTGRES_PASSWORD, MIZAN_DB_PASSWORD, KEYCLOAK_DB_PASSWORD, KEYCLOAK_ADMIN/_PASSWORD, host ports.

### [2026-10-22] HANDOFF Orchestrator → Scrum Master (Story 1.2)
Context: 🔴 chosen. Need: batch plan. Constraints: port 5432 busy → configurable host ports; apps have no DB/security config yet (Stories 1.4/2.1).

### [2026-10-22] PHASE PLAN — Story 1.2 (awaiting approval)
B1 compose infra + DB init + .env.example · B2 realm JSON · B3 Dockerfiles + apps profile · B4 health proof script, Dockerfile scan, README.

### [2026-10-22] HANDOFF Scrum Master → DevOps/DevSecOps
Context: Story 1.2 plan approved, env defaults accepted. Need: B1–B4. Constraints: images pinned per UNDERSTAND; non-root containers; .env never committed.

### [2026-10-22] DONE B1 (Story 1.2) — compose infra
compose.yaml (postgres 18.6, kafka 4.2.2 KRaft, keycloak 26.8.1 start-dev --import-realm on schema keycloak, otel-lgtm 0.37.0, mailpit profile mail, apps profile), db/init/01-schemas.sh, .env.example. Host ports bound to 127.0.0.1. All 4 healthy (cold start incl. pulls 7m42s). Verified: schemas mizan (mizan_app) + keycloak (keycloak, 101 tables), public empty, mizan_app denied on keycloak schema.

### [2026-10-22] DONE B2 (Story 1.2) — realm-mizan.json
Imported on start (--import-realm, IGNORE_EXISTING). Verified via admin API: registration+verifyEmail, bruteForce (5 failures, 60s→15 min backoff), password length(10)+notUsername+notEmail, tokens 300s / idle 1800s / max 28800s, locales fr(default)/ar/en, SMTP mailpit:1025; mizan-web public, PKCE S256, no implicit/ROPC, redirect localhost:4200, audience mapper mizan-api; default role → user.

### [2026-10-22] DONE B3 (Story 1.2) — app images + apps profile
docker/spring-app.Dockerfile (one file, ARG MODULE; Temurin 25.0.4.1 JDK/JRE alpine pinned by digest, amd64+arm64; BuildKit m2 cache; Boot tools extract --layers; jar renamed app.jar; UID 1001; MaxRAMPercentage=75). Image mizan-api 252MB. .dockerignore excludes .env/.git/target/node_modules. apps profile: api healthy (actuator UP), analyzer running.
Fixes: analyzer exited 0 (no web/listener) → spring.main.keep-alive=true; compose --wait reports exited containers as healthy → stack-check must also assert no exited. CRLF vs Spotless → repo-local core.autocrlf=false (.gitattributes rules), working files normalised to LF; 76 stat-only M cleared, no content change.

### [2026-10-22] DONE B4 (Story 1.2) — proof, scans, docs
scripts/stack-check.sh: first run FAILED (keycloak start-dev re-augments every boot, ~3 min). Fix: docker/keycloak.Dockerfile (kc.sh build: postgres, health, cache=local) + `start --optimized`, KC_HTTP_ENABLED (local). Script `down` now covers all profiles. Results: --fresh infra PASS 75 s; --profile apps PASS 63 s; keycloak 14.9 s. Negative test: stopped analyzer flagged. Issuer http://localhost:8180/realms/mizan identical from host and in-network; JWKS via keycloak:8080.
Trivy config docker/: 1 LOW (no HEALTHCHECK in shared Dockerfile — healthcheck lives in compose, by design). Trivy images (fixable HIGH/CRIT): api 0, analyzer 0 after lz4-java 1.11.2→1.11.4 pin, keycloak 0. README "Run locally".

### [2026-10-22] HANDOFF DevOps → Tester (Story 1.2)
Context: B1–B4 done. Need: full verify (Enforcer after lz4 pin), scans on new files. Constraints: no new app logic → coverage unchanged.

### [2026-10-22] PHASE VERIFY — Story 1.2 PASS
./mvnw clean verify GREEN (api 8, analyzer 4) incl. Enforcer after lz4 pin; web 1 test 100%. stack-check --profile apps PASS 75 s. Semgrep (p/default, p/dockerfile, p/docker-compose; p/bash 404 → dropped) 1 finding → keycloak.Dockerfile explicit USER 1000 → 0. Gitleaks no leaks; .env ignored. Trivy images 0 fixable HIGH/CRIT.

### [2026-10-22] HANDOFF Tester → DevOps/Deployment (Story 1.2)
Need: commit + push. CI still absent (Story 1.3) → no CI to monitor. No video (not a version completion).

### [2026-10-22] PUSH — feature/s1-skeleton @ 6937cb8
MILESTONE: Story 1.2 DONE. CI: none yet (Story 1.3).

### [2026-10-22] PHASE UNDERSTAND — Story 1.3 (CI)
AC: on push to any branch, GitHub Actions runs lint, backend tests, frontend tests, Gitleaks, Semgrep, Trivy; build fails < 80 % coverage. Repo PUBLIC (free Actions minutes, Code Scanning available). Out of scope per stories: sonar (6.2/S5), build-images+SBOM+bump-preprod (7.x), Playwright (no E2E yet). No secrets needed (GITHUB_TOKEN only).

### [2026-10-22] HANDOFF Orchestrator → Scrum Master (Story 1.3)
Context: 🔴 chosen. Need: batch plan. Constraints: public repo, GITHUB_TOKEN only; branch protection = repo setting (confirm at gate).

### [2026-10-22] PHASE PLAN — Story 1.3 (awaiting approval)
B1 ci.yml lint/test jobs · B2 security job + SARIF · B3 stack job + image scan · B4 dependabot, push, CI green loop · B5 branch protection (after green).

### [2026-10-22] HANDOFF Scrum Master → DevOps/DevSecOps (Story 1.3)
Context: plan approved (admin-exemption question open until B5). Need: B1–B4. Constraints: SHA-pinned actions, least privilege.

### [2026-10-22] DONE B1–B3 (Story 1.3) — ci.yml + dependabot
Jobs: lint (mvnw validate = Enforcer+Spotless+Checkstyle; ESLint, Prettier), test-backend (verify, JaCoCo gate, summary+artifact), test-frontend (npm test gate, json-summary → step summary + artifact), security (Gitleaks 8.30.1 full history, Semgrep 1.182.1 SARIF + --error, Trivy 0.76.1 fs SARIF + HIGH/CRIT gate), stack (bake + gha cache, stack-check --profile apps, Trivy image scan ignore-unfixed). Actions SHA-pinned, scanner images digest-pinned, contents:read default, security-events:write only in security job, concurrency cancel. .semgrepignore (skills/docs/.logs). dependabot weekly + 7-day cooldown (Semgrep finding).
Local pre-flight: actionlint 0 (SC2016 suppressed for JS template), Semgrep 0, Trivy fs 0. Trivy fs on pom.xml → Maven Central 429 → skip pom.xml in fs; Java deps covered by image scan.

### [2026-10-22] CI RED — run 39856772509 @ 280e016
lint ✅ test-backend ✅ test-frontend ✅ security ✅ stack ❌: scripts/stack-check.sh Permission denied (exit 126) — exec bit not tracked on Windows. Fix: git update-index --chmod=+x (stack-check.sh, 01-schemas.sh).

### [2026-10-22] CI GREEN — run 39857095772 @ 311dd84
lint ✅ (27 s) test-backend ✅ (36 s) test-frontend ✅ (22 s) security ✅ (66 s) stack ✅ (4 min; stack-check PASS 58 s on ubuntu-24.04, images 0 fixable HIGH/CRIT). Wall time ≈ 4 min.

### [2026-10-22] DONE B5 (Story 1.3) — branch protection on main
User chose: exempt admins. Required checks lint/test-backend/test-frontend/security/stack (strict: branch up to date), PR required (0 approvals — solo dev), stale reviews dismissed, conversation resolution required, no force-push, no deletion, enforce_admins=false.

### [2026-10-22] HANDOFF DevOps → Tester (Story 1.3)
Context: CI green, protection on. Need: prove coverage gate fails builds. Constraints: no code changes kept.

### [2026-10-22] PHASE VERIFY — Story 1.3 PASS
AC: every push runs lint, backend tests, frontend tests, Gitleaks, Semgrep, Trivy → run 39857095772 all green. Gate proof: -Dcoverage.minimum=1.01 → JaCoCo "Coverage checks have not been met" exit 1; Vitest lines threshold 101 → "does not meet global threshold" exit 1 (reverted). Code scanning: Semgrep 0, Trivy 2 LOW (DS-0026 HEALTHCHECK, by design).

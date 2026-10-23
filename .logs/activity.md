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

### [2026-10-22] PUSH + CI GREEN — feature/s1-skeleton @ 9c571b0 (run 39858005980, 5/5 green)
MILESTONE: Story 1.3 DONE. Sprint 1 remaining: Story 1.4 (Keycloak sign-up/login). Video: N/A (no user-facing flows yet).

### [2026-10-22] CI check — run 39858749115 @ 00686ee: exit=0

### [2026-10-23] PHASE UNDERSTAND — Story 1.4 started
Scope: API = OAuth2 resource server (issuer + audience mizan-api, realm roles → authorities, RFC 9457 401/403, CORS localhost:4200), GET /api/v1/me/quota (cap counting = Story 5.1), /api/v1/admin/** hasRole admin. SPA: OIDC code+PKCE (angular-oauth2-oidc 22.0.2), token in memory, landing → login → /contracts quota chip, logout. Keycloak: brand theme, password blacklist + admin TOTP (deferred from 1.2). Sprint 1 ends here with user-facing flow → Playwright video (rule 9). Env: KEYCLOAK_ISSUER_URI, KEYCLOAK_JWK_SET_URI, MIZAN_CORS_ALLOWED_ORIGINS, MIZAN_QUOTA_DAILY_PER_USER (value TBD by user). Libs: testcontainers-keycloak 4.4.0, @angular/material 22.2.4, Transloco 8.4.0, Playwright 1.64.1.

### [2026-10-23] HANDOFF Orchestrator → Scrum Master (Story 1.4)
Context: 🔴, cap 3. Need: batch plan with specialist handoffs (Security, Backend, DBA n/a, UX, UI, Frontend, Test Architect, DevOps). Constraints: L story; Sprint 1 ends here → video + PR into protected main.

### [2026-10-23] PHASE PLAN — Story 1.4 (awaiting approval)
B1 API security + quota · B2 Keycloak hardening (blacklist, admin TOTP) · B3 Keycloak theme · B4 SPA shell/i18n/landing · B5 SPA auth + contracts/quota · B6 Playwright E2E + video + CI e2e job · B7 docs/env. Then VERIFY, SHIP (PR → main, sprint close).

### [2026-10-23] HANDOFF Scrum Master → Security Engineer (Story 1.4 B1)
Context: plan approved, env confirmed. Need: resource-server design (issuer+audience, role mapping, 401/403 problem details, CORS, headers) + abuse test list. Constraints: stateless, no sessions; Modulith shared module (OPEN).

### [2026-10-23] HANDOFF Security Engineer → Backend Dev (Story 1.4 B1)
Design: explicit JwtDecoder (jwk-set-uri) + validator chain (timestamp 30 s skew, issuer, aud mizan-api); realm_access.roles → ROLE_*; stateless, CSRF off (bearer-only, no cookies); /actuator/health public, /api/v1/admin/** admin, /api/** authenticated, rest denyAll; 401/403 RFC 9457 + WWW-Authenticate; CORS from mizan.cors.allowed-origins (no credentials); CSP default-src 'none', frame-ancestors 'none', Referrer-Policy no-referrer. Tests: validator unit (expired/issuer/aud), slice with jwt(), Testcontainers Keycloak (valid 200, none 401, other realm 401, wrong aud 401, user→admin 403, admin→admin not 401/403).

### [2026-10-23] DONE B1 (Story 1.4) — API security + quota
shared.security: SecurityConfig (stateless, CSRF off w/ rationale, health public, /api/v1/admin/** admin, /api/** authenticated, else denyAll, CORS from props, CSP default-src 'none' + no-referrer), explicit NimbusJwtDecoder + AccessTokenValidators (30 s skew, issuer, aud mizan-api), KeycloakRealmRolesConverter, ProblemDetailsAuthHandler (RFC 9457 + WWW-Authenticate), RFC 9728 metadata customised (authorization_servers = issuer, mTLS-bound false). quota: GET /api/v1/me/quota {limit 3, used 0, remaining 3}. Tests 35 api (+25): validators 6, converter 4, rules 10, Keycloak Testcontainers 7 (valid 200, none 401, tampered 401, other realm 401, no aud 401, user→admin 403, admin→admin 404). Coverage lines 87/87 = 100 %, branches 7/8.
Found: Spring Security 7 adds RFC 9728 resource_metadata to WWW-Authenticate; default metadata lacked authorization_servers and claimed mTLS-bound tokens → customised.

### [2026-10-23] HANDOFF Backend Dev → Security Engineer (Story 1.4 B2)
Context: API security done. Need: password blacklist in Keycloak image + make-admin script with TOTP required action. Constraints: test container must get the same blacklist.

### [2026-10-23] DONE B2 (Story 1.4) — Keycloak hardening
docker/keycloak/password-blacklist.txt (117,572 entries ≥10 chars, lower-cased, from SecLists xato 1M @4c0fd59, MIT, NOTICE file) baked into image at data/password-blacklists/mizan.txt; realm policy + passwordBlacklist(mizan.txt). Test container gets the same file; new test blacklistedPasswordIsRejected ("Password123" → 400). scripts/make-admin.sh (kcadm in container; admin role + CONFIGURE_TOTP; unknown e-mail → exit 1; MSYS_NO_PATHCONV for Git Bash) verified live.
Found: Keycloak 26.8 imports realm during bootstrap AFTER /health/ready is UP (503s meanwhile) → compose healthcheck now requires 200 from /realms/mizan/.well-known/openid-configuration. stack-check --fresh PASS 81 s.

### [2026-10-23] HANDOFF Security Engineer → UX Designer → UI Designer (Story 1.4 B3)
Context: realm hardened. Need: themed Keycloak login pages (login, register, verify email, reset, error) matching docs/ui tokens, fr/ar + RTL. Constraints: extend keycloak.v2, no external font CDN (self-host), minimal templates override (YAGNI).

### [2026-10-23] DONE B3 (Story 1.4) — Keycloak login theme
docker/keycloak/themes/mizan (parent keycloak.v2, CSS-only: no .ftl overrides → all login pages themed, upgrade-safe; RTL from parent template; Keycloak's own fr/ar messages). Direction "notarial calm" (paper bg, ink-green primary, gold card rule, balance mark SVG). Fonts self-hosted (Inter + Noto Sans Arabic variable, OFL, NOTICE). Realm: loginTheme mizan, displayNameHtml bilingual lockup. Screens fr/ar/dark in .recordings/screens.
Self-review fixes: bidi "·" on wrong side → dropped, two-line lockup; dark primary button white on #6fc2ae 2.1:1 → #0b1f1a 8.17:1. All checked pairs ≥ 6.39:1.

### [2026-10-23] HANDOFF UI Designer → UX Designer → Frontend Dev (Story 1.4 B4)
Context: Keycloak themed with brand tokens. Need: SPA shell (Material 22, M3 theme from tokens), Transloco fr/ar + RTL switch, landing per UX doc. Constraints: logical CSS only, self-hosted fonts, same 'notarial calm' direction, no auth yet (B5).

### [2026-10-23] DONE B4 (Story 1.4) — SPA shell, i18n, landing
Material 22.2.4 (M3 palettes generated from #0F5C4C/#C9A24B/#B3261E; filled buttons pinned to exact brand ink), self-hosted Inter + Noto Sans Arabic (@fontsource-variable via angular.json styles), Transloco 8.4.0 (fr default, ar; HTTP loader public/i18n), LanguageService (stored → browser → fr; sets html lang/dir; storage failures tolerated), cdk Dir on shell, skip link, not-legal-advice footer, landing per UX/Copywriter (one CTA, numbered steps, FR-10 notice). Screens fr/ar/dark/ar-mobile, no console errors.
Tests 14, coverage 100 % — after fixing a false 100 %: Vitest only counted imported files; coverageInclude src/app/**/*.ts exposed 77 % lines / 69 % funcs → added loader/routes/config specs.
Fixes from review: toggle lang attr moved to inner span (aria-label stays in page language), dark-mode logo contrast.

### [2026-10-23] HANDOFF Frontend (shell) → Frontend + Security (auth) (Story 1.4 B5)
Context: shell/i18n/landing done. Need: OIDC code+PKCE, tokens in memory, token only to API origin, guard, /contracts with quota chip, logout. Constraints: docs/security §3 (no localStorage for tokens), refresh via refresh token.

### [2026-10-23] DONE B5 (Story 1.4) — SPA auth + contracts
angular-oauth2-oidc 22.0.2: code + PKCE (S256 seen live), ui_locales, prompt=create for sign-up, refresh via refresh token (no iframe), logOut via end_session. TransientOAuthStorage: tokens in memory only; PKCE_verifier + nonce in sessionStorage (prefixed) and cleared after login; RFC 9207 iss param stripped. Token attached by library interceptor only to allowedUrls [apiUrl]. authGuard → login. /contracts: httpResource quota chip with CLDR plurals (Intl.PluralRules; ar zero/one/two/few/many/other), error+retry, empty state. Shell: contracts link/logout vs login. compose mizan-api env: issuer localhost:8180, JWKS keycloak:8080, CORS, quota; .env.example +4 vars.
Live run (real Keycloak + API in compose): login → /contracts "3 analyses restantes aujourd'hui"; storages hold no tokens; Authorization only to :8080; ar plural "3 تحليلات متبقية اليوم"; reload → silent SSO re-login; logout → /contracts shows Keycloak login; 0 console errors.
Fixed from live run: PKCE_verifier/nonce left in sessionStorage; ?iss= left in URL.
Tests web 40 (+26), coverage lines 100 % / branches 98.75 % (src/app/** all files).

### [2026-10-23] HANDOFF Frontend/Security → Test Architect → Tester (Story 1.4 B6)
Context: auth works live. Need: Playwright E2E of AC (sign-up, verify e-mail, login, quota, 401/403, logout) + sprint video + CI e2e job. Constraints: Mailpit API for verification link; tokens only observable on the wire.

### [2026-10-23] DONE B6 (Story 1.4) — Playwright E2E, video, CI job
mizan-web/playwright.config.ts (video on, trace on failure, webServer ng serve, E2E_SLOWMO), e2e/sign-up-and-login.spec.ts mirrors the Gherkin: landing → prompt=create sign-up → verify link via Mailpit API → set password (blacklisted "password123" refused with visible message) → /contracts chip → token observed on the wire, absent from web storage → API 200/401 problem+json/403 → Arabic RTL plural → logout ends session. 3 consecutive local passes (~10 s). Sprint video .recordings/v0.1-2026-10-23.webm (28.9 s, E2E_PAUSE=2000, SLOWMO=200; frames verified with ffmpeg contact sheet). CI job e2e (bake + compose apps+mail + playwright, artifacts: videos/traces).
Finding: Keycloak 26.8 + verifyEmail → no password at registration; VERIFY_EMAIL + UPDATE_PASSWORD required actions (anti pre-hijacking). Flow = sign-up → verify → set password. UX doc to update (B7).

### [2026-10-23] HANDOFF Tester → Tech Lead / PM (Story 1.4 B7 docs)
Context: E2E green, video recorded. Need: ADR-9, ADR-8 table additions, UX flow (verify → set password), security doc statuses, README.

### [2026-10-23] DONE B7 (Story 1.4) — docs
architecture: ADR-9 (auth as built) + ADR-8 rows (oidc/material/transloco/playwright/testcontainers-keycloak). security: STRIDE Spoofing/Tampering-JWT/Elevation Done (1.4), Public repo Done (1.3), cap 3/day. ux: Flow 1 with verify → set password. README: web app, make-admin, E2E, recordings. .env.example done in B5.

### [2026-10-23] HANDOFF Tech Lead → Tester (Story 1.4 VERIFY)
Context: B1–B7 done. Need: clean full verify (backend incl. Testcontainers Keycloak, web unit + lint + format, E2E), coverage gate, security scans. Constraints: stack running locally.

### [2026-10-23] PHASE VERIFY — Story 1.4 PASS
Backend verify GREEN: api 36 + analyzer 4 = 40 tests (incl. 8 Testcontainers Keycloak). Web: 40 tests, lint + Prettier clean. E2E 1/1 (4 local runs green). Security: Semgrep 0, Gitleaks 0 after inline gitleaks:allow on 2 test-fixture passwords, Trivy fs 0, Trivy images api/keycloak 0 fixable HIGH/CRIT, npm audit 0. Manual review: no tokens in logs/storage, token only to API origin, CORS explicit, CSP on API, problem details leak nothing.
Env issue: OneDrive sync locks emptied target/ dirs → mvn clean fails locally; workaround rm -rf then verify (CI unaffected).

### [2026-10-23] CI RED — run 39919684605 @ f0fc08d
lint ✅ test-backend ✅ test-frontend ✅ security ✅ stack ❌ e2e ❌ — diagnosing.

### [2026-10-23] CI GREEN — run 39937561681 @ 5ddaf82
lint ✅ test-backend ✅ test-frontend ✅ security ✅ stack ✅ e2e ✅ (e2e 8 min incl. image build + stack).

### [2026-10-23] SHIP — Sprint 1: required checks on main now include e2e (6 total); opening PR feature/s1-skeleton → main.

### [2026-10-23] MERGED — PR #4 (Sprint 1) into main @ ab60c86 by owner; then owner merged Dependabot #5 (kafka image 4.3.1), #6 (lz4-java 1.12.0), #7 (Maven 3.10.0), #8 (typescript-eslint 8.71.2)
Intermediate main runs cancelled by concurrency (superseded). Final main run 39940671815 @ e7e4869: 6/6 GREEN. Maven 3.10.0 SHA-256 from Dependabot re-verified (Central SHA-512 match).

### [2026-10-23] MILESTONE — Sprint 1 DONE (Stories 1.1–1.4)
Video: .recordings/v0.1-2026-10-23.webm (local, git-ignored; CI artifact e2e-videos-and-traces on every run).

### [2026-10-23] RETROSPECTIVE — Sprint 1
- **Went well**: every gate caught something real before it shipped (Enforcer convergence, honest coverage, Trivy, E2E against real Keycloak); live browser runs found defects unit tests could not (OIDC leftovers, Keycloak readiness, deferred password).
- **Went badly**: two "100 %" coverage reports were false until measured over all files; a reused local `ng serve` hid a build break that only CI's clean run exposed; heredoc-heavy shell commands failed twice on Git Bash.
- **Change for Sprint 2**: always run `npm run build` (not only tests) before pushing web changes; never reuse a running dev server for E2E before a push; check coverage `include` whenever a new source folder is added; write multi-file changes with the file tool, not shell heredocs.

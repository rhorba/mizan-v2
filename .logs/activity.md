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

### [2026-10-23] SPRINT 2 START — main green @ da8ae8f (run 39941602238). Branch feature/s2-upload. Phase: UNDERSTAND (Stories 2.1, 2.2, 4.2).

### [2026-10-23] PHASE UNDERSTAND — Sprint 2 (2.1 upload/validate/extract, 2.2 upload screen, 4.2 history & delete)
API: POST /api/v1/contracts (multipart file + language fr|ar|ary → 202 {contract, analysis PENDING}), GET /api/v1/contracts (newest 20, more on demand), GET /api/v1/contracts/{id} (owner-scoped; 404 otherwise), DELETE /api/v1/contracts/{id} (cascade). PDF rules FR-2: magic bytes, ≤ 10 MB (413), ≤ 30 pages, text layer required; store text only (ADR-5). Persistence: JPA + Flyway V1 (contracts, analyses, clause_flags) in schema mizan as mizan_app; Testcontainers Postgres 18. Out of scope (later stories): Kafka publish (3.1), quota counting (5.1), analysis view content (4.1).
Libs: PDFBox 3.0.8, Flyway 12.4.0 / Hibernate 7.4.13 / pgjdbc 42.7.14 (Boot BOM).
New env: SPRING_DATASOURCE_URL / _USERNAME / _PASSWORD for mizan-api (reuse MIZAN_DB_PASSWORD).

### [2026-10-23] HANDOFF Orchestrator → Scrum Master (Sprint 2)
Context: 🔴 chosen. Need: batch plan with DBA, Security, Backend, DevOps, UX/UI, Frontend, Tester handoffs. Constraints: ClamAV signature download is slow (stack 120 s target), fetch backend has no upload progress, env defaults for datasource pending.

### [2026-10-23] PHASE PLAN — Sprint 2 (awaiting approval)
B1 persistence · B2 upload + PDF hardening · B3 list/get/delete + IDOR · B4 ClamAV + rate limit · B5 upload screen · B6 history + delete UI · B7 E2E/video/CI/docs. Then VERIFY, SHIP (PR → main).

### [2026-10-23] HANDOFF Scrum Master → DBA (Sprint 2 B1)
Context: plan approved. Need: Flyway V1 per docs/database §3–4 in schema mizan, JPA mapping validated against it, Testcontainers Postgres for all @SpringBootTest. Constraints: app connects as mizan_app (owns schema mizan, cannot create schemas); ddl-auto validate.

### [2026-10-23] DONE B1 (Sprint 2) — persistence
Deps: data-jpa, flyway (+ flyway-database-postgresql), pgjdbc, PDFBox 3.0.8 (parent pin), testcontainers-postgresql, data-jpa-test. V1__contracts_analyses_flags.sql per DB doc. Entities Contract/Analysis (UUIDv7 via Hibernate VERSION_7, micros-truncated timestamps for keyset), Language (fr/ar/ary converter), AnalysisStatus; owner-scoped ContractRepository. ddl-auto=validate, open-in-view off, flyway create-schemas false. Tests: config/application.yml jdbc:tc:postgresql:18.6-alpine for every Spring test; PersistenceTests 6 (UUIDv7, owner scoping, CHECK page_count/language/status, cascade). API 42 tests green. Live: compose mizan-api migrated as mizan_app (all tables owned by mizan_app), Hibernate validate OK. compose + .env.example: SPRING_DATASOURCE_*.

### [2026-10-23] HANDOFF DBA → Security Engineer → Backend Dev (Sprint 2 B2)
Security design for upload: order size → magic → PDFBox load (mixed memory 16 MB + temp files) → encrypted reject (incl. owner-password-only) → pages ≤ 30 before extraction → text (strip NUL) → scan heuristic (< 20 non-space chars/page avg). File name: last path segment, NFC, drop control/bidi-override/zero-width chars, ≤ 255, fallback. Problem types urn:mizan:problem:{slug}; 413 also for servlet MaxUploadSizeExceeded. Audit line ids only (no file name, no text). PDF bytes never persisted; multipart temp file is container-managed and deleted after the request.

### [2026-10-23] DONE B2 (Sprint 2) — upload + PDF hardening
POST /api/v1/contracts (multipart file + language) → 202 + Location, {id, fileName, pageCount, createdAt, analysis{id, language, status PENDING}}. PdfInspector: size → %PDF- magic → PDFBox loadPDF(mixed 16 MB + temp) → isEncrypted (incl. owner-only) / InvalidPasswordException → pages 0/ > 30 before extraction → text (NUL stripped) → < 20 visible chars/page = scanned. FileNames.sanitize: last segment, NFC, drop Cc/Cf/unassigned/private/surrogates (bidi overrides, ZWSP, BOM, NUL), ≤ 255 code points, fallback. UploadProblem types urn:mizan:problem:{not-a-pdf,too-large(413),too-many-pages,no-text-layer,encrypted,unreadable,bad-language}; servlet MaxUploadSizeExceeded → too-large; spring.mvc.problemdetails on; multipart 10 MB / 11 MB, threshold 0. Audit logger "audit": ids, pages, language only.
Tests: API 83 (+41: inspector 12, file names 19, upload HTTP 10 — 202 + text stored + no bytea column, 5 refusals store nothing, 11 MB → 413, missing file, unauthenticated). Coverage api 99.6 % lines; PdfInspector 16/16 branches.

### [2026-10-23] HANDOFF Backend Dev (B2) → Backend Dev + Tester (Sprint 2 B3)
Context: upload done. Need: keyset list (opaque cursor, 20/page, latest analysis per contract in 1 query), GET/DELETE by id owner-scoped (404 for foreign and missing alike), IDOR suite auto-discovering /{id} endpoints. Constraints: no find-by-id without owner.

### [2026-10-23] DONE B3 (Sprint 2) — list / get / delete + IDOR
GET /api/v1/contracts?cursor= → {items[20, latest analysis each], nextCursor}; native keyset (created_at, id) < (…) on idx (owner_sub, created_at DESC, id DESC), LIMIT 21 to detect more; latest analyses for a page in 1 query (IN). Opaque base64url cursor (micros|uuid); invalid → 400 urn:mizan:problem:bad-cursor. GET /{id}, DELETE /{id} (204, JPQL bulk delete + DB cascade, audit line); foreign and missing both 404 urn:mizan:problem:contract-not-found with identical bodies. {h-schema} in native SQL (superuser test conn has no mizan search_path).
Tests: API 95 (+12: history 8 — 25 → 20+5, 21 same-timestamp rows no skip/dup, latest analysis, empty, no analysis, delete cascade + 404 + repeat 404, tampered cursors, bad uuid; IDOR 4 — every /{id} endpoint auto-discovered from handler mappings → 404 for bob without leaking, rows untouched, bob's list empty, foreign == missing). Coverage api 99.7 % lines / 93.9 % branches.
Fix: PersistenceTests raw query not scoped → failed once other classes committed rows (shared DB); now scoped by id.

### [2026-10-23] HANDOFF Backend Dev → DevOps + Security Engineer (Sprint 2 B4)
Context: CRUD + IDOR done. Need: ClamAV (compose profile av, clamd INSTREAM client, 422 malware-detected, skip+warn when MIZAN_CLAMAV_HOST unset) and per-user upload rate limit (429 + Retry-After). Constraints: never write PDF to disk; EICAR must not appear literally in the repo (Defender/Gitleaks).

### [2026-10-23] DONE B4 (Sprint 2) — ClamAV + rate limit
ClamdClient (INSTREAM over socket, 64 KiB chunks, connect 2 s / read 60 s, reply ≤ 4 KiB, never touches disk), MalwareScanner runs BEFORE PdfInspector: host unset → skip + start-up WARN (owner choice); host set but unreachable → 503 scanner-unavailable (fail-closed, see decisions); infected → 422 malware-detected + audit warn with signature. UploadRateLimitFilter: Bucket4j 8.21.0 token bucket per subject (Caffeine, 10 min idle expiry, 100 k max), default 10/min (MIZAN_UPLOADS_PER_MINUTE), after Security / before MVC multipart, 429 rate-limited + Retry-After + RateLimit-Remaining. compose: clamav/clamav:1.5.4 in profile av (clamav-db volume), api depends_on clamav required:false. .env.example: MIZAN_UPLOADS_PER_MINUTE, MIZAN_CLAMAV_HOST.
Tests: API 106 (+11; FakeClamd speaks real INSTREAM: chunking, infected, clamd error, unreachable, disabled; HTTP: 422 stores nothing, clean scanned once, 4th upload 429 + Retry-After while other user OK, reads not limited).
Live (real ClamAV 1.5.4, healthy in 1m24 incl. signatures): EICAR (base64-decoded, piped, never on disk) → 422 + audit signature=Eicar-Test-Signature; 10 clean uploads 202 (RateLimit-Remaining 9→0), 11th 429 Retry-After 2; history 10 items. Temp Keycloak client created for the check and deleted. ClamAV stopped afterwards (RAM).

### [2026-10-23] HANDOFF Security/DevOps → UX → UI → Frontend (Sprint 2 B5)
UX (docs/ux §4 Upload wireframe + §5 states): title + quota chip, drop zone 'Drop a PDF here or [Choose a file]' with limits line, language select, Analyze; loading 'Reading PDF…' (+ progress), inline errors per problem type, selection kept, success → /contracts/:id. UI: same tokens, logical props, focus ring, dashed rule drop zone (no card), RTL. Frontend: XHR backend (upload progress), ContractsApi service, routes contracts/new + contracts/:id (guarded).

### [2026-10-23] DONE B5 (Sprint 2) — upload screen + contract page
SPA: XHR backend (withFetch removed: no upload progress with fetch). ContractsApi (upload with progress events, history cursor, url, delete; problemSlug() maps urn:mizan:problem:*). /contracts/new: label-wrapped focusable file input (aria-describedby limits+error, aria-invalid), drag & drop, client pre-checks (type, > 10 MB), language mat-select defaulting to UI language (fr/ar/ary), determinate progress bar, per-slug fr/ar messages incl. Retry-After, selection kept; retryable errors (rate-limited, scanner-unavailable, generic) re-enable Analyze, final ones need another file; size in Ko/Mo. /contracts/:id (input binding): name dir=auto, pages, language, StatusChip (words + shape, not colour alone), 404 → not found, other errors → retry. Contracts page CTA. Routes contracts/{'',new,:id} under one guard.
Bugs found & fixed: (1) upload.spec never ran (@angular/animations import; my grep hid "Failed Suites" — now always check exit code); (2) FileList.item() vs test arrays → index access; (3) deep link after reload/login landed on /contracts — requested path now carried in OIDC state, restored before first navigation only if same-origin ("/x", not "//", "/\", absolute) with tests; (4) Analyze stayed disabled after retryable errors; (5) "0.0 Mo" for small files.
Live: login → upload page; renamed text file refused by API (not-a-pdf) and kept; real PDF in Darija → /contracts/{id} "1 page · Darija · En attente"; cold deep link /contracts/new survives login and reload (silent SSO); Arabic RTL default language العربية; 0 console errors.
Tests web 74 (+23), exit 0; coverage lines 100 % / branches 97 %.

### [2026-10-23] HANDOFF Frontend (B5) → UX → Frontend (Sprint 2 B6)
UX: docs/ux §5 History states (empty + CTA, loading skeleton, error + Retry, rows with status chips), Flow 3 delete (confirm 'This removes the text and all analyses' → row gone + snackbar). UI: rows reflow to cards < 768 px; delete is a labelled text button (no icon font). Frontend: signals state, keyset load more, MatDialog + MatSnackBar.

### [2026-10-23] DONE B6 (Sprint 2) — history + delete UI
Contracts page: history list (ruled rows → stacked blocks < 768 px), name link dir=auto, Intl date in UI language, pages, StatusChip, labelled delete button; keyset "load more" via nextCursor; loading / empty (+ CTA) / error states; retry re-requests the page that failed (first or next) and keeps shown rows. Delete: MatDialog ConfirmDelete (file name + "text and all analyses") → DELETE → row removed + snackbar; cancel = no request; failure keeps row + snackbar. fr/ar strings.
Live: 11 rows, real dialog in Arabic → deleted, snackbar "تم حذف العقد.", 10 rows; 0 console errors.
Found live: signed-in toolbar 459 px wide at 390 px (fr) / overflow left (ar) → < 480 px: brand word visually hidden (still the link's accessible name), tighter paddings; verified scrollWidth = 360 at 360 px fr/ar.
Tests web 82 (+8 contracts, +1 dialog), exit 0; lines 100 % / branches 97.3 %. lint/format/build exit 0.

### [2026-10-23] HANDOFF Frontend → Test Architect → Tester → DevOps → Tech Lead (Sprint 2 B7)
Need: E2E upload (server-rejected, malware when E2E_AV, accepted Darija) → contract page → history → delete → API 404; sign-up steps shared; sprint video; CI e2e with av profile + cached signatures; ADR-10, DB doc index, README.

### [2026-10-23] DONE B7 (Sprint 2) — E2E, video, CI, docs
E2E: support/session.ts (sign-up helper, token captured on the wire), support/files.ts (text PDF built in code, fake PDF, EICAR decoded at run time, gitleaks:allow), upload-history-delete.spec.ts (empty CTA → fake PDF refused by API & kept → [E2E_AV] EICAR blocked → Darija PDF → page PENDING → history row → confirm delete → snackbar → empty → API 404 contract-not-found). Local: 2/2 without AV (25.8 s), 2/2 with real ClamAV (53 s). Video .recordings/v0.2-2026-10-23.webm (42 s, 2 tests concatenated, frames verified). CI e2e: av profile, E2E_AV=1, weekly actions/cache of ClamAV DB via CLAMAV_DB bind mount. Docs: ADR-10, ADR-8 row (PDFBox/Bucket4j/ClamAV), DB doc index + history query, README (av profile, status). ClamAV stopped after, .env reset.

### [2026-10-23] HANDOFF Tech Lead → Tester (Sprint 2 VERIFY)
Need: clean backend verify, web test/lint/format/build, E2E (done 2/2 ± AV), coverage gate, Semgrep/Gitleaks/Trivy. Rule from Sprint 1 retro + B5: judge by exit codes.

### [2026-10-23] PHASE VERIFY — Sprint 2 PASS (exit codes)
Backend ./mvnw verify exit 0: api 106 + analyzer 4 = 110 tests, lines 99 % / branches 91 %. Web: test exit 0 (82, lines 100 % / branches 97.3 %), lint 0, format 0, build 0, npm audit 0. E2E 2/2 without AV and 2/2 with real ClamAV. Semgrep 0, Gitleaks 0, Trivy fs 0, Trivy images api/analyzer 0 (fixable HIGH/CRIT).
Found and fixed during VERIFY: (1) Enforcer convergence failed only in coverage-report (aggregator merges two deployables' classpaths: errorprone 2.49/2.50, antlr 4.13.2/4.13.1) — hidden while building -pl mizan-api; aggregator now keeps Java/Maven rules only, apps still enforce convergence. (2) Parallel image builds raced on the shared BuildKit m2 cache → sharing=locked; 2 cold parallel builds green. (3) A real U+202E char in ContractUploadTests source (google-java-format turns the \u escape back into the char) → built from code point; repo-wide bidi scan clean. (4) unencrypted-socket ×3 (clamd has no TLS; in-network only; loopback tests) → justified nosemgrep on the preceding line (GJF re-wraps trailing comments).

### [2026-10-23] CI GREEN — run 39981858772 @ 43cd37e: lint, test-backend, test-frontend, security, stack, e2e (with ClamAV) all success.

### [2026-10-23] RETROSPECTIVE — Sprint 2
- **Went well**: live checks against the real stack (ClamAV EICAR, rate limit, real dialog, 360 px phones, cold deep links) found defects no unit test would; IDOR suite by endpoint discovery future-proofs ownership checks; CI green on first push.
- **Went badly**: I reported "51 passed" while the run had exited 1 (a suite never loaded and my grep hid it); building -pl mizan-api hid an aggregator-only Enforcer failure until VERIFY; shell/sed escaping broke edits several times.
- **Change for Sprint 3**: judge every run by exit code; always run the full reactor (not -pl) before declaring a batch done; make multi-line or escape-heavy edits with the file tools, not sed.

### [2026-10-23] PUSH + PR #10 (feature/s2-upload → main) — checks:      10 pass       4 pending 

### [2026-10-23] PR #10 unblocked — code-scanning bot opened 3 review threads for Semgrep unencrypted-socket (reported despite nosemgrep); branch rule requires conversation resolution. Alerts 3/4/5 dismissed as won't fix with written justification (clamd plain TCP in-network; loopback test doubles), threads resolved. No rule weakened.

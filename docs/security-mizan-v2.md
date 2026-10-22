# Security Baseline: Mizan v2
**Architecture Reference**: docs/architecture-mizan-v2.md
**Version**: 1.0 | **Date**: 2026-10-22 | **Author**: Security Engineer

## 1. Threat Model (5-Minute)
- **What are we building?** A public web app where people upload private contracts for AI analysis.
- **Who would attack it?** Opportunistic attackers (credential stuffing, IDOR scraping), bill abusers (free Claude usage), prompt injectors, bots.
- **Worst outcome?** One user reading another user's contract; a drained Anthropic account; a leaked secret in the public repo.

## 2. STRIDE Analysis (top risks only)
| Threat | Component | Mitigation | Status |
|---|---|---|---|
| Spoofing | Login | Keycloak: brute-force detection, verify-email, PKCE, no implicit flow | TODO |
| Tampering | JWT | RS256 signed by Keycloak; API checks issuer, audience `mizan-api`, expiry | TODO |
| Tampering | Kafka messages | Cluster-internal only (NetworkPolicy); no external listener | TODO |
| Repudiation | Contracts | Audit log line (trace id, subject, action, contract id) on upload/delete/re-analyze | TODO |
| Info Disclosure | Contracts API | Every query scoped by `owner_sub`; IDOR integration tests on every endpoint | TODO |
| Info Disclosure | Logs/traces | Never log contract text, tokens or prompts; span attributes exclude bodies | TODO |
| Info Disclosure | Public repo | Gitleaks in CI + pre-push scan; `.env`, `*.tfvars`, `*.pem` git-ignored | TODO |
| DoS / cost | Analyzer | Per-user 5/day + global 200/day caps; 10 MB / 30 pages; Traefik rate limit 10 req/s per IP | TODO |
| Elevation | Admin API | `hasRole('admin')`; admin role assigned manually in Keycloak only | TODO |
| Prompt injection | Analyzer | Contract text in a delimited data block; system prompt forbids following embedded instructions; structured output schema; output is rendered as text, never HTML | TODO |

## 3. Authentication Strategy
- **Type**: OIDC Authorization Code + PKCE (Keycloak 26); API is a stateless OAuth2 resource server.
- **MFA**: optional TOTP in Keycloak, off by default (demo audience); on for the admin account.
- **Password policy**: length ≥ 10, not username/email, Keycloak password blacklist enabled.
- **Session management**: access token 5 min, refresh 30 min idle / 8 h max; tokens kept in memory in the SPA (no localStorage); silent refresh.

## 4. Authorization Model
- **Pattern**: simple roles.
- **Roles defined**: `user` (default on sign-up), `admin`.
- **Resource-level checks**: yes — repository methods take `ownerSub`; there is no "find by id" without owner outside the admin module.

## 5. Data Protection
- **PII fields**: email + name (Keycloak); contract text, file name, analysis output (mizan schema).
- **Encryption at rest**: OCI boot/block volumes are encrypted by default; nightly dumps encrypted with `age` before upload.
- **Encryption in transit**: HTTPS only, HSTS, TLS 1.2+; Let's Encrypt via cert-manager. In-cluster traffic plain (single node), restricted by NetworkPolicy.
- **Retention**: PDFs never stored; Kafka 24 h; deleting a contract deletes text and analyses.
- **Secrets management**: local `.env` (ignored); cluster Kubernetes Secrets created from GitHub/OCI values, never committed; Anthropic key scoped to a workspace with a monthly spend limit.

## 6. Security Requirements for Dev Team
- [ ] All inputs validated server-side (Bean Validation; PDF magic bytes `%PDF-`, size, page count)
- [ ] Angular templates only — no `innerHTML` / `bypassSecurityTrust*` for model output
- [ ] No secrets, contract text, tokens or prompts in code, logs, traces or error messages
- [ ] Security headers at Traefik: HSTS, CSP (`default-src 'self'`, Keycloak origin for auth), `X-Content-Type-Options`, `Referrer-Policy`
- [ ] CI: Gitleaks (secrets), Trivy (images + IaC), Dependabot (Maven, npm, Actions), Semgrep (SAST)
- [ ] Containers run as non-root, read-only root filesystem, no privilege escalation
- [ ] Owner-scoping (IDOR) test for each contract endpoint

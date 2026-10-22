# PRD: Mizan v2 — AI contract analysis
**Version**: 1.0 | **Date**: 2026-10-22 | **Author**: PM | **Status**: Draft

## 1. Problem Statement
Moroccan freelancers (auto-entrepreneurs) and small businesses sign client, supplier and lease contracts
they don't fully understand. A lawyer's review costs 500–2,000 MAD per contract, so most skip it and
discover unfavorable clauses (penalties, unilateral termination, IP grabs) only when it is too late.

Mizan v2 rebuilds [mizan-ma](https://github.com/rhorba/mizan-ma) (v1, never deployed) as a **publicly
reachable preprod** on a 2026 enterprise Java stack. It has a second goal: it is the owner's portfolio
proof for Java Tech Lead missions (see `docs/inputs/market-gap-plan.md`).

## 2. Goals & Success Metrics
| Goal | Metric | Target |
|---|---|---|
| A buyer can try it | Public HTTPS preprod URL, sign-up to first analysis works end to end | 100 % of release smoke test |
| Fast enough to demo live | Upload → analysis visible, 10-page contract (p95) | ≤ 45 s |
| Every technical claim is visible | One upload = one distributed trace (web → API → Kafka → analyzer → Claude) in Grafana | Shown in recording |
| Bounded API bill | Claude spend on preprod | ≤ 10 USD / month (enforced by caps) |
| Quality | Combined coverage, Sonar quality gate | ≥ 80 %, gate green |

## 3. User Stories
- As a **user**, I want to sign up with my email and verify it, so that my contracts stay private to me.
- As a **user**, I want to upload a contract PDF and pick the output language (French, Arabic, Darija), so that I can read the analysis.
- As a **user**, I want to see the analysis status update without reloading, so that I know when it is ready.
- As a **user**, I want a plain-language summary and each risky clause flagged HIGH / MEDIUM / LOW with an explanation and a suggested correction, so that I know what to negotiate.
- As a **user**, I want my contract history and the ability to delete a contract, so that I control my data.
- As a **user**, I want to re-run an analysis in another language without re-uploading, so that I can share it with a partner.
- As an **admin**, I want usage, failure and risk-level statistics, so that I can watch platform health and cost.
- As any user, I want a permanent "not legal advice" notice, so that expectations are clear.

## 4. Scope
### In Scope
- Email sign-up, verification, login, password reset (delegated to Keycloak)
- PDF upload (≤ 10 MB, ≤ 30 pages, text-based PDFs), text extraction on upload
- **Asynchronous** analysis through Kafka; Claude via Spring AI with structured output
- Output languages: French, Modern Standard Arabic, Darija; UI in French and Arabic (RTL)
- Contract history, delete, re-analyze in another language
- Per-user daily cap and global daily cap on analyses
- Admin statistics page
- Preprod on Oracle Cloud (k3s), HTTPS, tracing/metrics/logs in Grafana

### Out of Scope
- Legal advice, lawyer marketplace, e-signature, contract drafting
- OCR of scanned PDFs (clear error instead)
- **Storing the PDF file** — the PDF is discarded after text extraction (changed from v1, which stored it in R2)
- Business accounts / teams / sharing (v1 had a BUSINESS role with no distinct behaviour — dropped)
- Payments, mobile apps, a production environment (preprod only)

## 5. Requirements
### Functional
- FR-1: Sign-up with email + password, mandatory email verification, login, logout, password reset.
- FR-2: Upload a PDF; reject non-PDF (magic bytes), > 10 MB, > 30 pages, or no text layer — with a clear message.
- FR-3: On upload, extract text, persist the contract as `PENDING`, and request analysis asynchronously.
- FR-4: The analysis returns a summary plus clause flags (clause text, risk level, explanation, suggested correction) in the chosen language.
- FR-5: Contract status `PENDING → ANALYZING → COMPLETE | FAILED` is visible to the user without page reload.
- FR-6: History lists the user's own contracts, newest first; a user can delete a contract and all its analyses.
- FR-7: A user can request a new analysis of an existing contract in another language.
- FR-8: Analyses are capped per user per day and globally per day; above the cap the user gets a clear "come back tomorrow" message.
- FR-9: Admin sees counts per day, status split, failure rate, risk-level split, analyses left under the global cap.
- FR-10: The "not legal advice" notice is shown on upload and on every analysis.

### Non-Functional
- NFR-1: Performance — upload response ≤ 2 s (p95); analysis visible ≤ 45 s (p95) for 10 pages.
- NFR-2: Security — a user can only ever read their own contracts; OWASP ASVS L1 baseline; see security doc.
- NFR-3: Accessibility — WCAG 2.2 AA; full RTL for Arabic.
- NFR-4: Availability — best effort single node; restarts must not lose an analysis request (outbox + Kafka).
- NFR-5: Privacy — no PDF retained; contract text deleted with the contract; Kafka topic retention 24 h.
- NFR-6: Observability — every request traceable end to end; logs correlated by trace id.

## 6. Constraints & Assumptions
- Constraint: Java 25, Spring Boot 4, Angular 22 — the point of the project is the modern stack.
- Constraint: Oracle Cloud Always Free (4 ARM OCPU, 24 GB RAM, 200 GB disk): all images arm64.
- Constraint: Anthropic Claude through Spring AI; model configurable, default `claude-sonnet-5-5`.
- Assumption: Low traffic (demo + a few real users); single-node k3s is acceptable.
- Assumption: The owner works on it part time; Claude Code does most implementation under CTS rules.

## 7. Risks
| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| Users treat output as legal advice | M | H | Permanent notice, wording review, ToS line |
| Claude bill grows from abuse | M | M | Per-user + global daily caps, email verification, rate limit |
| OCI A1 "out of host capacity" | H | M | Terraform retry across availability domains; fallback 2 OCPU / 12 GB |
| Spring Boot 4 ecosystem gaps (Spring AI, Modulith versions) | M | M | Pin compatible versions in Sprint 1 spike before writing features |
| Darija output quality uneven | M | M | Prompt with examples; manual QA sample before recording |

## 8. Timeline
| Milestone | Target Date |
|---|---|
| Foundation docs approved + pushed | 2026-10-23 |
| Sprint 1 — skeleton, CI, auth | 2026-10-30 |
| Sprint 2 — upload + extraction | 2026-11-06 |
| Sprint 3 — Kafka + analyzer + Spring AI | 2026-11-13 |
| Sprint 4 — results UI, history, caps, admin | 2026-11-20 |
| Sprint 5 — observability + Sonar | 2026-11-27 |
| Sprint 6 — Terraform + OCI preprod + recording | 2026-12-04 |

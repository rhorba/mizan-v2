# Stories: Mizan v2
**PRD**: docs/prd-mizan-v2.md
**Architecture**: docs/architecture-mizan-v2.md

Sizes: S ≈ ½ day, M ≈ 1 day, L ≈ 2 days. Every story ends with tests green and coverage ≥ 80 %.

## Epic 1: Foundation
Skeleton that builds, tests, scans and runs locally.

### Story 1.1: Version spike and project skeleton
**Priority**: Must | **Size**: M | **Specialist**: Tech Lead
As the tech lead, I want the latest compatible GA versions pinned (Java 25, Boot 4.0.x, Modulith, Spring AI, Spring Kafka, Testcontainers, Angular 22), so that no feature work is blocked by an incompatible library.
```gherkin
Given an empty repo
When ./mvnw verify and npm test run
Then mizan-api, mizan-analyzer and mizan-web build, one smoke test each passes, and versions are recorded in ADR-8
```
**Technical Notes**: Maven multi-module, Spotless, JaCoCo aggregate; fallbacks from Architecture §8 decided here.
**Dependencies**: none

### Story 1.2: Local stack with Docker Compose
**Priority**: Must | **Size**: S | **Specialist**: DevOps
```gherkin
Given Docker is running
When I run "docker compose up -d"
Then PostgreSQL 18, Kafka 4, Keycloak 26 (realm imported) and otel-lgtm are healthy within 2 minutes
```
**Dependencies**: 1.1

### Story 1.3: CI pipeline
**Priority**: Must | **Size**: M | **Specialist**: DevOps/DevSecOps
```gherkin
Given a push to any branch
When GitHub Actions runs
Then lint, backend tests, frontend tests, Gitleaks, Semgrep and Trivy run, and the build fails under 80 % coverage
```
**Dependencies**: 1.1

### Story 1.4: Sign-up and login with Keycloak
**Priority**: Must | **Size**: L | **Specialist**: Backend + Frontend
As a user, I want to sign up with email verification and log in, so that my contracts are private.
```gherkin
Given the realm "mizan" with self-registration and verify-email on
When a visitor signs up, verifies the email and logs in from the SPA
Then the SPA holds an access token in memory and GET /api/v1/me/quota returns 200
And a call without a token returns 401, and a "user" token on /api/v1/admin/stats returns 403
```
**Technical Notes**: ADR-3; PKCE; Keycloak theme with brand tokens; realm JSON in `keycloak/`.
**Dependencies**: 1.2

## Epic 2: Upload
### Story 2.1: Upload, validate and extract a PDF
**Priority**: Must | **Size**: L | **Specialist**: Backend
```gherkin
Given a verified user
When they POST a 3-page text PDF with language "fr"
Then the API returns 202 with a contract id and an analysis in PENDING
And the text is stored, the PDF bytes are not
When they POST a non-PDF, an 11 MB file, a 31-page PDF or a scanned PDF
Then the API returns 400/413 Problem Details and stores nothing
```
**Technical Notes**: PDFBox 3; ADR-5; FR-2, FR-3.
**Dependencies**: 1.4

### Story 2.2: Upload screen
**Priority**: Must | **Size**: M | **Specialist**: Frontend
```gherkin
Given the upload page
When I drop a valid PDF, choose "Darija" and press Analyze
Then I land on the analysis view; invalid files show the matching inline error and keep the selection
```
**Dependencies**: 2.1

## Epic 3: Asynchronous analysis
### Story 3.1: Publish analysis requests through the outbox
**Priority**: Must | **Size**: M | **Specialist**: Backend
```gherkin
Given an upload transaction
When it commits
Then exactly one "contract.analysis-requested" message keyed by contract id is on Kafka
And if the API stops after commit but before publishing, the event is published on restart
```
**Technical Notes**: ADR-2; Modulith `@Externalized`; Testcontainers Kafka.
**Dependencies**: 2.1

### Story 3.2: Analyzer worker with Spring AI
**Priority**: Must | **Size**: L | **Specialist**: Backend
```gherkin
Given an "analysis-requested" message
When the analyzer consumes it
Then it calls the model once with the language-specific prompt, maps the reply to AnalysisOutput
And publishes "analysis-completed" with summary, flags, model and token counts
Given the model returns invalid output twice or times out after 3 retries
Then it publishes "analysis-failed" and the message lands in the DLT
```
**Technical Notes**: ADR-4, ADR-6; stub ChatModel in tests; prompt-injection guard.
**Dependencies**: 3.1

### Story 3.3: Apply results idempotently
**Priority**: Must | **Size**: M | **Specialist**: Backend
```gherkin
Given an analysis in ANALYZING
When "analysis-completed" arrives twice
Then the analysis is COMPLETE with its flags exactly once
And a result for an unknown or deleted contract is logged and dropped
```
**Dependencies**: 3.2

### Story 3.4: Live status (SSE)
**Priority**: Must | **Size**: M | **Specialist**: Backend + Frontend
```gherkin
Given I am on the analysis view of a PENDING analysis
When the status changes
Then the stepper updates within 1 second without reload; if SSE drops, polling every 3 s takes over
```
**Technical Notes**: ADR-7; owner check on the stream.
**Dependencies**: 3.3

## Epic 4: Results and history
### Story 4.1: Analysis view
**Priority**: Must | **Size**: M | **Specialist**: Frontend
```gherkin
Given a COMPLETE analysis
Then I see the summary, flags sorted HIGH → LOW with filter chips, expandable details and a Copy button
And the not-legal-advice notice is visible; Arabic/Darija render RTL
```
**Dependencies**: 3.4

### Story 4.2: History and delete
**Priority**: Must | **Size**: M | **Specialist**: Backend + Frontend
```gherkin
Given I own 25 contracts
Then the history shows the newest 20 with status chips and loads more on demand
When I delete one and confirm
Then the contract, its text and analyses are gone and a GET returns 404
And no endpoint ever returns another user's contract (IDOR suite)
```
**Dependencies**: 2.1

### Story 4.3: Re-analyze in another language
**Priority**: Should | **Size**: S | **Specialist**: Backend + Frontend
```gherkin
Given a COMPLETE analysis in French
When I choose "Analyze in Arabic"
Then a new analysis starts, uses one quota unit, and both results are available as tabs
```
**Dependencies**: 4.1, 5.1

## Epic 5: Quota and admin
### Story 5.1: Per-user and global daily caps
**Priority**: Must | **Size**: M | **Specialist**: Backend
```gherkin
Given a cap of 5 per user and 200 globally
When a user at 4 sends 10 parallel uploads
Then exactly one is accepted and nine get 429 Problem Details
And /api/v1/me/quota returns the remaining count; a FAILED analysis refunds its unit
```
**Technical Notes**: atomic upsert in Database §3; caps from env.
**Dependencies**: 2.1

### Story 5.2: Admin statistics
**Priority**: Should | **Size**: M | **Specialist**: Backend + Frontend
```gherkin
Given an admin
Then /admin shows analyses per day (14 days), status split, failure rate, risk split, tokens today and global cap left
```
**Dependencies**: 3.3

## Epic 6: Observability and quality
### Story 6.1: End-to-end tracing, metrics and logs
**Priority**: Must | **Size**: M | **Specialist**: DevOps + Backend
```gherkin
Given the local stack
When I upload a contract
Then Grafana shows one trace spanning web → mizan-api → Kafka → mizan-analyzer → model call → result apply
And logs in Loki carry the same trace id; no span or log contains contract text or tokens
```
**Dependencies**: 3.3

### Story 6.2: SonarCloud quality gate
**Priority**: Must | **Size**: S | **Specialist**: DevOps
```gherkin
Given SONAR_TOKEN is set
When CI runs on main
Then SonarCloud reports coverage and the quality gate blocks the build when failed
```
**Dependencies**: 1.3

## Epic 7: Preprod on Oracle Cloud
### Story 7.1: Terraform for OCI
**Priority**: Must | **Size**: L | **Specialist**: DevOps
```gherkin
Given OCI credentials in local tfvars
When I run "terraform apply"
Then a 4 OCPU / 24 GB A1 instance with k3s is reachable on 80/443, SSH only from my IP, state in the OCI bucket
```
**Dependencies**: 1.3

### Story 7.2: Kubernetes manifests and GitOps
**Priority**: Must | **Size**: L | **Specialist**: DevOps
```gherkin
Given the cluster from 7.1
When Argo CD syncs deploy/overlays/preprod
Then all pods are Ready with limits from System Design, NetworkPolicies applied, HTTPS valid on the public host
And merging to main rolls out new multi-arch images without manual steps
```
**Dependencies**: 7.1, 6.1

### Story 7.3: Backups and uptime check
**Priority**: Should | **Size**: S | **Specialist**: DevOps
```gherkin
Given the preprod
Then a nightly encrypted dump lands in the bucket (7 kept) and a restore test succeeds once
And a scheduled health check opens an issue after 2 failures
```
**Dependencies**: 7.2

### Story 7.4: Release v2.0 — smoke, recording, README case study
**Priority**: Must | **Size**: M | **Specialist**: Tester + Tech Lead
```gherkin
Given preprod is green
Then the Playwright happy path passes against the public URL and is recorded to .recordings/v2.0-<date>.webm
And the README has the live link, architecture diagram, trace screenshot and "problem / built / stack / outcome" summary
```
**Dependencies**: all Must stories

## Sprint Allocation
| Sprint | Stories | Estimated Effort |
|---|---|---|
| Sprint 1 (→ Oct 30) | 1.1, 1.2, 1.3, 1.4 | 5 days |
| Sprint 2 (→ Nov 6) | 2.1, 2.2, 4.2 | 4 days |
| Sprint 3 (→ Nov 13) | 3.1, 3.2, 3.3 | 4 days |
| Sprint 4 (→ Nov 20) | 3.4, 4.1, 5.1, 4.3, 5.2 | 4.5 days |
| Sprint 5 (→ Nov 27) | 6.1, 6.2, 7.1 | 3.5 days |
| Sprint 6 (→ Dec 4) | 7.2, 7.3, 7.4 | 3.5 days |

## Traceability
| PRD | Stories |
|---|---|
| FR-1 | 1.4 |
| FR-2, FR-3 | 2.1, 2.2, 3.1 |
| FR-4 | 3.2, 3.3, 4.1 |
| FR-5 | 3.4 |
| FR-6 | 4.2 |
| FR-7 | 4.3 |
| FR-8 | 5.1 |
| FR-9 | 5.2 |
| FR-10 | 2.2, 4.1 |
| NFR-4 | 3.1, 3.3 |
| NFR-6 | 6.1 |
| Preprod goal | 7.1–7.4 |

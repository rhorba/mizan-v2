# Database Design: Mizan v2
**Architecture Reference**: docs/architecture-mizan-v2.md
**Version**: 1.0 | **Date**: 2026-10-22 | **Author**: DBA

## 1. Database Selection
- **Engine**: PostgreSQL 18.
- **Rationale**: relational data, JSONB for the raw model output, one instance for both the app and Keycloak.
- **Hosting**: self-hosted StatefulSet on k3s (local-path volume), nightly `pg_dump`.
- **Layout**: database `mizan` with schemas `mizan` (owned by `mizan_app`) and `keycloak` (owned by `keycloak`). No users table — identity lives in Keycloak; rows store the Keycloak subject (`owner_sub`).

## 2. Entity-Relationship Model
```
contracts ──1:N──> analyses ──1:N──> clause_flags
daily_usage (owner_sub, usage_day)          -- quota module
event_publication                            -- Spring Modulith outbox (library-managed)
```

## 3. Schema Design
```sql
CREATE TABLE contracts (
  id            UUID PRIMARY KEY,                -- generated in Java (UUIDv7)
  owner_sub     VARCHAR(64)  NOT NULL,           -- Keycloak subject
  file_name     VARCHAR(255) NOT NULL,
  page_count    INT          NOT NULL CHECK (page_count BETWEEN 1 AND 30),
  text_content  TEXT         NOT NULL,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE analyses (
  id            UUID PRIMARY KEY,
  contract_id   UUID        NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
  language      VARCHAR(3)  NOT NULL CHECK (language IN ('fr','ar','ary')),
  status        VARCHAR(12) NOT NULL CHECK (status IN ('PENDING','ANALYZING','COMPLETE','FAILED')),
  summary       TEXT,
  failure_reason VARCHAR(255),
  model         VARCHAR(64),
  input_tokens  INT,
  output_tokens INT,
  raw_output    JSONB,
  requested_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at  TIMESTAMPTZ,
  version       BIGINT      NOT NULL DEFAULT 0   -- optimistic locking
);

CREATE TABLE clause_flags (
  id                   UUID PRIMARY KEY,
  analysis_id          UUID        NOT NULL REFERENCES analyses(id) ON DELETE CASCADE,
  position             INT         NOT NULL,
  clause_text          TEXT        NOT NULL,
  risk_level           VARCHAR(6)  NOT NULL CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
  explanation          TEXT        NOT NULL,
  suggested_correction TEXT
);

CREATE TABLE daily_usage (
  owner_sub     VARCHAR(64) NOT NULL,   -- '*' = the global row of the day
  usage_day     DATE        NOT NULL,   -- Africa/Casablanca day of the request
  analyses      INT         NOT NULL DEFAULT 0 CHECK (analyses >= 0),
  input_tokens  BIGINT      NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
  output_tokens BIGINT      NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
  PRIMARY KEY (owner_sub, usage_day)
);
```
Quota increments use `INSERT … ON CONFLICT (owner_sub, usage_day) DO UPDATE SET analyses = daily_usage.analyses + 1 WHERE daily_usage.analyses < :cap RETURNING analyses` — atomic, no race. The global cap uses the same upsert on the day's `'*'` row, taken **first** in the same transaction (fixed lock order, no deadlock); a `SUM` would let two parallel requests both pass at cap − 1 (Sprint 4 correction). A FAILED analysis decrements both rows (refund); token counts are added on completion and failure, so spend survives contract deletion.

## 4. Index Strategy
| Table | Index Name | Columns | Query Pattern |
|---|---|---|---|
| contracts | idx_contracts_owner_created | owner_sub, created_at DESC, id DESC | History page (keyset cursor on created_at, id) |
| analyses | idx_analyses_contract | contract_id, requested_at DESC | Latest analysis per contract |
| analyses | idx_analyses_status_requested | status, requested_at | Admin stats, stuck-job sweep |
| clause_flags | idx_flags_analysis | analysis_id, position | Load flags in order |
| daily_usage | (PK) | owner_sub, usage_day | Quota check |
| daily_usage | idx_usage_day | usage_day | Global cap sum |

## 5. Migration Plan
| Migration File | Description | Reversible |
|---|---|---|
| V1__contracts_analyses_flags.sql | contracts, analyses, clause_flags + indexes | Yes (drop in reverse order) |
| V2__modulith_event_publication.sql | Spring Modulith JDBC event publication table (from the library's schema; Sprint 3) | Yes |
| V3__daily_usage.sql | quota table (Sprint 4) | Yes |

Flyway, run by `mizan-api` at startup, schema `mizan`. Keycloak manages its own schema.

## 6. Access Patterns
| Use Case | Query Pattern | Index Coverage |
|---|---|---|
| History | `WHERE owner_sub = ? AND (created_at, id) < (?, ?) ORDER BY created_at DESC, id DESC LIMIT 21` | idx_contracts_owner_created |
| Contract detail | `WHERE id = ? AND owner_sub = ?` + latest analysis + flags | PK, idx_analyses_contract, idx_flags_analysis |
| Apply analysis result | `UPDATE analyses SET … WHERE id = ? AND contract_id = ? AND status IN ('PENDING','ANALYZING')` | PK |
| Stuck jobs (> 10 min ANALYZING) | `WHERE status = 'ANALYZING' AND requested_at < ?` | idx_analyses_status_requested |
| Admin stats | `GROUP BY date(requested_at), status` / `GROUP BY risk_level` | idx_analyses_status_requested |

## 7. Sensitive Data
- `contracts.text_content`, `analyses.summary`, `analyses.raw_output`, `clause_flags.*` are confidential: owner-only reads, never logged.
- Row-level security: not used; owner scoping in repositories plus IDOR tests. Revisit if a second app ever reads this schema.

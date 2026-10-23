-- docs/database-mizan-v2.md §3–4. Runs in schema "mizan" as mizan_app (Flyway default schema).
-- Reverse: DROP TABLE clause_flags; DROP TABLE analyses; DROP TABLE contracts;

CREATE TABLE contracts (
  id            UUID PRIMARY KEY,
  owner_sub     VARCHAR(64)  NOT NULL,
  file_name     VARCHAR(255) NOT NULL,
  page_count    INT          NOT NULL CHECK (page_count BETWEEN 1 AND 30),
  text_content  TEXT         NOT NULL,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE analyses (
  id             UUID PRIMARY KEY,
  contract_id    UUID         NOT NULL REFERENCES contracts (id) ON DELETE CASCADE,
  language       VARCHAR(3)   NOT NULL CHECK (language IN ('fr', 'ar', 'ary')),
  status         VARCHAR(12)  NOT NULL CHECK (status IN ('PENDING', 'ANALYZING', 'COMPLETE', 'FAILED')),
  summary        TEXT,
  failure_reason VARCHAR(255),
  model          VARCHAR(64),
  input_tokens   INT,
  output_tokens  INT,
  raw_output     JSONB,
  requested_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  completed_at   TIMESTAMPTZ,
  version        BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE clause_flags (
  id                   UUID PRIMARY KEY,
  analysis_id          UUID       NOT NULL REFERENCES analyses (id) ON DELETE CASCADE,
  position             INT        NOT NULL,
  clause_text          TEXT       NOT NULL,
  risk_level           VARCHAR(6) NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
  explanation          TEXT       NOT NULL,
  suggested_correction TEXT
);

-- History page: WHERE owner_sub = ? ORDER BY created_at DESC, id DESC (keyset cursor).
CREATE INDEX idx_contracts_owner_created ON contracts (owner_sub, created_at DESC, id DESC);
CREATE INDEX idx_analyses_contract ON analyses (contract_id, requested_at DESC);
CREATE INDEX idx_analyses_status_requested ON analyses (status, requested_at);
CREATE INDEX idx_flags_analysis ON clause_flags (analysis_id, position);
